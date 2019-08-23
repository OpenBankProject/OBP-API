package code.bankconnectors.rest

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

import java.net.URLEncoder
import java.util.UUID.randomUUID
import java.util.Date

import akka.http.scaladsl.model.{HttpProtocol, _}
import akka.http.scaladsl.model.headers.RawHeader
import akka.util.ByteString
import code.api.APIFailureNewStyle
import code.api.cache.Caching
import code.api.util.APIUtil.{AdapterImplementation, MessageDoc, OBPReturnType, saveConnectorMetric}
import code.api.util.ErrorMessages._
import code.api.util.{CallContext, NewStyle, OBPQueryParam}
import code.bankconnectors._
import code.bankconnectors.vJune2017.AuthInfo
import code.kafka.{KafkaHelper, Topics}
import code.util.AkkaHttpClient._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model._
import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
import net.liftweb.common.{Box, Empty, _}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import code.api.util.ExampleValue._
import code.api.util.APIUtil._
import code.methodrouting.MethodRoutingParam
import org.apache.commons.lang3.StringUtils
import net.liftweb.json._

trait RestConnector_vMar2019 extends Connector with KafkaHelper with MdcLoggable {
  //this one import is for implicit convert, don't delete
  import com.openbankproject.commons.model.{CustomerFaceImage, CreditLimit, CreditRating, AmountOfMoney}

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

  /*
    All the following code is created automatclly. 
    It will read the method from Connector.scala and generate the messageDoc and implemented the methods:
    Take the `def getBankFuture` for example: 
    messageDocs += MessageDoc(
    process = "obp.getBank",
    messageFormat = messageFormat,
    description = "Get Bank",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetBankFuture(outboundAdapterCallContext = OutboundAdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        consumerId = Option("string"),
        generalContext = Option(List(BasicGeneralContext(key = "string",
          value = "string"))),
        outboundAdapterAuthInfo = Option(OutboundAdapterAuthInfo(userId = Option("string"),
          username = Option("string"),
          linkedCustomers = Option(List(BasicLinkedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContext = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = BankId(value = "string"))
      ),
    exampleInboundMessage = (
      InBoundGetBankFuture(inboundAdapterCallContext = InboundAdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        generalContext = Option(List(BasicGeneralContext(key = "string",
          value = "string")))),
        data = BankCommons(bankId = BankId(value = "string"),
          shortName = "string",
          fullName = "string",
          logoUrl = "string",
          websiteUrl = "string",
          bankRoutingScheme = "string",
          bankRoutingAddress = "string",
          swiftBic = "string",
          nationalIdentifier = "string"))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getBankFuture/bankId/{bankId}
  override def getBankFuture(bankId: BankId, callContext: Option[CallContext]): Future[Box[(Bank, Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getBankFuture", ("bankId", bankId))
        sendGetRequest[InBoundGetBankFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
            }

          }
      }
    }
  }("getBankFuture")
   */
  






//---------------- dynamic start -------------------please don't modify this line
// ---------- create on Fri Aug 23 16:54:56 CEST 2019

messageDocs += MessageDoc(
    process = "obp.getAdapterInfo",
    messageFormat = messageFormat,
    description = "Get Adapter Info",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetAdapterInfo.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetAdapterInfo.getClass.getSimpleName).response),
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
    import net.liftweb.json.Serialization.write

    val url = getUrl("getAdapterInfo")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundGetAdapterInfo(outboundAdapterCallContext , ))
    sendPostRequest[InBoundGetAdapterInfo](url, callContext, jsonStr)
      .map{ boxedResult =>
      boxedResult.map { result =>
          (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
        }
    
    }
  }
    
messageDocs += MessageDoc(
    process = "obp.getChallengeThreshold",
    messageFormat = messageFormat,
    description = "Get Challenge Threshold",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetChallengeThreshold.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetChallengeThreshold.getClass.getSimpleName).response),
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
  // url example: /getChallengeThreshold
  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, userName: String, callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = {
    import net.liftweb.json.Serialization.write

    val url = getUrl("getChallengeThreshold")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundGetChallengeThreshold(outboundAdapterCallContext , bankId, accountId, viewId, transactionRequestType, currency, userId, userName))
    sendPostRequest[InBoundGetChallengeThreshold](url, callContext, jsonStr)
      .map{ boxedResult =>
      boxedResult match {
        case Full(result) => (Full(result.data), buildCallContext(result.inboundAdapterCallContext, callContext))
        case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
      }
    
    }
  }
    
