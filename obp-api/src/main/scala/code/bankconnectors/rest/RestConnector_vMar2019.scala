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
import akka.util.ByteString
import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions.inboundStatus
import code.api.cache.Caching
import code.api.util.APIUtil.{AdapterImplementation, MessageDoc, OBPReturnType, saveConnectorMetric}
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue._
import code.api.util.{CallContext, OBPQueryParam}
import code.bankconnectors._
import code.bankconnectors.vJune2017.AuthInfo
import code.kafka.{KafkaHelper, Topics}
import code.util.AkkaHttpClient._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model._
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, _}
import net.liftweb.json._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.runtime.universe._

import code.api.util.ExampleValue._

import code.api.util.APIUtil._

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
// ---------- create on Thu Jun 13 11:07:28 CST 2019

messageDocs += MessageDoc(
    process = "obp.createCustomer",
    messageFormat = messageFormat,
    description = "Create Customer",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateCustomer.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateCustomer.getClass.getSimpleName).response),
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
    import net.liftweb.json.Serialization.write

    val url = getUrl("createCustomer")
    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
    val jsonStr = write(OutBoundCreateCustomer(outboundAdapterCallContext , bankId, legalName, mobileNumber, email, faceImage, dateOfBirth, relationshipStatus, dependents, dobOfDependents, highestEducationAttained, employmentStatus, kycStatus, lastOkDate, creditRating, creditLimit, title, branchId, nameSuffix))
    sendPostRequest[InBoundCreateCustomer](url, callContext, jsonStr)
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

  //TODO every connector should implement this method to build authorization headers with callContext
  private[this] implicit def buildHeaders(callContext: Option[CallContext]): List[HttpHeader] = Nil

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

  private[this] def getUrl(methodName: String, variables: (String, Any)*): String = {
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
