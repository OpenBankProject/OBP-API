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

import java.nio.charset.{Charset, StandardCharsets}
import java.util.TimeZone

import code.api.ResponseHeader
import dispatch.Defaults._
import dispatch._
import net.liftweb.common.Full
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json._
import net.liftweb.util.Helpers._
import java.net.URLDecoder

import io.netty.handler.codec.http.HttpHeaders

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

case class APIResponse(code: Int, body: JValue, headers: Option[HttpHeaders])

/**
  * This trait simulate the Rest process, HTTP parameters --> Reset parameters
  * simulate the four methods GET, POST, DELETE and POST 
  * Prepare the Headers, query parameters and form parameters, send these to OBP-API 
  * and get the response code and response body back.
  * 
  */
trait SendServerRequests {

  TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
  
  case class ReqData (
                      url: String,
                      method: String,
                      body: String,
                      body_encoding: String,
                      headers: Map[String, String],
                      query_params: Map[String,String],
                      form_params: Map[String,String]
                     )

  def encode_% (s: String) = java.net.URLEncoder.encode(s, StandardCharsets.UTF_8.name())

  def decode_% (s: String) = java.net.URLDecoder.decode(s, StandardCharsets.UTF_8.name())

  def createRequest( reqData: ReqData ): Req = {
    val charset = if(reqData.body_encoding == "") Charset.defaultCharset() else Charset.forName(reqData.body_encoding)
    val rb = url(reqData.url)
      .setMethod(reqData.method)
      .setBodyEncoding(charset)
      .setBody(reqData.body) <:< reqData.headers
    if (reqData.query_params.nonEmpty)
      rb <<? reqData.query_params
    rb
  }

  // generate the requestData from input values, such as request, body, encoding and headers.
  def extractParamsAndHeaders(req: Req, body: String, encoding: String, extra_headers:Map[String,String] = Map.empty): ReqData= {
    val r = req.toRequest
    val query_params:Map[String,String] = r.getQueryParams.asScala.map(qp => qp.getName -> URLDecoder.decode(qp.getValue,"UTF-8")).toMap[String,String]
    val form_params: Map[String,String] = r.getFormParams.asScala.map( fp => fp.getName -> fp.getValue).toMap[String,String]
    val headers:Map[String,String] = r.getHeaders.entries().asScala.map (h => h.getKey -> h.getValue).toMap[String,String]
    val url:String = r.getUrl
    val method:String = r.getMethod

    ReqData(url, method, body, encoding, headers ++ extra_headers, query_params, form_params)
  }


  private def ApiResponseCommonPart(req: Req) = {
    for (response <- Http.default(req > as.Response(p => p)))
      yield {
        //{} -->parse(body) => JObject(List()) , this is not "NO Content", change "" --> JNothing
        val body = if (response.getResponseBody().isEmpty) "" else response.getResponseBody()

        // Check that every response has a correlationId at Response Header
        val list = response.getHeaders(ResponseHeader.`Correlation-Id`).asScala.toList
        list match {
          case Nil =>
            // Improve diagnostic information: include HTTP status, all response headers and a snippet of the body.
            val status = response.getStatusCode
            val headersStr = try {
              // response.getHeaders().entries() returns a Java collection of header entries
              response.getHeaders().entries().asScala.map(h => s"${h.getKey}: ${h.getValue}").mkString(", ")
            } catch {
              case _: Throwable => "unable to read headers"
            }
            val bodySnippet = if (body == null) {
              ""
            } else {
              val maxLen = 1000
              if (body.length > maxLen) body.take(maxLen) + "..." else body
            }
            throw new Exception(
              s"""There is no ${ResponseHeader.`Correlation-Id`} in response header.
                 |Couldn't parse response from ${req.url}
                 |status=$status
                 |headers=[$headersStr]
                 |body-snippet=${bodySnippet}""".stripMargin
            )
          case _ =>
        }

        // Handle YAML responses: don't try to parse as JSON. Wrap YAML as a JString so tests
        // that expect a JValue can still receive the body.
        val contentTypeList = response.getHeaders("Content-Type").asScala.toList.map(_.toLowerCase)
        val isYaml = contentTypeList.exists(_.contains("yaml"))
        if (isYaml) {
          APIResponse(response.getStatusCode, JString(body), Some(response.getHeaders()))
        } else {
          val parsedBody = tryo {
            parse(body)
          }
          parsedBody match {
            case Full(b) => APIResponse(response.getStatusCode, b, Some(response.getHeaders()))
            case _ => throw new Exception(s"couldn't parse response from ${req.url} : $body")
          }
        }
      }
  }

