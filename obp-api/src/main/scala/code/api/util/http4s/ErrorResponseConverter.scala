package code.api.util.http4s

import org.json4s._
import cats.effect._
import code.api.APIFailureNewStyle
import code.api.JsonResponseException
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{ErrorMessageBG, ErrorMessagesBG}
import code.api.util.APIUtil.JsonResponseExtractor
import code.api.util.BerlinGroupError
import code.api.util.ErrorMessages._
import code.api.util.CallContext
import net.liftweb.common.{Failure => LiftFailure}
import com.openbankproject.commons.util.JsonAliases.parse
import org.json4s.Extraction
import com.openbankproject.commons.util.JsonAliases.compactRender
import org.json4s.JsonDSL._
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.CIString
import org.slf4j.LoggerFactory

/**
 * Converts OBP errors to http4s Response[IO].
 * 
 * Handles:
 * - APIFailureNewStyle (structured errors with code and message)
 * - Box Failure (Lift framework errors)
 * - Unknown exceptions
 * 
 * All responses include:
 * - JSON body with code and message
 * - Correlation-Id header for request tracing
 * - Appropriate HTTP status code
 */
object ErrorResponseConverter {
  import org.json4s.Formats
  import code.api.util.CustomJsonFormats

  private val logger = LoggerFactory.getLogger(getClass)
  implicit val formats: Formats = CustomJsonFormats.formats
  private val jsonContentType: `Content-Type` = `Content-Type`(MediaType.application.json)

  private val obpErrorCodePrefix = "^OBP-\\d{5}: ".r

  private def tryExtractApiFailureFromExceptionMessage(error: Throwable): Option[APIFailureNewStyle] = {
    val msg = Option(error.getMessage).getOrElse("").trim
    if (msg.startsWith("{") && msg.contains("\"failCode\"") && msg.contains("\"failMsg\"")) {
      try {
        val jv       = parse(msg)
        val failCode = (jv \ "failCode").extract[Int]
        val failMsg  = (jv \ "failMsg").extract[String]
        Some(APIFailureNewStyle(failMsg, failCode))
      } catch {
        case _: Throwable => None
      }
    } else if (obpErrorCodePrefix.findFirstIn(msg).isDefined) {
      // Plain Exception("OBP-XXXXX: ...") thrown by fullBoxOrException(Failure(msg)) — Lift treats these as 400.
      Some(APIFailureNewStyle(msg, 400))
    } else {
      None
    }
  }
  
  /**
   * OBP standard error response format.
   */
  case class OBPErrorResponse(
    code: Int,
    message: String
  )

  /**
   * Convert error response to JSON string using Lift JSON.
   */
  private def toJsonString(error: OBPErrorResponse): String = {
    val json = ("code" -> error.code) ~ ("message" -> error.message)
    compactRender(json)
  }

  private def isBerlinGroupRequest(callContext: CallContext): Boolean =
    callContext.url.contains(ConstantsBG.berlinGroupVersion1.urlPrefix)

  /** Mirror the BG error body from APIUtil.failedJsonResponse for http4s paths. */
  private def toBgErrorBody(statusCode: Int, message: String, callContext: CallContext): String = {
    val pathOpt = Some(callContext.url)
    val codeText = BerlinGroupError.translateToBerlinGroupError(statusCode.toString, message)
    val errBg = ErrorMessagesBG(tppMessages = List(ErrorMessageBG(category = "ERROR", code = codeText, path = pathOpt, text = message)))
    compactRender(Extraction.decompose(errBg))
  }
  
  /**
   * Convert any error to http4s Response[IO].
   */
  def toHttp4sResponse(error: Throwable, callContext: CallContext): IO[Response[IO]] = {
    error match {
      case e: APIFailureNewStyle => apiFailureToResponse(e, callContext)
      case JsonResponseException(jsonResponse) =>
        // Force-Error / JSON-schema validation (APIUtil.afterAuthenticateInterceptResult, applied
        // inside the auth/session-context chain) and dynamic-resource-doc permission errors
        // (NewStyle) are raised as JsonResponseException carrying a Lift JsonResponse. Lift's
        // OBPRestHelper catches these and returns the embedded response; mirror that here using
        // the JsonResponse's own status code (no OBP-prefix remapping).
        jsonResponse match {
          case JsonResponseExtractor(message, code) => jsonErrorResponse(code, message, callContext)
          case _                                    => unknownErrorToResponse(error, callContext)
        }
      case _ =>
        tryExtractApiFailureFromExceptionMessage(error) match {
          case Some(apiFailure) => apiFailureToResponse(apiFailure, callContext)
          case None => unknownErrorToResponse(error, callContext)
        }
    }
  }

