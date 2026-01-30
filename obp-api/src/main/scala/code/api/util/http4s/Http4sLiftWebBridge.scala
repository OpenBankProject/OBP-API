package code.api.util.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.{APIFailure, JsonResponseException, ResponseHeader}
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.actor.LAFuture
import net.liftweb.common.{Box, Empty, Failure, Full, ParamFailure}
import net.liftweb.http._
import net.liftweb.http.provider.{HTTPContext, HTTPParam, HTTPProvider, HTTPRequest, HTTPSession, HTTPCookie, RetryState}
import org.http4s._
import org.typelevel.ci.CIString

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import java.util.{Locale, UUID}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

object Http4sLiftWebBridge extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]

  // Configurable timeout for continuation resolution (default: 60 seconds)
  private lazy val continuationTimeoutMs: Long =
    APIUtil.getPropsAsLongValue("http4s.continuation.timeout.ms", 60000L)

  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req => dispatch(req)
  }

  def withStandardHeaders(routes: HttpRoutes[IO]): HttpRoutes[IO] = {
    Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
      routes.run(req).map(resp => ensureStandardHeaders(req, resp))
    }
  }

  def dispatch(req: Request[IO]): IO[Response[IO]] = {
    val uri = req.uri.renderString
    val method = req.method.name
    logger.debug(s"Http4sLiftBridge dispatching: $method $uri")
    for {
      bodyBytes <- req.body.compile.to(Array)
      liftReq = buildLiftReq(req, bodyBytes)
      liftResp <- IO {
        val session = LiftRules.statelessSession.vend.apply(liftReq)
        S.init(Full(liftReq), session) {
          try {
            runLiftDispatch(liftReq)
          } catch {
            case JsonResponseException(jsonResponse) => jsonResponse
            case e if e.getClass.getName == "net.liftweb.http.rest.ContinuationException" =>
              resolveContinuation(e)
          }
        }
      }
      http4sResponse <- liftResponseToHttp4s(liftResp)
    } yield {
      logger.debug(s"Http4sLiftBridge completed: $method $uri -> ${http4sResponse.status.code}")
      ensureStandardHeaders(req, http4sResponse)
    }
  }

  private def runLiftDispatch(req: Req): LiftResponse = {
    val handlers = LiftRules.statelessDispatch.toList ++ LiftRules.dispatch.toList
    val handler = handlers.collectFirst { case pf if pf.isDefinedAt(req) => pf(req) }
    handler match {
      case Some(run) =>
        try {
          run() match {
            case Full(resp) => resp
            case ParamFailure(_, _, _, apiFailure: APIFailure) =>
              APIUtil.errorJsonResponse(apiFailure.msg, apiFailure.responseCode)
            case Failure(msg, _, _) =>
              APIUtil.errorJsonResponse(msg)
            case Empty =>
              NotFoundResponse()
          }
        } catch {
          case JsonResponseException(jsonResponse) => jsonResponse
          case e if e.getClass.getName == "net.liftweb.http.rest.ContinuationException" =>
            resolveContinuation(e)
        }
      case None => NotFoundResponse()
    }
  }

  private def resolveContinuation(exception: Throwable): LiftResponse = {
    logger.debug(s"Resolving ContinuationException for async Lift handler")
    val func =
      ReflectUtils
        .getCallByNameValue(exception, "f")
        .asInstanceOf[((=> LiftResponse) => Unit) => Unit]
    val future = new LAFuture[LiftResponse]
    val satisfy: (=> LiftResponse) => Unit = response => future.satisfy(response)
    func(satisfy)
    future.get(continuationTimeoutMs).openOr {
      logger.warn(s"Continuation timeout after ${continuationTimeoutMs}ms, returning InternalServerError")
      InternalServerErrorResponse()
    }
  }

  private def buildLiftReq(req: Request[IO], body: Array[Byte]): Req = {
    val headers = http4sHeadersToParams(req.headers.headers)
    val params = http4sParamsToParams(req.uri.query.multiParams.toList)
    val httpRequest = new Http4sLiftRequest(
      req = req,
      body = body,
      headerParams = headers,
      queryParams = params
    )
    Req(
      httpRequest,
      LiftRules.statelessRewrite.toList,
      Nil,
      LiftRules.statelessReqTest.toList,
      System.nanoTime()
    )
  }

  private def http4sHeadersToParams(headers: List[Header.Raw]): List[HTTPParam] = {
    headers
      .groupBy(_.name.toString)
      .toList
      .map { case (name, values) =>
        HTTPParam(name, values.map(_.value))
      }
  }

  private def http4sParamsToParams(params: List[(String, collection.Seq[String])]): List[HTTPParam] = {
    params.map { case (name, values) =>
      HTTPParam(name, values.toList)
    }
  }

  private def liftResponseToHttp4s(response: LiftResponse): IO[Response[IO]] = {
    response.toResponse match {
      case InMemoryResponse(data, headers, _, code) =>
        IO.pure(buildHttp4sResponse(code, data, headers))
      case StreamingResponse(data, onEnd, _, headers, _, code) =>
        IO {
          try {
            val bytes = readAllBytes(data.asInstanceOf[InputStream])
            buildHttp4sResponse(code, bytes, headers)
          } finally {
            onEnd()
          }
        }
      case OutputStreamResponse(out, _, headers, _, code) =>
        IO {
          val baos = new ByteArrayOutputStream()
          out(baos)
          buildHttp4sResponse(code, baos.toByteArray, headers)
        }
      case basic: BasicResponse =>
        IO.pure(buildHttp4sResponse(basic.code, Array.emptyByteArray, basic.headers))
    }
  }

  private def buildHttp4sResponse(code: Int, body: Array[Byte], headers: List[(String, String)]): Response[IO] = {
    val hasContentType = headers.exists { case (name, _) => name.equalsIgnoreCase("Content-Type") }
    val normalizedHeaders = if (hasContentType) {
      headers
    } else {
      ("Content-Type", "application/json; charset=utf-8") :: headers
    }
    val http4sHeaders = Headers(
      normalizedHeaders.map { case (name, value) => Header.Raw(CIString(name), value) }
    )
    Response[IO](
      status = org.http4s.Status.fromInt(code).getOrElse(org.http4s.Status.InternalServerError)
    ).withEntity(body).withHeaders(http4sHeaders)
  }

  private def readAllBytes(input: InputStream): Array[Byte] = {
    val buffer = new ByteArrayOutputStream()
    val chunk = new Array[Byte](4096)
    var read = input.read(chunk)
    while (read != -1) {
      buffer.write(chunk, 0, read)
      read = input.read(chunk)
    }
    buffer.toByteArray
  }

  private def ensureStandardHeaders(req: Request[IO], resp: Response[IO]): Response[IO] = {
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

  private object Http4sLiftContext extends HTTPContext {
    // Thread-safe attribute store using ConcurrentHashMap
    private val attributesStore = new ConcurrentHashMap[String, Any]()
    def path: String = ""
    def resource(path: String): java.net.URL = null
    def resourceAsStream(path: String): InputStream = null
    def mimeType(path: String): net.liftweb.common.Box[String] = Empty
    def initParam(name: String): net.liftweb.common.Box[String] = Empty
    def initParams: List[(String, String)] = Nil
    def attribute(name: String): net.liftweb.common.Box[Any] = Box(Option(attributesStore.get(name)))
    def attributes: List[(String, Any)] = attributesStore.asScala.toList
    def setAttribute(name: String, value: Any): Unit = attributesStore.put(name, value)
    def removeAttribute(name: String): Unit = attributesStore.remove(name)
  }

  private object Http4sLiftProvider extends HTTPProvider {
    override protected def context: HTTPContext = Http4sLiftContext
  }

  private final class Http4sLiftSession(val sessionId: String) extends HTTPSession {
    private val attributesStore = scala.collection.mutable.Map.empty[String, Any]
    private var maxInactive: Long = 0L
    private val createdAt: Long = System.currentTimeMillis()
    def link(liftSession: LiftSession): Unit = ()
    def unlink(liftSession: LiftSession): Unit = ()
    def maxInactiveInterval: Long = maxInactive
    def setMaxInactiveInterval(interval: Long): Unit = { maxInactive = interval }
    def lastAccessedTime: Long = createdAt
    def setAttribute(name: String, value: Any): Unit = attributesStore.update(name, value)
    def attribute(name: String): Any = attributesStore.getOrElse(name, null)
    def removeAttribute(name: String): Unit = attributesStore.remove(name)
    def terminate: Unit = ()
  }

  private final class Http4sLiftRequest(
    req: Request[IO],
    body: Array[Byte],
    headerParams: List[HTTPParam],
    queryParams: List[HTTPParam]
  ) extends HTTPRequest {
    private val sessionValue = new Http4sLiftSession(UUID.randomUUID().toString)
    private val uriPath = req.uri.path.renderString
    private val uriQuery = req.uri.query.renderString
    private val remoteAddr = req.remoteAddr
    def cookies: List[HTTPCookie] = Nil
    def provider: HTTPProvider = Http4sLiftProvider
    def authType: net.liftweb.common.Box[String] = Empty
    def headers(name: String): List[String] =
      headerParams.find(_.name.equalsIgnoreCase(name)).map(_.values).getOrElse(Nil)
    def headers: List[HTTPParam] = headerParams
    def contextPath: String = ""
    def context: HTTPContext = Http4sLiftContext
    def contentType: net.liftweb.common.Box[String] = req.contentType.map(_.mediaType.toString)
    def uri: String = uriPath
    def url: String = req.uri.renderString
    def queryString: net.liftweb.common.Box[String] = if (uriQuery.nonEmpty) Full(uriQuery) else Empty
    def param(name: String): List[String] = req.uri.query.multiParams.getOrElse(name, Nil).toList
    def params: List[HTTPParam] = queryParams
    def paramNames: List[String] = queryParams.map(_.name).distinct
    def session: HTTPSession = sessionValue
    def destroyServletSession(): Unit = ()
    def sessionId: net.liftweb.common.Box[String] = Full(sessionValue.sessionId)
    def remoteAddress: String = remoteAddr.map(_.toUriString).getOrElse("")
    def remotePort: Int = req.uri.port.getOrElse(0)
    def remoteHost: String = remoteAddr.map(_.toUriString).getOrElse("")
    def serverName: String = req.uri.host.map(_.value).getOrElse("localhost")
    def scheme: String = req.uri.scheme.map(_.value).getOrElse("http")
    def serverPort: Int = req.uri.port.getOrElse(0)
    def method: String = req.method.name
    def suspendResumeSupport_? : Boolean = false
    def resumeInfo: Option[(Req, LiftResponse)] = None
    def suspend(timeout: Long): RetryState.Value = RetryState.TIMED_OUT
    def resume(what: (Req, LiftResponse)): Boolean = false
    def inputStream: InputStream = new ByteArrayInputStream(body)
    def multipartContent_? : Boolean = contentType.exists(_.toLowerCase.contains("multipart/"))
    def extractFiles: List[net.liftweb.http.ParamHolder] = Nil
    def locale: net.liftweb.common.Box[Locale] = Empty
    def setCharacterEncoding(encoding: String): Unit = ()
    def snapshot: HTTPRequest = this
    def userAgent: net.liftweb.common.Box[String] = header("User-Agent")
  }
}
