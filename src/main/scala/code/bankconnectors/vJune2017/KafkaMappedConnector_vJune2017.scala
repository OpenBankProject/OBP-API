package code.bankconnectors.vJune2017

/*
Open Bank Project - API
Copyright (C) 2011-2017, TESOBE Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see http://www.gnu.org/licenses/.

Email: contact@tesobe.com
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany
*/

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import code.api.util.APIUtil.{MessageDoc, saveConnectorMetric}
import code.api.util.ErrorMessages
import code.api.v2_1_0.PostCounterpartyBespoke
import code.api.v3_0_0.CoreAccountJsonV300
import code.bankconnectors._
import code.bankconnectors.vMar2017._
import code.customer._
import code.kafka.KafkaHelper
import code.metadata.counterparties.CounterpartyTrait
import code.model._
import code.model.dataAccess._
import code.transactionrequests.TransactionRequests.TransactionRequest
import code.util.Helper.MdcLoggable
import com.google.common.cache.CacheBuilder
import net.liftweb.common.{Box, _}
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.collection.immutable.{List, Nil, Seq}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scalacache.ScalaCache
import scalacache.guava.GuavaCache
import scalacache.memoization.memoizeSync

trait KafkaMappedConnector_vJune2017 extends Connector with KafkaHelper with MdcLoggable {
  
  type AccountType = BankAccountJune2017
  
  implicit override val nameOfConnector = KafkaMappedConnector_vJune2017.toString
  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))
  val getBankTTL                            = Props.get("connector.cache.ttl.seconds.getBank", "0").toInt * 1000 // Miliseconds
  val getBanksTTL                           = Props.get("connector.cache.ttl.seconds.getBanks", "0").toInt * 1000 // Miliseconds
  val getUserTTL                            = Props.get("connector.cache.ttl.seconds.getUser", "0").toInt * 1000 // Miliseconds
  val getAccountTTL                         = Props.get("connector.cache.ttl.seconds.getAccount", "0").toInt * 1000 // Miliseconds
  val getAccountHolderTTL                   = Props.get("connector.cache.ttl.seconds.getAccountHolderTTL", "0").toInt * 1000 // Miliseconds
  val getAccountsTTL                        = Props.get("connector.cache.ttl.seconds.getAccounts", "0").toInt * 1000 // Miliseconds
  val getTransactionTTL                     = Props.get("connector.cache.ttl.seconds.getTransaction", "0").toInt * 1000 // Miliseconds
  val getTransactionsTTL                    = Props.get("connector.cache.ttl.seconds.getTransactions", "0").toInt * 1000 // Miliseconds
  val getCounterpartyFromTransactionTTL     = Props.get("connector.cache.ttl.seconds.getCounterpartyFromTransaction", "0").toInt * 1000 // Miliseconds
  val getCounterpartiesFromTransactionTTL   = Props.get("connector.cache.ttl.seconds.getCounterpartiesFromTransaction", "0").toInt * 1000 // Miliseconds
  
  
  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "June2017"

  implicit val formats = net.liftweb.json.DefaultFormats
  override val messageDocs = ArrayBuffer[MessageDoc]()
  val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
  val exampleDate = simpleDateFormat.parse("22/08/2013")
  val emptyObjectJson: JValue = decompose(Nil)
  def currentResourceUserId = AuthUser.getCurrentResourceUserUserId
  def currentResourceUsername = AuthUser.getCurrentUserUsername
  val authInfoExample = AuthInfo(userId = "userId", username = "username", cbsToken = "cbsToken")
  val inboundStatusMessagesExample = List(InboundStatusMessage("ESB", "Success", "0", "OK"))
  val errorCodeExample = "OBP-6001: ..."
  
  //TODO, this a temporary way, we do not know when should we update the MfToken, for now, we update it once it call the override def getBankAccounts(username: String).
  var cbsToken = ""
