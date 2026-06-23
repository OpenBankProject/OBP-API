package code.setup

import java.nio.charset.{Charset, StandardCharsets}
import java.util.concurrent.TimeUnit

import okhttp3.{Headers => OkHeaders, MediaType => OkMediaType, OkHttpClient, Request, RequestBody, HttpUrl}

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
  reqHeaders: Map[String, String] = Map.empty,
  queryParams: Map[String, String] = Map.empty
) {
  def /(segment: Any): OBPReq = {
    val seg = segment.toString
    val cleanBase = if (baseUrl.endsWith("/")) baseUrl.dropRight(1) else baseUrl
    val cleanSeg  = if (seg.startsWith("/")) seg.drop(1) else seg
    copy(baseUrl = s"$cleanBase/$cleanSeg")
  }

  def GET: OBPReq     = copy(method = "GET")
  def POST: OBPReq    = copy(method = "POST")
  def PUT: OBPReq     = copy(method = "PUT")
  def DELETE: OBPReq  = copy(method = "DELETE")
  def PATCH: OBPReq   = copy(method = "PATCH")
  def HEAD: OBPReq    = copy(method = "HEAD")
  def OPTIONS: OBPReq = copy(method = "OPTIONS")

  def secure: OBPReq = copy(baseUrl = baseUrl.replaceFirst("^http://", "https://"))

  def <:<(hdrs: Map[String, String]): OBPReq = copy(reqHeaders = reqHeaders ++ hdrs)
  def <<?(params: Iterable[(String, String)]): OBPReq = copy(queryParams = queryParams ++ params)
  def <<(body: String): OBPReq = copy(reqBody = body)

  def addHeader(name: String, value: String): OBPReq = copy(reqHeaders = reqHeaders + (name -> value))
  def setHeader(name: String, value: String): OBPReq = copy(reqHeaders = reqHeaders + (name -> value))
  def addQueryParameter(name: String, value: String): OBPReq = <<?(List((name, value)))
  def setMethod(m: String): OBPReq = copy(method = m)
  def setBody(body: String): OBPReq = copy(reqBody = body)
  def setBodyEncoding(charset: Charset): OBPReq = copy(bodyCharset = charset)
  def setContentType(mediaType: String, charset: Charset): OBPReq =
    copy(reqHeaders = reqHeaders + ("Content-Type" -> s"$mediaType; charset=${charset.name()}"))

  def url: String = baseUrl

  def toRequest(): Request = toOkHttpRequest

  def toOkHttpRequest: Request = {
    val parsedUrl = HttpUrl.parse(baseUrl)
    if (parsedUrl == null) throw new IllegalArgumentException(s"Invalid URL: $baseUrl")

    val urlBuilder = parsedUrl.newBuilder()
    queryParams.foreach { case (k, v) => urlBuilder.addQueryParameter(k, v) }

    val requestBody: RequestBody = method.toUpperCase match {
      case "GET" | "HEAD" | "OPTIONS" => null
      case _ if reqBody.isEmpty => RequestBody.create(new Array[Byte](0), null)
      case _ =>
        val mt = reqHeaders.get("Content-Type")
          .flatMap(ct => Option(OkMediaType.parse(ct)))
          .orNull
        RequestBody.create(reqBody.getBytes(bodyCharset), mt)
    }

    val builder = new Request.Builder()
      .url(urlBuilder.build())
      .method(method.toUpperCase, requestBody)

    reqHeaders.foreach { case (k, v) => builder.addHeader(k, v) }
    builder.build()
  }
}

object OBPReq {
  val client: OkHttpClient = new OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

  def url(s: String): OBPReq = OBPReq(baseUrl = s)
  def host(h: String, p: Int): OBPReq = OBPReq(baseUrl = s"http://$h:$p")
  def host(h: String): OBPReq = OBPReq(baseUrl = s"http://$h")
}