  private def getAPIResponse(req : Req) : APIResponse = {
    try {
      Await.result(ApiResponseCommonPart(req), Duration.Inf)
    } catch {
      case e: Exception if e.getMessage != null && e.getMessage.contains("invalid version format") =>
        // Connection pool pollution detected - retry once with a fresh connection
        // This happens when concurrent tests share the same HTTP client and one test's
        // error response corrupts the connection state
        Thread.sleep(100) // Brief delay to let connection close
        Await.result(ApiResponseCommonPart(req), Duration.Inf)
    }
  }

  private def getAPIResponseAsync(req: Req): Future[APIResponse] = {
    ApiResponseCommonPart(req)
  }

  /**
  *this method does a POST request given a URL, a JSON
    */
  def makePostRequest(req: Req, json: String, headers: List[(String, String)] = Nil): APIResponse = {
    val extra_headers = Map(  "Content-Type" -> "application/json",
                              "Accept" -> "application/json") ++ headers
    val reqData = extractParamsAndHeaders(req.POST, json, "UTF-8", extra_headers)
    val jsonReq = createRequest(reqData)
    getAPIResponse(jsonReq)
  }
  /**
  *this method does a POST request given a URL, a JSON
    */
  def makePostRequestAsync(req: Req, json: String = ""): Future[APIResponse] = {
    val extra_headers = Map(  "Content-Type" -> "application/json",
                              "Accept" -> "application/json")
    val reqData = extractParamsAndHeaders(req.POST, json, "UTF-8", extra_headers)
    val jsonReq = createRequest(reqData)
    getAPIResponseAsync(jsonReq)
  }

// Accepts an additional option header Map
  def makePostRequestAdditionalHeader(req: Req, json: String = "", params: List[(String, String)] = Nil): APIResponse = {
    val extra_headers = Map(  "Content-Type" -> "application/json",
                              "Accept" -> "application/json") ++ params
    val reqData = extractParamsAndHeaders(req.POST, json, "UTF-8", extra_headers)
    val jsonReq = createRequest(reqData)
    getAPIResponse(jsonReq)
  }

  def makePutRequest(req: Req, json: String, headers: (String, String) *) : APIResponse = {
    val extra_headers = Map("Content-Type" -> "application/json") ++ headers.toMap
    val reqData = extractParamsAndHeaders(req.PUT, json, "UTF-8", extra_headers)
    val jsonReq = createRequest(reqData)
    getAPIResponse(jsonReq)
  }

  def makePutRequestAsync(req: Req, json: String = ""): Future[APIResponse] = {
    val extra_headers = Map("Content-Type" -> "application/json")
    val reqData = extractParamsAndHeaders(req.PUT, json, "UTF-8", extra_headers)
    val jsonReq = createRequest(reqData)
    getAPIResponseAsync(jsonReq)
  }

  /**
   * this method does a GET request given a URL
   */
  def makeGetRequest(req: Req, params: List[(String, String)] = Nil) : APIResponse = {
    val extra_headers = Map.empty ++ params
    val reqData = extractParamsAndHeaders(req.GET, "", "UTF-8", extra_headers)
    val jsonReq = createRequest(reqData)
    getAPIResponse(jsonReq)
  }
  
  /**
   * this method does a HEAD request given a URL
   */
  def makeHeadRequest(req: Req, params: List[(String, String)] = Nil) : APIResponse = {
    val extra_headers = Map.empty ++ params
    val reqData = extractParamsAndHeaders(req.HEAD, "", "UTF-8", extra_headers)
    val jsonReq = createRequest(reqData)
    getAPIResponse(jsonReq)
  }
  /**
   * this method does a GET request given a URL
   */
  def makeGetRequestAsync(req: Req, params: List[(String, String)] = Nil): Future[APIResponse] = {
    val extra_headers = Map.empty ++ params
    val reqData = extractParamsAndHeaders(req.GET, "", "UTF-8", extra_headers)
    val jsonReq = createRequest(reqData)
    getAPIResponseAsync(jsonReq)
  }

  /**
   * this method does a delete request given a URL
   */
  def makeDeleteRequest(req: Req) : APIResponse = {
    //Note: method will be set too late for oauth signing, so set it before using <@
    val jsonReq = req.DELETE
    getAPIResponse(jsonReq)
  }
  /**
   * this method does a delete request given a URL
   */
  def makeDeleteRequestAsync(req: Req): Future[APIResponse] = {
    //Note: method will be set too late for oauth signing, so set it before using <@
    val jsonReq = req.DELETE
    getAPIResponseAsync(jsonReq)
  }

}
