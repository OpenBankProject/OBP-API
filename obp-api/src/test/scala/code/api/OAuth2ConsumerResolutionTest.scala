package code.api

import code.api.util.APIUtil
import code.consumer.Consumers
import code.model.Consumer
import code.setup.ServerSetup
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import net.liftweb.common.Empty

import java.net.URI

/**
 * Executable specification of how OAuth2/OIDC id_tokens are resolved to Consumers
 * (OAuth2Login.OAuth2Util.getOrCreateConsumer -> MappedConsumersProvider.getOrCreateConsumer).
 *
 * The contract under test (see docs/IdP/OAUTH2_IDENTITY_PROVIDERS.md, "Google client ID policy"):
 *  - granularity is one Consumer per (azp, iss) — per OAuth client per issuer, NOT per user;
 *    the sub claim is stored on the Consumer but is not part of the lookup key
 *  - a pre-registered Consumer whose key equals the OAuth2 client ID takes priority over
 *    auto-creation, and displaces a stale auto-created Consumer holding the same (azp, iss)
 *  - auto-created Consumers are named from the token's name claim (the first user who logged
 *    in with that client), falling back to the description; their consumerId derives from azp
 */
class OAuth2ConsumerResolutionTest extends ServerSetup {

  private val googleIssuer = "https://accounts.google.com"

  private object oidcProvider extends OAuth2Login.OAuth2Util {
    override def wellKnownOpenidConfiguration: URI = new URI("https://accounts.google.com/.well-known/openid-configuration")
  }

  // getOrCreateConsumer only parses claims (no signature verification), so an
  // HMAC-signed token is enough to exercise it — same trick as OAuth2AudienceValidationTest.
  private def idToken(azp: String, iss: String, sub: String, name: Option[String] = None): String = {
    val builder = new JWTClaimsSet.Builder().issuer(iss).subject(sub).audience(azp).claim("azp", azp)
    name.foreach(builder.claim("name", _))
    val jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build())
    jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef"))
    jwt.serialize()
  }

  private def freshClientId() = s"${APIUtil.generateUUID().takeWhile(_ != '-')}-test.apps.googleusercontent.com"

  private def resolve(token: String, description: String = "OpenID Connect"): Consumer =
    oidcProvider.getOrCreateConsumer(token, Empty, Some(description))
      .openOrThrowException("getOrCreateConsumer must return a consumer")

  Feature("consumer resolution is per (azp, iss) — one Consumer per OAuth client per issuer") {

    Scenario("two different users of the same client resolve to the same consumer") {
      val clientId = freshClientId()
      When("two users with different sub claims log in with the same client and issuer")
      val first = resolve(idToken(clientId, googleIssuer, sub = "user-one", name = Some(s"Alice ${APIUtil.generateUUID()}")))
      val second = resolve(idToken(clientId, googleIssuer, sub = "user-two", name = Some(s"Bob ${APIUtil.generateUUID()}")))
      Then("both resolve to the same consumer and no duplicate is created")
      second.consumerId should equal(first.consumerId)
      Consumer.findAllByAzp(clientId).size should equal(1)
      And("the sub claim is stored from the first login but does not key the lookup")
      second.sub should equal("user-one")
    }

    Scenario("the same client ID under a different issuer resolves to a different consumer") {
      val clientId = freshClientId()
      When("the same client ID is presented by two different issuers")
      val googleConsumer = resolve(idToken(clientId, googleIssuer, sub = "user-one"))
      val otherConsumer = resolve(idToken(clientId, "https://keycloak.example.com/realms/obp", sub = "user-two"))
      Then("each issuer gets its own consumer for that client ID")
      otherConsumer.consumerId should not equal googleConsumer.consumerId
      Consumer.findAllByAzp(clientId).size should equal(2)
    }
  }

  Feature("auto-created consumer metadata") {

    Scenario("the consumer is named from the token's name claim, falling back to the description") {
      val namedUser = s"Alice ${APIUtil.generateUUID()}"
      When("the token carries a name claim")
      val named = resolve(idToken(freshClientId(), googleIssuer, sub = "user-one", name = Some(namedUser)))
      Then("the consumer is named after the first user who logged in with that client")
      named.name should equal(namedUser)
      When("the token carries no name claim")
      val unnamed = resolve(idToken(freshClientId(), googleIssuer, sub = "user-one"))
      Then("the consumer name falls back to the description")
      unnamed.name should startWith("OpenID Connect")
    }

    Scenario("the consumerId is derived from the client ID") {
      Given("a google-style (non-UUID) client ID")
      val clientId = freshClientId()
      resolve(idToken(clientId, googleIssuer, sub = "user-one")).consumerId should startWith(s"${clientId}_")
      Given("a UUID client ID")
      val uuidClientId = APIUtil.generateUUID()
      resolve(idToken(uuidClientId, googleIssuer, sub = "user-one")).consumerId should equal(uuidClientId)
    }
  }

  Feature("a pre-registered consumer whose key is the OAuth2 client ID takes priority") {

    Scenario("the token resolves to the pre-registered consumer instead of auto-creating one") {
      val clientId = freshClientId()
      Given("an operator pre-registered a consumer with key = the Google client ID")
      val registered = Consumers.consumers.vend.createConsumer(
        key = Some(clientId), secret = Some(APIUtil.generateUUID()), isActive = Some(true),
        name = Some(s"API Explorer ${APIUtil.generateUUID()}"), appType = None,
        description = Some("pre-registered"), developerEmail = Some("operator@example.com"), redirectURL = None,
        createdByUserId = None, clientCertificate = None, company = None, logoURL = None
      ).openOrThrowException("test consumer must be created")
      When("a token minted for that client ID arrives")
      val resolved = resolve(idToken(clientId, googleIssuer, sub = "user-one"))
      Then("the pre-registered consumer is used and its azp/iss are populated")
      resolved.consumerId should equal(registered.consumerId)
      resolved.azp should equal(clientId)
      resolved.iss should equal(googleIssuer)
      Consumer.findAllByAzp(clientId).size should equal(1)
    }

    Scenario("a stale auto-created consumer is displaced by the pre-registered one") {
      val clientId = freshClientId()
      Given("a consumer was auto-created before the operator registered the client ID")
      val stale = resolve(idToken(clientId, googleIssuer, sub = "user-one"))
      val registered = Consumers.consumers.vend.createConsumer(
        key = Some(clientId), secret = Some(APIUtil.generateUUID()), isActive = Some(true),
        name = Some(s"API Manager ${APIUtil.generateUUID()}"), appType = None,
        description = Some("pre-registered"), developerEmail = Some("operator@example.com"), redirectURL = None,
        createdByUserId = None, clientCertificate = None, company = None, logoURL = None
      ).openOrThrowException("test consumer must be created")
      When("the next token for that client ID arrives")
      val resolved = resolve(idToken(clientId, googleIssuer, sub = "user-two"))
      Then("it resolves to the pre-registered consumer, not the stale auto-created one")
      resolved.consumerId should equal(registered.consumerId)
      And("the stale consumer no longer holds the (azp, iss) pair")
      val staleReloaded = Consumer.findByConsumerId(stale.consumerId)
        .openOrThrowException("stale consumer must still exist")
      staleReloaded.azp should not equal clientId
      Consumer.findAllByAzp(clientId).size should equal(1)
    }
  }
}
