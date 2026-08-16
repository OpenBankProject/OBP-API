package code.api.util.http4s

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import code.api.v4_0_0.Http4s400
import code.api.v7_0_0.Http4s700
import code.setup.ServerSetup
import fs2.Stream
import org.http4s.{Headers, Method, Request, Uri}
import org.scalatest.{GivenWhenThen, Tag}

/**
 * Integration test for the enable/disable Props wiring inside `ResourceDocMiddleware`.
 *
 * Drives `Http4s700.wrappedRoutesV700Services` in-process — no TCP, no DB. Verifies that
 * the endpoint-level Props (`api_disabled_endpoints`, `api_enabled_endpoints`) actually
 * change routing behaviour at request time, AND pins the deliberate non-enforcement of
 * the version-level Props (`api_disabled_versions`, `api_enabled_versions`) at the
 * middleware layer.
 *
 * Why version Props are NOT enforced here:
 *   They are enforced once at startup by `Http4sApp.gate` for the URL prefix the
 *   request arrives at. The middleware deliberately does not re-check
 *   `implementedInApiVersion` per request, so that disabling vX retires
 *   `/obp/vX/...` but leaves vX's endpoints reachable via newer enabled prefixes
 *   through the cascade. See `ResourceDocMiddleware.isEndpointEnabled` for the
 *   rationale. Because this test drives `Http4s700.wrappedRoutesV700Services`
 *   directly (bypassing `Http4sApp.gate`), `api_disabled_versions=[v7.0.0]` does
 *   not turn `/obp/v7.0.0/...` into 404s — and that is the contract this file pins.
 *
 * Why a separate test class from `ResourceDocMiddlewareEnableDisableTest`:
 *   That test pins the pure decision logic (`isEndpointEnabled`). This one pins the
 *   wiring — that the middleware actually reads the Props on each request and
 *   short-circuits to `OptionT.none` when the decision says disabled. With the routes
 *   driven via `.orNotFound`, a short-circuited request surfaces as 404.
 *
 * Why this works despite the Props being read inside the Kleisli:
 *   `PropsReset.setPropsValues` writes to Lift's locked-providers list at runtime. The
 *   middleware reads `APIUtil.getDisabledEndpointOperationIds()` etc. on every request,
 *   so changes made by `setPropsValues` in `beforeEach` are visible to the next request.
 *   `PropsReset.afterEach` restores the original providers so tests don't leak Props.
 *
 * The endpoint we use is `GET /obp/v7.0.0/root` — no auth, no DB, returns 200 on the
 * happy path. This isolates routing from every other concern.
 */
class ResourceDocMiddlewareEnableDisablePropsTest extends ServerSetup with GivenWhenThen {

  object EnableDisablePropsTag extends Tag("EnableDisableProps")

  implicit val runtime: IORuntime = IORuntime.global
  private val app = Http4s700.wrappedRoutesV700Services.orNotFound

  // A second app rooted at v4.0.0's wrapped routes — includes the v400ToV310Bridge.
  // Used by the cascade scenarios below to prove that a v3.1.0-origin endpoint
  // reached via `/obp/v4.0.0/...` still serves when v3.1.0 is in
  // `api_disabled_versions`. The middleware running inside Http4s310 no longer
  // enforces the version Prop, so the cascade is unaffected.
  private val v4App = Http4s400.wrappedRoutesV400Services.orNotFound

  // OperationIds match `APIUtil.buildOperationId(v, partialFunctionName)` →
  // s"$fullyQualifiedVersion-$name". v7.0.0's fully qualified form is "OBPv7.0.0".
  private val rootOpId    = "OBPv7.0.0-root"
  // A second NATIVE v7 endpoint. getScannedApiVersions was removed from v7 (it now
  // cascades to v6), so it can no longer serve as a "native v7" allowlist target.
  // getErrorMessages is public, native to v7, and needs no DB.
  private val versionsOpId = "OBPv7.0.0-getErrorMessages"

  private val rootPath     = "/obp/v7.0.0/root"
  private val versionsPath = "/obp/v7.0.0/api/error-messages"

  private def get(path: String): Int = {
    val req = Request[IO](Method.GET, Uri.unsafeFromString(path), headers = Headers.empty,
                          body = Stream.empty)
    app.run(req).unsafeRunSync().status.code
  }

  private def getV4(path: String): Int = {
    val req = Request[IO](Method.GET, Uri.unsafeFromString(path), headers = Headers.empty,
                          body = Stream.empty)
    v4App.run(req).unsafeRunSync().status.code
  }

