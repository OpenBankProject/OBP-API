/**
  * Open Bank Project - API
  * Copyright (C) 2011-2018, TESOBE Ltd
  **
  *This program is free software: you can redistribute it and/or modify
  *it under the terms of the GNU Affero General Public License as published by
  *the Free Software Foundation, either version 3 of the License, or
  *(at your option) any later version.
  **
  *This program is distributed in the hope that it will be useful,
  *but WITHOUT ANY WARRANTY; without even the implied warranty of
  *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  *GNU Affero General Public License for more details.
  **
  *You should have received a copy of the GNU Affero General Public License
*along with this program.  If not, see <http://www.gnu.org/licenses/>.
  **
 *Email: contact@tesobe.com
*TESOBE Ltd
*Osloerstrasse 16/17
*Berlin 13359, Germany
  **
 *This product includes software developed at
  *TESOBE (http://www.tesobe.com/)
  * by
  *Simon Redfern : simon AT tesobe DOT com
  *Stefan Bethge : stefan AT tesobe DOT com
  *Everett Sochowski : everett AT tesobe DOT com
  *Ayoub Benali: ayoub AT tesobe DOT com
  *
 */
package code.setup

import java.nio.charset.Charset
import java.util.TimeZone
import code.api.oauth1a.OauthParams._
import code.api.util.APIUtil.OAuth
import code.consumer.Consumers
import code.token.Tokens
import dispatch.Defaults._
import dispatch._
import net.liftweb.common.Full
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json._
import net.liftweb.util.Helpers._

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeMap
import scala.concurrent.Await
import scala.concurrent.duration.Duration

case class APIResponse(code: Int, body: JValue)

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

  def encode_% (s: String) = java.net.URLEncoder.encode(s, org.apache.http.protocol.HTTP.UTF_8)

  def decode_% (s: String) = java.net.URLDecoder.decode(s, org.apache.http.protocol.HTTP.UTF_8)

  //normalize to OAuth percent encoding
  def %% (str: String): String = {
      val remaps = ("+", "%20") :: ("%7E", "~") :: ("*", "%2A") :: Nil
      (encode_%(str) /: remaps) { case (str, (a, b)) => str.replace(a,b) }
  }
  def %% (s: Seq[String]): String = s map %% mkString "&"
  def %% (t: (String, Any)): (String, String) = (%%(t._1), %%(t._2.toString))

  def getOAuthParameters(headers: Map[String,String]) : Map[String,String]= {
    //Convert the string containing the list of OAuth parameters to a Map
    def toMap(parametersList : String) = {
      //transform the string "oauth_prameter="value""
      //to a tuple (oauth_parameter,Decoded(value))
      def dynamicListExtract(input: String)  = {
        val oauthPossibleParameters =
          List(
            "oauth_consumer_key",
            NonceName,
            SignatureMethodName,
            TimestampName,
            VersionName,
            SignatureName,
            CallbackName,
            TokenName,
            VerifierName
          )
        if (input contains "=") {
          val split = input.split("=",2)
          val parameterValue = split(1).replace("\"","")
          //add only OAuth parameters and not empty
          if(oauthPossibleParameters.contains(split(0)) && ! parameterValue.isEmpty)
            Some(split(0),parameterValue)  // return key , value
          else
            None
        }
        else
          None
      }
      //we delete the "Oauth" prefix and all the white spaces that may exist in the string
      val cleanedParameterList = parametersList.stripPrefix("OAuth").replaceAll("\\s","")
      Map(cleanedParameterList.split(",").flatMap(dynamicListExtract _): _*)
    }
    toMap(headers("Authorization"))
  }

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

  def getConsumerSecret(consumerKey : String ) : String = {
    Consumers.consumers.vend.getConsumerByConsumerKey(consumerKey) match {
      case Full(c) => c.secret.get
      case _ => ""
    }
  }

  def getTokenSecret(token : String ) : String = {
    Tokens.tokens.vend.getTokenByKey(token) match {
      case Full(t) => t.secret.get
      case _ => ""
    }
  }

  // generate the requestData from input values, such as request, body, encoding and headers.
  def extractParamsAndHeaders(req: Req, body: String, encoding: String, extra_headers:Map[String,String] = Map.empty): ReqData= {
    val r = req.toRequest
    val query_params:Map[String,String] = r.getQueryParams.asScala.map(qp => qp.getName -> qp.getValue).toMap[String,String]
    val form_params: Map[String,String] = r.getFormParams.asScala.map( fp => fp.getName -> fp.getValue).toMap[String,String]
    var headers:Map[String,String] = r.getHeaders.entries().asScala.map (h => h.getKey -> h.getValue).toMap[String,String]
    val url:String = r.getUrl
    val urlWithoutQueryParams:String = if (r.getUrl.contains("?")) r.getUrl.splitAt("?").head._1 else r.getUrl
    val method:String = r.getMethod

    if (headers.isDefinedAt("Authorization") && headers("Authorization").contains("OAuth")) {
      val oauth_params = getOAuthParameters(headers)
      val consumer_secret = getConsumerSecret(oauth_params("oauth_consumer_key"))
      val token_secret = getTokenSecret(oauth_params(TokenName))
      val new_oauth_params = OAuth.sign(
        method,
        urlWithoutQueryParams,
        query_params ++ form_params, // ++ extra_headers,
        OAuth.Consumer(oauth_params("oauth_consumer_key"), consumer_secret),
        Option(OAuth.Token(oauth_params.getOrElse(TokenName, ""), token_secret)),
        oauth_params.get("verifier"),
        oauth_params.get("callback"))
      val new_oauth_headers = (new TreeMap[String, String] ++ (new_oauth_params map %%)
      ) map { case (k, v) => k + """="""" + v + """"""" } mkString ","
      headers = Map("Authorization" -> ("OAuth " + new_oauth_headers))
    }

    ReqData(url, method, body, encoding, headers ++ extra_headers, query_params, form_params)
  }


  private def ApiResponseCommonPart(req: Req) = {
    for (response <- Http(req > as.Response(p => p)))
      yield {
        val body = if (response.getResponseBody().isEmpty) "{}" else response.getResponseBody()

        // Check that every response has a correlationId at Response Header
        val list = response.getHeaders("Correlation-Id").asScala.toList
        list match {
          case Nil => throw new Exception(s"There is no Correlation-Id in response header. Couldn't parse response from ${req.url} : $body")
          case _ =>
        }

        val parsedBody = tryo {
          parse(body)
        }
        parsedBody match {
          case Full(b) => APIResponse(response.getStatusCode, b)
          case _ => throw new Exception(s"couldn't parse response from ${req.url} : $body")
        }
      }
  }

  private def getAPIResponse(req : Req) : APIResponse = {
    //println("<<<<<<< " + req.toRequest.toString)
    Await.result(ApiResponseCommonPart(req), Duration.Inf)
  }

  private def getAPIResponseAsync(req: Req): Future[APIResponse] = {
    ApiResponseCommonPart(req)
  }

  /**
  *this method does a POST request given a URL, a JSON
    */
  def makePostRequest(req: Req, json: String = ""): APIResponse = {
    val extra_headers = Map(  "Content-Type" -> "application/json",
                              "Accept" -> "application/json")
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

  def makePutRequest(req: Req, json: String = "") : APIResponse = {
    val extra_headers = Map("Content-Type" -> "application/json")
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
