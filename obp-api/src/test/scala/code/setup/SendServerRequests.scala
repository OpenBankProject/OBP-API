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
package code.setup

import java.net.URLDecoder
import java.nio.charset.{Charset, StandardCharsets}
import java.util.TimeZone

import code.api.ResponseHeader
import net.liftweb.common.Full
import net.liftweb.util.Helpers._
import okhttp3.{Headers => OkHeaders}
import org.json4s.JsonAST.JValue
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

case class APIResponse(code: Int, body: JValue, headers: Option[OkHeaders])

/**
  * This trait simulate the Rest process, HTTP parameters --> Reset parameters
  * simulate the four methods GET, POST, DELETE and POST
  * Prepare the Headers, query parameters and form parameters, send these to OBP-API
  * and get the response code and response body back.
  *
  */
trait SendServerRequests {

  TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

  import code.api.util.APIUtil.OAuth.{Consumer, Token}

  implicit def Request2RequestSigner(r: OBPReq): RequestSigner = new RequestSigner(r)

  class RequestSigner(rb: OBPReq) {
    def <@(consumer: Consumer, token: Token): OBPReq =
      rb <:< Map("Authorization" -> s"""DirectLogin token="${token.value}"""")
    def <@(consumerAndToken: Option[(Consumer, Token)]): OBPReq =
      consumerAndToken match {
        case Some((_, token)) => rb <:< Map("Authorization" -> s"""DirectLogin token="${token.value}"""")
        case None => rb
      }
  }

  protected def url(s: String): OBPReq = OBPReq.url(s)
  protected def host(h: String, p: Int): OBPReq = OBPReq.host(h, p)
  protected def host(h: String): OBPReq = OBPReq.host(h)

  case class ReqData(
    url: String,
    method: String,
    body: String,
    body_encoding: String,
    headers: Map[String, String],
    query_params: Map[String, String],
    form_params: Map[String, String]
  )

  def encode_%(s: String) = java.net.URLEncoder.encode(s, StandardCharsets.UTF_8.name())
  def decode_%(s: String) = URLDecoder.decode(s, StandardCharsets.UTF_8.name())

  def createRequest(reqData: ReqData): OBPReq = {
    val charset = if (reqData.body_encoding == "") Charset.defaultCharset() else Charset.forName(reqData.body_encoding)
    val rb = OBPReq.url(reqData.url)
      .setMethod(reqData.method)
      .setBodyEncoding(charset)
      .setBody(reqData.body) <:< reqData.headers
    if (reqData.query_params.nonEmpty) rb <<? reqData.query_params else rb
  }

  def extractParamsAndHeaders(req: OBPReq, body: String, encoding: String, extra_headers: Map[String, String] = Map.empty): ReqData = {
    ReqData(
      url = req.baseUrl,
      method = req.method,
      body = body,
      body_encoding = encoding,
      headers = req.reqHeaders ++ extra_headers,
      query_params = req.queryParams,
      form_params = Map.empty
    )
  }

  private def executeRequest(req: OBPReq): APIResponse = {
    val okReq = req.toOkHttpRequest
    val response = OBPReq.client.newCall(okReq).execute()
    val bodyStr = try {
      Option(response.body()).map(_.string()).getOrElse("")
    } finally {
      response.close()
    }
    val responseCode = response.code()
    val okHeaders    = response.headers()

    val corrList = okHeaders.values(ResponseHeader.`Correlation-Id`).asScala.toList
    if (corrList.isEmpty) {
      val status = responseCode
      val headersStr = okHeaders.toMultimap.asScala
        .flatMap { case (k, vs) => vs.asScala.map(v => s"$k: $v") }
        .mkString(", ")
      val bodySnippet = if (bodyStr == null) "" else {
        val maxLen = 1000
        if (bodyStr.length > maxLen) bodyStr.take(maxLen) + "..." else bodyStr
      }
      throw new Exception(
        s"""There is no ${ResponseHeader.`Correlation-Id`} in response header.
           |Couldn't parse response from ${req.url}
           |status=$status
           |headers=[$headersStr]
           |body-snippet=${bodySnippet}""".stripMargin
      )
    }

    val contentTypeList = okHeaders.values("Content-Type").asScala.toList.map(_.toLowerCase)
    val isYaml = contentTypeList.exists(_.contains("yaml"))

    if (isYaml) {
      APIResponse(responseCode, JString(bodyStr), Some(okHeaders))
    } else {
      val parsedBody: Option[JValue] = tryo { parse(bodyStr) }.toOption orElse
        tryo {
          parse(s"[$bodyStr]") match {
            case JArray(v :: _) => v
            case _ => throw new RuntimeException("empty array")
          }
        }.toOption
      parsedBody match {
        case Some(b) => APIResponse(responseCode, b, Some(okHeaders))
        case None => throw new Exception(s"couldn't parse response from ${req.url} : $bodyStr")
      }
    }
  }

  private def getAPIResponse(req: OBPReq): APIResponse = {
    try {
      executeRequest(req)
    } catch {
      case e: Exception if e.getMessage != null && e.getMessage.contains("invalid version format") =>
        Thread.sleep(100)
        executeRequest(req)
    }
  }

  private def getAPIResponseAsync(req: OBPReq): Future[APIResponse] = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    Future { getAPIResponse(req) }
  }

