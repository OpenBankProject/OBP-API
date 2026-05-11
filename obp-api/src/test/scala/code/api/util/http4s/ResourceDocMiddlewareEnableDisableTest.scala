package code.api.util.http4s

import code.api.util.APIUtil.ResourceDoc
import code.api.util.ApiTag.ResourceDocTag
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.json.JsonAST.JObject
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers, Tag}

/**
 * Unit tests for `ResourceDocMiddleware.isEndpointEnabled`.
 *
 * Covers the gating logic the middleware applies on every request, matching the
 * semantics of `APIUtil.getAllowedResourceDocs` / `APIUtil.versionIsAllowed` used
 * on the Lift path:
 *
 * - `api_disabled_endpoints`  — operationIds blocked by this Prop
 * - `api_enabled_endpoints`   — allowlist; if non-empty, only listed operationIds pass
 * - `api_disabled_versions` / `api_enabled_versions` — modelled here as a
 *   `ScannedApiVersion => Boolean` so the decision can be tested without
 *   mutating global Props.
 *
 * Integration of the four real Props (`APIUtil.getDisabledEndpointOperationIds`
 * etc.) into the middleware happens once at middleware construction; that wiring
 * is verified by compile-time type-checking of `apply` plus the existing pure-
 * function tests in `APIUtilHeavyTest`. This file pins the *composition* logic.
 */
class ResourceDocMiddlewareEnableDisableTest extends FeatureSpec with Matchers with GivenWhenThen {

  object EnableDisableTag extends Tag("EnableDisable")

  private def doc(operationName: String, version: ScannedApiVersion = ApiVersion.v7_0_0): ResourceDoc =
    ResourceDoc(
      partialFunction = null,
      implementedInApiVersion = version,
      partialFunctionName = operationName,
      requestVerb = "GET",
      requestUrl = "/test",
      summary = "Test endpoint",
      description = "Test description",
      exampleRequestBody = JObject(Nil),
      successResponseBody = JObject(Nil),
      errorResponseBodies = List.empty,
      tags = List(ResourceDocTag("test")),
      roles = None
    )

  private val allowAllVersions: ScannedApiVersion => Boolean = _ => true
  private val denyAllVersions: ScannedApiVersion => Boolean = _ => false

  feature("ResourceDocMiddleware.isEndpointEnabled — endpoint-level gating") {

    scenario("baseline: no Props set → endpoint is enabled", EnableDisableTag) {
      Given("a ResourceDoc and empty disabled/enabled sets")
      val rd = doc("getBank")

      When("isEndpointEnabled is called")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd, disabledOperationIds = Set.empty, enabledOperationIds = Set.empty, allowAllVersions
      )

      Then("the endpoint is enabled")
      result shouldBe true
    }

    scenario("operationId in api_disabled_endpoints → disabled", EnableDisableTag) {
      Given("a ResourceDoc whose operationId is listed in disabled")
      val rd = doc("getBank")

      When("isEndpointEnabled is called with that operationId disabled")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd, disabledOperationIds = Set(rd.operationId), enabledOperationIds = Set.empty, allowAllVersions
      )

      Then("the endpoint is disabled")
      result shouldBe false
    }

    scenario("api_enabled_endpoints is non-empty and excludes this operationId → disabled", EnableDisableTag) {
      Given("an enabled allowlist that does not contain this operationId")
      val rd = doc("getBank")
      val other = doc("getBanks")

      When("isEndpointEnabled is called against the allowlist")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd, disabledOperationIds = Set.empty, enabledOperationIds = Set(other.operationId), allowAllVersions
      )

      Then("the endpoint is disabled (allowlist excludes it)")
      result shouldBe false
    }

    scenario("api_enabled_endpoints contains this operationId → enabled", EnableDisableTag) {
      Given("an enabled allowlist that contains this operationId")
      val rd = doc("getBank")

      When("isEndpointEnabled is called against the allowlist")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd, disabledOperationIds = Set.empty, enabledOperationIds = Set(rd.operationId), allowAllVersions
      )

      Then("the endpoint is enabled")
      result shouldBe true
    }

    scenario("empty api_enabled_endpoints does NOT disable anything (allow-all semantics)", EnableDisableTag) {
      Given("an empty enabled allowlist")
      val rd = doc("getBank")

      When("isEndpointEnabled is called")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd, disabledOperationIds = Set.empty, enabledOperationIds = Set.empty, allowAllVersions
      )

      Then("the endpoint is enabled — empty allowlist means 'no restriction'")
      result shouldBe true
    }

    scenario("disabled wins over enabled — operationId in both sets → disabled", EnableDisableTag) {
      Given("an operationId that is both enabled and disabled")
      val rd = doc("getBank")

      When("isEndpointEnabled is called with the operationId in both sets")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd,
        disabledOperationIds = Set(rd.operationId),
        enabledOperationIds = Set(rd.operationId),
        allowAllVersions
      )

      Then("disabled wins")
      result shouldBe false
    }
  }

  feature("ResourceDocMiddleware.isEndpointEnabled — version-level gating") {

    scenario("version is disabled → endpoint is disabled regardless of endpoint Props", EnableDisableTag) {
      Given("a ResourceDoc whose version is denied")
      val rd = doc("getBank", version = ApiVersion.v7_0_0)

      When("isEndpointEnabled is called with versionAllowed returning false")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd, disabledOperationIds = Set.empty, enabledOperationIds = Set.empty, denyAllVersions
      )

      Then("the endpoint is disabled")
      result shouldBe false
    }

    scenario("version is disabled overrides an explicit enabled-endpoints entry → disabled", EnableDisableTag) {
      Given("a ResourceDoc explicitly enabled by operationId but on a disabled version")
      val rd = doc("getBank", version = ApiVersion.v7_0_0)

      When("isEndpointEnabled is called with the version denied")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd,
        disabledOperationIds = Set.empty,
        enabledOperationIds = Set(rd.operationId),
        denyAllVersions
      )

      Then("the version gate wins — endpoint is disabled")
      result shouldBe false
    }

    scenario("per-version gating: only the doc's own version matters", EnableDisableTag) {
      Given("a versionAllowed function that allows v7 but denies v6")
      val rd7 = doc("getBank", version = ApiVersion.v7_0_0)
      val rd6 = doc("getBank", version = ApiVersion.v6_0_0)
      val versionAllowed: ScannedApiVersion => Boolean = {
        case v if v == ApiVersion.v7_0_0 => true
        case _ => false
      }

      When("isEndpointEnabled is called for each ResourceDoc")
      val v7Result = ResourceDocMiddleware.isEndpointEnabled(rd7, Set.empty, Set.empty, versionAllowed)
      val v6Result = ResourceDocMiddleware.isEndpointEnabled(rd6, Set.empty, Set.empty, versionAllowed)

      Then("v7 is enabled and v6 is disabled")
      v7Result shouldBe true
      v6Result shouldBe false
    }
  }
}
