package code.api.util.http4s

import cats.effect._
import code.api.APIFailureNewStyle
import code.api.util.ErrorMessages._
import code.api.util.{CallContext => SharedCallContext}
import net.liftweb.common.{Failure => LiftFailure}
import net.liftweb.json.compactRender
import net.liftweb.json.JsonDSL._
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.CIString

/**
 * Converts OBP errors to http4s Response[IO].
 * Uses Lift JSON for serialization (consistent with OBP codebase).
 */
object ErrorResponseConverter {
  import net.liftweb.json.Formats
  import code.api.util.CustomJsonFormats
  
  implicit val formats: Formats = CustomJsonFormats.formats
  private val jsonContentType: `Content-Type` = `Content-Type`(MediaType.application.json)
  
  /**
   * OBP standard error response format
   */
  case class OBPErrorResponse(
    code: Int,
    message: String
  )
  
  /**
   * Convert error response to JSON string
   */
  private def toJsonString(error: OBPErrorResponse): String = {
    val json = ("code" -> error.code) ~ ("message" -> error.message)
    compactRender(json)
  }
  
  /**
   * Convert an error to http4s Response[IO]
   */
  def toHttp4sResponse(error: Throwable, callContext: SharedCallContext): IO[Response[IO]] = {
    error match {
      case e: APIFailureNewStyle =>
        apiFailureToResponse(e, callContext)
      case e =>
        unknownErrorToResponse(e, callContext)
    }
  }
  
  /**
   * Convert APIFailureNewStyle to http4s Response
   */
  def apiFailureToResponse(failure: APIFailureNewStyle, callContext: SharedCallContext): IO[Response[IO]] = {
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
   * Convert Box Failure to http4s Response
   */
  def boxFailureToResponse(failure: LiftFailure, callContext: SharedCallContext): IO[Response[IO]] = {
    val errorJson = OBPErrorResponse(400, failure.msg)
    IO.pure(
      Response[IO](org.http4s.Status.BadRequest)
        .withEntity(toJsonString(errorJson))
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
  
  /**
   * Convert unknown error to http4s Response
   */
  def unknownErrorToResponse(e: Throwable, callContext: SharedCallContext): IO[Response[IO]] = {
    val errorJson = OBPErrorResponse(500, s"$UnknownError: ${e.getMessage}")
    IO.pure(
      Response[IO](org.http4s.Status.InternalServerError)
        .withEntity(toJsonString(errorJson))
        .withContentType(jsonContentType)
        .putHeaders(org.http4s.Header.Raw(CIString("Correlation-Id"), callContext.correlationId))
    )
  }
  
  /**
   * Create error response with specific status code and message
   */
  def createErrorResponse(statusCode: Int, message: String, callContext: SharedCallContext): IO[Response[IO]] = {
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
