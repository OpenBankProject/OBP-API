/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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
package code.consumer

import code.api.util.APIUtil.{has2021DirectLoginHeader, hasAnOAuth2Header, hasDirectLoginHeader, isApplicationsOwnUser}
import code.api.util.{CallContext, JwtUtil}
import code.model.Consumer
import com.openbankproject.commons.model.User
import net.liftweb.common.Box

/**
 * A Consumer's grant types: how tokens can be obtained through the Consumer.
 *
 * They say whether people can log in through the Consumer, whether the application can get a token for itself,
 * or both. They are optional, and only an administrator can set them.
 *
 * A person "logs in through a Consumer" when they get a token with that Consumer's client id or key. They can do this by an OAuth2
 * login, by DirectLogin, or by renewing such a login. The token then names both the person and the application.
 *
 * A Consumer with no grant types set (every existing Consumer) can be used in every way, exactly as before.
 *
 * Grant types only control how tokens are obtained. They do not decide what the application may do with a token,
 * or whether a call counts as the application acting as itself. For example:
 *
 *  - Only client_credentials: the application can get a token for itself through the Consumer. People cannot
 *    log in through it.
 *  - No client_credentials: people can log in through the Consumer. The application cannot get a token for itself
 *    through it, so it cannot act on its own through this Consumer.
 *
 * OBP never works out the grant types from other settings, such as the Scopes the Consumer holds or whether it
 * is a Platform App. So granting a Consumer a new Scope never changes how it can be used.
 *
 * The grant types are checked twice:
 *
 *  1. When a token is requested: by DirectLogin, and by OBP-OIDC and the Keycloak client storage, which read
 *     the grant types from OBP.
 *  2. When a token is used: by OBP-API, in AfterApiAuth.checkConsumerGrantTypes, using refusal() below.
 */
object ConsumerGrantTypes {

  val AuthorizationCode = "authorization_code"
  val RefreshToken = "refresh_token"
  val ClientCredentials = "client_credentials"
  /** OBP's DirectLogin: a person's username and password sent with the Consumer's key. */
  val DirectLogin = "direct_login"

  val Known: List[String] = List(AuthorizationCode, RefreshToken, ClientCredentials, DirectLogin)

  /** The grants by which a person logs in through a Consumer. */
  val PersonLogins: Set[String] = Set(AuthorizationCode, DirectLogin)

  /** The OAuth2 grant types of a Consumer that sets none, as identity providers have treated it so far. */
  val DefaultOAuth2: List[String] = List(AuthorizationCode, RefreshToken, ClientCredentials)

  /** "authorization_code, refresh_token" -> List(authorization_code, refresh_token): trimmed, lower case, distinct. */
  def parse(value: String): List[String] =
    Option(value).toList.flatMap(_.split(",")).map(_.trim.toLowerCase).filter(_.nonEmpty).distinct

  /** The unknown values in a list of grant types, if any. */
  def unknown(grantTypes: List[String]): List[String] = grantTypes.filterNot(Known.contains)

  def of(consumer: Consumer): List[String] = parse(consumer.grantTypes.get)

  def isSet(consumer: Consumer): Boolean = of(consumer).nonEmpty

  /** True when the Consumer sets no grant types, or lists this one. */
  def allows(consumer: Consumer, grantType: String): Boolean = !isSet(consumer) || of(consumer).contains(grantType)

  /** The OAuth2 grant types an identity provider may issue tokens for (DirectLogin is OBP's own, not OAuth2). */
  def oauth2GrantTypes(consumer: Consumer): List[String] =
    if (isSet(consumer)) of(consumer).filterNot(_ == DirectLogin) else DefaultOAuth2

  /**
   * Whether a JWT access token was issued by client credentials, to the application itself:
   *  - OBP-OIDC marks it with the claim grant_type = client_credentials;
   *  - a token whose subject is its own client (sub = azp), as OBP-OIDC and others issue it;
   *  - Keycloak issues it to the client's service account, preferred_username "service-account-" + the client id.
   */
  def isClientCredentialsToken(jwt: String): Boolean = {
    def claim(name: String): Option[String] = Option(JwtUtil.getClaim(name = name, jwtToken = jwt)).filter(_.nonEmpty)
    val azp = claim("azp").orElse(claim("client_id"))
    claim("grant_type").contains(ClientCredentials) ||
      (claim("sub").nonEmpty && claim("sub") == azp) ||
      azp.exists(clientId => claim("preferred_username").contains(s"service-account-$clientId"))
  }

  private def bearerToken(cc: CallContext): Option[String] =
    cc.authReqHeaderField.toOption.filter(h => hasAnOAuth2Header(Box !! h))
      .map(_.replaceAll("Authorization:", "").replaceAll("Bearer", "").trim).filter(_.nonEmpty)

  /**
   * Checks whether this call may go through this Consumer. Returns None if it may. If it may not, returns the
   * grant type the call would need, for the error message.
   *
   * Only Consumers with grant types set are checked. A call without a user is never refused here. In that case
   * only the Consumer's key or certificate identifies the caller, and nobody has logged in. The endpoint's own
   * access check then decides whether the application can make the call.
   */
  def refusal(consumer: Consumer, user: Box[User], cc: CallContext): Option[String] = {
    if (!isSet(consumer) || user.isEmpty) None
    else {
      val directLogin = has2021DirectLoginHeader(cc.requestHeaders) || hasDirectLoginHeader(cc.authReqHeaderField)
      val bearer = bearerToken(cc)
      // The application's own User (OBP-OIDC's client provider) is never a person: nobody can sign up or log in
      // with a password there, whichever header carries its token.
      val applicationItself =
        user.exists(isApplicationsOwnUser(_, consumer)) || (!directLogin && bearer.exists(isClientCredentialsToken))
      if (applicationItself) {
        if (allows(consumer, ClientCredentials)) None else Some(ClientCredentials)
      } else {
        val login = if (directLogin) DirectLogin else if (bearer.isDefined) AuthorizationCode else s"$AuthorizationCode or $DirectLogin"
        val allowed = if (directLogin || bearer.isDefined) allows(consumer, login) else of(consumer).exists(PersonLogins.contains)
        if (allowed) None else Some(login)
      }
    }
  }
}