//  var currentResourceUsername = ""
  
  //////////////////////////////////////////////////////////////////////////////
  // the following methods, have been implemented in new Adapter code
  messageDocs += MessageDoc(
    process = "obp.get.AdapterInfo",
    messageFormat = messageFormat,
    description = "getAdapterInfo from kafka ",
    exampleOutboundMessage = decompose(
      OutboundGetAdapterInfo(date = (new Date()).toString)
    ),
    exampleInboundMessage = decompose(
      InboundAdapterInfo(
        InboundAdapterInfoInternal(
          errorCodeExample,
          inboundStatusMessagesExample,
          name = "Obp-Kafka-South",
          version = "June2017",
          git_commit = "...",
          date = (new Date()).toString
        )
      )
    )
  )
  override def getAdapterInfo: Box[InboundAdapterInfoInternal] = {
    val req = OutboundGetAdapterInfo((new Date()).toString)
    
    logger.debug(s"Kafka getAdapterInfo Req says:  is: $req")
    val box = processToBox[OutboundGetAdapterInfo](req).map(_.extract[InboundAdapterInfo].data)
    logger.debug(s"Kafka getAdapterInfo Res says:  is: $Box")
    
    val res = box match {
      case Full(list) if (list.errorCode=="") =>
        Full(list)
      case Full(list) if (list.errorCode!="") =>
        Failure("INTERNAL-OBP-ADAPTER-xxx: "+ list.errorCode+". + CoreBank-Error:"+ list.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c)  =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    
    res
  }
  
  
  messageDocs += MessageDoc(
    process = "obp.get.User",
    messageFormat = messageFormat,
    description = "getUser from kafka ",
    exampleOutboundMessage = decompose(
      OutboundGetUserByUsernamePassword(
        authInfoExample,
        password = "2b78e8"
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetUserByUsernamePassword(
        authInfoExample,
        InboundValidatedUser(
          errorCodeExample,
          inboundStatusMessagesExample,
          email = "susan.uk.29@example.com",
          displayName = "susan"
        )
      )
    )
  )
  override def getUser(username: String, password: String): Box[InboundUser] = saveConnectorMetric {
    memoizeSync(getUserTTL millisecond) {
      
      val req = OutboundGetUserByUsernamePassword(AuthInfo(currentResourceUserId, username, cbsToken), password = password)
  
      logger.debug(s"Kafka getUser Req says:  is: $req")
      val box = processToBox[OutboundGetUserByUsernamePassword](req).map(_.extract[InboundGetUserByUsernamePassword].data)
      logger.debug(s"Kafka getUser Res says:  is: $Box")
      
      val res = box match {
        case Full(list) if (list.errorCode=="" && username == list.displayName) =>
          Full(new InboundUser(username, password, username))
        case Full(list) if (list.errorCode!="") =>
          Failure("INTERNAL-OBP-ADAPTER-xxx: "+ list.errorCode+". + CoreBank-Error:"+ list.backendMessages)
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse)
        case Failure(msg, e, c) =>
          Failure(msg, e, c)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
  
      res

    }}("getUser")

  
  messageDocs += MessageDoc(
    process = "obp.get.Banks",
    messageFormat = messageFormat,
    description = "getBanks",
    exampleOutboundMessage = decompose(
      OutboundGetBanks(authInfoExample)
    ),
    exampleInboundMessage = decompose(
      InboundGetBanks(
        authInfoExample,
        InboundBank(
          errorCode = errorCodeExample,
          inboundStatusMessagesExample,
          bankId = "gh.29.uk",
          name = "sushan",
          logo = "TESOBE",
          url = "https://tesobe.com/"
        )  :: Nil
      )
      
    )
  )
  override def getBanks(): Box[List[Bank]] = saveConnectorMetric {
    memoizeSync(getBanksTTL millisecond){
      val req = OutboundGetBanks(AuthInfo(currentResourceUserId, currentResourceUsername, cbsToken))
      logger.info(s"Kafka getBanks Req is: $req")
      val box: Box[List[InboundBank]] = processToBox[OutboundGetBanks](req).map(_.extract[InboundGetBanks].data)
      logger.debug(s"Kafka getBanks Res says:  is: $Box")
      val res = box match {
        case Full(list) if (list.head.errorCode=="") =>
          Full(list map (new Bank2(_)))
        case Full(list) if (list.head.errorCode!="") =>
          Failure("INTERNAL-OBP-ADAPTER-xxx: "+ list.head.errorCode+". + CoreBank-Error:"+ list.head.backendMessages)
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse)
        case Failure(msg, e, c) =>
          Failure(msg, e, c)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
      logger.info(s"Kafka getBanks says res is $res")
      res
    }
  }("getBanks")
  
  
  messageDocs += MessageDoc(
    process = "obp.get.Bank",
    messageFormat = messageFormat,
    description = "getBank from kafka ",
    exampleOutboundMessage = decompose(
      OutboundGetBank(authInfoExample,"bankId")
    ),
    exampleInboundMessage = decompose(
      InboundGetBank(
        authInfoExample,
        InboundBank(
          errorCodeExample,
          inboundStatusMessagesExample,
          bankId = "gh.29.uk",
          name = "sushan",
          logo = "TESOBE",
          url = "https://tesobe.com/"
        )
      )
      
    )
  )
  override def getBank(bankId: BankId): Box[Bank] =  saveConnectorMetric {
    memoizeSync(getBankTTL millisecond){
      val req = OutboundGetBank(
        authInfo = AuthInfo(currentResourceUsername, currentResourceUserId, cbsToken),
        bankId = bankId.toString
      )
      logger.debug(s"Kafka getBank Req says:  is: $req")
      val box =  processToBox[OutboundGetBank](req).map(_.extract[InboundGetBank].data)
      logger.debug(s"Kafka getBank Res says:  is: $Box") 
      
      box match {
        case Full(list) if (list.errorCode=="") =>
          Full(new Bank2(list))
        case Full(list) if (list.errorCode!="") =>
          Failure("INTERNAL-OBP-ADAPTER-xxx: "+ list.errorCode+". + CoreBank-Error:"+ list.backendMessages)
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse)
        case Failure(msg, e, c) =>
          logger.error(msg,e)
          logger.error(msg)
          Failure(msg, e, c)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
      
    }}("getBank")
  
  
  messageDocs += MessageDoc(
    process = "obp.get.Accounts",
    messageFormat = messageFormat,
    description = "getBankAccounts from kafka",
    exampleOutboundMessage = decompose(
      OutboundGetAccounts(
        authInfoExample,
        true,
        InternalBasicCustomers(customers =List(
          InternalBasicCustomer(
            bankId="bankId",
            customerId = "customerId",
            customerNumber = "customerNumber",
            legalName = "legalName",
            dateOfBirth = exampleDate
          ))))
    ),
    exampleInboundMessage = decompose(
      InboundGetAccounts(
        authInfoExample,
        InboundAccountJune2017(
          errorCode = errorCodeExample,
          inboundStatusMessagesExample,
          cbsToken ="cbsToken",
          bankId = "gh.29.uk",
          branchId = "222",
          accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
          accountNumber = "123",
          accountType = "AC",
          balanceAmount = "50",
          balanceCurrency = "EUR",
          owners = "Susan" :: " Frank" :: Nil,
          viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
          bankRoutingScheme = "iban",
          bankRoutingAddress = "bankRoutingAddress",
          branchRoutingScheme = "branchRoutingScheme",
          branchRoutingAddress = " branchRoutingAddress",
          accountRoutingScheme = "accountRoutingScheme",
          accountRoutingAddress = "accountRoutingAddress"
        ) :: Nil)
    )
  )
  override def getBankAccounts(username: String, callMfFlag: Boolean): Box[List[InboundAccountJune2017]] = saveConnectorMetric {
    memoizeSync(getAccountsTTL millisecond) {
      val customerList :List[Customer]= Customer.customerProvider.vend.getCustomersByUserId(currentResourceUserId)
      val internalCustomers = JsonFactory_vJune2017.createCustomersJson(customerList)
      
      val req = OutboundGetAccounts(AuthInfo(currentResourceUserId, username, cbsToken),callMfFlag,internalCustomers)
      logger.debug(s"Kafka getBankAccounts says: req is: $req")
      val box: Box[List[InboundAccountJune2017]] = processToBox[OutboundGetAccounts](req).map(_.extract[InboundGetAccounts].data)
      logger.debug(s"Kafka getBankAccounts says res is $box")
      box match {
        case Full(list) if (list.head.errorCode=="") =>
          cbsToken = list.head.cbsToken
//          currentResourceUsername = username
          Full(list)
        case Full(list) if (list.head.errorCode!="") =>
          Failure("INTERNAL-OBP-ADAPTER-xxx: "+ list.head.errorCode+". + CoreBank-Error:"+ list.head.backendMessages)
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
        case Failure(msg, e, c) =>
          Failure(msg, e, c)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
    }}("getBankAccounts")
  
  
  messageDocs += MessageDoc(
    process = "obp.get.Account",
    messageFormat = messageFormat,
    description = "getBankAccount from kafka",
    exampleOutboundMessage = decompose(
      OutboundGetAccountbyAccountID(
        authInfoExample,
        "bankId",
        "accountId"
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetAccountbyAccountID(
        authInfoExample,
        InboundAccountJune2017(
          errorCode = errorCodeExample,
          inboundStatusMessagesExample,
          cbsToken = "cbsToken",
          bankId = "gh.29.uk",
          branchId = "222",
          accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
          accountNumber = "123",
          accountType = "AC",
          balanceAmount = "50",
          balanceCurrency = "EUR",
          owners = "Susan" :: " Frank" :: Nil,
          viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
          bankRoutingScheme = "iban",
          bankRoutingAddress = "bankRoutingAddress",
          branchRoutingScheme = "branchRoutingScheme",
          branchRoutingAddress = " branchRoutingAddress",
          accountRoutingScheme = "accountRoutingScheme",
          accountRoutingAddress = "accountRoutingAddress"
        )
      ))
  )
  override def getBankAccount(bankId: BankId, accountId: AccountId): Box[BankAccountJune2017] = saveConnectorMetric{
    memoizeSync(getAccountTTL millisecond){
      // Generate random uuid to be used as request-response match id
      val req = OutboundGetAccountbyAccountID(
        authInfo = AuthInfo(currentResourceUserId, currentResourceUsername, cbsToken),
        bankId = bankId.toString,
        accountId = accountId.value
      )
      logger.debug(s"Kafka getBankAccount says: req is: $req")
      val box: Box[InboundAccountJune2017] = processToBox[OutboundGetAccountbyAccountID](req).map(_.extract[InboundGetAccountbyAccountID].data)
      logger.debug(s"Kafka getBankAccount says res is $box")
      box match {
        case Full(f) if (f.errorCode=="") =>
          Full(new BankAccountJune2017(f))
        case Full(f) if (f.errorCode!="") =>
          Failure("INTERNAL-OBP-ADAPTER-xxx: "+ f.errorCode+". + CoreBank-Error:"+ f.backendMessages)
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
        case Failure(msg, e, c) =>
          Failure(msg, e, c)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
    }}("getBankAccount")
  
  messageDocs += MessageDoc(
    process = "obp.get.Account",
    messageFormat = messageFormat,
    description = "getBankAccount from kafka",
    exampleOutboundMessage = decompose(
      OutboundGetAccountbyAccountID(
        authInfoExample,
        "bankId",
        "accountId"
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetAccountbyAccountID(
        authInfoExample,
        InboundAccountJune2017(
          errorCode = errorCodeExample,
          inboundStatusMessagesExample,
          cbsToken = "cbsToken",
          bankId = "gh.29.uk",
          branchId = "222",
          accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
          accountNumber = "123",
          accountType = "AC",
          balanceAmount = "50",
          balanceCurrency = "EUR",
          owners = "Susan" :: " Frank" :: Nil,
          viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
          bankRoutingScheme = "iban",
          bankRoutingAddress = "bankRoutingAddress",
          branchRoutingScheme = "branchRoutingScheme",
          branchRoutingAddress = " branchRoutingAddress",
          accountRoutingScheme = "accountRoutingScheme",
          accountRoutingAddress = "accountRoutingAddress"
        )
      ))
  )
  override def getCoreBankAccounts(BankIdAccountIds: List[BankIdAccountId]) : Box[List[CoreAccountJsonV300]] = saveConnectorMetric{
    memoizeSync(getAccountTTL millisecond){
      
      val req = OutboundGetCoreBankAccounts(
        authInfo = AuthInfo(currentResourceUserId, currentResourceUsername, cbsToken),
        BankIdAccountIds
      )
      
      logger.debug(s"Kafka getCoreBankAccounts says: req is: $req")
      val box: Box[List[InternalInboundCoreAccount]] = processToBox[OutboundGetCoreBankAccounts](req).map(_.extract[InboundGetCoreBankAccounts].data)
      logger.debug(s"Kafka getCoreBankAccounts says res is $box")
      box match {
        case Full(f) if (f.head.errorCode=="") =>
          Full(f.map( x => CoreAccountJsonV300(x.id,x.label,x.bank_id,x.account_routing)))
        case Full(f) if (f.head.errorCode!="") =>
          Failure("INTERNAL-OBP-ADAPTER-xxx: "+ f.head.errorCode+". + CoreBank-Error:"+ f.head.backendMessages)
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
        case Failure(msg, e, c) =>
          Failure(msg, e, c)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
    }}("getBankAccount")
  
  
  messageDocs += MessageDoc(
    process = "obp.get.Transactions",
    messageFormat = messageFormat,
    description = "getTransactions from kafka",
    exampleOutboundMessage = decompose(
      OutboundGetTransactions(
        authInfo = authInfoExample,
        bankId = "bankId",
        accountId = "accountId",
        limit =100,
        fromDate="exampleDate",
        toDate="exampleDate"
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetTransactions(
        authInfoExample,
        InternalTransaction(
          errorCodeExample,
          inboundStatusMessagesExample,
          transactionId = "1234",
          accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
          amount = "100",
          bankId = "gh.29.uk",
          completedDate = "",
          counterpartyId = "1234",
          counterpartyName = "obp",
          currency = "EUR",
          description = "Good Boy",
          newBalanceAmount = "10000",
          newBalanceCurrency = "1000",
          postedDate = "",
          `type` = "AC",
          userId = "1234"
        ):: Nil
      ))
  )
  override def getTransactions(bankId: BankId, accountId: AccountId, queryParams: OBPQueryParam*): Box[List[Transaction]] = {
    val limit: OBPLimit = queryParams.collect { case OBPLimit(value) => OBPLimit(value) }.headOption.get
    val offset = queryParams.collect { case OBPOffset(value) => OBPOffset(value) }.headOption.get
    val fromDate = queryParams.collect { case OBPFromDate(date) => OBPFromDate(date) }.headOption.get
    val toDate = queryParams.collect { case OBPToDate(date) => OBPToDate(date)}.headOption.get
    val ordering = queryParams.collect {
      //we don't care about the intended sort field and only sort on finish date for now
      case OBPOrdering(field, direction) => OBPOrdering(field, direction)}.headOption.get
    val optionalParams = Seq(limit, offset, fromDate, toDate, ordering)
    
    val req = OutboundGetTransactions(
      authInfo = AuthInfo(userId = currentResourceUserId, username = currentResourceUsername,cbsToken = cbsToken),
      bankId = bankId.toString,
      accountId = accountId.value,
      limit = limit.value,
      fromDate = fromDate.value.toString,
      toDate = toDate.value.toString
    )
    
    implicit val formats = net.liftweb.json.DefaultFormats
    logger.debug(s"Kafka getTransactions says: req is: $req")
    val box= processToBox[OutboundGetTransactions](req).map(_.extract[InboundGetTransactions].data)
    logger.debug(s"Kafka getTransactions says: res is: $box")
    
    box match {
      case Full(list) if (list.head.errorCode=="")  =>
        // Check does the response data match the requested data
        val isCorrect = list.forall(x => x.accountId == accountId.value && x.bankId == bankId.value)
        if (!isCorrect) throw new Exception(ErrorMessages.InvalidConnectorResponseForGetTransactions)
        // Populate fields and generate result
        val res = for {
          r: InternalTransaction <- list
          transaction: Transaction <- createNewTransaction(r)
        } yield {
          transaction
        }
        Full(res)
      //TODO is this needed updateAccountTransactions(bankId, accountId)
      case Full(list) if (list.head.errorCode!="") =>
        Failure("INTERNAL-OBP-ADAPTER-xxx: "+ list.head.errorCode+". + CoreBank-Error:"+ list.head.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.Transaction",
    messageFormat = messageFormat,
    description = "getTransaction from kafka ",
    exampleOutboundMessage = decompose(
      OutboundGetTransaction(
        authInfoExample,
        "bankId",
        "accountId",
        "transactionId"
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetTransaction(
        authInfoExample,
        InternalTransaction(
          errorCode = errorCodeExample,
          inboundStatusMessagesExample,
          transactionId = "1234",
          accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
          amount = "100",
          bankId = "gh.29.uk",
          completedDate = "",
          counterpartyId = "1234",
          counterpartyName = "obp",
          currency = "EUR",
          description = "Good Boy",
          newBalanceAmount = "10000",
          newBalanceCurrency = "1000",
          postedDate = "",
          `type` = "AC",
          userId = "1234"
        )
      ))
  )
  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Box[Transaction] = {
    val req = OutboundGetTransaction(
      authInfo = AuthInfo(currentResourceUserId, currentResourceUsername, cbsToken),
      bankId = bankId.toString,
      accountId = accountId.value,
      transactionId = transactionId.toString)
    
    // Since result is single account, we need only first list entry
    logger.debug(s"Kafka getTransaction Req says:  is: $req")
    val box: Box[InternalTransaction] = processToBox[OutboundGetTransaction](req).map(_.extract[InboundGetTransaction].data)
    logger.debug(s"Kafka getTransaction Res says: is: $box")
    
    box match {
      // Check does the response data match the requested data
      case Full(x) if (transactionId.value != x.transactionId && x.errorCode=="") =>
        Failure(ErrorMessages.InvalidConnectorResponseForGetTransaction, Empty, Empty)
      case Full(x) if (transactionId.value == x.transactionId && x.errorCode=="") =>
        createNewTransaction(x)
      case Full(x) if (x.errorCode!="") =>
        Failure("INTERNAL-OBP-ADAPTER-xxx: "+ x.errorCode+". + CoreBank-Error:"+ x.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    
  }
  
  messageDocs += MessageDoc(
    process = "obp.create.Challenge",
    messageFormat = messageFormat,
    description = "CreateChallenge from kafka ",
    exampleOutboundMessage = decompose(
      OutboundChallengeBase(
        action = "obp.create.Challenge",
        messageFormat = messageFormat,
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        transactionRequestType = "SANDBOX_TAN",
        transactionRequestId = "1234567"
      )
    ),
    exampleInboundMessage = decompose(
      InboundCreateChallengeJune2017(
        authInfoExample,
        InternalCreateChallengeJune2017(
          errorCodeExample,
          inboundStatusMessagesExample,
          "1234"
        )
      )
    )
  )
  
  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String) = {
    val req = OutboundCreateChallengeJune2017(
      authInfo = AuthInfo(currentResourceUserId, currentResourceUsername, cbsToken),
      bankId = bankId.value,
      accountId = accountId.value,
      userId = userId,
      username = AuthUser.getCurrentUserUsername,
      transactionRequestType = transactionRequestType.value,
      transactionRequestId = transactionRequestId
    )
    logger.debug(s"Kafka createChallenge Req says:  is: $req")
    val box: Box[InternalCreateChallengeJune2017] = processToBox[OutboundCreateChallengeJune2017](req).map(_.extract[InboundCreateChallengeJune2017].data)
    logger.debug(s"Kafka createChallenge Res says:  is: $Box")
    
    val res = box match {
      case Full(x) if (x.errorCode=="")  =>
        Full(x.answer)
      case Full(x) if (x.errorCode!="") =>
        Failure("INTERNAL-OBP-ADAPTER-xxx: "+ x.errorCode+". + CoreBank-Error:"+ x.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    res
    
  }
  
  messageDocs += MessageDoc(
    process = "obp.create.Counterparty",
    messageFormat = messageFormat,
    description = "createCounterparty from kafka ",
    exampleOutboundMessage = decompose(
      OutboundCreateCounterparty(
        authInfoExample,
        OutboundCounterparty(
          name = "name",
          description = "description",
          createdByUserId = "createdByUserId",
          thisBankId = "thisBankId",
          thisAccountId = "thisAccountId",
          thisViewId = "thisViewId",
          otherAccountRoutingScheme = "otherAccountRoutingScheme",
          otherAccountRoutingAddress = "otherAccountRoutingAddress",
          otherAccountSecondaryRoutingScheme = "otherAccountSecondaryRoutingScheme",
          otherAccountSecondaryRoutingAddress = "otherAccountSecondaryRoutingAddress",
          otherBankRoutingScheme = "otherBankRoutingScheme",
          otherBankRoutingAddress = "otherBankRoutingAddress",
          otherBranchRoutingScheme = "otherBranchRoutingScheme",
          otherBranchRoutingAddress = "otherBranchRoutingAddress",
          isBeneficiary = true,
          bespoke = PostCounterpartyBespoke("key","value") ::Nil
        )
      )
    ),
    exampleInboundMessage = decompose(
      InboundCreateChallengeJune2017(
        authInfoExample,
        InternalCreateChallengeJune2017(
          errorCodeExample,
          inboundStatusMessagesExample,
          "1234"
        )
      )
    )
  )
  
  override def createCounterparty(
    name: String,
    description: String,
    createdByUserId: String,
    thisBankId: String,
    thisAccountId: String,
    thisViewId: String,
    otherAccountRoutingScheme: String,
    otherAccountRoutingAddress: String,
    otherAccountSecondaryRoutingScheme: String,
    otherAccountSecondaryRoutingAddress: String,
    otherBankRoutingScheme: String,
    otherBankRoutingAddress: String,
    otherBranchRoutingScheme: String,
    otherBranchRoutingAddress: String,
    isBeneficiary:Boolean,
    bespoke: List[PostCounterpartyBespoke]
  ): Box[CounterpartyTrait] = {
    val req = OutboundCreateCounterparty(
      authInfo = AuthInfo(currentResourceUserId, currentResourceUsername, cbsToken),
      counterparty = OutboundCounterparty(
        name: String,
        description: String,
        createdByUserId: String,
        thisBankId: String,
        thisAccountId: String,
        thisViewId: String,
        otherAccountRoutingScheme: String,
        otherAccountRoutingAddress: String,
        otherAccountSecondaryRoutingScheme: String,
        otherAccountSecondaryRoutingAddress: String,
        otherBankRoutingScheme: String,
        otherBankRoutingAddress: String,
        otherBranchRoutingScheme: String,
        otherBranchRoutingAddress: String,
        isBeneficiary:Boolean,
        bespoke: List[PostCounterpartyBespoke]
      )
    )
  
    logger.debug(s"Kafka createCounterparty Req says: is: $req")
    val box: Box[InternalCounterparty] = processToBox[OutboundCreateCounterparty](req).map(_.extract[InboundCreateCounterparty].data)
    logger.debug(s"Kafka createCounterparty Res says: is: $box")
    
    val res: Box[CounterpartyTrait] = box match {
      case Full(x) if (x.errorCode=="")  =>
        Full(x)
      case Full(x) if (x.errorCode!="") =>
        Failure("OBP-Error:"+ x.errorCode+". + CoreBank-Error:"+ x.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    res
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.transactionRequests210",
    messageFormat = messageFormat,
    description = "getTransactionRequests210 from kafka ",
    exampleOutboundMessage = decompose(
      OutboundGetTransactionRequests210(
        authInfoExample,
        OutboundTransactionRequests(
          "accountId: String",
          "accountType: String",
          "currency: String",
          "iban: String",
          "number: String",
          "bankId: BankId",
          "branchId: String",
          "accountRoutingScheme: String",
          "accountRoutingAddress: String"
        )
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetTransactionRequests210(
        authInfoExample,
        InternalGetTransactionRequests(
          errorCodeExample,
          inboundStatusMessagesExample,
          Nil
        )
      )
    )
  )
  
  override def getTransactionRequests210(user : User, fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    val req = OutboundGetTransactionRequests210(
      authInfo = AuthInfo(currentResourceUserId, currentResourceUsername, cbsToken),
      counterparty = OutboundTransactionRequests(
        accountId = fromAccount.accountId.value,
        accountType = fromAccount.accountType,
        currency = fromAccount.currency,
        iban = fromAccount.iban.getOrElse(""),
        number = fromAccount.number,
        bankId = fromAccount.bankId.value,
        branchId = fromAccount.bankId.value,
        accountRoutingScheme = fromAccount.accountRoutingScheme,
        accountRoutingAddress= fromAccount.accountRoutingAddress
      )
    )
  
    logger.debug(s"Kafka createCounterparty Req says: is: $req")
    val box: Box[InternalGetTransactionRequests] = processToBox[OutboundGetTransactionRequests210](req).map(_.extract[InboundGetTransactionRequests210].data)
    logger.debug(s"Kafka createCounterparty Res says: is: $box")
  
    val res: Box[List[TransactionRequest]] = box match {
      case Full(x) if (x.errorCode=="")  =>
        Full(x.transactionRequests)
      case Full(x) if (x.errorCode!="") =>
        Failure("OBP-Error:"+ x.errorCode+". + CoreBank-Error:"+ x.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    res
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.counterparties",
    messageFormat = messageFormat,
    description = "getCounterparties from kafka ",
    exampleOutboundMessage = decompose(
      OutboundGetTransactionRequests210(
        authInfoExample,
        OutboundTransactionRequests(
          "accountId: String",
          "accountType: String",
          "currency: String",
          "iban: String",
          "number: String",
          "bankId: BankId",
          "branchId: String",
          "accountRoutingScheme: String",
          "accountRoutingAddress: String"
        )
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetTransactionRequests210(
        authInfoExample,
        InternalGetTransactionRequests(
          errorCodeExample,
          inboundStatusMessagesExample,
          Nil
        )
      )
    )
  )
  
  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId,viewId :ViewId): Box[List[CounterpartyTrait]] = {
    val req = OutboundGetCounterparties(
      authInfo = AuthInfo(currentResourceUserId, currentResourceUsername, cbsToken),
      counterparty = InternalOutboundGetCounterparties(
        thisBankId = thisBankId.value,
        thisAccountId = thisAccountId.value,
        viewId = viewId.value
      )
    )
  
    logger.debug(s"Kafka getCounterparties Req says: is: $req")
    val box: Box[List[InternalCounterparty]] = processToBox[OutboundGetCounterparties](req).map(_.extract[InboundGetCounterparties].data)
    logger.debug(s"Kafka getCounterparties Res says: is: $box")
  
    val res: Box[List[CounterpartyTrait]] = box match {
      case Full(x) if (x.head.errorCode=="")  =>
        Full(x)
      case Full(x) if (x.head.errorCode!="") =>
        Failure("OBP-Error:"+ x.head.errorCode+". + CoreBank-Error:"+ x.head.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    res
  }
  
  
  messageDocs += MessageDoc(
    process = "obp.get.counterparties",
    messageFormat = messageFormat,
    description = "getCounterparties from kafka ",
    exampleOutboundMessage = decompose(
      OutboundGetCustomersByUserIdFuture(
        authInfoExample
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetCustomersByUserIdFuture(
        authInfoExample,
        Nil
      )
    )
  )
  
  override def getCustomersByUserIdFuture(userId: String): Future[Box[List[Customer]]] = Future {
    
    val box = for {
      req <- Full(OutboundGetCustomersByUserIdFuture(authInfo = AuthInfo(currentResourceUserId, currentResourceUsername, cbsToken)))
      _<- Full(logger.debug(s"Kafka getCustomersByUserIdFuture Req says: is: $req"))
      kafkaMessage <- processToBox[OutboundGetCustomersByUserIdFuture](req)
      inboundGetCustomersByUserIdFuture <- tryo{kafkaMessage.extract[InboundGetCustomersByUserIdFuture]} ?~! s"$InboundGetCustomersByUserIdFuture extract error"
      internalCustomer <- Full(inboundGetCustomersByUserIdFuture.data)
    } yield{
      internalCustomer
    }
    
    logger.debug(s"Kafka getCustomersByUserIdFuture Res says: is: $box")
    
    val res: Box[List[InternalCustomer]] = box match {
      case Full(x) if (x.head.errorCode=="")  =>
        Full(x)
      case Full(x) if (x.head.errorCode!="") =>
        Failure("OBP-Error:"+ x.head.errorCode+". + CoreBank-Error:"+ x.head.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    res
  }
  
  
  /////////////////////////////////////////////////////////////////////////////
  // Helper for creating a transaction
  def createNewTransaction(r: InternalTransaction): Box[Transaction] = {
    var datePosted: Date = null
    if (r.postedDate != null) // && r.details.posted.matches("^[0-9]{8}$"))
      datePosted = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(r.postedDate)

    var dateCompleted: Date = null
    if (r.completedDate != null) // && r.details.completed.matches("^[0-9]{8}$"))
      dateCompleted = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(r.completedDate)

    for {
      counterpartyId <- tryo {
        r.counterpartyId
      }
      counterpartyName <- tryo {
        r.counterpartyName
      }
      thisAccount <- getBankAccount(BankId(r.bankId), AccountId(r.accountId))
      //creates a dummy OtherBankAccount without an OtherBankAccountMetadata, which results in one being generated (in OtherBankAccount init)
      dummyOtherBankAccount <- tryo {
        createCounterparty(counterpartyId, counterpartyName, thisAccount, None)
      }
      //and create the proper OtherBankAccount with the correct "id" attribute set to the metadataId of the OtherBankAccountMetadata object
      //note: as we are passing in the OtherBankAccountMetadata we don't incur another db call to get it in OtherBankAccount init
      counterparty <- tryo {
        createCounterparty(counterpartyId, counterpartyName, thisAccount, Some(dummyOtherBankAccount.metadata))
      }
    } yield {
      // Create new transaction
      new Transaction(
        r.transactionId, // uuid:String
        TransactionId(r.transactionId), // id:TransactionId
        thisAccount, // thisAccount:BankAccount
        counterparty, // otherAccount:OtherBankAccount
        r.`type`, // transactionType:String
        BigDecimal(r.amount), // val amount:BigDecimal
        thisAccount.currency, // currency:String
        Some(r.description), // description:Option[String]
        datePosted, // startDate:Date
        dateCompleted, // finishDate:Date
        BigDecimal(r.newBalanceAmount) // balance:BigDecimal)
      )
    }
  }


  // Helper for creating other bank account
  def createCounterparty(counterpartyId: String, counterpartyName: String, o: BankAccountJune2017, alreadyFoundMetadata: Option[CounterpartyMetadata]) = {
    new Counterparty(
      counterPartyId = alreadyFoundMetadata.map(_.metadataId).getOrElse(""),
      label = counterpartyName,
      nationalIdentifier = "1234",
      otherBankRoutingAddress = None,
      otherAccountRoutingAddress = None,
      thisAccountId = AccountId(counterpartyId),
      thisBankId = BankId(""),
      kind = "1234",
      otherBankId = o.bankId,
      otherAccountId = o.accountId,
      alreadyFoundMetadata = alreadyFoundMetadata,
      name = "sushan",
      otherBankRoutingScheme = "obp",
      otherAccountRoutingScheme = "obp",
      otherAccountProvider = "obp",
      isBeneficiary = true
    )
  }

}


object KafkaMappedConnector_vJune2017 extends KafkaMappedConnector_vJune2017{
  
}