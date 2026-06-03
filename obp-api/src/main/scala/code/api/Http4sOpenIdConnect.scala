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

import cats.effect.IO
import code.api.OAuth2Login.Hydra
import code.api.util.APIUtil._
import code.api.util.http4s.{ErrorResponseConverter, Http4sCallContextBuilder}
import code.api.util.{APIUtil, AfterApiAuth, CustomJsonFormats, ErrorMessages, JwtUtil}
import code.api.v6_0_0.JSONFactory600
import code.consumer.Consumers
import code.loginattempts.LoginAttempt
import code.model.dataAccess.AuthUser
import code.model.{AppType, Consumer}
import code.token.{OpenIDConnectToken, TokensOpenIDConnect}
import code.users.Users
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import net.liftweb.common._
import net.liftweb.db.DB
import net.liftweb.json
import net.liftweb.json.JsonAST.prettyRender
import net.liftweb.json.{Extraction, Formats}
import net.liftweb.mapper.By
import net.liftweb.util.DefaultConnectionIdentifier
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`

import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection

/**
 * Per-identity-provider OpenID Connect configuration, read from
 * `openid_connect_$provider.*` props. Moved verbatim from the retired Lift
 * `openidconnect.scala`; consumed by [[Http4sOpenIdConnect]].
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
  lazy val openIDConnectEnabled = code.api.Constant.openidConnectEnabled
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

/**
 * Native http4s OpenID Connect callback, replacing the Lift `OpenIdConnect`
 * `serve {}` dispatch. OBP-API acts as the OIDC relying party: an external
 * provider (OBP-OIDC, Keycloak, ...) authenticates the user, redirects the
 * browser to one of these callbacks with `?code=...&state=...`, and the handler
 * exchanges the code for tokens server-side.
 *
 * Provider contract preserved unchanged: the three callback paths, the
 * form-encoded token exchange to `openid_connect_$provider.endpoint.token`
 * (reading the same `openid_connect_$provider.*` props), and JWT validation
 * against the provider's `jwks_uri`.
 *
 * Difference from the Lift version: instead of the (now-vestigial) Lift-session
 * `logUserIn` + redirect, on success we mint a usable OBP DirectLogin token and
 * return `200 {"token": "..."}`. The client then calls OBP APIs with
 * `DirectLogin: token=...`.
 *
 * Gating: the route only fires when `openid_connect.enabled=true` (default
 * false); otherwise the pattern guard fails and the request falls through to
 * `notFoundCatchAll` (JSON 404), matching prior behaviour. A second runtime gate
 * `allow_openid_connect` (default true) returns 401 when set false.
 */
object Http4sOpenIdConnect extends MdcLoggable {

  private implicit val formats: Formats = CustomJsonFormats.formats

  // Referenced by code.api.OAuth2 (getOrCreateConsumer description); kept here as
  // the single home after the Lift OpenIdConnect object was retired.
  val openIdConnect = "OpenID Connect"

  // Registration gate, read per request so it stays togglable (default false).
  private def enabled: Boolean = getPropsAsBoolValue("openid_connect.enabled", false)

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ (GET | POST) -> Root / "auth" / "openid-connect" / "callback"   if enabled => handle(req, 1)
    case req @ (GET | POST) -> Root / "auth" / "openid-connect" / "callback-1" if enabled => handle(req, 1)
    case req @ (GET | POST) -> Root / "auth" / "openid-connect" / "callback-2" if enabled => handle(req, 2)
  }

  private val jsonContentType = `Content-Type`(MediaType.application.json, Charset.`UTF-8`)

  private def handle(req: Request[IO], identityProvider: Int): IO[Response[IO]] =
    Http4sCallContextBuilder.fromRequest(req, apiVersion = "").flatMap { cc =>
      if (!getPropsAsBoolValue("allow_openid_connect", true)) {
        ErrorResponseConverter.createErrorResponse(401, ErrorMessages.OpenIDConnectIsDisabled, cc)
      } else {
        val code  = param(req, cc, "code").getOrElse("")
        val state = param(req, cc, "state").getOrElse("0")
        // The whole flow is synchronous Lift-mapper / blocking HTTP work; run it off the compute pool.
        IO.blocking(processCallback(identityProvider, code, state)).flatMap {
          case Right(token) =>
            Ok(prettyRender(Extraction.decompose(JSONFactory600.createTokenJSON(token))))
              .map(_.withContentType(jsonContentType))
          case Left((httpCode, message)) =>
            ErrorResponseConverter.createErrorResponse(httpCode, message, cc)
        }
      }
    }

  /** Read a parameter from the query string, falling back to a form-urlencoded body (mirrors Lift `S.param`). */
  private def param(req: Request[IO], cc: code.api.util.CallContext, name: String): Option[String] =
    req.uri.query.params.get(name).orElse {
      cc.httpBody.flatMap { body =>
        body.split("&").iterator.map(_.split("=", 2)).collectFirst {
          case Array(k, v) if java.net.URLDecoder.decode(k, "UTF-8") == name => java.net.URLDecoder.decode(v, "UTF-8")
        }
      }
    }

  private def checkSessionState(state: String, sessionState: String): Boolean =
    if (getPropsAsBoolValue("openid_connect.check_session_state", true)) state == sessionState else true

  /**
   * Ports the Lift `callbackUrlCommonCode` business logic. Returns the minted OBP token on success,
   * or `(httpCode, message)` on failure. All provider-facing steps (token exchange, JWT validation)
   * and all provisioning side effects (resource user, auth user, entitlements, consumer, OIDC-token
   * persistence) are preserved verbatim.
   */
  private def processCallback(identityProvider: Int, code: String, state: String): Either[(Int, String), String] = {
    // Session state was always defaulted to "" once the portal pages were removed; preserved here.
    val sessionState = ""
    if (!checkSessionState(state, sessionState)) {
      Left((401, ErrorMessages.InvalidOpenIDConnectState))
    } else {
      exchangeAuthorizationCodeForTokens(code, identityProvider) match {
        case Full((idToken, accessToken, tokenType, expiresIn, refreshToken, scope)) =>
          JwtUtil.validateIdToken(idToken, OpenIdConnectConfig.get(identityProvider).jwks_uri) match {
            case Full(_) =>
              // Restore the single-connection-per-request semantics that Lift's removed
              // S.addAround(DB.buildLoanWrapper) gave: all provisioning writes share one
              // connection and commit together; a thrown DB error rolls the whole set back
              // (same primitive as deletion.DeletionUtil.databaseAtomicTask). The network
              // steps above (token exchange + JWKS validation) are kept OUTSIDE the tx so no
              // DB connection is held during remote HTTP calls.
              DB.use(DefaultConnectionIdentifier) { _ =>
                getOrCreateResourceUser(idToken) match {
                  case Full(user) if LoginAttempt.userIsLocked(user.provider, user.name) =>
                    Left((401, ErrorMessages.UsernameHasBeenLocked))
                  case Full(user) =>
                    getOrCreateAuthUser(user) match {
                      case Full(authUser) =>
                        // Grant roles according to the props email_domain_to_space_mappings
                        AuthUser.grantEmailDomainEntitlementsToUser(authUser)
                        AuthUser.grantEntitlementsToUseDynamicEndpointsInSpaces(authUser)
                        // User init actions
                        AfterApiAuth.innerLoginUserInitAction(Full(authUser))
                        getOrCreateConsumer(idToken, user.userId) match {
                          case Full(consumer) =>
                            saveAuthorizationToken(tokenType, accessToken, idToken, refreshToken, scope, expiresIn, authUser.id.get) match {
                              case Full(_) =>
                                // Mint a usable OBP DirectLogin token bound to the provisioned user + consumer.
                                DirectLogin.issueTokenForUser(user.userPrimaryKey.value, consumer.key.get) match {
                                  case Full(token) => Right(token)
                                  case _           => Left((500, ErrorMessages.CouldNotHandleOpenIDConnectData + "issueToken"))
                                }
                              case _ => Left((401, ErrorMessages.CouldNotHandleOpenIDConnectData + "saveAuthorizationToken"))
                            }
                          case _ => Left((401, ErrorMessages.CouldNotHandleOpenIDConnectData + "getOrCreateConsumer"))
                        }
                      case _ => Left((401, ErrorMessages.CouldNotHandleOpenIDConnectData + "getOrCreateAuthUser"))
                    }
                  case _ => Left((401, ErrorMessages.CouldNotSaveOpenIDConnectUser))
                }
              }
            case _ => Left((401, ErrorMessages.CouldNotValidateIDToken))
          }
        case _ => Left((401, ErrorMessages.CouldNotExchangeAuthorizationCodeForTokens))
      }
    }
  }

  // ── Business-logic helpers, ported verbatim from the Lift OpenIdConnect object ────────────────────

  private def getOrCreateAuthUser(user: User): Box[AuthUser] = {
    AuthUser.find(By(AuthUser.user, user.userPrimaryKey.value)) match {
      case Full(user) => Full(user)
      case _ => createAuthUser(user)
    }
  }

  private def getOrCreateResourceUser(idToken: String): Box[User] = {
    val uniqueIdGivenByProvider = JwtUtil.getSubject(idToken)
    val preferredUsername = JwtUtil.getOptionalClaim("preferred_username", idToken)
    // Try to get provider from token first, fallback to Hydra resolver
    val provider = JwtUtil.getProvider(idToken).getOrElse(Hydra.resolveProvider(idToken))
    val providerId = preferredUsername.orElse(uniqueIdGivenByProvider)
    Users.users.vend.getUserByProviderId(provider = provider, idGivenByProvider = providerId.getOrElse("")).or { // Find a user
      Users.users.vend.createResourceUser( // Otherwise create a new one
        provider = provider,
        providerId = providerId,
        createdByConsentId = None,
        name = providerId,
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
          logger.debug(s"(idToken: $idToken, accessToken: $accessToken, tokenType: $tokenType, expiresIn.toLong: ${expiresIn.toLong}, refreshToken: $refreshToken, scope: $scope)")
          (idToken, accessToken, tokenType, expiresIn.toLong, refreshToken, scope)
        }
      case badObject@Failure(_, _, _) =>
        logger.debug("Error at exchangeAuthorizationCodeForTokens: " + badObject)
        badObject
      case everythingElse =>
        logger.debug("Error at exchangeAuthorizationCodeForTokens: " + everythingElse)
        Failure(ErrorMessages.InternalServerError + " - exchangeAuthorizationCodeForTokens")
    }
  }

  private def getOrCreateConsumer(idToken: String, userId: String): Box[Consumer] = {
    Consumers.consumers.vend.getOrCreateConsumer(
      consumerId=None,
      None,
      None,
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
