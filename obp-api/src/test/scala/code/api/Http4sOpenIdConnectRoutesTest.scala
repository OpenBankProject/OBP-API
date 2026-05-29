package code.api

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.util.ErrorMessages
import code.setup.ServerSetup
import org.http4s.{Method, Request, Uri}

/**
 * Pure route test for the native http4s OpenID Connect callback
 * (`Http4sOpenIdConnect`). No live provider, no TCP, no DB — drives the routes
 * in-process and flips the gating Props via [[PropsReset]].
 *
 * Pins the gates and path matching that the OBP-OIDC / Keycloak integration
 * depends on:
 *   - `openid_connect.enabled=false` (default) → the callback paths do not match
 *     and fall through (None), exactly as before the migration.
 *   - `openid_connect.enabled=true` → the three callback paths match GET and POST.
 *   - `allow_openid_connect=false` → 401 OpenIDConnectIsDisabled.
 *   - the session-state gate and the token-exchange failure both surface as 401.
 *
 * The success path (200 {token}) needs a real provider to mint the OIDC tokens,
 * so it is covered by the manual end-to-end verification, not here.
 */
class Http4sOpenIdConnectRoutesTest extends ServerSetup {

  private def run(req: Request[IO]): Option[(Int, String)] =
    Http4sOpenIdConnect.routes.run(req).value.unsafeRunSync().map { resp =>
      val body = new String(resp.body.compile.to(Array).unsafeRunSync(), "UTF-8")
      (resp.status.code, body)
    }

  private def get(path: String): Request[IO]  = Request[IO](Method.GET,  Uri.unsafeFromString(path))
  private def post(path: String): Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString(path))

  feature("OpenID Connect callback gating (openid_connect.enabled)") {

    scenario("Disabled by default — callback paths fall through (None)") {
      Given("openid_connect.enabled is not set (default false)")
      When("the three callback paths are invoked with GET and POST")
      Then("none match — request falls through to the next route (ultimately notFoundCatchAll / JSON 404)")
      run(get("/auth/openid-connect/callback"))    shouldBe None
      run(post("/auth/openid-connect/callback"))   shouldBe None
      run(get("/auth/openid-connect/callback-1"))  shouldBe None
      run(post("/auth/openid-connect/callback-2")) shouldBe None
    }

    scenario("Enabled — the three callback paths match GET and POST") {
      Given("openid_connect.enabled=true and the session-state check disabled (no portal)")
      setPropsValues(
        "openid_connect.enabled" -> "true",
        "openid_connect.check_session_state" -> "false"
      )
      When("the three callback paths are invoked with GET and POST (no provider configured)")
      Then("each matches and yields a 401 token-exchange failure (not None)")
      // No provider props → exchangeAuthorizationCodeForTokens fails → 401 CouldNotExchange...
      List(
        get("/auth/openid-connect/callback"),
        post("/auth/openid-connect/callback"),
        get("/auth/openid-connect/callback-1"),
        post("/auth/openid-connect/callback-1"),
        get("/auth/openid-connect/callback-2"),
        post("/auth/openid-connect/callback-2")
      ).foreach { req =>
        val (code, body) = run(req).getOrElse(fail(s"route did not match ${req.method} ${req.uri}"))
        code shouldBe 401
        body should include(ErrorMessages.CouldNotExchangeAuthorizationCodeForTokens)
      }
    }

    scenario("Enabled but allow_openid_connect=false → 401 OpenIDConnectIsDisabled") {
      Given("openid_connect.enabled=true but allow_openid_connect=false")
      setPropsValues(
        "openid_connect.enabled" -> "true",
        "allow_openid_connect" -> "false"
      )
      When("the callback is invoked")
      val (code, body) = run(post("/auth/openid-connect/callback"))
        .getOrElse(fail("route did not match"))
      Then("it returns 401 OpenIDConnectIsDisabled before any token exchange")
      code shouldBe 401
      body should include(ErrorMessages.OpenIDConnectIsDisabled)
    }

    scenario("Enabled, default session-state check, non-matching state → 401 InvalidOpenIDConnectState") {
      Given("openid_connect.enabled=true with the session-state check left at its default (true)")
      setPropsValues("openid_connect.enabled" -> "true")
      When("a callback arrives whose state does not equal the (empty) session state")
      val (code, body) = run(get("/auth/openid-connect/callback?code=abc&state=non-empty"))
        .getOrElse(fail("route did not match"))
      Then("it returns 401 InvalidOpenIDConnectState before any token exchange")
      code shouldBe 401
      body should include(ErrorMessages.InvalidOpenIDConnectState)
    }

    scenario("Enabled — an unrelated /auth/openid-connect path does not match") {
      Given("openid_connect.enabled=true")
      setPropsValues("openid_connect.enabled" -> "true")
      When("a path that is not one of the three callbacks is invoked")
      Then("the route does not match")
      run(get("/auth/openid-connect/callback-3")) shouldBe None
      run(get("/auth/openid-connect/other"))      shouldBe None
    }
  }
}
