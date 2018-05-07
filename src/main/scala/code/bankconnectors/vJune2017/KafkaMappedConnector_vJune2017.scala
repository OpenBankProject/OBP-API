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
import code.api.util.APIUtil.{MessageDoc, getSecondsCache, saveConnectorMetric}
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, ApiSession, CallContext, ErrorMessages}
import code.atms.Atms.{AtmId, AtmT}
import code.bankconnectors._
import code.bankconnectors.vMar2017._
import code.branches.Branches.{BranchId, BranchT, Lobby}
import code.common._
import code.customer._
import code.kafka.KafkaHelper
import code.metadata.counterparties.CounterpartyTrait
import code.model._
import code.model.dataAccess._
import code.transactionrequests.TransactionRequests._
import code.util.Helper.MdcLoggable
import com.google.common.cache.CacheBuilder
import com.sksamuel.avro4s.SchemaFor
import net.liftweb.common.{Box, _}
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{Extraction, MappingException, parse}
import net.liftweb.util.Helpers.tryo
import scalacache.ScalaCache
import scalacache.guava.GuavaCache
import scalacache.memoization.{cacheKeyExclude, memoizeSync}

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait KafkaMappedConnector_vJune2017 extends Connector with KafkaHelper with MdcLoggable {
  
//  override type AccountType = BankAccountJune2017
  
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
  val counterpartyTrait = getSecondsCache("getCounterpartyTrait")
  val customersByUserIdBoxTTL = getSecondsCache("getCustomersByUserIdBox")
  val memoryCounterpartyTTL = getSecondsCache("createMemoryCounterparty")
  val memoryTransactionTTL = getSecondsCache("createMemoryTransaction") 
  val branchesTTL = getSecondsCache("getBranches") 
  val branchTTL = getSecondsCache("getBranch")
  val atmsTTL = getSecondsCache("getAtms")
  val atmTTL = getSecondsCache("getAtm")


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
  val statusExample = Status(errorCodeExample, inboundStatusMessagesExample)
  
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
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetAdapterInfo]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundAdapterInfoInternal]().toString(true)))
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
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetUserByUsernamePassword]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundGetUserByUsernamePassword]().toString(true)))
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
        Status(
          errorCode = errorCodeExample,
          inboundStatusMessagesExample),
        InboundBank(
          bankId = "gh.29.uk",
          name = "sushan",
          logo = "TESOBE",
          url = "https://tesobe.com/"
        )  :: Nil
      )
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetBanks]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundGetBanks]().toString(true)))
  )
  override def getBanks(): Box[List[Bank]] = saveConnectorMetric {
    memoizeSync(banksTTL second){
      val req = OutboundGetBanks(AuthInfo(currentResourceUserId, getUsername, getCbsToken))
      logger.info(s"Kafka getBanks Req is: $req")
      
      val box: Box[(List[InboundBank], Status)] = for {
        kafkaMessage <- processToBox[OutboundGetBanks](req)
        inboundGetBanks <- tryo{kafkaMessage.extract[InboundGetBanks]} ?~! s"$InboundGetBanks extract error"
        (inboundBanks, status) <- Full(inboundGetBanks.data, inboundGetBanks.status)
      } yield {
        (inboundBanks, status)
      }

      logger.debug(s"Kafka getBanks Res says:  is: $Box")
      val res = box match {
        case Full((banks, status)) if (status.errorCode=="") =>
          Full(banks map (new Bank2(_)))
        case Full((banks, status)) if (status.errorCode!="") =>
          Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
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
        Status(
          errorCodeExample,
          inboundStatusMessagesExample),
        InboundBank(
          bankId = "gh.29.uk",
          name = "sushan",
          logo = "TESOBE",
          url = "https://tesobe.com/"
        )
      )
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetBank]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundGetBank]().toString(true)))
  )
  override def getBank(bankId: BankId): Box[Bank] =  saveConnectorMetric {
    memoizeSync(bankTTL second){
      val req = OutboundGetBank(
        authInfo = AuthInfo(currentResourceUserId, getUsername, getCbsToken),
        bankId = bankId.toString
      )
      logger.debug(s"Kafka getBank Req says:  is: $req")
  
      val box: Box[(InboundBank, Status)] = for {
        kafkaMessage <- processToBox[OutboundGetBank](req)
        inboundGetBank <- tryo{kafkaMessage.extract[InboundGetBank]} ?~! s"$InboundGetBank extract error"
        (inboundBank, status) <- Full(inboundGetBank.data, inboundGetBank.status)
      } yield {
        (inboundBank, status)
      }
      
      
      logger.debug(s"Kafka getBank Res says:  is: $Box") 
      
      box match {
        case Full((bank, status)) if (status.errorCode=="") =>
          Full((new Bank2(bank)))
        case Full((_, status)) if (status.errorCode!="") =>
          Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
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
      InboundGetAccounts(authInfoExample, statusExample, InboundAccountJune2017("", cbsToken ="cbsToken", bankId = "gh.29.uk", branchId = "222", accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0", accountNumber = "123", accountType = "AC", balanceAmount = "50", balanceCurrency = "EUR", owners = "Susan" :: " Frank" :: Nil, viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil, bankRoutingScheme = "iban", bankRoutingAddress = "bankRoutingAddress", branchRoutingScheme = "branchRoutingScheme", branchRoutingAddress = " branchRoutingAddress", accountRoutingScheme = "accountRoutingScheme", accountRoutingAddress = "accountRoutingAddress", accountRouting = Nil, accountRules = Nil) :: Nil)
    )
  )
  override def getBankAccounts(username: String, forceFresh: Boolean): Box[List[InboundAccountJune2017]] = saveConnectorMetric {
    memoizeSync(accountsTTL second) {
      val customerList :List[Customer]= Customer.customerProvider.vend.getCustomersByUserId(currentResourceUserId)
      val internalCustomers = JsonFactory_vJune2017.createCustomersJson(customerList)

      val req = OutboundGetAccounts(AuthInfo(currentResourceUserId, username, getCbsToken),forceFresh,internalCustomers)
      logger.debug(s"Kafka getBankAccounts says: req is: $req")
  
      val box = for {
        kafkaMessage <- processToBox[OutboundGetAccounts](req)
        inboundGetAccounts <- tryo{kafkaMessage.extract[InboundGetAccounts]} ?~! s"$InboundGetAccounts extract error"
        (inboundAccountJune2017, status) <- Full(inboundGetAccounts.data, inboundGetAccounts.status)
      } yield{
        (inboundAccountJune2017, status)
      }
      logger.debug(s"Kafka getBankAccounts says res is $box")
      
      box match {
        case Full((data, status)) if (status.errorCode=="") =>
          Full(data)
        case Full((data, status)) if (status.errorCode!="") =>
          Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
        case Failure(msg, e, c) =>
          Failure(msg, e, c)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
    }}("getBankAccounts")

  override def getBankAccountsFuture(username: String, forceFresh: Boolean): Future[Box[List[InboundAccountJune2017]]] = saveConnectorMetric {
    memoizeSync(accountsTTL second) {
      val customerList :List[Customer]= Customer.customerProvider.vend.getCustomersByUserId(currentResourceUserId)
      val internalCustomers = JsonFactory_vJune2017.createCustomersJson(customerList)

      val req = OutboundGetAccounts(AuthInfo(currentResourceUserId, username, getCbsToken),forceFresh,internalCustomers)
      logger.debug(s"Kafka getBankAccountsFuture says: req is: $req")

      val future = for {
        res <- processToFuture[OutboundGetAccounts](req) map {
          f =>
            try {
              f.extract[InboundGetAccounts]
            } catch {
              case e: Exception => throw new MappingException(s"$InboundGetAccounts extract error", e)
            }
        } map {
          (x => (x.data, x.status))
        }
      } yield {
        res
      }
      logger.debug(s"Kafka getBankAccounts says res is $future")

      future map {
        case (List(), status) =>
          Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
        case (data, status) if (status.errorCode=="") =>
          Full(data)
        case (data, status) if (status.errorCode!="") =>
          Failure("INTERNAL-OBP-ADAPTER-xxx: "+ status.errorCode+". + CoreBank-Error:"+ status.backendMessages)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
    }}("getBankAccountsFuture")
  
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
        statusExample,
        Some(InboundAccountJune2017("", cbsToken = "cbsToken", bankId = "gh.29.uk", branchId = "222", accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0", accountNumber = "123", accountType = "AC", balanceAmount = "50", balanceCurrency = "EUR", owners = "Susan" :: " Frank" :: Nil, viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil, bankRoutingScheme = "iban", bankRoutingAddress = "bankRoutingAddress", branchRoutingScheme = "branchRoutingScheme", branchRoutingAddress = " branchRoutingAddress", accountRoutingScheme = "accountRoutingScheme", accountRoutingAddress = "accountRoutingAddress", accountRouting = Nil, accountRules = Nil))))
  )
  override def getBankAccount(bankId: BankId, accountId: AccountId, @cacheKeyExclude session: Option[CallContext]): Box[BankAccount] = saveConnectorMetric {
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
        (inboundAccountJune2017, status) <- Full(inboundGetAccountbyAccountID.data, inboundGetAccountbyAccountID.status)
      } yield{
        (inboundAccountJune2017, status)
      }
      
      logger.debug(s"Kafka getBankAccount says res is $box")
      box match {
        case Full((Some(data), status)) if (status.errorCode=="") =>
          Full(new BankAccountJune2017(data))
        case Full((data, status)) if (status.errorCode!="") =>
          Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
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
        statusExample,
        Some(InboundAccountJune2017("", cbsToken = "cbsToken", bankId = "gh.29.uk", branchId = "222", accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0", accountNumber = "123", accountType = "AC", balanceAmount = "50", balanceCurrency = "EUR", owners = "Susan" :: " Frank" :: Nil, viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil, bankRoutingScheme = "iban", bankRoutingAddress = "bankRoutingAddress", branchRoutingScheme = "branchRoutingScheme", branchRoutingAddress = " branchRoutingAddress", accountRoutingScheme = "accountRoutingScheme", accountRoutingAddress = "accountRoutingAddress", accountRouting = Nil, accountRules = Nil)))
    )
  )
  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, @cacheKeyExclude session: Option[CallContext]): Box[BankAccount] = saveConnectorMetric {
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
        (inboundAccountJune2017, status) <- Full(inboundCheckBankAccountExists.data, inboundCheckBankAccountExists.status)
      } yield{
        (inboundAccountJune2017, status)
      }
      
      logger.debug(s"Kafka checkBankAccountExists says res is $box")
      box match {
        case Full((Some(data), status)) if (status.errorCode=="") =>
          Full(new BankAccountJune2017(data))
        case Full((data,status)) if (status.errorCode!="") =>
          Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
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
        statusExample, 
        Some(InboundAccountJune2017("", cbsToken = "cbsToken", bankId = "gh.29.uk", branchId = "222", accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0", accountNumber = "123", accountType = "AC", balanceAmount = "50", balanceCurrency = "EUR", owners = "Susan" :: " Frank" :: Nil, viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil, bankRoutingScheme = "iban", bankRoutingAddress = "bankRoutingAddress", branchRoutingScheme = "branchRoutingScheme", branchRoutingAddress = " branchRoutingAddress", accountRoutingScheme = "accountRoutingScheme", accountRoutingAddress = "accountRoutingAddress", accountRouting = Nil, accountRules = Nil))))
  )
  override def getCoreBankAccounts(BankIdAccountIds: List[BankIdAccountId], @cacheKeyExclude session: Option[CallContext]) : Box[List[CoreAccount]] = saveConnectorMetric{
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

  override def getCoreBankAccountsFuture(BankIdAccountIds: List[BankIdAccountId], @cacheKeyExclude session: Option[CallContext]) : Future[Box[List[CoreAccount]]] = saveConnectorMetric{
    memoizeSync(accountsTTL second){

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
      logger.debug(s"Kafka getCoreBankAccountsFuture says: req is: $req")

      val future = for {
        res <- processToFuture[OutboundGetCoreBankAccounts](req) map {
          f =>
            try {
              f.extract[InboundGetCoreBankAccounts]
            } catch {
              case e: Exception => throw new MappingException(s"$InboundGetCoreBankAccounts extract error", e)
            }
        } map {
          _.data
        }
      } yield {
        res
      }
      logger.debug(s"Kafka getCoreBankAccountsFuture says res is $future")

      future map {
        case List() =>
          Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
        case list if (list.head.errorCode=="") =>
          Full(list.map( x => CoreAccount(x.id,x.label,x.bank_id,x.account_routing)))
        case list if (list.head.errorCode!="") =>
          Failure("INTERNAL-OBP-ADAPTER-xxx: "+ list.head.errorCode+". + CoreBank-Error:"+ list.head.backendMessages)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
    }}("getCoreBankAccountsFuture")
  
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
        statusExample,
        InternalTransaction_vJune2017(
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
          userId = "String")::Nil))
  )
  // TODO Get rid on these param lookups and document.
  override def getTransactions(bankId: BankId, accountId: AccountId, session: Option[CallContext], queryParams: OBPQueryParam*): Box[List[Transaction]] = saveConnectorMetric{
    val limit = queryParams.collect { case OBPLimit(value) => value}.headOption.getOrElse(100)
    val fromDate = queryParams.collect { case OBPFromDate(date) => date.toString}.headOption.getOrElse(dateformat.parse("3049-01-01").toString)
    val toDate = queryParams.collect { case OBPToDate(date) => date.toString}.headOption.getOrElse(new Date(0).toString)

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
      limit = limit,
      fromDate = fromDate,
      toDate = toDate
    )
      
    //Note: because there is `queryParams: OBPQueryParam*` in getTransactions, so create the getTransactionsCached to cache data.
    def getTransactionsCached(req:OutboundGetTransactions): Box[List[Transaction]] = memoizeSync(transactionsTTL second){
      logger.debug(s"Kafka getTransactions says: req is: $req")
      val box = for {
        kafkaMessage <- processToBox[OutboundGetTransactions](req)
        inboundGetTransactions <- tryo{kafkaMessage.extract[InboundGetTransactions]} ?~! s"$InvalidConnectorResponseForGetTransactions $InboundGetTransactions extract error"
        (internalTransactions, status) <- Full(inboundGetTransactions.data, inboundGetTransactions.status)
      } yield{
        (internalTransactions, status)
      }
      logger.debug(s"Kafka getTransactions says: res is: $box")

      box match {
        case Full((data, status)) if (status.errorCode!="") =>
          Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
        case Full((data, status)) if (!data.forall(x => x.accountId == accountId.value && x.bankId == bankId.value))  =>
          Failure(InvalidConnectorResponseForGetTransactions)
        case Full((data, status)) if (status.errorCode=="") =>
          val bankAccount = checkBankAccountExists(BankId(data.head.bankId), AccountId(data.head.accountId), session)
    
          val res = for {
            internalTransaction <- data
            thisBankAccount <- bankAccount ?~! ErrorMessages.BankAccountNotFound
            transaction <- createInMemoryTransaction(thisBankAccount,internalTransaction)
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
  
  override def getTransactionsCore(bankId: BankId, accountId: AccountId, session: Option[CallContext], queryParams: OBPQueryParam*): Box[List[TransactionCore]] = saveConnectorMetric{
    val limit = queryParams.collect { case OBPLimit(value) => value}.headOption.getOrElse(100)
    val fromDate = queryParams.collect { case OBPFromDate(date) => date.toString}.headOption.getOrElse(dateformat.parse("3049-01-01").toString)
    val toDate = queryParams.collect { case OBPToDate(date) => date.toString}.headOption.getOrElse(new Date(0).toString)
  
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
      limit = limit,
      fromDate = fromDate,
      toDate = toDate
    )
    
    //Note: because there is `queryParams: OBPQueryParam*` in getTransactions, so create the getTransactionsCoreCached to cache data.
    //Note: getTransactionsCoreCached and getTransactionsCached have the same parameters,but the different method name.
    def getTransactionsCoreCached(req:OutboundGetTransactions): Box[List[TransactionCore]] = memoizeSync(transactionsTTL second){
      logger.debug(s"Kafka getTransactions says: req is: $req")
      val box = for {
        kafkaMessage <- processToBox[OutboundGetTransactions](req)
        inboundGetTransactions <- tryo{kafkaMessage.extract[InboundGetTransactions]} ?~! s"$InvalidConnectorResponseForGetTransactions $InboundGetTransactions extract error"
        (internalTransactions, status) <- Full(inboundGetTransactions.data, inboundGetTransactions.status)
      } yield{
        (internalTransactions, status)
      }
      logger.debug(s"Kafka getTransactions says: res is: $box")
      
      box match {
        case Full((data, status)) if (status.errorCode!="") =>
          Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
        case Full((data, status)) if (!data.forall(x => x.accountId == accountId.value && x.bankId == bankId.value))  =>
          Failure(InvalidConnectorResponseForGetTransactions)
        case Full((data, status)) if (status.errorCode=="") =>
          val bankAccount = checkBankAccountExists(BankId(data.head.bankId), AccountId(data.head.accountId), session)
          
          val res = for {
            internalTransaction <- data
            thisBankAccount <- bankAccount ?~! ErrorMessages.BankAccountNotFound
            transaction <- createInMemoryTransactionCore(thisBankAccount,internalTransaction)
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
    
    getTransactionsCoreCached(req)
    
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
      InboundGetTransaction(authInfoExample, statusExample, Some(InternalTransaction_vJune2017(
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
              )))
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
      (internalTransaction, status) <- Full(inboundGetTransaction.data, inboundGetTransaction.status)
    } yield{
      (internalTransaction, status)
    }
    logger.debug(s"Kafka getTransaction Res says: is: $box")
    
    box match {
      // Check does the response data match the requested data
      case Full((Some(data), status)) if (transactionId.value != data.transactionId) =>
        Failure(s"$InvalidConnectorResponseForGetTransaction")
      case Full((data,status)) if (status.errorCode!="") =>
        Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
      case Full((Some(data), status)) if (transactionId.value == data.transactionId && status.errorCode=="") =>
        for {
          bankAccount <- checkBankAccountExists(BankId(data.bankId), AccountId(data.accountId)) ?~! ErrorMessages.BankAccountNotFound
          transaction: Transaction <- createInMemoryTransaction(bankAccount,data)
        } yield {
          transaction
        }
      case Full((data,status)) if (status.errorCode!="") =>
        Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
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
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundCreateChallengeJune2017]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundCreateChallengeJune2017]().toString(true)))
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
          bespoke = CounterpartyBespoke("key","value") ::Nil
        )
      )
    ),
    exampleInboundMessage = decompose(
      InboundCreateCounterparty(
        authInfoExample, 
        statusExample,
        Some(InternalCounterparty(
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
          bespoke =  List(CounterpartyBespoke(
                          key = "String",
                          value = "String"
                        )))))
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
    bespoke: List[CounterpartyBespoke]
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
        bespoke: List[CounterpartyBespoke]
      )
    )
    logger.debug(s"Kafka createCounterparty Req says: is: $req")
  
    val box = for {
      kafkaMessage <- processToBox[OutboundCreateCounterparty](req)
      inboundCreateCounterparty <- tryo{kafkaMessage.extract[InboundCreateCounterparty]} ?~! s"$InboundCreateCounterparty extract error"
      (internalCounterparty, status) <- Full(inboundCreateCounterparty.data, inboundCreateCounterparty.status)
    } yield{
      (internalCounterparty, status)
    }
    logger.debug(s"Kafka createCounterparty Res says: is: $box")
    
    val res: Box[CounterpartyTrait] = box match {
      case Full((Some(data), status)) if (status.errorCode=="")  =>
        Full(data)
      case Full((data, status)) if (status.errorCode!="") =>
        Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
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
        statusExample,
        List(
          TransactionRequest(
          id = TransactionRequestId("id"),
          `type` = "String",
          from = TransactionRequestAccount("10", "12"),
          body = SwaggerDefinitionsJSON.transactionRequestBodyAllTypes,
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
          is_beneficiary = false)
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
        (internalGetTransactionRequests, status) <- Full(inboundGetTransactionRequests210.data, inboundGetTransactionRequests210.status)
    } yield{
      (internalGetTransactionRequests, status)
    }
    logger.debug(s"Kafka getTransactionRequests210 Res says: is: $box")
  
    val res: Box[List[TransactionRequest]] = box match {
      case Full((data, status)) if (status.errorCode=="")  =>
        //For consistency with sandbox mode, we need combine obp transactions in database and adapter transactions  
        for{
          adapterTransactionRequests <- Full(data)
          //TODO, this will cause performance issue, we need limit the number of transaction requests. 
          obpTransactionRequests <- LocalMappedConnector.getTransactionRequestsImpl210(fromAccount) ?~! s"$ConnectorEmptyResponse, error on LocalMappedConnector.getTransactionRequestsImpl210"
        } yield {
          adapterTransactionRequests ::: obpTransactionRequests
        }
      case Full((data, status)) if (status.errorCode!="") =>
        Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
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
      InboundGetCounterparties(authInfoExample, statusExample,
        InternalCounterparty(
          createdByUserId = "",
          name = "",
          thisBankId = "",
          thisAccountId = "",
          thisViewId = "",
          counterpartyId = "",
          otherAccountRoutingScheme = "",
          otherAccountRoutingAddress = "",
          otherBankRoutingScheme = "",
          otherBankRoutingAddress = "",
          otherBranchRoutingScheme = "",
          otherBranchRoutingAddress = "",
          isBeneficiary = true,
          description = "",
          otherAccountSecondaryRoutingScheme = "",
          otherAccountSecondaryRoutingAddress = "",
          bespoke =  List(
            CounterpartyBespoke(key = "key", value = "value"))
        ) :: Nil
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
      (internalCounterparties, status) <- Full(inboundGetCounterparties.data, inboundGetCounterparties.status)
    } yield{
      (internalCounterparties, status)
    }
    logger.debug(s"Kafka getCounterparties Res says: is: $box")
  
    val res: Box[List[CounterpartyTrait]] = box match {
      case Full((data, status)) if (status.errorCode=="")  =>
        Full(data)
      case Full((data, status)) if (status.errorCode!="") =>
        Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
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
      InboundGetCounterparty(authInfoExample, statusExample, Some(InternalCounterparty(createdByUserId = "String", name = "String", thisBankId = "String", thisAccountId = "String", thisViewId = "String", counterpartyId = "String", otherAccountRoutingScheme = "String", otherAccountRoutingAddress = "String", otherBankRoutingScheme = "String", otherBankRoutingAddress = "String", otherBranchRoutingScheme = "String", otherBranchRoutingAddress = "String", isBeneficiary = true, description = "String", otherAccountSecondaryRoutingScheme = "String", otherAccountSecondaryRoutingAddress = "String", bespoke = Nil)))
    )
  )
  override def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId): Box[CounterpartyTrait] = saveConnectorMetric{memoizeSync(counterpartyByCounterpartyIdTTL second){
    val req = OutboundGetCounterpartyByCounterpartyId(authInfo = AuthInfo(currentResourceUserId, getUsername, getCbsToken),OutboundGetCounterpartyById(counterpartyId.value))
    logger.debug(s"Kafka getCounterpartyByCounterpartyId Req says: is: $req")
  
    val box = for {
      kafkaMessage <- processToBox[OutboundGetCounterpartyByCounterpartyId](req)
      inboundGetCustomersByUserIdFuture <- tryo{kafkaMessage.extract[InboundGetCounterparty]} ?~! s"$InboundGetCustomersByUserId extract error"
      (internalCustomer, status) <- Full(inboundGetCustomersByUserIdFuture.data, inboundGetCustomersByUserIdFuture.status)
    } yield{
      (internalCustomer, status)
    }
    logger.debug(s"Kafka getCounterpartyByCounterpartyId Res says: is: $box")
  
    val res = box match {
      case Full((Some(data), status)) if (status.errorCode=="")  =>
        Full(data)
      case Full((data, status)) if (status.errorCode!="") =>
        Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    res
  }}("getCounterpartyByCounterpartyId")


  override def getCounterpartyTrait(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[CounterpartyTrait] = saveConnectorMetric{memoizeSync(counterpartyTrait second){
    val req = OutboundGetCounterparty(authInfo = AuthInfo(currentResourceUserId, getUsername, getCbsToken), thisBankId.value, thisAccountId.value, couterpartyId)
    logger.debug(s"Kafka getCounterpartyTrait Req says: is: $req")

    val box = for {
      kafkaMessage <- processToBox[OutboundGetCounterparty](req)
      inboundGetCounterparty <- tryo{kafkaMessage.extract[InboundGetCounterparty]} ?~! s"$InboundGetCounterparty extract error"
      (data, status) <- Full(inboundGetCounterparty.data, inboundGetCounterparty.status)
    } yield{
      (data, status)
    }
    logger.debug(s"Kafka getCounterpartyTrait Res says: is: $box")

    val res = box match {
      case Full((Some(data), status)) if (status.errorCode=="")  =>
        Full(data)
      case Full((data, status)) if (status.errorCode!="") =>
        Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    res
  }}("getCounterpartyTrait")
  
  
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
        statusExample,
        InternalCustomer(customerId = "String", bankId = "String", number = "String", legalName = "String", mobileNumber = "String", email = "String", faceImage = CustomerFaceImage(date = exampleDate, url = "String"), dateOfBirth = exampleDate, relationshipStatus= "String", dependents = 1, dobOfDependents = List(exampleDate), highestEducationAttained= "String", employmentStatus= "String", creditRating = CreditRating(rating ="String", source = "String"), creditLimit=  CreditLimit(currency ="String", amount= "String"), kycStatus = false, lastOkDate = exampleDate)::Nil
      )
    ),
    outboundAvroSchema = None,
    inboundAvroSchema = None
  )

  override def getCustomersByUserIdFuture(userId: String)(@cacheKeyExclude session: Option[CallContext]): Future[Box[List[Customer]]] = saveConnectorMetric{ memoizeSync(customersByUserIdBoxTTL second) {

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

    val future = for {
      res <- processToFuture[OutboundGetCustomersByUserId](req) map {
        f =>
          try {
            f.extract[InboundGetCustomersByUserId]
          } catch {
            case e: Exception => throw new MappingException(s"$InboundGetCustomersByUserId extract error", e)
          }
      } map {x => (x.data, x.status)}
    } yield{
      res
    }
    logger.debug(s"Kafka getCustomersByUserIdFuture Res says: is: $future")

    val res = future map {
      case (List(),status) =>
        Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
      case (list, status) if (status.errorCode=="") =>
        Full(list)
      case (list, status) if (status.errorCode!="") =>
        Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:" + status.backendMessages)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    res
  }}("getCustomersByUserIdFuture")
  
  
  /////////////////////////////////////////////////////////////////////////////
  // Helper for creating a transaction
  def createInMemoryTransaction(bankAccount: BankAccount,internalTransaction: InternalTransaction_vJune2017): Box[Transaction] =  memoizeSync(memoryTransactionTTL second){
    for {
      datePosted <- tryo {new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(internalTransaction.postedDate)} ?~!s"$InvalidConnectorResponseForGetTransaction Wrong posteDate format should be yyyyMMdd, current is ${internalTransaction.postedDate}"
      dateCompleted <- tryo {new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(internalTransaction.completedDate)} ?~!s"$InvalidConnectorResponseForGetTransaction Wrong completedDate format should be yyyyMMdd, current is ${internalTransaction.completedDate}"
      
      counterpartyName <- tryo {internalTransaction.counterpartyName}?~!s"$InvalidConnectorResponseForGetTransaction. Can not get counterpartyName from Adapter. "
      counterpartyId <- Full(APIUtil.createImplicitCounterpartyId(bankAccount.bankId.value, bankAccount.accountId.value, counterpartyName))
      counterparty <- createInMemoryCounterparty(bankAccount, counterpartyName, counterpartyId)
      
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
 
  def createInMemoryTransactionCore(bankAccount: BankAccount,internalTransaction: InternalTransaction_vJune2017): Box[TransactionCore] =  memoizeSync(memoryTransactionTTL second){
    for {
      datePosted <- tryo {new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(internalTransaction.postedDate)} ?~!s"$InvalidConnectorResponseForGetTransaction Wrong posteDate format should be yyyyMMdd, current is ${internalTransaction.postedDate}"
      dateCompleted <- tryo {new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(internalTransaction.completedDate)} ?~!s"$InvalidConnectorResponseForGetTransaction Wrong completedDate format should be yyyyMMdd, current is ${internalTransaction.completedDate}"
      counterpartyCore <- Full(CounterpartyCore(
        counterpartyId = APIUtil.createImplicitCounterpartyId(bankAccount.bankId.value, bankAccount.accountId.value, internalTransaction.counterpartyName),
        counterpartyName = internalTransaction.counterpartyName,
        kind = null,
        thisBankId = BankId(""),
        thisAccountId = AccountId(""),
        otherBankRoutingScheme = "",
        otherBankRoutingAddress = None,
        otherAccountRoutingScheme =  "",
        otherAccountRoutingAddress = None,
        otherAccountProvider = "",
        isBeneficiary = true
      ))
    } yield {
      // Create new transaction
       TransactionCore(
        TransactionId(internalTransaction.transactionId), // id:TransactionId
        bankAccount, // thisAccount:BankAccount
        counterpartyCore, // otherAccount:OtherBankAccount
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
  def createInMemoryCounterparty(bankAccount: BankAccount, counterpartyName: String, counterpartyId: String): Box[Counterparty] =  memoizeSync(memoryCounterpartyTTL second){
    Full(
      Counterparty(
        thisBankId = BankId(bankAccount.bankId.value),
        thisAccountId = bankAccount.accountId,
        counterpartyId = counterpartyId,
        counterpartyName = counterpartyName, 
        
        otherBankRoutingAddress = None,
        otherAccountRoutingAddress = None,
        otherBankRoutingScheme = null,
        otherAccountRoutingScheme = null,
        otherAccountProvider = null,
        isBeneficiary = true,
          
        kind = null,
        nationalIdentifier = null
      )
    )
  }

  messageDocs += MessageDoc(
    process = "obp.get.Branches",
    messageFormat = messageFormat,
    description = "getBranches",
    exampleOutboundMessage = decompose(
      OutboundGetBranches(authInfoExample,"bankid")
    ),
    exampleInboundMessage = decompose(
      InboundGetBranches(
        authInfoExample,
        Status("",
        inboundStatusMessagesExample),
        InboundBranchVJune2017(
          branchId = BranchId(""),
          bankId = BankId(""),
          name = "",
          address =  Address(line1 = "",
            line2 = "",
            line3 = "",
            city = "",
            county = Some(""),
            state = "",
            postCode = "",
            //ISO_3166-1_alpha-2
            countryCode = ""),
          location = Location(11,11, None,None),
          lobbyString = None,
          driveUpString = None,
          meta = Meta(License("","")),
          branchRouting = None,
          lobby = Some(Lobby(monday = List(OpeningTimes("","")),
            tuesday = List(OpeningTimes("","")),
            wednesday = List(OpeningTimes("","")),
            thursday = List(OpeningTimes("","")),
            friday = List(OpeningTimes("","")),
            saturday = List(OpeningTimes("","")),
            sunday = List(OpeningTimes("",""))
          )),
          driveUp = None,
          // Easy access for people who use wheelchairs etc.
          isAccessible = Some(true),
          accessibleFeatures = None,
          branchType  = Some(""),
          moreInfo = Some(""),
          phoneNumber = Some("")
        )  :: Nil
      )

    )
  )

  override def getBranchesFuture(bankId: BankId, queryParams: OBPQueryParam*): Future[Box[List[InboundBranchVJune2017]]] = saveConnectorMetric {
    memoizeSync(branchesTTL millisecond){
      val req = OutboundGetBranches(AuthInfo(currentResourceUserId, getUsername, getCbsToken), bankId.toString)
      logger.info(s"Kafka getBranchesFuture Req is: $req")

      val future: Future[(List[InboundBranchVJune2017], Status)] = for {
        res <- processToFuture[OutboundGetBranches](req) map {
          f =>
            try {
              f.extract[InboundGetBranches]
            } catch {
              case e: Exception => throw new MappingException(s"$InboundGetBranches extract error", e)
            }
        } map {
          d => (d.data, d.status)
        }
      } yield {
        res
      }

      logger.debug(s"Kafka getBranchFuture Res says:  is: $future")
      future map {
        case (branches, status) if (status.errorCode=="") =>
          Full(branches)
        case (_, status) if (status.errorCode!="") =>
          Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
    }
  }("getBranchesFuture")

  messageDocs += MessageDoc(
    process = "obp.get.Branch",
    messageFormat = messageFormat,
    description = "getBranch",
    exampleOutboundMessage = decompose(
      OutboundGetBranch(authInfoExample,"bankid", "branchid")
    ),
    exampleInboundMessage = decompose(
      InboundGetBranch(
        authInfoExample,
        Status("",
          inboundStatusMessagesExample),
        Some(InboundBranchVJune2017(
          branchId = BranchId(""),
          bankId = BankId(""),
          name = "",
          address =  Address(line1 = "",
            line2 = "",
            line3 = "",
            city = "",
            county = Some(""),
            state = "",
            postCode = "",
            //ISO_3166-1_alpha-2
            countryCode = ""),
          location = Location(11,11, None,None),
          lobbyString = None,
          driveUpString = None,
          meta = Meta(License("","")),
          branchRouting = None,
          lobby = Some(Lobby(monday = List(OpeningTimes("","")),
            tuesday = List(OpeningTimes("","")),
            wednesday = List(OpeningTimes("","")),
            thursday = List(OpeningTimes("","")),
            friday = List(OpeningTimes("","")),
            saturday = List(OpeningTimes("","")),
            sunday = List(OpeningTimes("",""))
          )),
          driveUp = None,
          // Easy access for people who use wheelchairs etc.
          isAccessible = Some(true),
          accessibleFeatures = None,
          branchType  = Some(""),
          moreInfo = Some(""),
          phoneNumber = Some("")
        ))
      )

    )
  )

  override def getBranch(bankId : BankId, branchId: BranchId) : Box[BranchT] = saveConnectorMetric {
    
    logger.debug("Enter getBranch for: " + branchId)
    memoizeSync(branchTTL millisecond){
      val req = OutboundGetBranch(AuthInfo(currentResourceUserId, getUsername, getCbsToken), bankId.toString, branchId.toString)
      logger.info(s"Kafka getBranch Req is: $req")

      val box = for {
        kafkaMessage <- processToBox[OutboundGetBranch](req)
        inboundGetBranch <- tryo{kafkaMessage.extract[InboundGetBranch]} ?~! s"$InboundGetBranch extract error"
          (inboundBranch, status) <- Full(inboundGetBranch.data, inboundGetBranch.status)
      } yield{
        (inboundBranch, status)
      }


      logger.debug(s"Kafka getBranch Res says:  is: $Box")
      val res = box match {
        case Full((Some(branch), status)) if (status.errorCode=="") =>
          Full(branch)
        case Full((_, status)) if (status.errorCode!="") =>
          Failure("INTERNAL-OBP-ADAPTER-xxx: "+ status.errorCode+". + CoreBank-Error:"+ status.backendMessages)
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse)
        case Failure(msg, e, c) =>
          Failure(msg, e, c)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
      logger.info(s"Kafka getBranch says res is $res")
      res
    }
  }("getBranch")

  override def getBranchFuture(bankId : BankId, branchId: BranchId) : Future[Box[BranchT]] = saveConnectorMetric {

    logger.debug("Enter getBranch for: " + branchId)
    memoizeSync(branchTTL millisecond){
      val req = OutboundGetBranch(AuthInfo(currentResourceUserId, getUsername, getCbsToken), bankId.toString, branchId.toString)
      logger.info(s"Kafka getBranchFuture Req is: $req")

      val future: Future[(Option[InboundBranchVJune2017], Status)] = for {
        res <- processToFuture[OutboundGetBranch](req) map {
          f =>
            try {
              f.extract[InboundGetBranch]
            } catch {
              case e: Exception => throw new MappingException(s"$InboundGetBranch extract error", e)
            }
        } map {
          d => (d.data, d.status)
        }
      } yield {
        res
      }

      logger.debug(s"Kafka getBranchFuture Res says:  is: $future")
      future map {
        case (Some(branch), status) if (status.errorCode=="") =>
          Full(branch)
        case (_, status) if (status.errorCode!="") =>
          Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
    }
  }("getBranchFuture")


  messageDocs += MessageDoc(
    process = "obp.get.Atms",
    messageFormat = messageFormat,
    description = "getAtms",
    exampleOutboundMessage = decompose(
      OutboundGetAtms(authInfoExample,"bankid")
    ),
    exampleInboundMessage = decompose(
      InboundGetAtms(
        authInfoExample,
        Status(errorCodeExample, inboundStatusMessagesExample),
        InboundAtmJune2017(
          atmId = AtmId("333"),
          bankId = BankId("10"),
          name = "",
          address =  Address(line1 = "",
            line2 = "",
            line3 = "",
            city = "",
            county = Some(""),
            state = "",
            postCode = "",
            //ISO_3166-1_alpha-2
            countryCode = ""),
          location = Location(11,11, None,None),
          meta = Meta(License(id = "pddl", name = "Open Data Commons Public Domain Dedication and License (PDDL)")),
          OpeningTimeOnMonday = Some(""),
          ClosingTimeOnMonday = Some(""),

          OpeningTimeOnTuesday = Some(""),
          ClosingTimeOnTuesday = Some(""),

          OpeningTimeOnWednesday = Some(""),
          ClosingTimeOnWednesday = Some(""),

          OpeningTimeOnThursday = Some(""),
          ClosingTimeOnThursday = Some(""),

          OpeningTimeOnFriday = Some(""),
          ClosingTimeOnFriday = Some(""),

          OpeningTimeOnSaturday  = Some(""),
          ClosingTimeOnSaturday = Some(""),

          OpeningTimeOnSunday = Some(""),
          ClosingTimeOnSunday = Some(""),
          isAccessible = Some(true),

          locatedAt = Some(""),
          moreInfo = Some(""),
          hasDepositCapability = Some(true)
        )  :: Nil
      )

    )
  )

  override def getAtmsFuture(bankId: BankId, queryParams: OBPQueryParam*): Future[Box[List[InboundAtmJune2017]]] = saveConnectorMetric {
    memoizeSync(atmsTTL millisecond){
      val req = OutboundGetAtms(AuthInfo(currentResourceUserId, getUsername, getCbsToken), bankId.value)
      logger.info(s"Kafka getAtmsFuture Req is: $req")

      val future = for {
        res <- processToFuture[OutboundGetAtms](req) map {
          f =>
            try {
              f.extract[InboundGetAtms]
            } catch {
              case e: Exception => throw new MappingException(s"$InboundGetAtms extract error", e)
            }
        } map {
          d => (d.data, d.status)
        }
      } yield {
        res
      }

      logger.debug(s"Kafka getAtmsFuture Res says:  is: $future")
      future map {
        case (atms, status) if (status.errorCode=="") =>
          Full(atms)
        case (_, status) if (status.errorCode!="") =>
          Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
    }
  }("getAtmsFuture")

  messageDocs += MessageDoc(
    process = "obp.get.Atm",
    messageFormat = messageFormat,
    description = "getAtm",
    exampleOutboundMessage = decompose(
      OutboundGetAtm(authInfoExample,"bankId", "atmId")
    ),
    exampleInboundMessage = decompose(
      InboundGetAtm(
        authInfoExample,
        Status(errorCodeExample, inboundStatusMessagesExample),
        Some(InboundAtmJune2017(
          atmId = AtmId("333"),
          bankId = BankId("10"),
          name = "",
          address =  Address(line1 = "",
            line2 = "",
            line3 = "",
            city = "",
            county = Some(""),
            state = "",
            postCode = "",
            //ISO_3166-1_alpha-2
            countryCode = ""),
          location = Location(11,11, None,None),
          meta = Meta(License(id = "pddl", name = "Open Data Commons Public Domain Dedication and License (PDDL)")),
          OpeningTimeOnMonday = Some(""),
          ClosingTimeOnMonday = Some(""),

          OpeningTimeOnTuesday = Some(""),
          ClosingTimeOnTuesday = Some(""),

          OpeningTimeOnWednesday = Some(""),
          ClosingTimeOnWednesday = Some(""),

          OpeningTimeOnThursday = Some(""),
          ClosingTimeOnThursday = Some(""),

          OpeningTimeOnFriday = Some(""),
          ClosingTimeOnFriday = Some(""),

          OpeningTimeOnSaturday  = Some(""),
          ClosingTimeOnSaturday = Some(""),

          OpeningTimeOnSunday = Some(""),
          ClosingTimeOnSunday = Some(""),
          isAccessible = Some(true),

          locatedAt = Some(""),
          moreInfo = Some(""),
          hasDepositCapability = Some(true)
        )
      ))

    )
  )

  override def getAtm(bankId : BankId, atmId: AtmId) : Box[AtmT] = saveConnectorMetric {
    memoizeSync(atmTTL millisecond){
      val req = OutboundGetAtm(AuthInfo(currentResourceUserId, getUsername, getCbsToken), bankId.value, atmId.value)
      logger.info(s"Kafka getAtm Req is: $req")

      val box = for {
        kafkaMessage <- processToBox[OutboundGetAtm](req)
        inboundGetAtm <- tryo{kafkaMessage.extract[InboundGetAtm]} ?~! s"$InboundGetAtm extract error"
        (inboundAtm, inboundStatus) <- Full(inboundGetAtm.data, inboundGetAtm.status)
      } yield{
        (inboundAtm, inboundStatus)
      }


      logger.debug(s"Kafka getAtm Res says:  is: $Box")
      val res = box match {
        case Full((Some(atm), status)) if (status.errorCode=="") =>
          Full(atm)
        case Full((_, status)) if (status.errorCode!="") =>
          Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
        case Empty =>
          Failure(ErrorMessages.ConnectorEmptyResponse)
        case Failure(msg, e, c) =>
          Failure(msg, e, c)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
      logger.info(s"Kafka getAtm says res is $res")
      res
    }
  }("getAtm")


  override def getAtmFuture(bankId : BankId, atmId: AtmId) : Future[Box[AtmT]] = saveConnectorMetric {
    memoizeSync(atmTTL millisecond){
      val req = OutboundGetAtm(AuthInfo(currentResourceUserId, getUsername, getCbsToken), bankId.value, atmId.value)
      logger.info(s"Kafka getAtmFuture Req is: $req")

      val future: Future[(Option[InboundAtmJune2017], Status)] = for {
        res <- processToFuture[OutboundGetAtm](req) map {
          f =>
            try {
              f.extract[InboundGetAtm]
            } catch {
              case e: Exception => throw new MappingException(s"$InboundGetAtm extract error", e)
            }
        } map {
          d => (d.data, d.status)
        }
      } yield {
        res
      }

      logger.debug(s"Kafka getAtmFuture Res says:  is: $future")
      future map {
        case (Some(atm), status) if (status.errorCode=="") =>
          Full(atm)
        case (_, status) if (status.errorCode!="") =>
          Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
        case _ =>
          Failure(ErrorMessages.UnknownError)
      }
    }
  }("getAtmFuture")


}


object KafkaMappedConnector_vJune2017 extends KafkaMappedConnector_vJune2017{
  
}