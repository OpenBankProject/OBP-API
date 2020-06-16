package code.bankconnectors.storedprocedure

/*
Open Bank Project - API
Copyright (C) 2011-2017, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany
*/

import java.net.URLEncoder
import java.util.Date
import java.util.UUID.randomUUID

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpProtocol, _}
import akka.util.ByteString
import code.api.cache.Caching
import code.api.util.APIUtil.{AdapterImplementation, MessageDoc, OBPReturnType, saveConnectorMetric, _}
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue._
import code.api.util.{APIUtil, CallContext, CustomJsonFormats, NewStyle, OBPQueryParam}
import code.api.{APIFailure, APIFailureNewStyle, ApiVersionHolder}
import com.openbankproject.commons.model.ErrorMessage
import code.bankconnectors._
import code.bankconnectors.vJune2017.AuthInfo
import code.customer.internalMapping.MappedCustomerIdMappingProvider
import code.kafka.KafkaHelper
import code.model.dataAccess.internalMapping.MappedAccountIdMappingProvider
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.dto.{InBoundTrait, _}
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.{AccountAttributeType, CardAttributeType, DynamicEntityOperation, ProductAttributeType}
import com.openbankproject.commons.model.{TopicTrait, _}
import com.openbankproject.commons.util.ReflectUtils
import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
import dispatch.url
import net.liftweb.common.{Box, Empty, _}
import net.liftweb.json._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.StringHelpers

import scala.collection.immutable.List
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import com.openbankproject.commons.ExecutionContext.Implicits.global

trait StoredProcedureConnector_vDec2019 extends Connector with MdcLoggable {
  //this one import is for implicit convert, don't delete
  import com.openbankproject.commons.model.{AmountOfMoney, CreditLimit, CreditRating, CustomerFaceImage}

  implicit override val nameOfConnector = StoredProcedureConnector_vDec2019.toString

  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "Dec2019"

  override val messageDocs = ArrayBuffer[MessageDoc]()

  val authInfoExample = AuthInfo(userId = "userId", username = "username", cbsToken = "cbsToken")
  val errorCodeExample = "INTERNAL-OBP-ADAPTER-6001: ..."

  val connectorName = "stored_procedure_vDec2019"

//---------------- dynamic start -------------------please don't modify this line
// ---------- create on Thu Dec 12 20:57:07 CST 2019

