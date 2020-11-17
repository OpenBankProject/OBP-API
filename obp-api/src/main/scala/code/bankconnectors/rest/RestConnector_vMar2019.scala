package code.bankconnectors.rest

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

import java.net.{ConnectException, URLEncoder, UnknownHostException}
import java.util.Date
import java.util.UUID.randomUUID

import _root_.akka.stream.StreamTcpException
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpProtocol, _}
import akka.util.ByteString
import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions
import code.api.cache.Caching
import code.api.util.APIUtil.{AdapterImplementation, MessageDoc, OBPReturnType, saveConnectorMetric, _}
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue._
import code.api.util.{APIUtil, CallContext, OBPQueryParam}
import code.api.v4_0_0.MockResponseHolder
import code.bankconnectors._
import code.bankconnectors.vJune2017.AuthInfo
import code.customer.internalMapping.MappedCustomerIdMappingProvider
import code.kafka.KafkaHelper
import code.model.dataAccess.internalMapping.MappedAccountIdMappingProvider
import code.util.AkkaHttpClient._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.dto.{InBoundTrait, _}
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.{AccountAttributeType, CardAttributeType, DynamicEntityOperation, ProductAttributeType}
import com.openbankproject.commons.model.{ErrorMessage, TopicTrait, _}
import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
import net.liftweb.common.{Box, Empty, _}
import net.liftweb.json
import net.liftweb.json.Extraction.decompose
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser.ParseException
import net.liftweb.json.{JValue, _}
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.reflect.runtime.universe._


trait RestConnector_vMar2019 extends Connector with KafkaHelper with MdcLoggable {
  //this one import is for implicit convert, don't delete
  import com.openbankproject.commons.model.{AmountOfMoney, CreditLimit, CreditRating, CustomerFaceImage}

  implicit override val nameOfConnector = RestConnector_vMar2019.toString

  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "March2019"

  override val messageDocs = ArrayBuffer[MessageDoc]()

  val authInfoExample = AuthInfo(userId = "userId", username = "username", cbsToken = "cbsToken")
  val errorCodeExample = "INTERNAL-OBP-ADAPTER-6001: ..."

  val connectorName = "rest_vMar2019"


//---------------- dynamic start -------------------please don't modify this line
// ---------- created on Tue Sep 03 17:49:04 CEST 2019

