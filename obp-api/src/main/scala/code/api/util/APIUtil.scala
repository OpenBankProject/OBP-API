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

package code.api.util

import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.Charset
import java.text.{ParsePosition, SimpleDateFormat}
import java.util.{Date, UUID}

import code.accountholders.AccountHolders
import code.api.Constant._
import code.api.OAuthHandshake._
import code.api.builder.OBP_APIBuilder
import code.api.oauth1a.Arithmetics
import code.api.oauth1a.OauthParams._
import code.api.sandbox.SandboxApiCalls
import code.api.util.ApiTag.{ResourceDocTag, apiTagBank, apiTagNewStyle}
import code.api.util.Glossary.GlossaryItem
import code.api.util.RateLimitingJson.CallLimit
import code.api.v1_2.ErrorMessage
import code.api.{DirectLogin, _}
import code.bankconnectors.Connector
import code.consumer.Consumers
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.metrics._
import code.model._
import code.model.dataAccess.AuthUser
import code.ratelimiting.{RateLimiting, RateLimitingDI}
import code.sanitycheck.SanityCheck
import code.scope.Scope
import code.usercustomerlinks.UserCustomerLink
import code.util.Helper
import code.util.Helper.{MdcLoggable, SILENCE_IS_GOLDEN}
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.{PemCertificateRole, StrongCustomerAuthentication}
import com.openbankproject.commons.model.{Customer, _}
import dispatch.url
import net.liftweb.actor.LAFuture
import net.liftweb.common.{Empty, _}
import net.liftweb.http._
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.provider.HTTPParam
import net.liftweb.http.rest.RestContinuation
import net.liftweb.json
import net.liftweb.json.JsonAST.{JField, JValue}
import net.liftweb.json.JsonParser.ParseException
import net.liftweb.json._
import net.liftweb.util.Helpers._
import net.liftweb.util.{Helpers, LiftFlowOfControlException, Props, StringHelpers, ThreadGlobal}

import scala.collection.JavaConverters._
import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.{ApiVersion, ReflectUtils, ScannedApiVersion}
import com.openbankproject.commons.util.Functions.Implicits._
import org.apache.commons.lang3.StringUtils

import scala.concurrent.Future
import scala.io.BufferedSource
import scala.xml.{Elem, XML}

object APIUtil extends MdcLoggable with CustomJsonFormats{

  val DateWithDay = "yyyy-MM-dd"
  val DateWithDay2 = "yyyyMMdd"
  val DateWithDay3 = "dd/MM/yyyy"
  val DateWithMinutes = "yyyy-MM-dd'T'HH:mm'Z'"
  val DateWithSeconds = "yyyy-MM-dd'T'HH:mm:ss'Z'"
  val DateWithMs = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
  val DateWithMsRollback = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
  
  val DateWithDayFormat = new SimpleDateFormat(DateWithDay)
  val DateWithSecondsFormat = new SimpleDateFormat(DateWithSeconds)
  val DateWithMsFormat = new SimpleDateFormat(DateWithMs)
  val DateWithMsRollbackFormat = new SimpleDateFormat(DateWithMsRollback)
  
  
  val DateWithDayExampleString: String = "2017-09-19"
  val DateWithSecondsExampleString: String = "2017-09-19T02:31:05Z"
  val DateWithMsExampleString: String = "2017-09-19T02:31:05.000Z"
  val DateWithMsRollbackExampleString: String = "2017-09-19T02:31:05.000+0000"
  
  val DateWithMsForFilteringFromDateString: String = "0000-00-00T00:00:00.000Z"
  val DateWithMsForFilteringEenDateString: String = "3049-01-01T00:00:00.000Z"
  
  
  // Use a fixed date far into the future (rather than current date/time so that cache keys are more static)
  // (Else caching is invalidated by constantly changing date)
  
  val DateWithDayExampleObject = DateWithDayFormat.parse(DateWithDayExampleString)
  val DateWithSecondsExampleObject = DateWithDayFormat.parse(DateWithSecondsExampleString)
  val DateWithMsExampleObject = DateWithDayFormat.parse(DateWithMsExampleString)
  val DateWithMsRollbackExampleObject = DateWithDayFormat.parse(DateWithMsRollbackExampleString)
  
  val DefaultFromDate = DateWithMsFormat.parse(DateWithMsForFilteringFromDateString)
  val DefaultToDate = DateWithMsFormat.parse(DateWithMsForFilteringEenDateString)

  implicit def errorToJson(error: ErrorMessage): JValue = Extraction.decompose(error)
  val headers = ("Access-Control-Allow-Origin","*") :: Nil
  val defaultJValue = Extraction.decompose(EmptyClassJson())
  val emptyObjectJson = EmptyClassJson()
  
  lazy val initPasswd = try {System.getenv("UNLOCK")} catch {case  _:Throwable => ""}
  import code.api.util.ErrorMessages._

