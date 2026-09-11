package code.api

import java.nio.charset.StandardCharsets
import java.time.Instant

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.util.APIUtil.HTTPParam
import org.http4s.{Method, Request, Uri}
import org.scalatest.GivenWhenThen
import org.web3j.crypto.{Keys, Sign}
import org.web3j.utils.Numeric
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers

/**
 * Pure unit tests for the SIWE (Sign-In With Ethereum, EIP-4361) auth method.
 *
 * These exercise the genuinely-new logic — message build/parse, EOA `ecrecover`,
 * and header parsing — with NO running server, DB, or Redis. The signature is
 * produced in-test with a freshly generated web3j keypair, then recovered, so the
 * crypto path is verified end-to-end without any external dependency (Phase 1: EOA only).
 */
class SIWETest extends AnyFeatureSpec with Matchers with GivenWhenThen {

  // Sign an EIP-4361 message exactly the way a wallet would (EIP-191 personal_sign),
  // returning the 0x-prefixed 65-byte signature hex.
  private def signMessage(message: String, keyPair: org.web3j.crypto.ECKeyPair): String = {
    val sig = Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), keyPair)
    Numeric.toHexString(sig.getR ++ sig.getS ++ sig.getV)
  }

  Feature("EIP-4361 message build + parse") {

    Scenario("a built message round-trips through parseMessage") {
      Given("a message built by SIWE.buildMessage")
      val address = Keys.toChecksumAddress("0x" + "a" * 40)
      val issuedAt = Instant.parse("2026-06-24T10:00:00Z")
      val expiresAt = issuedAt.plusSeconds(300)
      val message = SIWE.buildMessage(
        domain = "example.com",
        checksumAddress = address,
        chainId = 11155111L,
        nonce = "abc123XYZ",
        uri = "https://example.com",
        statement = "Sign in to OBP.",
        issuedAt = issuedAt.toString,
        expirationTime = expiresAt.toString)

      When("it is parsed")
      val parsed = SIWE.parseMessage(message).openOrThrowException("should parse")

      Then("every field is recovered")
      parsed.domain should equal("example.com")
      parsed.address should equal(address)
      parsed.chainId should equal(11155111L)
      parsed.nonce should equal("abc123XYZ")
      parsed.uri should equal(Some("https://example.com"))
      parsed.issuedAt should equal(Some(issuedAt.toString))
      parsed.expirationTime should equal(Some(expiresAt.toString))
    }

    Scenario("a message with no address line fails to parse") {
      val garbage = "this is not a SIWE message"
      SIWE.parseMessage(garbage).isDefined should equal(false)
    }
  }

  Feature("EOA signature recovery (ecrecover)") {

    Scenario("recovers the exact signer of a built message") {
      Given("a fresh keypair and a message signed by it")
      val keyPair = Keys.createEcKeyPair()
      val address = Keys.toChecksumAddress(Keys.getAddress(keyPair))
      val message = SIWE.buildMessage(
        "example.com", address, 1L, "nonce0001",
        "https://example.com", "Sign in.",
        Instant.parse("2026-06-24T10:00:00Z").toString,
        Instant.parse("2026-06-24T10:05:00Z").toString)
      val signature = signMessage(message, keyPair)

      When("the signature is recovered")
      val recovered = SIWE.recoverEoaAddress(message, signature).openOrThrowException("should recover")

      Then("it equals the signer's checksummed address")
      recovered.equalsIgnoreCase(address) should equal(true)
    }

    Scenario("a signature over a different message does not recover the claimed address") {
      val keyPair = Keys.createEcKeyPair()
      val address = Keys.toChecksumAddress(Keys.getAddress(keyPair))
      val signed = SIWE.buildMessage("example.com", address, 1L, "n1", "https://example.com", "a",
        "2026-06-24T10:00:00Z", "2026-06-24T10:05:00Z")
      val tampered = signed.replace("n1", "n2") // change the nonce after signing
      val signature = signMessage(signed, keyPair)

      val recovered = SIWE.recoverEoaAddress(tampered, signature).openOrThrowException("recovers some addr")
      recovered.equalsIgnoreCase(address) should equal(false)
    }

    Scenario("a malformed signature yields a Failure, not an exception") {
      SIWE.recoverEoaAddress("any message", "0xdeadbeef").isDefined should equal(false)
    }
  }

  Feature("address + expiry helpers") {

    Scenario("isValidEthAddress") {
      SIWE.isValidEthAddress("0x" + "A" * 40) should equal(true)
      SIWE.isValidEthAddress("0x" + "A" * 39) should equal(false)
      SIWE.isValidEthAddress("nope") should equal(false)
    }

    Scenario("isExpired is false for None and future, true for past") {
      SIWE.isExpired(None) should equal(false)
      SIWE.isExpired(Some(Instant.now().plusSeconds(60).toString)) should equal(false)
      SIWE.isExpired(Some(Instant.now().minusSeconds(60).toString)) should equal(true)
    }
  }

  Feature("SIWE header parsing (subsequent requests)") {

    Scenario("hasSiweHeader + getSiweToken extract token=...") {
      val headers = List(HTTPParam("SIWE", List("token=abc.def.ghi")))
      SIWE.hasSiweHeader(headers) should equal(true)
      SIWE.getSiweToken(headers) should equal(Some("abc.def.ghi"))
    }

    Scenario("quoted token value is unquoted") {
      val headers = List(HTTPParam("SIWE", List("""token="abc.def.ghi"""")))
      SIWE.getSiweToken(headers) should equal(Some("abc.def.ghi"))
    }

    Scenario("no SIWE header → None") {
      val headers = List(HTTPParam("DirectLogin", List("token=xyz")))
      SIWE.hasSiweHeader(headers) should equal(false)
      SIWE.getSiweToken(headers) should equal(None)
    }
  }

  Feature("feature is OFF by default") {

    Scenario("with allow_siwe unset, the routes do not match (HttpRoutes.empty)") {
      Given("the default test environment has no allow_siwe prop")
      SIWE.isEnabled should equal(false)

      When("a POST /my/logins/siwe/challenge is dispatched to the route")
      val req = Request[IO](method = Method.POST, uri = Uri.unsafeFromString("/my/logins/siwe/challenge"))
      val matched = SIWERoutes.routes.run(req).value.unsafeRunSync()

      Then("nothing matches — the feature is inert until allow_siwe=true")
      matched should equal(None)
    }
  }
}
