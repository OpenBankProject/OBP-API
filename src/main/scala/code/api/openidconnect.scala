/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

 */
package code.api

import java.net.HttpURLConnection
import java.util.Date
import javax.net.ssl.HttpsURLConnection

import code.api.util.APIUtil._
import code.api.util.{APIUtil, ApiVersion}
import code.model.User
import code.model.dataAccess.{AuthUser, ResourceUser}
import code.token.Tokens
import code.util.Helper.MdcLoggable
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.json
import net.liftweb.json.{JObject, JValue}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers._

import scala.compat.Platform

/**
  * This object provides the API calls necessary to authenticate
  * users using OpenIdConnect (http://openid.net).
  */

case class OpenIdConnectConfig( clientSecret: String,
                                clientId: String,
                                callbackURL: String,
                                domain: String,
                                url_userinfo: String,
                                url_token: String,
                                url_login: String,
                                url_button: String
                              )

object OpenIdConnectConfig {
  def get() = {
    OpenIdConnectConfig(
      APIUtil.getPropsValue("openidconnect.clientSecret").openOrThrowException("no openidconnect.clientSecret set"),
      APIUtil.getPropsValue("openidconnect.clientId").openOrThrowException("no openidconnect.clientId set"),
      APIUtil.getPropsValue("openidconnect.callbackURL").openOrThrowException("no openidconnect.callbackURL set"),
      APIUtil.getPropsValue("openidconnect.domain").openOrThrowException("no openidconnect.domain set"),
      APIUtil.getPropsValue("openidconnect.url.userinfo").openOrThrowException("no openidconnect.url.userinfo set"),
      APIUtil.getPropsValue("openidconnect.url.token").openOrThrowException("no openidconnect.url.token set"),
      APIUtil.getPropsValue("openidconnect.url.login").openOrThrowException("no openidconnect.url.login set"),
      APIUtil.getPropsValue("openidconnect.url.buttonImage").openOrThrowException("no openidconnect.url.buttonImage set")
    )
  }
}

object OpenIdConnect extends OBPRestHelper with MdcLoggable {

  val version = ApiVersion.openIdConnect1 // "1.0" // TODO: Should this be the lowest version supported or when introduced?
  val versionStatus = "UNKNOWN"

  serve {
    case Req("my" :: "logins" :: "openidconnect" :: Nil, _, PostRequest | GetRequest) => {
      var httpCode = 500
      var message = "unknown"
      for {
        code <- S.params("code")
        //state <- S.param("state")
      } yield {
        // Get the token
        message=code
        getToken(code) match {
          case Full((idToken, accessToken, tokenType)) =>
            getUser(accessToken) match {

              case Full(json_user:JObject) =>
                for {
                  emailVerified <- tryo{(json_user \ "email_verified").extractOrElse[Boolean](false)}
                  userEmail <- tryo{(json_user \ "email").extractOrElse[String]("")}
                  auth_user: AuthUser <- AuthUser.find(By(AuthUser.email, userEmail))
                  resource_user: ResourceUser <- User.findResourceUserByResourceUserId(auth_user.user.get)
                  if emailVerified && resource_user.userPrimaryKey.value > 0
                } yield {
                  saveAuthorizationToken(accessToken, accessToken, resource_user.userPrimaryKey.value)
                  httpCode = 200
                  message= String.format("oauth_token=%s&oauth_token_secret=%s", accessToken, accessToken)
                  val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
                  AuthUser.logUserIn(auth_user, () => {
                    S.notice(S.?("logged.in"))
                    //This redirect to homePage, it is from scala code, no open redirect issue.
                    S.redirectTo(AuthUser.homePage)
                  })
                }
              case _ => message=String.format("Could not find user with token %s", accessToken)
            }
            case _ => message=String.format("Could not get token for code %s", code)
        }
      }

      errorJsonResponse(message, httpCode)
    }
  }

  def getToken(code: String): Box[(String, String, String)] = {
    val config = OpenIdConnectConfig.get()
    val data =    "client_id=" + config.clientId + "&" +
                  "client_secret=" + config.clientSecret + "&" +
                  "redirect_uri=" + config.callbackURL + "&" +
                  "code=" + code + "&" +
                  "grant_type=authorization_code"
    val response = fromUrl(String.format("%s", config.url_token), data, "POST")
    val tokenResponse = json.parse(response)
    for {
      idToken <- tryo{(tokenResponse \ "id_token").extractOrElse[String]("")}
      accessToken <- tryo{(tokenResponse \ "access_token").extractOrElse[String]("")}
      tokenType <- tryo{(tokenResponse \ "token_type").extractOrElse[String]("")}
    } yield {
      (idToken, accessToken, tokenType)
    }
  }

  def getUser(accessToken: String): Box[JValue] = {
    val config = OpenIdConnectConfig.get()
    val userResponse = json.parse(fromUrl(String.format("%s", config.url_userinfo), "?access_token="+accessToken, "GET"))

    userResponse match {
      case response: JValue => Full(response)
      case _ => Empty
    }
  }

  private def saveAuthorizationToken(tokenKey: String, tokenSecret: String, userId: Long) =
  {
    import code.model.TokenType
    // TODO Consumer is not needed with oauth2/openid or is it?
    //Consumers.consumers.vend.getConsumerByConsumerKey(directLoginParameters.getOrElse("consumer_key", "")) match {
    //  case Full(consumer) => token.consumerId(consumer.id)
    //  case _ => None
    //}
    //token.consumerId(0)
    val currentTime = Platform.currentTime
    val tokenDuration : Long = Helpers.weeks(4)
    val tokenSaved = Tokens.tokens.vend.createToken(TokenType.Access,
                                                    None,
                                                    Some(userId),
                                                    Some(tokenKey),
                                                    Some(tokenSecret),
                                                    Some(tokenDuration),
                                                    Some(new Date(currentTime+tokenDuration)),
                                                    Some(new Date(currentTime)),
                                                    None
                                                  )
    tokenSaved match {
      case Full(_) => true
      case _       => false
    }
  }

  def fromUrl( url: String,
               data: String = "",
               method: String,
               connectTimeout: Int = 2000,
               readTimeout: Int = 10000
             ) = {
    var content:String = ""
    import java.net.URL
    try {
      val connection = {
        if (url.startsWith("https://")) {
          val conn: HttpsURLConnection = new URL(url + {
            if (method == "GET") data
            else ""
          }).openConnection.asInstanceOf[HttpsURLConnection]
          conn
        }
        else {
          val conn: HttpURLConnection = new URL(url + {
            if (method == "GET") data
            else ""
          }).openConnection.asInstanceOf[HttpURLConnection]
          conn
        }
      }
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
      content = scala.io.Source.fromInputStream(inputStream).mkString
      if (inputStream != null) inputStream.close()
    } catch {
      case e:Throwable => logger.error(e)
    }
    content
  }


}