messageDocs += MessageDoc(
    process = "obp.getChargeLevel",
    messageFormat = messageFormat,
    description = "Get Charge Level",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetChargeLevel.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetChargeLevel.getClass.getSimpleName).response),
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
  // url example: /getChargeLevel
  override def getChargeLevel(bankId: BankId, accountId: AccountId, viewId: ViewId, userId: String, userName: String, transactionRequestType: String, currency: String, callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = {
    import net.liftweb.json.Serialization.write

    val url = getUrl("getChargeLevel")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundGetChargeLevel(outboundAdapterCallContext , bankId, accountId, viewId, userId, userName, transactionRequestType, currency))
    sendPostRequest[InBoundGetChargeLevel](url, callContext, jsonStr)
      .map{ boxedResult =>
      boxedResult match {
        case Full(result) => (Full(result.data), buildCallContext(result.inboundAdapterCallContext, callContext))
        case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
      }
    
    }
  }
    
messageDocs += MessageDoc(
    process = "obp.createChallenge",
    messageFormat = messageFormat,
    description = "Create Challenge",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateChallenge.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateChallenge.getClass.getSimpleName).response),
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
      transactionRequestId="string")
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
    import net.liftweb.json.Serialization.write

    val url = getUrl("createChallenge")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundCreateChallenge(outboundAdapterCallContext , bankId, accountId, userId, transactionRequestType, transactionRequestId, scaMethod))
    sendPostRequest[InBoundCreateChallenge](url, callContext, jsonStr)
      .map{ boxedResult =>
      boxedResult match {
        case Full(result) => (Full(result.data), buildCallContext(result.inboundAdapterCallContext, callContext))
        case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
      }
    
    }
  }
    
messageDocs += MessageDoc(
    process = "obp.getBank",
    messageFormat = messageFormat,
    description = "Get Bank",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBank.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBank.getClass.getSimpleName).response),
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
    import net.liftweb.json.Serialization.write

    val url = getUrl("getBank")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundGetBank(outboundAdapterCallContext , bankId))
    sendPostRequest[InBoundGetBank](url, callContext, jsonStr)
      .map{ boxedResult =>
      boxedResult.map { result =>
          (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
        }
    
    }
  }
    
messageDocs += MessageDoc(
    process = "obp.getBanks",
    messageFormat = messageFormat,
    description = "Get Banks",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBanks.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBanks.getClass.getSimpleName).response),
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
    import net.liftweb.json.Serialization.write

    val url = getUrl("getBanks")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundGetBanks(outboundAdapterCallContext , ))
    sendPostRequest[InBoundGetBanks](url, callContext, jsonStr)
      .map{ boxedResult =>
      boxedResult.map { result =>
          (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
        }
    
    }
  }
    
messageDocs += MessageDoc(
    process = "obp.getBankAccountsForUser",
    messageFormat = messageFormat,
    description = "Get Bank Accounts For User",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBankAccountsForUser.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBankAccountsForUser.getClass.getSimpleName).response),
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
    import net.liftweb.json.Serialization.write

    val url = getUrl("getBankAccountsForUser")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundGetBankAccountsForUser(outboundAdapterCallContext , username))
    sendPostRequest[InBoundGetBankAccountsForUser](url, callContext, jsonStr)
      .map{ boxedResult =>
      boxedResult.map { result =>
          (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
        }
    
    }
  }
    
messageDocs += MessageDoc(
    process = "obp.getBankAccount",
    messageFormat = messageFormat,
    description = "Get Bank Account",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBankAccount.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBankAccount.getClass.getSimpleName).response),
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
  // url example: /getBankAccount
  override def getBankAccount(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
    import net.liftweb.json.Serialization.write

    val url = getUrl("getBankAccount")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundGetBankAccount(outboundAdapterCallContext , bankId, accountId))
    sendPostRequest[InBoundGetBankAccount](url, callContext, jsonStr)
      .map{ boxedResult =>
      boxedResult match {
        case Full(result) => (Full(result.data), buildCallContext(result.inboundAdapterCallContext, callContext))
        case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
      }
    
    }
  }
    
