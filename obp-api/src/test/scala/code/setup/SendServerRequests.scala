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

import java.nio.charset.StandardCharsets
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

  implicit def requestToRequestSigner(r: OBPReq): RequestSigner = new RequestSigner(r)

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

  private def executeRequest(req: OBPReq): APIResponse = {
    val (responseCode, bodyStr, okHeaders) = req.executeRaw()

    if (okHeaders.values(ResponseHeader.`Correlation-Id`).asScala.isEmpty) {
      throw missingCorrelationIdException(req, responseCode, bodyStr, okHeaders)
    }

    val contentTypeList = okHeaders.values(OBPReq.ContentTypeHeader).asScala.toList.map(_.toLowerCase)
    if (contentTypeList.exists(_.contains("yaml"))) {
      APIResponse(responseCode, JString(bodyStr), Some(okHeaders))
    } else {
      parseJsonBody(bodyStr) match {
        case Some(b) => APIResponse(responseCode, b, Some(okHeaders))
        case None => throw new Exception(s"couldn't parse response from ${req.url} : $bodyStr")
      }
    }
  }

  private def missingCorrelationIdException(req: OBPReq, responseCode: Int, bodyStr: String, okHeaders: OkHeaders): Exception = {
    val headersStr = okHeaders.toMultimap.asScala
      .flatMap { case (k, vs) => vs.asScala.map(v => s"$k: $v") }
      .mkString(", ")
    val maxLen = 1000
    val bodySnippet =
      if (bodyStr == null) ""
      else if (bodyStr.length > maxLen) bodyStr.take(maxLen) + "..." else bodyStr
    new Exception(
      s"""There is no ${ResponseHeader.`Correlation-Id`} in response header.
         |Couldn't parse response from ${req.url}
         |status=$responseCode
         |headers=[$headersStr]
         |body-snippet=${bodySnippet}""".stripMargin
    )
  }

  private def parseJsonBody(bodyStr: String): Option[JValue] =
    if (bodyStr.isEmpty) Some(JNothing)
    else tryo { parse(bodyStr) }.toOption orElse
      tryo {
        parse(s"[$bodyStr]") match {
          case JArray(v :: _) => v
          case _ => throw new RuntimeException("empty array")
        }
      }.toOption

  private def getAPIResponse(req: OBPReq): APIResponse =
    try {
      executeRequest(req)
    } catch {
      case _: java.io.IOException =>
        // Concurrent shards/tests share OBPReq.client's connection pool; one test's error
        // response can corrupt a pooled connection, surfacing as a broken status line on
        // the next request that reuses it. OkHttp does not retry this itself
        // (RetryAndFollowUpInterceptor.recover() refuses to recover a ProtocolException).
        // Retry once with a fresh connection after a brief delay — the same recovery the
        // old dispatch-based client had for the same "invalid version format" symptom.
        Thread.sleep(100)
        executeRequest(req)
    }

  private def getAPIResponseAsync(req: OBPReq): Future[APIResponse] =
    Future { scala.concurrent.blocking { getAPIResponse(req) } }(ExecutionContext.global)

  private def prepareRequest(req: OBPReq, body: String, extraHeaders: Map[String, String]): OBPReq =
    req.setBody(body).setBodyEncoding(StandardCharsets.UTF_8) <:< extraHeaders

  private def sendSync(req: OBPReq, body: String = "", extraHeaders: Map[String, String] = Map.empty): APIResponse =
    getAPIResponse(prepareRequest(req, body, extraHeaders))

  private def sendAsync(req: OBPReq, body: String = "", extraHeaders: Map[String, String] = Map.empty): Future[APIResponse] =
    getAPIResponseAsync(prepareRequest(req, body, extraHeaders))

  private val ContentType    = OBPReq.ContentTypeHeader
  private val ApplicationJson = "application/json"

  private val jsonHeaders: Map[String, String] = Map(ContentType -> ApplicationJson, "Accept" -> ApplicationJson)
  private val putHeaders:  Map[String, String] = Map(ContentType -> ApplicationJson)

  def makePostRequest(req: OBPReq, json: String, headers: List[(String, String)] = Nil): APIResponse =
    sendSync(req.POST, json, jsonHeaders ++ headers)

  def makePostRequestAsync(req: OBPReq, json: String = ""): Future[APIResponse] =
    sendAsync(req.POST, json, jsonHeaders)

  def makePostRequestAdditionalHeader(req: OBPReq, json: String = "", params: List[(String, String)] = Nil): APIResponse =
    sendSync(req.POST, json, jsonHeaders ++ params)

  def makePutRequest(req: OBPReq, json: String, headers: (String, String)*): APIResponse =
    sendSync(req.PUT, json, putHeaders ++ headers.toMap)

  def makePatchRequest(req: OBPReq, json: String, headers: (String, String)*): APIResponse =
    sendSync(req.PATCH, json, putHeaders ++ headers.toMap)

  def makePutRequestAsync(req: OBPReq, json: String = ""): Future[APIResponse] =
    sendAsync(req.PUT, json, putHeaders)

  def makeGetRequest(req: OBPReq, params: List[(String, String)] = Nil): APIResponse =
    sendSync(req.GET, extraHeaders = Map.empty ++ params)

  def makeHeadRequest(req: OBPReq, params: List[(String, String)] = Nil): APIResponse =
    sendSync(req.HEAD, extraHeaders = Map.empty ++ params)

  def makeGetRequestAsync(req: OBPReq, params: List[(String, String)] = Nil): Future[APIResponse] =
    sendAsync(req.GET, extraHeaders = Map.empty ++ params)

  def makeDeleteRequest(req: OBPReq): APIResponse = getAPIResponse(req.DELETE)

  def makeDeleteRequestAsync(req: OBPReq): Future[APIResponse] = getAPIResponseAsync(req.DELETE)

}
