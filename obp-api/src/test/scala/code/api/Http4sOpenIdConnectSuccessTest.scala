package code.api

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.setup.ServerSetup
import code.users.Users
import com.comcast.ip4s._
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.{HttpRoutes, Method, Request, Uri}

import java.util.Date

/**
 * Success-path integration test for [[Http4sOpenIdConnect]] — the `200 {"token": ...}`
 * branch that the routing/failure suite ([[Http4sOpenIdConnectRoutesTest]]) cannot reach
 * because it needs a provider to mint the OIDC tokens.
 *
 * Self-contained, no live provider: stands up a local stub OIDC provider (Ember) that
 * serves
 *   - `POST /token` → a token response whose `id_token` is a locally-signed RS256 JWT
 *   - `GET  /jwks`  → the matching public JWK set
 * points the `openid_connect_1.*` props at it, then drives the callback route in-process.
 * It asserts the handler exchanges the code, validates the JWT against the JWKS,
 * provisions the resource user, and returns `200 {"token": ...}`.
 *
 * Why a freshly-signed token is enough: `JwtUtil.validateIdToken` reads `iss`/`aud` from
 * the token itself and only enforces the signature (against the served JWKS) and expiry,
 * so no configured iss/aud matching is required. The JWS header `kid` matches the served
 * JWK so the verification key selector picks the right key.
 *
 * This also exercises the M1 change — the provisioning block is now wrapped in
 * `DB.use(DefaultConnectionIdentifier)` (one connection for all OIDC writes).
 */
class Http4sOpenIdConnectSuccessTest extends ServerSetup {

  private val providerClaim = "http://127.0.0.1/oidc-test-provider"
  private val preferredUser = "oidctestuser"
  private val clientId      = "obp-oidc-test-client"

  // RSA keypair used to sign the id_token; its public half is served at /jwks.
  private val rsaJwk = new RSAKeyGenerator(2048).keyID("oidc-test-kid").generate()

  private def signedIdToken(issuer: String): String = {
    val claims = new JWTClaimsSet.Builder()
      .issuer(issuer)
      .subject("oidc-test-subject")
      .audience(clientId)
      .expirationTime(new Date(System.currentTimeMillis() + 3600L * 1000))
      .issueTime(new Date())
      .claim("preferred_username", preferredUser)
      .claim("email", "oidctest@example.com")
      .claim("provider", providerClaim)
      .claim("azp", clientId)
      .build()
    val jwt = new SignedJWT(
      new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID).build(),
      claims
    )
    jwt.sign(new RSASSASigner(rsaJwk))
    jwt.serialize()
  }

  /** Allocate an ephemeral free port for the stub provider. */
  private def freePort(): Int = {
    val socket = new java.net.ServerSocket(0)
    try socket.getLocalPort finally socket.close()
  }

  private def stubProvider(issuer: String): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case POST -> Root / "token" =>
      Ok(s"""{"id_token":"${signedIdToken(issuer)}","access_token":"access-xyz",""" +
         s""""token_type":"Bearer","expires_in":"3600","refresh_token":"refresh-xyz","scope":"openid"}""")
    case GET -> Root / "jwks" =>
      Ok(new JWKSet(rsaJwk.toPublicJWK).toString)
  }

  private def run(req: Request[IO]): (Int, String) =
    Http4sOpenIdConnect.routes.run(req).value.unsafeRunSync().map { resp =>
      (resp.status.code, new String(resp.body.compile.to(Array).unsafeRunSync(), "UTF-8"))
    }.getOrElse(fail(s"route did not match ${req.method} ${req.uri}"))

  feature("OpenID Connect callback — success path") {

    scenario("valid code + signed id_token → 200 {token} and the user is provisioned") {
      val port    = freePort()
      val portObj = Port.fromInt(port).getOrElse(fail(s"invalid free port $port"))
      val issuer  = s"http://127.0.0.1:$port"

      val server = EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(portObj)
        .withHttpApp(stubProvider(issuer).orNotFound)
        .build

      server.use { _ =>
        IO {
          Given("a local stub OIDC provider and openid_connect_1.* pointed at it")
          setPropsValues(
            "openid_connect.enabled" -> "true",
            "openid_connect.check_session_state" -> "false",
            "allow_openid_connect" -> "true",
            "openid_connect_1.client_id" -> clientId,
            "openid_connect_1.client_secret" -> "test-secret",
            "openid_connect_1.callback_url" -> "http://localhost/auth/openid-connect/callback",
            "openid_connect_1.endpoint.token" -> s"$issuer/token",
            "openid_connect_1.endpoint.jwks_uri" -> s"$issuer/jwks"
          )

          When("the provider redirects back to the callback with an authorization code")
          val (code, body) = run(
            Request[IO](Method.GET,
              Uri.unsafeFromString("/auth/openid-connect/callback?code=auth-code-123&state=ignored"))
          )

          Then("the handler returns 200 with a minted OBP DirectLogin token")
          code shouldBe 200
          body should include("token")

          And("the resource user was provisioned from the validated claims")
          Users.users.vend.getUserByProviderId(providerClaim, preferredUser).isDefined shouldBe true
        }
      }.unsafeRunSync()
    }
  }
}
