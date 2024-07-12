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

import code.api.OAuth2Login.Hydra
import code.api.util.APIUtil._
import code.api.util.{APIUtil, AfterApiAuth, ErrorMessages, JwtUtil}
import code.consumer.Consumers
import code.loginattempts.LoginAttempt
import code.model.{AppType, Consumer}
import code.model.dataAccess.AuthUser
import code.snippet.OpenIDConnectSessionState
import code.token.{OpenIDConnectToken, TokensOpenIDConnect}
import code.users.Users
import code.util.Helper.{MdcLoggable, ObpS}
import com.openbankproject.commons.model.User
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}
import javax.net.ssl.HttpsURLConnection
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.json
import net.liftweb.json.JValue
import net.liftweb.mapper.By
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers._

/**
  * This object provides the API calls necessary to authenticate
  * users using OpenIdConnect (http://openid.net).
  */

case class OpenIdConnectConfig(client_secret: String,
                               client_id: String,
                               callback_url: String,
                               userinfo_endpoint: String,
                               token_endpoint: String,
                               authorization_endpoint: String,
                               jwks_uri: String,
                               access_type_offline: Boolean
                              )

object OpenIdConnectConfig {
  lazy val openIDConnectEnabled = APIUtil.getPropsAsBoolValue("openid_connect.enabled", false)
  def getProps(props: String): String = {
    APIUtil.getPropsValue(props).getOrElse("")
  }
  def get(provider: Int): OpenIdConnectConfig = {
    OpenIdConnectConfig(
      getProps(s"openid_connect_$provider.client_secret"),
      getProps(s"openid_connect_$provider.client_id"),
      getProps(s"openid_connect_$provider.callback_url"),
      getProps(s"openid_connect_$provider.endpoint.userinfo"),
      getProps(s"openid_connect_$provider.endpoint.token"),
      getProps(s"openid_connect_$provider.endpoint.authorization"),
      getProps(s"openid_connect_$provider.endpoint.jwks_uri"),
      APIUtil.getPropsAsBoolValue(s"openid_connect_$provider.access_type_offline", false),
    )
  }
}

object OpenIdConnect extends OBPRestHelper with MdcLoggable {

  val version = ApiVersion.openIdConnect1 // "1.0" // TODO: Should this be the lowest version supported or when introduced?
  val versionStatus = ApiVersionStatus.DRAFT.toString

  val openIdConnect = "OpenID Connect"

  serve {
    case Req("auth" :: "openid-connect" :: "callback" :: Nil, _, PostRequest | GetRequest) =>
      callbackUrlCommonCode(1)    
    case Req("auth" :: "openid-connect" :: "callback-1" :: Nil, _, PostRequest | GetRequest) =>
      callbackUrlCommonCode(1)
    case Req("auth" :: "openid-connect" :: "callback-2" :: Nil, _, PostRequest | GetRequest) =>
      callbackUrlCommonCode(2)
  }

