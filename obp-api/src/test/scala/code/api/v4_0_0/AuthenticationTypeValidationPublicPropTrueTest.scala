package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.entitlement.Entitlement
import code.setup.APIResponse
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import com.openbankproject.commons.util.json
import org.json4s.JArray
import org.json4s.JString
import org.scalatest.Tag

// Boots its own JVM with read_authentication_type_validation_requires_role forced true, via
// OBP_READ_AUTHENTICATION_TYPE_VALIDATION_REQUIRES_ROLE (see run_tests_parallel.sh and the CI job
// that runs this class in isolation). Same rationale as JsonSchemaValidationPublicPropTrueTest:
// the prop's effect is baked into Http4s400's ResourceDoc error list at object-initialization
// time, so it needs its own process to observe the true branch.
class AuthenticationTypeValidationPublicPropTrueTest extends V400ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object PropGatedPublicEndpoint extends Tag("PropGatedPublicEndpoint")

  private val mockOperationId = "MOCK_OPERATION_ID"

  private val allowedDirectLogin =
    """
      |["DirectLogin"]
      |""".stripMargin

  private def grantEntitlement(role: code.api.util.ApiRole) =
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, role.toString)

  private def addOneAuthenticationTypeValidation(allowedAuthTypes: String, operationId: String): APIResponse = {
    grantEntitlement(code.api.util.ApiRole.canCreateAuthenticationTypeValidation)
    val request = (v4_0_0_Request / "management" / "authentication-type-validations" / operationId).POST <@ user1
    val response = makePostRequest(request, allowedAuthTypes)
    response.code should equal(201)
    response
  }

  feature(s"test GET /endpoints/authentication-type-validations version $VersionOfApi - read_authentication_type_validation_requires_role=true") {
    scenario("Anonymous access is rejected when the prop requires authentication", PropGatedPublicEndpoint, VersionOfApi) {
      When("We make an anonymous request to the public endpoint")
      val request = (v4_0_0_Request / "endpoints" / "authentication-type-validations").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }

    scenario("Authenticated access (no entitlement needed) succeeds when the prop requires authentication", PropGatedPublicEndpoint, VersionOfApi) {
      addOneAuthenticationTypeValidation(allowedDirectLogin, mockOperationId)

      When("We make an authenticated request to the public endpoint")
      val request = (v4_0_0_Request / "endpoints" / "authentication-type-validations").GET <@ user1
      val response = makeGetRequest(request)
      Then("We should get a 200 - roles=None on this ResourceDoc, only authentication is required")
      response.code should equal(200)
      val authTypeValidations = response.body \ "authentication_types_validations"
      authTypeValidations shouldBe a[JArray]
      val authTypeValidation = authTypeValidations(0)
      authTypeValidation \ "operation_id" should equal(JString(mockOperationId))
      authTypeValidation \ "allowed_authentication_types" should equal(json.parse(allowedDirectLogin))
    }
  }
}