  messageDocs += getAdapterInfoDoc
  def getAdapterInfoDoc = MessageDoc(
    process = "obp.getAdapterInfo",
    messageFormat = messageFormat,
    description = "Get Adapter Info",
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
  // url example: /getAdapterInfo
  override def getAdapterInfo(callContext: Option[CallContext]): Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAdapterInfo => OutBound, InBoundGetAdapterInfo => InBound}
        val url = getUrl(callContext, "getAdapterInfo")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull )
        val result: OBPReturnType[Box[InboundAdapterInfoInternal]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getChallengeThresholdDoc
  def getChallengeThresholdDoc = MessageDoc(
    process = "obp.getChallengeThreshold",
    messageFormat = messageFormat,
    description = "Get Challenge Threshold",
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
      username="string")
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
  // url example: /getChallengeThreshold
  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, username: String, callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = {
        import com.openbankproject.commons.dto.{OutBoundGetChallengeThreshold => OutBound, InBoundGetChallengeThreshold => InBound}
        val url = getUrl(callContext, "getChallengeThreshold")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, viewId, transactionRequestType, currency, userId, username)
        val result: OBPReturnType[Box[AmountOfMoney]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getChargeLevelDoc
  def getChargeLevelDoc = MessageDoc(
    process = "obp.getChargeLevel",
    messageFormat = messageFormat,
    description = "Get Charge Level",
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
      username="string",
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
  // url example: /getChargeLevel
  override def getChargeLevel(bankId: BankId, accountId: AccountId, viewId: ViewId, userId: String, username: String, transactionRequestType: String, currency: String, callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = {
        import com.openbankproject.commons.dto.{OutBoundGetChargeLevel => OutBound, InBoundGetChargeLevel => InBound}
        val url = getUrl(callContext, "getChargeLevel")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, viewId, userId, username, transactionRequestType, currency)
        val result: OBPReturnType[Box[AmountOfMoney]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createChallengeDoc
  def createChallengeDoc = MessageDoc(
    process = "obp.createChallenge",
    messageFormat = messageFormat,
    description = "Create Challenge",
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
  // url example: /createChallenge
  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String, scaMethod: Option[SCA], callContext: Option[CallContext]): OBPReturnType[Box[String]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateChallenge => OutBound, InBoundCreateChallenge => InBound}
        val url = getUrl(callContext, "createChallenge")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, userId, transactionRequestType, transactionRequestId, scaMethod)
        val result: OBPReturnType[Box[String]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += validateChallengeAnswerDoc
  def validateChallengeAnswerDoc = MessageDoc(
    process = "obp.validateChallengeAnswer",
    messageFormat = messageFormat,
    description = "Validate Challenge Answer",
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
  // url example: /validateChallengeAnswer
  override def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundValidateChallengeAnswer => OutBound, InBoundValidateChallengeAnswer => InBound}
        val url = getUrl(callContext, "validateChallengeAnswer")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , challengeId, hashOfSuppliedAnswer)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBankLegacyDoc
  def getBankLegacyDoc = MessageDoc(
    process = "obp.getBankLegacy",
    messageFormat = messageFormat,
    description = "Get Bank Legacy",
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
  // url example: /getBankLegacy
  override def getBankLegacy(bankId: BankId, callContext: Option[CallContext]): Box[(Bank, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankLegacy => OutBound, InBoundGetBankLegacy => InBound}
        val url = getUrl(callContext, "getBankLegacy")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId)
        val result: OBPReturnType[Box[BankCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBankDoc
  def getBankDoc = MessageDoc(
    process = "obp.getBank",
    messageFormat = messageFormat,
    description = "Get Bank",
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
  // url example: /getBank
  override def getBank(bankId: BankId, callContext: Option[CallContext]): Future[Box[(Bank, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBank => OutBound, InBoundGetBank => InBound}
        val url = getUrl(callContext, "getBank")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId)
        val result: OBPReturnType[Box[BankCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBanksLegacyDoc
  def getBanksLegacyDoc = MessageDoc(
    process = "obp.getBanksLegacy",
    messageFormat = messageFormat,
    description = "Get Banks Legacy",
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
  // url example: /getBanksLegacy
  override def getBanksLegacy(callContext: Option[CallContext]): Box[(List[Bank], Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetBanksLegacy => OutBound, InBoundGetBanksLegacy => InBound}
        val url = getUrl(callContext, "getBanksLegacy")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull )
        val result: OBPReturnType[Box[List[BankCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBanksDoc
  def getBanksDoc = MessageDoc(
    process = "obp.getBanks",
    messageFormat = messageFormat,
    description = "Get Banks",
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
  // url example: /getBanks
  override def getBanks(callContext: Option[CallContext]): Future[Box[(List[Bank], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBanks => OutBound, InBoundGetBanks => InBound}
        val url = getUrl(callContext, "getBanks")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull )
        val result: OBPReturnType[Box[List[BankCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBankAccountsForUserLegacyDoc
  def getBankAccountsForUserLegacyDoc = MessageDoc(
    process = "obp.getBankAccountsForUserLegacy",
    messageFormat = messageFormat,
    description = "Get Bank Accounts For User Legacy",
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
  // url example: /getBankAccountsForUserLegacy
  override def getBankAccountsForUserLegacy(username: String, callContext: Option[CallContext]): Box[(List[InboundAccount], Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountsForUserLegacy => OutBound, InBoundGetBankAccountsForUserLegacy => InBound}
        val url = getUrl(callContext, "getBankAccountsForUserLegacy")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , username)
        val result: OBPReturnType[Box[List[InboundAccountCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBankAccountsForUserDoc
  def getBankAccountsForUserDoc = MessageDoc(
    process = "obp.getBankAccountsForUser",
    messageFormat = messageFormat,
    description = "Get Bank Accounts For User",
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
  // url example: /getBankAccountsForUser
  override def getBankAccountsForUser(username: String, callContext: Option[CallContext]): Future[Box[(List[InboundAccount], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountsForUser => OutBound, InBoundGetBankAccountsForUser => InBound}
        val url = getUrl(callContext, "getBankAccountsForUser")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , username)
        val result: OBPReturnType[Box[List[InboundAccountCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBankAccountLegacyDoc
  def getBankAccountLegacyDoc = MessageDoc(
    process = "obp.getBankAccountLegacy",
    messageFormat = messageFormat,
    description = "Get Bank Account Legacy",
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /getBankAccountLegacy
  override def getBankAccountLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountLegacy => OutBound, InBoundGetBankAccountLegacy => InBound}
        val url = getUrl(callContext, "getBankAccountLegacy")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    

  messageDocs += getBankAccountByIbanDoc
  def getBankAccountByIbanDoc = MessageDoc(
    process = "obp.getBankAccountByIban",
    messageFormat = messageFormat,
    description = "Get Bank Account By Iban",
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /getBankAccountByIban
  override def getBankAccountByIban(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountByIban => OutBound, InBoundGetBankAccountByIban => InBound}
        val url = getUrl(callContext, "getBankAccountByIban")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , iban)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBankAccountByRoutingDoc
  def getBankAccountByRoutingDoc = MessageDoc(
    process = "obp.getBankAccountByRouting",
    messageFormat = messageFormat,
    description = "Get Bank Account By Routing",
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
      bankId=Some(BankId(bankIdExample.value)),
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /getBankAccountByRouting
  override def getBankAccountByRouting(bankId: Option[BankId], scheme: String, address: String, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountByRouting => OutBound, InBoundGetBankAccountByRouting => InBound}
        val url = getUrl(callContext, "getBankAccountByRouting")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, scheme, address)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBankAccountsDoc
  def getBankAccountsDoc = MessageDoc(
    process = "obp.getBankAccounts",
    messageFormat = messageFormat,
    description = "Get Bank Accounts",
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /getBankAccounts
  override def getBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[List[BankAccount]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccounts => OutBound, InBoundGetBankAccounts => InBound}
        val url = getUrl(callContext, "getBankAccounts")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankIdAccountIds)
        val result: OBPReturnType[Box[List[BankAccountCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBankAccountsBalancesDoc
  def getBankAccountsBalancesDoc = MessageDoc(
    process = "obp.getBankAccountsBalances",
    messageFormat = messageFormat,
    description = "Get Bank Accounts Balances",
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
  // url example: /getBankAccountsBalances
  override def getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], @CacheKeyOmit callContext: Option[CallContext]): OBPReturnType[Box[AccountsBalances]] =  saveConnectorMetric {
    /**
     * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
     * is just a temporary value field with UUID values in order to prevent any ambiguity.
     * The real value will be assigned by Macro during compile time at this line of a code:
     * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
     */
    //Note: here is a bit different, we get the headers from api level and also use them as the cache key.  
    val basicUserAuthContext = callContext.map(createBasicUserAuthContextJsonFromCallContext(_))
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString) 
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()+ basicUserAuthContext.toString()))(bankAccountsBalancesTTL second){{
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountsBalances => OutBound, InBoundGetBankAccountsBalances => InBound}
        val url = getUrl(callContext, "getBankAccountsBalances")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankIdAccountIds)
        val result: OBPReturnType[Box[AccountsBalances]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }}}}("getBankAccountsBalances")
    
  messageDocs += getCoreBankAccountsLegacyDoc
  def getCoreBankAccountsLegacyDoc = MessageDoc(
    process = "obp.getCoreBankAccountsLegacy",
    messageFormat = messageFormat,
    description = "Get Core Bank Accounts Legacy",
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
  // url example: /getCoreBankAccountsLegacy
  override def getCoreBankAccountsLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Box[(List[CoreAccount], Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetCoreBankAccountsLegacy => OutBound, InBoundGetCoreBankAccountsLegacy => InBound}
        val url = getUrl(callContext, "getCoreBankAccountsLegacy")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankIdAccountIds)
        val result: OBPReturnType[Box[List[CoreAccount]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCoreBankAccountsDoc
  def getCoreBankAccountsDoc = MessageDoc(
    process = "obp.getCoreBankAccounts",
    messageFormat = messageFormat,
    description = "Get Core Bank Accounts",
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
  // url example: /getCoreBankAccounts
  override def getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Future[Box[(List[CoreAccount], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCoreBankAccounts => OutBound, InBoundGetCoreBankAccounts => InBound}
        val url = getUrl(callContext, "getCoreBankAccounts")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankIdAccountIds)
        val result: OBPReturnType[Box[List[CoreAccount]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBankAccountsHeldLegacyDoc
  def getBankAccountsHeldLegacyDoc = MessageDoc(
    process = "obp.getBankAccountsHeldLegacy",
    messageFormat = messageFormat,
    description = "Get Bank Accounts Held Legacy",
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
      label = labelExample.value,
      bankId=bankIdExample.value,
      number="string",
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /getBankAccountsHeldLegacy
  override def getBankAccountsHeldLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Box[List[AccountHeld]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountsHeldLegacy => OutBound, InBoundGetBankAccountsHeldLegacy => InBound}
        val url = getUrl(callContext, "getBankAccountsHeldLegacy")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankIdAccountIds)
        val result: OBPReturnType[Box[List[AccountHeld]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBankAccountsHeldDoc
  def getBankAccountsHeldDoc = MessageDoc(
    process = "obp.getBankAccountsHeld",
    messageFormat = messageFormat,
    description = "Get Bank Accounts Held",
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
      label = labelExample.value,
      bankId=bankIdExample.value,
      number="string",
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /getBankAccountsHeld
  override def getBankAccountsHeld(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[List[AccountHeld]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountsHeld => OutBound, InBoundGetBankAccountsHeld => InBound}
        val url = getUrl(callContext, "getBankAccountsHeld")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankIdAccountIds)
        val result: OBPReturnType[Box[List[AccountHeld]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += checkBankAccountExistsLegacyDoc
  def checkBankAccountExistsLegacyDoc = MessageDoc(
    process = "obp.checkBankAccountExistsLegacy",
    messageFormat = messageFormat,
    description = "Check Bank Account Exists Legacy",
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /checkBankAccountExistsLegacy
  override def checkBankAccountExistsLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundCheckBankAccountExistsLegacy => OutBound, InBoundCheckBankAccountExistsLegacy => InBound}
        val url = getUrl(callContext, "checkBankAccountExistsLegacy")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += checkBankAccountExistsDoc
  def checkBankAccountExistsDoc = MessageDoc(
    process = "obp.checkBankAccountExists",
    messageFormat = messageFormat,
    description = "Check Bank Account Exists",
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /checkBankAccountExists
  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
        import com.openbankproject.commons.dto.{OutBoundCheckBankAccountExists => OutBound, InBoundCheckBankAccountExists => InBound}
        val url = getUrl(callContext, "checkBankAccountExists")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCounterpartyTraitDoc
  def getCounterpartyTraitDoc = MessageDoc(
    process = "obp.getCounterpartyTrait",
    messageFormat = messageFormat,
    description = "Get Counterparty Trait",
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
      currency=currencyExample.value,
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
  // url example: /getCounterpartyTrait
  override def getCounterpartyTrait(bankId: BankId, accountId: AccountId, couterpartyId: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCounterpartyTrait => OutBound, InBoundGetCounterpartyTrait => InBound}
        val url = getUrl(callContext, "getCounterpartyTrait")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, couterpartyId)
        val result: OBPReturnType[Box[CounterpartyTraitCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCounterpartyByCounterpartyIdLegacyDoc
  def getCounterpartyByCounterpartyIdLegacyDoc = MessageDoc(
    process = "obp.getCounterpartyByCounterpartyIdLegacy",
    messageFormat = messageFormat,
    description = "Get Counterparty By Counterparty Id Legacy",
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
      currency=currencyExample.value,
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
  // url example: /getCounterpartyByCounterpartyIdLegacy
  override def getCounterpartyByCounterpartyIdLegacy(counterpartyId: CounterpartyId, callContext: Option[CallContext]): Box[(CounterpartyTrait, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetCounterpartyByCounterpartyIdLegacy => OutBound, InBoundGetCounterpartyByCounterpartyIdLegacy => InBound}
        val url = getUrl(callContext, "getCounterpartyByCounterpartyIdLegacy")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , counterpartyId)
        val result: OBPReturnType[Box[CounterpartyTraitCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCounterpartyByCounterpartyIdDoc
  def getCounterpartyByCounterpartyIdDoc = MessageDoc(
    process = "obp.getCounterpartyByCounterpartyId",
    messageFormat = messageFormat,
    description = "Get Counterparty By Counterparty Id",
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
      currency=currencyExample.value,
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
  // url example: /getCounterpartyByCounterpartyId
  override def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCounterpartyByCounterpartyId => OutBound, InBoundGetCounterpartyByCounterpartyId => InBound}
        val url = getUrl(callContext, "getCounterpartyByCounterpartyId")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , counterpartyId)
        val result: OBPReturnType[Box[CounterpartyTraitCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCounterpartyByIbanDoc
  def getCounterpartyByIbanDoc = MessageDoc(
    process = "obp.getCounterpartyByIban",
    messageFormat = messageFormat,
    description = "Get Counterparty By Iban",
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
      currency=currencyExample.value,
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
  // url example: /getCounterpartyByIban
  override def getCounterpartyByIban(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCounterpartyByIban => OutBound, InBoundGetCounterpartyByIban => InBound}
        val url = getUrl(callContext, "getCounterpartyByIban")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , iban)
        val result: OBPReturnType[Box[CounterpartyTraitCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCounterpartiesLegacyDoc
  def getCounterpartiesLegacyDoc = MessageDoc(
    process = "obp.getCounterpartiesLegacy",
    messageFormat = messageFormat,
    description = "Get Counterparties Legacy",
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
      currency=currencyExample.value,
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
  // url example: /getCounterpartiesLegacy
  override def getCounterpartiesLegacy(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext]): Box[(List[CounterpartyTrait], Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetCounterpartiesLegacy => OutBound, InBoundGetCounterpartiesLegacy => InBound}
        val url = getUrl(callContext, "getCounterpartiesLegacy")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , thisBankId, thisAccountId, viewId)
        val result: OBPReturnType[Box[List[CounterpartyTraitCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCounterpartiesDoc
  def getCounterpartiesDoc = MessageDoc(
    process = "obp.getCounterparties",
    messageFormat = messageFormat,
    description = "Get Counterparties",
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
      currency=currencyExample.value,
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
  // url example: /getCounterparties
  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext]): OBPReturnType[Box[List[CounterpartyTrait]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCounterparties => OutBound, InBoundGetCounterparties => InBound}
        val url = getUrl(callContext, "getCounterparties")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , thisBankId, thisAccountId, viewId)
        val result: OBPReturnType[Box[List[CounterpartyTraitCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getTransactionsLegacyDoc
  def getTransactionsLegacyDoc = MessageDoc(
    process = "obp.getTransactionsLegacy",
    messageFormat = messageFormat,
    description = "Get Transactions Legacy",
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
      accountId=AccountId(accountIdExample.value),
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
      data=List( Transaction(uuid=transactionUuidExample.value,
      id=TransactionId(transactionIdExample.value),
      thisAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /getTransactionsLegacy
  override def getTransactionsLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Box[(List[Transaction], Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionsLegacy => OutBound, InBoundGetTransactionsLegacy => InBound}
        val url = getUrl(callContext, "getTransactionsLegacy")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[Transaction]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getTransactionsDoc
  def getTransactionsDoc = MessageDoc(
    process = "obp.getTransactions",
    messageFormat = messageFormat,
    description = "Get Transactions",
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
      data=List( Transaction(uuid=transactionUuidExample.value,
      id=TransactionId(transactionIdExample.value),
      thisAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /getTransactions
  override def getTransactions(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): OBPReturnType[Box[List[Transaction]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactions => OutBound, InBoundGetTransactions => InBound}
        val url = getUrl(callContext, "getTransactions")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[Transaction]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getTransactionsCoreDoc
  def getTransactionsCoreDoc = MessageDoc(
    process = "obp.getTransactionsCore",
    messageFormat = messageFormat,
    description = "Get Transactions Core",
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
      accountId=AccountId(accountIdExample.value),
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /getTransactionsCore
  override def getTransactionsCore(bankId: BankId, accountId: AccountId, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionCore]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionsCore => OutBound, InBoundGetTransactionsCore => InBound}
        val url = getUrl(callContext, "getTransactionsCore")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[TransactionCore]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getTransactionLegacyDoc
  def getTransactionLegacyDoc = MessageDoc(
    process = "obp.getTransactionLegacy",
    messageFormat = messageFormat,
    description = "Get Transaction Legacy",
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
      accountId=AccountId(accountIdExample.value),
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
      data= Transaction(uuid=transactionUuidExample.value,
      id=TransactionId(transactionIdExample.value),
      thisAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /getTransactionLegacy
  override def getTransactionLegacy(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]): Box[(Transaction, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionLegacy => OutBound, InBoundGetTransactionLegacy => InBound}
        val url = getUrl(callContext, "getTransactionLegacy")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, transactionId)
        val result: OBPReturnType[Box[Transaction]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getTransactionDoc
  def getTransactionDoc = MessageDoc(
    process = "obp.getTransaction",
    messageFormat = messageFormat,
    description = "Get Transaction",
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
      data= Transaction(uuid=transactionUuidExample.value,
      id=TransactionId(transactionIdExample.value),
      thisAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceAmountExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /getTransaction
  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[Box[Transaction]] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransaction => OutBound, InBoundGetTransaction => InBound}
        val url = getUrl(callContext, "getTransaction")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, transactionId)
        val result: OBPReturnType[Box[Transaction]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getPhysicalCardForBankDoc
  def getPhysicalCardForBankDoc = MessageDoc(
    process = "obp.getPhysicalCardForBank",
    messageFormat = messageFormat,
    description = "Get Physical Card For Bank",
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
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /getPhysicalCardForBank
  override def getPhysicalCardForBank(bankId: BankId, cardId: String, callContext: Option[CallContext]): OBPReturnType[Box[PhysicalCardTrait]] = {
        import com.openbankproject.commons.dto.{OutBoundGetPhysicalCardForBank => OutBound, InBoundGetPhysicalCardForBank => InBound}
        val url = getUrl(callContext, "getPhysicalCardForBank")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, cardId)
        val result: OBPReturnType[Box[PhysicalCard]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += deletePhysicalCardForBankDoc
  def deletePhysicalCardForBankDoc = MessageDoc(
    process = "obp.deletePhysicalCardForBank",
    messageFormat = messageFormat,
    description = "Delete Physical Card For Bank",
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
  // url example: /deletePhysicalCardForBank
  override def deletePhysicalCardForBank(bankId: BankId, cardId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundDeletePhysicalCardForBank => OutBound, InBoundDeletePhysicalCardForBank => InBound}
        val url = getUrl(callContext, "deletePhysicalCardForBank")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, cardId)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](url, HttpMethods.DELETE, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getPhysicalCardsForBankDoc
  def getPhysicalCardsForBankDoc = MessageDoc(
    process = "obp.getPhysicalCardsForBank",
    messageFormat = messageFormat,
    description = "Get Physical Cards For Bank",
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
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /getPhysicalCardsForBank
  override def getPhysicalCardsForBank(bank: Bank, user: User, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[PhysicalCard]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetPhysicalCardsForBank => OutBound, InBoundGetPhysicalCardsForBank => InBound}
        val url = getUrl(callContext, "getPhysicalCardsForBank")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bank, user, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[PhysicalCard]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createPhysicalCardLegacyDoc
  def createPhysicalCardLegacyDoc = MessageDoc(
    process = "obp.createPhysicalCardLegacy",
    messageFormat = messageFormat,
    description = "Create Physical Card Legacy",
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
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /createPhysicalCardLegacy
  override def createPhysicalCardLegacy(bankCardNumber: String, nameOnCard: String, cardType: String, issueNumber: String, serialNumber: String, validFrom: Date, expires: Date, enabled: Boolean, cancelled: Boolean, onHotList: Boolean, technology: String, networks: List[String], allows: List[String], accountId: String, bankId: String, replacement: Option[CardReplacementInfo], pinResets: List[PinResetInfo], collected: Option[CardCollectionInfo], posted: Option[CardPostedInfo], customerId: String, callContext: Option[CallContext]): Box[PhysicalCard] = {
        import com.openbankproject.commons.dto.{OutBoundCreatePhysicalCardLegacy => OutBound, InBoundCreatePhysicalCardLegacy => InBound}
        val url = getUrl(callContext, "createPhysicalCardLegacy")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankCardNumber, nameOnCard, cardType, issueNumber, serialNumber, validFrom, expires, enabled, cancelled, onHotList, technology, networks, allows, accountId, bankId, replacement, pinResets, collected, posted, customerId)
        val result: OBPReturnType[Box[PhysicalCard]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createPhysicalCardDoc
  def createPhysicalCardDoc = MessageDoc(
    process = "obp.createPhysicalCard",
    messageFormat = messageFormat,
    description = "Create Physical Card",
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
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /createPhysicalCard
  override def createPhysicalCard(bankCardNumber: String, nameOnCard: String, cardType: String, issueNumber: String, serialNumber: String, validFrom: Date, expires: Date, enabled: Boolean, cancelled: Boolean, onHotList: Boolean, technology: String, networks: List[String], allows: List[String], accountId: String, bankId: String, replacement: Option[CardReplacementInfo], pinResets: List[PinResetInfo], collected: Option[CardCollectionInfo], posted: Option[CardPostedInfo], customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[PhysicalCard]] = {
        import com.openbankproject.commons.dto.{OutBoundCreatePhysicalCard => OutBound, InBoundCreatePhysicalCard => InBound}
        val url = getUrl(callContext, "createPhysicalCard")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankCardNumber, nameOnCard, cardType, issueNumber, serialNumber, validFrom, expires, enabled, cancelled, onHotList, technology, networks, allows, accountId, bankId, replacement, pinResets, collected, posted, customerId)
        val result: OBPReturnType[Box[PhysicalCard]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += updatePhysicalCardDoc
  def updatePhysicalCardDoc = MessageDoc(
    process = "obp.updatePhysicalCard",
    messageFormat = messageFormat,
    description = "Update Physical Card",
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
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /updatePhysicalCard
  override def updatePhysicalCard(cardId: String, bankCardNumber: String, nameOnCard: String, cardType: String, issueNumber: String, serialNumber: String, validFrom: Date, expires: Date, enabled: Boolean, cancelled: Boolean, onHotList: Boolean, technology: String, networks: List[String], allows: List[String], accountId: String, bankId: String, replacement: Option[CardReplacementInfo], pinResets: List[PinResetInfo], collected: Option[CardCollectionInfo], posted: Option[CardPostedInfo], customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[PhysicalCardTrait]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdatePhysicalCard => OutBound, InBoundUpdatePhysicalCard => InBound}
        val url = getUrl(callContext, "updatePhysicalCard")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , cardId, bankCardNumber, nameOnCard, cardType, issueNumber, serialNumber, validFrom, expires, enabled, cancelled, onHotList, technology, networks, allows, accountId, bankId, replacement, pinResets, collected, posted, customerId)
        val result: OBPReturnType[Box[PhysicalCard]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += makePaymentv210Doc
  def makePaymentv210Doc = MessageDoc(
    process = "obp.makePaymentv210",
    messageFormat = messageFormat,
    description = "Make Paymentv210",
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      transactionRequestId = TransactionRequestId(uuidExample.value),
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
  // url example: /makePaymentv210
  override def makePaymentv210(fromAccount: BankAccount, toAccount: BankAccount, transactionRequestId: TransactionRequestId, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, amount: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String, callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]] = {
        import com.openbankproject.commons.dto.{OutBoundMakePaymentv210 => OutBound, InBoundMakePaymentv210 => InBound}
        val url = getUrl(callContext, "makePaymentv210")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , fromAccount, toAccount, transactionRequestId, transactionRequestCommonBody, amount, description, transactionRequestType, chargePolicy)
        val result: OBPReturnType[Box[TransactionId]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createTransactionRequestv210Doc
  def createTransactionRequestv210Doc = MessageDoc(
    process = "obp.createTransactionRequestv210",
    messageFormat = messageFormat,
    description = "Create Transaction Requestv210",
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /createTransactionRequestv210
  override def createTransactionRequestv210(initiator: User, viewId: ViewId, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, detailsPlain: String, chargePolicy: String, challengeType: Option[String], scaMethod: Option[SCA], callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionRequestv210 => OutBound, InBoundCreateTransactionRequestv210 => InBound}
        val url = getUrl(callContext, "createTransactionRequestv210")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , initiator, viewId, fromAccount, toAccount, transactionRequestType, transactionRequestCommonBody, detailsPlain, chargePolicy, challengeType, scaMethod)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getTransactionRequests210Doc
  def getTransactionRequests210Doc = MessageDoc(
    process = "obp.getTransactionRequests210",
    messageFormat = messageFormat,
    description = "Get Transaction Requests210",
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /getTransactionRequests210
  override def getTransactionRequests210(initiator: User, fromAccount: BankAccount, callContext: Option[CallContext]): Box[(List[TransactionRequest], Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionRequests210 => OutBound, InBoundGetTransactionRequests210 => InBound}
        val url = getUrl(callContext, "getTransactionRequests210")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , initiator, fromAccount)
        val result: OBPReturnType[Box[List[TransactionRequest]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getTransactionRequestImplDoc
  def getTransactionRequestImplDoc = MessageDoc(
    process = "obp.getTransactionRequestImpl",
    messageFormat = messageFormat,
    description = "Get Transaction Request Impl",
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
  // url example: /getTransactionRequestImpl
  override def getTransactionRequestImpl(transactionRequestId: TransactionRequestId, callContext: Option[CallContext]): Box[(TransactionRequest, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetTransactionRequestImpl => OutBound, InBoundGetTransactionRequestImpl => InBound}
        val url = getUrl(callContext, "getTransactionRequestImpl")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , transactionRequestId)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createTransactionAfterChallengeV210Doc
  def createTransactionAfterChallengeV210Doc = MessageDoc(
    process = "obp.createTransactionAfterChallengeV210",
    messageFormat = messageFormat,
    description = "Create Transaction After Challenge V210",
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /createTransactionAfterChallengeV210
  override def createTransactionAfterChallengeV210(fromAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionAfterChallengeV210 => OutBound, InBoundCreateTransactionAfterChallengeV210 => InBound}
        val url = getUrl(callContext, "createTransactionAfterChallengeV210")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , fromAccount, transactionRequest)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += updateBankAccountDoc
  def updateBankAccountDoc = MessageDoc(
    process = "obp.updateBankAccount",
    messageFormat = messageFormat,
    description = "Update Bank Account",
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
      accountRoutings=List(AccountRouting(accountRoutingSchemeExample.value, accountRoutingAddressExample.value)))
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /updateBankAccount
  override def updateBankAccount(bankId: BankId, accountId: AccountId, accountType: String, accountLabel: String, branchId: String, accountRoutings: List[AccountRouting], callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdateBankAccount => OutBound, InBoundUpdateBankAccount => InBound}
        val url = getUrl(callContext, "updateBankAccount")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, accountType, accountLabel, branchId, accountRoutings)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createBankAccountDoc
  def createBankAccountDoc = MessageDoc(
    process = "obp.createBankAccount",
    messageFormat = messageFormat,
    description = "Create Bank Account",
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
      List(AccountRouting(accountRoutingSchemeExample.value, accountRoutingAddressExample.value))
     )
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /createBankAccount
  override def createBankAccount(bankId: BankId, accountId: AccountId, accountType: String, accountLabel: String, currency: String, initialBalance: BigDecimal, accountHolderName: String, branchId: String, accountRoutings: List[AccountRouting], callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateBankAccount => OutBound, InBoundCreateBankAccount => InBound}
        val url = getUrl(callContext, "createBankAccount")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, accountType, accountLabel, currency, initialBalance, accountHolderName, branchId, accountRoutings)
        val result: OBPReturnType[Box[BankAccountCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBranchDoc
  def getBranchDoc = MessageDoc(
    process = "obp.getBranch",
    messageFormat = messageFormat,
    description = "Get Branch",
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
  // url example: /getBranch
  override def getBranch(bankId: BankId, branchId: BranchId, callContext: Option[CallContext]): Future[Box[(BranchT, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBranch => OutBound, InBoundGetBranch => InBound}
        val url = getUrl(callContext, "getBranch")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, branchId)
        val result: OBPReturnType[Box[BranchTCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getBranchesDoc
  def getBranchesDoc = MessageDoc(
    process = "obp.getBranches",
    messageFormat = messageFormat,
    description = "Get Branches",
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
  // url example: /getBranches
  override def getBranches(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[(List[BranchT], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetBranches => OutBound, InBoundGetBranches => InBound}
        val url = getUrl(callContext, "getBranches")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[BranchTCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getAtmDoc
  def getAtmDoc = MessageDoc(
    process = "obp.getAtm",
    messageFormat = messageFormat,
    description = "Get Atm",
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
  // url example: /getAtm
  override def getAtm(bankId: BankId, atmId: AtmId, callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAtm => OutBound, InBoundGetAtm => InBound}
        val url = getUrl(callContext, "getAtm")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, atmId)
        val result: OBPReturnType[Box[AtmTCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getAtmsDoc
  def getAtmsDoc = MessageDoc(
    process = "obp.getAtms",
    messageFormat = messageFormat,
    description = "Get Atms",
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
  // url example: /getAtms
  override def getAtms(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[(List[AtmT], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAtms => OutBound, InBoundGetAtms => InBound}
        val url = getUrl(callContext, "getAtms")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[AtmTCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createTransactionAfterChallengev300Doc
  def createTransactionAfterChallengev300Doc = MessageDoc(
    process = "obp.createTransactionAfterChallengev300",
    messageFormat = messageFormat,
    description = "Create Transaction After Challengev300",
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
  // url example: /createTransactionAfterChallengev300
  override def createTransactionAfterChallengev300(initiator: User, fromAccount: BankAccount, transReqId: TransactionRequestId, transactionRequestType: TransactionRequestType, callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionAfterChallengev300 => OutBound, InBoundCreateTransactionAfterChallengev300 => InBound}
        val url = getUrl(callContext, "createTransactionAfterChallengev300")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , initiator, fromAccount, transReqId, transactionRequestType)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += makePaymentv300Doc
  def makePaymentv300Doc = MessageDoc(
    process = "obp.makePaymentv300",
    messageFormat = messageFormat,
    description = "Make Paymentv300",
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toCounterparty= CounterpartyTraitCommons(createdByUserId="string",
      name="string",
      description="string",
      currency=currencyExample.value,
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
  // url example: /makePaymentv300
  override def makePaymentv300(initiator: User, fromAccount: BankAccount, toAccount: BankAccount, toCounterparty: CounterpartyTrait, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, transactionRequestType: TransactionRequestType, chargePolicy: String, callContext: Option[CallContext]): Future[Box[(TransactionId, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundMakePaymentv300 => OutBound, InBoundMakePaymentv300 => InBound}
        val url = getUrl(callContext, "makePaymentv300")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , initiator, fromAccount, toAccount, toCounterparty, transactionRequestCommonBody, transactionRequestType, chargePolicy)
        val result: OBPReturnType[Box[TransactionId]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createTransactionRequestv300Doc
  def createTransactionRequestv300Doc = MessageDoc(
    process = "obp.createTransactionRequestv300",
    messageFormat = messageFormat,
    description = "Create Transaction Requestv300",
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      toCounterparty= CounterpartyTraitCommons(createdByUserId="string",
      name="string",
      description="string",
      currency=currencyExample.value,
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
  // url example: /createTransactionRequestv300
  override def createTransactionRequestv300(initiator: User, viewId: ViewId, fromAccount: BankAccount, toAccount: BankAccount, toCounterparty: CounterpartyTrait, transactionRequestType: TransactionRequestType, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, detailsPlain: String, chargePolicy: String, callContext: Option[CallContext]): Future[Box[(TransactionRequest, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTransactionRequestv300 => OutBound, InBoundCreateTransactionRequestv300 => InBound}
        val url = getUrl(callContext, "createTransactionRequestv300")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , initiator, viewId, fromAccount, toAccount, toCounterparty, transactionRequestType, transactionRequestCommonBody, detailsPlain, chargePolicy)
        val result: OBPReturnType[Box[TransactionRequest]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createCounterpartyDoc
  def createCounterpartyDoc = MessageDoc(
    process = "obp.createCounterparty",
    messageFormat = messageFormat,
    description = "Create Counterparty",
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
      currency=currencyExample.value,
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
      currency=currencyExample.value,
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
  // url example: /createCounterparty
  override def createCounterparty(name: String, description: String, currency: String, createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String, otherAccountRoutingScheme: String, otherAccountRoutingAddress: String, otherAccountSecondaryRoutingScheme: String, otherAccountSecondaryRoutingAddress: String, otherBankRoutingScheme: String, otherBankRoutingAddress: String, otherBranchRoutingScheme: String, otherBranchRoutingAddress: String, isBeneficiary: Boolean, bespoke: List[CounterpartyBespoke], callContext: Option[CallContext]): Box[(CounterpartyTrait, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundCreateCounterparty => OutBound, InBoundCreateCounterparty => InBound}
        val url = getUrl(callContext, "createCounterparty")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , name, description, currency, createdByUserId, thisBankId, thisAccountId, thisViewId, otherAccountRoutingScheme, otherAccountRoutingAddress, otherAccountSecondaryRoutingScheme, otherAccountSecondaryRoutingAddress, otherBankRoutingScheme, otherBankRoutingAddress, otherBranchRoutingScheme, otherBranchRoutingAddress, isBeneficiary, bespoke)
        val result: OBPReturnType[Box[CounterpartyTraitCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += checkCustomerNumberAvailableDoc
  def checkCustomerNumberAvailableDoc = MessageDoc(
    process = "obp.checkCustomerNumberAvailable",
    messageFormat = messageFormat,
    description = "Check Customer Number Available",
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
  // url example: /checkCustomerNumberAvailable
  override def checkCustomerNumberAvailable(bankId: BankId, customerNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundCheckCustomerNumberAvailable => OutBound, InBoundCheckCustomerNumberAvailable => InBound}
        val url = getUrl(callContext, "checkCustomerNumberAvailable")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, customerNumber)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createCustomerDoc
  def createCustomerDoc = MessageDoc(
    process = "obp.createCustomer",
    messageFormat = messageFormat,
    description = "Create Customer",
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
  // url example: /createCustomer
  override def createCustomer(bankId: BankId, legalName: String, mobileNumber: String, email: String, faceImage: CustomerFaceImageTrait, dateOfBirth: Date, relationshipStatus: String, dependents: Int, dobOfDependents: List[Date], highestEducationAttained: String, employmentStatus: String, kycStatus: Boolean, lastOkDate: Date, creditRating: Option[CreditRatingTrait], creditLimit: Option[AmountOfMoneyTrait], title: String, branchId: String, nameSuffix: String, callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateCustomer => OutBound, InBoundCreateCustomer => InBound}
        val url = getUrl(callContext, "createCustomer")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, legalName, mobileNumber, email, faceImage, dateOfBirth, relationshipStatus, dependents, dobOfDependents, highestEducationAttained, employmentStatus, kycStatus, lastOkDate, creditRating, creditLimit, title, branchId, nameSuffix)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += updateCustomerScaDataDoc
  def updateCustomerScaDataDoc = MessageDoc(
    process = "obp.updateCustomerScaData",
    messageFormat = messageFormat,
    description = "Update Customer Sca Data",
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
  // url example: /updateCustomerScaData
  override def updateCustomerScaData(customerId: String, mobileNumber: Option[String], email: Option[String], customerNumber: Option[String], callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdateCustomerScaData => OutBound, InBoundUpdateCustomerScaData => InBound}
        val url = getUrl(callContext, "updateCustomerScaData")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId, mobileNumber, email, customerNumber)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += updateCustomerCreditDataDoc
  def updateCustomerCreditDataDoc = MessageDoc(
    process = "obp.updateCustomerCreditData",
    messageFormat = messageFormat,
    description = "Update Customer Credit Data",
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
  // url example: /updateCustomerCreditData
  override def updateCustomerCreditData(customerId: String, creditRating: Option[String], creditSource: Option[String], creditLimit: Option[AmountOfMoney], callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdateCustomerCreditData => OutBound, InBoundUpdateCustomerCreditData => InBound}
        val url = getUrl(callContext, "updateCustomerCreditData")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId, creditRating, creditSource, creditLimit)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += updateCustomerGeneralDataDoc
  def updateCustomerGeneralDataDoc = MessageDoc(
    process = "obp.updateCustomerGeneralData",
    messageFormat = messageFormat,
    description = "Update Customer General Data",
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
  // url example: /updateCustomerGeneralData
  override def updateCustomerGeneralData(customerId: String, legalName: Option[String], faceImage: Option[CustomerFaceImageTrait], dateOfBirth: Option[Date], relationshipStatus: Option[String], dependents: Option[Int], highestEducationAttained: Option[String], employmentStatus: Option[String], title: Option[String], branchId: Option[String], nameSuffix: Option[String], callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdateCustomerGeneralData => OutBound, InBoundUpdateCustomerGeneralData => InBound}
        val url = getUrl(callContext, "updateCustomerGeneralData")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId, legalName, faceImage, dateOfBirth, relationshipStatus, dependents, highestEducationAttained, employmentStatus, title, branchId, nameSuffix)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCustomersByUserIdDoc
  def getCustomersByUserIdDoc = MessageDoc(
    process = "obp.getCustomersByUserId",
    messageFormat = messageFormat,
    description = "Get Customers By User Id",
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
  // url example: /getCustomersByUserId
  override def getCustomersByUserId(userId: String, callContext: Option[CallContext]): Future[Box[(List[Customer], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCustomersByUserId => OutBound, InBoundGetCustomersByUserId => InBound}
        val url = getUrl(callContext, "getCustomersByUserId")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , userId)
        val result: OBPReturnType[Box[List[CustomerCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCustomerByCustomerIdLegacyDoc
  def getCustomerByCustomerIdLegacyDoc = MessageDoc(
    process = "obp.getCustomerByCustomerIdLegacy",
    messageFormat = messageFormat,
    description = "Get Customer By Customer Id Legacy",
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
  // url example: /getCustomerByCustomerIdLegacy
  override def getCustomerByCustomerIdLegacy(customerId: String, callContext: Option[CallContext]): Box[(Customer, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{OutBoundGetCustomerByCustomerIdLegacy => OutBound, InBoundGetCustomerByCustomerIdLegacy => InBound}
        val url = getUrl(callContext, "getCustomerByCustomerIdLegacy")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCustomerByCustomerIdDoc
  def getCustomerByCustomerIdDoc = MessageDoc(
    process = "obp.getCustomerByCustomerId",
    messageFormat = messageFormat,
    description = "Get Customer By Customer Id",
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
  // url example: /getCustomerByCustomerId
  override def getCustomerByCustomerId(customerId: String, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCustomerByCustomerId => OutBound, InBoundGetCustomerByCustomerId => InBound}
        val url = getUrl(callContext, "getCustomerByCustomerId")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCustomerByCustomerNumberDoc
  def getCustomerByCustomerNumberDoc = MessageDoc(
    process = "obp.getCustomerByCustomerNumber",
    messageFormat = messageFormat,
    description = "Get Customer By Customer Number",
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
  // url example: /getCustomerByCustomerNumber
  override def getCustomerByCustomerNumber(customerNumber: String, bankId: BankId, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCustomerByCustomerNumber => OutBound, InBoundGetCustomerByCustomerNumber => InBound}
        val url = getUrl(callContext, "getCustomerByCustomerNumber")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerNumber, bankId)
        val result: OBPReturnType[Box[CustomerCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCustomerAddressDoc
  def getCustomerAddressDoc = MessageDoc(
    process = "obp.getCustomerAddress",
    messageFormat = messageFormat,
    description = "Get Customer Address",
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
  // url example: /getCustomerAddress
  override def getCustomerAddress(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAddress]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCustomerAddress => OutBound, InBoundGetCustomerAddress => InBound}
        val url = getUrl(callContext, "getCustomerAddress")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[List[CustomerAddressCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createCustomerAddressDoc
  def createCustomerAddressDoc = MessageDoc(
    process = "obp.createCustomerAddress",
    messageFormat = messageFormat,
    description = "Create Customer Address",
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
  // url example: /createCustomerAddress
  override def createCustomerAddress(customerId: String, line1: String, line2: String, line3: String, city: String, county: String, state: String, postcode: String, countryCode: String, tags: String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateCustomerAddress => OutBound, InBoundCreateCustomerAddress => InBound}
        val url = getUrl(callContext, "createCustomerAddress")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId, line1, line2, line3, city, county, state, postcode, countryCode, tags, status)
        val result: OBPReturnType[Box[CustomerAddressCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += updateCustomerAddressDoc
  def updateCustomerAddressDoc = MessageDoc(
    process = "obp.updateCustomerAddress",
    messageFormat = messageFormat,
    description = "Update Customer Address",
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
  // url example: /updateCustomerAddress
  override def updateCustomerAddress(customerAddressId: String, line1: String, line2: String, line3: String, city: String, county: String, state: String, postcode: String, countryCode: String, tags: String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdateCustomerAddress => OutBound, InBoundUpdateCustomerAddress => InBound}
        val url = getUrl(callContext, "updateCustomerAddress")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerAddressId, line1, line2, line3, city, county, state, postcode, countryCode, tags, status)
        val result: OBPReturnType[Box[CustomerAddressCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += deleteCustomerAddressDoc
  def deleteCustomerAddressDoc = MessageDoc(
    process = "obp.deleteCustomerAddress",
    messageFormat = messageFormat,
    description = "Delete Customer Address",
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
  // url example: /deleteCustomerAddress
  override def deleteCustomerAddress(customerAddressId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundDeleteCustomerAddress => OutBound, InBoundDeleteCustomerAddress => InBound}
        val url = getUrl(callContext, "deleteCustomerAddress")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerAddressId)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](url, HttpMethods.DELETE, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createTaxResidenceDoc
  def createTaxResidenceDoc = MessageDoc(
    process = "obp.createTaxResidence",
    messageFormat = messageFormat,
    description = "Create Tax Residence",
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
  // url example: /createTaxResidence
  override def createTaxResidence(customerId: String, domain: String, taxNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[TaxResidence]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateTaxResidence => OutBound, InBoundCreateTaxResidence => InBound}
        val url = getUrl(callContext, "createTaxResidence")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId, domain, taxNumber)
        val result: OBPReturnType[Box[TaxResidenceCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getTaxResidenceDoc
  def getTaxResidenceDoc = MessageDoc(
    process = "obp.getTaxResidence",
    messageFormat = messageFormat,
    description = "Get Tax Residence",
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
  // url example: /getTaxResidence
  override def getTaxResidence(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[TaxResidence]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetTaxResidence => OutBound, InBoundGetTaxResidence => InBound}
        val url = getUrl(callContext, "getTaxResidence")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[List[TaxResidenceCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += deleteTaxResidenceDoc
  def deleteTaxResidenceDoc = MessageDoc(
    process = "obp.deleteTaxResidence",
    messageFormat = messageFormat,
    description = "Delete Tax Residence",
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
  // url example: /deleteTaxResidence
  override def deleteTaxResidence(taxResourceId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundDeleteTaxResidence => OutBound, InBoundDeleteTaxResidence => InBound}
        val url = getUrl(callContext, "deleteTaxResidence")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , taxResourceId)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](url, HttpMethods.DELETE, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCustomersDoc
  def getCustomersDoc = MessageDoc(
    process = "obp.getCustomers",
    messageFormat = messageFormat,
    description = "Get Customers",
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
  // url example: /getCustomers
  override def getCustomers(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCustomers => OutBound, InBoundGetCustomers => InBound}
        val url = getUrl(callContext, "getCustomers")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val result: OBPReturnType[Box[List[CustomerCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCheckbookOrdersDoc
  def getCheckbookOrdersDoc = MessageDoc(
    process = "obp.getCheckbookOrders",
    messageFormat = messageFormat,
    description = "Get Checkbook Orders",
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
  // url example: /getCheckbookOrders
  override def getCheckbookOrders(bankId: String, accountId: String, callContext: Option[CallContext]): Future[Box[(CheckbookOrdersJson, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCheckbookOrders => OutBound, InBoundGetCheckbookOrders => InBound}
        val url = getUrl(callContext, "getCheckbookOrders")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId)
        val result: OBPReturnType[Box[CheckbookOrdersJson]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getStatusOfCreditCardOrderDoc
  def getStatusOfCreditCardOrderDoc = MessageDoc(
    process = "obp.getStatusOfCreditCardOrder",
    messageFormat = messageFormat,
    description = "Get Status Of Credit Card Order",
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
  // url example: /getStatusOfCreditCardOrder
  override def getStatusOfCreditCardOrder(bankId: String, accountId: String, callContext: Option[CallContext]): Future[Box[(List[CardObjectJson], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{OutBoundGetStatusOfCreditCardOrder => OutBound, InBoundGetStatusOfCreditCardOrder => InBound}
        val url = getUrl(callContext, "getStatusOfCreditCardOrder")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId)
        val result: OBPReturnType[Box[List[CardObjectJson]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createUserAuthContextDoc
  def createUserAuthContextDoc = MessageDoc(
    process = "obp.createUserAuthContext",
    messageFormat = messageFormat,
    description = "Create User Auth Context",
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
  // url example: /createUserAuthContext
  override def createUserAuthContext(userId: String, key: String, value: String, callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContext]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateUserAuthContext => OutBound, InBoundCreateUserAuthContext => InBound}
        val url = getUrl(callContext, "createUserAuthContext")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , userId, key, value)
        val result: OBPReturnType[Box[UserAuthContextCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createUserAuthContextUpdateDoc
  def createUserAuthContextUpdateDoc = MessageDoc(
    process = "obp.createUserAuthContextUpdate",
    messageFormat = messageFormat,
    description = "Create User Auth Context Update",
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
  // url example: /createUserAuthContextUpdate
  override def createUserAuthContextUpdate(userId: String, key: String, value: String, callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContextUpdate]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateUserAuthContextUpdate => OutBound, InBoundCreateUserAuthContextUpdate => InBound}
        val url = getUrl(callContext, "createUserAuthContextUpdate")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , userId, key, value)
        val result: OBPReturnType[Box[UserAuthContextUpdateCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += deleteUserAuthContextsDoc
  def deleteUserAuthContextsDoc = MessageDoc(
    process = "obp.deleteUserAuthContexts",
    messageFormat = messageFormat,
    description = "Delete User Auth Contexts",
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
  // url example: /deleteUserAuthContexts
  override def deleteUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundDeleteUserAuthContexts => OutBound, InBoundDeleteUserAuthContexts => InBound}
        val url = getUrl(callContext, "deleteUserAuthContexts")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , userId)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](url, HttpMethods.DELETE, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += deleteUserAuthContextByIdDoc
  def deleteUserAuthContextByIdDoc = MessageDoc(
    process = "obp.deleteUserAuthContextById",
    messageFormat = messageFormat,
    description = "Delete User Auth Context By Id",
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
  // url example: /deleteUserAuthContextById
  override def deleteUserAuthContextById(userAuthContextId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundDeleteUserAuthContextById => OutBound, InBoundDeleteUserAuthContextById => InBound}
        val url = getUrl(callContext, "deleteUserAuthContextById")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , userAuthContextId)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](url, HttpMethods.DELETE, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getUserAuthContextsDoc
  def getUserAuthContextsDoc = MessageDoc(
    process = "obp.getUserAuthContexts",
    messageFormat = messageFormat,
    description = "Get User Auth Contexts",
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
  // url example: /getUserAuthContexts
  override def getUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[UserAuthContext]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetUserAuthContexts => OutBound, InBoundGetUserAuthContexts => InBound}
        val url = getUrl(callContext, "getUserAuthContexts")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , userId)
        val result: OBPReturnType[Box[List[UserAuthContextCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createOrUpdateProductAttributeDoc
  def createOrUpdateProductAttributeDoc = MessageDoc(
    process = "obp.createOrUpdateProductAttribute",
    messageFormat = messageFormat,
    description = "Create Or Update Product Attribute",
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
  // url example: /createOrUpdateProductAttribute
  override def createOrUpdateProductAttribute(bankId: BankId, productCode: ProductCode, productAttributeId: Option[String], name: String, productAttributeType: ProductAttributeType.Value, value: String, callContext: Option[CallContext]): OBPReturnType[Box[ProductAttribute]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateProductAttribute => OutBound, InBoundCreateOrUpdateProductAttribute => InBound}
        val url = getUrl(callContext, "createOrUpdateProductAttribute")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, productCode, productAttributeId, name, productAttributeType, value)
        val result: OBPReturnType[Box[ProductAttributeCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getProductAttributeByIdDoc
  def getProductAttributeByIdDoc = MessageDoc(
    process = "obp.getProductAttributeById",
    messageFormat = messageFormat,
    description = "Get Product Attribute By Id",
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
  // url example: /getProductAttributeById
  override def getProductAttributeById(productAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[ProductAttribute]] = {
        import com.openbankproject.commons.dto.{OutBoundGetProductAttributeById => OutBound, InBoundGetProductAttributeById => InBound}
        val url = getUrl(callContext, "getProductAttributeById")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , productAttributeId)
        val result: OBPReturnType[Box[ProductAttributeCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getProductAttributesByBankAndCodeDoc
  def getProductAttributesByBankAndCodeDoc = MessageDoc(
    process = "obp.getProductAttributesByBankAndCode",
    messageFormat = messageFormat,
    description = "Get Product Attributes By Bank And Code",
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
  // url example: /getProductAttributesByBankAndCode
  override def getProductAttributesByBankAndCode(bank: BankId, productCode: ProductCode, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductAttribute]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetProductAttributesByBankAndCode => OutBound, InBoundGetProductAttributesByBankAndCode => InBound}
        val url = getUrl(callContext, "getProductAttributesByBankAndCode")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bank, productCode)
        val result: OBPReturnType[Box[List[ProductAttributeCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += deleteProductAttributeDoc
  def deleteProductAttributeDoc = MessageDoc(
    process = "obp.deleteProductAttribute",
    messageFormat = messageFormat,
    description = "Delete Product Attribute",
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
  // url example: /deleteProductAttribute
  override def deleteProductAttribute(productAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{OutBoundDeleteProductAttribute => OutBound, InBoundDeleteProductAttribute => InBound}
        val url = getUrl(callContext, "deleteProductAttribute")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , productAttributeId)
        val result: OBPReturnType[Box[Boolean]] = sendRequest[InBound](url, HttpMethods.DELETE, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getAccountAttributeByIdDoc
  def getAccountAttributeByIdDoc = MessageDoc(
    process = "obp.getAccountAttributeById",
    messageFormat = messageFormat,
    description = "Get Account Attribute By Id",
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
  // url example: /getAccountAttributeById
  override def getAccountAttributeById(accountAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountAttribute]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAccountAttributeById => OutBound, InBoundGetAccountAttributeById => InBound}
        val url = getUrl(callContext, "getAccountAttributeById")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , accountAttributeId)
        val result: OBPReturnType[Box[AccountAttributeCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createOrUpdateAccountAttributeDoc
  def createOrUpdateAccountAttributeDoc = MessageDoc(
    process = "obp.createOrUpdateAccountAttribute",
    messageFormat = messageFormat,
    description = "Create Or Update Account Attribute",
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
  // url example: /createOrUpdateAccountAttribute
  override def createOrUpdateAccountAttribute(bankId: BankId, accountId: AccountId, productCode: ProductCode, productAttributeId: Option[String], name: String, accountAttributeType: AccountAttributeType.Value, value: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountAttribute]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateAccountAttribute => OutBound, InBoundCreateOrUpdateAccountAttribute => InBound}
        val url = getUrl(callContext, "createOrUpdateAccountAttribute")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, productCode, productAttributeId, name, accountAttributeType, value)
        val result: OBPReturnType[Box[AccountAttributeCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createAccountAttributesDoc
  def createAccountAttributesDoc = MessageDoc(
    process = "obp.createAccountAttributes",
    messageFormat = messageFormat,
    description = "Create Account Attributes",
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
  // url example: /createAccountAttributes
  override def createAccountAttributes(bankId: BankId, accountId: AccountId, productCode: ProductCode, accountAttributes: List[ProductAttribute], callContext: Option[CallContext]): OBPReturnType[Box[List[AccountAttribute]]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateAccountAttributes => OutBound, InBoundCreateAccountAttributes => InBound}
        val url = getUrl(callContext, "createAccountAttributes")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId, productCode, accountAttributes)
        val result: OBPReturnType[Box[List[AccountAttributeCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getAccountAttributesByAccountDoc
  def getAccountAttributesByAccountDoc = MessageDoc(
    process = "obp.getAccountAttributesByAccount",
    messageFormat = messageFormat,
    description = "Get Account Attributes By Account",
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
  // url example: /getAccountAttributesByAccount
  override def getAccountAttributesByAccount(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[List[AccountAttribute]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAccountAttributesByAccount => OutBound, InBoundGetAccountAttributesByAccount => InBound}
        val url = getUrl(callContext, "getAccountAttributesByAccount")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, accountId)
        val result: OBPReturnType[Box[List[AccountAttributeCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createOrUpdateCardAttributeDoc
  def createOrUpdateCardAttributeDoc = MessageDoc(
    process = "obp.createOrUpdateCardAttribute",
    messageFormat = messageFormat,
    description = "Create Or Update Card Attribute",
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
  // url example: /createOrUpdateCardAttribute
  override def createOrUpdateCardAttribute(bankId: Option[BankId], cardId: Option[String], cardAttributeId: Option[String], name: String, cardAttributeType: CardAttributeType.Value, value: String, callContext: Option[CallContext]): OBPReturnType[Box[CardAttribute]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateCardAttribute => OutBound, InBoundCreateOrUpdateCardAttribute => InBound}
        val url = getUrl(callContext, "createOrUpdateCardAttribute")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, cardId, cardAttributeId, name, cardAttributeType, value)
        val result: OBPReturnType[Box[CardAttributeCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCardAttributeByIdDoc
  def getCardAttributeByIdDoc = MessageDoc(
    process = "obp.getCardAttributeById",
    messageFormat = messageFormat,
    description = "Get Card Attribute By Id",
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
  // url example: /getCardAttributeById
  override def getCardAttributeById(cardAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[CardAttribute]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCardAttributeById => OutBound, InBoundGetCardAttributeById => InBound}
        val url = getUrl(callContext, "getCardAttributeById")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , cardAttributeId)
        val result: OBPReturnType[Box[CardAttributeCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getCardAttributesFromProviderDoc
  def getCardAttributesFromProviderDoc = MessageDoc(
    process = "obp.getCardAttributesFromProvider",
    messageFormat = messageFormat,
    description = "Get Card Attributes From Provider",
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
  // url example: /getCardAttributesFromProvider
  override def getCardAttributesFromProvider(cardId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[CardAttribute]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetCardAttributesFromProvider => OutBound, InBoundGetCardAttributesFromProvider => InBound}
        val url = getUrl(callContext, "getCardAttributesFromProvider")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , cardId)
        val result: OBPReturnType[Box[List[CardAttributeCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createAccountApplicationDoc
  def createAccountApplicationDoc = MessageDoc(
    process = "obp.createAccountApplication",
    messageFormat = messageFormat,
    description = "Create Account Application",
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
  // url example: /createAccountApplication
  override def createAccountApplication(productCode: ProductCode, userId: Option[String], customerId: Option[String], callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateAccountApplication => OutBound, InBoundCreateAccountApplication => InBound}
        val url = getUrl(callContext, "createAccountApplication")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , productCode, userId, customerId)
        val result: OBPReturnType[Box[AccountApplicationCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getAllAccountApplicationDoc
  def getAllAccountApplicationDoc = MessageDoc(
    process = "obp.getAllAccountApplication",
    messageFormat = messageFormat,
    description = "Get All Account Application",
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
  // url example: /getAllAccountApplication
  override def getAllAccountApplication(callContext: Option[CallContext]): OBPReturnType[Box[List[AccountApplication]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAllAccountApplication => OutBound, InBoundGetAllAccountApplication => InBound}
        val url = getUrl(callContext, "getAllAccountApplication")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull )
        val result: OBPReturnType[Box[List[AccountApplicationCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getAccountApplicationByIdDoc
  def getAccountApplicationByIdDoc = MessageDoc(
    process = "obp.getAccountApplicationById",
    messageFormat = messageFormat,
    description = "Get Account Application By Id",
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
  // url example: /getAccountApplicationById
  override def getAccountApplicationById(accountApplicationId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] = {
        import com.openbankproject.commons.dto.{OutBoundGetAccountApplicationById => OutBound, InBoundGetAccountApplicationById => InBound}
        val url = getUrl(callContext, "getAccountApplicationById")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , accountApplicationId)
        val result: OBPReturnType[Box[AccountApplicationCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += updateAccountApplicationStatusDoc
  def updateAccountApplicationStatusDoc = MessageDoc(
    process = "obp.updateAccountApplicationStatus",
    messageFormat = messageFormat,
    description = "Update Account Application Status",
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
  // url example: /updateAccountApplicationStatus
  override def updateAccountApplicationStatus(accountApplicationId: String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] = {
        import com.openbankproject.commons.dto.{OutBoundUpdateAccountApplicationStatus => OutBound, InBoundUpdateAccountApplicationStatus => InBound}
        val url = getUrl(callContext, "updateAccountApplicationStatus")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , accountApplicationId, status)
        val result: OBPReturnType[Box[AccountApplicationCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getOrCreateProductCollectionDoc
  def getOrCreateProductCollectionDoc = MessageDoc(
    process = "obp.getOrCreateProductCollection",
    messageFormat = messageFormat,
    description = "Get Or Create Product Collection",
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
  // url example: /getOrCreateProductCollection
  override def getOrCreateProductCollection(collectionCode: String, productCodes: List[String], callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetOrCreateProductCollection => OutBound, InBoundGetOrCreateProductCollection => InBound}
        val url = getUrl(callContext, "getOrCreateProductCollection")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , collectionCode, productCodes)
        val result: OBPReturnType[Box[List[ProductCollectionCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getProductCollectionDoc
  def getProductCollectionDoc = MessageDoc(
    process = "obp.getProductCollection",
    messageFormat = messageFormat,
    description = "Get Product Collection",
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
  // url example: /getProductCollection
  override def getProductCollection(collectionCode: String, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetProductCollection => OutBound, InBoundGetProductCollection => InBound}
        val url = getUrl(callContext, "getProductCollection")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , collectionCode)
        val result: OBPReturnType[Box[List[ProductCollectionCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getOrCreateProductCollectionItemDoc
  def getOrCreateProductCollectionItemDoc = MessageDoc(
    process = "obp.getOrCreateProductCollectionItem",
    messageFormat = messageFormat,
    description = "Get Or Create Product Collection Item",
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
  // url example: /getOrCreateProductCollectionItem
  override def getOrCreateProductCollectionItem(collectionCode: String, memberProductCodes: List[String], callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetOrCreateProductCollectionItem => OutBound, InBoundGetOrCreateProductCollectionItem => InBound}
        val url = getUrl(callContext, "getOrCreateProductCollectionItem")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , collectionCode, memberProductCodes)
        val result: OBPReturnType[Box[List[ProductCollectionItemCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getProductCollectionItemDoc
  def getProductCollectionItemDoc = MessageDoc(
    process = "obp.getProductCollectionItem",
    messageFormat = messageFormat,
    description = "Get Product Collection Item",
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
  // url example: /getProductCollectionItem
  override def getProductCollectionItem(collectionCode: String, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetProductCollectionItem => OutBound, InBoundGetProductCollectionItem => InBound}
        val url = getUrl(callContext, "getProductCollectionItem")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , collectionCode)
        val result: OBPReturnType[Box[List[ProductCollectionItemCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }

  messageDocs += getProductCollectionItemsTreeDoc
  def getProductCollectionItemsTreeDoc = MessageDoc(
    process = "obp.getProductCollectionItemsTree",
    messageFormat = messageFormat,
    description = "Get Product Collection Items Tree",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetProductCollectionItemsTree(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
        collectionCode="string",
        bankId=bankIdExample.value)
      ),
    exampleInboundMessage = (
      InBoundGetProductCollectionItemsTree(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
        status=MessageDocsSwaggerDefinitions.inboundStatus,
        data=List( ProductCollectionItemsTree(productCollectionItem= ProductCollectionItemCommons(collectionCode="string",
          memberProductCode="string"),
          product= ProductCommons(bankId=BankId(bankIdExample.value),
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
              name="string"))),
          attributes=List( ProductAttributeCommons(bankId=BankId(bankIdExample.value),
            productCode=ProductCode("string"),
            productAttributeId="string",
            name="string",
            attributeType=com.openbankproject.commons.model.enums.ProductAttributeType.example,
            value=valueExample.value)))))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getProductCollectionItemsTree(collectionCode: String, bankId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItemsTree]]] = {
    import com.openbankproject.commons.dto.{OutBoundGetProductCollectionItemsTree => OutBound, InBoundGetProductCollectionItemsTree => InBound}
    val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, collectionCode, bankId)
    val response: Future[Box[InBound]] = sendRequest[InBound](getUrl(callContext, "getProductCollectionItemsTree"), HttpMethods.POST, req, callContext)
    response.map(convertToTuple[List[ProductCollectionItemsTree]](callContext))
  }
    
  messageDocs += createMeetingDoc
  def createMeetingDoc = MessageDoc(
    process = "obp.createMeeting",
    messageFormat = messageFormat,
    description = "Create Meeting",
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
  // url example: /createMeeting
  override def createMeeting(bankId: BankId, staffUser: User, customerUser: User, providerId: String, purposeId: String, when: Date, sessionId: String, customerToken: String, staffToken: String, creator: ContactDetails, invitees: List[Invitee], callContext: Option[CallContext]): OBPReturnType[Box[Meeting]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateMeeting => OutBound, InBoundCreateMeeting => InBound}
        val url = getUrl(callContext, "createMeeting")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, staffUser, customerUser, providerId, purposeId, when, sessionId, customerToken, staffToken, creator, invitees)
        val result: OBPReturnType[Box[MeetingCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getMeetingsDoc
  def getMeetingsDoc = MessageDoc(
    process = "obp.getMeetings",
    messageFormat = messageFormat,
    description = "Get Meetings",
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
  // url example: /getMeetings
  override def getMeetings(bankId: BankId, user: User, callContext: Option[CallContext]): OBPReturnType[Box[List[Meeting]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetMeetings => OutBound, InBoundGetMeetings => InBound}
        val url = getUrl(callContext, "getMeetings")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, user)
        val result: OBPReturnType[Box[List[MeetingCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getMeetingDoc
  def getMeetingDoc = MessageDoc(
    process = "obp.getMeeting",
    messageFormat = messageFormat,
    description = "Get Meeting",
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
  // url example: /getMeeting
  override def getMeeting(bankId: BankId, user: User, meetingId: String, callContext: Option[CallContext]): OBPReturnType[Box[Meeting]] = {
        import com.openbankproject.commons.dto.{OutBoundGetMeeting => OutBound, InBoundGetMeeting => InBound}
        val url = getUrl(callContext, "getMeeting")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, user, meetingId)
        val result: OBPReturnType[Box[MeetingCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createOrUpdateKycCheckDoc
  def createOrUpdateKycCheckDoc = MessageDoc(
    process = "obp.createOrUpdateKycCheck",
    messageFormat = messageFormat,
    description = "Create Or Update Kyc Check",
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
  // url example: /createOrUpdateKycCheck
  override def createOrUpdateKycCheck(bankId: String, customerId: String, id: String, customerNumber: String, date: Date, how: String, staffUserId: String, mStaffName: String, mSatisfied: Boolean, comments: String, callContext: Option[CallContext]): OBPReturnType[Box[KycCheck]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateKycCheck => OutBound, InBoundCreateOrUpdateKycCheck => InBound}
        val url = getUrl(callContext, "createOrUpdateKycCheck")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, customerId, id, customerNumber, date, how, staffUserId, mStaffName, mSatisfied, comments)
        val result: OBPReturnType[Box[KycCheckCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createOrUpdateKycDocumentDoc
  def createOrUpdateKycDocumentDoc = MessageDoc(
    process = "obp.createOrUpdateKycDocument",
    messageFormat = messageFormat,
    description = "Create Or Update Kyc Document",
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
  // url example: /createOrUpdateKycDocument
  override def createOrUpdateKycDocument(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, number: String, issueDate: Date, issuePlace: String, expiryDate: Date, callContext: Option[CallContext]): OBPReturnType[Box[KycDocument]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateKycDocument => OutBound, InBoundCreateOrUpdateKycDocument => InBound}
        val url = getUrl(callContext, "createOrUpdateKycDocument")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, customerId, id, customerNumber, `type`, number, issueDate, issuePlace, expiryDate)
        val result: OBPReturnType[Box[KycDocument]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createOrUpdateKycMediaDoc
  def createOrUpdateKycMediaDoc = MessageDoc(
    process = "obp.createOrUpdateKycMedia",
    messageFormat = messageFormat,
    description = "Create Or Update Kyc Media",
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
  // url example: /createOrUpdateKycMedia
  override def createOrUpdateKycMedia(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, url: String, date: Date, relatesToKycDocumentId: String, relatesToKycCheckId: String, callContext: Option[CallContext]): OBPReturnType[Box[KycMedia]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateKycMedia => OutBound, InBoundCreateOrUpdateKycMedia => InBound}
        val url = getUrl(callContext, "createOrUpdateKycMedia")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, customerId, id, customerNumber, `type`, url, date, relatesToKycDocumentId, relatesToKycCheckId)
        val result: OBPReturnType[Box[KycMediaCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createOrUpdateKycStatusDoc
  def createOrUpdateKycStatusDoc = MessageDoc(
    process = "obp.createOrUpdateKycStatus",
    messageFormat = messageFormat,
    description = "Create Or Update Kyc Status",
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
  // url example: /createOrUpdateKycStatus
  override def createOrUpdateKycStatus(bankId: String, customerId: String, customerNumber: String, ok: Boolean, date: Date, callContext: Option[CallContext]): OBPReturnType[Box[KycStatus]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateKycStatus => OutBound, InBoundCreateOrUpdateKycStatus => InBound}
        val url = getUrl(callContext, "createOrUpdateKycStatus")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , bankId, customerId, customerNumber, ok, date)
        val result: OBPReturnType[Box[KycStatusCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getKycChecksDoc
  def getKycChecksDoc = MessageDoc(
    process = "obp.getKycChecks",
    messageFormat = messageFormat,
    description = "Get Kyc Checks",
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
  // url example: /getKycChecks
  override def getKycChecks(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[KycCheck]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetKycChecks => OutBound, InBoundGetKycChecks => InBound}
        val url = getUrl(callContext, "getKycChecks")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[List[KycCheckCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getKycDocumentsDoc
  def getKycDocumentsDoc = MessageDoc(
    process = "obp.getKycDocuments",
    messageFormat = messageFormat,
    description = "Get Kyc Documents",
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
  // url example: /getKycDocuments
  override def getKycDocuments(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[KycDocument]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetKycDocuments => OutBound, InBoundGetKycDocuments => InBound}
        val url = getUrl(callContext, "getKycDocuments")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[List[KycDocumentCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getKycMediasDoc
  def getKycMediasDoc = MessageDoc(
    process = "obp.getKycMedias",
    messageFormat = messageFormat,
    description = "Get Kyc Medias",
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
  // url example: /getKycMedias
  override def getKycMedias(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[KycMedia]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetKycMedias => OutBound, InBoundGetKycMedias => InBound}
        val url = getUrl(callContext, "getKycMedias")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[List[KycMediaCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += getKycStatusesDoc
  def getKycStatusesDoc = MessageDoc(
    process = "obp.getKycStatuses",
    messageFormat = messageFormat,
    description = "Get Kyc Statuses",
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
  // url example: /getKycStatuses
  override def getKycStatuses(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[KycStatus]]] = {
        import com.openbankproject.commons.dto.{OutBoundGetKycStatuses => OutBound, InBoundGetKycStatuses => InBound}
        val url = getUrl(callContext, "getKycStatuses")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , customerId)
        val result: OBPReturnType[Box[List[KycStatusCommons]]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += createMessageDoc
  def createMessageDoc = MessageDoc(
    process = "obp.createMessage",
    messageFormat = messageFormat,
    description = "Create Message",
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
  // url example: /createMessage
  override def createMessage(user: User, bankId: BankId, message: String, fromDepartment: String, fromPerson: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerMessage]] = {
        import com.openbankproject.commons.dto.{OutBoundCreateMessage => OutBound, InBoundCreateMessage => InBound}
        val url = getUrl(callContext, "createMessage")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , user, bankId, message, fromDepartment, fromPerson)
        val result: OBPReturnType[Box[CustomerMessageCommons]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
        result
  }
    
  messageDocs += makeHistoricalPaymentDoc
  def makeHistoricalPaymentDoc = MessageDoc(
    process = "obp.makeHistoricalPayment",
    messageFormat = messageFormat,
    description = "Make Historical Payment",
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
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
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=parseDate(bankAccountLastUpdateExample.value).getOrElse(sys.error("bankAccountLastUpdateExample.value is not validate date format.")),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value),
      posted=new Date(),
      completed=new Date(),
      amount=BigDecimal("123.321"),
      currency=currencyExample.value,
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
  // url example: /makeHistoricalPayment
  override def makeHistoricalPayment(fromAccount: BankAccount, toAccount: BankAccount, posted: Date, completed: Date, amount: BigDecimal, currency: String, description: String, transactionRequestType: String, chargePolicy: String, callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]] = {
        import com.openbankproject.commons.dto.{OutBoundMakeHistoricalPayment => OutBound, InBoundMakeHistoricalPayment => InBound}
        val url = getUrl(callContext, "makeHistoricalPayment")
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , fromAccount, toAccount, posted, completed, amount, currency, description, transactionRequestType, chargePolicy)
        val result: OBPReturnType[Box[TransactionId]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
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
                                    bankId: Option[String],
                                    callContext: Option[CallContext]): OBPReturnType[Box[JValue]] = {
    import com.openbankproject.commons.dto.{OutBoundDynamicEntityProcess => OutBound, InBoundDynamicEntityProcess => InBound}
    val url = getUrl(callContext, "dynamicEntityProcess")
    val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , operation, entityName, requestBody, entityId, bankId)
    val result: OBPReturnType[Box[JValue]] = sendRequest[InBound](url, HttpMethods.POST, req, callContext).map(convertToTuple(callContext))
    result
  }


  override def dynamicEndpointProcess(url: String, jValue: JValue, method: HttpMethod, params: Map[String, List[String]], pathParams: Map[String, String],
                           callContext: Option[CallContext]): OBPReturnType[Box[JValue]] = {
    val urlInMethodRouting: Option[String] = MethodRoutingHolder.methodRouting match {
      case _: EmptyBox => None
      case Full(routing) => routing.parameters.find(_.key == "url").map(_.value)
    }
    val mockResponse: Box[(Int, JValue)] = MockResponseHolder.mockResponse

    // when there is no methodRouting, but there is mock response, just return mock response content
    if(urlInMethodRouting.isEmpty && mockResponse.isDefined) {
      val Full((code, body)) = mockResponse
      val response: JObject = ("code" -> code) ~ ("value" -> body)
      return Future.successful((Full(response), callContext))
    }

    val pathVariableRex = """\{:(.+?)\}""".r
    val targetUrl = urlInMethodRouting.map { urlInRouting =>
      val tuples: Iterator[(String, String)] = pathVariableRex.findAllMatchIn(urlInRouting).map{ regex =>
        val expression = regex.group(0)
        val paramName = regex.group(1)
        val paramValue =
          if(paramName.startsWith("body.")) {
            val path = StringUtils.substringAfter(paramName, "body.")
            val value = JsonUtils.getValueByPath(jValue, path)
            JsonUtils.toString(value)
          } else {
            pathParams.get(paramName)
              .orElse(params.get(paramName).flatMap(_.headOption)).getOrElse(throw new RuntimeException(s"param $paramName not exists."))
          }
        expression -> paramValue
      }

      tuples.foldLeft(urlInRouting) { (pre, kv)=>
        pre.replace(kv._1, kv._2)
      }
    }.getOrElse(url)

    val paramNameToValue = for {
      (name, values) <- params
      value <- values
      param = s"$name=$value"
    } yield param

    val paramUrl: String =
      if(params.isEmpty){
        targetUrl
      } else if(targetUrl.contains("?")) {
        targetUrl + "&" + paramNameToValue.mkString("&")
      } else {
        targetUrl + "?" + paramNameToValue.mkString("&")
      }

    val jsonToSend = if(jValue == JNothing) "" else compactRender(jValue)
    val request = prepareHttpRequest(paramUrl, method, HttpProtocol("HTTP/1.1"), jsonToSend).withHeaders(callContext)
    logger.debug(s"RestConnector_vMar2019 request is : $request")
    val responseFuture = makeHttpRequest(request)

    val result: Future[(Box[JValue], Option[CallContext])] = responseFuture.map {
      case HttpResponse(status, _, entity@_, _) =>
        (status, entity)
    }.flatMap {
      case (status, entity) if status.isSuccess() =>
        this.extractBody(entity)
          .map{
            case v if StringUtils.isBlank(v) =>
              (Full{
                ("code", status.intValue()) ~ ("value", JString(""))
              }, callContext)
            case v =>
              (Full{
                ("code", status.intValue()) ~ ("value", json.parse(v))
              }, callContext)
          }
      case (status, entity) => {
        val future: Future[JObject] = extractBody(entity) map { msg =>
          try {
            ("code", status.intValue()) ~ ("value", json.parse(msg))
          } catch {
            case _: ParseException => ("code", status.intValue()) ~ ("value", JString(msg))
          }
        }
        future.map { jObject =>
          (Full(jObject), callContext)
        }
      }
    }.recoverWith {
      case e: Exception if e.getMessage.contains(s"$httpRequestTimeout seconds") =>
        Future.failed(
          new Exception(s"$AdapterTimeOurError Please Check Adapter Side, the response should be returned to OBP-Side in $httpRequestTimeout seconds. Details: ${e.getMessage}", e)
        )
      case e: StreamTcpException if classOf[ConnectException].isInstance(e.getCause) || classOf[UnknownHostException].isInstance(e.getCause)=>
        logger.error(s"dynamic endpoint corresponding adapter function not available, the http method is: $method, url is ${method.value}", e)
        Future.failed(new Exception(s"$AdapterFunctionNotImplemented Please Check Rest Adapter Side! http method: ${method.value}, url: $paramUrl", e))
      case e: Exception =>
        Future.failed(new Exception(s"$AdapterUnknownError Please Check Adapter Side! Details: ${e.getMessage}", e))
    }

    result
  }
    

  //In RestConnector, we use the headers to propagate the parameters to Adapter. The parameters come from the CallContext.outboundAdapterAuthInfo.userAuthContext
  //We can set them from UserOauthContext or the http request headers.
  private[this] implicit def buildHeaders(callContext: Option[CallContext]): List[HttpHeader] = {
    val generalContext = callContext.flatMap(_.toOutboundAdapterCallContext.generalContext).getOrElse(List.empty[BasicGeneralContext])
    generalContext.map(generalContext => RawHeader(generalContext.key,generalContext.value))
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

  //TODO please modify this baseUrl to your remote api server base url of this connector
  private[this] val baseUrl = "http://localhost:8080/restConnector"

  private[this] def getUrl(callContext: Option[CallContext], methodName: String, variables: (String, Any)*): String = {
    // rest connector can have url value in the parameters, key is url
     
    //Temporary solution:
    val basicUserAuthContext: List[BasicUserAuthContext] = callContext.map(_.toOutboundAdapterCallContext.outboundAdapterAuthInfo.map(_.userAuthContext)).flatten.flatten.getOrElse(List.empty[BasicUserAuthContext])
    val bankId = basicUserAuthContext.find(_.key=="bank-id").map(_.value)
    val accountId = basicUserAuthContext.find(_.key=="account-id").map(_.value)
    val parameterUrl = if (bankId.isDefined && accountId.isDefined)  s"/${bankId.get},${accountId.get}" else ""
    
     //http://127.0.0.1:8080/restConnector/getBankAccountsBalances/bankIdAccountIds

     val urlInMethodRouting: Option[String] = MethodRoutingHolder.methodRouting match {
       case Full(routing) => routing.parameters.find(_.key == "url").map(_.value)
       case _ => None
     }

    // http://127.0.0.1:8080/restConnector/getBankAccountsBalances/bankIdAccountIds/dmo.02.de.de,60e65f3f-0743-41f5-9efd-3c6f0438aa42
    if(urlInMethodRouting.isDefined) {
      return urlInMethodRouting.get + parameterUrl
    }

    // convert any type value to string, to fill in the url
    def urlValueConverter(obj: Any):String = {
      val value = obj match {
        case null => ""
        case seq: Seq[_] => seq.map(_.toString.replaceFirst("^\\w+\\((.*)\\)$", "$1")).mkString(";")
        case seq: Array[_] => seq.map(_.toString.replaceFirst("^\\w+\\((.*)\\)$", "$1")).mkString(";")
        case other => other.toString
      }
      URLEncoder.encode(value, "UTF-8")
    }
    //build queryParams: List[OBPQueryParam] as query parameters
    val queryParams: Option[String] = variables.lastOption
      .filter(it => it._1 == "queryParams" && it._2.isInstanceOf[Seq[_]])
      .map(_._2.asInstanceOf[List[OBPQueryParam]])
      .map { queryParams =>
        val limit = OBPQueryParam.getLimit(queryParams)
        val offset = OBPQueryParam.getOffset(queryParams)
        val fromDate = OBPQueryParam.getFromDate(queryParams)
        val toDate = OBPQueryParam.getToDate(queryParams)
        s"?${OBPQueryParam.LIMIT}=${limit}&${OBPQueryParam.OFFSET}=${offset}&${OBPQueryParam.FROM_DATE}=${fromDate}&${OBPQueryParam.TO_DATE}=${toDate}"
      }
    variables.dropRight(queryParams.size)
      .foldLeft(s"$baseUrl/$methodName")((url, pair) => url.concat(s"/${pair._1}/${urlValueConverter(pair._2)}")) + queryParams.getOrElse("")
  }

  private[this] def sendRequest[T <: InBoundTrait[_]: TypeTag : Manifest](url: String, method: HttpMethod, outBound: TopicTrait, callContext: Option[CallContext]): Future[Box[T]] = {
    //transfer accountId to accountReference and customerId to customerReference in outBound
    this.convertToReference(outBound)
    val methodRouting = MethodRoutingHolder.methodRouting
    val inBoundMapping = methodRouting.flatMap(_.getInBoundMapping)
    val outBoundMapping = methodRouting.flatMap(_.getOutBoundMapping)

    val outBoundJson = outBoundMapping match {
      case Full(m) =>
        val source = decompose(outBound)
        val builtJson = JsonUtils.buildJson(source, m)
        compactRender(builtJson)
      case _ => net.liftweb.json.Serialization.write(outBound)
    }
    val request = prepareHttpRequest(url, method, HttpProtocol("HTTP/1.1"), outBoundJson).withHeaders(callContext)
    logger.debug(s"RestConnector_vMar2019 request is : $request")
    val responseFuture = makeHttpRequest(request)
    responseFuture.map {
      case HttpResponse(status, _, entity@_, _) => (status, entity)
    }.flatMap {
      case (status, entity) if status.isSuccess() => extractEntity[T](entity, inBoundMapping)
      case (status, _) if status.intValue == 404 =>
        Future {
          val errorMsg = s"$ResourceDoesNotExist the resource url is: $url"
          ParamFailure(errorMsg, APIFailureNewStyle(errorMsg, status.intValue()))
        }
      case (status, entity) => {
          val future: Future[Box[Box[T]]] = extractBody(entity) map { msg =>
            tryo {
            val errorMsg = parse(msg).extract[ErrorMessage]
            val failure: Box[T] = ParamFailure(errorMsg.message, APIFailureNewStyle(errorMsg.message, status.intValue()))
            failure
          } ~> APIFailureNewStyle(msg, status.intValue())
        }
        future.map{
          case Full(v) => v
          case e: EmptyBox => e
        }
      }
    }.map(convertToId(_)) recoverWith {
      //Can not catch the `StreamTcpException` here, so I used the contains to show the error.
      case e: Exception if (e.getMessage.contains(s"$httpRequestTimeout seconds")) => Future.failed(new Exception(s"$AdapterTimeOurError Please Check Adapter Side, the response should be returned to OBP-Side in $httpRequestTimeout seconds. Details: ${e.getMessage}", e))
      case e: Exception => Future.failed(new Exception(s"$AdapterUnknownError Please Check Adapter Side! Details: ${e.getMessage}", e))
    }
  }

  private[this] def extractBody(responseEntity: ResponseEntity): Future[String] = responseEntity.toStrict(2.seconds) flatMap { e =>
    e.dataBytes
      .runFold(ByteString.empty) { case (acc, b) => acc ++ b }
      .map(_.utf8String)
  }

  private[this] def extractEntity[T: TypeTag: Manifest](responseEntity: ResponseEntity, inBoundMapping: Box[JObject]): Future[Box[T]] = {
    this.extractBody(responseEntity)
      .map({
        case null => Empty
        case str => Connector.extractAdapterResponse[T](str, inBoundMapping)
      })
  }

  /**
    * interpolate url, bind variable
    * e.g: interpolateUrl("http://127.0.0.1:9093/:id/bank/:bank_id", Map("bank_id" -> "myId", "id"-> 123)):
    * result: http://127.0.0.1:9093/123/bank/myId
    *
    * @param urlTemplate url template
    * @param variables   key values
    * @return bind key and value url
    */
  def interpolateUrl(urlTemplate: String, variables: Map[String, Any]) = {
    variables.foldLeft(urlTemplate)((url, pair) => {
      val (key, value) = pair
      url
        // fill this format variables: http://rootpath/banks/{bank-id}
        .replace(s"{$key}", String.valueOf(value))
      // fill this format variables: http://rootpath/banks/:bank-id
      // url.replace(s":${key}", String.valueOf(value))
      // fill this format variables: http://rootpath/banks/:{bank-id}
      //.replaceAll(s":\\{\\s*$key\\s*\\}", String.valueOf(value))
    })
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
        (fieldName.equalsIgnoreCase("accountId") && fieldType =:= typeOf[String])||
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

object RestConnector_vMar2019 extends RestConnector_vMar2019
