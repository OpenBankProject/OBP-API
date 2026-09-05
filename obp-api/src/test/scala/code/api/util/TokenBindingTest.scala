package code.api.util

import java.security.cert.X509Certificate

import code.api.util.ErrorMessages.{Oauth2TokenBindingCertificateMismatch, Oauth2TokenBindingCertificateMissing, Oauth2TokenBindingRequired}
import code.api.util.SelfSignedCertificateUtil.generateSelfSignedCert
import code.api.util.TokenBinding.Mode
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import net.liftweb.common.{Failure, Full}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Pure tests for the FAPI / RFC 8705 sender-constrained token decision: no server, no props,
 * no TLS handshake — the mode and both inputs are passed explicitly, exactly so that every
 * row of the decision table can be exercised here.
 */
class TokenBindingTest extends AnyFlatSpec with Matchers {

  private def certFor(cn: String): X509Certificate =
    generateSelfSignedCert(cn)._2.asInstanceOf[X509Certificate]

  private val boundCert = certFor("bound-tpp-client")
  private val otherCert = certFor("some-other-client")
  private val boundThumbprint = TokenBinding.x5tS256(boundCert)

  private val hmacSecret = "0123456789abcdef0123456789abcdef" // 32 bytes for HS256

  private def signedJwt(claims: JWTClaimsSet): String = {
    val jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims)
    jwt.sign(new MACSigner(hmacSecret))
    jwt.serialize()
  }

  private def tokenWithCnf(thumbprint: String): String = {
    val cnf = new java.util.HashMap[String, Object]()
    cnf.put("x5t#S256", thumbprint)
    signedJwt(new JWTClaimsSet.Builder().subject("tpp-app").claim("cnf", cnf).build())
  }

  private val tokenWithoutCnf: String =
    signedJwt(new JWTClaimsSet.Builder().subject("plain-app").build())

  behavior of "x5tS256"

  it should "produce a 43-character base64url thumbprint without padding" in {
    boundThumbprint should have length 43
    boundThumbprint should not include "="
    boundThumbprint should not include "+"
    boundThumbprint should not include "/"
  }

  it should "be stable for the same certificate and differ between certificates" in {
    TokenBinding.x5tS256(boundCert) shouldBe boundThumbprint
    TokenBinding.x5tS256(otherCert) should not be boundThumbprint
  }

  behavior of "cnfX5tS256"

  it should "extract the thumbprint from a token carrying cnf.x5t#S256" in {
    TokenBinding.cnfX5tS256(tokenWithCnf(boundThumbprint)) shouldBe Some(boundThumbprint)
  }

  it should "return None for a token without a cnf claim" in {
    TokenBinding.cnfX5tS256(tokenWithoutCnf) shouldBe None
  }

  it should "return None for a string that is not a JWT" in {
    TokenBinding.cnfX5tS256("not-a-jwt") shouldBe None
  }

  behavior of "verify in NONE mode"

  it should "pass everything, even a mismatched bound token" in {
    TokenBinding.verify(Mode.NONE, Some(boundThumbprint), Some(otherCert), "test") shouldBe Full(())
    TokenBinding.verify(Mode.NONE, None, None, "test") shouldBe Full(())
  }

  behavior of "verify in MONITOR mode"

  it should "pass an unbound token" in {
    TokenBinding.verify(Mode.MONITOR, None, None, "test") shouldBe Full(())
  }

  it should "pass a matching bound token" in {
    TokenBinding.verify(Mode.MONITOR, Some(boundThumbprint), Some(boundCert), "test") shouldBe Full(())
  }

  it should "pass (but only log) a mismatched bound token" in {
    TokenBinding.verify(Mode.MONITOR, Some(boundThumbprint), Some(otherCert), "test") shouldBe Full(())
  }

  it should "pass (but only log) a bound token with no certificate presented" in {
    TokenBinding.verify(Mode.MONITOR, Some(boundThumbprint), None, "test") shouldBe Full(())
  }

  behavior of "verify in ENFORCE mode"

  it should "pass an unbound token" in {
    TokenBinding.verify(Mode.ENFORCE, None, None, "test") shouldBe Full(())
  }

  it should "pass a matching bound token" in {
    TokenBinding.verify(Mode.ENFORCE, Some(boundThumbprint), Some(boundCert), "test") shouldBe Full(())
  }

  it should "reject a mismatched bound token" in {
    TokenBinding.verify(Mode.ENFORCE, Some(boundThumbprint), Some(otherCert), "test") match {
      case Failure(msg, _, _) => msg should include(Oauth2TokenBindingCertificateMismatch)
      case other => fail(s"Expected Failure, got $other")
    }
  }

  it should "reject a bound token with no certificate presented" in {
    TokenBinding.verify(Mode.ENFORCE, Some(boundThumbprint), None, "test") match {
      case Failure(msg, _, _) => msg should include(Oauth2TokenBindingCertificateMissing)
      case other => fail(s"Expected Failure, got $other")
    }
  }

  behavior of "verify in REQUIRED mode"

  it should "reject an unbound token" in {
    TokenBinding.verify(Mode.REQUIRED, None, Some(boundCert), "test") match {
      case Failure(msg, _, _) => msg should include(Oauth2TokenBindingRequired)
      case other => fail(s"Expected Failure, got $other")
    }
  }

  it should "pass a matching bound token" in {
    TokenBinding.verify(Mode.REQUIRED, Some(boundThumbprint), Some(boundCert), "test") shouldBe Full(())
  }

  it should "reject a mismatched bound token" in {
    TokenBinding.verify(Mode.REQUIRED, Some(boundThumbprint), Some(otherCert), "test") match {
      case Failure(msg, _, _) => msg should include(Oauth2TokenBindingCertificateMismatch)
      case other => fail(s"Expected Failure, got $other")
    }
  }

  it should "reject a bound token with no certificate presented" in {
    TokenBinding.verify(Mode.REQUIRED, Some(boundThumbprint), None, "test") match {
      case Failure(msg, _, _) => msg should include(Oauth2TokenBindingCertificateMissing)
      case other => fail(s"Expected Failure, got $other")
    }
  }

  behavior of "configuredMode"

  it should "default to NONE when the prop is not set" in {
    TokenBinding.configuredMode shouldBe Mode.NONE
  }
}
