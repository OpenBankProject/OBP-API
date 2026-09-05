package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.api.util.ApiRole
import code.entitlement.Entitlement
import code.setup.APIResponse
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import com.openbankproject.commons.util.json
import org.json4s.JArray
import org.json4s.JString
import org.scalatest.Tag

// Boots its own JVM with read_json_schema_validation_requires_role forced true, via
// OBP_READ_JSON_SCHEMA_VALIDATION_REQUIRES_ROLE (see run_tests_parallel.sh and the CI job that
// runs this class in isolation). The prop's effect is baked into Http4s400's ResourceDoc error
// list at object-initialization time, so a single running JVM can only ever observe one value of
// it - this class exists to observe the true branch while JsonSchemaValidationTest observes the
// (default) false branch in every ordinary shard.
class JsonSchemaValidationPublicPropTrueTest extends V400ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object PropGatedPublicEndpoint extends Tag("PropGatedPublicEndpoint")

  private val mockOperationId = "MOCK_OPERATION_ID"

  // Same fixture JsonSchemaValidationTest uses — createJsonSchemaValidation validates the
  // incoming schema itself, so an under-specified schema (e.g. missing "required") 400s here
  // for reasons unrelated to what this test is actually checking.
  private val jsonSchemaFooBar =
    """
      |{
      |    "$schema": "http://json-schema.org/draft-07/schema",
      |    "$id": "http://example.com/example.json",
      |    "type": "object",
      |    "title": "The root schema",
      |    "description": "The root schema comprises the entire JSON document.",
      |    "examples": [
      |        {
      |            "name": "James Brown",
      |            "number": 698761728
      |        }
      |    ],
      |    "required": [
      |        "name",
      |        "number"
      |    ],
      |    "properties": {
      |        "name": {
      |            "type": "string",
      |            "description": "An explanation about the purpose of this instance.",
      |            "examples": [
      |                "James Brown"
      |            ]
      |        },
      |        "number": {
      |            "type": "integer",
      |            "description": "An explanation about the purpose of this instance.",
      |            "maximum": 698761730,
      |            "minimum": 10,
      |            "examples": [
      |                698761728
      |            ]
      |        }
      |    },
      |    "additionalProperties": true
      |}
      |""".stripMargin

  private def addEntitlement(role: ApiRole) =
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, role.toString)

  private def addOneValidation(schema: String, operationId: String): APIResponse = {
    addEntitlement(ApiRole.canCreateJsonSchemaValidation)
    val request = (v4_0_0_Request / "management" / "json-schema-validations" / operationId).POST <@ user1
    val response = makePostRequest(request, schema)
    response.code should equal(201)
    response
  }

  feature(s"test GET /endpoints/json-schema-validations version $VersionOfApi - read_json_schema_validation_requires_role=true") {
    scenario("Anonymous access is rejected when the prop requires authentication", PropGatedPublicEndpoint, VersionOfApi) {
      When("We make an anonymous request to the public endpoint")
      val request = (v4_0_0_Request / "endpoints" / "json-schema-validations").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }

    // user2 deliberately: the setup below grants roles to user1, so asserting the 403 as user1
    // could not tell "the role is required" apart from "some role user1 already holds suffices".
    scenario("Authenticated access without the role is rejected when the prop requires the role", PropGatedPublicEndpoint, VersionOfApi) {
      addOneValidation(jsonSchemaFooBar, mockOperationId)

      When("We make an authenticated request as a user holding no entitlement")
      val request = (v4_0_0_Request / "endpoints" / "json-schema-validations").GET <@ user2
      val response = makeGetRequest(request)
      Then("We should get a 403 naming the missing role")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should include(
        ApiRole.canGetJsonSchemaValidation.toString)
    }

    scenario("Authenticated access with the role succeeds when the prop requires the role", PropGatedPublicEndpoint, VersionOfApi) {
      addOneValidation(jsonSchemaFooBar, mockOperationId)
      addEntitlement(ApiRole.canGetJsonSchemaValidation)

      When("We make an authenticated request as a user holding the role")
      val request = (v4_0_0_Request / "endpoints" / "json-schema-validations").GET <@ user1
      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      val validations = response.body \ "json_schema_validations"
      validations shouldBe a[JArray]
      val validation = validations(0)
      validation \ "operation_id" should equal(JString(mockOperationId))
      validation \ "json_schema" should equal(json.parse(jsonSchemaFooBar))
    }
  }
}
