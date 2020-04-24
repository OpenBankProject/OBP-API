/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api

import java.net.URLDecoder

import code.api.Constant._
import code.api.OAuthHandshake._
import code.api.builder.AccountInformationServiceAISApi.APIMethods_AccountInformationServiceAISApi
import code.api.util.APIUtil._
import code.api.util.ErrorMessages.attemptedToOpenAnEmptyBox
import code.api.util._
import code.api.v3_0_0.APIMethods300
import code.api.v3_1_0.APIMethods310
import code.api.v4_0_0.APIMethods400
import code.model.dataAccess.AuthUser
import code.util.Helper.MdcLoggable
import com.alibaba.ttl.TransmittableThreadLocal
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.{ApiVersion, ReflectUtils, ScannedApiVersion}
import net.liftweb.common._
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, LiftResponse, Req, S}
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers

import scala.collection.immutable.List
import scala.collection.mutable.ArrayBuffer
import scala.math.Ordering

trait APIFailure{
  val msg : String
  val responseCode : Int
}

object APIFailure {
  def apply(message : String, httpResponseCode : Int) : APIFailure = new APIFailure{
    val msg = message
    val responseCode = httpResponseCode
  }

  def unapply(arg: APIFailure): Option[(String, Int)] = Some(arg.msg, arg.responseCode)
}

case class APIFailureNewStyle(failMsg: String,
                              failCode: Int = 400,
                              ccl: Option[CallContextLight] = None
                             )

//if you change this, think about backwards compatibility! All existing
//versions of the API return this failure message, so if you change it, make sure
//that all stable versions retain the same behavior
case class UserNotFound(providerId : String, userId: String) extends APIFailure {
  val responseCode = 400 //TODO: better as 404? -> would break some backwards compatibility (or at least the tests!)

  //to reiterate the comment about preserving backwards compatibility:
  //consider the case that an app may be parsing this string to decide what message to show their users
  //e.g. when granting view permissions, an app may not give their users a choice of provider and only
  //allow them to grant permissions to users from a certain hardcoded provider. In this case, showing this error
  //message is undesired and confusing. So in fact that app may be doing some regex stuff to try to match the string below
  //so that they can provide a useful message to their users. Obviously in the future this should be redesigned in a better
  //way, perhaps by using error codes.
  val msg = s"user $userId not found at provider $providerId"
}

object ApiVersionHolder {
  private val threadLocal: ThreadLocal[ApiVersion] = new TransmittableThreadLocal()

  def setApiVersion(apiVersion: ApiVersion) = threadLocal.set(apiVersion)

  def getApiVersion = threadLocal.get()

  /**
   * remove apiVersion from threadLocal, and return removed value
   * @return be removed apiVersion
   */
  def removeApiVersion(): ApiVersion = {
    val apiVersion = threadLocal.get()
    threadLocal.remove()
    apiVersion
  }
}

trait OBPRestHelper extends RestHelper with MdcLoggable {

  implicit def errorToJson(error: ErrorMessage): JValue = Extraction.decompose(error)

  val version : ApiVersion
  val versionStatus : String // TODO this should be property of ApiVersion
  //def vDottedVersion = vDottedApiVersion(version)

  def apiPrefix: OBPEndpoint => OBPEndpoint = version match {
    case ScannedApiVersion(urlPrefix, _, _) =>
      (urlPrefix / version.vDottedApiVersion).oPrefix(_)
    case _ =>
    (ApiPathZero / version.vDottedApiVersion).oPrefix(_)
  }

