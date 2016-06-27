/**
  * Open Bank Project - API
  * Copyright (C) 2011-2015, TESOBE / Music Pictures Ltd
  **
  *This program is free software: you can redistribute it and/or modify
  *it under the terms of the GNU Affero General Public License as published by
  *the Free Software Foundation, either version 3 of the License, or
  *(at your option) any later version.
  **
  *This program is distributed in the hope that it will be useful,
  *but WITHOUT ANY WARRANTY; without even the implied warranty of
  *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  *GNU Affero General Public License for more details.
  **
  *You should have received a copy of the GNU Affero General Public License
*along with this program.  If not, see <http://www.gnu.org/licenses/>.
  **
 *Email: contact@tesobe.com
*TESOBE / Music Pictures Ltd
*Osloerstrasse 16/17
*Berlin 13359, Germany
  **
 *This product includes software developed at
  *TESOBE (http://www.tesobe.com/)
  * by
  *Simon Redfern : simon AT tesobe DOT com
  *Stefan Bethge : stefan AT tesobe DOT com
  *Everett Sochowski : everett AT tesobe DOT com
  *Ayoub Benali: ayoub AT tesobe DOT com
  *
 */

package code.api.util

import code.api.Constant._
import code.api.DirectLogin
import code.api.OAuthHandshake._
import code.api.v1_2.ErrorMessage
import code.entitlement.Entitlement
import code.metrics.APIMetrics
import code.model._
import dispatch.url
import net.liftweb.common.{Empty, _}
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.js.JsExp
import net.liftweb.http.{CurrentReq, JsonResponse, Req, S}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{Extraction, parse}
import net.liftweb.util.Helpers._
import net.liftweb.util.{Helpers, Props, SecurityHelpers}

import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._


object ErrorMessages {

  // Infrastructure / config messages
  val HostnameNotSpecified = "OBP-00001: Hostname not specified. Could not get hostname from Props. Please edit your props file. Here are some example settings: hostname=http://127.0.0.1:8080 or hostname=https://www.example.com"

  // General messages
  val InvalidJsonFormat = "OBP-10001: Incorrect json format."
  val InvalidNumber = "OBP-10002: Invalid Number. Could not convert value to a number."
  val InvalidInitalBalance = "OBP-10003: Invalid Number. Initial balance must be a number, e.g 1000.00"



  // Authentication / Authorisation / User messages
  val UserNotLoggedIn = "OBP-20001: User not logged in. Authentication is required!"


  val DirectLoginMissingParameters = "OBP-20002: These DirectLogin parameters are missing: "
  val DirectLoginInvalidToken = "OBP-20003: This DirectLogin token is invalid or expired: "


  val InvalidLoginCredentials = "OBP-20004: Invalid login credentials. Check username/password."

  val UserNotFoundById = "OBP-20005: User not found by User Id."
  val UserDoesNotHaveRole = "OBP-20006: User does not have a role "


  // Resource related messages
  val BankNotFound = "OBP-30001: Bank not found. Please specify a valid value for BANK_ID."
  val CustomerNotFound = "OBP-30002: Customer not found. Please specify a valid value for CUSTOMER_NUMBER."
  val CustomerNotFoundByCustomerId = "OBP-30002: Customer not found. Please specify a valid value for CUSTOMER_ID."

  val AccountNotFound = "OBP-30003: Account not found. Please specify a valid value for ACCOUNT_ID."
  val CounterpartyNotFound = "OBP-30004: Counterparty not found. The BANK_ID / ACCOUNT_ID specified does not exist on this server."

  val ViewNotFound = "OBP-30005: View not found for Account. Please specify a valid value for VIEW_ID"

  val CustomerNumberAlreadyExists = "OBP-30006: Customer Number already exists. Please specify a different value for BANK_ID or CUSTOMER_NUMBER."
  val CustomerAlreadyExistsForUser = "OBP-30007: The User is already linked to a Customer at BANK_ID"

  val MeetingsNotSupported = "OBP-30101: Meetings are not supported on this server."
  val MeetingApiKeyNotConfigured = "OBP-30102: Meeting provider API Key is not configured."
  val MeetingApiSecretNotConfigured = "OBP-30103: Meeting provider Secret is not configured."

  // Transaction related messages:
  val InvalidTransactionRequestType = "OBP-40001: Invalid value for TRANSACTION_REQUEST_TYPE"
  val InsufficientAuthorisationToCreateTransactionRequest  = "OBP-40002: Insufficient authorisation to create TransactionRequest. The Transaction Request could not be created because you don't have access to the owner view of the from account and you don't have access to canCreateAnyTransactionRequest."

}




object APIUtil extends Loggable {