messageDocs += MessageDoc(
    process = "obp.getBankAccountsBalances",
    messageFormat = messageFormat,
    description = "Get Bank Accounts Balances",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBankAccountsBalances.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBankAccountsBalances.getClass.getSimpleName).response),
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
  override def getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[AccountsBalances]] = {
    import net.liftweb.json.Serialization.write

    val url = getUrl("getBankAccountsBalances")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundGetBankAccountsBalances(outboundAdapterCallContext , bankIdAccountIds))
    sendPostRequest[InBoundGetBankAccountsBalances](url, callContext, jsonStr)
      .map{ boxedResult =>
      boxedResult match {
        case Full(result) => (Full(result.data), buildCallContext(result.inboundAdapterCallContext, callContext))
        case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
      }
    
    }
  }
    
messageDocs += MessageDoc(
    process = "obp.getCoreBankAccounts",
    messageFormat = messageFormat,
    description = "Get Core Bank Accounts",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetCoreBankAccounts.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetCoreBankAccounts.getClass.getSimpleName).response),
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
    import net.liftweb.json.Serialization.write

    val url = getUrl("getCoreBankAccounts")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundGetCoreBankAccounts(outboundAdapterCallContext , bankIdAccountIds))
    sendPostRequest[InBoundGetCoreBankAccounts](url, callContext, jsonStr)
      .map{ boxedResult =>
      boxedResult.map { result =>
          (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
        }
    
    }
  }
    
messageDocs += MessageDoc(
    process = "obp.getBankAccountsHeld",
    messageFormat = messageFormat,
    description = "Get Bank Accounts Held",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBankAccountsHeld.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBankAccountsHeld.getClass.getSimpleName).response),
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
  // url example: /getBankAccountsHeld
  override def getBankAccountsHeld(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[List[AccountHeld]]] = {
    import net.liftweb.json.Serialization.write

    val url = getUrl("getBankAccountsHeld")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundGetBankAccountsHeld(outboundAdapterCallContext , bankIdAccountIds))
    sendPostRequest[InBoundGetBankAccountsHeld](url, callContext, jsonStr)
      .map{ boxedResult =>
      boxedResult match {
        case Full(result) => (Full(result.data), buildCallContext(result.inboundAdapterCallContext, callContext))
        case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
      }
    
    }
  }
    
messageDocs += MessageDoc(
    process = "obp.checkBankAccountExists",
    messageFormat = messageFormat,
    description = "Check Bank Account Exists",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCheckBankAccountExists.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCheckBankAccountExists.getClass.getSimpleName).response),
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
  // url example: /checkBankAccountExists
  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
    import net.liftweb.json.Serialization.write

    val url = getUrl("checkBankAccountExists")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundCheckBankAccountExists(outboundAdapterCallContext , bankId, accountId))
    sendPostRequest[InBoundCheckBankAccountExists](url, callContext, jsonStr)
      .map{ boxedResult =>
      boxedResult match {
        case Full(result) => (Full(result.data), buildCallContext(result.inboundAdapterCallContext, callContext))
        case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
      }
    
    }
  }
    
messageDocs += MessageDoc(
    process = "obp.getCounterpartyTrait",
    messageFormat = messageFormat,
    description = "Get Counterparty Trait",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetCounterpartyTrait.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetCounterpartyTrait.getClass.getSimpleName).response),
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
  // url example: /getCounterpartyTrait
  override def getCounterpartyTrait(bankId: BankId, accountId: AccountId, couterpartyId: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
    import net.liftweb.json.Serialization.write

    val url = getUrl("getCounterpartyTrait")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundGetCounterpartyTrait(outboundAdapterCallContext , bankId, accountId, couterpartyId))
    sendPostRequest[InBoundGetCounterpartyTrait](url, callContext, jsonStr)
      .map{ boxedResult =>
      boxedResult match {
        case Full(result) => (Full(result.data), buildCallContext(result.inboundAdapterCallContext, callContext))
        case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
      }
    
    }
  }
    
