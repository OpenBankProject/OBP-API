package code.api.util.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.ResponseHeader
import org.http4s._
import org.typelevel.ci.CIString

import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import java.util.UUID

/**
 * Standard HTTP response headers applied to every response.
 *
 * Extracted from Http4sLiftWebBridge so Http4sApp can apply them
 * without depending on the bridge at all.
 */
object Http4sStandardHeaders {

  def apply(req: Request[IO], resp: Response[IO]): Response[IO] = {
    val now = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME)
    val existing = resp.headers.headers
    def hasHeader(name: String): Boolean =
      existing.exists(_.name.toString.equalsIgnoreCase(name))
    val existingCorrelationId = existing
      .find(_.name.toString.equalsIgnoreCase(ResponseHeader.`Correlation-Id`))
      .map(_.value)
      .getOrElse("")
    val correlationId =
      Option(existingCorrelationId).map(_.trim).filter(_.nonEmpty)
        .orElse(req.headers.headers.find(_.name.toString.equalsIgnoreCase("X-Request-ID")).map(_.value))
        .getOrElse(UUID.randomUUID().toString)
    val extraHeaders = List.newBuilder[Header.Raw]
    if (existingCorrelationId.trim.isEmpty) {
      extraHeaders += Header.Raw(CIString(ResponseHeader.`Correlation-Id`), correlationId)
    }
    if (!hasHeader("Cache-Control")) {
      extraHeaders += Header.Raw(CIString("Cache-Control"), "no-cache, private, no-store")
    }
    if (!hasHeader("Pragma")) {
      extraHeaders += Header.Raw(CIString("Pragma"), "no-cache")
    }
    if (!hasHeader("Expires")) {
      extraHeaders += Header.Raw(CIString("Expires"), now)
    }
    if (!hasHeader("X-Frame-Options")) {
      extraHeaders += Header.Raw(CIString("X-Frame-Options"), "DENY")
    }
    val headersToAdd = extraHeaders.result()
    if (headersToAdd.isEmpty) resp
    else {
      val filtered = resp.headers.headers.filterNot(h =>
        h.name.toString.equalsIgnoreCase(ResponseHeader.`Correlation-Id`) &&
          h.value.trim.isEmpty
      )
      resp.copy(headers = Headers(filtered) ++ Headers(headersToAdd))
    }
  }
}
