/**
  * Open Bank Project - API
  * Copyright (C) 2011-2016, TESOBE Ltd
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
package code.api


import code.token.Tokens
import code.api.util.APIUtil.OAuth
import code.consumer.Consumers
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

trait SendServerRequests {

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
            "oauth_nonce",
            "oauth_signature_method",
            "oauth_timestamp",
            "oauth_version",
            "oauth_signature",
            "oauth_callback",
            "oauth_token",
            "oauth_verifier"
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
    val rb = url(reqData.url)
      .setMethod(reqData.method)
      .setBodyEncoding(reqData.body_encoding)
      .setBody(reqData.body) <:< reqData.headers
    if (reqData.query_params.nonEmpty)
      rb <<? reqData.query_params
    rb
  }

  def getConsumerSecret(consumerKey : String ) : String = {
    Consumers.consumers.vend.getConsumerByConsumerKey(consumerKey) match {
      case Full(c) => c.secret
      case _ => ""
    }
  }

  def getTokenSecret(token : String ) : String = {
    Tokens.tokens.vend.getTokenByKey(token) match {
      case Full(t) => t.secret
      case _ => ""
    }
  }

  def extractParamsAndHeaders(req: Req, body: String, encoding: String, extra_headers:Map[String,String] = Map.empty): ReqData= {
    val r = req.toRequest
    val query_params:Map[String,String] = r.getQueryParams.asScala.map(qp => qp.getName -> qp.getValue).toMap[String,String]
    val form_params: Map[String,String] = r.getFormParams.asScala.map( fp => fp.getName -> fp.getValue).toMap[String,String]
    var headers:Map[String,String] = r.getHeaders.entrySet.asScala.map (h => h.getKey -> h.getValue.get(0)).toMap[String,String]
    val url:String = r.getUrl
    val method:String = r.getMethod

    if (headers.isDefinedAt("Authorization") && headers("Authorization").contains("OAuth")) {
      val oauth_params = getOAuthParameters(headers)
      val consumer_secret = getConsumerSecret(oauth_params("oauth_consumer_key"))
      val token_secret = getTokenSecret(oauth_params("oauth_token"))
      val new_oauth_params = OAuth.sign(
        method,
        url,
        query_params ++ form_params, // ++ extra_headers,
        OAuth.Consumer(oauth_params("oauth_consumer_key"), consumer_secret),
        Option(OAuth.Token(oauth_params.getOrElse("oauth_token", ""), token_secret)),
        oauth_params.get("verifier"),
        oauth_params.get("callback"))
      val new_oauth_headers = (new TreeMap[String, String] ++ (new_oauth_params map %%)
      ) map { case (k, v) => k + """="""" + v + """"""" } mkString ","
      headers = Map("Authorization" -> ("OAuth " + new_oauth_headers))
    }

    ReqData(url, method, body, encoding, headers ++ extra_headers, query_params, form_params)
  }


  private def getAPIResponse(req : Req) : APIResponse = {
    //println("<<<<<<< " + req.toRequest.toString)
    Await.result(
      for(response <- Http(req > as.Response(p => p)))
      yield
      {
        val body = if(response.getResponseBody().isEmpty) "{}" else response.getResponseBody()
        val parsedBody = tryo {parse(body)}
        parsedBody match {
          case Full(b) => APIResponse(response.getStatusCode, b)
          case _ => throw new Exception(s"couldn't parse response from ${req.url} : $body")
        }
      }
      , Duration.Inf)
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

  /**
   * this method does a GET request given a URL
   */
  def makeGetRequest(req: Req, params: List[(String, String)] = Nil) : APIResponse = {
    val extra_headers = Map.empty ++ params
    val reqData = extractParamsAndHeaders(req.GET, "", "", extra_headers)
    val jsonReq = createRequest(reqData)
    getAPIResponse(jsonReq)
  }

  /**
   * this method does a delete request given a URL
   */
  def makeDeleteRequest(req: Req) : APIResponse = {
    //Note: method will be set too late for oauth signing, so set it before using <@
    val jsonReq = req.DELETE
    getAPIResponse(jsonReq)
  }

}
