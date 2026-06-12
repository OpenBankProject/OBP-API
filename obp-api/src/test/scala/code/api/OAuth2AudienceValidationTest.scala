package code.api

import code.api.util.ErrorMessages
import code.setup.PropsReset
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import net.liftweb.common.{Failure, Full}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

import java.net.URI
import scala.collection.JavaConverters._

class OAuth2AudienceValidationTest extends FeatureSpec with Matchers with GivenWhenThen with PropsReset {

  // Lift's Props.lockedProviders is a lazy val; PropsReset.beforeAll reads its backing
  // field via reflection, which is null until first forced. Suites with ServerSetup
  // force it during boot — this pure-unit suite must force it itself.
  net.liftweb.util.Props.get("force.lazy.lockedProviders.init")

  private val audiencePropsName = "oauth2.google.allowed_audiences"
  private val oidcProviderPropsName = "oauth2.oidc_provider"
  private val ourClientId = "883773244832-ours.apps.googleusercontent.com"
  private val foreignClientId = "407408718192-foreign.apps.googleusercontent.com"

  // Same shape as OAuth2Login.Google: an OAuth2Util with an audience allowlist props.
  // validateAudience is protected, so expose it through a subclass.
  private object restrictedProvider extends OAuth2Login.OAuth2Util {
    override def wellKnownOpenidConfiguration: URI = new URI("https://accounts.google.com/.well-known/openid-configuration")
    override protected def allowedAudiencesPropsName: Option[String] = Some(audiencePropsName)
    def validateAudiencePublic(token: String) = validateAudience(token)
  }

  // A provider that declares no allowlist props (the trait default) must never restrict.
  private object unrestrictedProvider extends OAuth2Login.OAuth2Util {
    override def wellKnownOpenidConfiguration: URI = new URI("")
    def validateAudiencePublic(token: String) = validateAudience(token)
    def validateProviderEnabledPublic = validateProviderEnabled
  }

  // A public provider subject to oauth2.oidc_provider enablement (like Google/Yahoo/Azure).
  private object enablementProvider extends OAuth2Login.OAuth2Util {
    override def wellKnownOpenidConfiguration: URI = new URI("https://accounts.google.com/.well-known/openid-configuration")
    override protected def oidcProviderName: Option[String] = Some("google")
    def validateProviderEnabledPublic = validateProviderEnabled
  }

  // validateAudience only parses claims (no signature verification), so an
  // HMAC-signed token is enough to exercise it.
  private def jwtWithAud(aud: List[String]): String = {
    val builder = new JWTClaimsSet.Builder().issuer("https://accounts.google.com").subject("113966854245780892959")
    val claims = (if (aud.isEmpty) builder else builder.audience(aud.asJava)).build()
    val jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims)
    jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef"))
    jwt.serialize()
  }

  feature("audience allowlist disabled (backward compatibility)") {
    scenario("props not set: any audience is accepted") {
      restrictedProvider.validateAudiencePublic(jwtWithAud(List(foreignClientId))) should be(Full(()))
    }
    scenario("props set to empty string: any audience is accepted") {
      setPropsValues(audiencePropsName -> "")
      restrictedProvider.validateAudiencePublic(jwtWithAud(List(foreignClientId))) should be(Full(()))
    }
    scenario("provider without an allowlist props ignores the props entirely") {
      setPropsValues(audiencePropsName -> ourClientId)
      unrestrictedProvider.validateAudiencePublic(jwtWithAud(List(foreignClientId))) should be(Full(()))
    }
  }

  feature("audience allowlist enforced") {
    scenario("aud matches the single allowed value") {
      setPropsValues(audiencePropsName -> ourClientId)
      restrictedProvider.validateAudiencePublic(jwtWithAud(List(ourClientId))) should be(Full(()))
    }
    scenario("multi-valued aud is accepted when any value matches") {
      setPropsValues(audiencePropsName -> ourClientId)
      restrictedProvider.validateAudiencePublic(jwtWithAud(List(foreignClientId, ourClientId))) should be(Full(()))
    }
    scenario("allowlist entries are trimmed") {
      setPropsValues(audiencePropsName -> s" $foreignClientId , $ourClientId ")
      restrictedProvider.validateAudiencePublic(jwtWithAud(List(ourClientId))) should be(Full(()))
    }
    scenario("aud not in the allowlist is rejected") {
      setPropsValues(audiencePropsName -> ourClientId)
      restrictedProvider.validateAudiencePublic(jwtWithAud(List(foreignClientId))) should be(Failure(ErrorMessages.Oauth2TokenAudienceNotAllowed))
    }
    scenario("missing aud claim is rejected") {
      setPropsValues(audiencePropsName -> ourClientId)
      restrictedProvider.validateAudiencePublic(jwtWithAud(Nil)) should be(Failure(ErrorMessages.Oauth2TokenAudienceNotAllowed))
    }
    scenario("matching is case-sensitive (client IDs are case-sensitive)") {
      setPropsValues(audiencePropsName -> ourClientId)
      restrictedProvider.validateAudiencePublic(jwtWithAud(List(ourClientId.toUpperCase))) should be(Failure(ErrorMessages.Oauth2TokenAudienceNotAllowed))
    }
  }

  feature("provider enablement via oauth2.oidc_provider") {
    scenario("props not set: provider is enabled (backward compatibility)") {
      enablementProvider.validateProviderEnabledPublic should be(Full(()))
    }
    scenario("props set to empty string: all providers are enabled") {
      setPropsValues(oidcProviderPropsName -> "")
      enablementProvider.validateProviderEnabledPublic should be(Full(()))
    }
    scenario("provider listed: enabled") {
      setPropsValues(oidcProviderPropsName -> "obp-oidc,keycloak,google")
      enablementProvider.validateProviderEnabledPublic should be(Full(()))
    }
    scenario("entries are trimmed and matched case-insensitively") {
      setPropsValues(oidcProviderPropsName -> " obp-oidc , Google ")
      enablementProvider.validateProviderEnabledPublic should be(Full(()))
    }
    scenario("provider not listed: rejected") {
      setPropsValues(oidcProviderPropsName -> "obp-oidc,keycloak")
      enablementProvider.validateProviderEnabledPublic should be(Failure(ErrorMessages.Oauth2ProviderNotEnabled))
    }
    scenario("props set to none: public providers are rejected") {
      setPropsValues(oidcProviderPropsName -> "none")
      enablementProvider.validateProviderEnabledPublic should be(Failure(ErrorMessages.Oauth2ProviderNotEnabled))
    }
    scenario("provider without an oidc provider name is never subject to enforcement") {
      setPropsValues(oidcProviderPropsName -> "obp-oidc")
      unrestrictedProvider.validateProviderEnabledPublic should be(Full(()))
    }
  }
}
