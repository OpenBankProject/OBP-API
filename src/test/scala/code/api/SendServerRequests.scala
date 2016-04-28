/**
Open Bank Project - API
Copyright (C) 2011-2015, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

  */
package code.api.test

import net.liftweb.common.Full
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json._
import net.liftweb.util.Helpers._
import dispatch._, Defaults._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

case class APIResponse(code: Int, body: JValue)

trait SendServerRequests {

  private def getAPIResponse(req : Req) : APIResponse = {
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
  this method does a POST request given a URL, a JSON
    */
  def makePostRequest(req: Req, json: String = ""): APIResponse = {
    req.addHeader("Content-Type", "application/json")
    req.addHeader("Accept", "application/json")
    req.setBody(json)
    req.setBodyEncoding("UTF-8")
    val jsonReq = (req).POST
    getAPIResponse(jsonReq)
  }

// Accepts an additional option header Map
  def makePostRequestAdditionalHeader(req: Req, json: String = "", params: List[(String, String)] = Nil): APIResponse = {
    req.addHeader("Content-Type", "application/json")
    req.addHeader("Accept", "application/json")
    req.setBody(json)
    req.setBodyEncoding("UTF-8")
    val jsonReq = req.POST
    params.foreach{
      headerAndValue => {
        jsonReq.addHeader(headerAndValue._1, headerAndValue._2)
      }
    }
    getAPIResponse(jsonReq)
  }



  def makePutRequest(req: Req, json: String = "") : APIResponse = {
    req.addHeader("Content-Type", "application/json")
    req.setBody(json)
    req.setBodyEncoding("UTF-8")
    val jsonReq = (req).PUT
    getAPIResponse(jsonReq)
  }

  /**
   * this method does a GET request given a URL
   */
  def makeGetRequest(req: Req, params: List[(String, String)] = Nil) : APIResponse = {
    val jsonReq = req.GET
    params.foreach{
      headerAndValue => {
        jsonReq.addHeader(headerAndValue._1, headerAndValue._2)
      }
    }
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
