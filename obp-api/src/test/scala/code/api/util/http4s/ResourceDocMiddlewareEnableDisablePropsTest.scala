package code.api.util.http4s

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import code.api.v7_0_0.Http4s700
import code.setup.ServerSetup
import fs2.Stream
import org.http4s.{Headers, Method, Request, Uri}
import org.scalatest.{GivenWhenThen, Tag}

/**
 * Integration test for the enable/disable Props wiring inside `ResourceDocMiddleware`.
 *
 * Drives `Http4s700.wrappedRoutesV700Services` in-process — no TCP, no DB. Verifies that
 * setting the four Props (`api_disabled_endpoints`, `api_enabled_endpoints`,
 * `api_disabled_versions`, `api_enabled_versions`) actually changes routing behaviour at
 * request time.
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

  // OperationIds match `APIUtil.buildOperationId(v, partialFunctionName)` →
  // s"$fullyQualifiedVersion-$name". v7.0.0's fully qualified form is "OBPv7.0.0".
  private val rootOpId    = "OBPv7.0.0-root"
  private val getBanksOpId = "OBPv7.0.0-getBanks"

  private val rootPath    = "/obp/v7.0.0/root"
  private val banksPath   = "/obp/v7.0.0/banks"

  private def get(path: String): Int = {
    val req = Request[IO](Method.GET, Uri.unsafeFromString(path), headers = Headers.empty,
                          body = Stream.empty)
    app.run(req).unsafeRunSync().status.code
  }

  feature("ResourceDocMiddleware — Props wiring at request time") {

    scenario("Baseline: no Props set → /root returns 200", EnableDisablePropsTag) {
      Given("no enable/disable Props are set")
      When("requesting GET /obp/v7.0.0/root")
      val status = get(rootPath)
      Then("the endpoint serves normally")
      status shouldBe 200
    }

    scenario("api_disabled_endpoints contains the operationId → 404", EnableDisablePropsTag) {
      Given(s"api_disabled_endpoints=[$rootOpId]")
      setPropsValues("api_disabled_endpoints" -> s"[$rootOpId]")

      When("requesting GET /obp/v7.0.0/root")
      val status = get(rootPath)

      Then("the middleware short-circuits to OptionT.none → 404 via orNotFound")
      status shouldBe 404

      And("other endpoints in the same version are unaffected")
      get(banksPath) shouldBe 200
    }

    scenario("api_enabled_endpoints contains a different operationId → 404 for non-listed", EnableDisablePropsTag) {
      Given(s"api_enabled_endpoints=[$getBanksOpId] (root is NOT listed)")
      setPropsValues("api_enabled_endpoints" -> s"[$getBanksOpId]")

      When("requesting GET /obp/v7.0.0/root")
      val rootStatus = get(rootPath)

      Then("the middleware short-circuits to 404 — allowlist excludes root")
      rootStatus shouldBe 404

      And("the explicitly enabled endpoint still serves")
      get(banksPath) shouldBe 200
    }

    scenario("api_enabled_endpoints contains the operationId → endpoint serves", EnableDisablePropsTag) {
      Given(s"api_enabled_endpoints=[$rootOpId]")
      setPropsValues("api_enabled_endpoints" -> s"[$rootOpId]")

      When("requesting GET /obp/v7.0.0/root")
      val status = get(rootPath)

      Then("the endpoint serves normally")
      status shouldBe 200
    }

    scenario("api_disabled_versions disables every endpoint of that version", EnableDisablePropsTag) {
      Given("api_disabled_versions=[v7.0.0]")
      setPropsValues("api_disabled_versions" -> "[v7.0.0]")

      When("requesting two unrelated v7 endpoints")
      val rootStatus = get(rootPath)
      val banksStatus = get(banksPath)

      Then("both are short-circuited by the middleware → 404")
      rootStatus shouldBe 404
      banksStatus shouldBe 404
    }

    scenario("Disabled-endpoint wins over enabled-endpoint when same id is in both", EnableDisablePropsTag) {
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

    scenario("api_disabled_versions overrides an explicit api_enabled_endpoints entry", EnableDisablePropsTag) {
      Given(s"api_disabled_versions=[v7.0.0] AND api_enabled_endpoints=[$rootOpId]")
      setPropsValues(
        "api_disabled_versions" -> "[v7.0.0]",
        "api_enabled_endpoints" -> s"[$rootOpId]"
      )

      When("requesting GET /obp/v7.0.0/root")
      val status = get(rootPath)

      Then("the version gate wins → 404")
      status shouldBe 404
    }

    scenario("After Props reset, baseline behavior is restored", EnableDisablePropsTag) {
      Given("no Props set (afterEach in the prior scenario has reset locked providers)")
      When("requesting GET /obp/v7.0.0/root")
      val status = get(rootPath)
      Then("the endpoint serves normally — proves PropsReset isolated each scenario")
      status shouldBe 200
    }
  }
}
