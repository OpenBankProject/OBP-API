package code.util

import code.model.dataAccess.APIMetric
import code.api.v1_2.ErrorMessage
import net.liftweb.http.JsonResponse
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.http.js.JsExp
import net.liftweb.common.Full
import net.liftweb.util.Helpers._
import net.liftweb.http.S
import net.liftweb.http.js.JE.JsRaw

object APIUtil {

  implicit val formats = net.liftweb.json.DefaultFormats
  implicit def errorToJson(error: ErrorMessage): JValue = Extraction.decompose(error)

  def httpMethod : String =
    S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }

  def isThereAnOAuthHeader : Boolean = {
    S.request match {
      case Full(a) =>  a.header("Authorization") match {
        case Full(parameters) => parameters.contains("OAuth")
        case _ => false
      }
      case _ => false
    }
  }

  def logAPICall =
    APIMetric.createRecord.
      url(S.uriAndQueryString.getOrElse("")).
      date((now: TimeSpan)).
      save

  def gitCommit : String = {
    val commit = tryo{
      val properties = new java.util.Properties()
      properties.load(getClass().getClassLoader().getResourceAsStream("git.properties"))
      properties.getProperty("git.commit.id", "")
    }
    commit getOrElse ""
  }

  def noContentJsonResponse : JsonResponse =
    JsonResponse(JsRaw(""), Nil, Nil, 204)

  def successJsonResponse(json: JsExp, httpCode : Int = 200) : JsonResponse =
    JsonResponse(json, Nil, Nil, httpCode)

  def errorJsonResponse(message : String = "error", httpCode : Int = 400) : JsonResponse =
    JsonResponse(Extraction.decompose(ErrorMessage(message)), Nil, Nil, httpCode)

  def oauthHeaderRequiredJsonResponce : JsonResponse =
    JsonResponse(Extraction.decompose(ErrorMessage("Authentication via OAuth is required")), Nil, Nil, 400)

}