  def httpMethod : String =
    S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }

  def hasDirectLoginHeader(authorization: Box[String]): Boolean = hasHeader("DirectLogin", authorization)

  def hasAnOAuthHeader(authorization: Box[String]): Boolean = hasHeader("OAuth", authorization)

  /*
     The OAuth 2.0 Authorization Framework: Bearer Token
     For example, the "bearer" token type defined in [RFC6750] is utilized
     by simply including the access token string in the request:
       GET /resource/1 HTTP/1.1
       Host: example.com
       Authorization: Bearer mF_9.B5f-4.1JqM
   */
  def hasAnOAuth2Header(authorization: Box[String]): Boolean = hasHeader("Bearer", authorization)

  def hasGatewayHeader(authorization: Box[String]) = hasHeader("GatewayLogin", authorization)

  /**
    * Helper function which tells us does an "Authorization" request header field has the Type of an authentication scheme
    * @param `type` Type of an authentication scheme
    * @param authorization "Authorization" request header field defined by HTTP/1.1 [RFC2617]
    * @return True or False i.e. does the "Authorization" request header field has the Type of the authentication scheme
    */
  def hasHeader(`type`: String, authorization: Box[String]) : Boolean = {
    authorization match {
      case Full(a) if a.contains(`type`) => true
      case _ => false
    }
  }

  /**
    * Purpose of this helper function is to get the Consent-Id value from a Request Headers.
    * @return the Consent-Id value from a Request Header as a String
    */
  def getConsentId(requestHeaders: List[HTTPParam]): Option[String] = {
    requestHeaders.toSet.filter(_.name == RequestHeader.`Consent-Id`).toList match {
      case x :: Nil => Some(x.values.mkString(", "))
      case _ => None
    }
  }
  /**
    * Purpose of this helper function is to get the PSD2-CERT value from a Request Headers.
    * @return the PSD2-CERT value from a Request Header as a String
    */
  def `getPSD2-CERT`(requestHeaders: List[HTTPParam]): Option[String] = {
    requestHeaders.toSet.filter(_.name == RequestHeader.`PSD2-CERT`).toList match {
      case x :: Nil => Some(x.values.mkString(", "))
      case _ => None
    }
  }
  def hasConsentId(requestHeaders: List[HTTPParam]): Boolean = {
    getConsentId(requestHeaders).isDefined
  }

  def registeredApplication(consumerKey: String): Boolean = {
    Consumers.consumers.vend.getConsumerByConsumerKey(consumerKey) match {
      case Full(application) => application.isActive.get
      case _ => false
    }
  }

  def registeredApplicationFuture(consumerKey: String): Future[Boolean] = {
    Consumers.consumers.vend.getConsumerByConsumerKeyFuture(consumerKey) map {
      case Full(c) => c.isActive.get
      case _ => false
    }
  }

  def logAPICall(callContext: Option[CallContextLight]) = {
    callContext match {
      case Some(cc) =>
        if(getPropsAsBoolValue("write_metrics", false)) {
          val userId = cc.userId.orNull
          val userName = cc.userName.orNull

          val implementedByPartialFunction = cc.partialFunctionName

          val duration =
            (cc.startTime, cc.endTime)  match {
              case (Some(s), Some(e)) => (e.getTime - s.getTime)
              case _       => -1
            }

          //execute saveMetric in future, as we do not need to know result of the operation
          Future {
            val consumerId = cc.consumerId.getOrElse(-1)
            val appName = cc.appName.orNull
            val developerEmail = cc.developerEmail.orNull

            APIMetrics.apiMetrics.vend.saveMetric(
              userId,
              cc.url,
              cc.startTime.getOrElse(null),
              duration,
              userName,
              appName,
              developerEmail,
              consumerId.toString,
              implementedByPartialFunction,
              cc.implementedInVersion,
              cc.verb,
              cc.httpCode,
              cc.correlationId,
            )
          }
        }
      case _ =>
        logger.error("SessionContext is not defined. Metrics cannot be saved.")
    }
  }

  def logAPICall(date: TimeSpan, duration: Long, rd: Option[ResourceDoc]) = {
    val authorization = S.request.map(_.header("Authorization")).flatten
    if(getPropsAsBoolValue("write_metrics", false)) {
      val user =
        if (hasAnOAuthHeader(authorization)) {
          getUser match {
            case Full(u) => Full(u)
            case _ => Empty
          }
        } else if (getPropsAsBoolValue("allow_direct_login", true) && hasDirectLoginHeader(authorization)) {
          DirectLogin.getUser match {
            case Full(u) => Full(u)
            case _ => Empty
          }
        } else {
            Empty
        }

      val consumer =
        if (hasAnOAuthHeader(authorization)) {
          getConsumer match {
            case Full(c) => Full(c)
            case _ => Empty
          }
        } else if (getPropsAsBoolValue("allow_direct_login", true) && hasDirectLoginHeader(authorization)) {
          DirectLogin.getConsumer match {
            case Full(c) => Full(c)
            case _ => Empty
          }
        } else {
          Empty
        }

      // TODO This should use Elastic Search or Kafka not an RDBMS
      val u: User = user.orNull
      val userId = if (u != null) u.userId else "null"
      val userName = if (u != null) u.name else "null"

      val c: Consumer = consumer.orNull
      //The consumerId, not key
      val consumerId = if (u != null) c.id.toString() else "null"
      var appName = if (u != null) c.name.toString() else "null"
      var developerEmail = if (u != null) c.developerEmail.toString() else "null"
      val implementedByPartialFunction = rd match {
        case Some(r) => r.partialFunctionName
        case _       => ""
      }
      //name of version where the call is implemented) -- S.request.get.view
      val implementedInVersion = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).view
      //(GET, POST etc.) --S.request.get.requestType.method
      val verb = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).requestType.method
      val url = S.uriAndQueryString.getOrElse("")
      val correlationId = getCorrelationId()

      //execute saveMetric in future, as we do not need to know result of operation
      Future {
        APIMetrics.apiMetrics.vend.saveMetric(
          userId,
          url,
          date,
          duration: Long,
          userName,
          appName,
          developerEmail,
          consumerId,
          implementedByPartialFunction,
          implementedInVersion, 
          verb, 
          None,
          correlationId
        )
      }

    }
  }


  /*
  Return the git commit. If we can't for some reason (not a git root etc) then log and return ""
   */
  def gitCommit : String = {
    val commit = try {
      val properties = new java.util.Properties()
      logger.debug("Before getResourceAsStream git.properties")
      properties.load(getClass().getClassLoader().getResourceAsStream("git.properties"))
      logger.debug("Before get Property git.commit.id")
      properties.getProperty("git.commit.id", "")
    } catch {
      case e : Throwable => {
               logger.warn("gitCommit says: Could not return git commit. Does resources/git.properties exist?")
               logger.error(s"Exception in gitCommit: $e")
        "" // Return empty string
      }
    }
    commit
  }

  private def getHeadersNewStyle(cc: Option[CallContextLight]) = {
    CustomResponseHeaders(
      getGatewayLoginHeader(cc).list ::: getRateLimitHeadersNewStyle(cc).list
    )
  }

  private def getRateLimitHeadersNewStyle(cc: Option[CallContextLight]) = {
    (cc, RateLimitingUtil.useConsumerLimits) match {
      case (Some(x), true) =>
        CustomResponseHeaders(
          List(
            ("X-Rate-Limit-Reset", x.`X-Rate-Limit-Reset`.toString),
            ("X-Rate-Limit-Remaining", x.`X-Rate-Limit-Remaining`.toString),
            ("X-Rate-Limit-Limit", x.`X-Rate-Limit-Limit`.toString)
          )
        )
      case _ =>
        CustomResponseHeaders((Nil))
    }
  }

  /**
    *
    * @param jwt is a JWT value extracted from GatewayLogin Authorization Header.
    *            Value None implies that Authorization Header is NOT GatewayLogin
    * @return GatewayLogin Custom Response Header
    * Example of the Header in Response generated by this function:
    * GatewayLogin: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJsb2dpbl91c2VyX25hbWUiOiJON2p1dDhkIiwiaXNfZmlyc3QiOmZhbHNlLCJhcHBfaWQiOiIxMjMiLCJhcHBfbmFtZSI6Ik5hbWUgb2YgQ29uc3VtZXIiLCJ0aW1lc3RhbXAiOiIiLCJjYnNfdG9rZW4iOiI-LD8gICAgICAgICAgODE0MzMwMjAxMDI2MTIiLCJ0ZW1lbm9zX2lkIjoiIn0.saE7W-ydZcwbjxfWx7q6HeQ1q4LMLYZiuYSx7qdP0k8
    */
  def getGatewayLoginHeader(jwt: Option[CallContextLight]) = {
    jwt match {
      case Some(v) =>
       v.gatewayLoginResponseHeader match {
         case Some(h) =>
           val header = (gatewayResponseHeaderName, h)
           CustomResponseHeaders(List(header))
         case None =>
           CustomResponseHeaders(Nil)
       }
      case None =>
        CustomResponseHeaders(Nil)
    }
  }

  /** This function provide a name of parameter used to define different spelling of some words
    * E.g. if we provide an URL obp/v2.1.0/users/current/customers?format=ISO20022
    * JSON response is changed from "currency":"EUR" to "ccy":"EUR"
    *
    * @return A name of the parameter
    */
  def nameOfSpellingParam(): String = "spelling"

  def getSpellingParam(): Box[String] = {
    S.request match {
      case Full(r) =>
        r.header(nameOfSpellingParam()) match {
          case Full(h) =>
            Full(h)
          case _ =>
            S.param(nameOfSpellingParam())
        }
      case _ =>
        S.param(nameOfSpellingParam())
    }
  }

  def getHeadersCommonPart() = headers ::: List((ResponseHeader.`Correlation-Id`, getCorrelationId()))

  def getHeaders() = getHeadersCommonPart() ::: getGatewayResponseHeader()

  case class CustomResponseHeaders(list: List[(String, String)])

  //Note: changed noContent--> defaultSuccess, because of the Swagger format. (Not support empty in DataType, maybe fix it latter.)
  def noContentJsonResponse(implicit headers: CustomResponseHeaders = CustomResponseHeaders(Nil)) : JsonResponse =
    JsonResponse(JsRaw(""), getHeaders() ::: headers.list, Nil, 204)

  def successJsonResponse(json: JsonAST.JValue, httpCode : Int = 200)(implicit headers: CustomResponseHeaders = CustomResponseHeaders(Nil)) : JsonResponse = {
    val cc = ApiSession.updateCallContext(Spelling(getSpellingParam()), None)
    val jsonAst = ApiSession.processJson(json, cc)
    JsonResponse(jsonAst, getHeaders() ::: headers.list, Nil, httpCode)
  }

  def createdJsonResponse(json: JsonAST.JValue, httpCode : Int = 201)(implicit headers: CustomResponseHeaders = CustomResponseHeaders(Nil)) : JsonResponse = {
    val cc = ApiSession.updateCallContext(Spelling(getSpellingParam()), None)
    val jsonAst = ApiSession.processJson(json, cc)
    JsonResponse(jsonAst, getHeaders() ::: headers.list, Nil, httpCode)
  }

  def successJsonResponseNewStyle(cc: Any, callContext: Option[CallContext], httpCode : Int = 200)(implicit headers: CustomResponseHeaders = CustomResponseHeaders(Nil)) : JsonResponse = {
    val jsonAst = ApiSession.processJson((Extraction.decompose(cc)), callContext)
    callContext match {
      case Some(c) if c.httpCode.isDefined =>
        JsonResponse(jsonAst, getHeaders() ::: headers.list, Nil, c.httpCode.get)
      case Some(c) if c.verb == "DELETE" =>
        JsonResponse(JsRaw(""), getHeaders() ::: headers.list, Nil, 204)
      case _ =>
        JsonResponse(jsonAst, getHeaders() ::: headers.list, Nil, httpCode)
    }
  }

  def acceptedJsonResponse(json: JsonAST.JValue, httpCode : Int = 202)(implicit headers: CustomResponseHeaders = CustomResponseHeaders(Nil)) : JsonResponse = {
    val cc = ApiSession.updateCallContext(Spelling(getSpellingParam()), None)
    val jsonAst = ApiSession.processJson(json, cc)
    JsonResponse(jsonAst, getHeaders() ::: headers.list, Nil, httpCode)
  }

  def errorJsonResponse(message : String = "error", httpCode : Int = 400)(implicit headers: CustomResponseHeaders = CustomResponseHeaders(Nil)) : JsonResponse = {
    val code =
      message.contains(UserHasMissingRoles) match {
        case true =>
          403
        case _ =>
          httpCode
      }
    JsonResponse(Extraction.decompose(ErrorMessage(message = message, code = code)), getHeaders() ::: headers.list, Nil, code)
  }

  def notImplementedJsonResponse(message : String = ErrorMessages.NotImplemented, httpCode : Int = 501)(implicit headers: CustomResponseHeaders = CustomResponseHeaders(Nil)) : JsonResponse =
    JsonResponse(Extraction.decompose(ErrorMessage(message = message, code = httpCode)), getHeaders() ::: headers.list, Nil, httpCode)


  def oauthHeaderRequiredJsonResponse(implicit headers: CustomResponseHeaders = CustomResponseHeaders(Nil)) : JsonResponse =
    JsonResponse(Extraction.decompose(ErrorMessage(message = "Authentication via OAuth is required", code = 400)), getHeaders() ::: headers.list, Nil, 400)

  lazy val CurrencyIsoCodeFromXmlFile: Elem = LiftRules.getResource("/media/xml/ISOCurrencyCodes.xml").map{ url =>
    val input: InputStream = url.openStream()
    val xml = XML.load(input)
    if (input != null) input.close()
    xml
  }.openOrThrowException(s"$UnknownError,ISOCurrencyCodes.xml is missing in OBP server.  ")
  
  /** check the currency ISO code from the ISOCurrencyCodes.xml file */
  def isValidCurrencyISOCode(currencyCode: String): Boolean = {
    val currencyIsoCodeArray = (CurrencyIsoCodeFromXmlFile \"CcyTbl" \ "CcyNtry" \ "Ccy").map(_.text).mkString(" ").split("\\s+")
    currencyIsoCodeArray.contains(currencyCode)
  }

  /** Check the id values from GUI, such as ACCOUNT_ID, BANK_ID ...  */
  def isValidID(id :String):Boolean= {
    val regex = """^([A-Za-z0-9\-_.]+)$""".r
    id match {
      case regex(e) if(e.length<256) => true
      case _ => false
    }
  }





  /** enforce the password.
    * The rules :
    * 1) length is >16 characters without validations
    * 2) or Min 10 characters with mixed numbers + letters + upper+lower case + at least one special character.
    * */
  def isValidStrongPassword(password: String): Boolean = {
    /**
      * (?=.*\d)                    //should contain at least one digit
      * (?=.*[a-z])                 //should contain at least one lower case
      * (?=.*[A-Z])                 //should contain at least one upper case
      * (?=.*[!"#$%&'\(\)*+,-./:;<=>?@\\[\\\\]^_\\`{|}~])              //should contain at least one special character
      * ([A-Za-z0-9!"#$%&'\(\)*+,-./:;<=>?@\\[\\\\]^_\\`{|}~]{10,16})  //should contain 10 to 16 valid characters
      **/
    val regex =
      """^(?=.*\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[!"#$%&'\(\)*+,-./:;<=>?@\\[\\\\]^_\\`{|}~])([A-Za-z0-9!"#$%&'\(\)*+,-./:;<=>?@\\[\\\\]^_\\`{|}~]{10,16})$""".r
    password match {
      case password if (password.length > 16) => true
      case regex(password) => true
      case _ => false
    }
  }



  /** These three functions check rather than assert. I.e. they are silent if OK and return an error message if not.
    * They do not throw an exception on failure thus they are not assertions
    */

  /** only  A-Z, a-z and max length <= 512  */
  def checkMediumAlpha(value:String): String ={
    val valueLength = value.length
    val regex = """^([A-Za-z]+)$""".r
    value match {
      case regex(e) if(valueLength <= 512) => SILENCE_IS_GOLDEN
      case regex(e) if(valueLength > 512) => ErrorMessages.InvalidValueLength
      case _ => ErrorMessages.InvalidValueCharacters
    }
  }

  /** only  A-Z, a-z, 0-9 and max length <= 512  */
  def checkMediumAlphaNumeric(value:String): String ={
    val valueLength = value.length
    val regex = """^([A-Za-z0-9]+)$""".r
    value match {
      case regex(e) if(valueLength <= 512) => SILENCE_IS_GOLDEN
      case regex(e) if(valueLength > 512) => ErrorMessages.InvalidValueLength
      case _ => ErrorMessages.InvalidValueCharacters
    }
  }

  /** only  A-Z, a-z, 0-9, all allowed characters for password and max length <= 512  */
  def checkMediumPassword(value:String): String ={
    val valueLength = value.length
    val regex = """^([A-Za-z0-9!"#$%&'\(\)*+,-./:;<=>?@\\[\\\\]^_\\`{|}~]+)$""".r
    value match {
      case regex(e) if(valueLength <= 512) => SILENCE_IS_GOLDEN
      case regex(e) if(valueLength > 512) => ErrorMessages.InvalidValueLength
      case _ => ErrorMessages.InvalidValueCharacters
    }
  }

  /** only  A-Z, a-z, 0-9, -, _, ., @, and max length <= 512  */
  def checkMediumString(value:String): String ={
    val valueLength = value.length
    val regex = """^([A-Za-z0-9\-._@]+)$""".r
    value match {
      case regex(e) if(valueLength <= 512) => SILENCE_IS_GOLDEN
      case regex(e) if(valueLength > 512) => ErrorMessages.InvalidValueLength
      case _ => ErrorMessages.InvalidValueCharacters
    }
  }

  /** only  A-Z, a-z, 0-9, -, _, ., @, space and max length <= 512  */
  def checkUsernameString(value:String): String ={
    val valueLength = value.length
    val regex = """^([A-Za-z0-9\-._@ ]+)$""".r
    value match {
      case regex(e) if(valueLength <= 512) => SILENCE_IS_GOLDEN
      case regex(e) if(valueLength > 512) => ErrorMessages.InvalidValueLength
      case _ => ErrorMessages.InvalidValueCharacters
    }
  }


  def ValueOrOBP(text : String) =
    text match {
      case t if t == null => "OBP"
      case t if t.length > 0 => t
      case _ => "OBP"
    }

  def ValueOrOBPId(text : String, OBPId: String) =
    text match {
      case t if t == null => OBPId
      case t if t.length > 0 => t
      case _ => OBPId
    }

  def stringOrNull(text : String) =
    if(text == null || text.isEmpty)
      null
    else
      text

  def stringOptionOrNull(text : Option[String]) =
    text match {
      case Some(t) => stringOrNull(t)
      case _ => null
    }

  //started -- Filtering and Paging revelent methods////////////////////////////
  def parseObpStandardDate(date: String): Box[Date] =
  {
    val parsedDate = tryo{DateWithMsFormat.parse(date)}
  
    //This is for V1.0 V1.1 and V1.2.0 and V1.2.1 
    lazy val fallBackParsedDate = tryo{DateWithMsRollbackFormat.parse(date)}
  
    if (parsedDate.isDefined)
    {
      Full(parsedDate.openOrThrowException(attemptedToOpenAnEmptyBox))
    }
    else if (fallBackParsedDate.isDefined)
    {
      Full(fallBackParsedDate.openOrThrowException(attemptedToOpenAnEmptyBox))
    }
    else
    {
      Failure(FilterDateFormatError)
    }
  }
  
  
  
  
  /**
    * Extract the `values` from HTTPParam by the `name`.
    * In HTTPParam, the values is a List[String], It supports many values for one name
    * So this method returns a Box[List[String]. If there is no values, we return Empty instead of Full(List())
    */
  def getHttpValues(httpParams: List[HTTPParam], name: String): Box[List[String]] ={
   for{
      httpParams <- Full(httpParams)
      valueList <- Full(httpParams.filter(_.name.equalsIgnoreCase(name)).map(_.values).flatten)
      values <- Full(valueList) if(valueList.length > 0)//return Empty instead of Full(List())
    } yield values
  }

   def getSortDirection(httpParams: List[HTTPParam]): Box[OBPOrder] = {

     def validate(v: String) = {
       if (v.toLowerCase == "desc" || v.toLowerCase == "asc") {
         Full(OBPOrder(Some(v.toLowerCase)))
       }
       else {
         Failure(FilterSortDirectionError)
       }
     }

     (getHttpValues(httpParams, "sort_direction"), getHttpValues(httpParams, "obp_sort_direction")) match {
      case (Full(left), _) =>
        validate(left.head)
      case (_, Full(r)) =>
        validate(r.head)
      case _ => Full(OBPOrder(None))
    }

  }

   def getFromDate(httpParams: List[HTTPParam]): Box[OBPFromDate] = {
    val date: Box[Date] = (getHttpValues(httpParams, "from_date"), getHttpValues(httpParams, "obp_from_date")) match {
      case (Full(left),_) =>
        parseObpStandardDate(left.head)
      case (_, Full(right)) =>
        parseObpStandardDate(right.head)
      case _ =>
        Full(DefaultFromDate)
    }

    date.map(OBPFromDate(_))
  }

   def getToDate(httpParams: List[HTTPParam]): Box[OBPToDate] = {
    val date: Box[Date] = (getHttpValues(httpParams, "to_date"), getHttpValues(httpParams, "obp_to_date")) match {
      case (Full(left),_) =>
        parseObpStandardDate(left.head)
      case (_, Full(right)) =>
        parseObpStandardDate(right.head)
      case _ => {
        Full(APIUtil.DefaultToDate)
      }
    }

    date.map(OBPToDate(_))
  }

   def getOffset(httpParams: List[HTTPParam]): Box[OBPOffset] = {
     (getPaginationParam(httpParams, "offset", None, 0, FilterOffersetError), getPaginationParam(httpParams, "obp_offset", Some(0), 0, FilterOffersetError)) match {
       case (Full(left), _) =>
         Full(OBPOffset(left))
       case (Failure(m, e, c), _) =>
         Failure(m, e, c)
       case (_, Full(right)) =>
         Full(OBPOffset(right))
       case (_, Failure(m, e, c)) =>
         Failure(m, e, c)
       case _ => Full(OBPOffset(0))
     }
  }

   def getLimit(httpParams: List[HTTPParam]): Box[OBPLimit] = {
     (getPaginationParam(httpParams, "limit", None, 1, FilterLimitError), getPaginationParam(httpParams, "obp_limit", Some(50), 1, FilterLimitError)) match {
       case (Full(left), _) =>
         Full(OBPLimit(left))
       case (Failure(m, e, c), _) =>
         Failure(m, e, c)
       case (_, Full(right)) =>
         Full(OBPLimit(right))
       case (_, Failure(m, e, c)) =>
         Failure(m, e, c)
       case _ => Full(OBPLimit(50))
     }
  }

   private def getPaginationParam(httpParams: List[HTTPParam], paramName: String, defaultValue: Option[Int], minimumValue: Int, errorMsg: String): Box[Int]= {
     getHttpValues(httpParams, paramName) match {
      case Full(v) => {
        tryo{
          v.head.toInt
        } match {
          case Full(value) => {
            if(value >= minimumValue){
              Full(value)
            }
            else{
              Failure(errorMsg)
            }
          }
          case _ => Failure(errorMsg)
        }
      }
      case _ =>
        defaultValue match {
          case Some(default) => Full(default)
          case _ => Empty
        }
    }
  }

  def getHttpParamValuesByName(httpParams: List[HTTPParam], name: String): Box[OBPQueryParam] = {
     val obpQueryParam = for {
       values <- getHttpValues(httpParams, name)
       obpQueryParam <- name match {
         case "anon" => 
           for{
            value <- tryo(values.head.toBoolean)?~! FilterAnonFormatError
            anon = OBPAnon(value)
           }yield anon
           
         case "consumer_id" => Full(OBPConsumerId(values.head))
         case "user_id" => Full(OBPUserId(values.head))
         case "bank_id" => Full(OBPBankId(values.head))
         case "account_id" => Full(OBPAccountId(values.head))
         case "url" => Full(OBPUrl(values.head))
         case "app_name" => Full(OBPAppName(values.head))
         case "implemented_by_partial_function" => Full(OBPImplementedByPartialFunction(values.head))
         case "implemented_in_version" => Full(OBPImplementedInVersion(values.head))
         case "verb" => Full(OBPVerb(values.head))
         case "correlation_id" => Full(OBPCorrelationId(values.head))
         case "duration" => 
           for{
            value <- tryo(values.head.toLong )?~! FilterDurationFormatError
            anon = OBPDuration(value)
           }yield anon
         case "exclude_app_names" => Full(OBPExcludeAppNames(values)) //This will return a string list. 
         case "exclude_url_patterns" => Full(OBPExcludeUrlPatterns(values))//This will return a string list.
         case "exclude_implemented_by_partial_functions" => Full(OBPExcludeImplementedByPartialFunctions(values)) //This will return a string list. 
         case "function_name" => Full(OBPFunctionName(values.head)) 
         case "connector_name" => Full(OBPConnectorName(values.head))
         case "customer_id" => Full(OBPCustomerId(values.head))
         case _ => Full(OBPEmpty())
       }
     } yield
       obpQueryParam
     
     obpQueryParam match {
       case Empty => Full(OBPEmpty()) //Only Map Empty to Full(OBPEmpty())
       case others => others
     }
  }
  
   def createQueriesByHttpParams(httpParams: List[HTTPParam]): Box[List[OBPQueryParam]] = {
    for{
      sortDirection <- getSortDirection(httpParams)
      fromDate <- getFromDate(httpParams)
      toDate <- getToDate(httpParams)
      limit <- getLimit(httpParams)
      offset <- getOffset(httpParams)
      //all optional fields
      anon <- getHttpParamValuesByName(httpParams,"anon")
      consumerId <- getHttpParamValuesByName(httpParams,"consumer_id")
      userId <- getHttpParamValuesByName(httpParams, "user_id")
      bankId <- getHttpParamValuesByName(httpParams, "bank_id")
      accountId <- getHttpParamValuesByName(httpParams, "account_id")
      url <- getHttpParamValuesByName(httpParams, "url")
      appName <- getHttpParamValuesByName(httpParams, "app_name")
      implementedByPartialFunction <- getHttpParamValuesByName(httpParams, "implemented_by_partial_function")
      implementedInVersion <- getHttpParamValuesByName(httpParams, "implemented_in_version")
      verb <- getHttpParamValuesByName(httpParams, "verb")
      correlationId <- getHttpParamValuesByName(httpParams, "correlation_id")
      duration <- getHttpParamValuesByName(httpParams, "duration")
      excludeAppNames <- getHttpParamValuesByName(httpParams, "exclude_app_names")
      excludeUrlPattern <- getHttpParamValuesByName(httpParams, "exclude_url_patterns")
      excludeImplementedByPartialfunctions <- getHttpParamValuesByName(httpParams, "exclude_implemented_by_partial_functions")
      connectorName <- getHttpParamValuesByName(httpParams, "connector_name")
      functionName <- getHttpParamValuesByName(httpParams, "function_name")
      customerId <- getHttpParamValuesByName(httpParams, "customer_id")
    }yield{
      /**
        * sortBy is currently disabled as it would open up a security hole:
        *
        * sortBy as currently implemented will take in a parameter that searches on the mongo field names. The issue here
        * is that it will sort on the true value, and not the moderated output. So if a view is supposed to return an alias name
        * rather than the true value, but someone uses sortBy on the other bank account name/holder, not only will the returned data
        * have the wrong order, but information about the true account holder name will be exposed due to its position in the sorted order
        *
        * This applies to all fields that can have their data concealed... which in theory will eventually be most/all
        *
        */
      //val sortBy = json.header("obp_sort_by")
      val sortBy = None
      val ordering = OBPOrdering(sortBy, sortDirection)
      //This guarantee the order 
      List(limit, offset, ordering, fromDate, toDate, 
           anon, consumerId, userId, url, appName, implementedByPartialFunction, implementedInVersion, 
           verb, correlationId, duration, excludeAppNames, excludeUrlPattern, excludeImplementedByPartialfunctions,
           connectorName,functionName, bankId, accountId, customerId
       ).filter(_ != OBPEmpty())
    }
  }
  
  def createQueriesByHttpParamsFuture(httpParams: List[HTTPParam]) = Future {
    createQueriesByHttpParams(httpParams: List[HTTPParam])
  }
  
  /**
    * Here we use the HTTPParam case class from liftweb.
    * We try to keep it the same as `S.request.openOrThrowException(attemptedToOpenAnEmptyBox).request.headers`, so we unite the URLs and headers. 
    * 
    * @param httpRequestUrl  = eg: /obp/v3.1.0/management/metrics/top-consumers?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString
    * @return List(HTTPParam("from_date","$DateWithMsExampleString"),HTTPParam("to_date","$DateWithMsExampleString"))
    */
  def createHttpParamsByUrl(httpRequestUrl: String): Box[List[HTTPParam]] = {
    val sortDirection = getHttpRequestUrlParam(httpRequestUrl,"sort_direction")
    val fromDate =  getHttpRequestUrlParam(httpRequestUrl,"from_date")
    val toDate =  getHttpRequestUrlParam(httpRequestUrl,"to_date")
    val limit =  getHttpRequestUrlParam(httpRequestUrl,"limit")
    val offset =  getHttpRequestUrlParam(httpRequestUrl,"offset")
    val anon =  getHttpRequestUrlParam(httpRequestUrl,"anon")
    val consumerId =  getHttpRequestUrlParam(httpRequestUrl,"consumer_id")
    val userId =  getHttpRequestUrlParam(httpRequestUrl, "user_id")
    val bankId =  getHttpRequestUrlParam(httpRequestUrl, "bank_id")
    val accountId =  getHttpRequestUrlParam(httpRequestUrl, "account_id")
    val url =  getHttpRequestUrlParam(httpRequestUrl, "url")
    val appName =  getHttpRequestUrlParam(httpRequestUrl, "app_name")
    val implementedByPartialFunction =  getHttpRequestUrlParam(httpRequestUrl, "implemented_by_partial_function")
    val implementedInVersion =  getHttpRequestUrlParam(httpRequestUrl, "implemented_in_version")
    val verb =  getHttpRequestUrlParam(httpRequestUrl, "verb")
    val correlationId =  getHttpRequestUrlParam(httpRequestUrl, "correlation_id")
    val duration =  getHttpRequestUrlParam(httpRequestUrl, "duration")
    val currency =  getHttpRequestUrlParam(httpRequestUrl, "currency")
    val amount =  getHttpRequestUrlParam(httpRequestUrl, "amount")
    val customerId =  getHttpRequestUrlParam(httpRequestUrl, "customer_id")

    //The following three are not a string, it should be List of String
    //eg: exclude_app_names=A,B,C --> List(A,B,C)
    val excludeAppNames =  getHttpRequestUrlParam(httpRequestUrl, "exclude_app_names").split(",").toList
    val excludeUrlPattern =  getHttpRequestUrlParam(httpRequestUrl, "exclude_url_patterns").split(",").toList
    val excludeImplementedByPartialfunctions =  getHttpRequestUrlParam(httpRequestUrl, "exclude_implemented_by_partial_functions").split(",").toList
    
    val functionName =  getHttpRequestUrlParam(httpRequestUrl, "function_name")
    val connectorName =  getHttpRequestUrlParam(httpRequestUrl, "connector_name")
    
    Full(List(
      HTTPParam("sort_direction",sortDirection), HTTPParam("from_date",fromDate), HTTPParam("to_date", toDate), HTTPParam("limit",limit), HTTPParam("offset",offset), 
      HTTPParam("anon", anon), HTTPParam("consumer_id", consumerId), HTTPParam("user_id", userId), HTTPParam("url", url), HTTPParam("app_name", appName), 
      HTTPParam("implemented_by_partial_function",implementedByPartialFunction), HTTPParam("implemented_in_version",implementedInVersion), HTTPParam("verb", verb), 
      HTTPParam("correlation_id", correlationId), HTTPParam("duration", duration), HTTPParam("exclude_app_names", excludeAppNames),
      HTTPParam("exclude_url_patterns", excludeUrlPattern),HTTPParam("exclude_implemented_by_partial_functions", excludeImplementedByPartialfunctions),
      HTTPParam("function_name", functionName),
      HTTPParam("currency", currency),
      HTTPParam("amount", amount),
      HTTPParam("bank_id", bankId),
      HTTPParam("account_id", accountId),
      HTTPParam("connector_name", connectorName),
      HTTPParam("customer_id", customerId)
    ).filter(_.values.head != ""))//Here filter the filed when value = "". 
  }
  
  def createHttpParamsByUrlFuture(httpRequestUrl: String) = Future {
    createHttpParamsByUrl(httpRequestUrl: String)
  }
  
  /**
    * 
    * @param httpRequestUrl eg:  /obp/v3.1.0/management/metrics/top-consumers?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString
    * @param name eg: from_date
    * @return the $DateWithMsExampleString for the from_date.
    *         There is no error handling here, just extract whatever it got from the Url string. If not value for that name, just return ""
    */
  def getHttpRequestUrlParam(httpRequestUrl: String, name: String): String = {
    val urlAndQueryString =  if (httpRequestUrl.contains("?")) httpRequestUrl.split("\\?",2)(1) else "" // Full(from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString)
    val queryStrings  = urlAndQueryString.split("&").map(_.split("=")).flatten  //Full(from_date, $DateWithMsExampleString, to_date, $DateWithMsExampleString)
    if (queryStrings.contains(name)&& queryStrings.length > queryStrings.indexOf(name)+1) queryStrings(queryStrings.indexOf(name)+1) else ""//Full($DateWithMsExampleString)
  }
  //ended -- Filtering and Paging revelent methods  ////////////////////////////


  /** Import this object's methods to add signing operators to dispatch.Request */
  object OAuth {
    import dispatch.{Req => Request}
    import org.apache.http.protocol.HTTP.UTF_8

    import scala.collection.Map
    import scala.collection.immutable.{Map => IMap}

    case class ReqData (
                      url: String,
                      method: String,
                      body: String,
                      body_encoding: String,
                      headers: Map[String, String],
                      query_params: Map[String,String],
                      form_params: Map[String,String]
                     )

    case class Consumer(key: String, secret: String)
    case class Token(value: String, secret: String)
    object Token {
      def apply[T <: Any](m: Map[String, T]): Option[Token] = List(TokenName, TokenSecretName).flatMap(m.get) match {
        case value :: secret :: Nil => Some(Token(value.toString, secret.toString))
        case _ => None
      }
    }

    /** @return oauth parameter map including signature */
    def sign(method: String, url: String, user_params: Map[String, String], consumer: Consumer, token: Option[Token], verifier: Option[String], callback: Option[String]): IMap[String, String] = {
      val oauth_params = IMap(
        "oauth_consumer_key" -> consumer.key,
        SignatureMethodName -> "HMAC-SHA256",
        TimestampName -> (System.currentTimeMillis / 1000).toString,
        NonceName -> System.nanoTime.toString,
        VersionName -> "1.0"
      ) ++ token.map { TokenName -> _.value } ++
        verifier.map { VerifierName -> _ } ++
        callback.map { CallbackName -> _ }

      val signatureBase = Arithmetics.concatItemsForSignature(method.toUpperCase, url, user_params.toList, Nil, oauth_params.toList)
      val computedSignature = Arithmetics.sign(signatureBase, consumer.secret, (token map { _.secret } getOrElse ""), Arithmetics.HmacSha256Algorithm)
      logger.debug("signatureBase: " + signatureBase)
      logger.debug("computedSignature: " + computedSignature)
      oauth_params + (SignatureName -> computedSignature)
    }

    /** Out-of-band callback code */
    val oob = "oob"

    /** Map with oauth_callback set to the given url */
    def callback(url: String) = IMap(CallbackName -> url)

    //normalize to OAuth percent encoding
    private def %% (str: String): String = {
      val remaps = ("+", "%20") :: ("%7E", "~") :: ("*", "%2A") :: Nil
      (encode_%(str) /: remaps) { case (str, (a, b)) => str.replace(a,b) }
    }
    private def %% (s: Seq[String]): String = s map %% mkString "&"
    private def %% (t: (String, Any)): (String, String) = (%%(t._1), %%(t._2.toString))

    private def bytes(str: String) = str.getBytes(UTF_8)

    /** Add OAuth operators to dispatch.Request */
    implicit def Request2RequestSigner(r: Request) = new RequestSigner(r)

    /** @return %-encoded string for use in URLs */
    def encode_% (s: String) = java.net.URLEncoder.encode(s, org.apache.http.protocol.HTTP.UTF_8)

    /** @return %-decoded string e.g. from query string or form body */
    def decode_% (s: String) = java.net.URLDecoder.decode(s, org.apache.http.protocol.HTTP.UTF_8)

    class RequestSigner(rb: Request) {
      private val r = rb.toRequest
      @deprecated("use <@ (consumer, callback) to pass the callback in the header for a request-token request")
      def <@ (consumer: Consumer): Request = sign(consumer, None, None, None)
      /** sign a request with a callback, e.g. a request-token request */
      def <@ (consumer: Consumer, callback: String): Request = sign(consumer, None, None, Some(callback))
      /** sign a request with a consumer, token, and verifier, e.g. access-token request */
      def <@ (consumer: Consumer, token: Token, verifier: String): Request =
        sign(consumer, Some(token), Some(verifier), None)
      /** sign a request with a consumer and a token, e.g. an OAuth-signed API request */
      def <@ (consumer: Consumer, token: Token): Request = sign(consumer, Some(token), None, None)
      def <@ (consumerAndToken: Option[(Consumer,Token)]): Request = {
        consumerAndToken match {
          case Some(cAndt) => sign(cAndt._1, Some(cAndt._2), None, None)
          case _ => rb
        }
      }

      /** Sign request by reading Post (<<) and query string parameters */
      private def sign(consumer: Consumer, token: Option[Token], verifier: Option[String], callback: Option[String]) = {

        val oauth_url = r.getUrl.split('?')(0)
        val query_params = r.getQueryParams.asScala.groupBy(_.getName).mapValues(_.map(_.getValue)).map {
            case (k, v) => k -> v.toString
          }
        val form_params = r.getFormParams.asScala.groupBy(_.getName).mapValues(_.map(_.getValue)).map {
            case (k, v) => k -> v.toString
          }
        val body_encoding = r.getCharset
        var body = new String()
        if (r.getByteData != null )
          body = new String(r.getByteData)
        val oauth_params = OAuth.sign(r.getMethod, oauth_url,
                                      query_params ++ form_params,
                                      consumer, token, verifier, callback)

        def createRequest( reqData: ReqData ): Request = {
          val charset = if(reqData.body_encoding == "null") Charset.defaultCharset() else Charset.forName(reqData.body_encoding)
          val rb = url(reqData.url)
            .setMethod(reqData.method)
            .setBodyEncoding(charset)
            .setBody(reqData.body) <:< reqData.headers
          if (reqData.query_params.nonEmpty)
            rb <<? reqData.query_params
          rb
        }

        createRequest( ReqData(
          oauth_url,
          r.getMethod,
          body,
          if (body_encoding == null) "null" else body_encoding.name(),
          IMap("Authorization" -> ("OAuth " + oauth_params.map {
            case (k, v) => encode_%(k) + "=\"%s\"".format(encode_%(v.toString))
          }.mkString(",") )),
          query_params,
          form_params
        ))
      }
    }
  }

  /*
  Used to document API calls / resources.

  TODO Can we extract apiVersion, apiFunction, requestVerb and requestUrl from partialFunction?

   */



  case class Catalogs(core: Boolean = false, psd2: Boolean = false, obwg: Boolean = false)

  val Core = true
  val PSD2 = true
  val OBWG = true
  val notCore = false
  val notPSD2 = false
  val notOBWG = false

  case class BaseErrorResponseBody(
    //code: String,//maybe used, for now, 400,204,200...are handled in RestHelper class
    //TODO, this should be a case class name, but for now, the InvalidNumber are just String, not the case class.
    name: String,
    detail: String
  )

  //check #511, https://github.com/OpenBankProject/OBP-API/issues/511
  // get rid of JValue, but in API-EXPLORER or other places, it need the Empty JValue "{}"
  // So create the EmptyClassJson to set the empty JValue "{}"
  case class EmptyClassJson(jsonString: String ="{}")

  // Used to document the API calls
  case class ResourceDoc(
                          partialFunction: OBPEndpoint, // PartialFunction[Req, Box[User] => Box[JsonResponse]],
                          implementedInApiVersion: ScannedApiVersion, // TODO: Use ApiVersion enumeration instead of string
                          partialFunctionName: String, // The string name of the partial function that implements this resource. Could use it to link to the source code that implements the call
                          requestVerb: String, // GET, POST etc. TODO: Constrain to GET, POST etc.
                          requestUrl: String, // The URL. THIS GETS MODIFIED TO include the implemented in prefix e.g. /obp/vX.X). Starts with / No trailing slash.
                          summary: String, // A summary of the call (originally taken from code comment) SHOULD be under 120 chars to be inline with Swagger
                          var description: String, // Longer description (originally taken from github wiki)
                          exampleRequestBody: scala.Product, // An example of the body required (maybe empty)
                          successResponseBody: scala.Product, // A successful response body
                          var errorResponseBodies: List[String], // Possible error responses
                          catalogs: Catalogs,
                          tags: List[ResourceDocTag],
                          var roles: Option[List[ApiRole]] = None,
                          isFeatured: Boolean = false,
                          specialInstructions: Option[String] = None,
                          specifiedUrl: Option[String] = None, // A derived value: Contains the called version (added at run time). See the resource doc for resource doc!
                          connectorMethods: Option[List[String]] = None
                        ) {
    // this code block will be merged to constructor.
    {
      val authenticationIsRequired = authenticationRequiredMessage(true)
      val authenticationIsOptional = authenticationRequiredMessage(false)

      val rolesIsEmpty = roles.map(_.isEmpty).getOrElse(true)
      // if required roles not empty, add UserHasMissingRoles to errorResponseBodies
      if (rolesIsEmpty) {
        errorResponseBodies ?-= UserHasMissingRoles
      } else {
        errorResponseBodies ?+= UserNotLoggedIn
        errorResponseBodies ?+= UserHasMissingRoles
      }
      // if authentication is required, add UserNotLoggedIn to errorResponseBodies
      if (description.contains(authenticationIsRequired)) {
        errorResponseBodies ?+= UserNotLoggedIn
      } else if (description.contains(authenticationIsOptional) && rolesIsEmpty) {
        errorResponseBodies ?-= UserNotLoggedIn
      } else if (errorResponseBodies.contains(UserNotLoggedIn)) {
        description +=
          s"""
             |
             |$authenticationIsRequired
             |"""
      } else if (!errorResponseBodies.contains(UserNotLoggedIn)) {
        description +=
          s"""
             |
             |$authenticationIsOptional
             |"""
      }
    }
    private val rolesForCheck = roles match {
      case Some(list) => list
      case _ => Nil
    }
    // un-wrapper roles
    roles = roles.map(_.flatMap({
      case RoleCombination(rs) => rs
      case r => r :: Nil
    }))

    /**
     * 0 is notset
     * 1 is enabled manually
     * -1 is disabled manually
     */
    private var _isAutoValidate = 0

    /**
     * enable auto validate instead of default manner
     */
    def enableAutoValidate(): ResourceDoc = {
      _isAutoValidate = 1
      this
    }

    /**
     * whether set auto validate manually
     */
    def isValidateEnabled = _isAutoValidate == 1

    /**
     * disable auto validate instead of default manner
     */
    def disableAutoValidate(): ResourceDoc = {
      _isAutoValidate = -1
      this
    }

    /**
     * whether disable auto validate manually
     */
    def isValidateDisabled = _isAutoValidate == -1


    private var _autoValidateRoles = true

    /**
     * disable roles auto validation, that means you want do validation by yourself.
     */
    def disableAutoValidateRoles(): ResourceDoc = {
      _autoValidateRoles = false
      this
    }
    private var _autoValidateAuthenticate = true
    def disableAutoValidateAuthenticate(): ResourceDoc = {
      _autoValidateAuthenticate = false
      this
    }

    /**
     * According errorResponseBodies whether contains UserNotLoggedIn and UserHasMissingRoles do validation.
     * So can avoid duplicate code in endpoint body for expression do check.
     * Note: maybe this will be misused, So currently just comment out.
     */
    //lazy val wrappedEndpoint: OBPEndpoint = wrappedWithAuthCheck(partialFunction)

    /**
     * wrapped an endpoint to a new one, let it do auth check before execute the endpoint body
     *
     * @param obpEndpoint original endpoint
     * @return wrapped endpoint
     */
    def wrappedWithAuthCheck(obpEndpoint: OBPEndpoint): OBPEndpoint = {

      val requestUrlPartPath: Array[String] = StringUtils.split(requestUrl, '/')

      val requestUrlBankId = requestUrlPartPath.indexOf("BANK_ID")
      val requestUrlAccountId = requestUrlPartPath.indexOf("ACCOUNT_ID")
      val requestUrlViewId = requestUrlPartPath.indexOf("VIEW_ID")

      def getIds(url: List[String]): (Option[BankId], Option[AccountId], Option[ViewId]) = {
        val apiPrefixLength = url.size - requestUrlPartPath.size

        val bankId = if (requestUrlBankId != -1) {
          url.lift(apiPrefixLength + requestUrlBankId).map(BankId(_))
        } else {
          None
        }
        val accountId = if (bankId.isDefined && requestUrlAccountId != -1) {
          url.lift(apiPrefixLength + requestUrlAccountId).map(AccountId(_))
        } else {
          None
        }

        val viewId = if (accountId.isDefined && requestUrlViewId != -1) {
          url.lift(apiPrefixLength + requestUrlViewId).map(ViewId(_))
        } else {
          None
        }

        (bankId, accountId, viewId)
      }

      def checkAuth(cc: CallContext): Future[(Box[User], Option[CallContext])] =
        if (errorResponseBodies.contains($UserNotLoggedIn)) authorizedAccess(cc) else anonymousAccess(cc)

      def checkRoles(bankId: Option[BankId], user: Box[User]):Future[Box[Unit]] =
        if(_autoValidateRoles && rolesForCheck.nonEmpty) {
            val bankIdStr = bankId.map(_.value).getOrElse("")
            val userIdStr = user.map(_.userId).openOr("")
            NewStyle.function.hasAtLeastOneEntitlement(bankIdStr, userIdStr, rolesForCheck)
          } else {
              Future.successful(Full(Unit))
            }

      def checkBank(bankId: Option[BankId], callContext: Option[CallContext]): Future[(Bank, Option[CallContext])] = {
        val needCheckBank = errorResponseBodies.contains($BankNotFound)
        (needCheckBank, bankId) match {
          case (true, Some(bId)) => NewStyle.function.getBank(bId, callContext)
          case _ => Future.successful(null.asInstanceOf[Bank] -> callContext)
        }
      }

      def checkAccount(bankId: Option[BankId], accountId: Option[AccountId], callContext: Option[CallContext]): Future[(BankAccount, Option[CallContext])] = {
        val needCheckAccount = errorResponseBodies.contains($BankAccountNotFound)
        (needCheckAccount,bankId, accountId) match {
          case (true, Some(bId), Some(aId)) => NewStyle.function.getBankAccount(bId, aId, callContext)
          case _ => Future.successful(null.asInstanceOf[BankAccount] -> callContext)
        }
      }

      def checkView(viewId: Option[ViewId],
                    bankId: Option[BankId],
                    accountId: Option[AccountId],
                    boxUser: Box[User],
                    callContext: Option[CallContext]): Future[View] = {
        val needCheckView = errorResponseBodies.contains($UserNoPermissionAccessView)

        (needCheckView, bankId, accountId, viewId) match {
          case (true, Some(bId), Some(aId), Some(vId)) =>
            val bankIdAccountId = BankIdAccountId(bId, aId)
            NewStyle.function.checkViewAccessAndReturnView(vId, bankIdAccountId, boxUser, callContext)
          case _ => Future.successful(null.asInstanceOf[View])
        }
      }

      new OBPEndpoint {
        override def isDefinedAt(x: Req): Boolean = obpEndpoint.isDefinedAt(x)

        override def apply(req: Req): CallContext => Box[JsonResponse] = {
          val originFn: CallContext => Box[JsonResponse] = obpEndpoint.apply(req)


          val (bankId, accountId, viewId) = getIds(req.path.partPath)

          val request: Box[Req] = S.request
          val session: Box[LiftSession] = S.session

          cc: CallContext => {
            // if authentication check, do authorizedAccess, else do Rate Limit check
            for {
              (boxUser, callContext) <- checkAuth(cc)

              // roles check
              _ <- checkRoles(bankId, boxUser)

              // check bankId valid
              (bank, callContext) <- checkBank(bankId, callContext)
              // check accountId valid
              (account, callContext) <- checkAccount(bankId, accountId, callContext)
              // check user access permission of this viewId corresponding view
              view <- checkView(viewId, bankId, accountId, boxUser, callContext)

            } yield {
              val Some(newCallContext) = if(boxUser.isDefined) callContext.map(_.copy(user=boxUser)) else callContext
              //pass session and request to endpoint body
              val boxResponse = S.init(request, session.orNull) {
                // pass user, bank, account and view to endpoint body
                SS.init(boxUser, bank, account, view) {
                  originFn(newCallContext)
                }
              }
              (boxResponse, callContext)
            }

          }
        }
      }

    }
  }

  /**
   * Simulate S pass request and session, this object pass bank, account and view.
   * This method invoke must fulfill three point:
   *  1. be invoked at endpoint's body
   *  2. be invoked out of for comprehension
   *  3. endpoint's corresponding ResourceDoc.errorResponseBodies must contains $BankNotFound, $BankAccountNotFound or $UserNoPermissionAccessView
   */
  object SS {
    private val _user = new ThreadGlobal[User]
    private val _bank = new ThreadGlobal[Bank]
    private val _bankAccount = new ThreadGlobal[BankAccount]
    private val _view = new ThreadGlobal[View]

    def init[B](boxUser: Box[User], bank: Bank, bankAccount: BankAccount, view: View)(f: => B):B = {
      _user.doWith(boxUser.orNull){
        _bank.doWith(bank) {
          _bankAccount.doWith(bankAccount) {
            _view.doWith(view) {
              f
            }
          }
        }
      }
    }

    private def bank: Bank = _bank.box.openOrThrowException(buildErrorMsg(nameOf($BankNotFound)))
    private def bankAccount: BankAccount = _bankAccount.box.openOrThrowException(buildErrorMsg(nameOf($BankAccountNotFound)))
    private def getView: View = _view.box.openOrThrowException(buildErrorMsg(nameOf($UserNoPermissionAccessView)))

    /**
     * Get current login user, recommend call cc.loggedInUser instead.
     * Note, the same as S.session of lift framework, the method only can be called at first line of endpoint for comprehension or out of for comprehension.
     * @return current login user
     */
    def user: Future[Box[User]] = Future.successful(_user.box)

    /**
     * Get current login user, and bank find by url /BANK_ID/.
     * Validation of id exists passed.
     * Note, the same as S.session of lift framework, the method only can be called at first line of endpoint for comprehension or out of for comprehension.
     */
    def userBank: Future[(Box[User], Bank)] = Future.successful(_user.box -> bank)
    /**
     * Get current login user and bank find by url /BANK_ID/ and account find by /ACCOUNT_ID/.
     * Validation of id exists passed.
     * Note, the same as S.session of lift framework, the method only can be called at first line of endpoint for comprehension or out of for comprehension.
     */
    def userBankAccount: Future[(Box[User], Bank, BankAccount)] = Future.successful {
      (_user.box, bank, bankAccount)
    }

    /**
     * Get current login user, and bank find by url /BANK_ID/, and account find by /ACCOUNT_ID/, and view find by  /VIEW_ID/.
     * Validation of id exists passed.
     * Note, the same as S.session of lift framework, the method only can be called at first line of endpoint for comprehension or out of for comprehension.
     */
    def userBankAccountView: Future[(Box[User], Bank, BankAccount, View)] = Future.successful {
      (_user.box, bank, bankAccount, getView)
    }

    /**
     * Get view find by  /VIEW_ID/.
     * Validation of id exists passed.
     * Note, the same as S.session of lift framework, the method only can be called at first line of endpoint for comprehension or out of for comprehension.
     */
    def view: Future[View] = Future.successful(getView)
    /**
     * Get current login user, and view find by  /VIEW_ID/.
     * Validation of id exists passed.
     * Note, the same as S.session of lift framework, the method only can be called at first line of endpoint for comprehension or out of for comprehension.
     */
    def userView: Future[(Box[User], View)] = Future.successful(_user.box -> getView)

    /**
     * Get current login user and account find by /ACCOUNT_ID/.
     * Validation of id exists passed.
     * Note, the same as S.session of lift framework, the method only can be called at first line of endpoint for comprehension or out of for comprehension.
     */
    def userAccount: Future[(Box[User], BankAccount)] = Future.successful(_user.box -> bankAccount)

    private def buildErrorMsg(msg: String) =
      s"""
         |This method invoke must fulfill three point:
         | 1. be invoked at endpoint's body
         | 2. be invoked out of for comprehension
         | 3. endpoint's corresponding ResourceDoc.errorResponseBodies must contains $msg
         |""".stripMargin
  }

  def getGlossaryItems : List[GlossaryItem] = {
    Glossary.glossaryItems.toList.sortBy(_.title)
  }

  // Used to document the KafkaMessage calls
  case class MessageDoc(
                         process: String,
                         messageFormat: String,
                         description: String,
                         outboundTopic: Option[String] = None,
                         inboundTopic: Option[String] = None,
                         exampleOutboundMessage: scala.Product,
                         exampleInboundMessage: scala.Product,
                         outboundAvroSchema: Option[JValue] = None,
                         inboundAvroSchema: Option[JValue] = None,
                         adapterImplementation : Option[AdapterImplementation] = None
  )

  case class AdapterImplementation(
                                    group: String,
                                    suggestedOrder : Integer
                           )

  // Define relations between API end points. Used to create _links in the JSON and maybe later for API Explorer browsing
  case class ApiRelation(
    fromPF : OBPEndpoint,
    toPF : OBPEndpoint,
    rel : String
  )

  // Populated from Resource Doc and ApiRelation
  case class InternalApiLink(
    fromPF : OBPEndpoint,
    toPF : OBPEndpoint,
    rel : String,
    requestUrl: String
    )

  // Used to pass context of current API call to the function that generates links for related Api calls.
  case class DataContext(
    user : Box[User],
    bankId :  Option[BankId],
    accountId: Option[AccountId],
    viewId: Option[ViewId],
    counterpartyId: Option[CounterpartyId],
    transactionId: Option[TransactionId]
)

  case class CallerContext(
    caller : OBPEndpoint
  )

  case class CodeContext(
    resourceDocsArrayBuffer : ArrayBuffer[ResourceDoc],
    relationsArrayBuffer : ArrayBuffer[ApiRelation]
  )



  case class ApiLink(
    rel: String,
    href: String
  )

  case class LinksJSON(
   _links: List[ApiLink]
 )

  case class ResultAndLinksJSON(
    result : JValue,
    _links: List[ApiLink]
  )


  def createResultAndLinksJSON(result : JValue, links : List[ApiLink] ) : ResultAndLinksJSON = {
    new ResultAndLinksJSON(
      result,
      links
    )
  }