  /** Build a JSON error response using the supplied status code verbatim (used for
   *  JsonResponseException, whose embedded JsonResponse already carries the final code). */
  private def jsonErrorResponse(code: Int, message: String, callContext: CallContext): IO[Response[IO]] = {
    val body = if (isBerlinGroupRequest(callContext)) toBgErrorBody(code, message, callContext)
               else toJsonString(OBPErrorResponse(code, message))
    val status = org.http4s.Status.fromInt(code).getOrElse(org.http4s.Status.BadRequest)
    IO.pure(
      Response[IO](status)
        .withEntity(body)
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
  
  /** Old-style versions keep raw 400 codes — they never promote to 403/401/etc.
   *  Mirrors the same set used in ResourceDocMiddleware.authenticate.
   */
  private val oldStyleShortVersions = Set("v1.2.1", "v1.3.0", "v1.4.0", "v2.0.0")

  /**
   * Translate a 400 default with an OBP-prefixed message to the canonical status
   * Lift assigns (403 for role/view-access codes, 401 for auth codes, etc.) via
   * ErrorMessages.getCodeByOBPPrefix. Leaves non-400 failCodes (caller set
   * status explicitly) untouched. Old-style versions (v1.x, v2.0.0) keep the
   * 400 — they return 400 for every error per the long-standing OBP convention.
   */
  private def resolveStatusCode(failCode: Int, failMsg: String, callContext: CallContext): Int =
    if (failCode == 400 && !oldStyleShortVersions.contains(callContext.implementedInVersion))
      code.api.util.ErrorMessages.getCodeByOBPPrefix(failMsg)
    else failCode

  /**
   * Convert APIFailureNewStyle to http4s Response.
   * Uses failCode as HTTP status and failMsg as error message.
   */
  def apiFailureToResponse(failure: APIFailureNewStyle, callContext: CallContext): IO[Response[IO]] = {
    val resolvedCode = resolveStatusCode(failure.failCode, failure.failMsg, callContext)
    val body = if (isBerlinGroupRequest(callContext)) toBgErrorBody(resolvedCode, failure.failMsg, callContext)
               else toJsonString(OBPErrorResponse(resolvedCode, failure.failMsg))
    val status = org.http4s.Status.fromInt(resolvedCode).getOrElse(org.http4s.Status.BadRequest)
    IO.pure(
      Response[IO](status)
        .withEntity(body)
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
  
  /**
   * Convert Lift Box Failure to http4s Response.
   * Returns 400 Bad Request with failure message.
   */
  def boxFailureToResponse(failure: LiftFailure, callContext: CallContext): IO[Response[IO]] = {
    val body = if (isBerlinGroupRequest(callContext)) toBgErrorBody(400, failure.msg, callContext)
               else toJsonString(OBPErrorResponse(400, failure.msg))
    IO.pure(
      Response[IO](org.http4s.Status.BadRequest)
        .withEntity(body)
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
  
  /**
   * Convert unknown error to http4s Response.
   * Returns 500 Internal Server Error.
   */
  def unknownErrorToResponse(e: Throwable, callContext: CallContext): IO[Response[IO]] = {
    logger.error(s"unknownErrorToResponse says: 500 returned (correlationId=${callContext.correlationId})", e)
    val message = s"$UnknownError: ${e.getMessage}"
    val body = if (isBerlinGroupRequest(callContext)) toBgErrorBody(500, message, callContext)
               else toJsonString(OBPErrorResponse(500, message))
    IO.pure(
      Response[IO](org.http4s.Status.InternalServerError)
        .withEntity(body)
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }

  /**
   * Create error response with specific status code and message.
   */
  def createErrorResponse(statusCode: Int, message: String, callContext: CallContext): IO[Response[IO]] = {
    val body = if (isBerlinGroupRequest(callContext)) toBgErrorBody(statusCode, message, callContext)
               else toJsonString(OBPErrorResponse(statusCode, message))
    val status = org.http4s.Status.fromInt(statusCode).getOrElse(org.http4s.Status.BadRequest)
    IO.pure(
      Response[IO](status)
        .withEntity(body)
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
}
