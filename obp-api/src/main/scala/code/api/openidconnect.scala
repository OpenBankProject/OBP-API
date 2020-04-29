/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
package code.api

import java.net.HttpURLConnection
import java.util.Date

import code.api.util.APIUtil._
import code.api.util.{APIUtil, JwtUtil}
import code.model.dataAccess.AuthUser
import code.token.Tokens
import code.users.Users
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import com.openbankproject.commons.util.ApiVersion
import javax.net.ssl.HttpsURLConnection
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.provider.HTTPCookie
import net.liftweb.json
import net.liftweb.json.JValue
import net.liftweb.mapper.By
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers._

import scala.compat.Platform

/**
  * This object provides the API calls necessary to authenticate
  * users using OpenIdConnect (http://openid.net).
  */

case class OpenIdConnectConfig(client_secret: String,
                               client_id: String,
                               callback_url: String,
                               domain: String,
                               userinfo_endpoint: String,
                               token_endpoint: String,
                               authorization_endpoint: String,
                               url_button: String
                              )

object OpenIdConnectConfig {
  def get() = {
    def getProps(props: String) = APIUtil.getPropsValue(props).openOrThrowException(s"no $props set")
    OpenIdConnectConfig(
      getProps("openid_connect.client_secret"),
      getProps("openid_connect.client_id"),
      getProps("openid_connect.callback_url"),
      getProps("openid_connect.domain"),
      getProps("openid_connect.endpoint.userinfo"),
      getProps("openid_connect.endpoint.token"),
      getProps("openid_connect.endpoint.authorization"),
      getProps("openid_connect.url.buttonImage")
    )
  }
}

object OpenIdConnect extends OBPRestHelper with MdcLoggable {

  val version = ApiVersion.openIdConnect1 // "1.0" // TODO: Should this be the lowest version supported or when introduced?
  val versionStatus = "DRAFT"

  serve {
    case Req("my" :: "logins" :: "openid-connect" :: Nil, _, PostRequest | GetRequest) => {
      var httpCode = 500
      var message = "unknown"
      for {
        code <- S.params("code")
        state <- S.param("state")
      } yield {
        // Get the token
        message=code
        exchangeAuthorizationCodeForTokens(code) match {
          case Full((idToken, accessToken, tokenType)) =>
            val subject = JwtUtil.getSubject(idToken).getOrElse("")
            val issuer = JwtUtil.getIssuer(idToken).getOrElse("")
            def getClaim(name: String, idToken: String): Option[String] = {
              val claim = JwtUtil.getClaim(name = name, jwtToken = idToken)
              claim match {
              case null => None
              case string => Some(string)
              }
            }
            Users.users.vend.getUserByProviderId(provider = issuer, idGivenByProvider = subject).or { // Find a user
              Users.users.vend.createResourceUser( // Otherwise create a new one
                provider = issuer,
                providerId = Some(subject),
                name = getClaim(name = "given_name", idToken = idToken).orElse(Some(subject)),
                email = getClaim(name = "email", idToken = idToken),
                userId = None
              )
            } match {
              case Full(user) =>
                for {
                  authUser: AuthUser <- AuthUser.find(By(AuthUser.user, user.userPrimaryKey.value)) match {
                    case Full(user) => Full(user)
                    case _          => createAuthUser(user)
                  }
                } yield {
                  saveAuthorizationToken(accessToken, accessToken, user.userPrimaryKey.value)
                  httpCode = 200
                  message= String.format("oauth_token=%s&oauth_token_secret=%s", accessToken, accessToken)
                  val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
                  AuthUser.logUserIn(authUser, () => {
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

  private def createAuthUser(user: User): Full[AuthUser] = {
    val newUser = AuthUser.create
      .firstName(user.name)
      .email(user.emailAddress)
      .user(user.userPrimaryKey.value)
      .username(user.idGivenByProvider)
      .provider(user.provider)
      // No need to store password, so store dummy string instead
      .password(generateUUID())
      .validated(true)
    // Save the user in order to be able to log in
    newUser.saveMe()
    Full(newUser)
  }

  def exchangeAuthorizationCodeForTokens(authorizationCode: String): Box[(String, String, String)] = {
    val config = OpenIdConnectConfig.get()
    val data =    "client_id=" + config.client_id + "&" +
                  "client_secret=" + config.client_secret + "&" +
                  "redirect_uri=" + config.callback_url + "&" +
                  "code=" + authorizationCode + "&" +
                  "grant_type=authorization_code"
    val response = fromUrl(String.format("%s", config.token_endpoint), data, "POST")
    val tokenResponse = json.parse(response)
    for {
      idToken <- tryo{(tokenResponse \ "id_token").extractOrElse[String]("")}
      accessToken <- tryo{(tokenResponse \ "access_token").extractOrElse[String]("")}
      tokenType <- tryo{(tokenResponse \ "token_type").extractOrElse[String]("")}
    } yield {
      (idToken, accessToken, tokenType)
    }
  }

  def getUserInfo(accessToken: String): Box[JValue] = {
    val config = OpenIdConnectConfig.get()
    val userResponse = json.parse(
      fromUrl(
        String.format("%s", config.userinfo_endpoint), 
        "?access_token="+accessToken, 
        "GET"
      )
    )
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
    val expiration = APIUtil.getPropsAsIntValue("token_expiration_weeks", 4)
    val tokenDuration : Long = Helpers.weeks(expiration)
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