  messageDocs += getAdapterInfoDoc6
  private def getAdapterInfoDoc6 = MessageDoc(
    process = "obp.getAdapterInfo",
    messageFormat = messageFormat,
    description = """
               |Get Adapter Info
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_adapter_info
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
          OutBoundGetAdapterInfo( OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))))
    ),
    exampleInboundMessage = (
     InBoundGetAdapterInfo(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= InboundAdapterInfoInternal(errorCode=inboundAdapterInfoInternalErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value)),
      name=inboundAdapterInfoInternalNameExample.value,
      version=inboundAdapterInfoInternalVersionExample.value,
      git_commit=inboundAdapterInfoInternalGit_commitExample.value,
      date=inboundAdapterInfoInternalDateExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_adapter_info
  override def getAdapterInfo(callContext: Option[CallContext]): Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAdapterInfo => OutBound, InBoundGetAdapterInfo => InBound}
        val procedureName = "get_adapter_info"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull )
        val result: OBPReturnType[Box[InboundAdapterInfoInternal]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getChallengeThresholdDoc30
  private def getChallengeThresholdDoc30 = MessageDoc(
    process = "obp.getChallengeThreshold",
    messageFormat = messageFormat,
    description = """
               |Get Challenge Threshold
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_challenge_threshold
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetChallengeThreshold(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=bankIdExample.value,
      accountId=accountIdExample.value,
      viewId=viewIdExample.value,
      transactionRequestType=transactionRequestTypeExample.value,
      currency=currencyExample.value,
      userId=userIdExample.value,
      userName="string")
    ),
    exampleInboundMessage = (
     InBoundGetChallengeThreshold(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= AmountOfMoney(currency=currencyExample.value,
      amount="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_challenge_threshold
  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, userName: String, callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = {
        import com.openbankproject.commons.dto.{OutBoundGetChallengeThreshold => OutBound, InBoundGetChallengeThreshold => InBound}
        val procedureName = "get_challenge_threshold"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, viewId, transactionRequestType, currency, userId, userName)
        val result: OBPReturnType[Box[AmountOfMoney]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getChargeLevelDoc5
  private def getChargeLevelDoc5 = MessageDoc(
    process = "obp.getChargeLevel",
    messageFormat = messageFormat,
    description = """
               |Get Charge Level
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_charge_level
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetChargeLevel(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      viewId=ViewId(viewIdExample.value),
      userId=userIdExample.value,
      userName="string",
      transactionRequestType=transactionRequestTypeExample.value,
      currency=currencyExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetChargeLevel(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= AmountOfMoney(currency=currencyExample.value,
      amount="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_charge_level
  override def getChargeLevel(bankId: BankId, accountId: AccountId, viewId: ViewId, userId: String, userName: String, transactionRequestType: String, currency: String, callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = {
        import com.openbankproject.commons.dto.{OutBoundGetChargeLevel => OutBound, InBoundGetChargeLevel => InBound}
        val procedureName = "get_charge_level"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, viewId, userId, userName, transactionRequestType, currency)
        val result: OBPReturnType[Box[AmountOfMoney]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createChallengeDoc62
  private def createChallengeDoc62 = MessageDoc(
    process = "obp.createChallenge",
    messageFormat = messageFormat,
    description = """
               |Create Challenge
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_challenge
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateChallenge(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      userId=userIdExample.value,
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      transactionRequestId="string",
      scaMethod=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SMS))
    ),
    exampleInboundMessage = (
     InBoundCreateChallenge(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data="string")
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_challenge
  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String, scaMethod: Option[SCA], callContext: Option[CallContext]): OBPReturnType[Box[String]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateChallenge => OutBound, InBoundCreateChallenge => InBound}
        val procedureName = "create_challenge"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, userId, transactionRequestType, transactionRequestId, scaMethod)
        val result: OBPReturnType[Box[String]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += validateChallengeAnswerDoc76
  private def validateChallengeAnswerDoc76 = MessageDoc(
    process = "obp.validateChallengeAnswer",
    messageFormat = messageFormat,
    description = """
               |Validate Challenge Answer
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: validate_challenge_answer
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundValidateChallengeAnswer(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      challengeId="string",
      hashOfSuppliedAnswer="string")
    ),
    exampleInboundMessage = (
     InBoundValidateChallengeAnswer(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: validate_challenge_answer
  override def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundValidateChallengeAnswer => OutBound, InBoundValidateChallengeAnswer => InBound}
        val procedureName = "validate_challenge_answer"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , challengeId, hashOfSuppliedAnswer)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBankLegacyDoc75
  private def getBankLegacyDoc75 = MessageDoc(
    process = "obp.getBankLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Bank Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_bank_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankLegacy(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetBankLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BankCommons(bankId=BankId(bankIdExample.value),
      shortName=bankShortNameExample.value,
      fullName=bankFullNameExample.value,
      logoUrl=bankLogoUrlExample.value,
      websiteUrl=bankWebsiteUrlExample.value,
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value,
      swiftBic=bankSwiftBicExample.value,
      nationalIdentifier=bankNationalIdentifierExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_bank_legacy
  override def getBankLegacy(bankId: BankId, callContext: Option[CallContext]): Box[(Bank, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankLegacy => OutBound, InBoundGetBankLegacy => InBound}
        val procedureName = "get_bank_legacy"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId)
        val result: OBPReturnType[Box[BankCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBankDoc38
  private def getBankDoc38 = MessageDoc(
    process = "obp.getBank",
    messageFormat = messageFormat,
    description = """
               |Get Bank
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_bank
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBank(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetBank(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BankCommons(bankId=BankId(bankIdExample.value),
      shortName=bankShortNameExample.value,
      fullName=bankFullNameExample.value,
      logoUrl=bankLogoUrlExample.value,
      websiteUrl=bankWebsiteUrlExample.value,
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value,
      swiftBic=bankSwiftBicExample.value,
      nationalIdentifier=bankNationalIdentifierExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_bank
  override def getBank(bankId: BankId, callContext: Option[CallContext]): Future[Box[(Bank, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBank => OutBound, InBoundGetBank => InBound}
        val procedureName = "get_bank"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId)
        val result: OBPReturnType[Box[BankCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBanksLegacyDoc48
  private def getBanksLegacyDoc48 = MessageDoc(
    process = "obp.getBanksLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Banks Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_banks_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
          OutBoundGetBanksLegacy( OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))))
    ),
    exampleInboundMessage = (
     InBoundGetBanksLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( BankCommons(bankId=BankId(bankIdExample.value),
      shortName=bankShortNameExample.value,
      fullName=bankFullNameExample.value,
      logoUrl=bankLogoUrlExample.value,
      websiteUrl=bankWebsiteUrlExample.value,
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value,
      swiftBic=bankSwiftBicExample.value,
      nationalIdentifier=bankNationalIdentifierExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_banks_legacy
  override def getBanksLegacy(callContext: Option[CallContext]): Box[(List[Bank], Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetBanksLegacy => OutBound, InBoundGetBanksLegacy => InBound}
        val procedureName = "get_banks_legacy"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull )
        val result: OBPReturnType[Box[List[BankCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBanksDoc77
  private def getBanksDoc77 = MessageDoc(
    process = "obp.getBanks",
    messageFormat = messageFormat,
    description = """
               |Get Banks
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_banks
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
          OutBoundGetBanks( OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))))
    ),
    exampleInboundMessage = (
     InBoundGetBanks(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( BankCommons(bankId=BankId(bankIdExample.value),
      shortName=bankShortNameExample.value,
      fullName=bankFullNameExample.value,
      logoUrl=bankLogoUrlExample.value,
      websiteUrl=bankWebsiteUrlExample.value,
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value,
      swiftBic=bankSwiftBicExample.value,
      nationalIdentifier=bankNationalIdentifierExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_banks
  override def getBanks(callContext: Option[CallContext]): Future[Box[(List[Bank], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBanks => OutBound, InBoundGetBanks => InBound}
        val procedureName = "get_banks"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull )
        val result: OBPReturnType[Box[List[BankCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBankAccountsForUserLegacyDoc21
  private def getBankAccountsForUserLegacyDoc21 = MessageDoc(
    process = "obp.getBankAccountsForUserLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Bank Accounts For User Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_bank_accounts_for_user_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountsForUserLegacy(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      username=usernameExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountsForUserLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( InboundAccountCommons(bankId=bankIdExample.value,
      branchId=branchIdExample.value,
      accountId=accountIdExample.value,
      accountNumber=accountNumberExample.value,
      accountType=accountTypeExample.value,
      balanceAmount=balanceAmountExample.value,
      balanceCurrency=balanceCurrencyExample.value,
      owners=inboundAccountOwnersExample.value.split("[,;]").toList,
      viewsToGenerate=inboundAccountViewsToGenerateExample.value.split("[,;]").toList,
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value,
      branchRoutingScheme=branchRoutingSchemeExample.value,
      branchRoutingAddress=branchRoutingAddressExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_bank_accounts_for_user_legacy
  override def getBankAccountsForUserLegacy(username: String, callContext: Option[CallContext]): Box[(List[InboundAccount], Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountsForUserLegacy => OutBound, InBoundGetBankAccountsForUserLegacy => InBound}
        val procedureName = "get_bank_accounts_for_user_legacy"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , username)
        val result: OBPReturnType[Box[List[InboundAccountCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBankAccountsForUserDoc50
  private def getBankAccountsForUserDoc50 = MessageDoc(
    process = "obp.getBankAccountsForUser",
    messageFormat = messageFormat,
    description = """
               |Get Bank Accounts For User
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_bank_accounts_for_user
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountsForUser(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      username=usernameExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountsForUser(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( InboundAccountCommons(bankId=bankIdExample.value,
      branchId=branchIdExample.value,
      accountId=accountIdExample.value,
      accountNumber=accountNumberExample.value,
      accountType=accountTypeExample.value,
      balanceAmount=balanceAmountExample.value,
      balanceCurrency=balanceCurrencyExample.value,
      owners=inboundAccountOwnersExample.value.split("[,;]").toList,
      viewsToGenerate=inboundAccountViewsToGenerateExample.value.split("[,;]").toList,
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value,
      branchRoutingScheme=branchRoutingSchemeExample.value,
      branchRoutingAddress=branchRoutingAddressExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_bank_accounts_for_user
  override def getBankAccountsForUser(username: String, callContext: Option[CallContext]): Future[Box[(List[InboundAccount], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountsForUser => OutBound, InBoundGetBankAccountsForUser => InBound}
        val procedureName = "get_bank_accounts_for_user"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , username)
        val result: OBPReturnType[Box[List[InboundAccountCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getUserDoc5
  private def getUserDoc5 = MessageDoc(
    process = "obp.getUser",
    messageFormat = messageFormat,
    description = """
               |Get User
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_user
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetUser(
      name=usernameExample.value,
      password="string")
    ),
    exampleInboundMessage = (
     InBoundGetUser(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= InboundUser(email=emailExample.value,
      password="string",
      displayName="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_user
  override def getUser(name: String, password: String): Box[InboundUser] = {
        import com.openbankproject.commons.dto.{OutBoundGetUser => OutBound, InBoundGetUser => InBound}
        val procedureName = "get_user"
        val callContext: Option[CallContext] = None
        val req = OutBound(name, password)
        val result: OBPReturnType[Box[InboundUser]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBankAccountDoc6
  private def getBankAccountDoc6 = MessageDoc(
    process = "obp.getBankAccount",
    messageFormat = messageFormat,
    description = """
               |Get Bank Account
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_bank_account
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccount(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetBankAccount(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_bank_account
  override def getBankAccountOld(bankId: BankId, accountId: AccountId): Box[BankAccount] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountOld => OutBound, InBoundGetBankAccountOld => InBound}
        val procedureName = "get_bank_account"
        val callContext: Option[CallContext] = None
        val req = OutBound(bankId, accountId)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBankAccountLegacyDoc66
  private def getBankAccountLegacyDoc66 = MessageDoc(
    process = "obp.getBankAccountLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Bank Account Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_bank_account_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountLegacy(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_bank_account_legacy
  override def getBankAccountLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountLegacy => OutBound, InBoundGetBankAccountLegacy => InBound}
        val procedureName = "get_bank_account_legacy"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBankAccountDoc62
  private def getBankAccountDoc62 = MessageDoc(
    process = "obp.getBankAccount",
    messageFormat = messageFormat,
    description = """
               |Get Bank Account
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_bank_account
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccount(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetBankAccount(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_bank_account
  override def getBankAccount(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccount => OutBound, InBoundGetBankAccount => InBound}
        val procedureName = "get_bank_account"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBankAccountByIbanDoc10
  private def getBankAccountByIbanDoc10 = MessageDoc(
    process = "obp.getBankAccountByIban",
    messageFormat = messageFormat,
    description = """
               |Get Bank Account By Iban
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_bank_account_by_iban
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountByIban(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      iban=ibanExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountByIban(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_bank_account_by_iban
  override def getBankAccountByIban(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountByIban => OutBound, InBoundGetBankAccountByIban => InBound}
        val procedureName = "get_bank_account_by_iban"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , iban)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBankAccountByRoutingDoc43
  private def getBankAccountByRoutingDoc43 = MessageDoc(
    process = "obp.getBankAccountByRouting",
    messageFormat = messageFormat,
    description = """
               |Get Bank Account By Routing
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_bank_account_by_routing
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountByRouting(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      scheme="string",
      address="string")
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountByRouting(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_bank_account_by_routing
  override def getBankAccountByRouting(scheme: String, address: String, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountByRouting => OutBound, InBoundGetBankAccountByRouting => InBound}
        val procedureName = "get_bank_account_by_routing"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , scheme, address)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBankAccountsDoc32
  private def getBankAccountsDoc32 = MessageDoc(
    process = "obp.getBankAccounts",
    messageFormat = messageFormat,
    description = """
               |Get Bank Accounts
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_bank_accounts
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccounts(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankIdAccountIds=List( BankIdAccountId(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))))
    ),
    exampleInboundMessage = (
     InBoundGetBankAccounts(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_bank_accounts
  override def getBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[List[BankAccount]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccounts => OutBound, InBoundGetBankAccounts => InBound}
        val procedureName = "get_bank_accounts"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankIdAccountIds)
        val result: OBPReturnType[Box[List[BankAccountCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBankAccountsBalancesDoc99
  private def getBankAccountsBalancesDoc99 = MessageDoc(
    process = "obp.getBankAccountsBalances",
    messageFormat = messageFormat,
    description = """
               |Get Bank Accounts Balances
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_bank_accounts_balances
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountsBalances(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankIdAccountIds=List( BankIdAccountId(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))))
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountsBalances(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= AccountsBalances(accounts=List( AccountBalance(id=accountIdExample.value,
      label=labelExample.value,
      bankId=bankIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      balance= AmountOfMoney(currency=balanceCurrencyExample.value,
      amount=balanceAmountExample.value))),
      overallBalance= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      overallBalanceDate=new Date()))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_bank_accounts_balances
  override def getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[AccountsBalances]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountsBalances => OutBound, InBoundGetBankAccountsBalances => InBound}
        val procedureName = "get_bank_accounts_balances"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankIdAccountIds)
        val result: OBPReturnType[Box[AccountsBalances]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCoreBankAccountsLegacyDoc99
  private def getCoreBankAccountsLegacyDoc99 = MessageDoc(
    process = "obp.getCoreBankAccountsLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Core Bank Accounts Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_core_bank_accounts_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCoreBankAccountsLegacy(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankIdAccountIds=List( BankIdAccountId(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))))
    ),
    exampleInboundMessage = (
     InBoundGetCoreBankAccountsLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( CoreAccount(id=accountIdExample.value,
      label=labelExample.value,
      bankId=bankIdExample.value,
      accountType=accountTypeExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_core_bank_accounts_legacy
  override def getCoreBankAccountsLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Box[(List[CoreAccount], Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetCoreBankAccountsLegacy => OutBound, InBoundGetCoreBankAccountsLegacy => InBound}
        val procedureName = "get_core_bank_accounts_legacy"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankIdAccountIds)
        val result: OBPReturnType[Box[List[CoreAccount]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCoreBankAccountsDoc89
  private def getCoreBankAccountsDoc89 = MessageDoc(
    process = "obp.getCoreBankAccounts",
    messageFormat = messageFormat,
    description = """
               |Get Core Bank Accounts
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_core_bank_accounts
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCoreBankAccounts(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankIdAccountIds=List( BankIdAccountId(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))))
    ),
    exampleInboundMessage = (
     InBoundGetCoreBankAccounts(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( CoreAccount(id=accountIdExample.value,
      label=labelExample.value,
      bankId=bankIdExample.value,
      accountType=accountTypeExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_core_bank_accounts
  override def getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Future[Box[(List[CoreAccount], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCoreBankAccounts => OutBound, InBoundGetCoreBankAccounts => InBound}
        val procedureName = "get_core_bank_accounts"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankIdAccountIds)
        val result: OBPReturnType[Box[List[CoreAccount]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBankAccountsHeldLegacyDoc41
  private def getBankAccountsHeldLegacyDoc41 = MessageDoc(
    process = "obp.getBankAccountsHeldLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Bank Accounts Held Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_bank_accounts_held_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountsHeldLegacy(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankIdAccountIds=List( BankIdAccountId(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))))
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountsHeldLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( AccountHeld(id="string",
      bankId=bankIdExample.value,
      number="string",
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_bank_accounts_held_legacy
  override def getBankAccountsHeldLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Box[List[AccountHeld]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountsHeldLegacy => OutBound, InBoundGetBankAccountsHeldLegacy => InBound}
        val procedureName = "get_bank_accounts_held_legacy"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankIdAccountIds)
        val result: OBPReturnType[Box[List[AccountHeld]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBankAccountsHeldDoc49
  private def getBankAccountsHeldDoc49 = MessageDoc(
    process = "obp.getBankAccountsHeld",
    messageFormat = messageFormat,
    description = """
               |Get Bank Accounts Held
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_bank_accounts_held
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountsHeld(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankIdAccountIds=List( BankIdAccountId(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))))
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountsHeld(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( AccountHeld(id="string",
      bankId=bankIdExample.value,
      number="string",
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_bank_accounts_held
  override def getBankAccountsHeld(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[List[AccountHeld]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountsHeld => OutBound, InBoundGetBankAccountsHeld => InBound}
        val procedureName = "get_bank_accounts_held"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankIdAccountIds)
        val result: OBPReturnType[Box[List[AccountHeld]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += checkBankAccountExistsLegacyDoc7
  private def checkBankAccountExistsLegacyDoc7 = MessageDoc(
    process = "obp.checkBankAccountExistsLegacy",
    messageFormat = messageFormat,
    description = """
               |Check Bank Account Exists Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: check_bank_account_exists_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCheckBankAccountExistsLegacy(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundCheckBankAccountExistsLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: check_bank_account_exists_legacy
  override def checkBankAccountExistsLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundCheckBankAccountExistsLegacy => OutBound, InBoundCheckBankAccountExistsLegacy => InBound}
        val procedureName = "check_bank_account_exists_legacy"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += checkBankAccountExistsDoc52
  private def checkBankAccountExistsDoc52 = MessageDoc(
    process = "obp.checkBankAccountExists",
    messageFormat = messageFormat,
    description = """
               |Check Bank Account Exists
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: check_bank_account_exists
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCheckBankAccountExists(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundCheckBankAccountExists(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: check_bank_account_exists
  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
        import com.openbankproject.commons.dto.{OutBoundCheckBankAccountExists => OutBound, InBoundCheckBankAccountExists => InBound}
        val procedureName = "check_bank_account_exists"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCounterpartyDoc95
  private def getCounterpartyDoc95 = MessageDoc(
    process = "obp.getCounterparty",
    messageFormat = messageFormat,
    description = """
               |Get Counterparty
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_counterparty
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCounterparty(
      thisBankId=BankId(bankIdExample.value),
      thisAccountId=AccountId(accountIdExample.value),
      couterpartyId="string")
    ),
    exampleInboundMessage = (
     InBoundGetCounterparty(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= Counterparty(nationalIdentifier=counterpartyNationalIdentifierExample.value,
      kind=counterpartyKindExample.value,
      counterpartyId=counterpartyIdExample.value,
      counterpartyName=counterpartyNameExample.value,
      thisBankId=BankId(bankIdExample.value),
      thisAccountId=AccountId(accountIdExample.value),
      otherBankRoutingScheme=counterpartyOtherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=Some(counterpartyOtherBankRoutingAddressExample.value),
      otherAccountRoutingScheme=counterpartyOtherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=Some(counterpartyOtherAccountRoutingAddressExample.value),
      otherAccountProvider=counterpartyOtherAccountProviderExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_counterparty
  override def getCounterparty(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty] = {
        import com.openbankproject.commons.dto.{OutBoundGetCounterparty => OutBound, InBoundGetCounterparty => InBound}
        val procedureName = "get_counterparty"
        val callContext: Option[CallContext] = None
        val req = OutBound(thisBankId, thisAccountId, couterpartyId)
        val result: OBPReturnType[Box[Counterparty]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCounterpartyTraitDoc42
  private def getCounterpartyTraitDoc42 = MessageDoc(
    process = "obp.getCounterpartyTrait",
    messageFormat = messageFormat,
    description = """
               |Get Counterparty Trait
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_counterparty_trait
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCounterpartyTrait(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      couterpartyId="string")
    ),
    exampleInboundMessage = (
     InBoundGetCounterpartyTrait(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CounterpartyTraitCommons(createdByUserId="string",
      name="string",
      description="string",
      thisBankId="string",
      thisAccountId="string",
      thisViewId="string",
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=accountRoutingSchemeExample.value,
      otherAccountRoutingAddress=accountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme="string",
      otherAccountSecondaryRoutingAddress="string",
      otherBankRoutingScheme=bankRoutingSchemeExample.value,
      otherBankRoutingAddress=bankRoutingAddressExample.value,
      otherBranchRoutingScheme=branchRoutingSchemeExample.value,
      otherBranchRoutingAddress=branchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_counterparty_trait
  override def getCounterpartyTrait(bankId: BankId, accountId: AccountId, couterpartyId: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCounterpartyTrait => OutBound, InBoundGetCounterpartyTrait => InBound}
        val procedureName = "get_counterparty_trait"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, couterpartyId)
        val result: OBPReturnType[Box[CounterpartyTraitCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCounterpartyByCounterpartyIdLegacyDoc82
  private def getCounterpartyByCounterpartyIdLegacyDoc82 = MessageDoc(
    process = "obp.getCounterpartyByCounterpartyIdLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Counterparty By Counterparty Id Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_counterparty_by_counterparty_id_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCounterpartyByCounterpartyIdLegacy(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      counterpartyId=CounterpartyId(counterpartyIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetCounterpartyByCounterpartyIdLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CounterpartyTraitCommons(createdByUserId="string",
      name="string",
      description="string",
      thisBankId="string",
      thisAccountId="string",
      thisViewId="string",
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=accountRoutingSchemeExample.value,
      otherAccountRoutingAddress=accountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme="string",
      otherAccountSecondaryRoutingAddress="string",
      otherBankRoutingScheme=bankRoutingSchemeExample.value,
      otherBankRoutingAddress=bankRoutingAddressExample.value,
      otherBranchRoutingScheme=branchRoutingSchemeExample.value,
      otherBranchRoutingAddress=branchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_counterparty_by_counterparty_id_legacy
  override def getCounterpartyByCounterpartyIdLegacy(counterpartyId: CounterpartyId, callContext: Option[CallContext]): Box[(CounterpartyTrait, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetCounterpartyByCounterpartyIdLegacy => OutBound, InBoundGetCounterpartyByCounterpartyIdLegacy => InBound}
        val procedureName = "get_counterparty_by_counterparty_id_legacy"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , counterpartyId)
        val result: OBPReturnType[Box[CounterpartyTraitCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCounterpartyByCounterpartyIdDoc49
  private def getCounterpartyByCounterpartyIdDoc49 = MessageDoc(
    process = "obp.getCounterpartyByCounterpartyId",
    messageFormat = messageFormat,
    description = """
               |Get Counterparty By Counterparty Id
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_counterparty_by_counterparty_id
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCounterpartyByCounterpartyId(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      counterpartyId=CounterpartyId(counterpartyIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetCounterpartyByCounterpartyId(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CounterpartyTraitCommons(createdByUserId="string",
      name="string",
      description="string",
      thisBankId="string",
      thisAccountId="string",
      thisViewId="string",
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=accountRoutingSchemeExample.value,
      otherAccountRoutingAddress=accountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme="string",
      otherAccountSecondaryRoutingAddress="string",
      otherBankRoutingScheme=bankRoutingSchemeExample.value,
      otherBankRoutingAddress=bankRoutingAddressExample.value,
      otherBranchRoutingScheme=branchRoutingSchemeExample.value,
      otherBranchRoutingAddress=branchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_counterparty_by_counterparty_id
  override def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCounterpartyByCounterpartyId => OutBound, InBoundGetCounterpartyByCounterpartyId => InBound}
        val procedureName = "get_counterparty_by_counterparty_id"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , counterpartyId)
        val result: OBPReturnType[Box[CounterpartyTraitCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCounterpartyByIbanDoc32
  private def getCounterpartyByIbanDoc32 = MessageDoc(
    process = "obp.getCounterpartyByIban",
    messageFormat = messageFormat,
    description = """
               |Get Counterparty By Iban
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_counterparty_by_iban
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCounterpartyByIban(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      iban=ibanExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCounterpartyByIban(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CounterpartyTraitCommons(createdByUserId="string",
      name="string",
      description="string",
      thisBankId="string",
      thisAccountId="string",
      thisViewId="string",
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=accountRoutingSchemeExample.value,
      otherAccountRoutingAddress=accountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme="string",
      otherAccountSecondaryRoutingAddress="string",
      otherBankRoutingScheme=bankRoutingSchemeExample.value,
      otherBankRoutingAddress=bankRoutingAddressExample.value,
      otherBranchRoutingScheme=branchRoutingSchemeExample.value,
      otherBranchRoutingAddress=branchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_counterparty_by_iban
  override def getCounterpartyByIban(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCounterpartyByIban => OutBound, InBoundGetCounterpartyByIban => InBound}
        val procedureName = "get_counterparty_by_iban"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , iban)
        val result: OBPReturnType[Box[CounterpartyTraitCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCounterpartiesLegacyDoc24
  private def getCounterpartiesLegacyDoc24 = MessageDoc(
    process = "obp.getCounterpartiesLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Counterparties Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_counterparties_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCounterpartiesLegacy(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      thisBankId=BankId(bankIdExample.value),
      thisAccountId=AccountId(accountIdExample.value),
      viewId=ViewId(viewIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetCounterpartiesLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( CounterpartyTraitCommons(createdByUserId="string",
      name="string",
      description="string",
      thisBankId="string",
      thisAccountId="string",
      thisViewId="string",
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=accountRoutingSchemeExample.value,
      otherAccountRoutingAddress=accountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme="string",
      otherAccountSecondaryRoutingAddress="string",
      otherBankRoutingScheme=bankRoutingSchemeExample.value,
      otherBankRoutingAddress=bankRoutingAddressExample.value,
      otherBranchRoutingScheme=branchRoutingSchemeExample.value,
      otherBranchRoutingAddress=branchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_counterparties_legacy
  override def getCounterpartiesLegacy(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext]): Box[(List[CounterpartyTrait], Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetCounterpartiesLegacy => OutBound, InBoundGetCounterpartiesLegacy => InBound}
        val procedureName = "get_counterparties_legacy"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , thisBankId, thisAccountId, viewId)
        val result: OBPReturnType[Box[List[CounterpartyTraitCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCounterpartiesDoc51
  private def getCounterpartiesDoc51 = MessageDoc(
    process = "obp.getCounterparties",
    messageFormat = messageFormat,
    description = """
               |Get Counterparties
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_counterparties
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCounterparties(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      thisBankId=BankId(bankIdExample.value),
      thisAccountId=AccountId(accountIdExample.value),
      viewId=ViewId(viewIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetCounterparties(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( CounterpartyTraitCommons(createdByUserId="string",
      name="string",
      description="string",
      thisBankId="string",
      thisAccountId="string",
      thisViewId="string",
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=accountRoutingSchemeExample.value,
      otherAccountRoutingAddress=accountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme="string",
      otherAccountSecondaryRoutingAddress="string",
      otherBankRoutingScheme=bankRoutingSchemeExample.value,
      otherBankRoutingAddress=bankRoutingAddressExample.value,
      otherBranchRoutingScheme=branchRoutingSchemeExample.value,
      otherBranchRoutingAddress=branchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_counterparties
  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext]): OBPReturnType[Box[List[CounterpartyTrait]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCounterparties => OutBound, InBoundGetCounterparties => InBound}
        val procedureName = "get_counterparties"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , thisBankId, thisAccountId, viewId)
        val result: OBPReturnType[Box[List[CounterpartyTraitCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getTransactionsLegacyDoc73
  private def getTransactionsLegacyDoc73 = MessageDoc(
    process = "obp.getTransactionsLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Transactions Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_transactions_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionsLegacy(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountID=AccountId(accountIdExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate="string",
      toDate="string")
    ),
    exampleInboundMessage = (
     InBoundGetTransactionsLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( TransactionCommons(uuid=transactionUuidExample.value,
      id=TransactionId(transactionIdExample.value),
      thisAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      otherAccount= Counterparty(nationalIdentifier=counterpartyNationalIdentifierExample.value,
      kind=counterpartyKindExample.value,
      counterpartyId=counterpartyIdExample.value,
      counterpartyName=counterpartyNameExample.value,
      thisBankId=BankId(bankIdExample.value),
      thisAccountId=AccountId(accountIdExample.value),
      otherBankRoutingScheme=counterpartyOtherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=Some(counterpartyOtherBankRoutingAddressExample.value),
      otherAccountRoutingScheme=counterpartyOtherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=Some(counterpartyOtherAccountRoutingAddressExample.value),
      otherAccountProvider=counterpartyOtherAccountProviderExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean),
      transactionType=transactionTypeExample.value,
      amount=BigDecimal(transactionAmountExample.value),
      currency=currencyExample.value,
      description=Some(transactionDescriptionExample.value),
      startDate=parseDate(transactionStartDateExample.value).getOrElse(sys.error("transactionStartDateExample.value is not validate date format.")),
      finishDate=parseDate(transactionFinishDateExample.value).getOrElse(sys.error("transactionFinishDateExample.value is not validate date format.")),
      balance=BigDecimal(balanceAmountExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_transactions_legacy
  override def getTransactionsLegacy(bankId: BankId, accountID: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Box[(List[Transaction], Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionsLegacy => OutBound, InBoundGetTransactionsLegacy => InBound}
        val procedureName = "get_transactions_legacy"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountID, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[TransactionCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getTransactionsDoc43
  private def getTransactionsDoc43 = MessageDoc(
    process = "obp.getTransactions",
    messageFormat = messageFormat,
    description = """
               |Get Transactions
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_transactions
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactions(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate=outBoundGetTransactionsFromDateExample.value,
      toDate=outBoundGetTransactionsToDateExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetTransactions(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( TransactionCommons(uuid=transactionUuidExample.value,
      id=TransactionId(transactionIdExample.value),
      thisAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      otherAccount= Counterparty(nationalIdentifier=counterpartyNationalIdentifierExample.value,
      kind=counterpartyKindExample.value,
      counterpartyId=counterpartyIdExample.value,
      counterpartyName=counterpartyNameExample.value,
      thisBankId=BankId(bankIdExample.value),
      thisAccountId=AccountId(accountIdExample.value),
      otherBankRoutingScheme=counterpartyOtherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=Some(counterpartyOtherBankRoutingAddressExample.value),
      otherAccountRoutingScheme=counterpartyOtherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=Some(counterpartyOtherAccountRoutingAddressExample.value),
      otherAccountProvider=counterpartyOtherAccountProviderExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean),
      transactionType=transactionTypeExample.value,
      amount=BigDecimal(transactionAmountExample.value),
      currency=currencyExample.value,
      description=Some(transactionDescriptionExample.value),
      startDate=parseDate(transactionStartDateExample.value).getOrElse(sys.error("transactionStartDateExample.value is not validate date format.")),
      finishDate=parseDate(transactionFinishDateExample.value).getOrElse(sys.error("transactionFinishDateExample.value is not validate date format.")),
      balance=BigDecimal(balanceAmountExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_transactions
  override def getTransactions(bankId: BankId, accountID: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): OBPReturnType[Box[List[Transaction]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactions => OutBound, InBoundGetTransactions => InBound}
        val procedureName = "get_transactions"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountID, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[TransactionCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getTransactionsCoreDoc70
  private def getTransactionsCoreDoc70 = MessageDoc(
    process = "obp.getTransactionsCore",
    messageFormat = messageFormat,
    description = """
               |Get Transactions Core
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_transactions_core
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionsCore(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountID=AccountId(accountIdExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate="string",
      toDate="string")
    ),
    exampleInboundMessage = (
     InBoundGetTransactionsCore(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( TransactionCore(id=TransactionId(transactionIdExample.value),
      thisAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      otherAccount= CounterpartyCore(kind="string",
      counterpartyId=counterpartyIdExample.value,
      counterpartyName=counterpartyNameExample.value,
      thisBankId=BankId(bankIdExample.value),
      thisAccountId=AccountId(accountIdExample.value),
      otherBankRoutingScheme=bankRoutingSchemeExample.value,
      otherBankRoutingAddress=Some(bankRoutingAddressExample.value),
      otherAccountRoutingScheme=accountRoutingSchemeExample.value,
      otherAccountRoutingAddress=Some(accountRoutingAddressExample.value),
      otherAccountProvider=otherAccountProviderExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean),
      transactionType=transactionTypeExample.value,
      amount=BigDecimal("123.321"),
      currency=currencyExample.value,
      description=Some("string"),
      startDate=new Date(),
      finishDate=new Date(),
      balance=BigDecimal(balanceAmountExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_transactions_core
  override def getTransactionsCore(bankId: BankId, accountID: AccountId, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionCore]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionsCore => OutBound, InBoundGetTransactionsCore => InBound}
        val procedureName = "get_transactions_core"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountID, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[TransactionCore]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getTransactionLegacyDoc68
  private def getTransactionLegacyDoc68 = MessageDoc(
    process = "obp.getTransactionLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Transaction Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_transaction_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionLegacy(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountID=AccountId(accountIdExample.value),
      transactionId=TransactionId(transactionIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetTransactionLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TransactionCommons(uuid=transactionUuidExample.value,
      id=TransactionId(transactionIdExample.value),
      thisAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      otherAccount= Counterparty(nationalIdentifier=counterpartyNationalIdentifierExample.value,
      kind=counterpartyKindExample.value,
      counterpartyId=counterpartyIdExample.value,
      counterpartyName=counterpartyNameExample.value,
      thisBankId=BankId(bankIdExample.value),
      thisAccountId=AccountId(accountIdExample.value),
      otherBankRoutingScheme=counterpartyOtherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=Some(counterpartyOtherBankRoutingAddressExample.value),
      otherAccountRoutingScheme=counterpartyOtherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=Some(counterpartyOtherAccountRoutingAddressExample.value),
      otherAccountProvider=counterpartyOtherAccountProviderExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean),
      transactionType=transactionTypeExample.value,
      amount=BigDecimal(transactionAmountExample.value),
      currency=currencyExample.value,
      description=Some(transactionDescriptionExample.value),
      startDate=parseDate(transactionStartDateExample.value).getOrElse(sys.error("transactionStartDateExample.value is not validate date format.")),
      finishDate=parseDate(transactionFinishDateExample.value).getOrElse(sys.error("transactionFinishDateExample.value is not validate date format.")),
      balance=BigDecimal(balanceAmountExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_transaction_legacy
  override def getTransactionLegacy(bankId: BankId, accountID: AccountId, transactionId: TransactionId, callContext: Option[CallContext]): Box[(Transaction, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionLegacy => OutBound, InBoundGetTransactionLegacy => InBound}
        val procedureName = "get_transaction_legacy"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountID, transactionId)
        val result: OBPReturnType[Box[TransactionCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getTransactionDoc61
  private def getTransactionDoc61 = MessageDoc(
    process = "obp.getTransaction",
    messageFormat = messageFormat,
    description = """
               |Get Transaction
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_transaction
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransaction(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      transactionId=TransactionId(transactionIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetTransaction(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TransactionCommons(uuid=transactionUuidExample.value,
      id=TransactionId(transactionIdExample.value),
      thisAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      otherAccount= Counterparty(nationalIdentifier=counterpartyNationalIdentifierExample.value,
      kind=counterpartyKindExample.value,
      counterpartyId=counterpartyIdExample.value,
      counterpartyName=counterpartyNameExample.value,
      thisBankId=BankId(bankIdExample.value),
      thisAccountId=AccountId(accountIdExample.value),
      otherBankRoutingScheme=counterpartyOtherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=Some(counterpartyOtherBankRoutingAddressExample.value),
      otherAccountRoutingScheme=counterpartyOtherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=Some(counterpartyOtherAccountRoutingAddressExample.value),
      otherAccountProvider=counterpartyOtherAccountProviderExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean),
      transactionType=transactionTypeExample.value,
      amount=BigDecimal(transactionAmountExample.value),
      currency=currencyExample.value,
      description=Some(transactionDescriptionExample.value),
      startDate=parseDate(transactionStartDateExample.value).getOrElse(sys.error("transactionStartDateExample.value is not validate date format.")),
      finishDate=parseDate(transactionFinishDateExample.value).getOrElse(sys.error("transactionFinishDateExample.value is not validate date format.")),
      balance=BigDecimal(balanceAmountExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_transaction
  override def getTransaction(bankId: BankId, accountID: AccountId, transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[Box[Transaction]] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransaction => OutBound, InBoundGetTransaction => InBound}
        val procedureName = "get_transaction"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountID, transactionId)
        val result: OBPReturnType[Box[TransactionCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getPhysicalCardsDoc50
  private def getPhysicalCardsDoc50 = MessageDoc(
    process = "obp.getPhysicalCards",
    messageFormat = messageFormat,
    description = """
               |Get Physical Cards
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_physical_cards
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetPhysicalCards(
      user= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetPhysicalCards(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( PhysicalCard(cardId=cardIdExample.value,
      bankId=bankIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      cardType=cardTypeExample.value,
      nameOnCard=nameOnCardExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=new Date(),
      expires=new Date(),
      enabled=true,
      cancelled=true,
      onHotList=true,
      technology="string",
      networks=List("string"),
      allows=List(com.openbankproject.commons.model.CardAction.DEBIT),
      account= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      replacement=Some( CardReplacementInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(new Date())),
      posted=Some(CardPostedInfo(new Date())),
      customerId=customerIdExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_physical_cards
  override def getPhysicalCards(user: User): Box[List[PhysicalCard]] = {
        import com.openbankproject.commons.dto.{OutBoundGetPhysicalCards => OutBound, InBoundGetPhysicalCards => InBound}
        val procedureName = "get_physical_cards"
        val callContext: Option[CallContext] = None
        val req = OutBound(user)
        val result: OBPReturnType[Box[List[PhysicalCard]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getPhysicalCardForBankDoc26
  private def getPhysicalCardForBankDoc26 = MessageDoc(
    process = "obp.getPhysicalCardForBank",
    messageFormat = messageFormat,
    description = """
               |Get Physical Card For Bank
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_physical_card_for_bank
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetPhysicalCardForBank(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      cardId=cardIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetPhysicalCardForBank(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= PhysicalCard(cardId=cardIdExample.value,
      bankId=bankIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      cardType=cardTypeExample.value,
      nameOnCard=nameOnCardExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=new Date(),
      expires=new Date(),
      enabled=true,
      cancelled=true,
      onHotList=true,
      technology="string",
      networks=List("string"),
      allows=List(com.openbankproject.commons.model.CardAction.DEBIT),
      account= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      replacement=Some( CardReplacementInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(new Date())),
      posted=Some(CardPostedInfo(new Date())),
      customerId=customerIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_physical_card_for_bank
  override def getPhysicalCardForBank(bankId: BankId, cardId: String, callContext: Option[CallContext]): OBPReturnType[Box[PhysicalCardTrait]] = {
        import com.openbankproject.commons.dto.{OutBoundGetPhysicalCardForBank => OutBound, InBoundGetPhysicalCardForBank => InBound}
        val procedureName = "get_physical_card_for_bank"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, cardId)
        val result: OBPReturnType[Box[PhysicalCard]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += deletePhysicalCardForBankDoc55
  private def deletePhysicalCardForBankDoc55 = MessageDoc(
    process = "obp.deletePhysicalCardForBank",
    messageFormat = messageFormat,
    description = """
               |Delete Physical Card For Bank
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: delete_physical_card_for_bank
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundDeletePhysicalCardForBank(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      cardId=cardIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundDeletePhysicalCardForBank(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: delete_physical_card_for_bank
  override def deletePhysicalCardForBank(bankId: BankId, cardId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundDeletePhysicalCardForBank => OutBound, InBoundDeletePhysicalCardForBank => InBound}
        val procedureName = "delete_physical_card_for_bank"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, cardId)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getPhysicalCardsForBankLegacyDoc28
  private def getPhysicalCardsForBankLegacyDoc28 = MessageDoc(
    process = "obp.getPhysicalCardsForBankLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Physical Cards For Bank Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_physical_cards_for_bank_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetPhysicalCardsForBankLegacy(
      bank= BankCommons(bankId=BankId(bankIdExample.value),
      shortName=bankShortNameExample.value,
      fullName=bankFullNameExample.value,
      logoUrl=bankLogoUrlExample.value,
      websiteUrl=bankWebsiteUrlExample.value,
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value,
      swiftBic=bankSwiftBicExample.value,
      nationalIdentifier=bankNationalIdentifierExample.value),
      user= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate="string",
      toDate="string")
    ),
    exampleInboundMessage = (
     InBoundGetPhysicalCardsForBankLegacy(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( PhysicalCard(cardId=cardIdExample.value,
      bankId=bankIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      cardType=cardTypeExample.value,
      nameOnCard=nameOnCardExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=new Date(),
      expires=new Date(),
      enabled=true,
      cancelled=true,
      onHotList=true,
      technology="string",
      networks=List("string"),
      allows=List(com.openbankproject.commons.model.CardAction.DEBIT),
      account= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      replacement=Some( CardReplacementInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(new Date())),
      posted=Some(CardPostedInfo(new Date())),
      customerId=customerIdExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_physical_cards_for_bank_legacy
  override def getPhysicalCardsForBankLegacy(bank: Bank, user: User, queryParams: List[OBPQueryParam]): Box[List[PhysicalCard]] = {
        import com.openbankproject.commons.dto.{OutBoundGetPhysicalCardsForBankLegacy => OutBound, InBoundGetPhysicalCardsForBankLegacy => InBound}
        val procedureName = "get_physical_cards_for_bank_legacy"
        val callContext: Option[CallContext] = None
        val req = OutBound(bank, user, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[PhysicalCard]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getPhysicalCardsForBankDoc51
  private def getPhysicalCardsForBankDoc51 = MessageDoc(
    process = "obp.getPhysicalCardsForBank",
    messageFormat = messageFormat,
    description = """
               |Get Physical Cards For Bank
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_physical_cards_for_bank
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetPhysicalCardsForBank(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bank= BankCommons(bankId=BankId(bankIdExample.value),
      shortName=bankShortNameExample.value,
      fullName=bankFullNameExample.value,
      logoUrl=bankLogoUrlExample.value,
      websiteUrl=bankWebsiteUrlExample.value,
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value,
      swiftBic=bankSwiftBicExample.value,
      nationalIdentifier=bankNationalIdentifierExample.value),
      user= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate="string",
      toDate="string")
    ),
    exampleInboundMessage = (
     InBoundGetPhysicalCardsForBank(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( PhysicalCard(cardId=cardIdExample.value,
      bankId=bankIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      cardType=cardTypeExample.value,
      nameOnCard=nameOnCardExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=new Date(),
      expires=new Date(),
      enabled=true,
      cancelled=true,
      onHotList=true,
      technology="string",
      networks=List("string"),
      allows=List(com.openbankproject.commons.model.CardAction.DEBIT),
      account= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      replacement=Some( CardReplacementInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(new Date())),
      posted=Some(CardPostedInfo(new Date())),
      customerId=customerIdExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_physical_cards_for_bank
  override def getPhysicalCardsForBank(bank: Bank, user: User, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[PhysicalCard]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetPhysicalCardsForBank => OutBound, InBoundGetPhysicalCardsForBank => InBound}
        val procedureName = "get_physical_cards_for_bank"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bank, user, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[PhysicalCard]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createPhysicalCardLegacyDoc40
  private def createPhysicalCardLegacyDoc40 = MessageDoc(
    process = "obp.createPhysicalCardLegacy",
    messageFormat = messageFormat,
    description = """
               |Create Physical Card Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_physical_card_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreatePhysicalCardLegacy(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankCardNumber=bankCardNumberExample.value,
      nameOnCard=nameOnCardExample.value,
      cardType=cardTypeExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=new Date(),
      expires=new Date(),
      enabled=true,
      cancelled=true,
      onHotList=true,
      technology="string",
      networks=List("string"),
      allows=List("string"),
      accountId=accountIdExample.value,
      bankId=bankIdExample.value,
      replacement=Some( CardReplacementInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(new Date())),
      posted=Some(CardPostedInfo(new Date())),
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreatePhysicalCardLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= PhysicalCard(cardId=cardIdExample.value,
      bankId=bankIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      cardType=cardTypeExample.value,
      nameOnCard=nameOnCardExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=new Date(),
      expires=new Date(),
      enabled=true,
      cancelled=true,
      onHotList=true,
      technology="string",
      networks=List("string"),
      allows=List(com.openbankproject.commons.model.CardAction.DEBIT),
      account= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      replacement=Some( CardReplacementInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(new Date())),
      posted=Some(CardPostedInfo(new Date())),
      customerId=customerIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_physical_card_legacy
  override def createPhysicalCardLegacy(bankCardNumber: String, nameOnCard: String, cardType: String, issueNumber: String, serialNumber: String, validFrom: Date, expires: Date, enabled: Boolean, cancelled: Boolean, onHotList: Boolean, technology: String, networks: List[String], allows: List[String], accountId: String, bankId: String, replacement: Option[CardReplacementInfo], pinResets: List[PinResetInfo], collected: Option[CardCollectionInfo], posted: Option[CardPostedInfo], customerId: String, callContext: Option[CallContext]): Box[PhysicalCard] = {
        import com.openbankproject.commons.dto.{OutBoundCreatePhysicalCardLegacy => OutBound, InBoundCreatePhysicalCardLegacy => InBound}
        val procedureName = "create_physical_card_legacy"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankCardNumber, nameOnCard, cardType, issueNumber, serialNumber, validFrom, expires, enabled, cancelled, onHotList, technology, networks, allows, accountId, bankId, replacement, pinResets, collected, posted, customerId)
        val result: OBPReturnType[Box[PhysicalCard]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createPhysicalCardDoc79
  private def createPhysicalCardDoc79 = MessageDoc(
    process = "obp.createPhysicalCard",
    messageFormat = messageFormat,
    description = """
               |Create Physical Card
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_physical_card
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreatePhysicalCard(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankCardNumber=bankCardNumberExample.value,
      nameOnCard=nameOnCardExample.value,
      cardType=cardTypeExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=new Date(),
      expires=new Date(),
      enabled=true,
      cancelled=true,
      onHotList=true,
      technology="string",
      networks=List("string"),
      allows=List("string"),
      accountId=accountIdExample.value,
      bankId=bankIdExample.value,
      replacement=Some( CardReplacementInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(new Date())),
      posted=Some(CardPostedInfo(new Date())),
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreatePhysicalCard(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= PhysicalCard(cardId=cardIdExample.value,
      bankId=bankIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      cardType=cardTypeExample.value,
      nameOnCard=nameOnCardExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=new Date(),
      expires=new Date(),
      enabled=true,
      cancelled=true,
      onHotList=true,
      technology="string",
      networks=List("string"),
      allows=List(com.openbankproject.commons.model.CardAction.DEBIT),
      account= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      replacement=Some( CardReplacementInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(new Date())),
      posted=Some(CardPostedInfo(new Date())),
      customerId=customerIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_physical_card
  override def createPhysicalCard(bankCardNumber: String, nameOnCard: String, cardType: String, issueNumber: String, serialNumber: String, validFrom: Date, expires: Date, enabled: Boolean, cancelled: Boolean, onHotList: Boolean, technology: String, networks: List[String], allows: List[String], accountId: String, bankId: String, replacement: Option[CardReplacementInfo], pinResets: List[PinResetInfo], collected: Option[CardCollectionInfo], posted: Option[CardPostedInfo], customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[PhysicalCard]] = {
        import com.openbankproject.commons.dto.{OutBoundCreatePhysicalCard => OutBound, InBoundCreatePhysicalCard => InBound}
        val procedureName = "create_physical_card"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankCardNumber, nameOnCard, cardType, issueNumber, serialNumber, validFrom, expires, enabled, cancelled, onHotList, technology, networks, allows, accountId, bankId, replacement, pinResets, collected, posted, customerId)
        val result: OBPReturnType[Box[PhysicalCard]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += updatePhysicalCardDoc7
  private def updatePhysicalCardDoc7 = MessageDoc(
    process = "obp.updatePhysicalCard",
    messageFormat = messageFormat,
    description = """
               |Update Physical Card
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: update_physical_card
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdatePhysicalCard(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      cardId=cardIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      nameOnCard=nameOnCardExample.value,
      cardType=cardTypeExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=new Date(),
      expires=new Date(),
      enabled=true,
      cancelled=true,
      onHotList=true,
      technology="string",
      networks=List("string"),
      allows=List("string"),
      accountId=accountIdExample.value,
      bankId=bankIdExample.value,
      replacement=Some( CardReplacementInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(new Date())),
      posted=Some(CardPostedInfo(new Date())),
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundUpdatePhysicalCard(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= PhysicalCard(cardId=cardIdExample.value,
      bankId=bankIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      cardType=cardTypeExample.value,
      nameOnCard=nameOnCardExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=new Date(),
      expires=new Date(),
      enabled=true,
      cancelled=true,
      onHotList=true,
      technology="string",
      networks=List("string"),
      allows=List(com.openbankproject.commons.model.CardAction.DEBIT),
      account= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      replacement=Some( CardReplacementInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=new Date(),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(new Date())),
      posted=Some(CardPostedInfo(new Date())),
      customerId=customerIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: update_physical_card
  override def updatePhysicalCard(cardId: String, bankCardNumber: String, nameOnCard: String, cardType: String, issueNumber: String, serialNumber: String, validFrom: Date, expires: Date, enabled: Boolean, cancelled: Boolean, onHotList: Boolean, technology: String, networks: List[String], allows: List[String], accountId: String, bankId: String, replacement: Option[CardReplacementInfo], pinResets: List[PinResetInfo], collected: Option[CardCollectionInfo], posted: Option[CardPostedInfo], customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[PhysicalCardTrait]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdatePhysicalCard => OutBound, InBoundUpdatePhysicalCard => InBound}
        val procedureName = "update_physical_card"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , cardId, bankCardNumber, nameOnCard, cardType, issueNumber, serialNumber, validFrom, expires, enabled, cancelled, onHotList, technology, networks, allows, accountId, bankId, replacement, pinResets, collected, posted, customerId)
        val result: OBPReturnType[Box[PhysicalCard]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += makePaymentDoc71
  private def makePaymentDoc71 = MessageDoc(
    process = "obp.makePayment",
    messageFormat = messageFormat,
    description = """
               |Make Payment
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: make_payment
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundMakePayment(
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      fromAccountUID= BankIdAccountId(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value)),
      toAccountUID= BankIdAccountId(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value)),
      amt=BigDecimal("123.321"),
      description="string",
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value))
    ),
    exampleInboundMessage = (
     InBoundMakePayment(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=TransactionId(transactionIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: make_payment
  override def makePayment(initiator: User, fromAccountUID: BankIdAccountId, toAccountUID: BankIdAccountId, amt: BigDecimal, description: String, transactionRequestType: TransactionRequestType): Box[TransactionId] = {
        import com.openbankproject.commons.dto.{OutBoundMakePayment => OutBound, InBoundMakePayment => InBound}
        val procedureName = "make_payment"
        val callContext: Option[CallContext] = None
        val req = OutBound(initiator, fromAccountUID, toAccountUID, amt, description, transactionRequestType)
        val result: OBPReturnType[Box[TransactionId]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += makePaymentv200Doc60
  private def makePaymentv200Doc60 = MessageDoc(
    process = "obp.makePaymentv200",
    messageFormat = messageFormat,
    description = """
               |Make Paymentv200
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: make_paymentv200
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundMakePaymentv200(
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      transactionRequestCommonBody= TransactionRequestCommonBodyJSONCommons(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string"),
      amount=BigDecimal("123.321"),
      description="string",
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      chargePolicy="string")
    ),
    exampleInboundMessage = (
     InBoundMakePaymentv200(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=TransactionId(transactionIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: make_paymentv200
  override def makePaymentv200(fromAccount: BankAccount, toAccount: BankAccount, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, amount: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String): Box[TransactionId] = {
        import com.openbankproject.commons.dto.{OutBoundMakePaymentv200 => OutBound, InBoundMakePaymentv200 => InBound}
        val procedureName = "make_paymentv200"
        val callContext: Option[CallContext] = None
        val req = OutBound(fromAccount, toAccount, transactionRequestCommonBody, amount, description, transactionRequestType, chargePolicy)
        val result: OBPReturnType[Box[TransactionId]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += makePaymentv210Doc26
  private def makePaymentv210Doc26 = MessageDoc(
    process = "obp.makePaymentv210",
    messageFormat = messageFormat,
    description = """
               |Make Paymentv210
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: make_paymentv210
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundMakePaymentv210(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      transactionRequestCommonBody= TransactionRequestCommonBodyJSONCommons(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string"),
      amount=BigDecimal("123.321"),
      description="string",
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      chargePolicy="string")
    ),
    exampleInboundMessage = (
     InBoundMakePaymentv210(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=TransactionId(transactionIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: make_paymentv210
  override def makePaymentv210(fromAccount: BankAccount, toAccount: BankAccount, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, amount: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String, callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]] = {
        import com.openbankproject.commons.dto.{OutBoundMakePaymentv210 => OutBound, InBoundMakePaymentv210 => InBound}
        val procedureName = "make_paymentv210"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , fromAccount, toAccount, transactionRequestCommonBody, amount, description, transactionRequestType, chargePolicy)
        val result: OBPReturnType[Box[TransactionId]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += makePaymentImplDoc4
  private def makePaymentImplDoc4 = MessageDoc(
    process = "obp.makePaymentImpl",
    messageFormat = messageFormat,
    description = """
               |Make Payment Impl
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: make_payment_impl
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundMakePaymentImpl(
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      transactionRequestCommonBody= TransactionRequestCommonBodyJSONCommons(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string"),
      amt=BigDecimal("123.321"),
      description="string",
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      chargePolicy="string")
    ),
    exampleInboundMessage = (
     InBoundMakePaymentImpl(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=TransactionId(transactionIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: make_payment_impl
  override def makePaymentImpl(fromAccount: BankAccount, toAccount: BankAccount, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, amt: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String): Box[TransactionId] = {
        import com.openbankproject.commons.dto.{OutBoundMakePaymentImpl => OutBound, InBoundMakePaymentImpl => InBound}
        val procedureName = "make_payment_impl"
        val callContext: Option[CallContext] = None
        val req = OutBound(fromAccount, toAccount, transactionRequestCommonBody, amt, description, transactionRequestType, chargePolicy)
        val result: OBPReturnType[Box[TransactionId]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createTransactionRequestDoc23
  private def createTransactionRequestDoc23 = MessageDoc(
    process = "obp.createTransactionRequest",
    messageFormat = messageFormat,
    description = """
               |Create Transaction Request
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_transaction_request
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionRequest(
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      body= TransactionRequestBody(to= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"))
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionRequest(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_transaction_request
  override def createTransactionRequest(initiator: User, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody): Box[TransactionRequest] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionRequest => OutBound, InBoundCreateTransactionRequest => InBound}
        val procedureName = "create_transaction_request"
        val callContext: Option[CallContext] = None
        val req = OutBound(initiator, fromAccount, toAccount, transactionRequestType, body)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createTransactionRequestv200Doc10
  private def createTransactionRequestv200Doc10 = MessageDoc(
    process = "obp.createTransactionRequestv200",
    messageFormat = messageFormat,
    description = """
               |Create Transaction Requestv200
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_transaction_requestv200
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionRequestv200(
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      body= TransactionRequestBody(to= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"))
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionRequestv200(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_transaction_requestv200
  override def createTransactionRequestv200(initiator: User, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody): Box[TransactionRequest] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionRequestv200 => OutBound, InBoundCreateTransactionRequestv200 => InBound}
        val procedureName = "create_transaction_requestv200"
        val callContext: Option[CallContext] = None
        val req = OutBound(initiator, fromAccount, toAccount, transactionRequestType, body)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createTransactionRequestv210Doc79
  private def createTransactionRequestv210Doc79 = MessageDoc(
    process = "obp.createTransactionRequestv210",
    messageFormat = messageFormat,
    description = """
               |Create Transaction Requestv210
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_transaction_requestv210
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionRequestv210(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      viewId=ViewId(viewIdExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      transactionRequestCommonBody= TransactionRequestCommonBodyJSONCommons(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string"),
      detailsPlain="string",
      chargePolicy="string",
      challengeType=Some("string"),
      scaMethod=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SMS))
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionRequestv210(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_transaction_requestv210
  override def createTransactionRequestv210(initiator: User, viewId: ViewId, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, detailsPlain: String, chargePolicy: String, challengeType: Option[String], scaMethod: Option[SCA], callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionRequestv210 => OutBound, InBoundCreateTransactionRequestv210 => InBound}
        val procedureName = "create_transaction_requestv210"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , initiator, viewId, fromAccount, toAccount, transactionRequestType, transactionRequestCommonBody, detailsPlain, chargePolicy, challengeType, scaMethod)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createTransactionRequestImplDoc78
  private def createTransactionRequestImplDoc78 = MessageDoc(
    process = "obp.createTransactionRequestImpl",
    messageFormat = messageFormat,
    description = """
               |Create Transaction Request Impl
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_transaction_request_impl
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionRequestImpl(
      transactionRequestId=TransactionRequestId("string"),
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      counterparty= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=counterpartyNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      body= TransactionRequestBody(to= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      status="string",
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")))
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionRequestImpl(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_transaction_request_impl
  override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType, fromAccount: BankAccount, counterparty: BankAccount, body: TransactionRequestBody, status: String, charge: TransactionRequestCharge): Box[TransactionRequest] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionRequestImpl => OutBound, InBoundCreateTransactionRequestImpl => InBound}
        val procedureName = "create_transaction_request_impl"
        val callContext: Option[CallContext] = None
        val req = OutBound(transactionRequestId, transactionRequestType, fromAccount, counterparty, body, status, charge)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createTransactionRequestImpl210Doc24
  private def createTransactionRequestImpl210Doc24 = MessageDoc(
    process = "obp.createTransactionRequestImpl210",
    messageFormat = messageFormat,
    description = """
               |Create Transaction Request Impl210
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_transaction_request_impl210
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionRequestImpl210(
      transactionRequestId=TransactionRequestId("string"),
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      transactionRequestCommonBody= TransactionRequestCommonBodyJSONCommons(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string"),
      details="string",
      status="string",
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      chargePolicy="string")
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionRequestImpl210(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_transaction_request_impl210
  override def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, details: String, status: String, charge: TransactionRequestCharge, chargePolicy: String): Box[TransactionRequest] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionRequestImpl210 => OutBound, InBoundCreateTransactionRequestImpl210 => InBound}
        val procedureName = "create_transaction_request_impl210"
        val callContext: Option[CallContext] = None
        val req = OutBound(transactionRequestId, transactionRequestType, fromAccount, toAccount, transactionRequestCommonBody, details, status, charge, chargePolicy)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getTransactionRequestsDoc43
  private def getTransactionRequestsDoc43 = MessageDoc(
    process = "obp.getTransactionRequests",
    messageFormat = messageFormat,
    description = """
               |Get Transaction Requests
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_transaction_requests
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionRequests(
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetTransactionRequests(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string"))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_transaction_requests
  override def getTransactionRequests(initiator: User, fromAccount: BankAccount): Box[List[TransactionRequest]] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionRequests => OutBound, InBoundGetTransactionRequests => InBound}
        val procedureName = "get_transaction_requests"
        val callContext: Option[CallContext] = None
        val req = OutBound(initiator, fromAccount)
        val result: OBPReturnType[Box[List[TransactionRequest]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getTransactionRequests210Doc26
  private def getTransactionRequests210Doc26 = MessageDoc(
    process = "obp.getTransactionRequests210",
    messageFormat = messageFormat,
    description = """
               |Get Transaction Requests210
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_transaction_requests210
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionRequests210(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetTransactionRequests210(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string"))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_transaction_requests210
  override def getTransactionRequests210(initiator: User, fromAccount: BankAccount, callContext: Option[CallContext]): Box[(List[TransactionRequest], Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionRequests210 => OutBound, InBoundGetTransactionRequests210 => InBound}
        val procedureName = "get_transaction_requests210"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , initiator, fromAccount)
        val result: OBPReturnType[Box[List[TransactionRequest]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getTransactionRequestsImplDoc18
  private def getTransactionRequestsImplDoc18 = MessageDoc(
    process = "obp.getTransactionRequestsImpl",
    messageFormat = messageFormat,
    description = """
               |Get Transaction Requests Impl
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_transaction_requests_impl
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionRequestsImpl(
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetTransactionRequestsImpl(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string"))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_transaction_requests_impl
  override def getTransactionRequestsImpl(fromAccount: BankAccount): Box[List[TransactionRequest]] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionRequestsImpl => OutBound, InBoundGetTransactionRequestsImpl => InBound}
        val procedureName = "get_transaction_requests_impl"
        val callContext: Option[CallContext] = None
        val req = OutBound(fromAccount)
        val result: OBPReturnType[Box[List[TransactionRequest]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getTransactionRequestsImpl210Doc81
  private def getTransactionRequestsImpl210Doc81 = MessageDoc(
    process = "obp.getTransactionRequestsImpl210",
    messageFormat = messageFormat,
    description = """
               |Get Transaction Requests Impl210
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_transaction_requests_impl210
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionRequestsImpl210(
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetTransactionRequestsImpl210(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string"))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_transaction_requests_impl210
  override def getTransactionRequestsImpl210(fromAccount: BankAccount): Box[List[TransactionRequest]] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionRequestsImpl210 => OutBound, InBoundGetTransactionRequestsImpl210 => InBound}
        val procedureName = "get_transaction_requests_impl210"
        val callContext: Option[CallContext] = None
        val req = OutBound(fromAccount)
        val result: OBPReturnType[Box[List[TransactionRequest]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getTransactionRequestImplDoc91
  private def getTransactionRequestImplDoc91 = MessageDoc(
    process = "obp.getTransactionRequestImpl",
    messageFormat = messageFormat,
    description = """
               |Get Transaction Request Impl
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_transaction_request_impl
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionRequestImpl(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      transactionRequestId=TransactionRequestId("string"))
    ),
    exampleInboundMessage = (
     InBoundGetTransactionRequestImpl(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_transaction_request_impl
  override def getTransactionRequestImpl(transactionRequestId: TransactionRequestId, callContext: Option[CallContext]): Box[(TransactionRequest, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionRequestImpl => OutBound, InBoundGetTransactionRequestImpl => InBound}
        val procedureName = "get_transaction_request_impl"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , transactionRequestId)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getTransactionRequestTypesImplDoc86
  private def getTransactionRequestTypesImplDoc86 = MessageDoc(
    process = "obp.getTransactionRequestTypesImpl",
    messageFormat = messageFormat,
    description = """
               |Get Transaction Request Types Impl
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_transaction_request_types_impl
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionRequestTypesImpl(
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetTransactionRequestTypesImpl(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List(TransactionRequestType(transactionRequestTypeExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_transaction_request_types_impl
  override def getTransactionRequestTypesImpl(fromAccount: BankAccount): Box[List[TransactionRequestType]] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionRequestTypesImpl => OutBound, InBoundGetTransactionRequestTypesImpl => InBound}
        val procedureName = "get_transaction_request_types_impl"
        val callContext: Option[CallContext] = None
        val req = OutBound(fromAccount)
        val result: OBPReturnType[Box[List[TransactionRequestType]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createTransactionAfterChallengeDoc63
  private def createTransactionAfterChallengeDoc63 = MessageDoc(
    process = "obp.createTransactionAfterChallenge",
    messageFormat = messageFormat,
    description = """
               |Create Transaction After Challenge
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_transaction_after_challenge
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionAfterChallenge(
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      transReqId=TransactionRequestId("string"))
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionAfterChallenge(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_transaction_after_challenge
  override def createTransactionAfterChallenge(initiator: User, transReqId: TransactionRequestId): Box[TransactionRequest] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionAfterChallenge => OutBound, InBoundCreateTransactionAfterChallenge => InBound}
        val procedureName = "create_transaction_after_challenge"
        val callContext: Option[CallContext] = None
        val req = OutBound(initiator, transReqId)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createTransactionAfterChallengev200Doc63
  private def createTransactionAfterChallengev200Doc63 = MessageDoc(
    process = "obp.createTransactionAfterChallengev200",
    messageFormat = messageFormat,
    description = """
               |Create Transaction After Challengev200
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_transaction_after_challengev200
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionAfterChallengev200(
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      transactionRequest= TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionAfterChallengev200(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_transaction_after_challengev200
  override def createTransactionAfterChallengev200(fromAccount: BankAccount, toAccount: BankAccount, transactionRequest: TransactionRequest): Box[TransactionRequest] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionAfterChallengev200 => OutBound, InBoundCreateTransactionAfterChallengev200 => InBound}
        val procedureName = "create_transaction_after_challengev200"
        val callContext: Option[CallContext] = None
        val req = OutBound(fromAccount, toAccount, transactionRequest)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createTransactionAfterChallengeV210Doc26
  private def createTransactionAfterChallengeV210Doc26 = MessageDoc(
    process = "obp.createTransactionAfterChallengeV210",
    messageFormat = messageFormat,
    description = """
               |Create Transaction After Challenge V210
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_transaction_after_challenge_v210
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionAfterChallengeV210(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      transactionRequest= TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionAfterChallengeV210(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_transaction_after_challenge_v210
  override def createTransactionAfterChallengeV210(fromAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionAfterChallengeV210 => OutBound, InBoundCreateTransactionAfterChallengeV210 => InBound}
        val procedureName = "create_transaction_after_challenge_v210"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , fromAccount, transactionRequest)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += updateBankAccountDoc3
  private def updateBankAccountDoc3 = MessageDoc(
    process = "obp.updateBankAccount",
    messageFormat = messageFormat,
    description = """
               |Update Bank Account
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: update_bank_account
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdateBankAccount(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      accountLabel="string",
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value)
    ),
    exampleInboundMessage = (
     InBoundUpdateBankAccount(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: update_bank_account
  override def updateBankAccount(bankId: BankId, accountId: AccountId, accountType: String, accountLabel: String, branchId: String, accountRoutingScheme: String, accountRoutingAddress: String, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdateBankAccount => OutBound, InBoundUpdateBankAccount => InBound}
        val procedureName = "update_bank_account"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, accountType, accountLabel, branchId, accountRoutingScheme, accountRoutingAddress)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createBankAccountDoc64
  private def createBankAccountDoc64 = MessageDoc(
    process = "obp.createBankAccount",
    messageFormat = messageFormat,
    description = """
               |Create Bank Account
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_bank_account
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateBankAccount(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      accountLabel="string",
      currency=currencyExample.value,
      initialBalance=BigDecimal("123.321"),
      accountHolderName="string",
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateBankAccount(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_bank_account
  override def createBankAccount(bankId: BankId, accountId: AccountId, accountType: String, accountLabel: String, currency: String, initialBalance: BigDecimal, accountHolderName: String, branchId: String, accountRoutingScheme: String, accountRoutingAddress: String, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateBankAccount => OutBound, InBoundCreateBankAccount => InBound}
        val procedureName = "create_bank_account"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, accountType, accountLabel, currency, initialBalance, accountHolderName, branchId, accountRoutingScheme, accountRoutingAddress)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createBankAccountLegacyDoc77
  private def createBankAccountLegacyDoc77 = MessageDoc(
    process = "obp.createBankAccountLegacy",
    messageFormat = messageFormat,
    description = """
               |Create Bank Account Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_bank_account_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateBankAccountLegacy(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      accountLabel="string",
      currency=currencyExample.value,
      initialBalance=BigDecimal("123.321"),
      accountHolderName="string",
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateBankAccountLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_bank_account_legacy
  override def createBankAccountLegacy(bankId: BankId, accountId: AccountId, accountType: String, accountLabel: String, currency: String, initialBalance: BigDecimal, accountHolderName: String, branchId: String, accountRoutingScheme: String, accountRoutingAddress: String): Box[BankAccount] = {
        import com.openbankproject.commons.dto.{OutBoundCreateBankAccountLegacy => OutBound, InBoundCreateBankAccountLegacy => InBound}
        val procedureName = "create_bank_account_legacy"
        val callContext: Option[CallContext] = None
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, accountType, accountLabel, currency, initialBalance, accountHolderName, branchId, accountRoutingScheme, accountRoutingAddress)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getProductsDoc44
  private def getProductsDoc44 = MessageDoc(
    process = "obp.getProducts",
    messageFormat = messageFormat,
    description = """
               |Get Products
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_products
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProducts(
      bankId=BankId(bankIdExample.value), Nil)
    ),
    exampleInboundMessage = (
     InBoundGetProducts(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( ProductCommons(bankId=BankId(bankIdExample.value),
      code=ProductCode("string"),
      parentProductCode=ProductCode("string"),
      name="string",
      category="string",
      family="string",
      superFamily="string",
      moreInfoUrl="string",
      details="string",
      description="string",
      meta=Meta( License(id="string",
      name="string")))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_products
  override def getProducts(bankId: BankId, params: Map[String, List[String]]): Box[List[Product]] = {
        import com.openbankproject.commons.dto.{OutBoundGetProducts => OutBound, InBoundGetProducts => InBound}
        val procedureName = "get_products"
        val callContext: Option[CallContext] = None
        val req = OutBound(bankId, params)
        val result: OBPReturnType[Box[List[ProductCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getProductDoc32
  private def getProductDoc32 = MessageDoc(
    process = "obp.getProduct",
    messageFormat = messageFormat,
    description = """
               |Get Product
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_product
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProduct(
      bankId=BankId(bankIdExample.value),
      productCode=ProductCode("string"))
    ),
    exampleInboundMessage = (
     InBoundGetProduct(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= ProductCommons(bankId=BankId(bankIdExample.value),
      code=ProductCode("string"),
      parentProductCode=ProductCode("string"),
      name="string",
      category="string",
      family="string",
      superFamily="string",
      moreInfoUrl="string",
      details="string",
      description="string",
      meta=Meta( License(id="string",
      name="string"))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_product
  override def getProduct(bankId: BankId, productCode: ProductCode): Box[Product] = {
        import com.openbankproject.commons.dto.{OutBoundGetProduct => OutBound, InBoundGetProduct => InBound}
        val procedureName = "get_product"
        val callContext: Option[CallContext] = None
        val req = OutBound(bankId, productCode)
        val result: OBPReturnType[Box[ProductCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createOrUpdateBankDoc78
  private def createOrUpdateBankDoc78 = MessageDoc(
    process = "obp.createOrUpdateBank",
    messageFormat = messageFormat,
    description = """
               |Create Or Update Bank
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_or_update_bank
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateBank(
      bankId=bankIdExample.value,
      fullBankName="string",
      shortBankName="string",
      logoURL="string",
      websiteURL="string",
      swiftBIC="string",
      national_identifier="string",
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateBank(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BankCommons(bankId=BankId(bankIdExample.value),
      shortName=bankShortNameExample.value,
      fullName=bankFullNameExample.value,
      logoUrl=bankLogoUrlExample.value,
      websiteUrl=bankWebsiteUrlExample.value,
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value,
      swiftBic=bankSwiftBicExample.value,
      nationalIdentifier=bankNationalIdentifierExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_or_update_bank
  override def createOrUpdateBank(bankId: String, fullBankName: String, shortBankName: String, logoURL: String, websiteURL: String, swiftBIC: String, national_identifier: String, bankRoutingScheme: String, bankRoutingAddress: String): Box[Bank] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateBank => OutBound, InBoundCreateOrUpdateBank => InBound}
        val procedureName = "create_or_update_bank"
        val callContext: Option[CallContext] = None
        val req = OutBound(bankId, fullBankName, shortBankName, logoURL, websiteURL, swiftBIC, national_identifier, bankRoutingScheme, bankRoutingAddress)
        val result: OBPReturnType[Box[BankCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createOrUpdateProductDoc90
  private def createOrUpdateProductDoc90 = MessageDoc(
    process = "obp.createOrUpdateProduct",
    messageFormat = messageFormat,
    description = """
               |Create Or Update Product
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_or_update_product
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateProduct(
      bankId=bankIdExample.value,
      code="string",
      parentProductCode=Some("string"),
      name="string",
      category="string",
      family="string",
      superFamily="string",
      moreInfoUrl="string",
      details="string",
      description="string",
      metaLicenceId="string",
      metaLicenceName="string")
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateProduct(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= ProductCommons(bankId=BankId(bankIdExample.value),
      code=ProductCode("string"),
      parentProductCode=ProductCode("string"),
      name="string",
      category="string",
      family="string",
      superFamily="string",
      moreInfoUrl="string",
      details="string",
      description="string",
      meta=Meta( License(id="string",
      name="string"))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_or_update_product
  override def createOrUpdateProduct(bankId: String, code: String, parentProductCode: Option[String], name: String, category: String, family: String, superFamily: String, moreInfoUrl: String, details: String, description: String, metaLicenceId: String, metaLicenceName: String): Box[Product] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateProduct => OutBound, InBoundCreateOrUpdateProduct => InBound}
        val procedureName = "create_or_update_product"
        val callContext: Option[CallContext] = None
        val req = OutBound(bankId, code, parentProductCode, name, category, family, superFamily, moreInfoUrl, details, description, metaLicenceId, metaLicenceName)
        val result: OBPReturnType[Box[ProductCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBranchLegacyDoc88
  private def getBranchLegacyDoc88 = MessageDoc(
    process = "obp.getBranchLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Branch Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_branch_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBranchLegacy(
      bankId=BankId(bankIdExample.value),
      branchId=BranchId(branchIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetBranchLegacy(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BranchTCommons(branchId=BranchId(branchIdExample.value),
      bankId=BankId(bankIdExample.value),
      name="string",
      address= Address(line1="string",
      line2="string",
      line3="string",
      city="string",
      county=Some("string"),
      state="string",
      postCode="string",
      countryCode="string"),
      location= Location(latitude=123.123,
      longitude=123.123,
      date=Some(new Date()),
      user=Some( BasicResourceUser(userId=userIdExample.value,
      provider="string",
      username=usernameExample.value))),
      lobbyString=Some(LobbyString("string")),
      driveUpString=Some(DriveUpString("string")),
      meta=Meta( License(id="string",
      name="string")),
      branchRouting=Some( Routing(scheme=branchRoutingSchemeExample.value,
      address=branchRoutingAddressExample.value)),
      lobby=Some( Lobby(monday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      tuesday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      wednesday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      thursday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      friday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      saturday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      sunday=List( OpeningTimes(openingTime="string",
      closingTime="string")))),
      driveUp=Some( DriveUp(monday= OpeningTimes(openingTime="string",
      closingTime="string"),
      tuesday= OpeningTimes(openingTime="string",
      closingTime="string"),
      wednesday= OpeningTimes(openingTime="string",
      closingTime="string"),
      thursday= OpeningTimes(openingTime="string",
      closingTime="string"),
      friday= OpeningTimes(openingTime="string",
      closingTime="string"),
      saturday= OpeningTimes(openingTime="string",
      closingTime="string"),
      sunday= OpeningTimes(openingTime="string",
      closingTime="string"))),
      isAccessible=Some(true),
      accessibleFeatures=Some("string"),
      branchType=Some("string"),
      moreInfo=Some("string"),
      phoneNumber=Some("string"),
      isDeleted=Some(true)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_branch_legacy
  override def getBranchLegacy(bankId: BankId, branchId: BranchId): Box[BranchT] = {
        import com.openbankproject.commons.dto.{OutBoundGetBranchLegacy => OutBound, InBoundGetBranchLegacy => InBound}
        val procedureName = "get_branch_legacy"
        val callContext: Option[CallContext] = None
        val req = OutBound(bankId, branchId)
        val result: OBPReturnType[Box[BranchTCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBranchDoc57
  private def getBranchDoc57 = MessageDoc(
    process = "obp.getBranch",
    messageFormat = messageFormat,
    description = """
               |Get Branch
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_branch
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBranch(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      branchId=BranchId(branchIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetBranch(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= BranchTCommons(branchId=BranchId(branchIdExample.value),
      bankId=BankId(bankIdExample.value),
      name="string",
      address= Address(line1="string",
      line2="string",
      line3="string",
      city="string",
      county=Some("string"),
      state="string",
      postCode="string",
      countryCode="string"),
      location= Location(latitude=123.123,
      longitude=123.123,
      date=Some(new Date()),
      user=Some( BasicResourceUser(userId=userIdExample.value,
      provider="string",
      username=usernameExample.value))),
      lobbyString=Some(LobbyString("string")),
      driveUpString=Some(DriveUpString("string")),
      meta=Meta( License(id="string",
      name="string")),
      branchRouting=Some( Routing(scheme=branchRoutingSchemeExample.value,
      address=branchRoutingAddressExample.value)),
      lobby=Some( Lobby(monday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      tuesday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      wednesday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      thursday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      friday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      saturday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      sunday=List( OpeningTimes(openingTime="string",
      closingTime="string")))),
      driveUp=Some( DriveUp(monday= OpeningTimes(openingTime="string",
      closingTime="string"),
      tuesday= OpeningTimes(openingTime="string",
      closingTime="string"),
      wednesday= OpeningTimes(openingTime="string",
      closingTime="string"),
      thursday= OpeningTimes(openingTime="string",
      closingTime="string"),
      friday= OpeningTimes(openingTime="string",
      closingTime="string"),
      saturday= OpeningTimes(openingTime="string",
      closingTime="string"),
      sunday= OpeningTimes(openingTime="string",
      closingTime="string"))),
      isAccessible=Some(true),
      accessibleFeatures=Some("string"),
      branchType=Some("string"),
      moreInfo=Some("string"),
      phoneNumber=Some("string"),
      isDeleted=Some(true)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_branch
  override def getBranch(bankId: BankId, branchId: BranchId, callContext: Option[CallContext]): Future[Box[(BranchT, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBranch => OutBound, InBoundGetBranch => InBound}
        val procedureName = "get_branch"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, branchId)
        val result: OBPReturnType[Box[BranchTCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getBranchesDoc2
  private def getBranchesDoc2 = MessageDoc(
    process = "obp.getBranches",
    messageFormat = messageFormat,
    description = """
               |Get Branches
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_branches
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBranches(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate="string",
      toDate="string")
    ),
    exampleInboundMessage = (
     InBoundGetBranches(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( BranchTCommons(branchId=BranchId(branchIdExample.value),
      bankId=BankId(bankIdExample.value),
      name="string",
      address= Address(line1="string",
      line2="string",
      line3="string",
      city="string",
      county=Some("string"),
      state="string",
      postCode="string",
      countryCode="string"),
      location= Location(latitude=123.123,
      longitude=123.123,
      date=Some(new Date()),
      user=Some( BasicResourceUser(userId=userIdExample.value,
      provider="string",
      username=usernameExample.value))),
      lobbyString=Some(LobbyString("string")),
      driveUpString=Some(DriveUpString("string")),
      meta=Meta( License(id="string",
      name="string")),
      branchRouting=Some( Routing(scheme=branchRoutingSchemeExample.value,
      address=branchRoutingAddressExample.value)),
      lobby=Some( Lobby(monday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      tuesday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      wednesday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      thursday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      friday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      saturday=List( OpeningTimes(openingTime="string",
      closingTime="string")),
      sunday=List( OpeningTimes(openingTime="string",
      closingTime="string")))),
      driveUp=Some( DriveUp(monday= OpeningTimes(openingTime="string",
      closingTime="string"),
      tuesday= OpeningTimes(openingTime="string",
      closingTime="string"),
      wednesday= OpeningTimes(openingTime="string",
      closingTime="string"),
      thursday= OpeningTimes(openingTime="string",
      closingTime="string"),
      friday= OpeningTimes(openingTime="string",
      closingTime="string"),
      saturday= OpeningTimes(openingTime="string",
      closingTime="string"),
      sunday= OpeningTimes(openingTime="string",
      closingTime="string"))),
      isAccessible=Some(true),
      accessibleFeatures=Some("string"),
      branchType=Some("string"),
      moreInfo=Some("string"),
      phoneNumber=Some("string"),
      isDeleted=Some(true))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_branches
  override def getBranches(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[(List[BranchT], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBranches => OutBound, InBoundGetBranches => InBound}
        val procedureName = "get_branches"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[BranchTCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getAtmLegacyDoc52
  private def getAtmLegacyDoc52 = MessageDoc(
    process = "obp.getAtmLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Atm Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_atm_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetAtmLegacy(
      bankId=BankId(bankIdExample.value),
      atmId=AtmId("string"))
    ),
    exampleInboundMessage = (
     InBoundGetAtmLegacy(
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= AtmTCommons(atmId=AtmId("string"),
      bankId=BankId(bankIdExample.value),
      name="string",
      address= Address(line1="string",
      line2="string",
      line3="string",
      city="string",
      county=Some("string"),
      state="string",
      postCode="string",
      countryCode="string"),
      location= Location(latitude=123.123,
      longitude=123.123,
      date=Some(new Date()),
      user=Some( BasicResourceUser(userId=userIdExample.value,
      provider="string",
      username=usernameExample.value))),
      meta=Meta( License(id="string",
      name="string")),
      OpeningTimeOnMonday=Some("string"),
      ClosingTimeOnMonday=Some("string"),
      OpeningTimeOnTuesday=Some("string"),
      ClosingTimeOnTuesday=Some("string"),
      OpeningTimeOnWednesday=Some("string"),
      ClosingTimeOnWednesday=Some("string"),
      OpeningTimeOnThursday=Some("string"),
      ClosingTimeOnThursday=Some("string"),
      OpeningTimeOnFriday=Some("string"),
      ClosingTimeOnFriday=Some("string"),
      OpeningTimeOnSaturday=Some("string"),
      ClosingTimeOnSaturday=Some("string"),
      OpeningTimeOnSunday=Some("string"),
      ClosingTimeOnSunday=Some("string"),
      isAccessible=Some(true),
      locatedAt=Some("string"),
      moreInfo=Some("string"),
      hasDepositCapability=Some(true)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_atm_legacy
  override def getAtmLegacy(bankId: BankId, atmId: AtmId): Box[AtmT] = {
        import com.openbankproject.commons.dto.{OutBoundGetAtmLegacy => OutBound, InBoundGetAtmLegacy => InBound}
        val procedureName = "get_atm_legacy"
        val callContext: Option[CallContext] = None
        val req = OutBound(bankId, atmId)
        val result: OBPReturnType[Box[AtmTCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getAtmDoc78
  private def getAtmDoc78 = MessageDoc(
    process = "obp.getAtm",
    messageFormat = messageFormat,
    description = """
               |Get Atm
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_atm
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetAtm(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      atmId=AtmId("string"))
    ),
    exampleInboundMessage = (
     InBoundGetAtm(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= AtmTCommons(atmId=AtmId("string"),
      bankId=BankId(bankIdExample.value),
      name="string",
      address= Address(line1="string",
      line2="string",
      line3="string",
      city="string",
      county=Some("string"),
      state="string",
      postCode="string",
      countryCode="string"),
      location= Location(latitude=123.123,
      longitude=123.123,
      date=Some(new Date()),
      user=Some( BasicResourceUser(userId=userIdExample.value,
      provider="string",
      username=usernameExample.value))),
      meta=Meta( License(id="string",
      name="string")),
      OpeningTimeOnMonday=Some("string"),
      ClosingTimeOnMonday=Some("string"),
      OpeningTimeOnTuesday=Some("string"),
      ClosingTimeOnTuesday=Some("string"),
      OpeningTimeOnWednesday=Some("string"),
      ClosingTimeOnWednesday=Some("string"),
      OpeningTimeOnThursday=Some("string"),
      ClosingTimeOnThursday=Some("string"),
      OpeningTimeOnFriday=Some("string"),
      ClosingTimeOnFriday=Some("string"),
      OpeningTimeOnSaturday=Some("string"),
      ClosingTimeOnSaturday=Some("string"),
      OpeningTimeOnSunday=Some("string"),
      ClosingTimeOnSunday=Some("string"),
      isAccessible=Some(true),
      locatedAt=Some("string"),
      moreInfo=Some("string"),
      hasDepositCapability=Some(true)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_atm
  override def getAtm(bankId: BankId, atmId: AtmId, callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAtm => OutBound, InBoundGetAtm => InBound}
        val procedureName = "get_atm"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, atmId)
        val result: OBPReturnType[Box[AtmTCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getAtmsDoc80
  private def getAtmsDoc80 = MessageDoc(
    process = "obp.getAtms",
    messageFormat = messageFormat,
    description = """
               |Get Atms
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_atms
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetAtms(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate="string",
      toDate="string")
    ),
    exampleInboundMessage = (
     InBoundGetAtms(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( AtmTCommons(atmId=AtmId("string"),
      bankId=BankId(bankIdExample.value),
      name="string",
      address= Address(line1="string",
      line2="string",
      line3="string",
      city="string",
      county=Some("string"),
      state="string",
      postCode="string",
      countryCode="string"),
      location= Location(latitude=123.123,
      longitude=123.123,
      date=Some(new Date()),
      user=Some( BasicResourceUser(userId=userIdExample.value,
      provider="string",
      username=usernameExample.value))),
      meta=Meta( License(id="string",
      name="string")),
      OpeningTimeOnMonday=Some("string"),
      ClosingTimeOnMonday=Some("string"),
      OpeningTimeOnTuesday=Some("string"),
      ClosingTimeOnTuesday=Some("string"),
      OpeningTimeOnWednesday=Some("string"),
      ClosingTimeOnWednesday=Some("string"),
      OpeningTimeOnThursday=Some("string"),
      ClosingTimeOnThursday=Some("string"),
      OpeningTimeOnFriday=Some("string"),
      ClosingTimeOnFriday=Some("string"),
      OpeningTimeOnSaturday=Some("string"),
      ClosingTimeOnSaturday=Some("string"),
      OpeningTimeOnSunday=Some("string"),
      ClosingTimeOnSunday=Some("string"),
      isAccessible=Some(true),
      locatedAt=Some("string"),
      moreInfo=Some("string"),
      hasDepositCapability=Some(true))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_atms
  override def getAtms(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[(List[AtmT], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAtms => OutBound, InBoundGetAtms => InBound}
        val procedureName = "get_atms"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[AtmTCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createTransactionAfterChallengev300Doc57
  private def createTransactionAfterChallengev300Doc57 = MessageDoc(
    process = "obp.createTransactionAfterChallengev300",
    messageFormat = messageFormat,
    description = """
               |Create Transaction After Challengev300
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_transaction_after_challengev300
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionAfterChallengev300(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      transReqId=TransactionRequestId("string"),
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value))
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionAfterChallengev300(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_transaction_after_challengev300
  override def createTransactionAfterChallengev300(initiator: User, fromAccount: BankAccount, transReqId: TransactionRequestId, transactionRequestType: TransactionRequestType, callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionAfterChallengev300 => OutBound, InBoundCreateTransactionAfterChallengev300 => InBound}
        val procedureName = "create_transaction_after_challengev300"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , initiator, fromAccount, transReqId, transactionRequestType)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += makePaymentv300Doc10
  private def makePaymentv300Doc10 = MessageDoc(
    process = "obp.makePaymentv300",
    messageFormat = messageFormat,
    description = """
               |Make Paymentv300
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: make_paymentv300
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundMakePaymentv300(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toCounterparty= CounterpartyTraitCommons(createdByUserId="string",
      name="string",
      description="string",
      thisBankId="string",
      thisAccountId="string",
      thisViewId="string",
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=accountRoutingSchemeExample.value,
      otherAccountRoutingAddress=accountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme="string",
      otherAccountSecondaryRoutingAddress="string",
      otherBankRoutingScheme=bankRoutingSchemeExample.value,
      otherBankRoutingAddress=bankRoutingAddressExample.value,
      otherBranchRoutingScheme=branchRoutingSchemeExample.value,
      otherBranchRoutingAddress=branchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))),
      transactionRequestCommonBody= TransactionRequestCommonBodyJSONCommons(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      chargePolicy="string")
    ),
    exampleInboundMessage = (
     InBoundMakePaymentv300(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=TransactionId(transactionIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: make_paymentv300
  override def makePaymentv300(initiator: User, fromAccount: BankAccount, toAccount: BankAccount, toCounterparty: CounterpartyTrait, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, transactionRequestType: TransactionRequestType, chargePolicy: String, callContext: Option[CallContext]): Future[Box[(TransactionId, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundMakePaymentv300 => OutBound, InBoundMakePaymentv300 => InBound}
        val procedureName = "make_paymentv300"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , initiator, fromAccount, toAccount, toCounterparty, transactionRequestCommonBody, transactionRequestType, chargePolicy)
        val result: OBPReturnType[Box[TransactionId]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createTransactionRequestv300Doc57
  private def createTransactionRequestv300Doc57 = MessageDoc(
    process = "obp.createTransactionRequestv300",
    messageFormat = messageFormat,
    description = """
               |Create Transaction Requestv300
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_transaction_requestv300
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionRequestv300(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      viewId=ViewId(viewIdExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toCounterparty= CounterpartyTraitCommons(createdByUserId="string",
      name="string",
      description="string",
      thisBankId="string",
      thisAccountId="string",
      thisViewId="string",
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=accountRoutingSchemeExample.value,
      otherAccountRoutingAddress=accountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme="string",
      otherAccountSecondaryRoutingAddress="string",
      otherBankRoutingScheme=bankRoutingSchemeExample.value,
      otherBankRoutingAddress=bankRoutingAddressExample.value,
      otherBranchRoutingScheme=branchRoutingSchemeExample.value,
      otherBranchRoutingAddress=branchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))),
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      transactionRequestCommonBody= TransactionRequestCommonBodyJSONCommons(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string"),
      detailsPlain="string",
      chargePolicy="string")
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionRequestv300(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TransactionRequest(id=TransactionRequestId("string"),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id="string",
      account_id="string"),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id="string",
      account_id="string")),
      to_sepa=Some(TransactionRequestIban("string")),
      to_counterparty=Some(TransactionRequestCounterpartyId("string")),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to=ToAccountTransferToPhone("string"))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      message="string",
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname="string"),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`="string",
      number="string")))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      description="string",
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name="string",
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount="string"),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string"),
      description="string"),
      transaction_ids="string",
      status="string",
      start_date=new Date(),
      end_date=new Date(),
      challenge= TransactionRequestChallenge(id="string",
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary="string",
      value= AmountOfMoney(currency=currencyExample.value,
      amount="string")),
      charge_policy="string",
      counterparty_id=CounterpartyId(counterpartyIdExample.value),
      name="string",
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_transaction_requestv300
  override def createTransactionRequestv300(initiator: User, viewId: ViewId, fromAccount: BankAccount, toAccount: BankAccount, toCounterparty: CounterpartyTrait, transactionRequestType: TransactionRequestType, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, detailsPlain: String, chargePolicy: String, callContext: Option[CallContext]): Future[Box[(TransactionRequest, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionRequestv300 => OutBound, InBoundCreateTransactionRequestv300 => InBound}
        val procedureName = "create_transaction_requestv300"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , initiator, viewId, fromAccount, toAccount, toCounterparty, transactionRequestType, transactionRequestCommonBody, detailsPlain, chargePolicy)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createCounterpartyDoc23
  private def createCounterpartyDoc23 = MessageDoc(
    process = "obp.createCounterparty",
    messageFormat = messageFormat,
    description = """
               |Create Counterparty
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_counterparty
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateCounterparty(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      name="string",
      description="string",
      createdByUserId="string",
      thisBankId="string",
      thisAccountId="string",
      thisViewId="string",
      otherAccountRoutingScheme=accountRoutingSchemeExample.value,
      otherAccountRoutingAddress=accountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme="string",
      otherAccountSecondaryRoutingAddress="string",
      otherBankRoutingScheme=bankRoutingSchemeExample.value,
      otherBankRoutingAddress=bankRoutingAddressExample.value,
      otherBranchRoutingScheme=branchRoutingSchemeExample.value,
      otherBranchRoutingAddress=branchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value)))
    ),
    exampleInboundMessage = (
     InBoundCreateCounterparty(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CounterpartyTraitCommons(createdByUserId="string",
      name="string",
      description="string",
      thisBankId="string",
      thisAccountId="string",
      thisViewId="string",
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=accountRoutingSchemeExample.value,
      otherAccountRoutingAddress=accountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme="string",
      otherAccountSecondaryRoutingAddress="string",
      otherBankRoutingScheme=bankRoutingSchemeExample.value,
      otherBankRoutingAddress=bankRoutingAddressExample.value,
      otherBranchRoutingScheme=branchRoutingSchemeExample.value,
      otherBranchRoutingAddress=branchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_counterparty
  override def createCounterparty(name: String, description: String, createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String, otherAccountRoutingScheme: String, otherAccountRoutingAddress: String, otherAccountSecondaryRoutingScheme: String, otherAccountSecondaryRoutingAddress: String, otherBankRoutingScheme: String, otherBankRoutingAddress: String, otherBranchRoutingScheme: String, otherBranchRoutingAddress: String, isBeneficiary: Boolean, bespoke: List[CounterpartyBespoke], callContext: Option[CallContext]): Box[(CounterpartyTrait, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundCreateCounterparty => OutBound, InBoundCreateCounterparty => InBound}
        val procedureName = "create_counterparty"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , name, description, createdByUserId, thisBankId, thisAccountId, thisViewId, otherAccountRoutingScheme, otherAccountRoutingAddress, otherAccountSecondaryRoutingScheme, otherAccountSecondaryRoutingAddress, otherBankRoutingScheme, otherBankRoutingAddress, otherBranchRoutingScheme, otherBranchRoutingAddress, isBeneficiary, bespoke)
        val result: OBPReturnType[Box[CounterpartyTraitCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += checkCustomerNumberAvailableDoc91
  private def checkCustomerNumberAvailableDoc91 = MessageDoc(
    process = "obp.checkCustomerNumberAvailable",
    messageFormat = messageFormat,
    description = """
               |Check Customer Number Available
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: check_customer_number_available
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCheckCustomerNumberAvailable(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      customerNumber=customerNumberExample.value)
    ),
    exampleInboundMessage = (
     InBoundCheckCustomerNumberAvailable(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: check_customer_number_available
  override def checkCustomerNumberAvailable(bankId: BankId, customerNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundCheckCustomerNumberAvailable => OutBound, InBoundCheckCustomerNumberAvailable => InBound}
        val procedureName = "check_customer_number_available"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, customerNumber)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createCustomerDoc18
  private def createCustomerDoc18 = MessageDoc(
    process = "obp.createCustomer",
    messageFormat = messageFormat,
    description = """
               |Create Customer
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_customer
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateCustomer(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=parseDate(customerFaceImageDateExample.value).getOrElse(sys.error("customerFaceImageDateExample.value is not validate date format.")),
      url=urlExample.value),
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=parseDate(outBoundCreateCustomerLastOkDateExample.value).getOrElse(sys.error("outBoundCreateCustomerLastOkDateExample.value is not validate date format.")),
      creditRating=Some( CreditRating(rating=ratingExample.value,
      source=sourceExample.value)),
      creditLimit=Some( AmountOfMoney(currency=currencyExample.value,
      amount=creditLimitAmountExample.value)),
      title=titleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateCustomer(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=parseDate(customerFaceImageDateExample.value).getOrElse(sys.error("customerFaceImageDateExample.value is not validate date format.")),
      url=urlExample.value),
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=parseDate(customerLastOkDateExample.value).getOrElse(sys.error("customerLastOkDateExample.value is not validate date format.")),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_customer
  override def createCustomer(bankId: BankId, legalName: String, mobileNumber: String, email: String, faceImage: CustomerFaceImageTrait, dateOfBirth: Date, relationshipStatus: String, dependents: Int, dobOfDependents: List[Date], highestEducationAttained: String, employmentStatus: String, kycStatus: Boolean, lastOkDate: Date, creditRating: Option[CreditRatingTrait], creditLimit: Option[AmountOfMoneyTrait], title: String, branchId: String, nameSuffix: String, callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateCustomer => OutBound, InBoundCreateCustomer => InBound}
        val procedureName = "create_customer"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, legalName, mobileNumber, email, faceImage, dateOfBirth, relationshipStatus, dependents, dobOfDependents, highestEducationAttained, employmentStatus, kycStatus, lastOkDate, creditRating, creditLimit, title, branchId, nameSuffix)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += updateCustomerScaDataDoc17
  private def updateCustomerScaDataDoc17 = MessageDoc(
    process = "obp.updateCustomerScaData",
    messageFormat = messageFormat,
    description = """
               |Update Customer Sca Data
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: update_customer_sca_data
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdateCustomerScaData(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerId=customerIdExample.value,
      mobileNumber=Some(mobileNumberExample.value),
      email=Some(emailExample.value),
      customerNumber=Some(customerNumberExample.value))
    ),
    exampleInboundMessage = (
     InBoundUpdateCustomerScaData(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=parseDate(customerFaceImageDateExample.value).getOrElse(sys.error("customerFaceImageDateExample.value is not validate date format.")),
      url=urlExample.value),
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=parseDate(customerLastOkDateExample.value).getOrElse(sys.error("customerLastOkDateExample.value is not validate date format.")),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: update_customer_sca_data
  override def updateCustomerScaData(customerId: String, mobileNumber: Option[String], email: Option[String], customerNumber: Option[String], callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdateCustomerScaData => OutBound, InBoundUpdateCustomerScaData => InBound}
        val procedureName = "update_customer_sca_data"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId, mobileNumber, email, customerNumber)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += updateCustomerCreditDataDoc22
  private def updateCustomerCreditDataDoc22 = MessageDoc(
    process = "obp.updateCustomerCreditData",
    messageFormat = messageFormat,
    description = """
               |Update Customer Credit Data
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: update_customer_credit_data
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdateCustomerCreditData(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerId=customerIdExample.value,
      creditRating=Some("string"),
      creditSource=Some("string"),
      creditLimit=Some( AmountOfMoney(currency=currencyExample.value,
      amount=creditLimitAmountExample.value)))
    ),
    exampleInboundMessage = (
     InBoundUpdateCustomerCreditData(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=parseDate(customerFaceImageDateExample.value).getOrElse(sys.error("customerFaceImageDateExample.value is not validate date format.")),
      url=urlExample.value),
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=parseDate(customerLastOkDateExample.value).getOrElse(sys.error("customerLastOkDateExample.value is not validate date format.")),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: update_customer_credit_data
  override def updateCustomerCreditData(customerId: String, creditRating: Option[String], creditSource: Option[String], creditLimit: Option[AmountOfMoney], callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdateCustomerCreditData => OutBound, InBoundUpdateCustomerCreditData => InBound}
        val procedureName = "update_customer_credit_data"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId, creditRating, creditSource, creditLimit)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += updateCustomerGeneralDataDoc50
  private def updateCustomerGeneralDataDoc50 = MessageDoc(
    process = "obp.updateCustomerGeneralData",
    messageFormat = messageFormat,
    description = """
               |Update Customer General Data
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: update_customer_general_data
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdateCustomerGeneralData(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerId=customerIdExample.value,
      legalName=Some(legalNameExample.value),
      faceImage=Some( CustomerFaceImage(date=new Date(),
      url=urlExample.value)),
      dateOfBirth=Some(parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format."))),
      relationshipStatus=Some(relationshipStatusExample.value),
      dependents=Some(dependentsExample.value.toInt),
      highestEducationAttained=Some(highestEducationAttainedExample.value),
      employmentStatus=Some(employmentStatusExample.value),
      title=Some(titleExample.value),
      branchId=Some(branchIdExample.value),
      nameSuffix=Some(nameSuffixExample.value))
    ),
    exampleInboundMessage = (
     InBoundUpdateCustomerGeneralData(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=parseDate(customerFaceImageDateExample.value).getOrElse(sys.error("customerFaceImageDateExample.value is not validate date format.")),
      url=urlExample.value),
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=parseDate(customerLastOkDateExample.value).getOrElse(sys.error("customerLastOkDateExample.value is not validate date format.")),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: update_customer_general_data
  override def updateCustomerGeneralData(customerId: String, legalName: Option[String], faceImage: Option[CustomerFaceImageTrait], dateOfBirth: Option[Date], relationshipStatus: Option[String], dependents: Option[Int], highestEducationAttained: Option[String], employmentStatus: Option[String], title: Option[String], branchId: Option[String], nameSuffix: Option[String], callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdateCustomerGeneralData => OutBound, InBoundUpdateCustomerGeneralData => InBound}
        val procedureName = "update_customer_general_data"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId, legalName, faceImage, dateOfBirth, relationshipStatus, dependents, highestEducationAttained, employmentStatus, title, branchId, nameSuffix)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCustomersByUserIdDoc25
  private def getCustomersByUserIdDoc25 = MessageDoc(
    process = "obp.getCustomersByUserId",
    messageFormat = messageFormat,
    description = """
               |Get Customers By User Id
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_customers_by_user_id
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomersByUserId(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      userId=userIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCustomersByUserId(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=parseDate(customerFaceImageDateExample.value).getOrElse(sys.error("customerFaceImageDateExample.value is not validate date format.")),
      url=urlExample.value),
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=parseDate(customerLastOkDateExample.value).getOrElse(sys.error("customerLastOkDateExample.value is not validate date format.")),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_customers_by_user_id
  override def getCustomersByUserId(userId: String, callContext: Option[CallContext]): Future[Box[(List[Customer], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCustomersByUserId => OutBound, InBoundGetCustomersByUserId => InBound}
        val procedureName = "get_customers_by_user_id"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , userId)
        val result: OBPReturnType[Box[List[CustomerCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCustomerByCustomerIdLegacyDoc14
  private def getCustomerByCustomerIdLegacyDoc14 = MessageDoc(
    process = "obp.getCustomerByCustomerIdLegacy",
    messageFormat = messageFormat,
    description = """
               |Get Customer By Customer Id Legacy
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_customer_by_customer_id_legacy
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomerByCustomerIdLegacy(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCustomerByCustomerIdLegacy(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=parseDate(customerFaceImageDateExample.value).getOrElse(sys.error("customerFaceImageDateExample.value is not validate date format.")),
      url=urlExample.value),
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=parseDate(customerLastOkDateExample.value).getOrElse(sys.error("customerLastOkDateExample.value is not validate date format.")),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_customer_by_customer_id_legacy
  override def getCustomerByCustomerIdLegacy(customerId: String, callContext: Option[CallContext]): Box[(Customer, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetCustomerByCustomerIdLegacy => OutBound, InBoundGetCustomerByCustomerIdLegacy => InBound}
        val procedureName = "get_customer_by_customer_id_legacy"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCustomerByCustomerIdDoc84
  private def getCustomerByCustomerIdDoc84 = MessageDoc(
    process = "obp.getCustomerByCustomerId",
    messageFormat = messageFormat,
    description = """
               |Get Customer By Customer Id
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_customer_by_customer_id
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomerByCustomerId(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCustomerByCustomerId(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=parseDate(customerFaceImageDateExample.value).getOrElse(sys.error("customerFaceImageDateExample.value is not validate date format.")),
      url=urlExample.value),
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=parseDate(customerLastOkDateExample.value).getOrElse(sys.error("customerLastOkDateExample.value is not validate date format.")),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_customer_by_customer_id
  override def getCustomerByCustomerId(customerId: String, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCustomerByCustomerId => OutBound, InBoundGetCustomerByCustomerId => InBound}
        val procedureName = "get_customer_by_customer_id"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCustomerByCustomerNumberDoc20
  private def getCustomerByCustomerNumberDoc20 = MessageDoc(
    process = "obp.getCustomerByCustomerNumber",
    messageFormat = messageFormat,
    description = """
               |Get Customer By Customer Number
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_customer_by_customer_number
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomerByCustomerNumber(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerNumber=customerNumberExample.value,
      bankId=BankId(bankIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetCustomerByCustomerNumber(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=parseDate(customerFaceImageDateExample.value).getOrElse(sys.error("customerFaceImageDateExample.value is not validate date format.")),
      url=urlExample.value),
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=parseDate(customerLastOkDateExample.value).getOrElse(sys.error("customerLastOkDateExample.value is not validate date format.")),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_customer_by_customer_number
  override def getCustomerByCustomerNumber(customerNumber: String, bankId: BankId, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCustomerByCustomerNumber => OutBound, InBoundGetCustomerByCustomerNumber => InBound}
        val procedureName = "get_customer_by_customer_number"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerNumber, bankId)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCustomerAddressDoc80
  private def getCustomerAddressDoc80 = MessageDoc(
    process = "obp.getCustomerAddress",
    messageFormat = messageFormat,
    description = """
               |Get Customer Address
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_customer_address
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomerAddress(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCustomerAddress(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( CustomerAddressCommons(customerId=customerIdExample.value,
      customerAddressId="string",
      line1="string",
      line2="string",
      line3="string",
      city="string",
      county="string",
      state="string",
      postcode="string",
      countryCode="string",
      status="string",
      tags="string",
      insertDate=new Date())))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_customer_address
  override def getCustomerAddress(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAddress]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCustomerAddress => OutBound, InBoundGetCustomerAddress => InBound}
        val procedureName = "get_customer_address"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[List[CustomerAddressCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createCustomerAddressDoc66
  private def createCustomerAddressDoc66 = MessageDoc(
    process = "obp.createCustomerAddress",
    messageFormat = messageFormat,
    description = """
               |Create Customer Address
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_customer_address
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateCustomerAddress(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerId=customerIdExample.value,
      line1="string",
      line2="string",
      line3="string",
      city="string",
      county="string",
      state="string",
      postcode="string",
      countryCode="string",
      tags="string",
      status="string")
    ),
    exampleInboundMessage = (
     InBoundCreateCustomerAddress(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CustomerAddressCommons(customerId=customerIdExample.value,
      customerAddressId="string",
      line1="string",
      line2="string",
      line3="string",
      city="string",
      county="string",
      state="string",
      postcode="string",
      countryCode="string",
      status="string",
      tags="string",
      insertDate=new Date()))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_customer_address
  override def createCustomerAddress(customerId: String, line1: String, line2: String, line3: String, city: String, county: String, state: String, postcode: String, countryCode: String, tags: String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateCustomerAddress => OutBound, InBoundCreateCustomerAddress => InBound}
        val procedureName = "create_customer_address"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId, line1, line2, line3, city, county, state, postcode, countryCode, tags, status)
        val result: OBPReturnType[Box[CustomerAddressCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += updateCustomerAddressDoc97
  private def updateCustomerAddressDoc97 = MessageDoc(
    process = "obp.updateCustomerAddress",
    messageFormat = messageFormat,
    description = """
               |Update Customer Address
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: update_customer_address
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdateCustomerAddress(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerAddressId="string",
      line1="string",
      line2="string",
      line3="string",
      city="string",
      county="string",
      state="string",
      postcode="string",
      countryCode="string",
      tags="string",
      status="string")
    ),
    exampleInboundMessage = (
     InBoundUpdateCustomerAddress(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CustomerAddressCommons(customerId=customerIdExample.value,
      customerAddressId="string",
      line1="string",
      line2="string",
      line3="string",
      city="string",
      county="string",
      state="string",
      postcode="string",
      countryCode="string",
      status="string",
      tags="string",
      insertDate=new Date()))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: update_customer_address
  override def updateCustomerAddress(customerAddressId: String, line1: String, line2: String, line3: String, city: String, county: String, state: String, postcode: String, countryCode: String, tags: String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdateCustomerAddress => OutBound, InBoundUpdateCustomerAddress => InBound}
        val procedureName = "update_customer_address"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerAddressId, line1, line2, line3, city, county, state, postcode, countryCode, tags, status)
        val result: OBPReturnType[Box[CustomerAddressCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += deleteCustomerAddressDoc84
  private def deleteCustomerAddressDoc84 = MessageDoc(
    process = "obp.deleteCustomerAddress",
    messageFormat = messageFormat,
    description = """
               |Delete Customer Address
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: delete_customer_address
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundDeleteCustomerAddress(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerAddressId="string")
    ),
    exampleInboundMessage = (
     InBoundDeleteCustomerAddress(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: delete_customer_address
  override def deleteCustomerAddress(customerAddressId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundDeleteCustomerAddress => OutBound, InBoundDeleteCustomerAddress => InBound}
        val procedureName = "delete_customer_address"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerAddressId)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createTaxResidenceDoc58
  private def createTaxResidenceDoc58 = MessageDoc(
    process = "obp.createTaxResidence",
    messageFormat = messageFormat,
    description = """
               |Create Tax Residence
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_tax_residence
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTaxResidence(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerId=customerIdExample.value,
      domain="string",
      taxNumber="string")
    ),
    exampleInboundMessage = (
     InBoundCreateTaxResidence(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= TaxResidenceCommons(customerId=customerIdExample.value,
      taxResidenceId="string",
      domain="string",
      taxNumber="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_tax_residence
  override def createTaxResidence(customerId: String, domain: String, taxNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[TaxResidence]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTaxResidence => OutBound, InBoundCreateTaxResidence => InBound}
        val procedureName = "create_tax_residence"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId, domain, taxNumber)
        val result: OBPReturnType[Box[TaxResidenceCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getTaxResidenceDoc28
  private def getTaxResidenceDoc28 = MessageDoc(
    process = "obp.getTaxResidence",
    messageFormat = messageFormat,
    description = """
               |Get Tax Residence
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_tax_residence
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTaxResidence(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetTaxResidence(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( TaxResidenceCommons(customerId=customerIdExample.value,
      taxResidenceId="string",
      domain="string",
      taxNumber="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_tax_residence
  override def getTaxResidence(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[TaxResidence]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetTaxResidence => OutBound, InBoundGetTaxResidence => InBound}
        val procedureName = "get_tax_residence"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[List[TaxResidenceCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += deleteTaxResidenceDoc54
  private def deleteTaxResidenceDoc54 = MessageDoc(
    process = "obp.deleteTaxResidence",
    messageFormat = messageFormat,
    description = """
               |Delete Tax Residence
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: delete_tax_residence
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundDeleteTaxResidence(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      taxResourceId="string")
    ),
    exampleInboundMessage = (
     InBoundDeleteTaxResidence(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: delete_tax_residence
  override def deleteTaxResidence(taxResourceId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundDeleteTaxResidence => OutBound, InBoundDeleteTaxResidence => InBound}
        val procedureName = "delete_tax_residence"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , taxResourceId)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCustomersDoc12
  private def getCustomersDoc12 = MessageDoc(
    process = "obp.getCustomers",
    messageFormat = messageFormat,
    description = """
               |Get Customers
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_customers
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomers(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate="string",
      toDate="string")
    ),
    exampleInboundMessage = (
     InBoundGetCustomers(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=parseDate(customerFaceImageDateExample.value).getOrElse(sys.error("customerFaceImageDateExample.value is not validate date format.")),
      url=urlExample.value),
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=parseDate(customerLastOkDateExample.value).getOrElse(sys.error("customerLastOkDateExample.value is not validate date format.")),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_customers
  override def getCustomers(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCustomers => OutBound, InBoundGetCustomers => InBound}
        val procedureName = "get_customers"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[CustomerCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCheckbookOrdersDoc40
  private def getCheckbookOrdersDoc40 = MessageDoc(
    process = "obp.getCheckbookOrders",
    messageFormat = messageFormat,
    description = """
               |Get Checkbook Orders
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_checkbook_orders
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCheckbookOrders(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=bankIdExample.value,
      accountId=accountIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCheckbookOrders(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CheckbookOrdersJson(account= AccountV310Json(bank_id="string",
      account_id="string",
      account_type="string",
      account_routings=List( AccountRoutingJsonV121(scheme="string",
      address="string")),
      branch_routings=List( BranchRoutingJsonV141(scheme="string",
      address="string"))),
      orders=List(OrderJson( OrderObjectJson(order_id="string",
      order_date="string",
      number_of_checkbooks="string",
      distribution_channel="string",
      status="string",
      first_check_number="string",
      shipping_code="string")))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_checkbook_orders
  override def getCheckbookOrders(bankId: String, accountId: String, callContext: Option[CallContext]): Future[Box[(CheckbookOrdersJson, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCheckbookOrders => OutBound, InBoundGetCheckbookOrders => InBound}
        val procedureName = "get_checkbook_orders"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId)
        val result: OBPReturnType[Box[CheckbookOrdersJson]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getStatusOfCreditCardOrderDoc96
  private def getStatusOfCreditCardOrderDoc96 = MessageDoc(
    process = "obp.getStatusOfCreditCardOrder",
    messageFormat = messageFormat,
    description = """
               |Get Status Of Credit Card Order
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_status_of_credit_card_order
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetStatusOfCreditCardOrder(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=bankIdExample.value,
      accountId=accountIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetStatusOfCreditCardOrder(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( CardObjectJson(card_type="string",
      card_description="string",
      use_type="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_status_of_credit_card_order
  override def getStatusOfCreditCardOrder(bankId: String, accountId: String, callContext: Option[CallContext]): Future[Box[(List[CardObjectJson], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetStatusOfCreditCardOrder => OutBound, InBoundGetStatusOfCreditCardOrder => InBound}
        val procedureName = "get_status_of_credit_card_order"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId)
        val result: OBPReturnType[Box[List[CardObjectJson]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createUserAuthContextDoc5
  private def createUserAuthContextDoc5 = MessageDoc(
    process = "obp.createUserAuthContext",
    messageFormat = messageFormat,
    description = """
               |Create User Auth Context
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_user_auth_context
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateUserAuthContext(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      userId=userIdExample.value,
      key=keyExample.value,
      value=valueExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateUserAuthContext(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= UserAuthContextCommons(userAuthContextId="string",
      userId=userIdExample.value,
      key=keyExample.value,
      value=valueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_user_auth_context
  override def createUserAuthContext(userId: String, key: String, value: String, callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContext]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateUserAuthContext => OutBound, InBoundCreateUserAuthContext => InBound}
        val procedureName = "create_user_auth_context"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , userId, key, value)
        val result: OBPReturnType[Box[UserAuthContextCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createUserAuthContextUpdateDoc97
  private def createUserAuthContextUpdateDoc97 = MessageDoc(
    process = "obp.createUserAuthContextUpdate",
    messageFormat = messageFormat,
    description = """
               |Create User Auth Context Update
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_user_auth_context_update
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateUserAuthContextUpdate(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      userId=userIdExample.value,
      key=keyExample.value,
      value=valueExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateUserAuthContextUpdate(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= UserAuthContextUpdateCommons(userAuthContextUpdateId="string",
      userId=userIdExample.value,
      key=keyExample.value,
      value=valueExample.value,
      challenge="string",
      status="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_user_auth_context_update
  override def createUserAuthContextUpdate(userId: String, key: String, value: String, callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContextUpdate]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateUserAuthContextUpdate => OutBound, InBoundCreateUserAuthContextUpdate => InBound}
        val procedureName = "create_user_auth_context_update"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , userId, key, value)
        val result: OBPReturnType[Box[UserAuthContextUpdateCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += deleteUserAuthContextsDoc21
  private def deleteUserAuthContextsDoc21 = MessageDoc(
    process = "obp.deleteUserAuthContexts",
    messageFormat = messageFormat,
    description = """
               |Delete User Auth Contexts
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: delete_user_auth_contexts
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundDeleteUserAuthContexts(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      userId=userIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundDeleteUserAuthContexts(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: delete_user_auth_contexts
  override def deleteUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundDeleteUserAuthContexts => OutBound, InBoundDeleteUserAuthContexts => InBound}
        val procedureName = "delete_user_auth_contexts"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , userId)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += deleteUserAuthContextByIdDoc77
  private def deleteUserAuthContextByIdDoc77 = MessageDoc(
    process = "obp.deleteUserAuthContextById",
    messageFormat = messageFormat,
    description = """
               |Delete User Auth Context By Id
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: delete_user_auth_context_by_id
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundDeleteUserAuthContextById(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      userAuthContextId="string")
    ),
    exampleInboundMessage = (
     InBoundDeleteUserAuthContextById(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: delete_user_auth_context_by_id
  override def deleteUserAuthContextById(userAuthContextId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundDeleteUserAuthContextById => OutBound, InBoundDeleteUserAuthContextById => InBound}
        val procedureName = "delete_user_auth_context_by_id"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , userAuthContextId)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getUserAuthContextsDoc51
  private def getUserAuthContextsDoc51 = MessageDoc(
    process = "obp.getUserAuthContexts",
    messageFormat = messageFormat,
    description = """
               |Get User Auth Contexts
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_user_auth_contexts
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetUserAuthContexts(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      userId=userIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetUserAuthContexts(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( UserAuthContextCommons(userAuthContextId="string",
      userId=userIdExample.value,
      key=keyExample.value,
      value=valueExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_user_auth_contexts
  override def getUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[UserAuthContext]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetUserAuthContexts => OutBound, InBoundGetUserAuthContexts => InBound}
        val procedureName = "get_user_auth_contexts"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , userId)
        val result: OBPReturnType[Box[List[UserAuthContextCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createOrUpdateProductAttributeDoc69
  private def createOrUpdateProductAttributeDoc69 = MessageDoc(
    process = "obp.createOrUpdateProductAttribute",
    messageFormat = messageFormat,
    description = """
               |Create Or Update Product Attribute
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_or_update_product_attribute
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateProductAttribute(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      productCode=ProductCode("string"),
      productAttributeId=Some("string"),
      name="string",
      productAttributeType=com.openbankproject.commons.model.enums.ProductAttributeType.example,
      value=valueExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateProductAttribute(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= ProductAttributeCommons(bankId=BankId(bankIdExample.value),
      productCode=ProductCode("string"),
      productAttributeId="string",
      name="string",
      attributeType=com.openbankproject.commons.model.enums.ProductAttributeType.example,
      value=valueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_or_update_product_attribute
  override def createOrUpdateProductAttribute(bankId: BankId, productCode: ProductCode, productAttributeId: Option[String], name: String, productAttributeType: ProductAttributeType.Value, value: String, callContext: Option[CallContext]): OBPReturnType[Box[ProductAttribute]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateProductAttribute => OutBound, InBoundCreateOrUpdateProductAttribute => InBound}
        val procedureName = "create_or_update_product_attribute"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, productCode, productAttributeId, name, productAttributeType, value)
        val result: OBPReturnType[Box[ProductAttributeCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getProductAttributeByIdDoc70
  private def getProductAttributeByIdDoc70 = MessageDoc(
    process = "obp.getProductAttributeById",
    messageFormat = messageFormat,
    description = """
               |Get Product Attribute By Id
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_product_attribute_by_id
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProductAttributeById(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      productAttributeId="string")
    ),
    exampleInboundMessage = (
     InBoundGetProductAttributeById(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= ProductAttributeCommons(bankId=BankId(bankIdExample.value),
      productCode=ProductCode("string"),
      productAttributeId="string",
      name="string",
      attributeType=com.openbankproject.commons.model.enums.ProductAttributeType.example,
      value=valueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_product_attribute_by_id
  override def getProductAttributeById(productAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[ProductAttribute]] = {
        import com.openbankproject.commons.dto.{OutBoundGetProductAttributeById => OutBound, InBoundGetProductAttributeById => InBound}
        val procedureName = "get_product_attribute_by_id"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , productAttributeId)
        val result: OBPReturnType[Box[ProductAttributeCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getProductAttributesByBankAndCodeDoc99
  private def getProductAttributesByBankAndCodeDoc99 = MessageDoc(
    process = "obp.getProductAttributesByBankAndCode",
    messageFormat = messageFormat,
    description = """
               |Get Product Attributes By Bank And Code
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_product_attributes_by_bank_and_code
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProductAttributesByBankAndCode(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bank=BankId(bankIdExample.value),
      productCode=ProductCode("string"))
    ),
    exampleInboundMessage = (
     InBoundGetProductAttributesByBankAndCode(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( ProductAttributeCommons(bankId=BankId(bankIdExample.value),
      productCode=ProductCode("string"),
      productAttributeId="string",
      name="string",
      attributeType=com.openbankproject.commons.model.enums.ProductAttributeType.example,
      value=valueExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_product_attributes_by_bank_and_code
  override def getProductAttributesByBankAndCode(bank: BankId, productCode: ProductCode, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductAttribute]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetProductAttributesByBankAndCode => OutBound, InBoundGetProductAttributesByBankAndCode => InBound}
        val procedureName = "get_product_attributes_by_bank_and_code"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bank, productCode)
        val result: OBPReturnType[Box[List[ProductAttributeCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += deleteProductAttributeDoc43
  private def deleteProductAttributeDoc43 = MessageDoc(
    process = "obp.deleteProductAttribute",
    messageFormat = messageFormat,
    description = """
               |Delete Product Attribute
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: delete_product_attribute
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundDeleteProductAttribute(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      productAttributeId="string")
    ),
    exampleInboundMessage = (
     InBoundDeleteProductAttribute(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: delete_product_attribute
  override def deleteProductAttribute(productAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundDeleteProductAttribute => OutBound, InBoundDeleteProductAttribute => InBound}
        val procedureName = "delete_product_attribute"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , productAttributeId)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getAccountAttributeByIdDoc35
  private def getAccountAttributeByIdDoc35 = MessageDoc(
    process = "obp.getAccountAttributeById",
    messageFormat = messageFormat,
    description = """
               |Get Account Attribute By Id
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_account_attribute_by_id
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetAccountAttributeById(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      accountAttributeId="string")
    ),
    exampleInboundMessage = (
     InBoundGetAccountAttributeById(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= AccountAttributeCommons(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      productCode=ProductCode("string"),
      accountAttributeId="string",
      name="string",
      attributeType=com.openbankproject.commons.model.enums.AccountAttributeType.example,
      value=valueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_account_attribute_by_id
  override def getAccountAttributeById(accountAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountAttribute]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAccountAttributeById => OutBound, InBoundGetAccountAttributeById => InBound}
        val procedureName = "get_account_attribute_by_id"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , accountAttributeId)
        val result: OBPReturnType[Box[AccountAttributeCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createOrUpdateAccountAttributeDoc97
  private def createOrUpdateAccountAttributeDoc97 = MessageDoc(
    process = "obp.createOrUpdateAccountAttribute",
    messageFormat = messageFormat,
    description = """
               |Create Or Update Account Attribute
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_or_update_account_attribute
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateAccountAttribute(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      productCode=ProductCode("string"),
      productAttributeId=Some("string"),
      name="string",
      accountAttributeType=com.openbankproject.commons.model.enums.AccountAttributeType.example,
      value=valueExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateAccountAttribute(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= AccountAttributeCommons(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      productCode=ProductCode("string"),
      accountAttributeId="string",
      name="string",
      attributeType=com.openbankproject.commons.model.enums.AccountAttributeType.example,
      value=valueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_or_update_account_attribute
  override def createOrUpdateAccountAttribute(bankId: BankId, accountId: AccountId, productCode: ProductCode, productAttributeId: Option[String], name: String, accountAttributeType: AccountAttributeType.Value, value: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountAttribute]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateAccountAttribute => OutBound, InBoundCreateOrUpdateAccountAttribute => InBound}
        val procedureName = "create_or_update_account_attribute"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, productCode, productAttributeId, name, accountAttributeType, value)
        val result: OBPReturnType[Box[AccountAttributeCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createAccountAttributesDoc39
  private def createAccountAttributesDoc39 = MessageDoc(
    process = "obp.createAccountAttributes",
    messageFormat = messageFormat,
    description = """
               |Create Account Attributes
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_account_attributes
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateAccountAttributes(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      productCode=ProductCode("string"),
      accountAttributes=List( ProductAttributeCommons(bankId=BankId(bankIdExample.value),
      productCode=ProductCode("string"),
      productAttributeId="string",
      name="string",
      attributeType=com.openbankproject.commons.model.enums.ProductAttributeType.example,
      value=valueExample.value)))
    ),
    exampleInboundMessage = (
     InBoundCreateAccountAttributes(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( AccountAttributeCommons(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      productCode=ProductCode("string"),
      accountAttributeId="string",
      name="string",
      attributeType=com.openbankproject.commons.model.enums.AccountAttributeType.example,
      value=valueExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_account_attributes
  override def createAccountAttributes(bankId: BankId, accountId: AccountId, productCode: ProductCode, accountAttributes: List[ProductAttribute], callContext: Option[CallContext]): OBPReturnType[Box[List[AccountAttribute]]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateAccountAttributes => OutBound, InBoundCreateAccountAttributes => InBound}
        val procedureName = "create_account_attributes"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, productCode, accountAttributes)
        val result: OBPReturnType[Box[List[AccountAttributeCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getAccountAttributesByAccountDoc69
  private def getAccountAttributesByAccountDoc69 = MessageDoc(
    process = "obp.getAccountAttributesByAccount",
    messageFormat = messageFormat,
    description = """
               |Get Account Attributes By Account
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_account_attributes_by_account
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetAccountAttributesByAccount(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetAccountAttributesByAccount(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( AccountAttributeCommons(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      productCode=ProductCode("string"),
      accountAttributeId="string",
      name="string",
      attributeType=com.openbankproject.commons.model.enums.AccountAttributeType.example,
      value=valueExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_account_attributes_by_account
  override def getAccountAttributesByAccount(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[List[AccountAttribute]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAccountAttributesByAccount => OutBound, InBoundGetAccountAttributesByAccount => InBound}
        val procedureName = "get_account_attributes_by_account"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId)
        val result: OBPReturnType[Box[List[AccountAttributeCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createOrUpdateCardAttributeDoc89
  private def createOrUpdateCardAttributeDoc89 = MessageDoc(
    process = "obp.createOrUpdateCardAttribute",
    messageFormat = messageFormat,
    description = """
               |Create Or Update Card Attribute
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_or_update_card_attribute
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateCardAttribute(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=Some(BankId(bankIdExample.value)),
      cardId=Some(cardIdExample.value),
      cardAttributeId=Some(cardAttributeIdExample.value),
      name="string",
      cardAttributeType=com.openbankproject.commons.model.enums.CardAttributeType.example,
      value=valueExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateCardAttribute(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CardAttributeCommons(bankId=Some(BankId(bankIdExample.value)),
      cardId=Some(cardIdExample.value),
      cardAttributeId=Some(cardAttributeIdExample.value),
      name=cardAttributeNameExample.value,
      attributeType=com.openbankproject.commons.model.enums.CardAttributeType.example,
      value=cardAttributeValueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_or_update_card_attribute
  override def createOrUpdateCardAttribute(bankId: Option[BankId], cardId: Option[String], cardAttributeId: Option[String], name: String, cardAttributeType: CardAttributeType.Value, value: String, callContext: Option[CallContext]): OBPReturnType[Box[CardAttribute]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateCardAttribute => OutBound, InBoundCreateOrUpdateCardAttribute => InBound}
        val procedureName = "create_or_update_card_attribute"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, cardId, cardAttributeId, name, cardAttributeType, value)
        val result: OBPReturnType[Box[CardAttributeCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCardAttributeByIdDoc11
  private def getCardAttributeByIdDoc11 = MessageDoc(
    process = "obp.getCardAttributeById",
    messageFormat = messageFormat,
    description = """
               |Get Card Attribute By Id
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_card_attribute_by_id
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCardAttributeById(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      cardAttributeId=cardAttributeIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCardAttributeById(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CardAttributeCommons(bankId=Some(BankId(bankIdExample.value)),
      cardId=Some(cardIdExample.value),
      cardAttributeId=Some(cardAttributeIdExample.value),
      name=cardAttributeNameExample.value,
      attributeType=com.openbankproject.commons.model.enums.CardAttributeType.example,
      value=cardAttributeValueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_card_attribute_by_id
  override def getCardAttributeById(cardAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[CardAttribute]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCardAttributeById => OutBound, InBoundGetCardAttributeById => InBound}
        val procedureName = "get_card_attribute_by_id"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , cardAttributeId)
        val result: OBPReturnType[Box[CardAttributeCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getCardAttributesFromProviderDoc16
  private def getCardAttributesFromProviderDoc16 = MessageDoc(
    process = "obp.getCardAttributesFromProvider",
    messageFormat = messageFormat,
    description = """
               |Get Card Attributes From Provider
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_card_attributes_from_provider
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCardAttributesFromProvider(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      cardId=cardIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCardAttributesFromProvider(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( CardAttributeCommons(bankId=Some(BankId(bankIdExample.value)),
      cardId=Some(cardIdExample.value),
      cardAttributeId=Some(cardAttributeIdExample.value),
      name=cardAttributeNameExample.value,
      attributeType=com.openbankproject.commons.model.enums.CardAttributeType.example,
      value=cardAttributeValueExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_card_attributes_from_provider
  override def getCardAttributesFromProvider(cardId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[CardAttribute]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCardAttributesFromProvider => OutBound, InBoundGetCardAttributesFromProvider => InBound}
        val procedureName = "get_card_attributes_from_provider"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , cardId)
        val result: OBPReturnType[Box[List[CardAttributeCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createAccountApplicationDoc73
  private def createAccountApplicationDoc73 = MessageDoc(
    process = "obp.createAccountApplication",
    messageFormat = messageFormat,
    description = """
               |Create Account Application
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_account_application
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateAccountApplication(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      productCode=ProductCode("string"),
      userId=Some(userIdExample.value),
      customerId=Some(customerIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundCreateAccountApplication(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= AccountApplicationCommons(accountApplicationId="string",
      productCode=ProductCode("string"),
      userId=userIdExample.value,
      customerId=customerIdExample.value,
      dateOfApplication=new Date(),
      status="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_account_application
  override def createAccountApplication(productCode: ProductCode, userId: Option[String], customerId: Option[String], callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateAccountApplication => OutBound, InBoundCreateAccountApplication => InBound}
        val procedureName = "create_account_application"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , productCode, userId, customerId)
        val result: OBPReturnType[Box[AccountApplicationCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getAllAccountApplicationDoc33
  private def getAllAccountApplicationDoc33 = MessageDoc(
    process = "obp.getAllAccountApplication",
    messageFormat = messageFormat,
    description = """
               |Get All Account Application
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_all_account_application
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
          OutBoundGetAllAccountApplication( OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))))
    ),
    exampleInboundMessage = (
     InBoundGetAllAccountApplication(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( AccountApplicationCommons(accountApplicationId="string",
      productCode=ProductCode("string"),
      userId=userIdExample.value,
      customerId=customerIdExample.value,
      dateOfApplication=new Date(),
      status="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_all_account_application
  override def getAllAccountApplication(callContext: Option[CallContext]): OBPReturnType[Box[List[AccountApplication]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAllAccountApplication => OutBound, InBoundGetAllAccountApplication => InBound}
        val procedureName = "get_all_account_application"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull )
        val result: OBPReturnType[Box[List[AccountApplicationCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getAccountApplicationByIdDoc83
  private def getAccountApplicationByIdDoc83 = MessageDoc(
    process = "obp.getAccountApplicationById",
    messageFormat = messageFormat,
    description = """
               |Get Account Application By Id
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_account_application_by_id
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetAccountApplicationById(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      accountApplicationId="string")
    ),
    exampleInboundMessage = (
     InBoundGetAccountApplicationById(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= AccountApplicationCommons(accountApplicationId="string",
      productCode=ProductCode("string"),
      userId=userIdExample.value,
      customerId=customerIdExample.value,
      dateOfApplication=new Date(),
      status="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_account_application_by_id
  override def getAccountApplicationById(accountApplicationId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAccountApplicationById => OutBound, InBoundGetAccountApplicationById => InBound}
        val procedureName = "get_account_application_by_id"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , accountApplicationId)
        val result: OBPReturnType[Box[AccountApplicationCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += updateAccountApplicationStatusDoc26
  private def updateAccountApplicationStatusDoc26 = MessageDoc(
    process = "obp.updateAccountApplicationStatus",
    messageFormat = messageFormat,
    description = """
               |Update Account Application Status
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: update_account_application_status
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdateAccountApplicationStatus(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      accountApplicationId="string",
      status="string")
    ),
    exampleInboundMessage = (
     InBoundUpdateAccountApplicationStatus(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= AccountApplicationCommons(accountApplicationId="string",
      productCode=ProductCode("string"),
      userId=userIdExample.value,
      customerId=customerIdExample.value,
      dateOfApplication=new Date(),
      status="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: update_account_application_status
  override def updateAccountApplicationStatus(accountApplicationId: String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdateAccountApplicationStatus => OutBound, InBoundUpdateAccountApplicationStatus => InBound}
        val procedureName = "update_account_application_status"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , accountApplicationId, status)
        val result: OBPReturnType[Box[AccountApplicationCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getOrCreateProductCollectionDoc67
  private def getOrCreateProductCollectionDoc67 = MessageDoc(
    process = "obp.getOrCreateProductCollection",
    messageFormat = messageFormat,
    description = """
               |Get Or Create Product Collection
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_or_create_product_collection
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetOrCreateProductCollection(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      collectionCode="string",
      productCodes=List("string"))
    ),
    exampleInboundMessage = (
     InBoundGetOrCreateProductCollection(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( ProductCollectionCommons(collectionCode="string",
      productCode="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_or_create_product_collection
  override def getOrCreateProductCollection(collectionCode: String, productCodes: List[String], callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetOrCreateProductCollection => OutBound, InBoundGetOrCreateProductCollection => InBound}
        val procedureName = "get_or_create_product_collection"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , collectionCode, productCodes)
        val result: OBPReturnType[Box[List[ProductCollectionCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getProductCollectionDoc57
  private def getProductCollectionDoc57 = MessageDoc(
    process = "obp.getProductCollection",
    messageFormat = messageFormat,
    description = """
               |Get Product Collection
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_product_collection
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProductCollection(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      collectionCode="string")
    ),
    exampleInboundMessage = (
     InBoundGetProductCollection(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( ProductCollectionCommons(collectionCode="string",
      productCode="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_product_collection
  override def getProductCollection(collectionCode: String, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetProductCollection => OutBound, InBoundGetProductCollection => InBound}
        val procedureName = "get_product_collection"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , collectionCode)
        val result: OBPReturnType[Box[List[ProductCollectionCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getOrCreateProductCollectionItemDoc68
  private def getOrCreateProductCollectionItemDoc68 = MessageDoc(
    process = "obp.getOrCreateProductCollectionItem",
    messageFormat = messageFormat,
    description = """
               |Get Or Create Product Collection Item
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_or_create_product_collection_item
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetOrCreateProductCollectionItem(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      collectionCode="string",
      memberProductCodes=List("string"))
    ),
    exampleInboundMessage = (
     InBoundGetOrCreateProductCollectionItem(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( ProductCollectionItemCommons(collectionCode="string",
      memberProductCode="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_or_create_product_collection_item
  override def getOrCreateProductCollectionItem(collectionCode: String, memberProductCodes: List[String], callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetOrCreateProductCollectionItem => OutBound, InBoundGetOrCreateProductCollectionItem => InBound}
        val procedureName = "get_or_create_product_collection_item"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , collectionCode, memberProductCodes)
        val result: OBPReturnType[Box[List[ProductCollectionItemCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getProductCollectionItemDoc49
  private def getProductCollectionItemDoc49 = MessageDoc(
    process = "obp.getProductCollectionItem",
    messageFormat = messageFormat,
    description = """
               |Get Product Collection Item
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_product_collection_item
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProductCollectionItem(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      collectionCode="string")
    ),
    exampleInboundMessage = (
     InBoundGetProductCollectionItem(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( ProductCollectionItemCommons(collectionCode="string",
      memberProductCode="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_product_collection_item
  override def getProductCollectionItem(collectionCode: String, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetProductCollectionItem => OutBound, InBoundGetProductCollectionItem => InBound}
        val procedureName = "get_product_collection_item"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , collectionCode)
        val result: OBPReturnType[Box[List[ProductCollectionItemCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getProductCollectionItemsTreeDoc57
  private def getProductCollectionItemsTreeDoc57 = MessageDoc(
    process = "obp.getProductCollectionItemsTree",
    messageFormat = messageFormat,
    description = """
               |Get Product Collection Items Tree
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_product_collection_items_tree
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProductCollectionItemsTree(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      collectionCode="string",
      bankId=bankIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetProductCollectionItemsTree(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List(( ProductCollectionItemCommons(collectionCode="string",
      memberProductCode="string"),  ProductCommons(bankId=BankId(bankIdExample.value),
      code=ProductCode("string"),
      parentProductCode=ProductCode("string"),
      name="string",
      category="string",
      family="string",
      superFamily="string",
      moreInfoUrl="string",
      details="string",
      description="string",
      meta=Meta( License(id="string",
      name="string"))), List( ProductAttributeCommons(bankId=BankId(bankIdExample.value),
      productCode=ProductCode("string"),
      productAttributeId="string",
      name="string",
      attributeType=com.openbankproject.commons.model.enums.ProductAttributeType.example,
      value=valueExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_product_collection_items_tree
  override def getProductCollectionItemsTree(collectionCode: String, bankId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[(ProductCollectionItem, Product, List[ProductAttribute])]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetProductCollectionItemsTree => OutBound, InBoundGetProductCollectionItemsTree => InBound}
        val procedureName = "get_product_collection_items_tree"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , collectionCode, bankId)
        val result: OBPReturnType[Box[List[(ProductCollectionItemCommons, ProductCommons, List[ProductAttributeCommons])]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createMeetingDoc10
  private def createMeetingDoc10 = MessageDoc(
    process = "obp.createMeeting",
    messageFormat = messageFormat,
    description = """
               |Create Meeting
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_meeting
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateMeeting(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      staffUser= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      customerUser= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      providerId="string",
      purposeId="string",
      when=new Date(),
      sessionId=sessionIdExample.value,
      customerToken="string",
      staffToken="string",
      creator= ContactDetails(name="string",
      phone="string",
      email=emailExample.value),
      invitees=List( Invitee(contactDetails= ContactDetails(name="string",
      phone="string",
      email=emailExample.value),
      status="string")))
    ),
    exampleInboundMessage = (
     InBoundCreateMeeting(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= MeetingCommons(meetingId="string",
      providerId="string",
      purposeId="string",
      bankId=bankIdExample.value,
      present= MeetingPresent(staffUserId="string",
      customerUserId="string"),
      keys= MeetingKeys(sessionId=sessionIdExample.value,
      customerToken="string",
      staffToken="string"),
      when=new Date(),
      creator= ContactDetails(name="string",
      phone="string",
      email=emailExample.value),
      invitees=List( Invitee(contactDetails= ContactDetails(name="string",
      phone="string",
      email=emailExample.value),
      status="string"))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_meeting
  override def createMeeting(bankId: BankId, staffUser: User, customerUser: User, providerId: String, purposeId: String, when: Date, sessionId: String, customerToken: String, staffToken: String, creator: ContactDetails, invitees: List[Invitee], callContext: Option[CallContext]): OBPReturnType[Box[Meeting]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateMeeting => OutBound, InBoundCreateMeeting => InBound}
        val procedureName = "create_meeting"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, staffUser, customerUser, providerId, purposeId, when, sessionId, customerToken, staffToken, creator, invitees)
        val result: OBPReturnType[Box[MeetingCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getMeetingsDoc82
  private def getMeetingsDoc82 = MessageDoc(
    process = "obp.getMeetings",
    messageFormat = messageFormat,
    description = """
               |Get Meetings
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_meetings
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetMeetings(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      user= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetMeetings(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( MeetingCommons(meetingId="string",
      providerId="string",
      purposeId="string",
      bankId=bankIdExample.value,
      present= MeetingPresent(staffUserId="string",
      customerUserId="string"),
      keys= MeetingKeys(sessionId=sessionIdExample.value,
      customerToken="string",
      staffToken="string"),
      when=new Date(),
      creator= ContactDetails(name="string",
      phone="string",
      email=emailExample.value),
      invitees=List( Invitee(contactDetails= ContactDetails(name="string",
      phone="string",
      email=emailExample.value),
      status="string")))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_meetings
  override def getMeetings(bankId: BankId, user: User, callContext: Option[CallContext]): OBPReturnType[Box[List[Meeting]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetMeetings => OutBound, InBoundGetMeetings => InBound}
        val procedureName = "get_meetings"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, user)
        val result: OBPReturnType[Box[List[MeetingCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getMeetingDoc69
  private def getMeetingDoc69 = MessageDoc(
    process = "obp.getMeeting",
    messageFormat = messageFormat,
    description = """
               |Get Meeting
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_meeting
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetMeeting(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=BankId(bankIdExample.value),
      user= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      meetingId="string")
    ),
    exampleInboundMessage = (
     InBoundGetMeeting(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= MeetingCommons(meetingId="string",
      providerId="string",
      purposeId="string",
      bankId=bankIdExample.value,
      present= MeetingPresent(staffUserId="string",
      customerUserId="string"),
      keys= MeetingKeys(sessionId=sessionIdExample.value,
      customerToken="string",
      staffToken="string"),
      when=new Date(),
      creator= ContactDetails(name="string",
      phone="string",
      email=emailExample.value),
      invitees=List( Invitee(contactDetails= ContactDetails(name="string",
      phone="string",
      email=emailExample.value),
      status="string"))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_meeting
  override def getMeeting(bankId: BankId, user: User, meetingId: String, callContext: Option[CallContext]): OBPReturnType[Box[Meeting]] = {
        import com.openbankproject.commons.dto.{OutBoundGetMeeting => OutBound, InBoundGetMeeting => InBound}
        val procedureName = "get_meeting"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, user, meetingId)
        val result: OBPReturnType[Box[MeetingCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createOrUpdateKycCheckDoc28
  private def createOrUpdateKycCheckDoc28 = MessageDoc(
    process = "obp.createOrUpdateKycCheck",
    messageFormat = messageFormat,
    description = """
               |Create Or Update Kyc Check
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_or_update_kyc_check
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateKycCheck(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      id="string",
      customerNumber=customerNumberExample.value,
      date=new Date(),
      how="string",
      staffUserId="string",
      mStaffName="string",
      mSatisfied=true,
      comments="string")
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateKycCheck(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= KycCheckCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      idKycCheck="string",
      customerNumber=customerNumberExample.value,
      date=new Date(),
      how="string",
      staffUserId="string",
      staffName="string",
      satisfied=true,
      comments="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_or_update_kyc_check
  override def createOrUpdateKycCheck(bankId: String, customerId: String, id: String, customerNumber: String, date: Date, how: String, staffUserId: String, mStaffName: String, mSatisfied: Boolean, comments: String, callContext: Option[CallContext]): OBPReturnType[Box[KycCheck]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateKycCheck => OutBound, InBoundCreateOrUpdateKycCheck => InBound}
        val procedureName = "create_or_update_kyc_check"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, customerId, id, customerNumber, date, how, staffUserId, mStaffName, mSatisfied, comments)
        val result: OBPReturnType[Box[KycCheckCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createOrUpdateKycDocumentDoc9
  private def createOrUpdateKycDocumentDoc9 = MessageDoc(
    process = "obp.createOrUpdateKycDocument",
    messageFormat = messageFormat,
    description = """
               |Create Or Update Kyc Document
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_or_update_kyc_document
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateKycDocument(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      id="string",
      customerNumber=customerNumberExample.value,
      `type`="string",
      number="string",
      issueDate=new Date(),
      issuePlace="string",
      expiryDate=new Date())
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateKycDocument(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= KycDocumentCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      idKycDocument="string",
      customerNumber=customerNumberExample.value,
      `type`="string",
      number="string",
      issueDate=new Date(),
      issuePlace="string",
      expiryDate=new Date()))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_or_update_kyc_document
  override def createOrUpdateKycDocument(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, number: String, issueDate: Date, issuePlace: String, expiryDate: Date, callContext: Option[CallContext]): OBPReturnType[Box[KycDocument]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateKycDocument => OutBound, InBoundCreateOrUpdateKycDocument => InBound}
        val procedureName = "create_or_update_kyc_document"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, customerId, id, customerNumber, `type`, number, issueDate, issuePlace, expiryDate)
        val result: OBPReturnType[Box[KycDocument]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createOrUpdateKycMediaDoc17
  private def createOrUpdateKycMediaDoc17 = MessageDoc(
    process = "obp.createOrUpdateKycMedia",
    messageFormat = messageFormat,
    description = """
               |Create Or Update Kyc Media
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_or_update_kyc_media
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateKycMedia(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      id="string",
      customerNumber=customerNumberExample.value,
      `type`="string",
      url=urlExample.value,
      date=new Date(),
      relatesToKycDocumentId="string",
      relatesToKycCheckId="string")
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateKycMedia(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= KycMediaCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      idKycMedia="string",
      customerNumber=customerNumberExample.value,
      `type`="string",
      url=urlExample.value,
      date=new Date(),
      relatesToKycDocumentId="string",
      relatesToKycCheckId="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_or_update_kyc_media
  override def createOrUpdateKycMedia(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, url: String, date: Date, relatesToKycDocumentId: String, relatesToKycCheckId: String, callContext: Option[CallContext]): OBPReturnType[Box[KycMedia]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateKycMedia => OutBound, InBoundCreateOrUpdateKycMedia => InBound}
        val procedureName = "create_or_update_kyc_media"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, customerId, id, customerNumber, `type`, url, date, relatesToKycDocumentId, relatesToKycCheckId)
        val result: OBPReturnType[Box[KycMediaCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createOrUpdateKycStatusDoc23
  private def createOrUpdateKycStatusDoc23 = MessageDoc(
    process = "obp.createOrUpdateKycStatus",
    messageFormat = messageFormat,
    description = """
               |Create Or Update Kyc Status
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_or_update_kyc_status
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateKycStatus(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      ok=true,
      date=new Date())
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateKycStatus(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= KycStatusCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      ok=true,
      date=new Date()))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_or_update_kyc_status
  override def createOrUpdateKycStatus(bankId: String, customerId: String, customerNumber: String, ok: Boolean, date: Date, callContext: Option[CallContext]): OBPReturnType[Box[KycStatus]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateKycStatus => OutBound, InBoundCreateOrUpdateKycStatus => InBound}
        val procedureName = "create_or_update_kyc_status"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, customerId, customerNumber, ok, date)
        val result: OBPReturnType[Box[KycStatusCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getKycChecksDoc63
  private def getKycChecksDoc63 = MessageDoc(
    process = "obp.getKycChecks",
    messageFormat = messageFormat,
    description = """
               |Get Kyc Checks
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_kyc_checks
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetKycChecks(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetKycChecks(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( KycCheckCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      idKycCheck="string",
      customerNumber=customerNumberExample.value,
      date=new Date(),
      how="string",
      staffUserId="string",
      staffName="string",
      satisfied=true,
      comments="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_kyc_checks
  override def getKycChecks(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[KycCheck]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetKycChecks => OutBound, InBoundGetKycChecks => InBound}
        val procedureName = "get_kyc_checks"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[List[KycCheckCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getKycDocumentsDoc38
  private def getKycDocumentsDoc38 = MessageDoc(
    process = "obp.getKycDocuments",
    messageFormat = messageFormat,
    description = """
               |Get Kyc Documents
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_kyc_documents
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetKycDocuments(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetKycDocuments(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( KycDocumentCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      idKycDocument="string",
      customerNumber=customerNumberExample.value,
      `type`="string",
      number="string",
      issueDate=new Date(),
      issuePlace="string",
      expiryDate=new Date())))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_kyc_documents
  override def getKycDocuments(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[KycDocument]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetKycDocuments => OutBound, InBoundGetKycDocuments => InBound}
        val procedureName = "get_kyc_documents"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[List[KycDocumentCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getKycMediasDoc31
  private def getKycMediasDoc31 = MessageDoc(
    process = "obp.getKycMedias",
    messageFormat = messageFormat,
    description = """
               |Get Kyc Medias
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_kyc_medias
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetKycMedias(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetKycMedias(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( KycMediaCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      idKycMedia="string",
      customerNumber=customerNumberExample.value,
      `type`="string",
      url=urlExample.value,
      date=new Date(),
      relatesToKycDocumentId="string",
      relatesToKycCheckId="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_kyc_medias
  override def getKycMedias(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[KycMedia]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetKycMedias => OutBound, InBoundGetKycMedias => InBound}
        val procedureName = "get_kyc_medias"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[List[KycMediaCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getKycStatusesDoc96
  private def getKycStatusesDoc96 = MessageDoc(
    process = "obp.getKycStatuses",
    messageFormat = messageFormat,
    description = """
               |Get Kyc Statuses
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: get_kyc_statuses
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetKycStatuses(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetKycStatuses(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=List( KycStatusCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      ok=true,
      date=new Date())))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: get_kyc_statuses
  override def getKycStatuses(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[KycStatus]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetKycStatuses => OutBound, InBoundGetKycStatuses => InBound}
        val procedureName = "get_kyc_statuses"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[List[KycStatusCommons]]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += createMessageDoc72
  private def createMessageDoc72 = MessageDoc(
    process = "obp.createMessage",
    messageFormat = messageFormat,
    description = """
               |Create Message
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: create_message
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateMessage(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      user= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider="string",
      emailAddress=emailExample.value,
      name=usernameExample.value),
      bankId=BankId(bankIdExample.value),
      message="string",
      fromDepartment="string",
      fromPerson="string")
    ),
    exampleInboundMessage = (
     InBoundCreateMessage(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data= CustomerMessageCommons(messageId="string",
      date=new Date(),
      message="string",
      fromDepartment="string",
      fromPerson="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: create_message
  override def createMessage(user: User, bankId: BankId, message: String, fromDepartment: String, fromPerson: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerMessage]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateMessage => OutBound, InBoundCreateMessage => InBound}
        val procedureName = "create_message"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , user, bankId, message, fromDepartment, fromPerson)
        val result: OBPReturnType[Box[CustomerMessageCommons]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += makeHistoricalPaymentDoc41
  private def makeHistoricalPaymentDoc41 = MessageDoc(
    process = "obp.makeHistoricalPayment",
    messageFormat = messageFormat,
    description = """
               |Make Historical Payment
               |
               |The connector name is: stored_procedure_vDec2019
               |The MS SQL Server stored procedure name is: make_historical_payment
      """.stripMargin,
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundMakeHistoricalPayment(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      consumerId=Some(consumerIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value))),
      outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
      username=Some(usernameExample.value),
      linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value))),
      userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
      value=valueExample.value))),
      authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
      name=viewNameExample.value,
      description=viewDescriptionExample.value),
      account= AccountBasic(id=accountIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      legalName=legalNameExample.value,
      dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
      userOwners=List( InternalBasicUser(userId=userIdExample.value,
      emailAddress=emailExample.value,
      name=usernameExample.value))))))))),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      iban=Some(ibanExample.value),
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      posted=new Date(),
      completed=new Date(),
      amount=BigDecimal("123.321"),
      description="string",
      transactionRequestType=transactionRequestTypeExample.value,
      chargePolicy="string")
    ),
    exampleInboundMessage = (
     InBoundMakeHistoricalPayment(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
      sessionId=Some(sessionIdExample.value),
      generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
      value=valueExample.value)))),
      status= Status(errorCode=statusErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value))),
      data=TransactionId(transactionIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // stored procedure name: make_historical_payment
  override def makeHistoricalPayment(fromAccount: BankAccount, toAccount: BankAccount, posted: Date, completed: Date, amount: BigDecimal, description: String, transactionRequestType: String, chargePolicy: String, callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]] = {
        import com.openbankproject.commons.dto.{OutBoundMakeHistoricalPayment => OutBound, InBoundMakeHistoricalPayment => InBound}
        val procedureName = "make_historical_payment"

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , fromAccount, toAccount, posted, completed, amount, description, transactionRequestType, chargePolicy)
        val result: OBPReturnType[Box[TransactionId]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
        result
  }

//---------------- dynamic end ---------------------please don't modify this line

  private val availableOperation = DynamicEntityOperation.values.map(it => s""""$it"""").mkString("[", ", ", "]")

  messageDocs += dynamicEntityProcessDoc
  def dynamicEntityProcessDoc = MessageDoc(
    process = "obp.dynamicEntityProcess",
    messageFormat = messageFormat,
    description = s"operate committed dynamic entity data, the available value of 'operation' can be: ${availableOperation}",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundDynamicEntityProcessDoc(outboundAdapterCallContext = OutboundAdapterCallContext(correlationId=correlationIdExample.value,
        sessionId=Some(sessionIdExample.value),
        consumerId=Some(consumerIdExample.value),
        generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
          value=valueExample.value))),
        outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
          username=Some(usernameExample.value),
          linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
            customerNumber=customerNumberExample.value,
            legalName=legalNameExample.value))),
          userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
            value=valueExample.value))),
          authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
            name=viewNameExample.value,
            description=viewDescriptionExample.value),
            account= AccountBasic(id=accountIdExample.value,
              accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
                address=accountRoutingAddressExample.value)),
              customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
                customerId=customerIdExample.value,
                customerNumber=customerNumberExample.value,
                legalName=legalNameExample.value,
                dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
              userOwners=List( InternalBasicUser(userId=userIdExample.value,
                emailAddress=emailExample.value,
                name=usernameExample.value))))))))),
        operation = DynamicEntityOperation.UPDATE,
        entityName = "FooBar",
        requestBody = Some(FooBar(name = "James Brown", number = 1234567890)),
        entityId = Some("foobar-id-value"))
      ),
    exampleInboundMessage = (
      InBoundDynamicEntityProcessDoc(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
        sessionId=Some(sessionIdExample.value),
        generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
          value=valueExample.value)))),
        status= Status(errorCode=statusErrorCodeExample.value,
          backendMessages=List( InboundStatusMessage(source=sourceExample.value,
            status=inboundStatusMessageStatusExample.value,
            errorCode=inboundStatusMessageErrorCodeExample.value,
            text=inboundStatusMessageTextExample.value))),
        data=FooBar(name = "James Brown", number = 1234567890, fooBarId = Some("foobar-id-value")))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def dynamicEntityProcess(operation: DynamicEntityOperation,
                                    entityName: String,
                                    requestBody: Option[JObject],
                                    entityId: Option[String],
                                    callContext: Option[CallContext]): OBPReturnType[Box[JValue]] = {
    import com.openbankproject.commons.dto.{InBoundDynamicEntityProcess => InBound, OutBoundDynamicEntityProcess => OutBound}
    val procedureName = StringHelpers.snakify("dynamicEntityProcess")
    val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , operation, entityName, requestBody, entityId)
    val result: OBPReturnType[Box[JValue]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
    result
  }

  private[this] def buildAdapterCallContext(callContext: Option[CallContext]): OutboundAdapterCallContext = callContext.map(_.toOutboundAdapterCallContext).orNull

  /**
    * some methods return type is not future, this implicit method make these method have the same body, it facilitate to generate code.
    *
    * @param future
    * @tparam T
    * @return result of future
    */
  // This result is accessed synchronously (blocking)
  // TODO 1. Consider can be the result accessed asynchronously (non-blocking)
  // TODO 2. Consider making the duration tweakable via props file at least
  private[this] implicit def convertFuture[T](future: Future[T]): T = Await.result(future, 1.minute)

  /**
   * convert return value of OBPReturnType[Box[T]] to Box[(T, Option[CallContext])], this can let all method have the same body even though return type is not match
   * @param future
   * @tparam T
   * @return
   */
  private[this] implicit def convertFutureToBoxTuple[T](future: OBPReturnType[Box[T]]): Box[(T, Option[CallContext])] = {
    val (boxT, cc) = convertFuture(future)
    boxT.map((_, cc))
  }
  /**
   * convert return value of OBPReturnType[Box[T]] to Box[T], this can let all method have the same body even though return type is not match
   * @param future
   * @tparam T
   * @return
   */
  private[this] implicit def convertFutureToBox[T](future: OBPReturnType[Box[T]]): Box[T] = convertFuture(future)._1
  /**
   * convert return value of OBPReturnType[Box[T]] to Future[T], this can let all method have the same body even though return type is not match
   * @param future
   * @tparam T
   * @return
   */
  private[this] implicit def convertToIgnoreCC[T](future: OBPReturnType[T]): Future[T] = future.map(it => it._1)

  private[this] def sendRequest[T <: InBoundTrait[_]: TypeTag : Manifest](procedureName: String, outBound: TopicTrait, callContext: Option[CallContext]): Future[Box[T]] = {
    //transfer accountId to accountReference and customerId to customerReference in outBound
    this.convertToReference(outBound)
    Future{
      StoredProcedureUtils.callProcedure[T](procedureName, outBound)
    }.map(convertToId(_)) recoverWith {
      case e: Exception => Future.failed(new Exception(s"$AdapterUnknownError Please Check Adapter Side! Details: ${e.getMessage}", e))
    }
  }


  //-----helper methods

  //TODO hongwei confirm the third valu: OutboundAdapterCallContext#adapterAuthInfo
  private[this] def buildCallContext(inboundAdapterCallContext: InboundAdapterCallContext, callContext: Option[CallContext]): Option[CallContext] =
    for (cc <- callContext)
      yield cc.copy(correlationId = inboundAdapterCallContext.correlationId, sessionId = inboundAdapterCallContext.sessionId)

  private[this] def buildCallContext(boxedInboundAdapterCallContext: Box[InboundAdapterCallContext], callContext: Option[CallContext]): Option[CallContext] = boxedInboundAdapterCallContext match {
    case Full(inboundAdapterCallContext) => buildCallContext(inboundAdapterCallContext, callContext)
    case _ => callContext
  }

  /**
   * helper function to convert customerId and accountId in a given instance
   * @param obj
   * @param customerIdConverter customerId converter, to or from customerReference
   * @param accountIdConverter accountId converter, to or from accountReference
   * @tparam T type of instance
   * @return modified instance
   */
  private def convertId[T](obj: T, customerIdConverter: String=> String, accountIdConverter: String=> String): T = {
    //1st: We must not convert when connector == mapped. this will ignore the implicitly_convert_ids props.
    //2rd: if connector != mapped, we still need the `implicitly_convert_ids == true`

    def isCustomerId(fieldName: String, fieldType: Type, fieldValue: Any, ownerType: Type) = {
        ownerType =:= typeOf[CustomerId] ||
        (fieldName.equalsIgnoreCase("customerId") && fieldType =:= typeOf[String]) ||
        (ownerType <:< typeOf[Customer] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])
      }

    def isAccountId(fieldName: String, fieldType: Type, fieldValue: Any, ownerType: Type) = {
        ownerType <:< typeOf[AccountId] ||
        (fieldName.equalsIgnoreCase("accountId") && fieldType =:= typeOf[String])
        (ownerType <:< typeOf[CoreAccount] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[AccountBalance] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[AccountHeld] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])
      }

    if(APIUtil.getPropsValue("connector","mapped") != "mapped" && APIUtil.getPropsAsBoolValue("implicitly_convert_ids",false)){
      ReflectUtils.resetNestedFields(obj){
        case (fieldName, fieldType, fieldValue: String, ownerType) if isCustomerId(fieldName, fieldType, fieldValue, ownerType) => customerIdConverter(fieldValue)
        case (fieldName, fieldType, fieldValue: String, ownerType) if isAccountId(fieldName, fieldType, fieldValue, ownerType) => accountIdConverter(fieldValue)
      }
      obj
    } else
      obj
  }

  /**
   * convert given instance nested CustomerId to customerReference, AccountId to accountReference
   * @param obj
   * @tparam T type of instance
   * @return modified instance
   */
  def convertToReference[T](obj: T): T = {
    import code.api.util.ErrorMessages.{CustomerNotFoundByCustomerId, InvalidAccountIdFormat}
    def customerIdConverter(customerId: String): String = MappedCustomerIdMappingProvider
      .getCustomerPlainTextReference(CustomerId(customerId))
      .openOrThrowException(s"$CustomerNotFoundByCustomerId the invalid customerId is $customerId")
    def accountIdConverter(accountId: String): String = MappedAccountIdMappingProvider
      .getAccountPlainTextReference(AccountId(accountId))
      .openOrThrowException(s"$InvalidAccountIdFormat the invalid accountId is $accountId")
    convertId[T](obj, customerIdConverter, accountIdConverter)
  }

  /**
   * convert given instance nested customerReference to CustomerId, accountReference to AccountId
   * @param obj
   * @tparam T type of instance
   * @return modified instance
   */
  def convertToId[T](obj: T): T = {
    import code.api.util.ErrorMessages.{CustomerNotFoundByCustomerId, InvalidAccountIdFormat}
    def customerIdConverter(customerReference: String): String = MappedCustomerIdMappingProvider
      .getOrCreateCustomerId(customerReference)
      .map(_.value)
      .openOrThrowException(s"$CustomerNotFoundByCustomerId the invalid customerReference is $customerReference")
    def accountIdConverter(accountReference: String): String = MappedAccountIdMappingProvider
      .getOrCreateAccountId(accountReference)
      .map(_.value).openOrThrowException(s"$InvalidAccountIdFormat the invalid accountReference is $accountReference")
    if(obj.isInstanceOf[EmptyBox]) {
        obj
    } else {
      convertId[T](obj, customerIdConverter, accountIdConverter)
    }
  }
}
object StoredProcedureConnector_vDec2019 extends StoredProcedureConnector_vDec2019


