package code.api.util.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.util.APIUtil
import code.api.{APIFailure, JsonResponseException, ResponseHeader}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.actor.LAFuture
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.provider._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.CIString

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.{Locale, UUID}
import scala.collection.JavaConverters._

/**
 * In-memory tally of which requests reach the Lift fallback bridge.
 *
 * Goal: data-driven prioritisation of remaining migration work. Every request
 * that lands here means no http4s handler claimed it. Once an audit run shows
 * which (method, path-bucket) pairs still hit the bridge, those buckets become
 * the next migration targets. When the map is empty for a representative
 * traffic window, the bridge can be retired.
 *
 * Path-bucket normalisation collapses common ID segments (long opaque tokens,
 * UUIDs, numbers, version segments) so we don't fill the map with one entry
 * per real-world ID value. The exact form of each bucket is logged the first
 * time it is observed.
 */
object Http4sLiftBridgeTraffic extends MdcLoggable {
  private val counts: ConcurrentHashMap[String, AtomicLong] = new ConcurrentHashMap()
  private val UUID_RE = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$".r
  private val VERSION_RE = "^v\\d+(_\\d+){2}$|^v\\d+(\\.\\d+){2}$".r
  private val NUMERIC_RE = "^\\d+$".r

  /** Buckets that look like opaque IDs:
    *   1. Plain UUID                                              → {uuid}
    *   2. All-digits                                              → {n}
    *   3. Contains a `.` (e.g. `gh.29.uk`, `some.bank.io`, `127.0.0.1`) → {id}
    *      (API-version strings like `v6.0.0` are caught earlier and kept.)
    *   4. Long-ish (≥ 12 chars) AND contains both letters & digits    → {id}
    *
    * Keeps short path keywords like `openid-connect`, `callback-1`,
    * `BANK_ID`, `accounts`, `views`.
    */
  private def bucketSegment(seg: String): String = {
    if (seg.isEmpty) return seg
    if (VERSION_RE.findFirstIn(seg).isDefined) return seg
    if (UUID_RE.findFirstIn(seg).isDefined) return "{uuid}"
    if (NUMERIC_RE.findFirstIn(seg).isDefined) return "{n}"
    if (seg.contains('.')) return "{id}"
    val hasDigit = seg.exists(_.isDigit)
    val hasLetter = seg.exists(_.isLetter)
    if (seg.length >= 12 && hasDigit && hasLetter) return "{id}"
    seg
  }

  def bucket(path: String): String =
    "/" + path.split('/').filter(_.nonEmpty).map(bucketSegment).mkString("/")

  /** Record one bridge dispatch with its outcome. Key is `METHOD BUCKET STATUS`
    * so the snapshot can distinguish:
    *   - real Lift handler work (2xx/3xx/5xx) — actual migration targets
    *   - 404 fall-throughs — test code probing for non-existent endpoints, or
    *     stale callers; not migration work
    */
  def observe(method: String, path: String, status: Int): Unit = {
    val key = s"$method ${bucket(path)} $status"
    val prev = counts.putIfAbsent(key, new AtomicLong(1L))
    if (prev == null) {
      logger.info(s"[BRIDGE-AUDIT] first hit: $key   (original path: $path)")
    } else {
      prev.incrementAndGet()
    }
  }

  /** Snapshot of (`METHOD BUCKET STATUS` → hit-count). */
  def snapshot(): Map[String, Long] =
    counts.asScala.toMap.map { case (k, v) => k -> v.get() }

  /** Wipe the in-memory tally. Mostly for tests / a manual reset after a baseline. */
  def reset(): Unit = counts.clear()

