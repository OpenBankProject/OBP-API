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

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.{MessageDoc, saveConnectorMetric}
import code.api.util.ErrorMessages
import code.api.v2_1_0.TransactionRequestCommonBodyJSON
import code.bankconnectors._
import code.branches._
import code.customer.Customer
import code.fx.fx
import code.metadata.counterparties.CounterpartyTrait
import code.model._
import code.model.dataAccess._
import code.transactionrequests.TransactionRequests.TransactionRequestTypes._
import code.util.Helper.MdcLoggable
import com.google.common.cache.CacheBuilder
import net.liftweb.common._
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers.{tryo, _}
import net.liftweb.util.Props

import scala.collection.immutable.{Nil, Seq}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.language.postfixOps
import scalacache.ScalaCache
import scalacache.guava.GuavaCache
import scalacache.memoization.memoizeSync

object KafkaMappedConnector_vJune2017 extends Connector with KafkaHelper with MdcLoggable {
  
  type AccountType = BankAccountJune2017
  
  implicit override val nameOfConnector = KafkaMappedConnector_vJune2017.getClass.getSimpleName
  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L
  ).build[String, Object]
  implicit val scalaCache = ScalaCache(GuavaCache(underlyingGuavaCache))
  val getBankTTL = Props.get("connector.cache.ttl.seconds.getBank", "0"
  ).toInt * 1000
  // Miliseconds
  val getBanksTTL = Props.get("connector.cache.ttl.seconds.getBanks", "0"
  ).toInt * 1000
  // Miliseconds
  val getUserTTL = Props.get("connector.cache.ttl.seconds.getUser", "0"
  ).toInt * 1000
  // Miliseconds
  val getAccountTTL = Props.get("connector.cache.ttl.seconds.getAccount", "0"
  ).toInt * 1000
  // Miliseconds
  val getAccountHolderTTL = Props.get(
    "connector.cache.ttl.seconds.getAccountHolderTTL", "0"
  ).toInt * 1000
  // Miliseconds
  val getAccountsTTL = Props.get("connector.cache.ttl.seconds.getAccounts", "0"
  ).toInt * 1000
  // Miliseconds
  val getTransactionTTL = Props.get(
    "connector.cache.ttl.seconds.getTransaction", "0"
  ).toInt * 1000
  // Miliseconds
  val getTransactionsTTL = Props.get(
    "connector.cache.ttl.seconds.getTransactions", "0"
  ).toInt * 1000
  // Miliseconds
  val getCounterpartyFromTransactionTTL = Props.get(
    "connector.cache.ttl.seconds.getCounterpartyFromTransaction", "0"
  ).toInt * 1000
  // Miliseconds
  val getCounterpartiesFromTransactionTTL = Props.get(
    "connector.cache.ttl.seconds.getCounterpartiesFromTransaction", "0"
  ).toInt * 1000 // Miliseconds
  
  
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
  
  
  //////////////////////////////////////////////////////////////////////////////
  // the following methods, have been implemented in new Adapter code
  messageDocs += MessageDoc(
    process = "obp.get.AdapterInfo",
    messageFormat = messageFormat,
    description = "getAdapterInfo from kafka ",
    exampleOutboundMessage = decompose(
      GetAdapterInfo(date = (new Date()).toString)
    ),
    exampleInboundMessage = decompose(
      InboundAdapterInfo(
        errorCode = "OBP-6001: ...",
        name = "Obp-Kafka-South",
        version = "June2017",
        git_commit = "...",
        date = (new Date()).toString
      )
    )
  )
  override def getAdapterInfo: Box[InboundAdapterInfo] = {
    val req = GetAdapterInfo((new Date()).toString)
    
    val box = processToBox[GetAdapterInfo](req).map(_.extract[AdapterInfo].data)
    val res = box match {
      case Full(list) =>
        Full(list)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, _, _) =>
        Failure(msg)
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
      GetUserByUsernamePassword(
        AuthInfo(userId = "userId", username = "username", cbsToken = "cbsToken"),
        password = "2b78e8"
      )
    ),
    exampleInboundMessage = decompose(
      UserWrapper(
        Some(InboundValidatedUser(
          errorCode = "OBP-6001: ...",
          email = "susan.uk.29@example.com",
          displayName = "susan"
        )))
    )
  )
  override def getUser(username: String, password: String): Box[InboundUser] = saveConnectorMetric {
    memoizeSync(getUserTTL millisecond) {
      for {
        req <- Full(
          GetUserByUsernamePassword(AuthInfo(currentResourceUserId, username,"cbsToken"), password = password)
        )
        user: Option[InboundValidatedUser] <- processToBox[GetUserByUsernamePassword](req).map(_.extract[UserWrapper].data)
        u <- user
        recUsername <- Some(u.displayName)
      } yield 
        if (username == u.displayName) 
          new InboundUser(recUsername, password, recUsername
      ) else 
          null
    }}("getUser")

  
  messageDocs += MessageDoc(
    process = "obp.get.Banks",
    messageFormat = messageFormat,
    description = "getBanks",
    exampleOutboundMessage = decompose(
      GetBanks(AuthInfo("userId", "username", "cbsToken"),"")
    ),
    exampleInboundMessage = decompose(
      InboundBank(
        errorCode = "OBP-6001: ...",
        bankId = "gh.29.uk",
        name = "sushan",
        logo = "TESOBE",
        url = "https://tesobe.com/"
      ) :: InboundBank(
        errorCode = "OBP-6001: ...",
        bankId = "gh.29.uk",
        name = "sushan",
        logo = "TESOBE",
        url = "https://tesobe.com/"
      ) :: Nil
    )
  )
  //gets banks handled by this connector
  override def getBanks(): Box[List[Bank]] = saveConnectorMetric {
    memoizeSync(getBanksTTL millisecond){
      val req = GetBanks(AuthInfo(currentResourceUserId, currentResourceUsername, "cbsToken"),criteria="")
      logger.debug(s"Kafka getBanks says: req is: $req")
      val box: Box[List[InboundBank]] = processToBox[GetBanks](req).map(_.extract[Banks].data)
      val res = box match {
        case Full(list) =>
          Full(list map (new Bank2(_)))
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse)
        case Failure(msg, _, _) =>
          Failure(msg)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
      logger.debug(s"Kafka getBanks says res is $res")
      res
      }}("getBanks")

  
  messageDocs += MessageDoc(
    process = "obp.get.Bank",
    messageFormat = messageFormat,
    description = "getBank from kafka ",
    exampleOutboundMessage = decompose(
      GetBank(AuthInfo("userId", "username", "cbsToken"),"bankId")
    ),
    exampleInboundMessage = decompose(
      InboundBank(
        errorCode = "OBP-6001: ...",
        bankId = "gh.29.uk",
        name = "sushan",
        logo = "TESOBE",
        url = "https://tesobe.com/"
      )
    )
  )
  override def getBank(bankId: BankId): Box[Bank] =  saveConnectorMetric {
    memoizeSync(getBankTTL millisecond){
      val req = GetBank(
        authInfo = AuthInfo(currentResourceUsername, currentResourceUserId, "cbsToken"),
        bankId = bankId.toString
      )

      val r =  processToBox[GetBank](req).map(_.extract[BankWrapper].data)

      r match {
        case Full(v) =>
          Full(new Bank2(v))
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse)
        case Failure(msg, _, _) =>
          Failure(msg)
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
        AuthInfo("userId", "username","cbsToken"),
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
      InboundBankAccounts(
        AuthInfo("userId", "username", "cbsToken"),
        InboundAccountJune2017(
          errorCode = "OBP-6001: ...",
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
  override def getBankAccounts(username: String): Box[List[InboundAccountJune2017]] = saveConnectorMetric {
    memoizeSync(getAccountsTTL millisecond) {
      val customerList :List[Customer]= Customer.customerProvider.vend.getCustomersByUserId(currentResourceUserId)
      val internalCustomers = JsonFactory_vJune2017.createCustomersJson(customerList)
        
      val req = OutboundGetAccounts(AuthInfo(currentResourceUserId, username,"cbsToken"),internalCustomers)
      logger.debug(s"Kafka getBankAccounts says: req is: $req")
      val rList: Box[List[InboundAccountJune2017]] = processToBox[OutboundGetAccounts](req).map(_.extract[InboundBankAccounts].data)
      val res = rList //map (new BankAccountJune2017(_))
      logger.debug(s"Kafka getBankAccounts says res is $res")
      res match {
        // Check does the response data match the requested data
        case Full(list) =>
          Full(list)
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
        case Failure(msg, _, _) =>
          Failure(msg)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
  }}("getBankAccounts")
  
  
  messageDocs += MessageDoc(
    process = "obp.get.Account",
    messageFormat = messageFormat,
    description = "getBankAccount from kafka",
    exampleOutboundMessage = decompose(
      GetAccountbyAccountID(
        AuthInfo("userId", "username", "cbsToken"),
        "bankId",
        "accountId"
      )
    ),
    exampleInboundMessage = decompose(
      InboundBankAccount(
        AuthInfo("userId", "username", "cbsToken"),
        InboundAccountJune2017(
          errorCode = "OBP-6001: ...",
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
      val req = GetAccountbyAccountID(
        authInfo = AuthInfo(currentResourceUserId, currentResourceUsername,"cbsToken"),
        bankId = bankId.toString,
        accountId = accountId.value
      )
      logger.debug(s"Kafka getBankAccount says: req is: $req")
        // 1 there is error in Adapter code,
        // 2 there is no account in Adapter code,
        // 3 there is error in Kafka
        // 4 there is error in Akka
        // 5 there is error in Future
      val res: Box[InboundAccountJune2017] = processToBox[GetAccountbyAccountID](req).map(_.extract[InboundBankAccount].data)
      logger.debug(s"Kafka getBankAccount says res is $res")
      res match {
        // Check does the response data match the requested data
        case Full(r) =>
          Full(new BankAccountJune2017(r))
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
        case Failure(msg, _, _) =>
          Failure(msg)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
  }}("getBankAccount")
  
  
  messageDocs += MessageDoc(
    process = "obp.get.Transactions",
    messageFormat = messageFormat,
    description = "getTransactions from kafka",
    exampleOutboundMessage = decompose(
      GetTransactions(
        authInfo = AuthInfo("userId", "username", "cbsToken" ),
        bankId = "bankId",
        accountId = "accountId",
        limit =100,
        fromDate="exampleDate",
        toDate="exampleDate"
      )
    ),
    exampleInboundMessage = decompose(
      InboundTransactions(
      AuthInfo("userId", "username", "cbsToken" ),
      InternalTransaction(
        errorCode = "OBP-6001: ...",
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
      ) :: InternalTransaction(
        errorCode = "OBP-6001: ...",
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
      ) :: Nil
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
    
    val req = GetTransactions(
      authInfo = AuthInfo(userId = currentResourceUserId, username = currentResourceUsername,cbsToken = "cbsToken" ),
      bankId = bankId.toString,
      accountId = accountId.value,
      limit = limit.value,
      fromDate = fromDate.value.toString,
      toDate = toDate.value.toString
    )
    
    implicit val formats = net.liftweb.json.DefaultFormats
    logger.debug(s"Kafka getTransactions says: req is: $req")
    processToBox[GetTransactions](req).map(_.extract[InboundTransactions].data) match {
      case Full(list) =>
        logger.debug(s"Kafka getTransactions says: req is: $list")
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
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }

  }
  
  messageDocs += MessageDoc(
    process = "obp.get.Transaction",
    messageFormat = messageFormat,
    description = "getTransaction from kafka ",
    exampleOutboundMessage = decompose(
      GetTransaction(
        AuthInfo("userId","usename","cbsToken"),
        "bankId",
        "accountId",
        "transactionId"
      )
    ),
    exampleInboundMessage = decompose(
      InboundTransaction(
        AuthInfo("userId","usename","cbsToken"),
        InternalTransaction(
        errorCode = "OBP-6001: ...",
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
    val req = GetTransaction(
      authInfo = AuthInfo(currentResourceUserId, currentResourceUsername,"cbsToken"),
      bankId = bankId.toString,
      accountId = accountId.value,
      transactionId = transactionId.toString)
    
    // Since result is single account, we need only first list entry
    logger.debug(s"Kafka getTransaction request says:  is: $req")
    val r: Box[InternalTransaction] = processToBox[GetTransaction](req).map(_.extract[InboundTransaction].data)
    logger.debug(s"Kafka getTransaction response says: is: $r")
    r match {
      // Check does the response data match the requested data
      case Full(x) if transactionId.value != x.transactionId =>
        Failure(ErrorMessages.InvalidConnectorResponseForGetTransaction, Empty, Empty)
      case Full(x) if transactionId.value == x.transactionId =>
        createNewTransaction(x)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    
  }
  
  messageDocs += MessageDoc(
    process = "obp.makePaymentImpl",
    messageFormat = messageFormat,
    description = "saveTransaction from kafka",
    exampleOutboundMessage = decompose(
      CreateTransaction(
        AuthInfo("userId","usename","cbsToken"),
        // fromAccount
        fromAccountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        fromAccountBankId = "gh.29.uk",
        
        // transaction details
        transactionRequestType = "SANDBOX_TAN",
        transactionChargePolicy = "RECEIVER",
        transactionRequestCommonBody=SwaggerDefinitionsJSON.transactionRequestBodyCounterpartyJSON,
        // toAccount or toCounterparty
        toCounterpartyId = "1234",
        toCounterpartyName = "obp",
        toCounterpartyCurrency = "EUR",
        toCounterpartyRoutingAddress = "1234",
        toCounterpartyRoutingScheme = "OBP",
        toCounterpartyBankRoutingAddress = "12345",
        toCounterpartyBankRoutingScheme = "OBP"
      )
    ),
    exampleInboundMessage = decompose(
      InboundCreateTransactionId(
        AuthInfo("userId","usename","cbsToken"),
        InternalTransactionId("123")
      )
    )
  )
  
   override def makePaymentv300(
     initiator: User,
     fromAccount: BankAccount,
     toAccount: BankAccount,
     toCounterparty: CounterpartyTrait,
     transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
     transactionRequestType: TransactionRequestType,
     chargePolicy: String
   ): Box[TransactionId] = {
    
    val postedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).format(now)
    
    val req = CreateTransaction(
      authInfo = AuthInfo(currentResourceUserId, currentResourceUsername, "cbsToken"),
  
      // fromAccount
      fromAccountId = fromAccount.accountId.value,
      fromAccountBankId = fromAccount.bankId.value,
  
      // transaction details
      transactionRequestType = transactionRequestType.value,
      transactionChargePolicy = chargePolicy,
      transactionRequestCommonBody = transactionRequestCommonBody,
  
      // toAccount or toCounterparty
      toCounterpartyId = toAccount.accountId.value,
      toCounterpartyName = toAccount.name,
      toCounterpartyCurrency = toAccount.currency,
      toCounterpartyRoutingAddress = toAccount.accountId.value,
      toCounterpartyRoutingScheme = "OBP",
      toCounterpartyBankRoutingAddress = toAccount.bankId.value,
      toCounterpartyBankRoutingScheme = "OBP"
    )
    
    // Since result is single account, we need only first list entry
     val box= processToBox[CreateTransaction](req).map(_.extract[InboundCreateTransactionId].data)
     
     val res = box match {
       case Full(r) =>
         Full(TransactionId(r.id))
       case Empty =>
         Failure(ErrorMessages.ConnectorEmptyResponse)
       case Failure(msg, _, _) =>
         Failure(msg)
       case _ =>
         Failure(ErrorMessages.UnknownError)
     }
  
     res
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
      InboundCreateChallange(
        errorCode = "OBP-6001: ...",
        challengeId = "1234567"
      )
    )
  )
  
  override def createChallenge(
    bankId: BankId,
    accountId: AccountId,
    userId: String,
    transactionRequestType: TransactionRequestType,
    transactionRequestId: String,
    phoneNumber: String
  ): Box[String] = {
    val req = OutboundCreateChallengeJune2017(
      authInfo = AuthInfo(currentResourceUserId, currentResourceUsername,"cbsToken"),
      bankId = bankId.value,
      accountId = accountId.value,
      userId = userId,
      username = AuthUser.getCurrentUserUsername,
      transactionRequestType = transactionRequestType.value,
      transactionRequestId = transactionRequestId,
      phoneNumber=phoneNumber
    )
    
  
    val box: Box[InternalCreateChallengeJune2017] = processToBox[OutboundCreateChallengeJune2017](req).map(_.extract[InboundCreateChallengeJune2017].data)
  
    val res = box match {
      case Full(r) =>
        Full(r.answer)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  
    res
    
    
  }
  
  //////////////////////////////////////////////////////////////////////////////// 
  // the following methods do not implement in new Adapter code
  messageDocs += MessageDoc(
    process = "obp.get.ChallengeThreshold",
    messageFormat = messageFormat,
    description = "getChallengeThreshold from kafka ",
    exampleOutboundMessage = decompose(
      OutboundChallengeThresholdBase(
        messageFormat = messageFormat,
        action = "obp.get.ChallengeThreshold",
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        viewId = "owner",
        transactionRequestType = SANDBOX_TAN.toString,
        currency = "GBP",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com"
      )
    ),
    exampleInboundMessage = decompose(
      InboundChallengeLevel(
        errorCode = "OBP-6001: ...",
        limit = "1000",
        currency = "EUR"
      )
    )
  )
  // Gets current challenge level for transaction request
  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, username: String) = {
    // Create argument list
    val req = OutboundChallengeThresholdBase(
      action = "obp.get.ChallengeThreshold",
      messageFormat = messageFormat,
      bankId = bankId,
      accountId = accountId,
      viewId = viewId,
      transactionRequestType = transactionRequestType,
      currency = currency,
      userId = userId,
      username = username)

//    val r: Option[InboundChallengeLevel] = process(req).extractOpt[InboundChallengeLevel]
//     Return result
//    r match {
//       Check does the response data match the requested data
//      case Some(x) => AmountOfMoney(x.currency, x.limit)
//      case _ => {
        val limit = BigDecimal("1000")
        val rate = fx.exchangeRate("ILS", currency)
        val convertedLimit = fx.convert(limit, rate)
        Full(AmountOfMoney(currency, convertedLimit.toString()))
//      }
//    }
  }

  messageDocs += MessageDoc(
    process = "obp.get.ChargeLevel",
    messageFormat = messageFormat,
    description = "ChargeLevel from kafka ",
    exampleOutboundMessage = decompose(OutboundChargeLevelBase(
      action = "obp.get.ChargeLevel",
      messageFormat = messageFormat,
      bankId = "gh.29.uk",
      accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
      viewId = "owner",
      userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
      username = "susan.uk.29@example.com",
      transactionRequestType = SANDBOX_TAN.toString,
      currency = "EUR"
    )
    ),
    exampleInboundMessage = decompose(
      InboundChargeLevel(
        errorCode = "OBP-6001: ...",
        currency = "EUR",
        amount = ""
      )
    )
  )
  override def getChargeLevel(
                               bankId: BankId,
                               accountId: AccountId,
                               viewId: ViewId,
                               userId: String,
                               username: String,
                               transactionRequestType: String,
                               currency: String
                             ): Box[AmountOfMoney] = {
    // Create argument list
    val req = OutboundChargeLevelBase(
      action = "obp.get.ChargeLevel",
      messageFormat = messageFormat,
      bankId = bankId.value,
      accountId = accountId.value,
      viewId = viewId.value,
      transactionRequestType = transactionRequestType,
      currency = currency,
      userId = userId,
      username = username
    )

//    val r: Option[InboundChargeLevel] = process(req).extractOpt[InboundChargeLevel]
//    // Return result
//    val chargeValue = r match {
//      // Check does the response data match the requested data
//      case Some(x) => AmountOfMoney(x.currency, x.amount)
//      case _ => {
//        AmountOfMoney("EUR", "0.0001")
//      }
//    }
//    Full(chargeValue)
    LocalMappedConnector.getChargeLevel(
      bankId: BankId,
      accountId: AccountId,
      viewId: ViewId,
      userId: String,
      username: String,
      transactionRequestType: String,
      currency: String
    )
  }



//  messageDocs += MessageDoc(
//    process = "obp.validate.ChallengeAnswer",
//    messageFormat = messageFormat,
//    description = "validateChallengeAnswer from kafka ",
//    exampleOutboundMessage = decompose(
//      OutboundChallengeAnswerBase(
//        messageFormat = messageFormat,
//        action = "obp.validate.ChallengeAnswer",
//        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
//        username = "susan.uk.29@example.com",
//        challengeId = "1234",
//        hashOfSuppliedAnswer = ""
//      )
//    ),
//    exampleInboundMessage = decompose(
//      InboundValidateChallangeAnswer(
//        errorCode = "OBP-6001: ...",
//        answer = ""
//      )
//    )
//  )

  override def validateChallengeAnswer(
                                        challengeId: String,
                                        hashOfSuppliedAnswer: String
                                      ): Box[Boolean] = {
//    // Create argument list
//    val req = OutboundChallengeAnswerBase(
//      messageFormat = messageFormat,
//      action = "obp.validate.ChallengeAnswer",
//      userId = currentResourceUserId,
//      username = currentResourceUsername,
//      challengeId = challengeId,
//      hashOfSuppliedAnswer = hashOfSuppliedAnswer)

//    val r: Option[InboundValidateChallangeAnswer] = process(req).extractOpt[InboundValidateChallangeAnswer]
    // Return result
//    r match {
//       //Check does the response data match the requested data
//      case Some(x) => Full(x.answer.toBoolean)
//      case _ => Empty
//    }
    Full(true)
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