/*
Returns a string showed to the developer
 */
  def authenticationRequiredMessage(authRequired: Boolean) : String =
  authRequired match {
      case true => "Authentication is Mandatory"
      case false => "Authentication is Optional"
    }



  def fullBaseUrl : String = {
    val crv = CurrentReq.value
    val apiPathZeroFromRequest = crv.path.partPath(0)
    if (apiPathZeroFromRequest != ApiPathZero) throw new Exception("Configured ApiPathZero is not the same as the actual.")

    val path = s"$HostName/$ApiPathZero"
    path
  }


// Modify URL replacing placeholders for Ids
  def contextModifiedUrl(url: String, context: DataContext) = {

  // Potentially replace BANK_ID
    val url2: String = context.bankId match {
      case Some(x) => url.replaceAll("BANK_ID", x.value)
      case _ => url
    }

    val url3: String = context.accountId match {
      // Take care *not* to change OTHER_ACCOUNT_ID HERE
      case Some(x) => url2.replaceAll("/ACCOUNT_ID", s"/${x.value}").replaceAll("COUNTERPARTY_ID", x.value)
      case _ => url2
    }

    val url4: String = context.viewId match {
      case Some(x) => url3.replaceAll("VIEW_ID", {x.value})
      case _ => url3
    }

    val url5: String = context.counterpartyId match {
      // Change OTHER_ACCOUNT_ID or COUNTERPARTY_ID
      case Some(x) => url4.replaceAll("OTHER_ACCOUNT_ID", x.value).replaceAll("COUNTERPARTY_ID", x.value)
      case _ => url4
    }

    val url6: String = context.transactionId match {
      case Some(x) => url5.replaceAll("TRANSACTION_ID", x.value)
      case _ => url5
    }

  // Add host, port, prefix, version.

  // not correct because call could be in other version
    val fullUrl = s"$fullBaseUrl$url6"

  fullUrl
  }


  def getApiLinkTemplates(callerContext: CallerContext,
                           codeContext: CodeContext
                         ) : List[InternalApiLink] = {



    // Relations of the API version where the caller is defined.
    val relations =  codeContext.relationsArrayBuffer.toList

    // Resource Docs
    // Note: This doesn't allow linking to calls in earlier versions of the API
    // TODO: Fix me
    val resourceDocs =  codeContext.resourceDocsArrayBuffer

    val pf = callerContext.caller

    val internalApiLinks: List[InternalApiLink] = for {
      relation <- relations.filter(r => r.fromPF == pf)
      toResourceDoc <- resourceDocs.find(rd => rd.partialFunction == relation.toPF)
    }
      yield new InternalApiLink(
        pf,
        toResourceDoc.partialFunction,
        relation.rel,
        // Add the vVersion to the documented url
        s"/${toResourceDoc.implementedInApiVersion.vDottedApiVersion}${toResourceDoc.requestUrl}"
      )
    internalApiLinks
  }



  // This is not currently including "templated" attribute
  def halLinkFragment (link: ApiLink) : String = {
    "\"" + link.rel +"\": { \"href\": \"" +link.href + "\" }"
  }


  // Since HAL links can't be represented via a case class, (they have dynamic attributes rather than a list) we need to generate them here.
  def buildHalLinks(links: List[ApiLink]): JValue = {

    val halLinksString = links match {
      case head :: tail => tail.foldLeft("{"){(r: String, c: ApiLink) => ( r + " " + halLinkFragment(c) + " ,"  ) } + halLinkFragment(head) + "}"
      case Nil => "{}"
    }
    parse(halLinksString)
  }


  // Returns API links (a list of them) that have placeholders (e.g. BANK_ID) replaced by values (e.g. ulster-bank)
  def getApiLinks(callerContext: CallerContext, codeContext: CodeContext, dataContext: DataContext) : List[ApiLink]  = {
    val templates = getApiLinkTemplates(callerContext, codeContext)
    // Replace place holders in the urls like BANK_ID with the current value e.g. 'ulster-bank' and return as ApiLinks for external consumption
    val links = templates.map(i => ApiLink(i.rel,
      contextModifiedUrl(i.requestUrl, dataContext) )
    )
    links
  }


  // Returns links formatted at objects.
  def getHalLinks(callerContext: CallerContext, codeContext: CodeContext, dataContext: DataContext) : JValue  = {
    val links = getApiLinks(callerContext, codeContext, dataContext)
    getHalLinksFromApiLinks(links)
  }



  def getHalLinksFromApiLinks(links: List[ApiLink]) : JValue = {
    val halLinksJson = buildHalLinks(links)
    halLinksJson
  }

  def isSuperAdmin(user_id: String) : Boolean = {
    val user_ids = APIUtil.getPropsValue("super_admin_user_ids") match {
      case Full(v) =>
        v.split(",").map(_.trim).toList
      case _ =>
        List()
    }
    user_ids.filter(_ == user_id).length > 0
  }

  def hasScope(bankId: String, consumerId: String, role: ApiRole): Boolean = {
    !Scope.scope.vend.getScope(bankId, consumerId, role.toString).isEmpty
  }
  def getConsumerPrimaryKey(callContext: Option[CallContext]): String = {
    callContext match {
      case Some(cc) =>
        cc.consumer.map(_.id.get.toString).getOrElse("")
      case _ =>
        ""
    }
  }
  def checkScope(bankId: String, consumerId: String, role: ApiRole): Boolean = {
    requireScopes(role) match {
      case false => true // if the props require_scopes == false, we do not need to check the Scope stuff..
      case true => !Scope.scope.vend.getScope(bankId, consumerId, role.toString).isEmpty
    }
  }
  
  // Function checks does a consumer specified by a parameter consumerId has at least one role provided by a parameter roles at a bank specified by a parameter bankId
  // i.e. does consumer has assigned at least one role from the list
  def hasAtLeastOneScope(bankId: String, consumerId: String, roles: List[ApiRole]): Boolean = {
    val list: List[Boolean] = for (role <- roles) yield {
      !Scope.scope.vend.getScope(if (role.requiresBankId == true) bankId else "", consumerId, role.toString).isEmpty
    }
    list.exists(_ == true)
  }

  def hasEntitlement(bankId: String, userId: String, apiRole: ApiRole): Boolean = apiRole match {
      case RoleCombination(roles) => roles.forall(hasEntitlement(bankId, userId, _))
      case role =>
        Entitlement.entitlement.vend.getEntitlement(if (role.requiresBankId) bankId else "", userId, role.toString).isDefined
  }

  case class EntitlementAndScopeStatus(
    hasBoth: Boolean,
    reason: Option[String] = None, //for Later
    errorMessage: String,
  )

  def requireScopes(role: ApiRole) = {
      ApiProperty.requireScopesForAllRoles match {
      case false =>
        getPropsValue("enable_scopes_for_roles").toList.map(_.split(",")).flatten.exists(_ == role.toString())
      case true => 
        true
    }
  }
  
  def hasEntitlementAndScope(bankId: String, userId: String, consumerId: String, role: ApiRole): Box[EntitlementAndScopeStatus]= {
    for{
      hasEntitlement <- tryo{ !Entitlement.entitlement.vend.getEntitlement(bankId, userId, role.toString).isEmpty} ?~! s"$UnknownError"
      hasScope <- requireScopes(role) match {
        case false => Full(true) // if the props require_scopes == false, we need not check the Scope stuff..
        case true => tryo{ !Scope.scope.vend.getScope(bankId, consumerId, role.toString).isEmpty} ?~! s"$UnknownError "
      }
        
      hasBoth = hasEntitlement && hasScope
      differentErrorMessages = if (!hasScope && !hasEntitlement ) "User and Customer both are "  else if (!hasEntitlement) "User is "  else if (!hasScope) "Customer is " else ""
      errorMessage = s"${UserHasMissingRoles.replace("User is ",differentErrorMessages)}$role"
      
      _ <- Helper.booleanToBox(hasBoth, errorMessage)
      
    } yield{
      EntitlementAndScopeStatus(hasBoth=hasBoth, errorMessage = errorMessage)
    }
  }
  
  // Function checks does a user specified by a parameter userId has at least one role provided by a parameter roles at a bank specified by a parameter bankId
  // i.e. does user has assigned at least one role from the list
  // when roles is empty, that means no access control, treat as pass auth check
  def hasAtLeastOneEntitlement(bankId: String, userId: String, roles: List[ApiRole]): Boolean =
    roles.isEmpty || roles.exists(hasEntitlement(bankId, userId, _))


  // Function checks does a user specified by a parameter userId has all roles provided by a parameter roles at a bank specified by a parameter bankId
  // i.e. does user has assigned all roles from the list
  // when roles is empty, that means no access control, treat as pass auth check
  // TODO Should we accept Option[BankId] for bankId  instead of String ?
  def hasAllEntitlements(bankId: String, userId: String, roles: List[ApiRole]): Boolean =
    roles.forall(hasEntitlement(bankId, userId, _))

  def getCustomers(ids: List[String]): List[Customer] = {
    val customers = {
      for {id <- ids
           c = CustomerX.customerProvider.vend.getCustomerByCustomerId(id)
           u <- c
      } yield {
        u
      }
    }
    customers
  }

  def getAutocompleteValue: String = {
    APIUtil.getPropsValue("autocomplete_at_login_form_enabled", "false") match {
      case "true"  => "on"
      case "false" => "off"
      case _       => "off"
    }
  }

  // check is there a "$" in the input value.
  // eg: MODULE$ is not the useful input.
  // eg2: allFieldsAndValues is just for SwaggerJSONsV220.allFieldsAndValues,it is not useful.
  def notExstingBaseClass(input: String): Boolean = {
    !input.contains("$") && !input.equalsIgnoreCase("allFieldsAndValues")
  }


  def saveConnectorMetric[R](blockOfCode: => R)(nameOfFunction: String = "")(implicit nameOfConnector: String): R = {
    val t0 = System.currentTimeMillis()
    // call-by-name
    val result = blockOfCode
    val t1 = System.currentTimeMillis()
    
    if (getPropsAsBoolValue("write_connector_metrics", false)){
      val correlationId = getCorrelationId()
      Future {
        ConnectorMetricsProvider.metrics.vend.saveConnectorMetric(nameOfConnector, nameOfFunction, correlationId, now, t1 - t0)
      }
    }
    result
  }

  def logEndpointTiming[R](callContext: Option[CallContextLight])(blockOfCode: => R): R = {
    val result = blockOfCode
    // call-by-name
    val endTime = Helpers.now
    callContext match {
      case Some(cc) =>
        val time = endTime.getTime() - cc.startTime.get.getTime()
        logger.info("Endpoint (" + cc.verb + ") " + cc.url + " returned " + cc.httpCode.getOrElse("xyz") + ", took " + time + " Milliseconds")
      case _ =>
        // There are no enough information for logging
    }
    logAPICall(callContext.map(_.copy(endTime = Some(endTime))))
    result
  }

  def akkaSanityCheck (): Box[Boolean] = {
    getPropsAsBoolValue("use_akka", false) match {
      case true =>
        val remotedataSecret = APIUtil.getPropsValue("remotedata.secret").openOrThrowException("Cannot obtain property remotedata.secret")
        SanityCheck.sanityCheck.vend.remoteAkkaSanityCheck(remotedataSecret)
      case false => Empty
    }


  }
  /**
    * The POST or PUT body.  This will be empty if the content
    * type is application/x-www-form-urlencoded or a multipart mime.
    * It will also be empty if rawInputStream is accessed
    */
  def getRequestBody(req: Box[Req]) = req.flatMap(_.body).map(_.map(_.toChar)).map(_.mkString)
  /**
    * @return - the HTTP session ID
    */
  def getCorrelationId(): String = S.containerSession.map(_.sessionId).openOr("")
  /**
    * @return - the remote address of the client or the last seen proxy.
    */
  def getRemoteIpAddress(): String = S.containerRequest.map(_.remoteAddress).openOr("Unknown")
  /**
    * @return - the fully qualified name of the client host or last seen proxy
    */
  def getRemoteHost(): String = S.containerRequest.map(_.remoteHost).openOr("Unknown")
  /**
    * @return - the source port of the client or last seen proxy.
    */
  def getRemotePort(): Int = S.containerRequest.map(_.remotePort).openOr(0)
  /**
    * @return - the server port
    */
  def getServerPort(): Int = S.containerRequest.map(_.serverPort).openOr(0)
  /**
    * @return - the host name of the server
    */
  def getServerName(): String = S.containerRequest.map(_.serverName).openOr("Unknown")

  /**
    * Defines Gateway Custom Response Header.
    */
  val gatewayResponseHeaderName = "GatewayLogin"
  /**
    * Set value of Gateway Custom Response Header.
    */
  def setGatewayResponseHeader(s: S)(value: String) = s.setSessionAttribute(gatewayResponseHeaderName, value)
  /**
    * @return - Gateway Custom Response Header.
    */
  def getGatewayResponseHeader() = {
    S.getSessionAttribute(gatewayResponseHeaderName) match {
      case Full(h) => List((gatewayResponseHeaderName, h))
      case _ => Nil
    }
  }
  def getGatewayLoginJwt(): Option[String] = {
    getGatewayResponseHeader() match {
      case x :: Nil =>
        Some(x._2)
      case _ =>
        None
    }
  }

  /**
    * Turn a string of format "FooBar" into snake case "foo_bar"
    *
    * Note: snakify is not reversible, ie. in general the following will _not_ be true:
    *
    * s == camelify(snakify(s))
    *
    * @return the underscored JValue
    */
  def snakify(json: JValue): JValue = json mapField {
    //IBAN is a speical value in bank, should not be convert to iban
    case JField("IBAN", x) => JField("IBAN", x)
    case JField(name, x) => JField(StringHelpers.snakify(name), x)
  }


  /**
    * Turns a string of format "foo_bar" into camel case "FooBar"
    *
    * Functional code courtesy of Jamie Webb (j@jmawebb.cjb.net) 2006/11/28
    * @param json the JValue to CamelCase
    *
    * @return the CamelCased JValue
    */
  def camelify(json: JValue): JValue = json mapField {
    case JField(name, x) => JField(StringHelpers.camelify(name), x)
  }

  /**
    * Turn a string of format "foo_bar" into camel case with the first letter in lower case: "fooBar"
    * This function is especially used to camelCase method names.
    *
    * @param json the JValue to CamelCase
    *
    * @return the CamelCased JValue
    */
  def camelifyMethod(json: JValue): JValue = json mapField {
    case JField(name, x) => JField(StringHelpers.camelifyMethod(name), x)
  }

  /**
    * Turn a string which is in OBP format into ISO20022 formatting
    *
    * @param json the JValue
    *
    * @return the JValue
    */
  def useISO20022Spelling(json: JValue): JValue = json transformField {
    case JField("currency", x) => JField("ccy", x)
  }

  /**
    * Turn a string which is in ISO20022 format into OBP formatting
    *
    * @param json the JValue
    *
    * @return the JValue
    */
  def useOBPSpelling(json: JValue): JValue = json transformField {
    case JField("ccy", x) => JField("currency", x)
  }

  def getDisabledVersions() : List[String] = APIUtil.getPropsValue("api_disabled_versions").getOrElse("").replace("[", "").replace("]", "").split(",").toList.filter(_.nonEmpty)

  def getDisabledEndpoints() : List[String] = APIUtil.getPropsValue("api_disabled_endpoints").getOrElse("").replace("[", "").replace("]", "").split(",").toList.filter(_.nonEmpty)



  def getEnabledVersions() : List[String] = APIUtil.getPropsValue("api_enabled_versions").getOrElse("").replace("[", "").replace("]", "").split(",").toList.filter(_.nonEmpty)

  def getEnabledEndpoints() : List[String] = APIUtil.getPropsValue("api_enabled_endpoints").getOrElse("").replace("[", "").replace("]", "").split(",").toList.filter(_.nonEmpty)

  def stringToDate(value: String, dateFormat: String): Date = {
    import java.text.SimpleDateFormat
    val format = new SimpleDateFormat(dateFormat)
    format.setLenient(false)
    format.parse(value)
  }
  def validatePhoneNumber(number: String): Boolean = {
    number.toList match {
      case x :: _ if x != '+' => false // First char has to be +
      case _ :: xs if xs.size > 15 => false // Number of digits has to be up to 15
      case _ :: xs if xs.size < 5  => false // Minimal number of digits is 5
      case _ :: xs if xs.exists(c => Character.isDigit(c) == false) => false // Ony digits are allowed
      case _ => true

    }
  }
  
  def isFirst(isFirst: String): Boolean = {
    isFirst.equalsIgnoreCase("true")
  }
  /*
  Determine if a version should be allowed.

    For a VERSION to be allowed it must be:

    1) Absent from Props api_disabled_versions
    2) Present here (api_enabled_versions=[v2_2_0,v3_0_0]) -OR- api_enabled_versions must be empty.

    Note we use "v" and "_" in the name to match the ApiVersions enumeration in ApiUtil.scala
   */
  def versionIsAllowed(version: ApiVersion) : Boolean = {
    def checkVersion: Boolean = {
      val disabledVersions: List[String] = getDisabledVersions()
      val enabledVersions: List[String] = getEnabledVersions()
      if (
        !disabledVersions.contains(version.toString) &&
          // Enabled versions or all
          (enabledVersions.contains(version.toString) || enabledVersions.isEmpty)
      ) true
      else
        false
    }
    APIUtil.getPropsValue("server_mode", "apis,portal") match {
      case mode if mode == "portal" => false
      case mode if mode == "apis" => checkVersion
      case mode if mode.contains("apis") && mode.contains("portal") => checkVersion
      case _ => checkVersion
    }
  }


  /*
  If a version is allowed, enable its endpoints.
  Note a version such as v3_0_0.OBPAPI3_0_0 may well include routes from other earlier versions.
   */

  def enableVersionIfAllowed(version: ApiVersion) : Boolean = {
    val allowed: Boolean = if (versionIsAllowed(version)
    ) {
      version match {
//        case ApiVersion.v1_0 => LiftRules.statelessDispatch.append(v1_0.OBPAPI1_0)
//        case ApiVersion.v1_1 => LiftRules.statelessDispatch.append(v1_1.OBPAPI1_1)
//        case ApiVersion.v1_2 => LiftRules.statelessDispatch.append(v1_2.OBPAPI1_2)
        // Can we depreciate the above?
        case ApiVersion.v1_2_1 => LiftRules.statelessDispatch.append(v1_2_1.OBPAPI1_2_1)
        case ApiVersion.v1_3_0 => LiftRules.statelessDispatch.append(v1_3_0.OBPAPI1_3_0)
        case ApiVersion.v1_4_0 => LiftRules.statelessDispatch.append(v1_4_0.OBPAPI1_4_0)
        case ApiVersion.v2_0_0 => LiftRules.statelessDispatch.append(v2_0_0.OBPAPI2_0_0)
        case ApiVersion.v2_1_0 => LiftRules.statelessDispatch.append(v2_1_0.OBPAPI2_1_0)
        case ApiVersion.v2_2_0 => LiftRules.statelessDispatch.append(v2_2_0.OBPAPI2_2_0)
        case ApiVersion.v3_0_0 => LiftRules.statelessDispatch.append(v3_0_0.OBPAPI3_0_0)
        case ApiVersion.v3_1_0 => LiftRules.statelessDispatch.append(v3_1_0.OBPAPI3_1_0)
        case ApiVersion.v4_0_0 => LiftRules.statelessDispatch.append(v4_0_0.OBPAPI4_0_0)
        case ApiVersion.`apiBuilder` => LiftRules.statelessDispatch.append(OBP_APIBuilder)
        case ApiVersion.sandbox => LiftRules.statelessDispatch.append(SandboxApiCalls)
        case version: ScannedApiVersion => LiftRules.statelessDispatch.append(ScannedApis.versionMapScannedApis(version))
        case _ => logger.info(s"There is no ${version.toString}")
      }

      logger.info(s"${version.toString} was ENABLED")

      true
    } else {
      logger.info(s"${version.toString} was NOT enabled")
      false
    }
    allowed
  }


  type OBPEndpoint = PartialFunction[Req, CallContext => Box[JsonResponse]]
  type OBPReturnType[T] = Future[(T, Option[CallContext])]


  def getAllowedEndpoints (endpoints : Iterable[OBPEndpoint], resourceDocs: ArrayBuffer[ResourceDoc]) : List[OBPEndpoint] = {

    // Endpoints
    val disabledEndpoints = getDisabledEndpoints

    // Endpoints
    val enabledEndpoints = getEnabledEndpoints

    val onlyNewStyle = APIUtil.getPropsAsBoolValue("new_style_only", false)


    val routes = for (
      item <- resourceDocs
         if
           // Remove any Resource Doc / endpoint mentioned in Disabled endpoints in Props
           !disabledEndpoints.contains(item.partialFunctionName) &&
           // Only allow Resrouce Doc / endpoints mentioned in enabled endpoints - unless none are mentioned in which case ignore.
           (enabledEndpoints.contains(item.partialFunctionName) || enabledEndpoints.isEmpty)  &&
           // Only allow Resource Doc if it matches one of the pre selected endpoints from the version list.
             // i.e. this function may recieve more Resource Docs than version endpoints
            endpoints.exists(_ == item.partialFunction) &&
             (item.tags.exists(_ == apiTagNewStyle) || !onlyNewStyle)
    )
      yield item.partialFunction
    routes.toList
    }

  def extractToCaseClass[T](in: String)(implicit ev: Manifest[T]): Box[T] = {
    try {
      val parseJValue: JValue = parse(in)
      val t: T = parseJValue.extract[T]
      Full(t)
    } catch {
      case m: ParseException =>
        logger.error("String-->Jvalue parse error"+in,m)
        Failure("String-->Jvalue parse error"+in+m.getMessage)
      case m: MappingException =>
        logger.error("JValue-->CaseClass extract error"+in,m)
        Failure("JValue-->CaseClass extract error"+in+m.getMessage)
      case m: Throwable =>
        logger.error("extractToCaseClass unknow error"+in,m)
        Failure("extractToCaseClass unknow error"+in+m.getMessage)
    }
  }

  def scalaFutureToLaFuture[T](scf: Future[T])(implicit m: Manifest[T]): LAFuture[T] = {
    val laf = new LAFuture[T]
    scf.onSuccess {
      case v: T => laf.satisfy(v)
      case _ => laf.abort
    }
    scf.onFailure {
      case e: Throwable => laf.fail(Failure(e.getMessage(), Full(e), Empty))
    }
    laf
  }


  def extractAPIFailureNewStyle(msg: String): Option[APIFailureNewStyle] = {
    try {
      parse(msg).extractOpt[APIFailureNewStyle] match {
        case Some(af) =>
          Some(af)
        case _ =>
          None
      }
    } catch {
      case _: Exception =>
        None
    }
  }

  /**
    * @param in LAFuture with a useful payload. Payload is tuple(Case Class, Option[SessionContext])
    * @return value of type JsonResponse
    *
    * Process a request asynchronously. The thread will not
    * block until there's a response.  The parameter is a function
    * that takes a function as it's parameter.  The function is invoked
    * when the calculation response is ready to be rendered:
    * RestContinuation.async {
    *   reply => {
    *     myActor ! DoCalc(123, answer => reply{XmlResponse(<i>{answer}</i>)})
    *   }
    * }
    * The body of the function will be executed on a separate thread.
    * When the answer is ready, apply the reply function... the function
    * body will be executed in the scope of the current request (the
    * current session and the current Req object).
    */
  def futureToResponse[T](in: LAFuture[(T, Option[CallContext])]): JsonResponse = {
    RestContinuation.async(reply => {
      in.onSuccess(
        t => logEndpointTiming(t._2.map(_.toLight))(reply.apply(successJsonResponseNewStyle(cc = t._1, t._2)(getHeadersNewStyle(t._2.map(_.toLight)))))
      )
      in.onFail {
        case Failure(msg, _, _) =>
          extractAPIFailureNewStyle(msg) match {
            case Some(af) =>
              val callContextLight = af.ccl.map(_.copy(httpCode = Some(af.failCode)))
              logEndpointTiming(callContextLight)(reply.apply(errorJsonResponse(af.failMsg, af.failCode)(getHeadersNewStyle(af.ccl))))
            case _ =>
              val errorResponse: JsonResponse = errorJsonResponse(msg)
              reply.apply(errorResponse)
          }
        case _                  =>
          val errorResponse: JsonResponse = errorJsonResponse(UnknownError)
          reply.apply(errorResponse)
      }
    })
  }


  /**
    * @param in LAFuture with a useful payload. Payload is tuple(Case Class, Option[SessionContext])
    * @return value of type Box[JsonResponse]
    *
    * Process a request asynchronously. The thread will not
    * block until there's a response.  The parameter is a function
    * that takes a function as it's parameter.  The function is invoked
    * when the calculation response is ready to be rendered:
    * RestContinuation.async {
    *   reply => {
    *     myActor ! DoCalc(123, answer => reply{XmlResponse(<i>{answer}</i>)})
    *   }
    * }
    * The body of the function will be executed on a separate thread.
    * When the answer is ready, apply the reply function... the function
    * body will be executed in the scope of the current request (the
    * current session and the current Req object).
    */
  def futureToBoxedResponse[T](in: LAFuture[(T, Option[CallContext])]): Box[JsonResponse] = {
    RestContinuation.async(reply => {
      in.onSuccess{ _ match {
          case (Full(jsonResponse: JsonResponse), _: Option[_]) =>
            reply(jsonResponse)
          case t => Full(logEndpointTiming(t._2.map(_.toLight))(reply.apply(successJsonResponseNewStyle(t._1, t._2)(getHeadersNewStyle(t._2.map(_.toLight))))))
        }
      }
      in.onFail {
        case Failure("Continuation", Full(e), _) if e.isInstanceOf[LiftFlowOfControlException] =>
          val f: ((=> LiftResponse) => Unit) => Unit = ReflectUtils.getFieldByType(e, "f")
              f(reply(_))

        case Failure(null, e, _) =>
          e.foreach(logger.error("", _))
          val errorResponse: JsonResponse = errorJsonResponse(UnknownError)
          Full(reply.apply(errorResponse))
        case Failure(msg, e, _) =>
          e.foreach(logger.error("", _))
          extractAPIFailureNewStyle(msg) match {
            case Some(af) =>
              val callContextLight = af.ccl.map(_.copy(httpCode = Some(af.failCode)))
              Full(logEndpointTiming(callContextLight)(reply.apply(errorJsonResponse(af.failMsg, af.failCode)(getHeadersNewStyle(af.ccl)))))
            case _ =>
              val errorResponse: JsonResponse = errorJsonResponse(msg)
              Full((reply.apply(errorResponse)))
          }
        case _ =>
          val errorResponse: JsonResponse = errorJsonResponse(UnknownError)
          Full(reply.apply(errorResponse))
      }
    })
  }

  implicit def scalaFutureToJsonResponse[T](scf: OBPReturnType[T])(implicit m: Manifest[T]): JsonResponse = {
    futureToResponse(scalaFutureToLaFuture(scf))
  }

  /**
    * This function is implicitly used at Endpoints to transform a Scala Future to Box[JsonResponse] for instance next part of code
    * for {
        users <- Future { someComputation }
      } yield {
        users
      }
      will be translated by Scala compiler to
      APIUtil.scalaFutureToBoxedJsonResponse(
        for {
          users <- Future { someComputation }
        } yield {
          users
        }
      )
    * @param scf
    * @param m
    * @tparam T
    * @return
    */
  implicit def scalaFutureToBoxedJsonResponse[T](scf: OBPReturnType[T])(implicit m: Manifest[T]): Box[JsonResponse] = {
    futureToBoxedResponse(scalaFutureToLaFuture(scf))
  }


  /**
    * This function is planed to be used at an endpoint in order to get a User based on Authorization Header data
    * It has to do the same thing as function OBPRestHelper.failIfBadAuthorizationHeader does
    * The only difference is that this function use Akka's Future in non-blocking way i.e. without using Await.result
    * @return A Tuple of an User wrapped into a Future and optional session context data
    */
  def getUserAndSessionContextFuture(cc: CallContext): OBPReturnType[Box[User]] = {
    val s = S
    val spelling = getSpellingParam()
    val body: Box[String] = getRequestBody(S.request)
    val implementedInVersion = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).view
    val verb = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).requestType.method
    val url = URLDecoder.decode(S.uriAndQueryString.getOrElse(""),"UTF-8")
    val correlationId = getCorrelationId()
    val reqHeaders = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).request.headers
    val remoteIpAddress = getRemoteIpAddress()
    val res =
    if (APIUtil.hasConsentId(reqHeaders)) {
      Consent.applyRules(APIUtil.getConsentId(reqHeaders), cc) 
    } else if (hasAnOAuthHeader(cc.authReqHeaderField)) {
      getUserFromOAuthHeaderFuture(cc)
    } else if (hasAnOAuth2Header(cc.authReqHeaderField)) {
      for {
        (user, callContext) <- OAuth2Login.getUserFuture(cc)
      } yield {
        if (!APIUtil.isSandboxMode && user.isDefined) 
          AuthUser.updateUserAccountViewsFuture(user.openOrThrowException("Can not be empty here"), callContext)
        (user, callContext)
      }
    } else if (getPropsAsBoolValue("allow_direct_login", true) && hasDirectLoginHeader(cc.authReqHeaderField)) {
      DirectLogin.getUserFromDirectLoginHeaderFuture(cc)
    } else if (getPropsAsBoolValue("allow_gateway_login", false) && hasGatewayHeader(cc.authReqHeaderField)) {
      APIUtil.getPropsValue("gateway.host") match {
        case Full(h) if h.split(",").toList.exists(_.equalsIgnoreCase(remoteIpAddress) == true) => // Only addresses from white list can use this feature
          val (httpCode, message, parameters) = GatewayLogin.validator(s.request)
          httpCode match {
            case 200 =>
              val payload = GatewayLogin.parseJwt(parameters)
              payload match {
                case Full(payload) =>
                  GatewayLogin.getOrCreateResourceUserFuture(payload: String, Some(cc)) map {
                    case Full((u, cbsToken, callContext)) => // Authentication is successful
                      val consumer = GatewayLogin.getOrCreateConsumer(payload, u)
                      val jwt = GatewayLogin.createJwt(payload, cbsToken)
                      val callContextUpdated = ApiSession.updateCallContext(GatewayLoginResponseHeader(Some(jwt)), callContext)
                      (Full(u), callContextUpdated.map(_.copy(consumer=consumer, user = Full(u))))
                    case Failure(msg, t, c) =>
                      (Failure(msg, t, c), None)
                    case _ =>
                      (Failure(payload), None)
                  }
                case Failure(msg, t, c) =>
                  Future { (Failure(msg, t, c), None) }
                case _ =>
                  Future { (Failure(ErrorMessages.GatewayLoginUnknownError), None) }
              }
            case _ =>
              Future { (Failure(message), None) }
          }
        case Full(h) if h.split(",").toList.exists(_.equalsIgnoreCase(remoteIpAddress) == false) => // All other addresses will be rejected
          Future { (Failure(ErrorMessages.GatewayLoginWhiteListAddresses), None) }
        case Empty =>
          Future { (Failure(ErrorMessages.GatewayLoginHostPropertyMissing), None) } // There is no gateway.host in props file
        case Failure(msg, t, c) =>
          Future { (Failure(msg, t, c), None) }
        case _ =>
          Future { (Failure(ErrorMessages.GatewayLoginUnknownError), None) }
      }
    } else if(Option(cc).flatMap(_.user).isDefined) {
      Future{(cc.user, Some(cc))}
    }
    else {
      Future { (Empty, None) }
    }
    
    

    /******************************************************************************************************************
      * This block of code needs to update Call Context with Rate Limiting
      * Please note that first source is the table RateLimiting and second the table Consumer
      */
    def getRateLimiting(consumerId: String): Future[Box[RateLimiting]] = {
      RateLimitingUtil.useConsumerLimits match {
        case true => RateLimitingDI.rateLimiting.vend.getByConsumerId(consumerId, Some(new Date()))
        case false => Future(Empty)
      }
    }
    val resultWithRateLimiting: Future[(Box[User], Option[CallContext])] = for {
      (user, cc) <- res
      consumer = cc.flatMap(_.consumer)
      rateLimiting <- getRateLimiting(consumer.map(_.consumerId.get).getOrElse(""))
    } yield {
      val limit: Option[CallLimit] = rateLimiting match {
        case Full(rl) => Some(CallLimit(
          rl.consumerId,
          None,
          None,
          None,
          rl.perSecondCallLimit,
          rl.perMinuteCallLimit,
          rl.perHourCallLimit,
          rl.perDayCallLimit,
          rl.perWeekCallLimit,
          rl.perMonthCallLimit))
        case Empty =>
          Some(CallLimit(
            consumer.map(_.consumerId.get).getOrElse(""),
            None,
            None,
            None,
            consumer.map(_.perSecondCallLimit.get).getOrElse(-1),
            consumer.map(_.perMinuteCallLimit.get).getOrElse(-1),
            consumer.map(_.perHourCallLimit.get).getOrElse(-1),
            consumer.map(_.perDayCallLimit.get).getOrElse(-1),
            consumer.map(_.perWeekCallLimit.get).getOrElse(-1),
            consumer.map(_.perMonthCallLimit.get).getOrElse(-1)
          ))
        case _ => None 
      }
      (user, cc.map(_.copy(rateLimiting = limit)))
    }
    /*************************************************************************************************************** */

      // Update Call Context
    resultWithRateLimiting map {
      x => (x._1, ApiSession.updateCallContext(Spelling(spelling), x._2))
    } map {
      x => (x._1, x._2.map(_.copy(implementedInVersion = implementedInVersion)))
    } map {
      x => (x._1, x._2.map(_.copy(verb = verb)))
    } map {
      x => (x._1, x._2.map(_.copy(url = url)))
    } map {
      x => (x._1, x._2.map(_.copy(correlationId = correlationId)))
    } map {
      x => (x._1, x._2.map(_.copy(requestHeaders = reqHeaders)))
    } map {
      x => (x._1, x._2.map(_.copy(ipAddress = remoteIpAddress)))
    }  map {
      x => (x._1, x._2.map(_.copy(httpBody = body.toOption)))
    } 
    
  }

  /**
    * This Function is used to terminate a Future used in for-comprehension with specific message and code in case that value of Box is not Full.
    * For example:
    *  - Future(Full("Some value")    -> Does NOT terminate
    *  - Future(Empty)                -> Terminates
    *  - Future(Failure/ParamFailure) -> Terminates
    * @param box Boxed Payload
    * @param cc Call Context
    * @param emptyBoxErrorMsg Error message in case of Empty Box
    * @param emptyBoxErrorCode Error code in case of Empty Box
    * @return
    */
  def getFullBoxOrFail[T](box: Box[T], cc: Option[CallContext], emptyBoxErrorMsg: String = "", emptyBoxErrorCode: Int = 400)(implicit m: Manifest[T]): Box[T] = {
    fullBoxOrException(box ~> APIFailureNewStyle(emptyBoxErrorMsg, emptyBoxErrorCode, cc.map(_.toLight)))
  }

  def unboxFullOrFail[T](box: Box[T], cc: Option[CallContext], emptyBoxErrorMsg: String = "", emptyBoxErrorCode: Int = 400)(implicit m: Manifest[T]): T = {
    unboxFull {
      fullBoxOrException(box ~> APIFailureNewStyle(emptyBoxErrorMsg, emptyBoxErrorCode, cc.map(_.toLight)))
    }
  }
  
  def connectorEmptyResponse[T](box: Box[T], cc: Option[CallContext])(implicit m: Manifest[T]): T = {
    unboxFullOrFail(box, cc, InvalidConnectorResponse, 400)
  }

  def unboxFuture[T](box: Box[Future[T]]): Future[Box[T]] = box match {
    case Full(v) => v.map(Box !! _)
    case other => Future(other.asInstanceOf[Box[T]])
  }

  def unboxOBPReturnType[T](box: Box[OBPReturnType[T]]): Future[Box[T]] = box match {
    case Full(v) => v.map(Box !! _._1)
    case other => Future(other.asInstanceOf[Box[T]])
  }

  def unboxOptionFuture[T](option: Option[Future[T]]): Future[Box[T]] = unboxFuture(Box(option))

  def unboxOptionOBPReturnType[T](option: Option[OBPReturnType[T]]): Future[Box[T]] = unboxOBPReturnType(Box(option))
  

  /**
    * This function is used to factor out common code at endpoints regarding Authorized access
    * @param emptyUserErrorMsg is a message which will be provided as a response in case that Box[User] = Empty
    */
  def authorizedAccess(cc: CallContext, emptyUserErrorMsg: String = UserNotLoggedIn): OBPReturnType[Box[User]] = {
    anonymousAccess(cc) map {
      x => (fullBoxOrException(
        x._1 ~> APIFailureNewStyle(emptyUserErrorMsg, 400, Some(cc.toLight))),
        x._2
      )
    }
  }

  /**
    * This function is used to introduce Rate Limit at an unauthorized endpoint
    * @param cc The call context of an request
    * @return Failure in case we exceeded rate limit
    */
  def anonymousAccess(cc: CallContext): Future[(Box[User], Option[CallContext])] = {
    getUserAndSessionContextFuture(cc) map {
      result => 
        val excludeFunctions = getPropsValue("rate_limiting.exclude_endpoints", "root").split(",").toList
        cc.resourceDocument.map(_.partialFunctionName) match {
          case Some(functionName) if excludeFunctions.exists(_ == functionName) => result
          case _ => RateLimitingUtil.underCallLimits(result)
        }
    }
  }

  def filterMessage(obj: Failure): String = {
    logger.debug("Failure: " + obj)

    def messageIsNotNull(x: Failure, obj: Failure) = {
      if (x.msg != null) true else { logger.info("Failure: " + obj); false }
    }

    // Remove duplicated content, because in the process of box, the FailBox will be wrapped may multiple times, and message is same.
    getPropsAsBoolValue("display_internal_errors", false) match {
      case true => // Show all error in a chain
        obj.messageChain.split(" <- ").distinct.mkString(" <- ")
      case false => // Do not display internal errors
        val obpFailures = obj.failureChain.filter(x => messageIsNotNull(x, obj) && x.msg.startsWith("OBP-"))
        obpFailures match {
          case Nil => ErrorMessages.AnUnspecifiedOrInternalErrorOccurred
          case _ => obpFailures.map(_.msg).distinct.mkString(" <- ")
        }
    }

  }

  /**
    * This Function is used to terminate a Future used in for-comprehension with specific message
    * Please note that boxToFailed(Empty ?~ ("Some failure message")) will be transformed to Failure("Some failure message", Empty, Empty)
    * @param box Some boxed type
    * @return Boxed value or throw some exception
    */
  def fullBoxOrException[T](box: Box[T]) : Box[T]= {
    box match {
      case Full(v) => // Just forwarding
        Full(v)
      case Empty => // Just forwarding
        throw new Exception("Empty Box not allowed")
      case obj1@ParamFailure(m,e,c,af: APIFailureNewStyle) =>
        val obj = (m,e, c) match {
          case ("", Empty, Empty) => Empty ?~! af.failMsg
          case _ => Failure (m, e, c) ?~! af.failMsg
        }
        val failuresMsg = filterMessage(obj)
        val callContext = af.ccl.map(_.copy(httpCode = Some(af.failCode)))
        val apiFailure = af.copy(failMsg = failuresMsg).copy(ccl = callContext)
        throw new Exception(JsonAST.compactRender(Extraction.decompose(apiFailure)))
      case ParamFailure(msg,_,_,_) =>
        throw new Exception(msg)
      case obj@Failure(msg, _, c) =>
        val failuresMsg = filterMessage(obj)
        throw new Exception(failuresMsg)
      case _ =>
        throw new Exception(UnknownError)
    }
  }

  def unboxFullAndWrapIntoFuture[T](box: Box[T])(implicit m: Manifest[T]) : Future[T] = {
    Future {
      unboxFull(fullBoxOrException(box))
    }
  }

  def unboxFull[T](box: Box[T])(implicit m: Manifest[T]) : T = {
    box match {
      case Full(value) =>
        value
      case _ =>
        throw new Exception("Only Full Box is allowed at function unboxFull")
    }
  }

  /**
    * This method is used for cache in connector level.
    * eg: KafkaMappedConnector_vJune2017.bankTTL
    * The default cache time unit is second.
    */
  def getSecondsCache(cacheType: String) : Int = {
    if(cacheType =="getOrCreateMetadata")
      APIUtil.getPropsValue(s"MapperCounterparties.cache.ttl.seconds.getOrCreateMetadata", "3600").toInt  // 3600s --> 1h
    else
      APIUtil.getPropsValue(s"connector.cache.ttl.seconds.$cacheType", "0").toInt
  }

  /**
    * Normally, we create the AccountId, BankId automatically in database. Because they are the UUIDString in the table.
    * We also can create the Id manually.
    * eg: CounterpartyId, because we use this Id both for Counterparty and counterpartyMetaData by some input fields.
    */
  def createOBPId(in:String)= {
    import java.security.MessageDigest

    import net.liftweb.util.SecurityHelpers._
    def base64EncodedSha256(in: String) = base64EncodeURLSafe(MessageDigest.getInstance("SHA-256").digest(in.getBytes("UTF-8"))).stripSuffix("=")

    base64EncodedSha256(in)
  }

  /**
    *  Create the explicit CounterpartyId, (Used in `Create counterparty for an account` endpoint ).
    *  This is just a UUID, use both in Counterparty.counterpartyId and CounterpartyMetadata.counterpartyId
    */
  def createExplicitCounterpartyId()= generateUUID()

  /**
    * Create the implicit CounterpartyId, we can only get limit data from Adapter. (Used in `getTransactions` endpoint, we create the counterparty implicitly.)
    * Note: The caller should take care of the `counterpartyName`,it depends how you get the data from transaction. and can generate the `counterpartyName`
    * 2018-07-18: We need more fields to identify the implicitCounterpartyId, only counterpartyName is not enough.
    *             If some connectors only return limit data, the caller, need decide what kind of data to map here.
    *
    */
  def createImplicitCounterpartyId(
    thisBankId: String,
    thisAccountId : String,
    counterpartyName: String,
    otherAccountRoutingScheme: String,
    otherAccountRoutingAddress: String
  )= createOBPId(s"$thisBankId$thisAccountId$counterpartyName$otherAccountRoutingScheme$otherAccountRoutingAddress")

  val isSandboxMode: Boolean = (APIUtil.getPropsValue("connector").openOrThrowException(attemptedToOpenAnEmptyBox).toString).equalsIgnoreCase("mapped")
  
  //If we use kafka connector, we need set up kafka server first. For some cases(eg: get Kafka MessageDoc), we do not need kafka.. 
  val isStarConnectorButNoKafkaSupport: Boolean = 
    (APIUtil.getPropsValue("connector").openOrThrowException(attemptedToOpenAnEmptyBox).toString).equalsIgnoreCase("star") && (!(APIUtil.getPropsValue("starConnector_supported_types").openOrThrowException(attemptedToOpenAnEmptyBox).toString).contains("kafka"))

  /**
    * This function is implemented in order to support encrypted values in props file.
    * Please note that some value is considered as encrypted if has an encryption mark property in addition to regular props value in props file e.g
    *  db.url=Helpers.base64Encode(SOME_ENCRYPTED_VALUE)
    *  db.url.is_encrypted=true
    *  getDecryptedPropsValue("db.url") = jdbc:postgresql://localhost:5432/han_obp_api_9?user=han_obp_api&password=mypassword
    *  Encrypt/Decrypt workflow:
    *  Encrypt: Array[Byte] -> Helpers.base64Encode(encrypted) -> Props file: String -> Helpers.base64Decode(encryptedValue) -> Decrypt: Array[Byte]
    * @param nameOfProperty Name of property which value should be decrypted
    * @return Decrypted value of a property
    */
  def getPropsValue(nameOfProperty: String): Box[String] = {

    val brandSpecificPropertyName = getBrandSpecificPropertyName(nameOfProperty)

    //Note: this property name prefix is only used for system environment, not for Liftweb props.
    val sysEnvironmentPropertyNamePrefix = Props.get("system_environment_property_name_prefix").openOr("OBP_")
    //All the property will first check from system environment, if not find then from the liftweb props file 
    //Replace "." with "_" (environment vars cannot include ".") and convert to upper case
    // Append "OBP_" because all Open Bank Project environment vars are namespaced with OBP
    val sysEnvironmentPropertyName = sysEnvironmentPropertyNamePrefix.concat(brandSpecificPropertyName.replace('.', '_').toUpperCase())
    val sysEnvironmentPropertyValue: Box[String] = tryo{sys.env(sysEnvironmentPropertyName)}
    sysEnvironmentPropertyValue match {
      case Full(_) => sysEnvironmentPropertyValue
      case _  =>
        (Props.get(brandSpecificPropertyName), Props.get(brandSpecificPropertyName + ".is_encrypted"), Props.get(brandSpecificPropertyName + ".is_obfuscated") ) match {
          case (Full(base64PropsValue), Full(isEncrypted), Empty)  if isEncrypted == "true" =>
            val decryptedValueAsString = RSAUtil.decrypt(base64PropsValue)
            Full(decryptedValueAsString)
          case (Full(property), Full(isEncrypted), Empty)  if isEncrypted == "false" =>
            Full(property)
          case (Full(property),Empty, Full(isObfuscated)) if isObfuscated == "true" =>
            Full(org.eclipse.jetty.util.security.Password.deobfuscate(property))
          case (Full(property),Empty, Full(isObfuscated)) if isObfuscated == "false" =>
            Full(property)
          case (Full(property), Empty,Empty) =>
            Full(property)
          case (Empty, Empty, Empty) =>
            Empty
          case _ =>
            logger.error(cannotDecryptValueOfProperty + brandSpecificPropertyName)
            Failure(cannotDecryptValueOfProperty + brandSpecificPropertyName)
        }
    }
  } 
  def getPropsValue(nameOfProperty: String, defaultValue: String): String = {
    getPropsValue(nameOfProperty) openOr(defaultValue)
  }

  def getPropsAsBoolValue(nameOfProperty: String, defaultValue: Boolean): Boolean = {
    getPropsValue(nameOfProperty) map(toBoolean) openOr(defaultValue)
  }
  def getPropsAsIntValue(nameOfProperty: String): Box[Int] = {
    getPropsValue(nameOfProperty) map(toInt)
  }
  def getPropsAsIntValue(nameOfProperty: String, defaultValue: Int): Int = {
    getPropsAsIntValue(nameOfProperty) openOr(defaultValue)
  }
  def getPropsAsLongValue(nameOfProperty: String): Box[Long] = {
    getPropsValue(nameOfProperty) flatMap(asLong)
  }
  def getPropsAsLongValue(nameOfProperty: String, defaultValue: Long): Long = {
    getPropsAsLongValue(nameOfProperty) openOr(defaultValue)
  }

