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
import code.api.util.{APIUtil, ApiSession, ErrorMessages, SessionContext}
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
import net.liftweb.json.Extraction
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
  def getUsername = APIUtil.getGatewayLoginUsername
  def getCbsToken = APIUtil.getGatewayLoginCbsToken

  val authInfoExample = AuthInfo(userId = "userId", username = "username", cbsToken = "cbsToken")
  val inboundStatusMessagesExample = List(InboundStatusMessage("ESB", "Success", "0", "OK"))
  val errorCodeExample = "OBP-6001: ..."
  
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
  
    val box = for {
      kafkaMessage <- processToBox[OutboundGetAdapterInfo](req)
      inboundAdapterInfo <- tryo{kafkaMessage.extract[InboundAdapterInfo]} ?~! s"$InboundAdapterInfo extract error"
      inboundAdapterInfoInternal <- Full(inboundAdapterInfo.data)
    } yield{
      inboundAdapterInfoInternal
    }
    
    
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
      
      val req = OutboundGetUserByUsernamePassword(AuthInfo(currentResourceUserId, username, getCbsToken), password = password)
  
      logger.debug(s"Kafka getUser Req says:  is: $req")
  
      val box = for {
        kafkaMessage <- processToBox[OutboundGetUserByUsernamePassword](req)
        inboundGetUserByUsernamePassword <- tryo{kafkaMessage.extract[InboundGetUserByUsernamePassword]} ?~! s"$InboundGetUserByUsernamePassword extract error"
        inboundValidatedUser <- Full(inboundGetUserByUsernamePassword.data)
      } yield{
        inboundValidatedUser
      }
      
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
      val req = OutboundGetBanks(AuthInfo(currentResourceUserId, getUsername, getCbsToken))
      logger.info(s"Kafka getBanks Req is: $req")
      
      val box = for {
        kafkaMessage <- processToBox[OutboundGetBanks](req)
        inboundGetBanks <- tryo{kafkaMessage.extract[InboundGetBanks]} ?~! s"$InboundGetBanks extract error"
        inboundBanks <- Full(inboundGetBanks.data)
      } yield{
        inboundBanks
      }
      
      
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
        authInfo = AuthInfo(currentResourceUserId, getUsername, getCbsToken),
        bankId = bankId.toString
      )
      logger.debug(s"Kafka getBank Req says:  is: $req")
  
      val box = for {
        kafkaMessage <- processToBox[OutboundGetBank](req)
        inboundGetBank <- tryo{kafkaMessage.extract[InboundGetBank]} ?~! s"$InboundGetBank extract error"
        inboundBank <- Full(inboundGetBank.data)
      } yield{
        inboundBank
      }
      
      
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
      
      val req = OutboundGetAccounts(AuthInfo(currentResourceUserId, username, getCbsToken),callMfFlag,internalCustomers)
      logger.debug(s"Kafka getBankAccounts says: req is: $req")
  
      val box = for {
        kafkaMessage <- processToBox[OutboundGetAccounts](req)
        inboundGetAccounts <- tryo{kafkaMessage.extract[InboundGetAccounts]} ?~! s"$InboundGetAccounts extract error"
        inboundAccountJune2017 <- Full(inboundGetAccounts.data)
      } yield{
        inboundAccountJune2017
      }
      logger.debug(s"Kafka getBankAccounts says res is $box")
      
      box match {
        case Full(list) if (list.head.errorCode=="") =>
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
  override def getBankAccount(bankId: BankId, accountId: AccountId, session: Option[SessionContext]): Box[BankAccountJune2017] = saveConnectorMetric{
    memoizeSync(getAccountTTL millisecond){

      val (userName, cbs) = session match {
        case Some(c) =>
          c.gatewayLoginRequestPayload match {
            case Some(p) =>
              (p.login_user_name, p.cbs_token.getOrElse("")) // New Style Endpoints use SessionContext
            case _ =>
              (getUsername, getCbsToken) // Old Style Endpoints use S object
          }
        case _ =>
          (getUsername, getCbsToken) // Old Style Endpoints use S object
      }

      // Generate random uuid to be used as request-response match id
      val req = OutboundGetAccountbyAccountID(
        authInfo = AuthInfo(currentResourceUserId, userName, cbs),
        bankId = bankId.toString,
        accountId = accountId.value
      )
      logger.debug(s"Kafka getBankAccount says: req is: $req")
  
      val box = for {
        kafkaMessage <- processToBox[OutboundGetAccountbyAccountID](req)
        inboundGetAccountbyAccountID <- tryo{kafkaMessage.extract[InboundGetAccountbyAccountID]} ?~! s"$InboundGetAccountbyAccountID extract error"
        inboundAccountJune2017 <- Full(inboundGetAccountbyAccountID.data)
      } yield{
        inboundAccountJune2017
      }
      
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
    process = "obp.check.BankAccountExists",
    messageFormat = messageFormat,
    description = "checkBankAccountExists from kafka",
    exampleOutboundMessage = decompose(
      OutboundCheckBankAccountExists(
        authInfoExample,
        "bankId",
        "accountId"
      )
    ),
    exampleInboundMessage = decompose(
      InboundCheckBankAccountExists(
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
      )
    )
  )
  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, session: Option[SessionContext]): Box[BankAccountJune2017] = saveConnectorMetric{
    memoizeSync(getAccountTTL millisecond){
      val (userName, cbs) = session match {
        case Some(c) =>
          c.gatewayLoginRequestPayload match {
            case Some(p) =>
              (p.login_user_name, p.cbs_token.getOrElse("")) // New Style Endpoints use SessionContext
            case _ =>
              (getUsername, getCbsToken) // Old Style Endpoints use S object
          }
        case _ =>
          (getUsername, getCbsToken) // Old Style Endpoints use S object
      }

      // Generate random uuid to be used as request-response match id
      val req = OutboundCheckBankAccountExists(
        authInfo = AuthInfo(currentResourceUserId, userName, cbs),
        bankId = bankId.toString,
        accountId = accountId.value
      )
      logger.debug(s"Kafka checkBankAccountExists says: req is: $req")
      
      val box = for {
        kafkaMessage <- processToBox[OutboundCheckBankAccountExists](req)
        inboundCheckBankAccountExists <- tryo{kafkaMessage.extract[InboundCheckBankAccountExists]} ?~! s"$InboundCheckBankAccountExists extract error"
        inboundAccountJune2017 <- Full(inboundCheckBankAccountExists.data)
      } yield{
        inboundAccountJune2017
      }
      
      logger.debug(s"Kafka checkBankAccountExists says res is $box")
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
    process = "obp.get.coreBankAccounts",
    messageFormat = messageFormat,
    description = "getCoreBankAccounts from kafka",
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
  override def getCoreBankAccounts(BankIdAccountIds: List[BankIdAccountId]) : Box[List[CoreAccount]] = saveConnectorMetric{
    memoizeSync(getAccountTTL millisecond){
      
      val req = OutboundGetCoreBankAccounts(
        authInfo = AuthInfo(currentResourceUserId, getUsername, getCbsToken),
        BankIdAccountIds
      )
      logger.debug(s"Kafka getCoreBankAccounts says: req is: $req")
  
      val box = for {
        kafkaMessage <- processToBox[OutboundGetCoreBankAccounts](req)
        inboundGetCoreBankAccounts <- tryo{kafkaMessage.extract[InboundGetCoreBankAccounts]} ?~! s"$InboundGetCoreBankAccounts extract error"
        internalInboundCoreAccounts <- Full(inboundGetCoreBankAccounts.data)
      } yield{
        internalInboundCoreAccounts
      }
      logger.debug(s"Kafka getCoreBankAccounts says res is $box")
      
      box match {
        case Full(f) if (f.head.errorCode=="") =>
          Full(f.map( x => CoreAccount(x.id,x.label,x.bank_id,x.account_routing)))
        case Full(f) if (f.head.errorCode!="") =>
          Failure("INTERNAL-OBP-ADAPTER-xxx: "+ f.head.errorCode+". + CoreBank-Error:"+ f.head.backendMessages)
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
        case Failure(msg, e, c) =>
          Failure(msg, e, c)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
    }}("getBankAccounts")
  
  
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
         Nil
      ))
  )


  // TODO Get rid on these param lookups and document.
  override def getTransactions(bankId: BankId, accountId: AccountId, session: Option[SessionContext], queryParams: OBPQueryParam*): Box[List[Transaction]] = {
    val limit: OBPLimit = queryParams.collect { case OBPLimit(value) => OBPLimit(value) }.headOption.get
    val offset = queryParams.collect { case OBPOffset(value) => OBPOffset(value) }.headOption.get
    val fromDate = queryParams.collect { case OBPFromDate(date) => OBPFromDate(date) }.headOption.get
    val toDate = queryParams.collect { case OBPToDate(date) => OBPToDate(date)}.headOption.get
    val ordering = queryParams.collect {
      //we don't care about the intended sort field and only sort on finish date for now
      case OBPOrdering(field, direction) => OBPOrdering(field, direction)}.headOption.get
    val optionalParams = Seq(limit, offset, fromDate, toDate, ordering)

    val (userName, cbs) = session match {
      case Some(c) =>
        c.gatewayLoginRequestPayload match {
          case Some(p) =>
            (p.login_user_name, p.cbs_token.getOrElse("")) // New Style Endpoints use SessionContext
          case _ =>
            (getUsername, getCbsToken) // Old Style Endpoints use S object
        }
      case _ =>
        (getUsername, getCbsToken) // Old Style Endpoints use S object
    }
    
    val req = OutboundGetTransactions(
      authInfo = AuthInfo(userId = currentResourceUserId, username = userName, cbsToken = cbs),
      bankId = bankId.toString,
      accountId = accountId.value,
      limit = limit.value,
      fromDate = fromDate.value.toString,
      toDate = toDate.value.toString
    )
    logger.debug(s"Kafka getTransactions says: req is: $req")
  
    val box = for {
      kafkaMessage <- processToBox[OutboundGetTransactions](req)
      inboundGetTransactions <- tryo{kafkaMessage.extract[InboundGetTransactions]} ?~! s"$InboundGetTransactions extract error"
      internalTransactionJune2017s <- Full(inboundGetTransactions.data)
    } yield{
      internalTransactionJune2017s
    }
    logger.debug(s"Kafka getTransactions says: res is: $box")
    
    box match {
      case Full(list) if (list.head.errorCode=="")  =>
        // Check does the response data match the requested data
        val isCorrect = list.forall(x => x.accountId == accountId.value && x.bankId == bankId.value)
        if (!isCorrect) throw new Exception(ErrorMessages.InvalidConnectorResponseForGetTransactions)
        // Populate fields and generate result
        val bankAccount = checkBankAccountExists(BankId(list.head.bankId), AccountId(list.head.accountId), session)
        
        val res = for {
          r: InternalTransaction <- list
          thisBankAccount <- bankAccount ?~! ErrorMessages.BankAccountNotFound
          transaction: Transaction <- createNewTransaction(thisBankAccount,r)
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
        null
      ))
  )
  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Box[Transaction] = {
    
    val req =  OutboundGetTransaction(
      authInfo = AuthInfo(currentResourceUserId, getUsername, getCbsToken),
      bankId = bankId.toString,
      accountId = accountId.value,
      transactionId = transactionId.toString)
    logger.debug(s"Kafka getTransaction Req says:  is: $req")
    
    val box = for {
      kafkaMessage <- processToBox[OutboundGetTransaction](req)
      inboundGetTransaction <- tryo{kafkaMessage.extract[InboundGetTransaction]} ?~! s"$InboundGetTransaction extract error"
      internalTransactionJune2017 <- Full(inboundGetTransaction.data)
    } yield{
      internalTransactionJune2017
    }
    logger.debug(s"Kafka getTransaction Res says: is: $box")
    
    box match {
      // Check does the response data match the requested data
      case Full(x) if (transactionId.value != x.transactionId && x.errorCode=="") =>
        Failure(ErrorMessages.InvalidConnectorResponseForGetTransaction, Empty, Empty)
      case Full(x) if (transactionId.value == x.transactionId && x.errorCode=="") =>
        for {
          bankAccount <- checkBankAccountExists(BankId(x.bankId), AccountId(x.accountId)) ?~! ErrorMessages.BankAccountNotFound
          transaction: Transaction <- createNewTransaction(bankAccount,x)
        } yield {
          transaction
        }
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
      authInfo = AuthInfo(currentResourceUserId, getUsername, getCbsToken),
      bankId = bankId.value,
      accountId = accountId.value,
      userId = userId,
      username = AuthUser.getCurrentUserUsername,
      transactionRequestType = transactionRequestType.value,
      transactionRequestId = transactionRequestId
    )
    logger.debug(s"Kafka createChallenge Req says:  is: $req")
    
    val box = for {
      kafkaMessage <- processToBox[OutboundCreateChallengeJune2017](req)
      inboundCreateChallengeJune2017 <- tryo{kafkaMessage.extract[InboundCreateChallengeJune2017]} ?~! s"$InboundCreateChallengeJune2017 extract error"
      internalCreateChallengeJune2017 <- Full(inboundCreateChallengeJune2017.data)
    } yield{
      internalCreateChallengeJune2017
    }
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
      InboundCreateCounterparty(
        authInfoExample,
        InternalCounterparty(
          errorCode= errorCodeExample,
          backendMessages = inboundStatusMessagesExample,
          createdByUserId= "String",
          name= "String",
          thisBankId= "String",
          thisAccountId= "String",
          thisViewId= "String",
          counterpartyId= "String",
          otherAccountRoutingScheme= "String",
          otherAccountRoutingAddress= "String",
          otherBankRoutingScheme= "String",
          otherBankRoutingAddress= "String",
          otherBranchRoutingScheme= "String",
          otherBranchRoutingAddress= "String",
          isBeneficiary = false,
          description= "String",
          otherAccountSecondaryRoutingScheme= "String",
          otherAccountSecondaryRoutingAddress= "String",
          bespoke =  List(PostCounterpartyBespoke(
            key = "String",
            value = "String"
          ))
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
      authInfo = AuthInfo(currentResourceUserId, getUsername, getCbsToken),
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
  
    val box = for {
      kafkaMessage <- processToBox[OutboundCreateCounterparty](req)
      inboundCreateCounterparty <- tryo{kafkaMessage.extract[InboundCreateCounterparty]} ?~! s"$InboundCreateCounterparty extract error"
      internalCounterparty <- Full(inboundCreateCounterparty.data)
    } yield{
      internalCounterparty
    }
    logger.debug(s"Kafka createCounterparty Res says: is: $box")
    
    val res: Box[CounterpartyTrait] = box match {
      case Full(x) if (x.errorCode=="")  =>
        Full(x)
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
      authInfo = AuthInfo(currentResourceUserId, getUsername, getCbsToken),
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
    
    val box = for {
      kafkaMessage <- processToBox[OutboundGetTransactionRequests210](req)
      inboundGetTransactionRequests210 <- tryo{kafkaMessage.extract[InboundGetTransactionRequests210]} ?~! s"$InboundGetTransactionRequests210 extract error"
      internalGetTransactionRequests <- Full(inboundGetTransactionRequests210.data)
    } yield{
      internalGetTransactionRequests
    }
    logger.debug(s"Kafka createCounterparty Res says: is: $box")
  
    val res: Box[List[TransactionRequest]] = box match {
      case Full(x) if (x.errorCode=="")  =>
        Full(x.transactionRequests)
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
    process = "obp.get.counterparties",
    messageFormat = messageFormat,
    description = "getCounterparties from kafka ",
    exampleOutboundMessage = decompose(
      OutboundGetCounterparties(
        authInfoExample,
        InternalOutboundGetCounterparties(
          thisBankId = "String",
          thisAccountId = "String",
          viewId = "String"
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
      authInfo = AuthInfo(currentResourceUserId, getUsername, getCbsToken),
      counterparty = InternalOutboundGetCounterparties(
        thisBankId = thisBankId.value,
        thisAccountId = thisAccountId.value,
        viewId = viewId.value
      )
    )
    logger.debug(s"Kafka getCounterparties Req says: is: $req")
    
    val box = for {
      kafkaMessage <- processToBox[OutboundGetCounterparties](req)
      inboundGetCounterparties <- tryo{kafkaMessage.extract[InboundGetCounterparties]} ?~! s"$InboundGetCounterparties extract error"
      internalCounterparties <- Full(inboundGetCounterparties.data)
    } yield{
      internalCounterparties
    }
    logger.debug(s"Kafka getCounterparties Res says: is: $box")
  
    val res: Box[List[CounterpartyTrait]] = box match {
      case Full(x) if (x.head.errorCode=="")  =>
        Full(x)
      case Full(x) if (x.head.errorCode!="") =>
        Failure("INTERNAL-OBP-ADAPTER-xxx: "+ x.head.errorCode+". + CoreBank-Error:"+ x.head.backendMessages)
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
    process = "obp.get.CounterpartyByCounterpartyId",
    messageFormat = messageFormat,
    description = "getCounterpartyByCounterpartyId from kafka ",
    exampleOutboundMessage = Extraction.decompose(
      OutboundGetCounterpartyByCounterpartyId(
        authInfoExample,
        OutboundGetCounterpartyById(
          thisBankId = "String",
          thisAccountId = "String",
          viewId = "String",
          counterpartyId = "String"
        )
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundGetCounterparty(
        authInfoExample,
        InternalCounterparty(
          errorCodeExample,
          inboundStatusMessagesExample,
          createdByUserId = "String",
          name = "String",
          thisBankId = "String",
          thisAccountId = "String",
          thisViewId = "String",
          counterpartyId = "String",
          otherAccountRoutingScheme = "String",
          otherAccountRoutingAddress = "String",
          otherBankRoutingScheme = "String",
          otherBankRoutingAddress = "String",
          otherBranchRoutingScheme = "String",
          otherBranchRoutingAddress = "String",
          isBeneficiary = true,
          description = "String",
          otherAccountSecondaryRoutingScheme = "String",
          otherAccountSecondaryRoutingAddress = "String",
          bespoke = Nil
        )
      )
    )
  )
  
  override def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId): Box[CounterpartyTrait] = {
    val req = OutboundGetCounterpartyByCounterpartyId(authInfo = AuthInfo(currentResourceUserId, getUsername, getCbsToken),OutboundGetCounterpartyById("","","",counterpartyId.value))
    logger.debug(s"Kafka getCounterpartyByCounterpartyId Req says: is: $req")
  
    val box = for {
      kafkaMessage <- processToBox[OutboundGetCounterpartyByCounterpartyId](req)
      inboundGetCustomersByUserIdFuture <- tryo{kafkaMessage.extract[InboundGetCounterparty]} ?~! s"$InboundGetCustomersByUserId extract error"
      internalCustomer <- Full(inboundGetCustomersByUserIdFuture.data)
    } yield{
      internalCustomer
    }
    logger.debug(s"Kafka getCounterpartyByCounterpartyId Res says: is: $box")
  
    val res = box match {
      case Full(x) if (x.errorCode=="")  =>
        Full(x)
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
    process = "obp.get.CustomersByUserIdBox",
    messageFormat = messageFormat,
    description = "getCustomersByUserIdBox from kafka ",
    exampleOutboundMessage = decompose(
      OutboundGetCustomersByUserId(
        authInfoExample
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetCustomersByUserId(
        authInfoExample,
        Nil
      )
    )
  )
  
  override def getCustomersByUserIdBox(userId: String)(session: Option[SessionContext]): Box[List[Customer]] =  {
    
    val payloadOfJwt = ApiSession.getGatawayLoginRequestInfo(session)
    val req = OutboundGetCustomersByUserId(
      authInfo =
        AuthInfo(
          currentResourceUserId,
          payloadOfJwt.login_user_name,
          payloadOfJwt.cbs_token.getOrElse(""),
          payloadOfJwt.is_first
        )
    )
    logger.debug(s"Kafka getCustomersByUserIdFuture Req says: is: $req")
    
    val box = for {
      kafkaMessage <- processToBox[OutboundGetCustomersByUserId](req)
      inboundGetCustomersByUserIdFuture <- tryo{kafkaMessage.extract[InboundGetCustomersByUserId]} ?~! s"$InboundGetCustomersByUserId extract error"
      internalCustomer <- Full(inboundGetCustomersByUserIdFuture.data)
    } yield{
      internalCustomer
    }
    logger.debug(s"Kafka getCustomersByUserIdFuture Res says: is: $box")
    
    val res: Box[List[InternalCustomer]] = box match {
      case Full(x) if (x.head.errorCode=="")  =>
        Full(x)
      case Full(x) if (x.head.errorCode!="") =>
        Failure("INTERNAL-OBP-ADAPTER-xxx: "+ x.head.errorCode+". + CoreBank-Error:"+ x.head.backendMessages)
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
  def createNewTransaction(thisAccount: BankAccount,r: InternalTransaction): Box[Transaction] = {
    var datePosted: Date = null
    if (r.postedDate != null) // && r.details.posted.matches("^[0-9]{8}$"))
      datePosted = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(r.postedDate)

    var dateCompleted: Date = null
    if (r.completedDate != null) // && r.details.completed.matches("^[0-9]{8}$"))
      dateCompleted = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(r.completedDate)

    for {
      counterpartyId <- tryo {r.counterpartyId}
      counterpartyName <- tryo {r.counterpartyName}
      thisAccount <- tryo {thisAccount}
      //creates a dummy OtherBankAccount without an OtherBankAccountMetadata, which results in one being generated (in OtherBankAccount init)
      dummyOtherBankAccount <- tryo {createCounterparty(counterpartyId, counterpartyName, thisAccount, None)}
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
  def createCounterparty(counterpartyId: String, counterpartyName: String, o: BankAccount, alreadyFoundMetadata: Option[CounterpartyMetadata]) = {

    // TODO Remove the counterPartyId input parameter since we are not using it.
    // TODO Fix dummy values.

    new Counterparty(
      counterPartyId = alreadyFoundMetadata.map(_.metadataId).getOrElse(""),
      label = counterpartyName,
      nationalIdentifier = null,
      otherBankRoutingAddress = None,
      otherAccountRoutingAddress = None,
      thisAccountId = AccountId(counterpartyId),
      thisBankId = BankId(null),
      kind = null,
      otherBankId = o.bankId,
      otherAccountId = o.accountId,
      alreadyFoundMetadata = alreadyFoundMetadata,
      name = null,
      otherBankRoutingScheme = null,
      otherAccountRoutingScheme = null,
      otherAccountProvider = null,
      isBeneficiary = false
    )
  }

}


object KafkaMappedConnector_vJune2017 extends KafkaMappedConnector_vJune2017{
  
}