  /*
  An implicit function to convert magically between a Boxed JsonResponse and a JsonResponse
  If we have something good, return it. Else log and return an error.
  Please note that behaviour of this function depends on property display_internal_errors=true/false in case of Failure
  # When is disabled we show only last message which should be a user friendly one. For instance:
  # {
  #   "error": "OBP-30001: Bank not found. Please specify a valid value for BANK_ID."
  # }
  # When is disabled we also do filtering. Every message which does not contain "OBP-" is considered as internal and as that is not shown.
  # In case the filtering implies an empty response we provide a generic one:
  # {
  #   "error": "OBP-50005: An unspecified or internal error occurred."
  # }
  # When is enabled we show all messages in a chain. For instance:
  # {
  #   "error": "OBP-30001: Bank not found. Please specify a valid value for BANK_ID. <- Full(Kafka_TimeoutExceptionjava.util.concurrent.TimeoutException: The stream has not been completed in 1550 milliseconds.)"
  # }
  */
  implicit def jsonResponseBoxToJsonResponse(box: Box[JsonResponse]): JsonResponse = {
    box match {
      case Full(r) => r
      case ParamFailure(_, _, _, apiFailure : APIFailure) => {
        logger.error("jsonResponseBoxToJsonResponse case ParamFailure says: API Failure: " + apiFailure.msg + " ($apiFailure.responseCode)")
        errorJsonResponse(apiFailure.msg, apiFailure.responseCode)
      }
      case obj@Failure(_, _, _) => {
        val failuresMsg = filterMessage(obj)
        logger.debug("jsonResponseBoxToJsonResponse case Failure API Failure: " + failuresMsg)
        errorJsonResponse(failuresMsg)
      }
      case Empty => {
        logger.error(s"jsonResponseBoxToJsonResponse case Empty : ${ErrorMessages.ScalaEmptyBoxToLiftweb}")
        errorJsonResponse(ErrorMessages.ScalaEmptyBoxToLiftweb)
      }
      case _ => {
        logger.error("jsonResponseBoxToJsonResponse case Unknown !")
        errorJsonResponse(ErrorMessages.UnknownError)
      }
    }
  }

  /*
  A method which takes
    a Request r
    and
    a partial function h
      which takes
      a Request
      and
      a User
      and returns a JsonResponse
    and returns a JsonResponse (but what about the User?)


   */
  def failIfBadJSON(r: Req, h: (OBPEndpoint)): CallContext => Box[JsonResponse] = {
    // Check if the content-type is text/json or application/json
    r.json_? match {
      case true =>
        //logger.debug("failIfBadJSON says: Cool, content-type is json")
        r.json match {
          case Failure(msg, _, _) => (x: CallContext) => Full(errorJsonResponse(ErrorMessages.InvalidJsonFormat + s"$msg"))
          case _ => h(r)
        }
      case false => h(r)
    }
  }


  /**
    * Function which inspect does an Endpoint use Akka's Future in non-blocking way i.e. without using Await.result
    * @param rd Resource Document which contains all description of an Endpoint
    * @return true if some endpoint can get User from Authorization Header
    */
  def newStyleEndpoints(rd: Option[ResourceDoc]) : Boolean = {
    rd match {
      // Versions that precede the 3.0.0 are mostly written as Old Style endpoint.
      // In this case we assume all are written as Old Style and explicitly list NewStyle endpoints.
      case Some(e) if NewStyle.endpoints.exists(_ == (e.partialFunctionName, e.implementedInApiVersion.toString())) =>
        true
      // Since the 3.0.0 we assume that endpoints are written in New Style.
      // In this case we list all endpoints as New Style and explicitly exclude Old ones.
      case Some(e) if APIMethods300.newStyleEndpoints.exists {
        (_ == (e.partialFunctionName, e.implementedInApiVersion.toString()))
      } =>
        true
      // Since the 3.0.0 we assume that endpoints are written in New Style.
      // In this case we list all endpoints as New Style and explicitly exclude Old ones.
      case Some(e) if APIMethods310.newStyleEndpoints.exists {
        (_ == (e.partialFunctionName, e.implementedInApiVersion.toString()))
      } =>
        true
      // Since the 3.0.0 we assume that endpoints are written in New Style.
      // In this case we list all endpoints as New Style and explicitly exclude Old ones.
      case Some(e) if APIMethods400.newStyleEndpoints.exists {
        (_ == (e.partialFunctionName, e.implementedInApiVersion.toString()))
      } =>
        true
      // Berlin Group endpoints are written in New Style
      case Some(e) if APIMethods_AccountInformationServiceAISApi.newStyleEndpoints.exists {
        (_ == (e.partialFunctionName, e.implementedInApiVersion.toString()))
      } =>
        true
      case Some(e) if List(
        ApiVersion.v1_2_1.toString, 
        ApiVersion.v1_3_0.toString, 
        ApiVersion.v1_4_0.toString, 
        ApiVersion.v2_0_0.toString, 
        ApiVersion.v2_1_0.toString, 
        ApiVersion.v2_2_0.toString,
        ApiVersion.apiBuilder.toString, //apiBuilder is the old style.
      ).exists(_ == e.implementedInApiVersion.toString()) =>
        false
      case Some(e) if APIMethods300.oldStyleEndpoints.exists(_ == e.partialFunctionName) =>
        false
      case None => //added the None resource doc endpoint is the false
        false
      case _ =>
        true
    }
  }