  /** Split the key string `METHOD BUCKET STATUS` back into its parts. */
  private def splitKey(key: String): (String, String, Int) = {
    // method is everything before the first space; status is the trailing int;
    // bucket is the middle.
    val firstSp = key.indexOf(' ')
    val lastSp  = key.lastIndexOf(' ')
    if (firstSp <= 0 || lastSp <= firstSp) ("?", key, 0)
    else {
      val method = key.substring(0, firstSp)
      val statusStr = key.substring(lastSp + 1)
      val status = try statusStr.toInt catch { case _: Throwable => 0 }
      val bucketStr = key.substring(firstSp + 1, lastSp)
      (method, bucketStr, status)
    }
  }

  /** Admin route: `GET /admin/lift-bridge-traffic` returns the snapshot as JSON,
    * grouped by `real_work` (non-404) vs `not_found` (404s — test probes /
    * stale URLs / dead links). Entries inside each group are sorted by hit
    * count desc.
    *
    * Wired into Http4sApp's baseServices ahead of the per-version routes so
    * the admin path can't be shadowed.
    */
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "admin" / "lift-bridge-traffic" =>
      val rows = snapshot().toList.map { case (k, n) =>
        val (m, b, s) = splitKey(k)
        (m, b, s, n)
      }
      val (notFound, realWork) = rows.partition { case (_, _, s, _) => s == 404 }
      def renderGroup(group: List[(String, String, Int, Long)]): String =
        group.sortBy { case (_, _, _, n) => -n }.map { case (m, b, s, n) =>
          s"""    {"method": ${jsonString(m)}, "bucket": ${jsonString(b)}, "status": $s, "count": $n}"""
        }.mkString(",\n")
      val totalUnique = rows.length
      val totalHits   = rows.map(_._4).sum
      val realHits    = realWork.map(_._4).sum
      val notFoundHits = notFound.map(_._4).sum
      val body =
        s"""{
           |  "unique_buckets": $totalUnique,
           |  "total_hits": $totalHits,
           |  "summary": {
           |    "real_work": {"unique_buckets": ${realWork.length}, "total_hits": $realHits},
           |    "not_found": {"unique_buckets": ${notFound.length}, "total_hits": $notFoundHits}
           |  },
           |  "real_work": [
           |${renderGroup(realWork)}
           |  ],
           |  "not_found": [
           |${renderGroup(notFound)}
           |  ]
           |}
           |""".stripMargin
      IO.pure(Response[IO]()
        .withEntity(body.getBytes("UTF-8"))
        .withHeaders(Headers(`Content-Type`(MediaType.application.json, Charset.`UTF-8`))))

    case POST -> Root / "admin" / "lift-bridge-traffic" / "reset" =>
      reset()
      IO.pure(Response[IO]()
        .withEntity("""{"status":"reset"}""".getBytes("UTF-8"))
        .withHeaders(Headers(`Content-Type`(MediaType.application.json, Charset.`UTF-8`))))
  }

  private def jsonString(s: String): String = {
    val esc = s.flatMap {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c < 0x20 => f"\\u${c.toInt}%04x"
      case c    => c.toString
    }
    s""""$esc""""
  }
}

