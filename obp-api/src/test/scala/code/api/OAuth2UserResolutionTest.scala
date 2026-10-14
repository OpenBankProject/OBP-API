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

package code.api

import code.api.util.APIUtil
import code.model.dataAccess.AuthUser
import code.setup.{DefaultUsers, ServerSetup, TestPasswordConfig}
import code.users.Users
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import com.openbankproject.commons.model.User
import net.liftweb.util.Helpers.randomString

import java.net.URI

/**
 * How a token's user is found (OAuth2Login.OAuth2Util.getOrCreateResourceUser), where one identity could
 * otherwise land on another's user:
 *  - an OBP-OIDC client-credentials token (sub = client id, no provider claim) gets a user no person can
 *    sign up as, while an app that already has its user under the local provider keeps it
 *  - only OBP's own identity providers may name the provider; only Keycloak may use the federated sub form
 */
class OAuth2UserResolutionTest extends ServerSetup with DefaultUsers {

  private val obpOidcIssuer = "http://localhost:9000/obp-oidc"
  private val otherIssuer = "https://idp.example.com"

  private object oidcProvider extends OAuth2Login.OAuth2Util {
    override def wellKnownOpenidConfiguration: URI = new URI(s"$obpOidcIssuer/.well-known/openid-configuration")
  }

  // getOrCreateResourceUser only parses claims (no signature verification), as in OAuth2ConsumerResolutionTest.
  private def token(iss: String, sub: String, claims: (String, String)*): String = {
    val builder = new JWTClaimsSet.Builder().issuer(iss).subject(sub)
    claims.foreach { case (k, v) => builder.claim(k, v) }
    val jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build())
    jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef"))
    jwt.serialize()
  }

  /** As OBP-OIDC issues it for client_credentials. */
  private def appToken(clientId: String) =
    token(obpOidcIssuer, clientId, "azp" -> clientId, "client_id" -> clientId, "grant_type" -> "client_credentials")

  /** Like the keys OBP-API generates for Consumers: 40 lowercase letters and digits, also a valid username. */
  private def freshClientId() = randomString(40).toLowerCase

  private def resolve(jwt: String): User =
    oidcProvider.getOrCreateResourceUser(jwt).openOrThrowException("getOrCreateResourceUser must return a user")

  private val local = Constant.localIdentityProvider

  feature("an OBP-OIDC client-credentials token never lands on a person's user") {

    scenario("a new app gets a user under a provider no person can sign up under") {
      val clientId = freshClientId()
      val user = resolve(appToken(clientId))
      user.provider should equal(OAuth2Login.OBPOIDC.clientCredentialsProvider)
      user.idGivenByProvider should equal(clientId)
      resolve(appToken(clientId)).userId should equal(user.userId)
    }

    scenario("an app that already has its user under the local provider keeps it") {
      val clientId = freshClientId()
      val existing = Users.users.vend.createResourceUser(
        provider = local, providerId = Some(clientId), createdByConsentId = None, name = Some(clientId), email = None,
        userId = None, createdByUserInvitationId = None, company = None, lastMarketingAgreementSignedDate = None
      ).openOrThrowException("could not create the app's existing user")
      resolve(appToken(clientId)).userId should equal(existing.userId)
    }

    scenario("a person who signed up with the client id as username does not become the app's user") {
      val clientId = freshClientId()
      AuthUser.create.email(s"$clientId@example.com").username(clientId).password(TestPasswordConfig.VALID_PASSWORD)
        .validated(true).firstName("x").lastName("y").saveMe()
      val person = Users.users.vend.getUserByProviderId(local, clientId).openOrThrowException("sign-up made no user")
      val user = resolve(appToken(clientId))
      user.userId should not equal person.userId
      user.provider should equal(OAuth2Login.OBPOIDC.clientCredentialsProvider)
    }
  }

  feature("only OBP's identity providers may say which user a token is") {

    scenario("an OBP-OIDC user token naming the local provider is that local user") {
      val user = resolve(token(obpOidcIssuer, resourceUser1.idGivenByProvider, "azp" -> "some-app", "provider" -> resourceUser1.provider))
      user.userId should equal(resourceUser1.userId)
    }

    scenario("another issuer's provider claim is ignored") {
      val user = resolve(token(otherIssuer, resourceUser1.idGivenByProvider, "azp" -> "some-app", "provider" -> resourceUser1.provider))
      user.userId should not equal resourceUser1.userId
      user.provider should equal(otherIssuer)
    }

    scenario("another issuer's federated sub does not name a user_id") {
      val sub = s"f:${APIUtil.generateUUID()}:${resourceUser1.userId}"
      val user = resolve(token(otherIssuer, sub, "azp" -> "some-app"))
      user.userId should not equal resourceUser1.userId
    }
  }
}