  Feature("ResourceDocMiddleware — Props wiring at request time") {

    Scenario("Baseline: no Props set → /root returns 200", EnableDisablePropsTag) {
      Given("no enable/disable Props are set")
      When("requesting GET /obp/v7.0.0/root")
      val status = get(rootPath)
      Then("the endpoint serves normally")
      status shouldBe 200
    }

    Scenario("api_disabled_endpoints contains the operationId → 404", EnableDisablePropsTag) {
      Given(s"api_disabled_endpoints=[$rootOpId]")
      setPropsValues("api_disabled_endpoints" -> s"[$rootOpId]")

      When("requesting GET /obp/v7.0.0/root")
      val status = get(rootPath)

      Then("the middleware short-circuits to OptionT.none → 404 via orNotFound")
      status shouldBe 404

      And("other endpoints in the same version are unaffected")
      get(versionsPath) shouldBe 200
    }

    Scenario("api_enabled_endpoints contains a different operationId → 404 for non-listed", EnableDisablePropsTag) {
      Given(s"api_enabled_endpoints=[$versionsOpId] (root is NOT listed)")
      setPropsValues("api_enabled_endpoints" -> s"[$versionsOpId]")

      When("requesting GET /obp/v7.0.0/root")
      val rootStatus = get(rootPath)

      Then("the middleware short-circuits to 404 — allowlist excludes root")
      rootStatus shouldBe 404

      And("the explicitly enabled endpoint still serves")
      get(versionsPath) shouldBe 200
    }

    Scenario("api_enabled_endpoints contains the operationId → endpoint serves", EnableDisablePropsTag) {
      Given(s"api_enabled_endpoints=[$rootOpId]")
      setPropsValues("api_enabled_endpoints" -> s"[$rootOpId]")

      When("requesting GET /obp/v7.0.0/root")
      val status = get(rootPath)

      Then("the endpoint serves normally")
      status shouldBe 200
    }

    Scenario("api_disabled_versions is NOT enforced by the middleware — cascade-friendly", EnableDisablePropsTag) {
      Given("api_disabled_versions=[v7.0.0] (would historically have killed every v7 endpoint)")
      setPropsValues("api_disabled_versions" -> "[v7.0.0]")

      When("requesting two unrelated v7 endpoints directly against Http4s700's wrapped routes")
      val rootStatus = get(rootPath)
      val banksStatus = get(versionsPath)

      Then("both still serve — the middleware deliberately ignores version-level Props")
      And("(the prefix-level gate lives in Http4sApp.gate, which this test bypasses)")
      And("this is the behaviour that lets older endpoints stay reachable via newer enabled prefixes")
      rootStatus shouldBe 200
      banksStatus shouldBe 200
    }

    Scenario("Disabled-endpoint wins over enabled-endpoint when same id is in both", EnableDisablePropsTag) {
      Given(s"api_disabled_endpoints=[$rootOpId] AND api_enabled_endpoints=[$rootOpId]")
      setPropsValues(
        "api_disabled_endpoints" -> s"[$rootOpId]",
        "api_enabled_endpoints" -> s"[$rootOpId]"
      )

      When("requesting GET /obp/v7.0.0/root")
      val status = get(rootPath)

      Then("the disabled list wins → 404")
      status shouldBe 404
    }

    Scenario("api_disabled_versions does NOT override api_enabled_endpoints at the middleware", EnableDisablePropsTag) {
      Given(s"api_disabled_versions=[v7.0.0] AND api_enabled_endpoints=[$rootOpId]")
      setPropsValues(
        "api_disabled_versions" -> "[v7.0.0]",
        "api_enabled_endpoints" -> s"[$rootOpId]"
      )

      When("requesting GET /obp/v7.0.0/root")
      val status = get(rootPath)

      Then("the endpoint serves — only the endpoint-level allowlist matters in the middleware")
      And("(the version-level gate is enforced separately by Http4sApp.gate at the URL prefix)")
      status shouldBe 200
    }

    Scenario("After Props reset, baseline behavior is restored", EnableDisablePropsTag) {
      Given("no Props set (afterEach in the prior scenario has reset locked providers)")
      When("requesting GET /obp/v7.0.0/root")
      val status = get(rootPath)
      Then("the endpoint serves normally — proves PropsReset isolated each scenario")
      status shouldBe 200
    }
  }

  // ─── Cascade reachability across a disabled middle version ──────────────────
  //
  // Driving `Http4s400.wrappedRoutesV400Services` directly exercises:
  //   1. Http4s400's own routes via its middleware  (no /certs match → falls through)
  //   2. v400ToV310Bridge — rewrites `/obp/v4.0.0/...` to `/obp/v3.1.0/...` and
  //      calls `Http4s310.wrappedRoutesV310Services` directly (bypasses Http4sApp.gate)
  //   3. Http4s310's middleware + own routes (matches /certs → serves)
  //
  // `getServerJWK` (GET /certs) is a no-auth no-DB endpoint declared only in
  // Http4s310 — newer versions don't redeclare it. Perfect for proving that an
  // endpoint originally registered against a "middle" version is still reachable
  // from a newer version's prefix even when the middle version is disabled.
  //
  // If a future change reintroduces a per-request `implementedInApiVersion`
  // check inside `ResourceDocMiddleware`, the second scenario flips to 404 and
  // a reviewer is forced to revisit the design before merging. That's the
  // safety net that pins the cascade contract end-to-end.
  Feature("ResourceDocMiddleware — cascade reachability survives api_disabled_versions on the middle version") {

    val certsViaV4 = "/obp/v4.0.0/certs"

    Scenario("Baseline: cascade reaches /certs from /obp/v4.0.0 via v400ToV310Bridge", EnableDisablePropsTag) {
      Given("no enable/disable Props set")
      When("requesting GET /obp/v4.0.0/certs against Http4s400.wrappedRoutesV400Services")
      val status = getV4(certsViaV4)
      Then("Http4s400 has no /certs route → v400ToV310Bridge serves it from Http4s310")
      status shouldBe 200
    }

    Scenario("api_disabled_versions=[v3.1.0] does NOT break the v4→v3.1 cascade", EnableDisablePropsTag) {
      Given("api_disabled_versions=[v3.1.0] — at some point during the migration to http4s, the intended design was broken and this would have killed cascaded reachability")
      setPropsValues("api_disabled_versions" -> "[v3.1.0]")

      When("requesting GET /obp/v4.0.0/certs through the v400ToV310Bridge")
      val status = getV4(certsViaV4)

      Then("the endpoint still serves — the middleware does not enforce version-level disable")
      And("(direct /obp/v3.1.0/certs would 404 in production via Http4sApp.gate, but the gate")
      And("is not exercised by driving wrappedRoutesV400Services directly — the cascade test is")
      And("specifically about ResourceDocMiddleware not killing the bridge dispatch)")
      status shouldBe 200
    }
  }
}