/*
  Get any brand specified in url parameter or form field, validate it, and if all good, set the session
  Else just return the session
  Note there are Read and Write side effects here!
*/
  def activeBrand() : Option[String] = {

    val brandParameter = "brand"

    // Use brand in parameter (query or form)
    val brand : Option[String] = S.param(brandParameter) match {
      case Full(value) => {
        // If found, and has a valid format, set the session.
          if (isValidID(value)) {
            S.setSessionAttribute(brandParameter, value)
            logger.debug(s"activeBrand says: I found a $brandParameter param. $brandParameter session has been set to: ${S.getSessionAttribute(brandParameter)}")
            Some(value)
          } else {
            logger.warn (s"activeBrand says: ${ErrorMessages.InvalidBankIdFormat}")
            None
          }
      }
      case _ =>  {
        // Else look in the session
        S.getSessionAttribute(brandParameter)
      }
    }
    brand
  }


  /*
  For bank specific branding and possibly other customisations, if we have an active brand (in url param, form field, session),
  we will look for property_FOR_BRAND_<BANK_ID>
  We also check that the property exists, else return the standard property name.
  */
  def getBrandSpecificPropertyName(nameOfProperty: String) : String = {
    // If we have an active brand, construct a target property name to look for.
    val brandSpecificPropertyName = activeBrand() match {
      case Some(brand) => s"${nameOfProperty}_FOR_BRAND_${brand}"
      case _ => nameOfProperty
    }

    // Check if the property actually exits, if not, return the default / standard property name
    val propertyToUse = Props.get(brandSpecificPropertyName) match {
      case Full(value) => brandSpecificPropertyName
      case _ => nameOfProperty
    }

    propertyToUse
  }



  val ALLOW_PUBLIC_VIEWS: Boolean = getPropsAsBoolValue("allow_public_views", false)
  val ALLOW_FIREHOSE_VIEWS: Boolean = getPropsAsBoolValue("allow_firehose_views", false)
  def canUseFirehose(user: User): Boolean = {
    ALLOW_FIREHOSE_VIEWS && hasEntitlement("", user.userId, ApiRole.canUseFirehoseAtAnyBank)
  }
  /**
    * This will accept all kinds of view and user.
    * Depends on the public, private and firehose, check the different view access.

    * @param view view object,
    * @param user Option User, can be Empty(No Authentication), or Login user.
    *
    */
  def hasAccess(view: View, bankIdAccountId: BankIdAccountId, user: Option[User]) : Boolean = {
    if(isPublicView(view: View))// No need for the Login user and public access
      true
    else
      user match {
        case Some(u) if hasFirehoseAccess(view,u)  => true//Login User and Firehose access
        case Some(u) if u.hasAccountAccess(view, bankIdAccountId)=> true     // Login User and check view access
        case _ =>
          false
      }
  }
  /**
   * All the get view and check view access must be in one method, can not be separated from now. Because we introduce system views. 
   * 1st check: if `Custom View is existing` and `have the custom owner view access` ==>  return the customView.
   * 2rd check: if `Custom view is not find or have no custom owner view access` and `find system owner view` and `have the system owner view access` ==> then return systemView. 
   * all other cases ==>return no access to the `viewId`
   * 
   * Note: this user is Option, for the public views, there is no need for the user. 
   */
  final def checkViewAccessAndReturnView(viewId : ViewId, bankIdAccountId: BankIdAccountId, user: Option[User]) = {
    val customerViewImplBox = Views.views.vend.customView(viewId, bankIdAccountId)
    customerViewImplBox match {
      case Full(v) if(v.isPublic && !ALLOW_PUBLIC_VIEWS) => Failure(PublicViewsNotAllowedOnThisInstance)
      case Full(v) if(isPublicView(v)) => customerViewImplBox
      case Full(v) if(user.isDefined && user.get.hasAccountAccess(v, bankIdAccountId)) => customerViewImplBox
      case _ => Views.views.vend.systemView(viewId) match  {
        case Full(v) if (user.isDefined && user.get.hasAccountAccess(v, bankIdAccountId)) => Full(v)
        case _ => Empty
      }
    }
  }
  
  // TODO Use this in code as a single point of entry whenever we need to check owner view
  def isOwnerView(viewId: ViewId): Boolean = {
    viewId.value == SYSTEM_OWNER_VIEW_ID ||
    viewId.value == "_" + SYSTEM_OWNER_VIEW_ID || // New views named like this are forbidden from this commit
    viewId.value == CUSTOM_OWNER_VIEW_ID // New views named like this are forbidden from this commit
  }
  
  /**
    * This view public is true and set `allow_public_views=true` in props
    */
  def isPublicView(view: View) : Boolean = {
    isOwnerView(view.viewId) match {
      case true if view.isPublic => // Sanity check. We don't want a public owner view.
        logger.warn(s"Public owner encountered. Primary view id: ${view.id}")
        false 
      case _ => view.isPublic && APIUtil.ALLOW_PUBLIC_VIEWS
    }
  }
  /**
    * This view Firehose is true and set `allow_firehose_views = true` and the user has  `CanUseFirehoseAtAnyBank` role
    */
  def hasFirehoseAccess(view: View, user: User) : Boolean = {
    if(view.isFirehose && canUseFirehose(user)) true
    else false
  }

  /**
    *  This value is used to construct some urls in Resource Docs
    *  Its the root of the server as opposed to the root of the api
    */
  def getServerUrl: String = getPropsValue("documented_server_url").openOr(MissingPropsValueAtThisInstance + "documented_server_url")

  // All OBP REST end points start with /obp
  def getObpApiRoot: String = s"$getServerUrl/obp"

  // Get OAuth2 Authentication Server URL
  def getOAuth2ServerUrl: String = getPropsValue("oauth2_server_url").openOr(MissingPropsValueAtThisInstance + "oauth2_server_url")
  
  lazy val defaultBankId = 
    if (Props.mode == Props.RunModes.Test)
      APIUtil.getPropsValue("defaultBank.bank_id", "DEFAULT_BANK_ID_NOT_SET_Test")
    else
      APIUtil.getPropsValue("defaultBank.bank_id", "DEFAULT_BANK_ID_NOT_SET")
      
  //This method will read sample.props.template file, and get all the fields which start with the webui_
  //it will return the webui_ props paris: 
  //eg: List(("webui_get_started_text","Get started building your application using this sandbox now"),
  // ("webui_post_consumer_registration_more_info_text"," Please tell us more your Application and / or Startup using this link"))
  def getWebUIPropsPairs: List[(String, String)] = {
    val filepath = this.getClass.getResource("/props/sample.props.template").getPath
    val bufferedSource: BufferedSource = scala.io.Source.fromFile(filepath)

    val proPairs: List[(String, String)] = for{
      line <- bufferedSource.getLines.toList if(line.startsWith("webui_") || line.startsWith("#webui_"))
      webuiProps = line.toString.split("=", 2)
    } yield {
      val webuiPropsKey = webuiProps(0).trim.replaceAll("#","") //Remove the whitespace 
      val webuiPropsValue = if (webuiProps.length > 1) webuiProps(1).trim else ""
      (webuiPropsKey, webuiPropsValue)
    }
    bufferedSource.close()
    proPairs
  }

  /**
    * This function is used to centralize generation of UUID values
    * @return UUID as a String value
    */
  def generateUUID(): String = UUID.randomUUID().toString
  
  def mockedDataText(isMockedData: Boolean) = 
    if (isMockedData) 
      """**NOTE: This endpoint currently only returns example data.**
        |
      """.stripMargin
    else 
      """
        |
      """.stripMargin
  
  //This is used to change connector level Message Doc to api level ResouceDoc.
  //Because we already have the code from resouceDocs --> Swagger File.
  //Here we use the same code for MessageDoc, so we transfer them first.
  def toResourceDoc(messageDoc: MessageDoc): ResourceDoc = {
    val connectorMethodName = {messageDoc.process.replaceAll("obp.","").replace(".","")}
    ResourceDoc(
      null,
      ApiVersion.v3_1_0,
      connectorMethodName,
      requestVerb = {
        getRequestTypeByMethodName(connectorMethodName)
      },
      s"/obp-adapter/$connectorMethodName",
      messageDoc.description,
      messageDoc.description,
      messageDoc.exampleOutboundMessage,
      messageDoc.exampleInboundMessage,
      errorResponseBodies = List(InvalidJsonFormat),
      Catalogs(notCore,notPSD2,notOBWG),
      List(apiTagBank)
    )
  }

  def getRequestTypeByMethodName(connectorMethodName: String) = {
    connectorMethodName match {
        //TODO, liftweb do not support json body for get request. Here we used `post` first.
      case v if (v.matches("(get.+|check.+|.+Exists)") && !v.matches("(getOrCreate.+)")) => "post"
      case v if (v.matches("(getOrCreate|create|save|make|answer).+")) => "post"
      case v if (v.matches("(?i)(update|set).+")) => "post"
      case v if (v.matches("(delete|remove).+")) => "delete"
      case _ => "post"
    }
  }

  def createBasicUserAuthContext(userAuthContest : UserAuthContext) : BasicUserAuthContext = {
    BasicUserAuthContext(
      key = userAuthContest.key,
      value = userAuthContest.value
    )
  }
  
  def createBasicUserAuthContextJson(userAuthContexts : List[UserAuthContext]) : List[BasicUserAuthContext] = {
    userAuthContexts.map(createBasicUserAuthContext)
  }

  def createBasicUserAuthContextJsonFromCallContext(callContext : CallContext) : List[BasicGeneralContext] = {
    val requestHeaders: List[HTTPParam] = callContext.requestHeaders
    
    //Only these Pass-Through headers can be propagated to Adapter side. 
    val passThroughHeaders: List[HTTPParam] = requestHeaders
      .filter(requestHeader => requestHeader.name.startsWith("Pass-Through"))

    passThroughHeaders.map(header => BasicGeneralContext(
      key = header.name,
      value = if (header.values.isEmpty) "" else header.values.head))
  }
  
  def createAuthInfoCustomerJson(customer : Customer) : InternalBasicCustomer = {
    InternalBasicCustomer(
      bankId=customer.bankId,
      customerId = customer.customerId,
      customerNumber = customer.number,
      legalName = customer.legalName,
      dateOfBirth = customer.dateOfBirth
    )
  }
  
  def createAuthInfoCustomersJson(customers : List[Customer]) : InternalBasicCustomers = {
    InternalBasicCustomers(customers.map(createAuthInfoCustomerJson))
  }
  
  def createAuthInfoUserJson(user : User) : InternalBasicUser = {
    InternalBasicUser(
      user.userId,
      user.emailAddress,
      user.name,
    )
  }
  
  def createInternalLinkedBasicCustomerJson(customer : Customer) : BasicLinkedCustomer = {
    BasicLinkedCustomer(
      customerId = customer.customerId,
      customerNumber = customer.number,
      legalName = customer.legalName,
    )
  }
  
  def createAuthInfoUsersJson(users : List[User]) : InternalBasicUsers = {
    InternalBasicUsers(users.map(createAuthInfoUserJson))
  }
  
  def createInternalLinkedBasicCustomersJson(customers : List[Customer]) : List[BasicLinkedCustomer] = {
    customers.map(createInternalLinkedBasicCustomerJson)
  }

  /**
    * parse string to Date object, use the follow Format object to do parse:
    *   DateWithDayFormat, DateWithSecondsFormat, DateWithMsFormat, DateWithMsRollbackFormat
    * return the first parse success object
    * @param date date string to do parse
    * @return Some(Date) or None
    */
  def parseDate(date: String): Option[Date] = {
    val currentSupportFormats = List(DateWithDayFormat, DateWithSecondsFormat, DateWithMsFormat, DateWithMsRollbackFormat)
    val parsePosition = new ParsePosition(0)
    currentSupportFormats.toStream.map(_.parse(date, parsePosition)).find(null !=)
  }
  
  private def passesPsd2ServiceProviderCommon(cc: Option[CallContext], serviceProvider: String) = {
    val result: Box[Boolean] = getPropsAsBoolValue("requirePsd2Certificates", false) match {
      case false => Full(true)
      case true =>
        `getPSD2-CERT`(cc.map(_.requestHeaders).getOrElse(Nil)) match {
          case Some(pem) =>
            val validatedPem = X509.validate(pem)
            validatedPem match {
              case Full(true) =>
                val roles = X509.extractPsd2Roles(pem).map(_.exists(_ == serviceProvider))
                roles match {
                  case Full(true) => Full(true)
                  case Full(false) => Failure(X509ActionIsNotAllowed)
                  case _ => roles
                }
              case _ =>
                validatedPem
            }
          case None => Failure(X509CannotGetCertificate)
        }
    }
    result
  }

  def passesPsd2ServiceProvider(cc: Option[CallContext], serviceProvider: String): OBPReturnType[Box[Boolean]] = {
    val result = passesPsd2ServiceProviderCommon(cc, serviceProvider)
    Future(result) map {
      x => (fullBoxOrException(x ~> APIFailureNewStyle(X509GeneralError, 400, cc.map(_.toLight))), cc)
    }
  }
  def passesPsd2Aisp(cc: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
    passesPsd2ServiceProvider(cc, PemCertificateRole.PSP_AI.toString())
  }
  def passesPsd2Pisp(cc: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
    passesPsd2ServiceProvider(cc, PemCertificateRole.PSP_PI.toString())
  }
  def passesPsd2Icsp(cc: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
    passesPsd2ServiceProvider(cc, PemCertificateRole.PSP_IC.toString())
  }
  def passesPsd2Assp(cc: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
    passesPsd2ServiceProvider(cc, PemCertificateRole.PSP_AS.toString())
  }


  def passesPsd2ServiceProviderOldStyle(cc: Option[CallContext], serviceProvider: String): Box[Boolean] = {
    passesPsd2ServiceProviderCommon(cc, serviceProvider) ?~! X509GeneralError
  }
  def passesPsd2AispOldStyle(cc: Option[CallContext]): Box[Boolean] = {
    passesPsd2ServiceProviderOldStyle(cc, PemCertificateRole.PSP_AI.toString())
  }
  def passesPsd2PispOldStyle(cc: Option[CallContext]): Box[Boolean] = {
    passesPsd2ServiceProviderOldStyle(cc, PemCertificateRole.PSP_PI.toString())
  }
  def passesPsd2IcspOldStyle(cc: Option[CallContext]): Box[Boolean] = {
    passesPsd2ServiceProviderOldStyle(cc, PemCertificateRole.PSP_IC.toString())
  }
  def passesPsd2AsspOldStyle(cc: Option[CallContext]): Box[Boolean] = {
    passesPsd2ServiceProviderOldStyle(cc, PemCertificateRole.PSP_AS.toString())
  }
  
  
  
  def getMaskedPrimaryAccountNumber(accountNumber: String): String = {
    val (first, second) = accountNumber.splitAt(accountNumber.size/2)
    if(first.length >=3 && second.length>=3)
      first.substring(0, first.size - 3) + "***" + "***" + second.substring(3)
    else if (first.length >=3 && second.length< 3)
      first.substring(0, first.size - 3) + "***" + "***" + second
    else if (first.length <3 && second.length>= 3)
      first + "***" + "***" + second.substring(3)
    else
      first+ "***" + "***" + second
  }

  /**
    * This endpoint returns BIC on an bank according to next rules:
    * 1st try - Inspect bank routing
    * 2nd ry - get deprecated field "swiftBic"
    * @param bankId The BANK_ID specified at an endpoint's path
    * @return BIC of the Bank
    */
  def getBicFromBankId(bankId: String)= {
      Connector.connector.vend.getBankLegacy(BankId(bankId), None) match {
      case Full((bank, _)) =>
        bank.bankRoutingScheme match {
          case "BIC" => bank.bankRoutingAddress
          case _ => bank.swiftBic
        }
      case _ => ""
    }
  }

  /**
    * This function finds a phone number of an Customer in accordance to next rule:
    * - account -> holders -> User -> User Customer Links -> Customer.phone_number
    * @param bankId The BANK_ID
    * @param accountId The ACCOUNT_ID
    * @return The phone number of a Customer
    */
  def getPhoneNumbersForAccount(bankId: BankId, accountId: AccountId): List[(String, String)] = {
    for{
      holder <- AccountHolders.accountHolders.vend.getAccountHolders(bankId, accountId).toList
      userCustomerLink <- UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(holder.userId)
      customer <- CustomerX.customerProvider.vend.getCustomerByCustomerId(userCustomerLink.customerId)
    } yield {
      (customer.legalName, customer.mobileNumber)
    }
  }

  /**
    * This function finds the phone numbers of an Customer in accordance to next rule:
    * - User -> User Customer Links -> Customer.phone_number
    * @param userId The USER_ID
    * @return The phone numbers of a Customer
    */
  def getPhoneNumbersByUserId(userId: String): List[(String, String)] = {
    for{
      userCustomerLink <- UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(userId)
      customer <- CustomerX.customerProvider.vend.getCustomerByCustomerId(userCustomerLink.customerId)
    } yield {
      (customer.legalName, customer.mobileNumber)
    }
  }
  /**
    * This function finds the emails of an Customer in accordance to next rule:
    * - User -> User Customer Links -> Customer.email
    * @return The phone numbers of a Customer
    */
  def getEmailsByUserId(userId: String): List[(String, String)] = {
    for{
      userCustomerLink <- UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(userId)
      customer <- CustomerX.customerProvider.vend.getCustomerByCustomerId(userCustomerLink.customerId)
    } yield {
      (customer.legalName, customer.email)
    }
  }
  
  def getScaMethodAtInstance(transactionType: String): Full[SCA] = {
    val propsName = transactionType + "_OTP_INSTRUCTION_TRANSPORT"
    APIUtil.getPropsValue(propsName).map(_.toUpperCase()) match {
      case Full(sca) if sca == StrongCustomerAuthentication.DUMMY.toString() => Full(StrongCustomerAuthentication.DUMMY)
      case Full(sca) if sca == StrongCustomerAuthentication.SMS.toString() => Full(StrongCustomerAuthentication.SMS)
      case Full(sca) if sca == StrongCustomerAuthentication.EMAIL.toString() => Full(StrongCustomerAuthentication.EMAIL)
      case _ => Full(StrongCustomerAuthentication.UNDEFINED)
    }
  }

  /**
    * Given two ranges(like date ranges), do they overlap and if so, how much?
    *
    *         A +-----------------+ B
    *                      C +-------------------+ D
    *
    * if NOT ( B <= C or A >= D) then they overlap
    * Next, do 4 calculations:
    * B-A, B-C, D-A, D-C
    * The actual overlap will be the least of these.
    * 
    */
  case class DateInterval(start: Date, end: Date)
  def dateRangesOverlap(range1: DateInterval, range2: DateInterval): Boolean = {
    if(range1.end.before(range2.start) || range1.start.after(range2.end)) false else true
  }
  
  def checkCustomViewName(name: String): Boolean = name match {
    case x if x == "_" + SYSTEM_OWNER_VIEW_ID => false // Reserved name
    case x if x.startsWith("_") => true // Allowed case
    case _ => false
  }

  def canGrantAccessToViewCommon(bankId: BankId, accountId: AccountId, user: User): Boolean = {
    user.hasOwnerViewAccess(BankIdAccountId(bankId, accountId)) || // TODO Use an action instead of the owner view
      AccountHolders.accountHolders.vend.getAccountHolders(bankId, accountId).exists(_.userId == user.userId)
  }
  def canRevokeAccessToViewCommon(bankId: BankId, accountId: AccountId, user: User): Boolean = {
    user.hasOwnerViewAccess(BankIdAccountId(bankId, accountId)) || // TODO Use an action instead of the owner view
      AccountHolders.accountHolders.vend.getAccountHolders(bankId, accountId).exists(_.userId == user.userId)
  }

  def getJValueFromJsonFile(path: String) = {
    val stream = getClass().getClassLoader().getResourceAsStream(path)
    try {
      val bufferedSource = scala.io.Source.fromInputStream(stream, "utf-8")
      val jsonStringFromFile = bufferedSource.mkString
      json.parse(jsonStringFromFile);
    } finally {
      stream.close()
    }
  }
  
}