//---------------- dynamic end ---------------------please don't modify this line
    
    
    
    
    
    
    
    
    
    


  private[this] def sendGetRequest[T: TypeTag : Manifest](url: String, callContext: Option[CallContext]) =
    sendRequest[T](url, callContext, HttpMethods.GET)

  private[this] def sendPostRequest[T: TypeTag : Manifest](url: String, callContext: Option[CallContext], entityJsonString: String) =
    sendRequest[T](url, callContext, HttpMethods.POST)

  private[this] def sendPutRequest[T: TypeTag : Manifest](url: String, callContext: Option[CallContext], entityJsonString: String) =
    sendRequest[T](url, callContext, HttpMethods.PUT)

  private[this] def sendDelteRequest[T: TypeTag : Manifest](url: String, callContext: Option[CallContext]) =
    sendRequest[T](url, callContext, HttpMethods.DELETE)

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
    * @return
    */
  private[this] implicit def convertFuture[T](future: Future[T]): T = Await.result(future, 1.minute)

  //TODO please modify this baseUrl to your remote api server base url of this connector
  private[this] val baseUrl = "http://localhost:8080/restConnector"

  private[this] def getUrl(callContext: Option[CallContext], methodName: String, variables: (String, Any)*): String = {
    // rest connector can have url value in the parameters, key is url
     
    //Temporary solution:
    val basicUserAuthContext: List[BasicUserAuthContext] = callContext.map(_.toOutboundAdapterCallContext.outboundAdapterAuthInfo.map(_.userAuthContext)).flatten.flatten.getOrElse(List.empty[BasicUserAuthContext])
    val bankId = basicUserAuthContext.find(_.key=="bank-id").map(_.value)
    val accountId = basicUserAuthContext.find(_.key=="account-id").map(_.value)
    val parameterUrl = if (bankId.isDefined &&accountId.isDefined)  s"/${bankId.get},${accountId.get}" else ""
    
     //http://127.0.0.1:8080/restConnector/getBankAccountsBalances/bankIdAccountIds
     val urlInMethodRouting = NewStyle.function.getMethodRoutings(Some(methodName))
       .flatMap(_.parameters)
       .find(_.key == "url")
       .map(_.value)

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

  private[this] def sendRequest[T: TypeTag : Manifest](url: String, callContext: Option[CallContext], method: HttpMethod, entityJsonString: String = ""): Future[Box[T]] = {
    val request = prepareHttpRequest(url, method, HttpProtocol("HTTP/1.1"), entityJsonString).withHeaders(callContext)
    logger.debug(s"RestConnector_vMar2019 request is : $request")
    val responseFuture = makeHttpRequest(request)
    val jsonType = typeOf[T]
    responseFuture.map {
      case response@HttpResponse(status, _, entity@_, _) => (status, entity)
    }.flatMap {
      case (status, entity) if status.isSuccess() => extractEntity[T](entity, callContext)
      case (status, entity) => extractBody(entity) map { msg => {
        Empty ~> APIFailureNewStyle(msg, status.intValue(), callContext.map(_.toLight))
      }
      }
    }
  }

  private[this] def extractBody(responseEntity: ResponseEntity): Future[String] = responseEntity.toStrict(2.seconds) flatMap { e =>
    e.dataBytes
      .runFold(ByteString.empty) { case (acc, b) => acc ++ b }
      .map(_.utf8String)
  }

  private[this] def extractEntity[T: Manifest](responseEntity: ResponseEntity, callContext: Option[CallContext], failCode: Int = 400): Future[Box[T]] = {
    this.extractBody(responseEntity)
      .map({
        case null => Empty
        case str => tryo {
          parse(str).extract[T]
        } ~> APIFailureNewStyle(s"$InvalidJsonFormat The Json body should be the ${manifest[T]} ", failCode, callContext.map(_.toLight))
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

  //TODO hongwei confirm the third valu: OutboundAdapterCallContext#adapterAuthInfo
  private[this] def buildCallContext(inboundAdapterCallContext: InboundAdapterCallContext, callContext: Option[CallContext]): Option[CallContext] =
    for (cc <- callContext)
      yield cc.copy(correlationId = inboundAdapterCallContext.correlationId, sessionId = inboundAdapterCallContext.sessionId)

  private[this] def buildCallContext(boxedInboundAdapterCallContext: Box[InboundAdapterCallContext], callContext: Option[CallContext]): Option[CallContext] = boxedInboundAdapterCallContext match {
    case Full(inboundAdapterCallContext) => buildCallContext(inboundAdapterCallContext, callContext)
    case _ => callContext
  }
}


object RestConnector_vMar2019 extends RestConnector_vMar2019 {

}
