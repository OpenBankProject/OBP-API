/**
  * Open Bank Project - API
  * Copyright (C) 2011-2016, TESOBE / Music Pictures Ltd
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
*TESOBE Ltd.
*Osloer Strasse 16/17
*Berlin 13359, Germany
  **
 *This product includes software developed at
  *TESOBE (http://www.tesobe.com/)
  *
 */
package code.api

import java.io.IOException
import java.security.cert.Certificate
import java.util.Date
import javax.net.ssl.{HttpsURLConnection, SSLPeerUnverifiedException}
import javax.security.cert.Certificate

import authentikat.jwt.{JsonWebToken, JwtClaimsSet, JwtHeader}
import code.api.util.APIUtil._
import code.model.dataAccess.{APIUser, OBPUser}
import code.model.{Consumer, Token, TokenType, User}
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.{Extraction, JObject, JValue}
import net.liftweb.mapper.By
import net.liftweb.util.{Helpers, Props}
import net.liftweb.util.Helpers._

import scala.compat.Platform
import code.api.util.{APIUtil, ErrorMessages}
import kafka.utils.Json
import net.liftweb.json

/**
  * This object provides the API calls necessary to authenticate
  * users using OpenIdConnect (http://openid.net).
  */

case class OpenIdConnectConfig(secret: String, clientId: String, callbackURL: String, domain: String)
object OpenIdConnectConfig {
  def get() = {
    OpenIdConnectConfig(
      Props.get("openidconnect.clientSecret").openOrThrowException("no openidconnect.clientSecret set"),
      Props.get("openidconnect.clientId").openOrThrowException("no openidconnect.clientId set"),
      Props.get("openidconnect.callbackURL").openOrThrowException("no openidconnect.callbackURL set"),
      Props.get("openidconnect.domain").openOrThrowException("no openidconnect.domain set")
    )
  }
}

object OpenIdConnect extends RestHelper with Loggable {

  // Our version of serve
  def openIdConnectServe(handler : PartialFunction[Req, JsonResponse]) : Unit = {
    val obpHandler : PartialFunction[Req, () => Box[LiftResponse]] = {
      new PartialFunction[Req, () => Box[LiftResponse]] {
        def apply(r : Req) = {
          handler(r)
        }
        def isDefinedAt(r : Req) = handler.isDefinedAt(r)
      }
    }
    super.serve(obpHandler)
  }

  openIdConnectServe {
    //Handling get request for a token
    case Req("my" :: "logins" :: "openidconnect" :: Nil, _, PostRequest | GetRequest) => {
      var httpCode = 500
      var message = ""
      for {
        code <- S.params("code")
        state <- S.param("state")
      } yield {
        // Get the token
        getToken(code) match {
          case Full((idToken, accessToken)) =>
            getUser(accessToken) match {

              case Full(json_user:JObject) =>
                for {
                  emailVerified <- tryo{(json_user \ "email_verified").extractOrElse[Boolean](false)}
                  userEmail <- tryo{(json_user \ "email").extractOrElse[String]("")}
                  obp_user <- OBPUser.find(By(OBPUser.email, userEmail))
                  api_user <- obp_user.user.foreign
                  if emailVerified && api_user.apiId.value > 0
                } yield {
                  saveAuthorizationToken(accessToken, state, api_user.apiId.value)
                  //println("[HOMEPAGE]---------------------> " + OBPUser.homePage) //Token.findAll())
                  httpCode = 200
                  message = accessToken
                  //OBPUser.logUserIn(obp_user) //, () => {
                    //S.notice(S.?("logged.in"))
                    //preLoginState()
                    //OBPUser.loginRedirect
                  //})
                }

              case _ => println("[TOKENS]---------------------> ERROR")
            }
        }
      }

      if (httpCode == 200)
        successJsonResponse(Extraction.decompose(JSONFactory.createTokenJSON(message)))
      else
        errorJsonResponse(message, httpCode)
    }
  }

  def getToken(code: String): Box[(String, String)] = {
    val config = OpenIdConnectConfig.get()
    val data =    "client_id=" + config.clientId + "&" +
                  "client_secret=" + config.secret + "&" +
                  "redirect_uri=" + config.callbackURL + "&" +
                  "code=" + code + "&" +
                  "grant_type=authorization_code"
    val response = fromUrl(String.format("https://%s/oauth/token", config.domain), data, "POST")
    val tokenResponse = json.parse(response)
    for {
      idToken <- tryo{(tokenResponse \ "id_token").extractOrElse[String]("")}
      accessToken <- tryo{(tokenResponse \ "access_token").extractOrElse[String]("")}
    } yield {
      (idToken, accessToken)
    }
  }

  def getUser(accessToken: String): Box[JValue] = {
    val config = OpenIdConnectConfig.get()
    val userResponse = json.parse(fromUrl(String.format("https://%s/userinfo", config.domain), "?access_token="+accessToken, "GET"))

    userResponse match {
      case response: JValue => Full(response)
      case _ => Empty
    }
  }

  private def saveAuthorizationToken(tokenKey: String, tokenSecret: String, userId: Long) =
  {
    import code.model.{Token, TokenType}
    val token = Token.create
    token.tokenType(TokenType.Access)
    //Consumer.find(By(Consumer.key, directLoginParameters.getOrElse("consumer_key", ""))) match {
    //  case Full(consumer) => token.consumerId(consumer.id)
    //  case _ => None
    //}
    token.consumerId(0)
    token.userForeignKey(userId)
    token.key(tokenKey)
    token.secret(tokenSecret)
    val currentTime = Platform.currentTime
    val tokenDuration : Long = Helpers.weeks(4)
    token.duration(tokenDuration)
    token.expirationDate(new Date(currentTime+tokenDuration))
    token.insertDate(new Date(currentTime))
    val tokenSaved = token.save()
    tokenSaved
  }

  def fromUrl( url: String,
               data: String = "",
               method: String,
               connectTimeout: Int = 2000,
               readTimeout: Int = 10000
             ) = {
    var content:String = ""
    try {
      import java.net.URL
      val connection:HttpsURLConnection = new URL(url + {
        if (method == "GET") data
        else ""
      }).openConnection.asInstanceOf[HttpsURLConnection]
      connection.setConnectTimeout(connectTimeout)
      connection.setReadTimeout(readTimeout)
      connection.setRequestMethod(method)
      connection.setRequestProperty("Accept", "application/json")
      if ( data != "" && method == "POST") {
        connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded")
        connection.setRequestProperty("Charset", "utf-8")
        val dataBytes = data.getBytes("UTF-8")
        connection.setRequestProperty("Content-Length", dataBytes.length.toString)
        connection.setDoOutput( true )
        connection.getOutputStream.write(dataBytes)
      }
      val inputStream = connection.getInputStream
      content = io.Source.fromInputStream(inputStream).mkString
      if (inputStream != null) inputStream.close()
    } catch {
      case e:Throwable => println(e)
    }
    content
  }


}
