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

import java.lang
import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import code.api.util.APIUtil.{MessageDoc, saveConnectorMetric}
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, ApiSession, ErrorMessages, SessionContext}
import code.api.v2_1_0.PostCounterpartyBespoke
import code.bankconnectors._
import code.bankconnectors.vMar2017._
import code.customer._
import code.kafka.KafkaHelper
import code.metadata.counterparties.CounterpartyTrait
import code.model._
import code.model.dataAccess._
import code.transactionrequests.TransactionRequests.{TransactionRequest, TransactionRequestAccount, TransactionRequestBody, TransactionRequestChallenge, TransactionRequestCharge}
import code.util.Helper.MdcLoggable
import code.api.util.APIUtil.getSecondsCache
import com.google.common.cache.CacheBuilder
import net.liftweb.common.{Box, _}
import net.liftweb.json.Extraction
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers.tryo
import scala.collection.immutable.{List, Nil, Seq}
import scala.collection.mutable.ArrayBuffer
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
  val bankTTL = getSecondsCache("getBanks")
  val banksTTL = getSecondsCache("getBanks")
  val userTTL = getSecondsCache("getUser")
  val accountTTL = getSecondsCache("getAccount")
  val accountsTTL = getSecondsCache("getAccounts")
  val transactionTTL = getSecondsCache("getTransaction")
  val transactionsTTL = getSecondsCache("getTransactions")
  val transactionRequests210TTL = getSecondsCache("getTransactionRequests210")
  val counterpartiesTTL = getSecondsCache("getCounterparties")
  val counterpartyByCounterpartyIdTTL = getSecondsCache("getCounterpartyByCounterpartyId")
  val customersByUserIdBoxTTL = getSecondsCache("getCustomersByUserIdBox")
  val memoryCounterpartyTTL = getSecondsCache("createMemoryCounterparty")
  val memoryTransactionTTL = getSecondsCache("createMemoryTransaction") 
  
  
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
  val errorCodeExample = "INTERNAL-OBP-ADAPTER-6001: ..."
  
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
        Failure("INTERNAL-"+ list.errorCode+". + CoreBank-Status:"+ list.backendMessages)
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
    memoizeSync(userTTL second) {
      
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
          Failure("INTERNAL-"+ list.errorCode+". + CoreBank-Status:"+ list.backendMessages)
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
    memoizeSync(banksTTL second){
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
          Failure("INTERNAL-"+ list.head.errorCode+". + CoreBank-Status:"+ list.head.backendMessages)
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
    memoizeSync(bankTTL second){
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
          Failure("INTERNAL-"+ list.errorCode+". + CoreBank-Status:"+ list.backendMessages)
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
          accountRoutingAddress = "accountRoutingAddress",
          accountRules = Nil
        ) :: Nil)
    )
  )
  override def getBankAccounts(username: String, callMfFlag: Boolean): Box[List[InboundAccountJune2017]] = saveConnectorMetric {
    memoizeSync(accountsTTL second) {
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
          Failure("INTERNAL-"+ list.head.errorCode+". + CoreBank-Status:"+ list.head.backendMessages)
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
          errorCodeExample,
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
          accountRoutingAddress = "accountRoutingAddress",
          accountRules = Nil
        )
      ))
  )
  override def getBankAccount(bankId: BankId, accountId: AccountId, session: Option[SessionContext]): Box[BankAccountJune2017] = saveConnectorMetric{
    memoizeSync(accountTTL second){

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
          Failure("INTERNAL-"+ f.errorCode+". + CoreBank-Status:"+ f.backendMessages)
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
          errorCodeExample,
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
          accountRoutingAddress = "accountRoutingAddress",
          accountRules = Nil
        )
      )
    )
  )
  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, session: Option[SessionContext]): Box[BankAccountJune2017] = saveConnectorMetric{
    memoizeSync(accountTTL second){
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
          Failure("INTERNAL-"+ f.errorCode+". + CoreBank-Status:"+ f.backendMessages)
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
          errorCodeExample,
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
          accountRoutingAddress = "accountRoutingAddress",
          accountRules = Nil
        )
      ))
  )
  override def getCoreBankAccounts(BankIdAccountIds: List[BankIdAccountId], session: Option[SessionContext]) : Box[List[CoreAccount]] = saveConnectorMetric{
    memoizeSync(accountTTL second){

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
      
      val req = OutboundGetCoreBankAccounts(
        authInfo = AuthInfo(currentResourceUserId, userName, cbs),
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
          Failure("INTERNAL-"+ f.head.errorCode+". + CoreBank-Status:"+ f.head.backendMessages)
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
        InternalTransaction(
          errorCodeExample,
          inboundStatusMessagesExample,
          transactionId = "String",
          accountId = "String",
          amount = "String",
          bankId = "String",
          completedDate = "String",
          counterpartyId = "String",
          counterpartyName = "String",
          currency = "String",
          description = "String",
          newBalanceAmount = "String",
          newBalanceCurrency = "String",
          postedDate = "String",
          `type` = "String",
          userId = "String"
        )::Nil
      ))
  )
  // TODO Get rid on these param lookups and document.
  override def getTransactions(bankId: BankId, accountId: AccountId, session: Option[SessionContext], queryParams: OBPQueryParam*): Box[List[Transaction]] = saveConnectorMetric{
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
      
    //Note: because there is `queryParams: OBPQueryParam*` in getTransactions, so create the getTransactionsCached to cache data.
    def getTransactionsCached(req:OutboundGetTransactions): Box[List[Transaction]] = memoizeSync(transactionsTTL second){
      logger.debug(s"Kafka getTransactions says: req is: $req")
      val box = for {
        kafkaMessage <- processToBox[OutboundGetTransactions](req)
        inboundGetTransactions <- tryo{kafkaMessage.extract[InboundGetTransactions]} ?~! s"$InvalidConnectorResponseForGetTransactions $InboundGetTransactions extract error"
        internalTransactions <- Full(inboundGetTransactions.data)
      } yield{
        internalTransactions
      }
      logger.debug(s"Kafka getTransactions says: res is: $box")

      box match {
        case Full(list) if (list.head.errorCode!="") =>
          Failure("INTERNAL-"+ list.head.errorCode+". + CoreBank-Status:"+ list.head.backendMessages)
        case Full(internalTransactions) if (!internalTransactions.forall(x => x.accountId == accountId.value && x.bankId == bankId.value))  =>
          Failure(InvalidConnectorResponseForGetTransactions)
        case Full(internalTransactions) if (internalTransactions.head.errorCode=="") =>
          val bankAccount = checkBankAccountExists(BankId(internalTransactions.head.bankId), AccountId(internalTransactions.head.accountId), session)
    
          val res = for {
            internalTransaction <- internalTransactions
            thisBankAccount <- bankAccount ?~! ErrorMessages.BankAccountNotFound
            transaction <- createMemoryTransaction(thisBankAccount,internalTransaction)
          } yield {
            transaction
          }
          Full(res)
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse)
        case Failure(msg, e, c) =>
          Failure(msg, e, c)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
    }
    
    getTransactionsCached(req)
    
  }("getTransactions")
  
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
          errorCodeExample,
          inboundStatusMessagesExample,
          transactionId = "String",
          accountId = "String",
          amount = "String",
          bankId = "String",
          completedDate = "String",
          counterpartyId = "String",
          counterpartyName = "String",
          currency = "String",
          description = "String",
          newBalanceAmount = "String",
          newBalanceCurrency = "String",
          postedDate = "String",
          `type` = "String",
          userId = "String"
        )
      )
    )
  )
  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Box[Transaction] = saveConnectorMetric{memoizeSync(transactionTTL second){
    val req =  OutboundGetTransaction(
      authInfo = AuthInfo(currentResourceUserId, getUsername, getCbsToken),
      bankId = bankId.toString,
      accountId = accountId.value,
      transactionId = transactionId.toString)
    logger.debug(s"Kafka getTransaction Req says:  is: $req")
    
    val box = for {
      kafkaMessage <- processToBox[OutboundGetTransaction](req)
      inboundGetTransaction <- tryo{kafkaMessage.extract[InboundGetTransaction]} ?~! s"$InvalidConnectorResponseForGetTransaction $InboundGetTransaction extract error"
      internalTransaction <- Full(inboundGetTransaction.data)
    } yield{
      internalTransaction
    }
    logger.debug(s"Kafka getTransaction Res says: is: $box")
    
    box match {
      // Check does the response data match the requested data
      case Full(x) if (transactionId.value != x.transactionId) =>
        Failure(s"$InvalidConnectorResponseForGetTransaction")
      case Full(x) if (x.errorCode!="") =>
        Failure("INTERNAL-"+ x.errorCode+". + CoreBank-Status:"+ x.backendMessages)
      case Full(internalTransaction) if (transactionId.value == internalTransaction.transactionId && internalTransaction.errorCode=="") =>
        for {
          bankAccount <- checkBankAccountExists(BankId(internalTransaction.bankId), AccountId(internalTransaction.accountId)) ?~! ErrorMessages.BankAccountNotFound
          transaction: Transaction <- createMemoryTransaction(bankAccount,internalTransaction)
        } yield {
          transaction
        }
      case Full(x) if (x.errorCode!="") =>
        Failure("INTERNAL-"+ x.errorCode+". + CoreBank-Status:"+ x.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }}("getTransaction")
  
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
        Failure("INTERNAL-"+ x.errorCode+". + CoreBank-Status:"+ x.backendMessages)
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
          errorCodeExample,
          inboundStatusMessagesExample,
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
        Failure("INTERNAL-"+ x.errorCode+". + CoreBank-Status:"+ x.backendMessages)
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
          List(TransactionRequest(
            id = TransactionRequestId("id"),
            `type` = "String",
            from = TransactionRequestAccount("10", "12"),
            details = null,
            body = TransactionRequestBody(
              TransactionRequestAccount("", ""),
              AmountOfMoney("ILS", "0"), ""
            ),
            transaction_ids = "",
            status = "COMPLETED",
            start_date = exampleDate,
            end_date = exampleDate,
            challenge = TransactionRequestChallenge("", 0, ""),
            charge = TransactionRequestCharge(
              "", 
              AmountOfMoney("ILS", "0")
            ),
            charge_policy = "",
            counterparty_id = CounterpartyId(""),
            name = "name",
            this_bank_id = BankId("10"),
            this_account_id = AccountId("1"),
            this_view_id = ViewId(""),
            other_account_routing_scheme = "",
            other_account_routing_address = "",
            other_bank_routing_scheme = "",
            other_bank_routing_address = "",
            is_beneficiary = false
          )
          )
        )
      )
    )
  )
  override def getTransactionRequests210(user : User, fromAccount : BankAccount) : Box[List[TransactionRequest]] = saveConnectorMetric{memoizeSync(transactionRequests210TTL second){
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
    logger.debug(s"Kafka getTransactionRequests210 Req says: is: $req")
    
    val box = for {
      kafkaMessage <- processToBox[OutboundGetTransactionRequests210](req)
      inboundGetTransactionRequests210 <- tryo{kafkaMessage.extract[InboundGetTransactionRequests210]} ?~! s"$InvalidConnectorResponseForGetTransactionRequests210, $InboundGetTransactionRequests210 extract error"
      internalGetTransactionRequests <- Full(inboundGetTransactionRequests210.data)
    } yield{
      internalGetTransactionRequests
    }
    logger.debug(s"Kafka getTransactionRequests210 Res says: is: $box")
  
    val res: Box[List[TransactionRequest]] = box match {
      case Full(x) if (x.errorCode=="")  =>
        //For consistency with sandbox mode, we need combine obp transactions in database and adapter transactions  
        for{
          adapterTransactionRequests <- Full(x.transactionRequests)
          //TODO, this will cause performance issue, we need limit the number of transaction requests. 
          obpTransactionRequests <- LocalMappedConnector.getTransactionRequestsImpl210(fromAccount) ?~! s"$ConnectorEmptyResponse, error on LocalMappedConnector.getTransactionRequestsImpl210"
        } yield {
          adapterTransactionRequests ::: obpTransactionRequests
        }
      case Full(x) if (x.errorCode!="") =>
        Failure("INTERNAL-"+ x.errorCode+". + CoreBank-Status:"+ x.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    res
  }}("getTransactionRequests210")
  
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
  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId,viewId :ViewId): Box[List[CounterpartyTrait]] = saveConnectorMetric{memoizeSync(counterpartiesTTL second){
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
        Failure("INTERNAL-"+ x.head.errorCode+". + CoreBank-Status:"+ x.head.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    res
  }}("getCounterparties")
  
  messageDocs += MessageDoc(
    process = "obp.get.CounterpartyByCounterpartyId",
    messageFormat = messageFormat,
    description = "getCounterpartyByCounterpartyId from kafka ",
    exampleOutboundMessage = Extraction.decompose(
      OutboundGetCounterpartyByCounterpartyId(
        authInfoExample,
        OutboundGetCounterpartyById(
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
  override def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId): Box[CounterpartyTrait] = saveConnectorMetric{memoizeSync(counterpartyByCounterpartyIdTTL second){
    val req = OutboundGetCounterpartyByCounterpartyId(authInfo = AuthInfo(currentResourceUserId, getUsername, getCbsToken),OutboundGetCounterpartyById(counterpartyId.value))
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
        Failure("INTERNAL-"+ x.errorCode+". + CoreBank-Status:"+ x.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    res
  }}("getCounterpartyByCounterpartyId")
  
  
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
        InternalCustomer(
          status = "String",
          errorCodeExample,
          inboundStatusMessagesExample,
          customerId = "String",
          bankId = "String",
          number = "String",  
          legalName = "String",
          mobileNumber = "String",
          email = "String",
          faceImage = CustomerFaceImage(date = exampleDate, url = "String"),
          dateOfBirth = exampleDate,
          relationshipStatus= "String",
          dependents = 1,
          dobOfDependents = List(exampleDate),
          highestEducationAttained= "String",
          employmentStatus= "String",
          creditRating = CreditRating(rating ="String", source = "String"),
          creditLimit=  CreditLimit(currency ="String", amount= "String"),
          kycStatus = false,
          lastOkDate = exampleDate
        )::Nil
      )
    )
  )
  override def getCustomersByUserIdBox(userId: String)(session: Option[SessionContext]): Box[List[Customer]] = saveConnectorMetric{memoizeSync(customersByUserIdBoxTTL second){
    
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
    logger.debug(s"Kafka getCustomersByUserIdBox Req says: is: $req")
    
    val box = for {
      kafkaMessage <- processToBox[OutboundGetCustomersByUserId](req)
      inboundGetCustomersByUserIdFuture <- tryo{kafkaMessage.extract[InboundGetCustomersByUserId]} ?~! s"$InboundGetCustomersByUserId extract error"
      internalCustomer <- Full(inboundGetCustomersByUserIdFuture.data)
    } yield{
      internalCustomer
    }
    logger.debug(s"Kafka getCustomersByUserIdBox Res says: is: $box")
    
    val res: Box[List[InternalCustomer]] = box match {
      case Full(x) if (x.head.errorCode=="")  =>
        Full(x)
      case Full(x) if (x.head.errorCode!="") =>
        Failure("INTERNAL-"+ x.head.errorCode+". + CoreBank-Status:"+ x.head.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    res
  }}("getCustomersByUserIdBox")
  
  
  /////////////////////////////////////////////////////////////////////////////
  // Helper for creating a transaction
  def createMemoryTransaction(bankAccount: BankAccount,internalTransaction: InternalTransaction): Box[Transaction] =  memoizeSync(memoryTransactionTTL second){
    for {
      datePosted <- tryo {new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(internalTransaction.postedDate)} ?~!s"$InvalidConnectorResponseForGetTransaction Wrong posteDate format should be yyyyMMdd, current is ${internalTransaction.postedDate}"
      dateCompleted <- tryo {new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(internalTransaction.completedDate)} ?~!s"$InvalidConnectorResponseForGetTransaction Wrong completedDate format should be yyyyMMdd, current is ${internalTransaction.completedDate}"
      counterpartyName <- tryo {internalTransaction.counterpartyName}?~!s"$InvalidConnectorResponseForGetTransaction. Can not get counterpartyName from Adapter. "

      //creates a dummy OtherBankAccount without an OtherBankAccountMetadata, which results in one being generated (in OtherBankAccount init)
      dummyOtherBankAccount <- createMemoryCounterparty(counterpartyName, bankAccount, None)
    
      //and create the proper OtherBankAccount with the correct "id" attribute set to the metadataId of the OtherBankAccountMetadata object
      //note: as we are passing in the OtherBankAccountMetadata we don't incur another db call to get it in OtherBankAccount init
      counterparty <- createMemoryCounterparty(counterpartyName, bankAccount, Some(dummyOtherBankAccount.metadata))
    } yield {
      // Create new transaction
      new Transaction(
        internalTransaction.transactionId, // uuid:String
        TransactionId(internalTransaction.transactionId), // id:TransactionId
        bankAccount, // thisAccount:BankAccount
        counterparty, // otherAccount:OtherBankAccount
        internalTransaction.`type`, // transactionType:String
        BigDecimal(internalTransaction.amount), // val amount:BigDecimal
        bankAccount.currency, // currency:String
        Some(internalTransaction.description), // description:Option[String]
        datePosted, // startDate:Date
        dateCompleted, // finishDate:Date
        BigDecimal(internalTransaction.newBalanceAmount) // balance:BigDecimal)
      )
    }
  }

  // Helper for creating other bank account, this will not create it in database, only in scala code.
  //Note, we have a method called createCounterparty in this connector, so named it here. 
  def createMemoryCounterparty(counterpartyName: String, bankAccount: BankAccount, alreadyFoundMetadata: Option[CounterpartyMetadata]): Box[Counterparty] =  memoizeSync(memoryCounterpartyTTL second){
    Full(
      new Counterparty(
        // We can only get following 4 fields from Adapter. 
        // Here we only have one bankAccount, but we need a `dummyOtherBankAccount` for metaData. 
        // so we use the same account for both `Counterparty.thisAccountId` and `Counterparty.otherAccountId`. 
        //these define the counterparty
        label = counterpartyName, // This will be used mapping `MappedCounterpartyMetadata.holder`
        thisAccountId = bankAccount.accountId,  //This will be used mapping `MappedCounterpartyMetadata.accountNumber`
        
        // these define the obp account to which this counterparty belongs
        otherBankId = bankAccount.bankId,   //This will be used mapping MappedCounterpartyMetadata.thisBankId
        otherAccountId = bankAccount.accountId, // This will be used mapping MappedCounterpartyMetadata.thisAccountId
        
        //This two will be generated in obp side implicitly
        alreadyFoundMetadata = alreadyFoundMetadata,
        counterPartyId = alreadyFoundMetadata.map(_.metadataId).getOrElse(""),
        
        thisBankId = BankId(null),
        nationalIdentifier = null,
        otherBankRoutingAddress = None,
        otherAccountRoutingAddress = None,
        kind = null,
        name = null,
        otherBankRoutingScheme = null,
        otherAccountRoutingScheme = null,
        otherAccountProvider = null,
        isBeneficiary = true
      )
    )
  }

}


object KafkaMappedConnector_vJune2017 extends KafkaMappedConnector_vJune2017{
  
}