  def failIfBadAuthorizationHeader(rd: Option[ResourceDoc])(fn: CallContext => Box[JsonResponse]) : JsonResponse = {
    val authorization = S.request.map(_.header("Authorization")).flatten
    val body: Box[String] = getRequestBody(S.request)
    val implementedInVersion = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).view
    val verb = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).requestType.method
    val url = URLDecoder.decode(S.uriAndQueryString.getOrElse(""),"UTF-8")
    val correlationId = getCorrelationId()
    val reqHeaders = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).request.headers
    val remoteIpAddress = getRemoteIpAddress()
    val cc = CallContext(
      resourceDocument = rd,
      startTime = Some(Helpers.now),
      authReqHeaderField = authorization,
      implementedInVersion = implementedInVersion,
      verb = verb,
      httpBody = body,
      correlationId = correlationId,
      url = url,
      ipAddress = remoteIpAddress,
      requestHeaders = reqHeaders
    )
    if(newStyleEndpoints(rd)) {
      fn(cc)
    } else if (APIUtil.hasConsentId(reqHeaders)) {
      val (usr, callContext) =  Consent.applyRulesOldStyle(APIUtil.getConsentId(reqHeaders), cc)
      usr match {
        case Full(u) => fn(callContext.copy(user = Full(u))) // Authentication is successful
        case ParamFailure(a, b, c, apiFailure : APIFailure) => ParamFailure(a, b, c, apiFailure : APIFailure)
        case Failure(msg, t, c) => Failure(msg, t, c)
        case _ => Failure("Consent error")
      }
    } else if (hasAnOAuthHeader(authorization)) {
      val (usr, callContext) = getUserAndCallContext(cc)
      usr match {
        case Full(u) => fn(callContext.copy(user = Full(u))) // Authentication is successful
        case ParamFailure(a, b, c, apiFailure : APIFailure) => ParamFailure(a, b, c, apiFailure : APIFailure)
        case Failure(msg, t, c) => Failure(msg, t, c)
        case _ => Failure("oauth error")
      }
    } else if (hasAnOAuth2Header(authorization)) {
      val (user, callContext) = OAuth2Login.getUser(cc)
      user match {
        case Full(u) =>
          AuthUser.updateUserAccountViews(u, callContext)
          fn(cc.copy(user = Full(u))) // Authentication is successful
        case ParamFailure(a, b, c, apiFailure : APIFailure) => ParamFailure(a, b, c, apiFailure : APIFailure)
        case Failure(msg, t, c) => Failure(msg, t, c)
        case _ => Failure("oauth error")
      }
    } else if (APIUtil.getPropsAsBoolValue("allow_direct_login", true) && hasDirectLoginHeader(authorization)) {
      DirectLogin.getUser match {
        case Full(u) => {
          val consumer = DirectLogin.getConsumer
          fn(cc.copy(user = Full(u), consumer=consumer))
        }// Authentication is successful
        case _ => {
          var (httpCode, message, directLoginParameters) = DirectLogin.validator("protectedResource")
          Full(errorJsonResponse(message, httpCode))
        }
      }
    } else if (APIUtil.getPropsAsBoolValue("allow_gateway_login", false) && hasGatewayHeader(authorization)) {
      logger.info("allow_gateway_login-getRemoteIpAddress: " + remoteIpAddress )
      APIUtil.getPropsValue("gateway.host") match {
        case Full(h) if h.split(",").toList.exists(_.equalsIgnoreCase(remoteIpAddress) == true) => // Only addresses from white list can use this feature
          val s = S
          val (httpCode, message, parameters) = GatewayLogin.validator(s.request)
          httpCode match {
            case 200 =>
              val payload = GatewayLogin.parseJwt(parameters)
              payload match {
                case Full(payload) =>
                  val s = S
                  GatewayLogin.getOrCreateResourceUser(payload: String, Some(cc)) match {
                    case Full((u, cbsToken, callContextNew)) => // Authentication is successful
                      val consumer = GatewayLogin.getOrCreateConsumer(payload, u)
                      setGatewayResponseHeader(s) {GatewayLogin.createJwt(payload, cbsToken)}
                      val jwt = GatewayLogin.createJwt(payload, cbsToken)
                      val callContextUpdated = ApiSession.updateCallContext(GatewayLoginResponseHeader(Some(jwt)), callContextNew)
                      fn(callContextUpdated.map( callContext =>callContext.copy(user = Full(u), consumer = consumer)).getOrElse(callContextNew.getOrElse(cc).copy(user = Full(u), consumer = consumer)))
                    case Failure(msg, t, c) => Failure(msg, t, c)
                    case _ => Full(errorJsonResponse(payload, httpCode))
                  }
                case Failure(msg, t, c) =>
                  Failure(msg, t, c)
                case _ =>
                  Failure(ErrorMessages.GatewayLoginUnknownError)
              }
            case _ =>
              Failure(message)
          }
        case Full(h) if h.split(",").toList.exists(_.equalsIgnoreCase(remoteIpAddress) == false) => // All other addresses will be rejected
          Failure(ErrorMessages.GatewayLoginWhiteListAddresses)
        case Empty =>
          Failure(ErrorMessages.GatewayLoginHostPropertyMissing) // There is no gateway.host in props file
        case Failure(msg, t, c) =>
          Failure(msg, t, c)
        case _ =>
          Failure(ErrorMessages.GatewayLoginUnknownError)
      }
    } else {
      fn(cc)
    }
  }

  class RichStringList(list: List[String]) {
    val listLen = list.length

    /**
      * Normally we would use ListServeMagic's prefix function, but it works with PartialFunction[Req, () => Box[LiftResponse]]
      * instead of the PartialFunction[Req, Box[User] => Box[JsonResponse]] that we need. This function does the same thing, really.
      */
    def oPrefix(pf: OBPEndpoint): OBPEndpoint =
      new OBPEndpoint {
        def isDefinedAt(req: Req): Boolean =
          req.path.partPath.startsWith(list) && {
            pf.isDefinedAt(req.withNewPath(req.path.drop(listLen)))
          }

        def apply(req: Req): CallContext => Box[JsonResponse] = {
          val function: CallContext => Box[JsonResponse] = pf.apply(req.withNewPath(req.path.drop(listLen)))

          callContext: CallContext => {
            // set endpoint apiVersion
            ApiVersionHolder.setApiVersion(version)
            val value = function(callContext)
            ApiVersionHolder.removeApiVersion()
            value
          }
        }
      }
  }

  //Give all lists of strings in OBPRestHelpers the oPrefix method
  implicit def stringListToRichStringList(list : List[String]) : RichStringList = new RichStringList(list)

  /*
  oauthServe wraps many get calls and probably all calls that post (and put and delete) json data.
  Since the URL path matching will fail if there is invalid JsonPost, and this leads to a generic 404 response which is confusing to the developer,
  we want to detect invalid json *before* matching on the url so we can fail with a more specific message.
  See SandboxApiCalls for an example of JsonPost being used.
  The down side is that we might be validating json more than once per request and we're doing work before authentication is completed
  (possible DOS vector?)

  TODO: should this be moved to def serve() further down?
   */

  def oauthServe(handler: PartialFunction[Req, CallContext => Box[JsonResponse]], rd: Option[ResourceDoc] = None): Unit = {
    val obpHandler : PartialFunction[Req, () => Box[LiftResponse]] = {
      new PartialFunction[Req, () => Box[LiftResponse]] {
        def apply(r : Req): () => Box[LiftResponse] = {
          //check (in that order):
          //if request is correct json
          //if request matches PartialFunction cases for each defined url
          //if request has correct oauth headers
          val startTime = Helpers.now
          val response = failIfBadAuthorizationHeader(rd) {
                          failIfBadJSON(r, handler)
                        }
          val endTime = Helpers.now
          logAPICall(startTime, endTime.getTime - startTime.getTime, rd)
          response
        }
        def isDefinedAt(r : Req) = {
          //if the content-type is json and json parsing failed, simply accept call but then fail in apply() before
          //the url cases don't match because json failed
          r.json_? match {
            case true =>
              //Try to evaluate the json
              r.json match {
                case Failure(msg, _, _) => true
                case _ => handler.isDefinedAt(r)
              }
            case false => handler.isDefinedAt(r)
          }
        }
      }
    }
    serve(obpHandler)
  }

  override protected def serve(handler: PartialFunction[Req, () => Box[LiftResponse]]) : Unit = {
    val obpHandler : PartialFunction[Req, () => Box[LiftResponse]] = {
      new PartialFunction[Req, () => Box[LiftResponse]] {
        def apply(r : Req) = {
          //Wraps the partial function with some logging
          handler(r)
        }
        def isDefinedAt(r : Req) = handler.isDefinedAt(r)
      }
    }
    super.serve(obpHandler)
  }

  /**
   * collect endpoints from APIMethodsxxx type
   * @param obj APIMethodsxxx instance
   * @return all collect endpoints
   */
  protected def getEndpoints(obj: AnyRef): Set[OBPEndpoint] = {
    ReflectUtils.getFieldsNameToValue[OBPEndpoint](obj)
      .values
      .toSet
  }

  /**
   * collect ResourceDoc objects
   * Note: if new version ResourceDoc's endpoint have the same 'requestUrl' and 'requestVerb' with old version, old version ResourceDoc will be omitted
   * @param allResourceDocs all ResourceDoc objects
   * @return collected ResourceDoc objects those omit duplicated old version ResourceDoc objects.
   */
  protected def collectResourceDocs(allResourceDocs: ArrayBuffer[ResourceDoc]*): ArrayBuffer[ResourceDoc] = {
    //descending sort by ApiVersion
    implicit val ordering = new Ordering[ScannedApiVersion] {
      override def compare(x: ScannedApiVersion, y: ScannedApiVersion): Int = y.toString().compareTo(x.toString())
    }
    val docsToOnceToSeq: Seq[ResourceDoc] = allResourceDocs.flatten
      .sortBy(_.implementedInApiVersion)

    val result = ArrayBuffer[ResourceDoc]()
    val urlAndMethods = scala.collection.mutable.Set[(String, String)]()
    for (doc <- docsToOnceToSeq) {
      val urlAndMethod = (doc.requestUrl, doc.requestVerb)
      if(!urlAndMethods.contains(urlAndMethod)) {
        urlAndMethods.add(urlAndMethod)
        result += doc
      }
    }
    result
  }

  protected def findResourceDoc(pf: OBPEndpoint, allResourceDocs: ArrayBuffer[ResourceDoc]): Option[ResourceDoc] = {
    allResourceDocs.find(_.partialFunction == pf)
  }

  protected def registerRoutes(routes: List[OBPEndpoint],
                               allResourceDocs: ArrayBuffer[ResourceDoc],
                               apiPrefix:OBPEndpoint => OBPEndpoint,
                               autoValidateAll: Boolean = false): Unit = {
    routes.foreach(route => {
      val maybeResourceDoc = findResourceDoc(route, allResourceDocs)
      val isAutoValidate = maybeResourceDoc.map{ doc =>
        doc.isValidateEnabled || (autoValidateAll && !doc.isValidateDisabled && doc.implementedInApiVersion == version)
      }.getOrElse(false)

      // if rd contains ResourceDoc, when autoValidateAll or doc isAutoValidate, just wrapped to auth check endpoint
      val authCheckRoute = (maybeResourceDoc, isAutoValidate) match {
        case (Some(doc), true) => doc.wrappedWithAuthCheck(route)
        case _ => route
      }
      oauthServe(apiPrefix(authCheckRoute), maybeResourceDoc)
    })
  }
}