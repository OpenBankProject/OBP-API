package code.api.util.http4s

import cats.effect._
import code.api.APIFailureNewStyle
import code.api.util.ErrorMessages._
import code.api.util.CallContext
import net.liftweb.common.{Failure => LiftFailure}
import net.liftweb.json.compactRender
import net.liftweb.json.JsonDSL._
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.CIString

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
  import net.liftweb.json.Formats
  import code.api.util.CustomJsonFormats
  
  implicit val formats: Formats = CustomJsonFormats.formats
  private val jsonContentType: `Content-Type` = `Content-Type`(MediaType.application.json)
  
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
  
  /**
   * Convert any error to http4s Response[IO].
   */
  def toHttp4sResponse(error: Throwable, callContext: CallContext): IO[Response[IO]] = {
    error match {
      case e: APIFailureNewStyle => apiFailureToResponse(e, callContext)
      case _ => unknownErrorToResponse(error, callContext)
    }
  }
  
  /**
   * Convert APIFailureNewStyle to http4s Response.
   * Uses failCode as HTTP status and failMsg as error message.
   */
  def apiFailureToResponse(failure: APIFailureNewStyle, callContext: CallContext): IO[Response[IO]] = {
    val errorJson = OBPErrorResponse(failure.failCode, failure.failMsg)
    val status = org.http4s.Status.fromInt(failure.failCode).getOrElse(org.http4s.Status.BadRequest)
    IO.pure(
      Response[IO](status)
        .withEntity(toJsonString(errorJson))
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
  
  /**
   * Convert Lift Box Failure to http4s Response.
   * Returns 400 Bad Request with failure message.
   */
  def boxFailureToResponse(failure: LiftFailure, callContext: CallContext): IO[Response[IO]] = {
    val errorJson = OBPErrorResponse(400, failure.msg)
    IO.pure(
      Response[IO](org.http4s.Status.BadRequest)
        .withEntity(toJsonString(errorJson))
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
  
  /**
   * Convert unknown error to http4s Response.
   * Returns 500 Internal Server Error.
   */
  def unknownErrorToResponse(e: Throwable, callContext: CallContext): IO[Response[IO]] = {
    val errorJson = OBPErrorResponse(500, s"$UnknownError: ${e.getMessage}")
    IO.pure(
      Response[IO](org.http4s.Status.InternalServerError)
        .withEntity(toJsonString(errorJson))
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
  
  /**
   * Create error response with specific status code and message.
   */
  def createErrorResponse(statusCode: Int, message: String, callContext: CallContext): IO[Response[IO]] = {
    val errorJson = OBPErrorResponse(statusCode, message)
    val status = org.http4s.Status.fromInt(statusCode).getOrElse(org.http4s.Status.BadRequest)
    IO.pure(
      Response[IO](status)
        .withEntity(toJsonString(errorJson))
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
}
