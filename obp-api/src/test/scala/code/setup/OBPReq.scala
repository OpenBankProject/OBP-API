package code.setup

import java.nio.charset.{Charset, StandardCharsets}
import java.util.concurrent.TimeUnit

import okhttp3.{Headers => OkHeaders, MediaType => OkMediaType, OkHttpClient, Request, RequestBody, HttpUrl, Response => OkResponse}

/**
 * Immutable HTTP request builder backed by OkHttp3.
 * Drop-in replacement for dispatch's Req with the same operator surface:
 * `/`, `.GET/.POST/.PUT/.DELETE/.PATCH/.HEAD/.OPTIONS`, `<:<`, `<<?`, `<<`,
 * `.addHeader`, `.setHeader`, `.setMethod`, `.setBody`, `.setBodyEncoding`, `.secure`
 */
case class OBPReq(
  baseUrl: String,
  method: String = "GET",
  reqBody: String = "",
  bodyCharset: Charset = StandardCharsets.UTF_8,
  reqHeaders: List[(String, String)] = Nil,
  queryParams: List[(String, String)] = Nil
) {
  def /(segment: Any): OBPReq = {
    val seg = segment.toString
    val cleanBase = if (baseUrl.endsWith("/")) baseUrl.dropRight(1) else baseUrl
    // Percent-encode characters that must not appear unencoded in a URI path segment.
    // '/' is the path delimiter — encoding it prevents a URL-valued provider string
    // (e.g. "http://localhost:8016") from being split into multiple path segments.
    // '%' must be encoded FIRST — otherwise a literal '%' in a test id/label (e.g. a
    // view name "50%") would either make HttpUrl.parse reject the segment as an invalid
    // escape, or — if it happens to look like a valid escape (e.g. "%2F") — get silently
    // decoded server-side into the character it "escapes", resolving to the wrong
    // resource. This replicates dispatch's addPathPart percent-encoding behaviour.
    val encodedSeg = seg.replace("%", "%25").replace("/", "%2F").replace("?", "%3F").replace("#", "%23")
    copy(baseUrl = s"$cleanBase/$encodedSeg")
  }

  def GET: OBPReq     = copy(method = "GET")
  def POST: OBPReq    = copy(method = "POST")
  def PUT: OBPReq     = copy(method = "PUT")
  def DELETE: OBPReq  = copy(method = "DELETE")
  def PATCH: OBPReq   = copy(method = "PATCH")
  def HEAD: OBPReq    = copy(method = "HEAD")
  def OPTIONS: OBPReq = copy(method = "OPTIONS")

  def secure: OBPReq = copy(baseUrl = baseUrl.replaceFirst("^http://", "https://"))

  def <:<(hdrs: Iterable[(String, String)]): OBPReq = copy(reqHeaders = reqHeaders ++ hdrs)
  def <<?(params: Iterable[(String, String)]): OBPReq = copy(queryParams = queryParams ++ params.toList)
  def <<(body: String): OBPReq = copy(reqBody = body)

  def addHeader(name: String, value: String): OBPReq = copy(reqHeaders = reqHeaders :+ (name -> value))
  def setHeader(name: String, value: String): OBPReq = copy(reqHeaders = reqHeaders.filterNot(_._1 == name) :+ (name -> value))
  def addQueryParameter(name: String, value: String): OBPReq = <<?(List((name, value)))
  def setMethod(m: String): OBPReq = copy(method = m)
  def setBody(body: String): OBPReq = copy(reqBody = body)
  def setBodyEncoding(charset: Charset): OBPReq = copy(bodyCharset = charset)
  def setContentType(mediaType: String, charset: Charset): OBPReq =
    copy(reqHeaders = reqHeaders.filterNot(_._1 == OBPReq.ContentTypeHeader) :+ (OBPReq.ContentTypeHeader -> s"$mediaType; charset=${charset.name()}"))

  def url: String = baseUrl

  def toRequest(): Request = toOkHttpRequest

  def executeRaw(): (Int, String, OkHeaders) = {
    val response: OkResponse = OBPReq.client.newCall(toOkHttpRequest).execute()
    try {
      val code = response.code()
      val body = Option(response.body()).fold("")(_.string())
      (code, body, response.headers())
    } finally { response.close() }
  }

  def toOkHttpRequest: Request = {
    val parsedUrl = HttpUrl.parse(baseUrl)
    if (parsedUrl == null) throw new IllegalArgumentException(s"Invalid URL: $baseUrl")

    val urlBuilder = parsedUrl.newBuilder()
    queryParams.foreach { case (k, v) => urlBuilder.addQueryParameter(k, v) }

    val requestBody: RequestBody = method.toUpperCase match {
      case "GET" | "HEAD" | "OPTIONS" => null
      case _ if reqBody.isEmpty => RequestBody.create(new Array[Byte](0), null)
      case _ =>
        val mt = reqHeaders.toMap.get(OBPReq.ContentTypeHeader)
          .flatMap(ct => Option(OkMediaType.parse(ct)))
          .orNull
        RequestBody.create(reqBody.getBytes(bodyCharset), mt)
    }

    val builder = new Request.Builder()
      .url(urlBuilder.build())
      .method(method.toUpperCase, requestBody)

    // Dedupe by header name (HTTP header names are case-insensitive), keeping the LAST
    // value for a given name. reqHeaders accumulates via <:</addHeader without removing
    // same-named entries, so e.g. makePostRequest's own Content-Type default appended
    // after a caller-set Content-Type would otherwise be sent twice on the wire. "Last
    // wins" matches the Content-Type lookup above (reqHeaders.toMap already takes the
    // last occurrence) and restores the override semantics the old Map-based header
    // merge had before the OkHttp port.
    val dedupedHeaders = {
      val seen = scala.collection.mutable.LinkedHashMap[String, (String, String)]()
      reqHeaders.foreach { case (k, v) => seen(k.toLowerCase) = (k, v) }
      seen.values
    }
    dedupedHeaders.foreach { case (k, v) => builder.addHeader(k, v) }
    builder.build()
  }
}

object OBPReq {
  private[setup] val ContentTypeHeader = "Content-Type"

  val client: OkHttpClient = new OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

  def url(s: String): OBPReq = OBPReq(baseUrl = s)
  def host(h: String, p: Int): OBPReq = OBPReq(baseUrl = s"http://$h:$p")
  def host(h: String): OBPReq = OBPReq(baseUrl = s"http://$h")
}