object Http4sLiftWebBridge extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]

  // Configurable timeout for continuation resolution (default: 60 seconds)
  private lazy val continuationTimeoutMs: Long =
    APIUtil.getPropsAsLongValue("http4s.continuation.timeout.ms", 60000L)

  private lazy val apiPathZero: String    = APIUtil.getPropsValue("apiPathZero", "obp")
  private lazy val v700Path: String       = s"/$apiPathZero/v7.0.0"
  // Version that the bridge falls back to for unmigrated v7.0.0 paths.
  private lazy val fallbackVersion: String = APIUtil.getPropsValue("http4s.v700.fallback.version", "v6.0.0")
  private lazy val fallbackPath: String   = s"/$apiPathZero/$fallbackVersion"

  /**
   * If the request targets a v7.0.0 path that was not claimed by any http4s handler
   * (otherwise it would never reach this bridge), rewrite the URI to the configured
   * fallback version (default v6.0.0) so Lift can serve it transparently.
   *
   * Returns the (possibly rewritten) request and the served version string to attach
   * as X-OBP-Version-Served, or None when no rewrite was needed.
   */
  private def rewriteIfV700(req: Request[IO]): (Request[IO], Option[String]) = {
    val pathStr = req.uri.path.renderString
    if (pathStr == v700Path || pathStr.startsWith(v700Path + "/")) {
      val rewrittenPath = fallbackPath + pathStr.drop(v700Path.length)
      logger.info(s"[BRIDGE] v7.0.0 fallback: $pathStr → $rewrittenPath (served by $fallbackVersion)")
      val newUri = req.uri.withPath(Uri.Path.unsafeFromString(rewrittenPath))
      (req.withUri(newUri), Some(fallbackVersion))
    } else {
      (req, None)
    }
  }

  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req => dispatch(req)
  }

  def withStandardHeaders(routes: HttpRoutes[IO]): HttpRoutes[IO] = {
    Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
      routes.run(req).map(resp => ensureStandardHeaders(req, resp))
    }
  }

  def dispatch(req: Request[IO]): IO[Response[IO]] = {
    val (effectiveReq, servedVersion) = rewriteIfV700(req)
    val uri    = req.uri.renderString
    val method = req.method.name
    val originalPath = req.uri.path.renderString
    logger.debug(s"Http4sLiftBridge dispatching: $method $uri, S.inStatefulScope_? = ${S.inStatefulScope_?}")
    val result = for {
      bodyBytes <- effectiveReq.body.compile.to(Array)
      liftReq = buildLiftReq(effectiveReq, bodyBytes)
      liftResp <- IO {
        val session = LiftRules.statelessSession.vend.apply(liftReq)
        S.init(Full(liftReq), session) {
          logger.debug(s"Http4sLiftBridge inside S.init, S.inStatefulScope_? = ${S.inStatefulScope_?}")
          try {
            runLiftDispatch(liftReq)
          } catch {
            case JsonResponseException(jsonResponse) => jsonResponse
            case e if e.getClass.getName == "net.liftweb.http.rest.ContinuationException" =>
              resolveContinuation(e)
            case e: Throwable =>
              logger.error(
                s"[BRIDGE] Exception inside S.init for $method $uri" +
                s" | thread=${Thread.currentThread().getName}" +
                s" | exceptionClass=${e.getClass.getName}" +
                s" | message=${e.getMessage}" +
                s" | requestScopeProxy=${code.api.util.http4s.RequestScopeConnection.currentProxy.get()}",
                e
              )
              throw e
          }
        }
      }
      http4sResponse <- liftResponseToHttp4s(liftResp)
    } yield {
      logger.debug(s"[BRIDGE] Http4sLiftBridge completed: $method $uri -> ${http4sResponse.status.code}")
      logger.debug(s"Http4sLiftBridge completed: $method $uri -> ${http4sResponse.status.code}")
      val baseResp = ensureStandardHeaders(req, http4sResponse)
      val finalResp = servedVersion.fold(baseResp)(v => baseResp.putHeaders(Header.Raw(CIString("X-OBP-Version-Served"), v)))
      // Tally with the response status now known. 404s tell us "test probes /
      // stale URLs"; non-404s are real Lift work still owned by the bridge.
      Http4sLiftBridgeTraffic.observe(method, originalPath, finalResp.status.code)
      finalResp
    }
    result.handleErrorWith { e =>
      logger.error(s"[BRIDGE] Uncaught exception in dispatch: $method $uri - ${e.getMessage}", e)
      val errorBody = s"""{"error":"Internal Server Error","message":"${e.getMessage}"}"""
      Http4sLiftBridgeTraffic.observe(method, originalPath, 500)
      IO.pure(ensureStandardHeaders(req, Response[IO](
        status = org.http4s.Status.InternalServerError
      ).withEntity(errorBody.getBytes("UTF-8"))
        .withHeaders(Headers(Header.Raw(CIString("Content-Type"), "application/json; charset=utf-8")))))
    }
  }

  private def runLiftDispatch(req: Req): LiftResponse = {
    val handlers = LiftRules.statelessDispatch.toList ++ LiftRules.dispatch.toList
    logger.debug(s"[BRIDGE] runLiftDispatch: ${req.request.method} ${req.request.uri}, handlers count: ${handlers.size}")
    logger.debug(s"[BRIDGE] Request contentType: ${req.request.contentType}")
    logger.debug(s"[BRIDGE] Request body available: ${req.body.isDefined}, json available: ${req.json.isDefined}")
    logger.debug(s"[BRIDGE] Checking if any handler is defined for this request...")
    handlers.zipWithIndex.foreach { case (pf, idx) =>
      val isDefined = pf.isDefinedAt(req)
      if (isDefined) {
        logger.debug(s"[BRIDGE] Handler $idx is defined for this request!")
      }
    }
    logger.debug(s"Http4sLiftBridge runLiftDispatch: ${req.request.method} ${req.request.uri}, handlers count: ${handlers.size}")
    val handler = handlers.collectFirst { case pf if pf.isDefinedAt(req) => pf(req) }
    logger.debug(s"Http4sLiftBridge handler found: ${handler.isDefined}")
    handler match {
      case Some(run) =>
        try {
          run() match {
            case Full(resp) =>
              logger.debug(s"Http4sLiftBridge handler returned Full response")
              resp
            case ParamFailure(_, _, _, apiFailure: APIFailure) =>
              logger.debug(s"Http4sLiftBridge handler returned ParamFailure: ${apiFailure.msg}")
              APIUtil.errorJsonResponse(apiFailure.msg, apiFailure.responseCode)
            case Failure(msg, _, _) =>
              logger.debug(s"Http4sLiftBridge handler returned Failure: $msg")
              APIUtil.errorJsonResponse(msg)
            case Empty =>
              logger.debug(s"Http4sLiftBridge handler returned Empty - returning JSON 404")
              val contentType = req.request.headers("Content-Type").headOption.getOrElse("")
              APIUtil.errorJsonResponse(s"${code.api.util.ErrorMessages.InvalidUri}Current Url is (${req.request.uri}), Current Content-Type Header is ($contentType)", 404)
          }
        } catch {
          case JsonResponseException(jsonResponse) => jsonResponse
          case e if e.getClass.getName == "net.liftweb.http.rest.ContinuationException" =>
            resolveContinuation(e)
          case e: Throwable =>
            logger.error(
              s"[BRIDGE] Exception in handler run() for ${req.request.method} ${req.request.uri}" +
              s" | thread=${Thread.currentThread().getName}" +
              s" | exceptionClass=${e.getClass.getName}" +
              s" | message=${e.getMessage}" +
              s" | requestScopeProxy=${code.api.util.http4s.RequestScopeConnection.currentProxy.get()}" +
              s" | stackTrace=${e.getStackTrace.take(10).mkString(" <- ")}",
              e
            )
            throw e
        }
      case None => 
        logger.debug(s"Http4sLiftBridge no handler found - returning JSON 404 for: ${req.request.method} ${req.request.uri}")
        val contentType = req.request.headers("Content-Type").headOption.getOrElse("")
        APIUtil.errorJsonResponse(s"${code.api.util.ErrorMessages.InvalidUri}Current Url is (${req.request.uri}), Current Content-Type Header is ($contentType)", 404)
    }
  }

  // Package-visible so scoped per-prefix dispatchers (e.g. Http4sDynamicEndpoint) can reuse
  // exactly the bridge's Lift-Req construction, response conversion, and async-continuation
  // handling without duplicating ~100 lines or going through LiftRules.statelessDispatch.
  // Same logic; only visibility changed.
  def resolveContinuation(exception: Throwable): LiftResponse = {
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

  def buildLiftReq(req: Request[IO], body: Array[Byte]): Req = {
    val headers = http4sHeadersToParams(req.headers.headers)
    val params = http4sParamsToParams(req.uri.query.multiParams.toList)
    val httpRequest = new Http4sLiftRequest(
      req = req,
      body = body,
      headerParams = headers,
      queryParams = params
    )
    val liftReq = Req(
      httpRequest,
      LiftRules.statelessRewrite.toList,
      Nil,
      LiftRules.statelessReqTest.toList,
      System.nanoTime()
    )
    val contentType = headers.find(_.name.equalsIgnoreCase("Content-Type")).map(_.values.mkString(",")).getOrElse("none")
    val authHeader = headers.find(_.name.equalsIgnoreCase("Authorization")).map(_.values.mkString(",")).getOrElse("none")
    val bodySize = body.length
    val bodyPreview = if (body.length > 0) new String(body.take(200), "UTF-8") else "empty"
    logger.debug(s"[BRIDGE] buildLiftReq: method=${liftReq.request.method}, uri=${liftReq.request.uri}, path=${liftReq.path.partPath.mkString("/")}, wholePath=${liftReq.path.wholePath.mkString("/")}, contentType=$contentType, authHeader=$authHeader, bodySize=$bodySize, bodyPreview=$bodyPreview")
    logger.debug(s"[BRIDGE] Req.json = ${liftReq.json}, Req.body = ${liftReq.body}")
    logger.debug(s"Http4sLiftBridge buildLiftReq: method=${liftReq.request.method}, uri=${liftReq.request.uri}, path=${liftReq.path.partPath.mkString("/")}")
    liftReq
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

  def liftResponseToHttp4s(response: LiftResponse): IO[Response[IO]] = {
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
    val status = org.http4s.Status.fromInt(code).getOrElse(org.http4s.Status.InternalServerError)
    
    // Log response construction for debugging protocol issues
    if (code >= 400) {
      val bodyPreview = if (body.length > 0) new String(body.take(100), "UTF-8") else "empty"
      logger.debug(s"[BRIDGE] buildHttp4sResponse: code=$code, status=$status, bodySize=${body.length}, bodyPreview=$bodyPreview")
    }
    
    Response[IO](status = status)
      .withEntity(body)
      .withHeaders(http4sHeaders)
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

  def ensureStandardHeaders(req: Request[IO], resp: Response[IO]): Response[IO] = {
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
    // Thread-safe attribute store using ConcurrentHashMap
    private val attributesStore = new ConcurrentHashMap[String, Any]()
    @volatile private var maxInactive: Long = 0L
    private val createdAt: Long = System.currentTimeMillis()
    def link(liftSession: LiftSession): Unit = ()
    def unlink(liftSession: LiftSession): Unit = ()
    def maxInactiveInterval: Long = maxInactive
    def setMaxInactiveInterval(interval: Long): Unit = { maxInactive = interval }
    def lastAccessedTime: Long = createdAt
    def setAttribute(name: String, value: Any): Unit = attributesStore.put(name, value)
    def attribute(name: String): Any = attributesStore.get(name)
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
    def contentType: net.liftweb.common.Box[String] = {
      // First try to get from http4s contentType
      req.contentType match {
        case Some(ct) => 
          // Content-Type header contains mediaType and optional charset
          // Convert to string format that Lift expects (e.g., "application/json")
          val mediaTypeStr = ct.mediaType.mainType + "/" + ct.mediaType.subType
          val charsetStr = ct.charset.map(cs => s"; charset=${cs.nioCharset.name}").getOrElse("")
          Full(mediaTypeStr + charsetStr)
        case None => 
          // Fallback to Content-Type header
          headerParams.find(_.name.equalsIgnoreCase("Content-Type")).flatMap(_.values.headOption) match {
            case Some(ct) => Full(ct)
            case None => Empty
          }
      }
    }
    def uri: String = uriPath
    def url: String = req.uri.renderString
    def queryString: net.liftweb.common.Box[String] = if (uriQuery.nonEmpty) Full(uriQuery) else Empty
    def param(name: String): List[String] = queryParams.find(_.name == name).map(_.values).getOrElse(Nil)
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
