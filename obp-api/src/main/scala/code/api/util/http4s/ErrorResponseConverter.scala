package code.api.util.http4s

import cats.effect._
import code.api.util.ErrorMessages._
import code.api.util.CallContext
import net.liftweb.json.{JInt, JString, parseOpt}
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
  private val internalFieldsFailCode = "failCode"
  private val internalFieldsFailMsg = "failMsg"
  
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
    parseApiFailureFromExceptionMessage(error).map { failure =>
      createErrorResponse(failure.code, failure.message, callContext)
    }.getOrElse {
      unknownErrorToResponse(error, callContext)
    }
  }
  
  private def parseApiFailureFromExceptionMessage(error: Throwable): Option[OBPErrorResponse] = {
    Option(error.getMessage).flatMap(parseOpt).flatMap { json =>
      (json \ internalFieldsFailCode, json \ internalFieldsFailMsg) match {
        case (JInt(code), JString(message)) => Some(OBPErrorResponse(code.toInt, message))
        case _ => None
      }
    }
  }
  
  /**
   * Convert unknown error to http4s Response.
   * Returns 500 Internal Server Error.
   */
  def unknownErrorToResponse(e: Throwable, callContext: CallContext): IO[Response[IO]] = {
    val errorJson = OBPErrorResponse(500, UnknownError)
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