  implicit val formats = net.liftweb.json.DefaultFormats
  implicit def errorToJson(error: ErrorMessage): JValue = Extraction.decompose(error)
  val headers = ("Access-Control-Allow-Origin","*") :: Nil

  def httpMethod : String =
    S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }

  def isThereDirectLoginHeader : Boolean = {
    S.request match {
      case Full(a) =>  a.header("Authorization") match {
        case Full(parameters) => parameters.contains("DirectLogin")
        case _ => false
      }
      case _ => false
    }
  }

  def isThereAnOAuthHeader : Boolean = {
    S.request match {
      case Full(a) =>  a.header("Authorization") match {
        case Full(parameters) => parameters.contains("OAuth")
        case _ => false
      }
      case _ => false
    }
  }

  def logAPICall = {
    if(Props.getBool("write_metrics", false)) {
      val user =
        if (isThereAnOAuthHeader) {
          getUser match {
            case Full(u) => Full(u)
            case _ => Empty
          }
        } else if (Props.getBool("allow_direct_login", true) && isThereDirectLoginHeader) {
          DirectLogin.getUser match {
            case Full(u) => Full(u)
            case _ => Empty
          }
        } else {
            Empty
        }
      // TODO This should use Elastic Search or Kafka not an RDBMS
      val u = user.orNull
      val userId = if (u != null) u.userId else "null"
      APIMetrics.apiMetrics.vend.saveMetric(userId, S.uriAndQueryString.getOrElse(""), (now: TimeSpan))
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

  def noContentJsonResponse : JsonResponse =
    JsonResponse(JsRaw(""), headers, Nil, 204)

  def successJsonResponse(json: JsExp, httpCode : Int = 200) : JsonResponse =
    JsonResponse(json, headers, Nil, httpCode)

  def createdJsonResponse(json: JsExp, httpCode : Int = 201) : JsonResponse =
    JsonResponse(json, headers, Nil, httpCode)

  def acceptedJsonResponse(json: JsExp, httpCode : Int = 202) : JsonResponse =
    JsonResponse(json, headers, Nil, httpCode)

  def errorJsonResponse(message : String = "error", httpCode : Int = 400) : JsonResponse =
    JsonResponse(Extraction.decompose(ErrorMessage(message)), headers, Nil, httpCode)

  def notImplementedJsonResponse(message : String = "Not Implemented", httpCode : Int = 501) : JsonResponse =
    JsonResponse(Extraction.decompose(ErrorMessage(message)), headers, Nil, httpCode)


  def oauthHeaderRequiredJsonResponse : JsonResponse =
    JsonResponse(Extraction.decompose(ErrorMessage("Authentication via OAuth is required")), headers, Nil, 400)

  /** Import this object's methods to add signing operators to dispatch.Request */
  object OAuth {
    import javax.crypto

    import dispatch.{Req => Request}
    import net.liftweb.util.Helpers
    import org.apache.http.protocol.HTTP.UTF_8

    import scala.collection.Map
    import scala.collection.immutable.{TreeMap, Map => IMap}

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
      def apply[T <: Any](m: Map[String, T]): Option[Token] = List("oauth_token", "oauth_token_secret").flatMap(m.get) match {
        case value :: secret :: Nil => Some(Token(value.toString, secret.toString))
        case _ => None
      }
    }

    /** @return oauth parameter map including signature */
    def sign(method: String, url: String, user_params: Map[String, Any], consumer: Consumer, token: Option[Token], verifier: Option[String], callback: Option[String]) = {
      val oauth_params = IMap(
        "oauth_consumer_key" -> consumer.key,
        "oauth_signature_method" -> "HMAC-SHA1",
        "oauth_timestamp" -> (System.currentTimeMillis / 1000).toString,
        "oauth_nonce" -> System.nanoTime.toString,
        "oauth_version" -> "1.0"
      ) ++ token.map { "oauth_token" -> _.value } ++
        verifier.map { "oauth_verifier" -> _ } ++
        callback.map { "oauth_callback" -> _ }

      val encoded_ordered_params = (
        new TreeMap[String, String] ++ (user_params ++ oauth_params map %%)
      ) map { case (k, v) => k + "=" + v } mkString "&"

      val message =
        %%(method.toUpperCase :: url :: encoded_ordered_params :: Nil)

      val SHA1 = "HmacSHA1"
      val key_str = %%(consumer.secret :: (token map { _.secret } getOrElse "") :: Nil)
      val key = new crypto.spec.SecretKeySpec(bytes(key_str), SHA1)
      val sig = {
        val mac = crypto.Mac.getInstance(SHA1)
        mac.init(key)
        base64Encode(mac.doFinal(bytes(message)))
      }
      oauth_params + ("oauth_signature" -> sig)
    }

    /** Out-of-band callback code */
    val oob = "oob"

    /** Map with oauth_callback set to the given url */
    def callback(url: String) = IMap("oauth_callback" -> url)

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
        val body_encoding = r.getBodyEncoding
        var body = new String()
        if (r.getByteData != null )
          body = new String(r.getByteData)
        val oauth_params = OAuth.sign(r.getMethod, oauth_url,
                                      query_params ++ form_params,
                                      consumer, token, verifier, callback)

        def createRequest( reqData: ReqData ): Request = {
          val rb = url(reqData.url)
            .setMethod(reqData.method)
            .setBodyEncoding(reqData.body_encoding)
            .setBody(reqData.body) <:< reqData.headers
          if (reqData.query_params.nonEmpty)
            rb <<? reqData.query_params
          rb
        }

        createRequest( ReqData(
          oauth_url,
          r.getMethod,
          body,
          body_encoding,
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

  // Used to tag Resource Docs
  case class ResourceDocTag(tag: String)

  // Use the *singular* case. for both the variable name and string.
  // e.g. "This call is Payment related"
  val apiTagTransactionRequest = ResourceDocTag("TransactionRequest")
  val apiTagApiInfo = ResourceDocTag("APIInfo")
  val apiTagBank = ResourceDocTag("Bank")
  val apiTagAccount = ResourceDocTag("Account")
  val apiTagPublicData = ResourceDocTag("PublicData")
  val apiTagPrivateData = ResourceDocTag("PrivateData")
  val apiTagTransaction = ResourceDocTag("Transaction")
  val apiTagMetaData = ResourceDocTag("MetaData")
  val apiTagView = ResourceDocTag("View")
  val apiTagEntitlement = ResourceDocTag("Entitlement")
  val apiTagOwnerRequired = ResourceDocTag("OwnerViewRequired")
  val apiTagCounterparty = ResourceDocTag("Counterparty")
  val apiTagKyc = ResourceDocTag("KYC")
  val apiTagCustomer = ResourceDocTag("Customer")
  val apiTagOnboarding = ResourceDocTag("Onboarding")
  val apiTagUser = ResourceDocTag("User")
  val apiTagMeeting = ResourceDocTag("Meeting")
  val apiTagExperimental = ResourceDocTag("Experimental")


  // Used to document the API calls
  case class ResourceDoc(
    partialFunction : PartialFunction[Req, Box[User] => Box[JsonResponse]],
    apiVersion: String, // TODO: Constrain to certain strings?
    apiFunction: String, // The partial function that implements this resource. Could use it to link to the source code that implements the call
    requestVerb: String, // GET, POST etc. TODO: Constrain to GET, POST etc.
    requestUrl: String, // The URL (not including /obp/vX.X). Starts with / No trailing slash. TODO Constrain the string?
    summary: String, // A summary of the call (originally taken from code comment) SHOULD be under 120 chars to be inline with Swagger
    description: String, // Longer description (originally taken from github wiki)
    exampleRequestBody: JValue, // An example of the body required (maybe empty)
    successResponseBody: JValue, // A successful response body
    errorResponseBodies: List[JValue], // Possible error responses
    isCore: Boolean,
    isPSD2: Boolean,
    isOBWG: Boolean,
    tags: List[ResourceDocTag]
  )

  // Define relations between API end points. Used to create _links in the JSON and maybe later for API Explorer browsing
  case class ApiRelation(
    fromPF : PartialFunction[Req, Box[User] => Box[JsonResponse]],
    toPF : PartialFunction[Req, Box[User] => Box[JsonResponse]],
    rel : String
  )

  // Populated from Resource Doc and ApiRelation
  case class InternalApiLink(
    fromPF : PartialFunction[Req, Box[User] => Box[JsonResponse]],
    toPF : PartialFunction[Req, Box[User] => Box[JsonResponse]],
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
    caller : PartialFunction[Req, Box[User] => Box[JsonResponse]]
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



  def authenticationRequiredMessage(authRequired: Boolean) : String =
    authRequired match {
      case true => "Authentication IS required"
      case false => "Authentication is NOT required"
    }



  def apiVersionWithV(apiVersion : String) : String = {
    // TODO Define a list of supported versions (put in Constant) and constrain the input
    // Append v and replace _ with .
    s"v${apiVersion.replaceAll("_",".")}"
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
        s"/${apiVersionWithV(toResourceDoc.apiVersion)}${toResourceDoc.requestUrl}"
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
    val user_ids = Props.get("super_admin_user_ids", "super_admin_user_ids is not defined").split(",").map(_.trim).toList
    user_ids.filter(_ == user_id).length > 0
  }

  def hasEntitlement(bankId: String, userId: String, role: ApiRole): Boolean = {
    !Entitlement.entitlement.vend.getEntitlement(bankId, userId, role.toString).isEmpty
  }

}
