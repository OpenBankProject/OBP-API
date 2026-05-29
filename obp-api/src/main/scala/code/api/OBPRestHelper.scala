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

import scala.language.reflectiveCalls
import scala.language.implicitConversions
import code.api.Constant._
import code.api.util.APIUtil._
import code.api.util._
import code.util.Helper.MdcLoggable
import com.alibaba.ttl.TransmittableThreadLocal
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.common._
import net.liftweb.http.{JsonResponse, LiftRules, TransientRequestMemoize}
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.{Helpers, NamedPF, Props, ThreadGlobal}

import java.util.{Locale, ResourceBundle}
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NoStackTrace
import scala.xml.{Node, NodeSeq}

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
                             ){
  def translatedErrorMessage = {
  
    val errorCode = extractErrorMessageCode(failMsg)
    val errorBody = extractErrorMessageBody(failMsg)
    
    val localeUrlParameter = getHttpRequestUrlParam(ccl.map(_.url).getOrElse(""),PARAM_LOCALE)
    val localeFromUrl = I18NUtil.computeLocale(localeUrlParameter)
    
    
    val locale: Locale = 
      if(localeFromUrl.toString.equals("")) //if the url local parameter is invalid, then we use the default Locale.
        I18NUtil.getDefaultLocale() 
      else 
        localeFromUrl

    val liftCoreResourceBundle = tryo(ResourceBundle.getBundle(LiftRules.liftCoreResourceName, locale)).toList
    
    val _resBundle = new ThreadGlobal[List[ResourceBundle]]
    object resourceValueCache extends TransientRequestMemoize[(String, Locale), String]
  
    def resourceBundles(loc: Locale): List[ResourceBundle] = {
      _resBundle.box match {
        case Full(bundles) => bundles
        case _ => {
          _resBundle.set(
            LiftRules.resourceForCurrentLoc.vend() :::
              LiftRules.resourceNames.flatMap(name => tryo{
                if (Props.devMode) {
                  tryo{
                    val clz = this.getClass.getClassLoader.loadClass("java.util.ResourceBundle")
                    val meth = clz.getDeclaredMethods.
                      filter{m => m.getName == "clearCache" && m.getParameterTypes.length == 0}.
                      toList.head
                    meth.invoke(null)
                  }
                }
                List(ResourceBundle.getBundle(name, loc))
              }.openOr(
                NamedPF.applyBox((name, loc), LiftRules.resourceBundleFactories.toList).map(List(_)) openOr Nil
                )))
          _resBundle.value
        }
      }
    }
   
  
    def resourceBundleList: List[ResourceBundle] = resourceBundles(locale) ++ liftCoreResourceBundle
  
    def ?!(str: String, resBundle: List[ResourceBundle]): String =
      resBundle.flatMap(
        r => tryo(
          r.getObject(str) match {
            case s: String => Full(s)
            case n: Node => Full(n.text)
            case ns: NodeSeq => Full(ns.text)
            case _ => Empty
          })
          .flatMap(s => s)).find(s => true) getOrElse {
        LiftRules.localizationLookupFailureNotice.foreach(_ (str, locale));
        str
      }
  
    def ?(str: String, locale: Locale): String = resourceValueCache.get(
      str -> 
        locale,
      if(locale.toString.startsWith("en") || ?!(str, resourceBundleList)==str) //If can not find the value from props or the local is `en`, then return 
        errorBody 
      else {
        val originalErrorMessageFromScalaCode = ErrorMessages.getValueMatches(_.startsWith(errorCode)).getOrElse("")
        // we need to keep the extra message, 
        // eg: OBP-20006: usuario le faltan uno o más roles':  CanGetUserInvitation for BankId(gh.29.uk). 
        if(failMsg.contains(originalErrorMessageFromScalaCode)){ 
          s": ${?!(str, resourceBundleList)}"+failMsg.replace(originalErrorMessageFromScalaCode,"")
        } else{
          s": ${?!(str, resourceBundleList)}"
        }
      }

      )
    
    val translatedErrorBody = ?(errorCode, locale)
    s"$errorCode$translatedErrorBody"
  }
}

object ObpApiFailure {
  def apply(failMsg: String, failCode: Int = 400, cc: Option[CallContext] = None) = {
    fullBoxOrException(Empty ~> APIFailureNewStyle(failMsg, failCode, cc.map(_.toLight)))
  }

  // overload for plain CallContext
  def apply(failMsg: String, failCode: Int, cc: CallContext) = {
    fullBoxOrException(Empty ~> APIFailureNewStyle(failMsg, failCode, Some(cc.toLight)))
  }
}


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
  // `childValue` is overridden to return `null` so newly-spawned threads do NOT
  // inherit the parent thread's ApiVersion. Same defensive pattern as
  // RequestScopeConnection.currentProxy (see that scaladoc for the full
  // explanation): when Scala's ForkJoinPool spawns a new worker mid-request,
  // the default InheritableThreadLocal childValue copies the parent's value,
  // and every subsequent TtlRunnable.restore() on that worker reverts to it —
  // even for tasks belonging to a completely different request. Returning
  // null blocks the inheritance; legitimate propagation still happens via
  // TtlRunnable's explicit capture/replay at submission time.
  //
  // https://github.com/alibaba/transmittable-thread-local/issues/100
  private val threadLocal: ThreadLocal[ApiVersion] =
    new TransmittableThreadLocal[ApiVersion]() {
      override protected def childValue(parentValue: ApiVersion): ApiVersion = null
    }

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

/**
 * any place throw this exception will send back the JsonResponse,
 * This is helpful if you want send back given error message and status code
 * @param jsonResponse
 */
case class JsonResponseException(jsonResponse: JsonResponse) extends RuntimeException with NoStackTrace

object JsonResponseException {
  /**
   *
   * @param errorMsg error message
   * @param errorCode response error code and status code
   * @param correlationId this value can be got from callContext
   */
  def apply(errorMsg: String, errorCode: Int, correlationId: String):JsonResponseException = {
    JsonResponseException(createErrorJsonResponse(errorMsg: String, errorCode: Int, correlationId: String))
  }
}

trait OBPRestHelper extends MdcLoggable {

  implicit def errorToJson(error: ErrorMessage): JValue = Extraction.decompose(error)

  val version : ApiVersion
  val versionStatus : String // TODO this should be property of ApiVersion
  //def vDottedVersion = vDottedApiVersion(version)

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
  #   "error": "OBP-30001: Bank not found. Please specify a valid value for BANK_ID. <- Full(TimeoutExceptionjava.util.concurrent.TimeoutException: The stream has not been completed in 1550 milliseconds.)"
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

  def isAutoValidate(doc: ResourceDoc, autoValidateAll: Boolean): Boolean = { //note: auto support v4.0.0 and later versions
    doc.isValidateEnabled || (autoValidateAll && !doc.isValidateDisabled && {
      // Auto support v4.0.0 and all later versions
      val docVersion = doc.implementedInApiVersion
      // Check if the version is v4.0.0 or later by comparing the version string
      docVersion match {
        case v: ScannedApiVersion =>
          // Extract version numbers and compare
          val versionStr = v.apiShortVersion.replace("v", "")
          val parts = versionStr.split("\\.")
          if (parts.length >= 2) {
            val major = parts(0).toInt
            val minor = parts(1).toInt
            major > 4 || (major == 4 && minor >= 0)
          } else {
            false
          }
        case _ => false
      }
    })
  }

  protected def registerRoutes(allResourceDocs: ArrayBuffer[ResourceDoc]): Unit = ()
}