  private def callbackUrlCommonCode(identityProvider: Int): JsonResponse = {
    val (code, state, sessionState) = extractParams(S)
    logger.debug("(code, state, sessionState) = " + (code, state, sessionState))
    logger.debug("S.receivedCookies = " + S.receivedCookies)
    logger.debug("S.responseCookies = " + S.responseCookies)
    logger.debug("server_mode = " + APIUtil.getPropsValue("server_mode"))

    def chainErrorMessage(badObj: Failure, errorMessage: String) = {
      val chainedFailure: Failure = badObj ?~! errorMessage
      (401, filterMessage(chainedFailure), None)
    }

    def checkSessionState: Boolean = {
      if (APIUtil.getPropsAsBoolValue("openid_connect.check_session_state", true))
        state == sessionState
      else true
    }

    val (httpCode, message, authorizationUser) = if (checkSessionState) {
      exchangeAuthorizationCodeForTokens(code, identityProvider) match {
        case Full((idToken, accessToken, tokenType, expiresIn, refreshToken, scope)) =>
          JwtUtil.validateIdToken(idToken, OpenIdConnectConfig.get(identityProvider).jwks_uri) match {
            case Full(_) =>
              getOrCreateResourceUser(idToken) match {
                case Full(user) if LoginAttempt.userIsLocked(user.provider, user.name) => // User is locked
                  (401, ErrorMessages.UsernameHasBeenLocked, None)
                case Full(user) => // All good
                  getOrCreateAuthUser(user) match {
                    case Full(authUser) =>
                      // Grant roles according to the props email_domain_to_space_mappings
                      AuthUser.grantEmailDomainEntitlementsToUser(authUser)
                      // Grant roles according to the props email_domain_to_space_mappings
                      AuthUser.grantEntitlementsToUseDynamicEndpointsInSpaces(authUser)
                      // User init actions
                      AfterApiAuth.innerLoginUserInitAction(Full(authUser))
                      // Consumer
                      getOrCreateConsumer(idToken, user.userId) match {
                        case Full(consumer) =>
                          saveAuthorizationToken(tokenType, accessToken, idToken, refreshToken, scope, expiresIn, authUser.id.get) match {
                            case Full(token) => (200, "OK", Some(authUser))
                            case badObj@Failure(_, _, _) => chainErrorMessage(badObj, ErrorMessages.CouldNotHandleOpenIDConnectData+ "saveAuthorizationToken") 
                            case _ => (401, ErrorMessages.CouldNotHandleOpenIDConnectData + "saveAuthorizationToken", Some(authUser))
                          }
                        case badObj@Failure(_, _, _) => chainErrorMessage(badObj, ErrorMessages.CouldNotHandleOpenIDConnectData + "getOrCreateConsumer")
                        case _ => (401, ErrorMessages.CouldNotHandleOpenIDConnectData + "getOrCreateConsumer", Some(authUser))
                      }
                    case badObj@Failure(_, _, _) => chainErrorMessage(badObj, ErrorMessages.CouldNotHandleOpenIDConnectData + "getOrCreateAuthUser")
                    case _ => (401, ErrorMessages.CouldNotHandleOpenIDConnectData + "getOrCreateAuthUser", None)
                  }
                case badObj@Failure(_, _, _) => chainErrorMessage(badObj, ErrorMessages.CouldNotSaveOpenIDConnectUser)
                case _ => (401, ErrorMessages.CouldNotSaveOpenIDConnectUser, None)
              }
            case badObj@Failure(_, _, _) => chainErrorMessage(badObj, ErrorMessages.CouldNotValidateIDToken)
            case _ => (401, ErrorMessages.CouldNotValidateIDToken, None)
          }
        case badObj@Failure(_, _, _) => chainErrorMessage(badObj, ErrorMessages.CouldNotExchangeAuthorizationCodeForTokens)
        case _ => (401, ErrorMessages.CouldNotExchangeAuthorizationCodeForTokens, None)
      }
    } else {
      (401, ErrorMessages.InvalidOpenIDConnectState, None)
    }

    (httpCode, authorizationUser) match {
      case (200, Some(user)) =>
        val loginRedirect = AuthUser.loginRedirect.get
        AuthUser.logUserIn(user, () => {
          S.notice(S.?("logged.in"))
          //This redirect to homePage, it is from scala code, no open redirect issue.
          val redirectUrl = loginRedirect match {
            case Full(url) =>
              AuthUser.loginRedirect(Empty)
              url
            case _ =>
              AuthUser.homePage
          }
          S.redirectTo(redirectUrl)
        })
      case _ =>
        errorJsonResponse(message, httpCode)
    }
  }

  private def extractParams(s: S): (String, String, String) = {
    // TODO Figure out why ObpS does not contain response parameter code
    val code = s.param("code")
    val state = ObpS.param("state")
    val sessionState = OpenIDConnectSessionState.get
    (code.getOrElse(""), state.getOrElse("0"), sessionState.map(_.toString).getOrElse("1"))
  }

  private def getOrCreateAuthUser(user: User): Box[AuthUser] = {
    AuthUser.find(By(AuthUser.user, user.userPrimaryKey.value)) match {
      case Full(user) => Full(user)
      case _ => createAuthUser(user)
    }
  }

  private def getOrCreateResourceUser(idToken: String): Box[User] = {
    val uniqueIdGivenByProvider = JwtUtil.getSubject(idToken)
    val provider = Hydra.resolveProvider(idToken)
    Users.users.vend.getUserByProviderId(provider = provider, idGivenByProvider = uniqueIdGivenByProvider.getOrElse("")).or { // Find a user
      Users.users.vend.createResourceUser( // Otherwise create a new one
        provider = provider,
        providerId = uniqueIdGivenByProvider,
        createdByConsentId = None,
        name = uniqueIdGivenByProvider,
        email = getClaim(name = "email", idToken = idToken),
        userId = None,
        createdByUserInvitationId = None,
        company = None,
        lastMarketingAgreementSignedDate = None
      )
    }
  }
  