  def makePostRequest(req: OBPReq, json: String, headers: List[(String, String)] = Nil): APIResponse = {
    val extra_headers = Map("Content-Type" -> "application/json", "Accept" -> "application/json") ++ headers
    val reqData = extractParamsAndHeaders(req.POST, json, "UTF-8", extra_headers)
    getAPIResponse(createRequest(reqData))
  }

  def makePostRequestAsync(req: OBPReq, json: String = ""): Future[APIResponse] = {
    val extra_headers = Map("Content-Type" -> "application/json", "Accept" -> "application/json")
    val reqData = extractParamsAndHeaders(req.POST, json, "UTF-8", extra_headers)
    getAPIResponseAsync(createRequest(reqData))
  }

  def makePostRequestAdditionalHeader(req: OBPReq, json: String = "", params: List[(String, String)] = Nil): APIResponse = {
    val extra_headers = Map("Content-Type" -> "application/json", "Accept" -> "application/json") ++ params
    val reqData = extractParamsAndHeaders(req.POST, json, "UTF-8", extra_headers)
    getAPIResponse(createRequest(reqData))
  }

  def makePutRequest(req: OBPReq, json: String, headers: (String, String)*): APIResponse = {
    val extra_headers = Map("Content-Type" -> "application/json") ++ headers.toMap
    val reqData = extractParamsAndHeaders(req.PUT, json, "UTF-8", extra_headers)
    getAPIResponse(createRequest(reqData))
  }

  def makePatchRequest(req: OBPReq, json: String, headers: (String, String)*): APIResponse = {
    val extra_headers = Map("Content-Type" -> "application/json") ++ headers.toMap
    val reqData = extractParamsAndHeaders(req.PATCH, json, "UTF-8", extra_headers)
    getAPIResponse(createRequest(reqData))
  }

  def makePutRequestAsync(req: OBPReq, json: String = ""): Future[APIResponse] = {
    val extra_headers = Map("Content-Type" -> "application/json")
    val reqData = extractParamsAndHeaders(req.PUT, json, "UTF-8", extra_headers)
    getAPIResponseAsync(createRequest(reqData))
  }

  def makeGetRequest(req: OBPReq, params: List[(String, String)] = Nil): APIResponse = {
    val extra_headers = Map.empty[String, String] ++ params
    val reqData = extractParamsAndHeaders(req.GET, "", "UTF-8", extra_headers)
    getAPIResponse(createRequest(reqData))
  }

  def makeHeadRequest(req: OBPReq, params: List[(String, String)] = Nil): APIResponse = {
    val extra_headers = Map.empty[String, String] ++ params
    val reqData = extractParamsAndHeaders(req.HEAD, "", "UTF-8", extra_headers)
    getAPIResponse(createRequest(reqData))
  }

  def makeGetRequestAsync(req: OBPReq, params: List[(String, String)] = Nil): Future[APIResponse] = {
    val extra_headers = Map.empty[String, String] ++ params
    val reqData = extractParamsAndHeaders(req.GET, "", "UTF-8", extra_headers)
    getAPIResponseAsync(createRequest(reqData))
  }

  def makeDeleteRequest(req: OBPReq): APIResponse = {
    getAPIResponse(req.DELETE)
  }

  def makeDeleteRequestAsync(req: OBPReq): Future[APIResponse] = {
    getAPIResponseAsync(req.DELETE)
  }

}
