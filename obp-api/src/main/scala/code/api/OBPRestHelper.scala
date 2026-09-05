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

import org.json4s._
import java.lang.ThreadLocal
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
import org.json4s.Extraction
import org.json4s.JsonAST.JValue

import java.util.{Locale, MissingResourceException, ResourceBundle}
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NoStackTrace

/**
 * Lightweight JSON HTTP response carrier (replaces the former Lift JsonResponse).
 * Carries a JSON body + HTTP headers + status code; the http4s middleware reads these to
 * build the real org.http4s.Response[IO].  Cookies are accepted but ignored (http4s path
 * never sets Lift cookies).
 */
case class JsonResponse(body: JValue,
                        headers: List[(String, String)],
                        cookies: List[Any],
                        code: Int)
object JsonResponse {
  def apply(body: JValue, code: Int): JsonResponse = JsonResponse(body, Nil, Nil, code)
}

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

    val localeUrlParameter = getHttpRequestUrlParam(ccl.map(_.url).getOrElse(""), PARAM_LOCALE)
    val localeFromUrl = I18NUtil.computeLocale(localeUrlParameter)
    val locale: Locale =
      if (localeFromUrl.toString.equals("")) I18NUtil.getDefaultLocale()
      else localeFromUrl

    val bundles: List[ResourceBundle] =
      try { ResourceBundle.getBundle("i18n.lift-core", locale) :: Nil }
      catch { case _: MissingResourceException => Nil }

    def lookup(key: String): Option[String] =
      bundles.flatMap(b => try { Some(b.getString(key)) } catch { case _: MissingResourceException => None }).headOption

    val translatedErrorBody: String = lookup(errorCode) match {
      case None => errorBody
      case Some(translated) if locale.toString.startsWith("en") || translated == errorCode => errorBody
      case Some(translated) =>
        val originalErrorMessageFromScalaCode = ErrorMessages.getValueMatches(_.startsWith(errorCode)).getOrElse("")
        if (failMsg.contains(originalErrorMessageFromScalaCode))
          s": $translated" + failMsg.replace(originalErrorMessageFromScalaCode, "")
        else
          s": $translated"
    }
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
      // Public, not protected: TransmittableThreadLocal declares childValue public, and an
      // override may not narrow that. 2.12 accepted the narrowing; 2.13 rejects it.
      override def childValue(parentValue: ApiVersion): ApiVersion = null
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

  // lazy: Scala 3 does not allow a lazy val to override an abstract strict val. ScannedApis
  // (mixed into the UK Open Banking / Berlin Group helpers) and three of the OBPAPI*_*_* objects
  // implement these with `lazy val version`/`lazy val versionStatus`; the rest use a strict val,
  // which still satisfies an abstract lazy val, so this widens the contract without breaking them.
  lazy val version : ApiVersion
  lazy val versionStatus : String // TODO this should be property of ApiVersion
  //def vDottedVersion = vDottedApiVersion(version)

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

  def isAutoValidate(doc: ResourceDoc, autoValidateAll: Boolean): Boolean = {
    doc.isValidateEnabled || (autoValidateAll && !doc.isValidateDisabled && {
      val docVersion = doc.implementedInApiVersion
      docVersion match {
        case v: ScannedApiVersion =>
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
}