  private def getClaim(name: String, idToken: String): Option[String] = {
    val claim = JwtUtil.getClaim(name = name, jwtToken = idToken)
    claim match {
      case null => None
      case string => Some(string)
    }
  }
  private def createAuthUser(user: User): Box[AuthUser] = tryo {
    val newUser = AuthUser.create
      .firstName(user.name)
      .email(user.emailAddress)
      .user(user.userPrimaryKey.value)
      .username(user.idGivenByProvider)
      .provider(user.provider)
      // No need to store password, so store dummy string instead
      .password(Helpers.randomString(40))
      .validated(true)
    // Save the user in order to be able to log in
    newUser.saveMe()
  }

  def exchangeAuthorizationCodeForTokens(authorizationCode: String, identityProvider: Int): Box[(String, String, String, Long, String, String)] = {
    val config = OpenIdConnectConfig.get(identityProvider)
    val data =    "client_id=" + config.client_id + "&" +
                  "client_secret=" + config.client_secret + "&" +
                  "redirect_uri=" + config.callback_url + "&" +
                  "code=" + authorizationCode + "&" +
                  "grant_type=authorization_code"
    logger.debug("Request parameters: " + data)
    logger.debug("Token endpoint: " + config.token_endpoint)
    val response: Box[String] = fromUrl(String.format("%s", config.token_endpoint), data, "POST")
    logger.debug("Response: " + response)
    response match {
      case Full(value) =>
        val tokenResponse = json.parse(value)
        logger.debug("Token response: " + tokenResponse)
        for {
          idToken <- tryo{(tokenResponse \ "id_token").extractOrElse[String]("")}
          accessToken <- tryo{(tokenResponse \ "access_token").extractOrElse[String]("")}
          tokenType <- tryo{(tokenResponse \ "token_type").extractOrElse[String]("")}
          expiresIn <- tryo{(tokenResponse \ "expires_in").extractOrElse[String]("")}
          refreshToken <- tryo{(tokenResponse \ "refresh_token").extractOrElse[String]("")}
          scope <- tryo{(tokenResponse \ "scope").extractOrElse[String]("")}
        } yield {
          (idToken, accessToken, tokenType, expiresIn.toLong, refreshToken, scope)
        }
      case badObject@Failure(_, _, _) => badObject
      case _ => Failure(ErrorMessages.InternalServerError + " - exchangeAuthorizationCodeForTokens")
    }
  }

  def getUserInfo(accessToken: String, identityProvider: Int): Box[JValue] = {
    val config = OpenIdConnectConfig.get(identityProvider)
    val userResponse = json.parse(
      fromUrl(
        String.format("%s", config.userinfo_endpoint), 
        "?access_token="+accessToken, 
        "GET"
      ).openOrThrowException(ErrorMessages.InternalServerError + " - getUserInfo")
    )
    userResponse match {
      case response: JValue => Full(response)
      case _ => Empty
    }
  }
  
  private def getOrCreateConsumer(idToken: String, userId: String): Box[Consumer] = {
    Consumers.consumers.vend.getOrCreateConsumer(
      consumerId=Some(APIUtil.generateUUID()),
      Some(Helpers.randomString(40).toLowerCase),
      Some(Helpers.randomString(40).toLowerCase),
      Some(JwtUtil.getAudience(idToken).mkString(",")),
      getClaim(name = "azp", idToken = idToken),
      JwtUtil.getIssuer(idToken),
      JwtUtil.getSubject(idToken),
      Some(true),
      name = Some(Helpers.randomString(10).toLowerCase),
      appType = Some(AppType.Confidential),
      description = Some(openIdConnect),
      developerEmail = getClaim(name = "email", idToken = idToken),
      redirectURL = None,
      createdByUserId = Some(userId)
    )
  }

  private def saveAuthorizationToken(tokenType: String,
                                     accessToken: String,
                                     idToken: String,
                                     refreshToken: String,
                                     scope: String,
                                     expiresIn: Long,
                                     authUserPrimaryKey: Long): Box[OpenIDConnectToken] = {
    val token = TokensOpenIDConnect.tokens.vend.createToken(
      tokenType = tokenType,
      accessToken = accessToken,
      idToken = idToken,
      refreshToken = refreshToken,
      scope = scope,
      expiresIn = expiresIn,
      authUserPrimaryKey = authUserPrimaryKey
    )
    token match  {
      case Full(_) => // All good
      case error => logger.error(error)
    }
    token
  }

  def fromUrl( url: String,
               data: String = "",
               method: String,
               connectTimeout: Int = 2000,
               readTimeout: Int = 10000
             ): Box[String] = {
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
      Full(content)
    } catch {
      case e:Throwable => 
        e.printStackTrace()
        logger.error(e)
        Failure(e.getMessage)
    }
  }


}
