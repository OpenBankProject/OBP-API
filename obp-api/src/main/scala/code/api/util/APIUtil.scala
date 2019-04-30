/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.util

import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.{Date, UUID}

import code.api.Constant._
import code.api.OAuthHandshake._
import code.api.builder.OBP_APIBuilder
import code.api.oauth1a.Arithmetics
import code.api.oauth1a.OauthParams._
import code.api.util.ApiTag.{ResourceDocTag, apiTagBank}
import code.api.util.Glossary.GlossaryItem
import code.api.v1_2.ErrorMessage
import code.api.{DirectLogin, util, _}
import code.consumer.Consumers
import code.customer.Customer
import code.entitlement.Entitlement
import code.metrics._
import code.model._
import code.sanitycheck.SanityCheck
import code.scope.Scope
import code.util.Helper
import code.util.Helper.{MdcLoggable, SILENCE_IS_GOLDEN}
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
import net.liftweb.util.{Helpers, Props, StringHelpers}

import scala.collection.JavaConverters._
import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
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
              cc.correlationId
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
          implementedInVersion, verb,
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
    (cc, RateLimitUtil.useConsumerLimits) match {
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

  /** check the currency ISO code from the ISOCurrencyCodes.xml file */
  def isValidCurrencyISOCode(currencyCode: String): Boolean = {
    //just for initialization the Elem variable
    var xml: Elem = <html/>
    LiftRules.getResource("/media/xml/ISOCurrencyCodes.xml").map{ url =>
      val input: InputStream = url.openStream()
      xml = XML.load(input)
    }
    val stringArray = (xml \ "CcyTbl" \ "CcyNtry" \ "Ccy").map(_.text).mkString(" ").split("\\s+")
    stringArray.contains(currencyCode)
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
           connectorName,functionName, bankId, accountId
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
      HTTPParam("connector_name", connectorName)
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
        SignatureMethodName -> "HMAC-SHA1",
        TimestampName -> (System.currentTimeMillis / 1000).toString,
        NonceName -> System.nanoTime.toString,
        VersionName -> "1.0"
      ) ++ token.map { TokenName -> _.value } ++
        verifier.map { VerifierName -> _ } ++
        callback.map { CallbackName -> _ }

      val signatureBase = Arithmetics.concatItemsForSignature(method.toUpperCase, url, user_params.toList, Nil, oauth_params.toList)
      val computedSignature = Arithmetics.sign(signatureBase, consumer.secret, (token map { _.secret } getOrElse ""), Arithmetics.HmacSha1Algorithm)
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
                          partialFunction : OBPEndpoint, // PartialFunction[Req, Box[User] => Box[JsonResponse]],
                          implementedInApiVersion: ScannedApiVersion, // TODO: Use ApiVersion enumeration instead of string
                          partialFunctionName: String, // The string name of the partial function that implements this resource. Could use it to link to the source code that implements the call
                          requestVerb: String, // GET, POST etc. TODO: Constrain to GET, POST etc.
                          requestUrl: String, // The URL. THIS GETS MODIFIED TO include the implemented in prefix e.g. /obp/vX.X). Starts with / No trailing slash.
                          summary: String, // A summary of the call (originally taken from code comment) SHOULD be under 120 chars to be inline with Swagger
                          description: String, // Longer description (originally taken from github wiki)
                          exampleRequestBody: scala.Product, // An example of the body required (maybe empty)
                          successResponseBody: scala.Product, // A successful response body
                          errorResponseBodies: List[String], // Possible error responses
                          catalogs: Catalogs,
                          tags: List[ResourceDocTag],
                          roles: Option[List[ApiRole]] = None,
                          isFeatured: Boolean = false,
                          specialInstructions: Option[String] = None,
                          specifiedUrl: Option[String] = None // A derived value: Contains the called version (added at run time). See the resource doc for resource doc!
  )


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
    REQUIRE_SCOPES match {
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



  def hasEntitlement(bankId: String, userId: String, role: ApiRole): Boolean = {
    !Entitlement.entitlement.vend.getEntitlement(bankId, userId, role.toString).isEmpty
  }

  case class EntitlementAndScopeStatus(
    hasBoth: Boolean,
    reason: Option[String] = None, //for Later
    errorMessage: String,
  )
  
  val REQUIRE_SCOPES: Boolean = getPropsAsBoolValue("require_scopes", false)
  
  def hasEntitlementAndScope(bankId: String, userId: String, consumerId: String, role: ApiRole): Box[EntitlementAndScopeStatus]= {
    for{
      hasEntitlement <- tryo{ !Entitlement.entitlement.vend.getEntitlement(bankId, userId, role.toString).isEmpty} ?~! s"$UnknownError"
      hasScope <- REQUIRE_SCOPES match {
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
  def hasAtLeastOneEntitlement(bankId: String, userId: String, roles: List[ApiRole]): Boolean = {
    val list: List[Boolean] = for (role <- roles) yield {
      !Entitlement.entitlement.vend.getEntitlement(if (role.requiresBankId == true) bankId else "", userId, role.toString).isEmpty
    }
    list.exists(_ == true)
  }

  // Function checks does a user specified by a parameter userId has all roles provided by a parameter roles at a bank specified by a parameter bankId
  // i.e. does user has assigned all roles from the list
  // TODO Should we accept Option[BankId] for bankId  instead of String ?
  def hasAllEntitlements(bankId: String, userId: String, roles: List[ApiRole]): Boolean = {
    val list: List[Boolean] = for (role <- roles) yield {
      !Entitlement.entitlement.vend.getEntitlement(if (role.requiresBankId == true) bankId else "", userId, role.toString).isEmpty
    }
    list.forall(_ == true)
  }

  def getCustomers(ids: List[String]): List[Customer] = {
    val customers = {
      for {id <- ids
           c = Customer.customerProvider.vend.getCustomerByCustomerId(id)
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
        case ApiVersion.`apiBuilder` => LiftRules.statelessDispatch.append(OBP_APIBuilder)
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


  def getAllowedEndpoints (endpoints : List[OBPEndpoint], resourceDocs: ArrayBuffer[ResourceDoc]) : List[OBPEndpoint] = {

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
             (NewStyle.endpoints.exists(x => x == (item.partialFunctionName, item.implementedInApiVersion.toString())) || !onlyNewStyle)
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
              logEndpointTiming(af.ccl)(reply.apply(errorJsonResponse(af.failMsg, af.failCode)(getHeadersNewStyle(af.ccl))))
            case _ =>
              reply.apply(errorJsonResponse(msg))
          }
        case _                  =>
          reply.apply(errorJsonResponse("Error"))
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
      in.onSuccess(
        t => Full(logEndpointTiming(t._2.map(_.toLight))(reply.apply(successJsonResponseNewStyle(t._1, t._2)(getHeadersNewStyle(t._2.map(_.toLight))))))
      )
      in.onFail {
        case Failure(null, _, _) => Full(reply.apply(errorJsonResponse(UnknownError)))
        case Failure(msg, _, _) =>
          extractAPIFailureNewStyle(msg) match {
            case Some(af) =>
              Full(logEndpointTiming(af.ccl)(reply.apply(errorJsonResponse(af.failMsg, af.failCode)(getHeadersNewStyle(af.ccl)))))
            case _ =>
              Full((reply.apply(errorJsonResponse(msg))))
          }
        case _ =>
          Full(reply.apply(errorJsonResponse(UnknownError)))
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
    val implementedInVersion = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).view
    val verb = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).requestType.method
    val url = URLDecoder.decode(S.uriAndQueryString.getOrElse(""),"UTF-8")
    val correlationId = getCorrelationId()
    val reqHeaders = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).request.headers
    val res =
    if (APIUtil.hasConsentId(reqHeaders)) {
      Consent.applyRules(APIUtil.getConsentId(reqHeaders), Some(cc)) 
    } else if (hasAnOAuthHeader(cc.authReqHeaderField)) {
      getUserFromOAuthHeaderFuture(cc)
    } else if (hasAnOAuth2Header(cc.authReqHeaderField)) {
      OAuth2Login.getUserFuture(cc)
    } else if (getPropsAsBoolValue("allow_direct_login", true) && hasDirectLoginHeader(cc.authReqHeaderField)) {
      DirectLogin.getUserFromDirectLoginHeaderFuture(cc)
    } else if (getPropsAsBoolValue("allow_gateway_login", false) && hasGatewayHeader(cc.authReqHeaderField)) {
      APIUtil.getPropsValue("gateway.host") match {
        case Full(h) if h.split(",").toList.exists(_.equalsIgnoreCase(getRemoteIpAddress()) == true) => // Only addresses from white list can use this feature
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
        case Full(h) if h.split(",").toList.exists(_.equalsIgnoreCase(getRemoteIpAddress()) == false) => // All other addresses will be rejected
          Future { (Failure(ErrorMessages.GatewayLoginWhiteListAddresses), None) }
        case Empty =>
          Future { (Failure(ErrorMessages.GatewayLoginHostPropertyMissing), None) } // There is no gateway.host in props file
        case Failure(msg, t, c) =>
          Future { (Failure(msg, t, c), None) }
        case _ =>
          Future { (Failure(ErrorMessages.GatewayLoginUnknownError), None) }
      }
    } else {
      Future { (Empty, None) }
    }
    // Update Call Context
    res map {
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
      x => (x._1, x._2.map(_.copy(ipAddress = getRemoteIpAddress())))
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
    unboxFullOrFail(box, cc, ConnectorEmptyResponse, 400)
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
    * This function checks rate limiting for a Consumer.
    * It will check rate limiting per minute, hour, day, week and month.
    * In case any of the above is hit an error is thrown.
    * In case two or more limits are hit rate limit with lower period has precedence regarding the error message.
    * @param userAndCallContext is a Tuple (Box[User], Option[CallContext]) provided from getUserAndSessionContextFuture function
    * @return a Tuple (Box[User], Option[CallContext]) enriched with rate limiting header or an error.
    */
  private def underCallLimits(userAndCallContext: (Box[User], Option[CallContext])): (Box[User], Option[CallContext]) = {
    import util.RateLimitPeriod._
    import util.RateLimitUtil._
    val perHourLimitAnonymous = APIUtil.getPropsAsIntValue("user_consumer_limit_anonymous_access", 60)
    def composeMsg(period: LimitCallPeriod, limit: Long): String = TooManyRequests + s" We only allow $limit requests ${RateLimitPeriod.humanReadable(period)} for this Consumer."

    def setXRateLimits(c: Consumer, z: (Long, Long), period: LimitCallPeriod): Option[CallContext] = {
      val limit = period match {
        case PER_SECOND => 
          c.perSecondCallLimit.get
        case PER_MINUTE => 
          c.perMinuteCallLimit.get
        case PER_HOUR   => 
          c.perHourCallLimit.get
        case PER_DAY    => 
          c.perDayCallLimit.get
        case PER_WEEK   => 
          c.perWeekCallLimit.get
        case PER_MONTH  => 
          c.perMonthCallLimit.get
        case PER_YEAR   => 
          -1
      }
      userAndCallContext._2.map(_.copy(`X-Rate-Limit-Limit` = limit))
        .map(_.copy(`X-Rate-Limit-Reset` = z._1))
        .map(_.copy(`X-Rate-Limit-Remaining` = limit - z._2))
    }
    def setXRateLimitsAnonymous(id: String, z: (Long, Long), period: LimitCallPeriod): Option[CallContext] = {
      val limit = period match {
        case PER_HOUR   => perHourLimitAnonymous
        case _   => -1
      }
      userAndCallContext._2.map(_.copy(`X-Rate-Limit-Limit` = limit))
        .map(_.copy(`X-Rate-Limit-Reset` = z._1))
        .map(_.copy(`X-Rate-Limit-Remaining` = limit - z._2))
    }

    def exceededRateLimit(c: Consumer, period: LimitCallPeriod): Option[CallContextLight] = {
      val remain = ttl(c.key.get, period)
      val limit = period match {
        case PER_SECOND => 
          c.perSecondCallLimit.get
        case PER_MINUTE => 
          c.perMinuteCallLimit.get
        case PER_HOUR   => 
          c.perHourCallLimit.get
        case PER_DAY    => 
          c.perDayCallLimit.get
        case PER_WEEK   => 
          c.perWeekCallLimit.get
        case PER_MONTH  => 
          c.perMonthCallLimit.get
        case PER_YEAR   => 
          -1
      }
      userAndCallContext._2.map(_.copy(`X-Rate-Limit-Limit` = limit))
        .map(_.copy(`X-Rate-Limit-Reset` = remain))
        .map(_.copy(`X-Rate-Limit-Remaining` = 0)).map(_.toLight)
    }

    def exceededRateLimitAnonymous(id: String, period: LimitCallPeriod): Option[CallContextLight] = {
      val remain = ttl(id, period)
      val limit = period match {
        case PER_HOUR   => perHourLimitAnonymous
        case _   => -1
      }
      userAndCallContext._2.map(_.copy(`X-Rate-Limit-Limit` = limit))
        .map(_.copy(`X-Rate-Limit-Reset` = remain))
        .map(_.copy(`X-Rate-Limit-Remaining` = 0)).map(_.toLight)
    }

    userAndCallContext._2 match {
      case Some(cc) =>
        cc.consumer match {
          case Full(c) => // Authorized access
            val checkLimits = List(
              underConsumerLimits(c.key.get, PER_SECOND, c.perSecondCallLimit.get),
              underConsumerLimits(c.key.get, PER_MINUTE, c.perMinuteCallLimit.get),
              underConsumerLimits(c.key.get, PER_HOUR, c.perHourCallLimit.get),
              underConsumerLimits(c.key.get, PER_DAY, c.perDayCallLimit.get),
              underConsumerLimits(c.key.get, PER_WEEK, c.perWeekCallLimit.get),
              underConsumerLimits(c.key.get, PER_MONTH, c.perMonthCallLimit.get)
            )
            checkLimits match {
              case x1 :: x2 :: x3 :: x4 :: x5 :: x6 :: Nil if x1 == false =>
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsg(PER_SECOND, c.perSecondCallLimit.get), 429, exceededRateLimit(c, PER_SECOND))), userAndCallContext._2)
              case x1 :: x2 :: x3 :: x4 :: x5 :: x6 :: Nil if x2 == false =>
               (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsg(PER_MINUTE, c.perMinuteCallLimit.get), 429, exceededRateLimit(c, PER_MINUTE))), userAndCallContext._2)
              case x1 :: x2 :: x3 :: x4 :: x5 :: x6 :: Nil if x3 == false =>
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsg(PER_HOUR, c.perHourCallLimit.get), 429, exceededRateLimit(c, PER_HOUR))), userAndCallContext._2)
              case x1 :: x2 :: x3 :: x4 :: x5 :: x6 :: Nil if x4 == false =>
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsg(PER_DAY, c.perDayCallLimit.get), 429, exceededRateLimit(c, PER_DAY))), userAndCallContext._2)
              case x1 :: x2 :: x3 :: x4 :: x5 :: x6 :: Nil if x5 == false =>
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsg(PER_WEEK, c.perWeekCallLimit.get), 429, exceededRateLimit(c, PER_WEEK))), userAndCallContext._2)
              case x1 :: x2 :: x3 :: x4 :: x5 :: x6 :: Nil if x6 == false =>
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsg(PER_MONTH, c.perMonthCallLimit.get), 429, exceededRateLimit(c, PER_MONTH))), userAndCallContext._2)
              case _ =>
                val incrementCounters = List (
                  incrementConsumerCounters(c.key.get, PER_SECOND, c.perSecondCallLimit.get),  // Responses other than the 429 status code MUST be stored by a cache.
                  incrementConsumerCounters(c.key.get, PER_MINUTE, c.perMinuteCallLimit.get),  // Responses other than the 429 status code MUST be stored by a cache.
                  incrementConsumerCounters(c.key.get, PER_HOUR, c.perHourCallLimit.get),  // Responses other than the 429 status code MUST be stored by a cache.
                  incrementConsumerCounters(c.key.get, PER_DAY, c.perDayCallLimit.get),  // Responses other than the 429 status code MUST be stored by a cache.
                  incrementConsumerCounters(c.key.get, PER_WEEK, c.perWeekCallLimit.get),  // Responses other than the 429 status code MUST be stored by a cache.
                  incrementConsumerCounters(c.key.get, PER_MONTH, c.perMonthCallLimit.get)  // Responses other than the 429 status code MUST be stored by a cache.
                )
                incrementCounters match {
                  case first :: _ :: _ :: _ :: _ :: _ :: Nil if first._1 > 0 => 
                    (userAndCallContext._1, setXRateLimits(c, first, PER_SECOND))
                  case _ :: second :: _ :: _ :: _ :: _ :: Nil if second._1 > 0 => 
                    (userAndCallContext._1, setXRateLimits(c, second, PER_MINUTE))
                  case _ :: _ :: third :: _ :: _ :: _ :: Nil if third._1 > 0 => 
                    (userAndCallContext._1, setXRateLimits(c, third, PER_HOUR))
                  case _ :: _ :: _ :: fourth :: _ :: _ :: Nil if fourth._1 > 0 => 
                    (userAndCallContext._1, setXRateLimits(c, fourth, PER_DAY))
                  case _ :: _ :: _ :: _ :: fifth :: _ :: Nil if fifth._1 > 0 => 
                    (userAndCallContext._1, setXRateLimits(c, fifth, PER_WEEK))
                  case _ :: _ :: _ :: _ :: _ :: sixth :: Nil if sixth._1 > 0 => 
                    (userAndCallContext._1, setXRateLimits(c, sixth, PER_MONTH))
                  case _  => 
                    (userAndCallContext._1, userAndCallContext._2)
                }
            }
          case Empty => // Anonymous access
            val consumerId = cc.ipAddress
            val checkLimits = List(
              underConsumerLimits(consumerId, PER_HOUR, perHourLimitAnonymous)
            )
            checkLimits match {
              case x1 :: Nil if x1 == false =>
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsg(PER_HOUR, perHourLimitAnonymous), 429, exceededRateLimitAnonymous(consumerId, PER_HOUR))), userAndCallContext._2)
              case _ =>
                val incrementCounters = List (
                    incrementConsumerCounters(consumerId, PER_HOUR, perHourLimitAnonymous),  // Responses other than the 429 status code MUST be stored by a cache.
                  )
                incrementCounters match {
                  case x1 :: Nil if x1._1 > 0 =>
                    (userAndCallContext._1, setXRateLimitsAnonymous(consumerId, x1, PER_HOUR))
                  case _  =>
                    (userAndCallContext._1, userAndCallContext._2)
                }
            }
          case _ => (userAndCallContext._1, userAndCallContext._2)
        }
      case _ => (userAndCallContext._1, userAndCallContext._2)
    }
  }

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
      x => underCallLimits(x)
    }
  }

  def filterMessage(obj: Failure): String = {
    logger.debug("Failure: " + obj)

    def messageIsNotNull(x: Failure, obj: Failure) = {
      if (x.msg != null) true else { logger.info("Failure: " + obj); false }
    }

    getPropsAsBoolValue("display_internal_errors", false) match {
      case true => // Show all error in a chain
        obj.messageChain
      case false => // Do not display internal errors
        val obpFailures = obj.failureChain.filter(x => messageIsNotNull(x, obj) && x.msg.startsWith("OBP-"))
        obpFailures match {
          case Nil => ErrorMessages.AnUnspecifiedOrInternalErrorOccurred
          case _ => obpFailures.map(_.msg).mkString(" <- ")
        }
    }
  }

  /**
    * This Function is used to terminate a Future used in for-comprehension with specific message
    * Please note that boxToFailed(Empty ?~ ("Some failure message")) will be transformed to Failure("Some failure message", Empty, Empty)
    * @param box Some boxed type
    * @return Boxed value or throw some exception
    */
  def fullBoxOrException[T](box: Box[T])(implicit m: Manifest[T]) : Box[T]= {
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
  def hasAccess(view: View, user: Option[User]) : Boolean = {
    if(hasPublicAccess(view: View))// No need for the Login user and public access
      true
    else
      user match {
        case Some(u) if hasFirehoseAccess(view,u)  => true//Login User and Firehose access
        case Some(u) if u.hasViewAccess(view)=> true     // Login User and check view access
        case _ =>
          false
      }
  }
  /**
    * This view public is true and set `allow_public_views=ture` in props
    */
  def hasPublicAccess(view: View) : Boolean = {
    if(view.isPublic && APIUtil.ALLOW_PUBLIC_VIEWS) true
    else false
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
      
  
  def getJValueFromFile (path: String) = {
    val jsonStringFromFile: String = scala.io.Source.fromFile(path).mkString 
    json.parse(jsonStringFromFile)
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
  def toResourceDoc(messageDoc: MessageDoc): ResourceDoc = ResourceDoc(
    null,
    ApiVersion.v3_1_0,
    messageDoc.process,
    "get",
    s"/obp-connector/${messageDoc.process.replaceAll("obp.","").replace(".","")}",
    messageDoc.description,
    messageDoc.description,
    messageDoc.exampleOutboundMessage,
    messageDoc.exampleInboundMessage,
    errorResponseBodies = List(InvalidJsonFormat),
    Catalogs(notCore,notPSD2,notOBWG),
    List(apiTagBank)
  )
  
  
  def createBasicUserAuthContext(userAuthContest : UserAuthContext) : BasicUserAuthContext = {
    BasicUserAuthContext(
      key = userAuthContest.key,
      value = userAuthContest.value
    )
  }
  
  def createBasicUserAuthContextJson(userAuthContexts : List[UserAuthContext]) : List[BasicUserAuthContext] = {
    userAuthContexts.map(createBasicUserAuthContext)
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

}
