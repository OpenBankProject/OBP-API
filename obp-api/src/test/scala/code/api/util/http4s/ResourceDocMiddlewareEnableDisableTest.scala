package code.api.util.http4s

import org.json4s._
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ApiTag.ResourceDocTag
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import org.json4s.JsonAST.JObject
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers, Tag}

/**
 * Unit tests for `ResourceDocMiddleware.isEndpointEnabled`.
 *
 * Covers the gating logic the middleware applies on every request:
 *
 * - `api_disabled_endpoints`  — operationIds blocked by this Prop
 * - `api_enabled_endpoints`   — allowlist; if non-empty, only listed operationIds pass
 *
 * Version-level enable/disable (`api_disabled_versions` / `api_enabled_versions`) is
 * NOT enforced here — it is applied once at startup by `Http4sApp.gate` for the URL
 * prefix the request arrives at. This preserves the documented cascading behaviour:
 * disabling vX retires `/obp/vX/...` but leaves vX's endpoints reachable via newer
 * enabled prefixes. The "version-level gating is delegated" feature below pins that
 * contract — anyone restoring a per-request version check in the middleware will
 * break those scenarios.
 */
class ResourceDocMiddlewareEnableDisableTest extends FeatureSpec with Matchers with GivenWhenThen {

  object EnableDisableTag extends Tag("EnableDisable")

  private def doc(operationName: String, version: ScannedApiVersion = ApiVersion.v7_0_0): ResourceDoc =
    ResourceDoc(
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

  feature("ResourceDocMiddleware.isEndpointEnabled — endpoint-level gating") {

    scenario("baseline: no Props set → endpoint is enabled", EnableDisableTag) {
      Given("a ResourceDoc and empty disabled/enabled sets")
      val rd = doc("getBank")

      When("isEndpointEnabled is called")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd, disabledOperationIds = Set.empty, enabledOperationIds = Set.empty
      )

      Then("the endpoint is enabled")
      result shouldBe true
    }

    scenario("operationId in api_disabled_endpoints → disabled", EnableDisableTag) {
      Given("a ResourceDoc whose operationId is listed in disabled")
      val rd = doc("getBank")

      When("isEndpointEnabled is called with that operationId disabled")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd, disabledOperationIds = Set(rd.operationId), enabledOperationIds = Set.empty
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
        rd, disabledOperationIds = Set.empty, enabledOperationIds = Set(other.operationId)
      )

      Then("the endpoint is disabled (allowlist excludes it)")
      result shouldBe false
    }

    scenario("api_enabled_endpoints contains this operationId → enabled", EnableDisableTag) {
      Given("an enabled allowlist that contains this operationId")
      val rd = doc("getBank")

      When("isEndpointEnabled is called against the allowlist")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd, disabledOperationIds = Set.empty, enabledOperationIds = Set(rd.operationId)
      )

      Then("the endpoint is enabled")
      result shouldBe true
    }

    scenario("empty api_enabled_endpoints does NOT disable anything (allow-all semantics)", EnableDisableTag) {
      Given("an empty enabled allowlist")
      val rd = doc("getBank")

      When("isEndpointEnabled is called")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd, disabledOperationIds = Set.empty, enabledOperationIds = Set.empty
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
        enabledOperationIds = Set(rd.operationId)
      )

      Then("disabled wins")
      result shouldBe false
    }
  }

  feature("ResourceDocMiddleware.isEndpointEnabled — version-level gating is delegated to Http4sApp.gate") {

    // These scenarios encode an intentional design decision: the middleware does NOT
    // re-check `implementedInApiVersion` against `api_disabled_versions` /
    // `api_enabled_versions`. Version-level disable is handled once at startup by
    // `Http4sApp.gate`, which empties the disabled version's top-level routes so
    // direct `/obp/vX.Y.Z/...` traffic falls through to a 404. Cascaded traffic
    // (e.g. `/obp/v4.0.0/foo` resolving to a v2.0.0-origin endpoint) keeps working
    // when v2.0.0 is disabled and v4.0.0 is enabled — that is the documented OBP
    // behaviour where newer versions act as the supported entry point for older
    // endpoints' functionality. If a future change reintroduces a per-request
    // version check inside `isEndpointEnabled`, these scenarios will flip and a
    // reviewer will be forced to revisit the design before merging.

    scenario("isEndpointEnabled has no `versionAllowed` parameter — pins the API shape", EnableDisableTag) {
      Given("a ResourceDoc on any version, e.g. v6")
      val rd = doc("getBank", version = ApiVersion.v6_0_0)

      When("isEndpointEnabled is called with both Props sets empty")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd, disabledOperationIds = Set.empty, enabledOperationIds = Set.empty
      )

      Then("the endpoint is enabled — version is not part of the decision")
      result shouldBe true
    }

    scenario("the version on the ResourceDoc never influences the decision", EnableDisableTag) {
      Given("two ResourceDocs that differ only in version")
      val rd6 = doc("getBank", version = ApiVersion.v6_0_0)
      val rd7 = doc("getBank", version = ApiVersion.v7_0_0)

      When("isEndpointEnabled is called for each with identical Props")
      val r6 = ResourceDocMiddleware.isEndpointEnabled(rd6, Set.empty, Set.empty)
      val r7 = ResourceDocMiddleware.isEndpointEnabled(rd7, Set.empty, Set.empty)

      Then("both are enabled — the middleware treats version as out-of-scope")
      r6 shouldBe true
      r7 shouldBe true
    }

    scenario("endpoint-level disable still applies regardless of version", EnableDisableTag) {
      Given("a v6 ResourceDoc whose operationId is in api_disabled_endpoints")
      val rd = doc("getBank", version = ApiVersion.v6_0_0)

      When("isEndpointEnabled is called")
      val result = ResourceDocMiddleware.isEndpointEnabled(
        rd, disabledOperationIds = Set(rd.operationId), enabledOperationIds = Set.empty
      )

      Then("the endpoint is disabled — endpoint-level Props are unaffected by this change")
      result shouldBe false
    }
  }
}
