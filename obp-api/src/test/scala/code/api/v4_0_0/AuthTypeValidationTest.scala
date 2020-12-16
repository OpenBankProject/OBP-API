package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v3_0_0.OBPAPI3_0_0.Implementations2_2_0
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import code.setup.APIResponse
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json
import net.liftweb.json.JsonAST.JBool
import net.liftweb.json.{JArray, JString}
import org.scalatest.Tag

class AuthTypeValidationTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createAuthTypeValidation))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateAuthTypeValidation))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.deleteAuthTypeValidation))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getAuthTypeValidation))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getAllAuthTypeValidation))

  object ApiEndpointCreateFx extends Tag(nameOf(Implementations2_2_0.createFx))

  lazy val bankId = randomBankId
  private val mockOperationId = "MOCK_OPERATION_ID"

  feature(s"test AuthTypeValidation endpoints version $VersionOfApi - Unauthenticated access") {
    scenario(s"We will call the endpoint $ApiEndpoint1 without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).POST
      val response= makePostRequest(request, allowedDirectLogin)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).PUT
      val response= makePutRequest(request, allowedDirectLogin)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).DELETE
      val response= makeDeleteRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).GET
      val response= makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario(s"We will call the endpoint $ApiEndpoint5 without user credentials", ApiEndpoint5, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" ).GET
      val response= makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test AuthTypeValidation endpoints version $VersionOfApi - Unauthorized access") {
    scenario(s"We will call the endpoint $ApiEndpoint1 without required role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).POST <@ user1
      val response= makePostRequest(request, allowedDirectLogin)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canCreateAuthTypeValidation")
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 without required role", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).PUT <@ user1
      val response= makePutRequest(request, allowedDirectLogin)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canUpdateAuthTypeValidation")
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 without required role", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).DELETE <@ user1
      val response= makeDeleteRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canDeleteAuthTypeValidation")
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 without required role", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canGetAuthTypeValidation")
    }

    scenario(s"We will call the endpoint $ApiEndpoint5 without required role", ApiEndpoint5, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" ).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canGetAuthTypeValidation")
    }
  }

  feature(s"test AuthTypeValidation endpoints version $VersionOfApi - Authorized access") {
    scenario(s"We will call the endpoint $ApiEndpoint1 with required role", ApiEndpoint1, VersionOfApi) {
      addEntitlement(canCreateAuthTypeValidation)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).POST <@ user1
      val response= makePostRequest(request, allowedDirectLogin)
      Then("We should get a 201")
      response.code should equal(201)
      val authTypeValidation = response.body
      authTypeValidation \ "operation_id" should equal (JString(mockOperationId))
      authTypeValidation \ "allowed_auth_types" should equal (json.parse(allowedDirectLogin))
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 with required role", ApiEndpoint2, VersionOfApi) {
      addOneAuthTypeValidation(allowedDirectLogin, mockOperationId)
      addEntitlement(canUpdateAuthTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).PUT <@ user1
      val response= makePutRequest(request, allowedAll)
      Then("We should get a 200")
      response.code should equal(200)
      val authTypeValidation = response.body
      authTypeValidation \ "operation_id" should equal (JString(mockOperationId))
      authTypeValidation \ "allowed_auth_types" should equal (json.parse(allowedAll))
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 with required role", ApiEndpoint3, VersionOfApi) {
      addOneAuthTypeValidation(allowedDirectLogin, mockOperationId)
      addEntitlement(canDeleteAuthTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).DELETE <@ user1
      val response= makeDeleteRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      response.body should equal(JBool(true))
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 with required role", ApiEndpoint4, VersionOfApi) {
      addOneAuthTypeValidation(allowedDirectLogin, mockOperationId)
      addEntitlement(canGetAuthTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      val authTypeValidation = response.body
      authTypeValidation \ "operation_id" should equal (JString(mockOperationId))
      authTypeValidation \ "allowed_auth_types" should equal (json.parse(allowedDirectLogin))
    }

    scenario(s"We will call the endpoint $ApiEndpoint5 with required role", ApiEndpoint5, VersionOfApi) {
      addOneAuthTypeValidation(allowedDirectLogin, mockOperationId)
      addEntitlement(canGetAuthTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" ).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      val authTypeValidations = response.body \ "auth_types_validations"
      authTypeValidations shouldBe a [JArray]

      val authTypeValidation = authTypeValidations(0)
      authTypeValidation \ "operation_id" should equal (JString(mockOperationId))
      authTypeValidation \ "allowed_auth_types" should equal (json.parse(allowedDirectLogin))
    }
  }

  feature(s"test AuthTypeValidation endpoints version $VersionOfApi - Wrong request") {
    scenario(s"We will call the endpoint $ApiEndpoint1 with wrong auth type name", ApiEndpoint1, VersionOfApi) {
      addEntitlement(canCreateAuthTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).POST <@ user1
      val response= makePostRequest(request, """["wrong_auth_name"]""")
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthTypeNameIllegal)
      message should include("Allowed AuthType names: [")
    }

    scenario(s"We will call the endpoint $ApiEndpoint1 with exists operationId", ApiEndpoint1, VersionOfApi) {
      addOneAuthTypeValidation(allowedDirectLogin, mockOperationId)

      When("We make a request v4.0.0")
      addEntitlement(canCreateAuthTypeValidation)
      val request = (v4_0_0_Request / "management" / "authTypeValidations" / mockOperationId).POST <@ user1
      val response = makePostRequest(request, allowedDirectLogin)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(ValidationOperationIdExistsError)
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 with not exists operationId", ApiEndpoint2, VersionOfApi) {
      addEntitlement(canUpdateAuthTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).PUT <@ user1
      val response= makePutRequest(request, allowedDirectLogin)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthTypeValidationNotFound)
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 with required role", ApiEndpoint3, VersionOfApi) {
      addEntitlement(canDeleteAuthTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).DELETE <@ user1
      val response = makeDeleteRequest(request)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthTypeValidationNotFound)
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 with required role", ApiEndpoint4, VersionOfApi) {
      addEntitlement(canGetAuthTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authTypeValidations" /  mockOperationId).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthTypeValidationNotFound)
    }

  }


  feature(s"test AuthTypeValidation endpoints version $VersionOfApi - Validate static endpoint request body") {
    scenario(s"We will call the endpoint $ApiEndpointCreateFx with invalid Fx", VersionOfApi) {
      addOneAuthTypeValidation(allowedDirectLogin, "OBPv2.2.0-createFx")
      addEntitlement(canCreateFxRate, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId /  "fx").PUT <@ user1
      val response= makePutRequest(request, newFx)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthTypeIllegal)
      message should include("allowed auth types: [DirectLogin]")
      message should include("current request auth type: OAuth1.0a")
    }

    scenario(s"We will call the endpoint $ApiEndpointCreateFx with valid Fx", VersionOfApi) {
      addOneAuthTypeValidation(allowedAll, "OBPv2.2.0-createFx")
      addEntitlement(canCreateFxRate, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId /  "fx").PUT <@ user1
      val response= makePutRequest(request, newFx)
      Then("We should get a 201")
      response.code should equal(201)
    }

  }

  feature(s"test AuthTypeValidation endpoints version $VersionOfApi - Validate dynamic entity endpoint request body") {
    scenario(s"We will call the endpoint $ApiEndpoint1 with invalid FooBar", ApiEndpoint1, VersionOfApi) {
      addOneAuthTypeValidation(allowedDirectLogin, "OBPv4.0.0-dynamicEntity_createFooBar")
      addDynamicEntity()
      addStringEntitlement("CanCreateDynamicEntity_FooBar", bankId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId /  "FooBar").POST <@ user1
      val response= makePostRequest(request, newFooBar)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthTypeIllegal)
      message should include("allowed auth types: [DirectLogin]")
      message should include("current request auth type: OAuth1.0a")
    }

    scenario(s"We will call the endpoint $ApiEndpoint1 with valid FooBar", ApiEndpoint1, VersionOfApi) {
      addOneAuthTypeValidation(allowedAll, "OBPv4.0.0-dynamicEntity_createFooBar")
      addDynamicEntity()
      addStringEntitlement("CanCreateDynamicEntity_FooBar", bankId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId /  "FooBar").POST <@ user1
      val response= makePostRequest(request, newFooBar)
      Then("We should get a 201")
      response.code should equal(201)
    }

  }

  feature(s"test AuthTypeValidation endpoints version $VersionOfApi - Validate dynamic endpoints endpoint request body") {
    scenario("We will call the endpoint /dynamic/save with invalid FooBar", VersionOfApi) {
      addOneAuthTypeValidation(allowedDirectLogin, "OBPv4.0.0-dynamicEndpoint_POST_save")
      addDynamicEndpoints()
      addStringEntitlement("CanCreateDynamicEndpoint_User469")

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "dynamic" / "save").POST <@ user1
      val response= makePostRequest(request, newUser)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthTypeIllegal)
      message should include("allowed auth types: [DirectLogin]")
      message should include("current request auth type: OAuth1.0a")
    }

    scenario("We will call the endpoint /dynamic/save with valid FooBar", ApiEndpoint1, VersionOfApi) {
      addOneAuthTypeValidation(allowedAll, "OBPv4.0.0-dynamicEndpoint_POST_save")
      addDynamicEndpoints()
      addStringEntitlement("CanCreateDynamicEndpoint_User469")

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "dynamic" /  "save").POST <@ user1
      val response= makePostRequest(request, newUser)
      Then("We should get a 201")
      response.code should equal(201)
    }

  }

  private def addEntitlement(role: ApiRole, bankId: String = "") = addStringEntitlement(role.toString, bankId)
  private def addStringEntitlement(role: String, bankId: String = "") = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, role)

  // prepare one AuthTypeValidation for update, delete and get
  private def addOneAuthTypeValidation(allowedAuthTypes: String, operationId: String): APIResponse = {
    addEntitlement(canCreateAuthTypeValidation)
    val request = (v4_0_0_Request / "management" / "authTypeValidations" / operationId).POST <@ user1
    val response = makePostRequest(request, allowedAuthTypes)
    response.code should equal(201)

    response
  }
  // prepare one dynamic entity FooBar
  private def addDynamicEntity(): APIResponse = {
    addEntitlement(canCreateDynamicEntity)
    val request = (v4_0_0_Request / "management" / "dynamic-entities").POST <@ user1
    val fooBar =
      s"""
        |{
        |    "bankId": "$bankId",
        |    "FooBar": {
        |        "description": "description of this entity, can be markdown text.",
        |        "required": [
        |            "name"
        |        ],
        |        "properties": {
        |            "name": {
        |                "type": "string",
        |                "minLength": 3,
        |                "maxLength": 20,
        |                "example": "James Brown",
        |                "description": "description of **name** field, can be markdown text."
        |            },
        |            "number": {
        |                "type": "integer",
        |                "example": 698761728,
        |                "description": "description of **number** field, can be markdown text."
        |            }
        |        }
        |    }
        |}""".stripMargin
    val response = makePostRequest(request, fooBar)
    response.code should equal(201)

    response
  }
  // prepare dynamic endpoints
  private def addDynamicEndpoints(): APIResponse = {
    addEntitlement(canCreateDynamicEndpoint)
    val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST <@ user1

    val response = makePostRequest(request, swagger)
    response.code should equal(201)

    response
  }

  private val allowedDirectLogin =
    """
      |["DirectLogin"]
      |""".stripMargin

  private val allowedAll =
    """
      |["DirectLogin", "OAuth1.0a", "GatewayLogin", "OAuth2_OIDC", "OAuth2_OIDC_FAPI"]
      |""".stripMargin

  private val newFx =
    """
      |{
      |    "bank_id": "gh.29.uk",
      |    "from_currency_code": "EUR",
      |    "to_currency_code": "USD",
      |    "conversion_value": 1.136305,
      |    "inverse_conversion_value": 0.8800454103431737,
      |    "effective_date": "2017-09-19T00:00:00Z"
      |}
      |""".stripMargin

  private val newFooBar = """{  "name":"James Brown",  "number":200}"""

  private val swagger =
    """
      |{
      |    "swagger": "2.0",
      |    "info": {
      |        "version": "0.0.1",
      |        "title": "User Infomation for json-schema authTypeValidation",
      |        "description": "Example Description",
      |        "contact": {
      |            "name": "Example Company",
      |            "email": " simon@example.com",
      |            "url": "https://www.tesobe.com/"
      |        }
      |    },
      |    "host": "obp_mock",
      |    "basePath": "/user",
      |    "schemes": [
      |        "http"
      |    ],
      |    "consumes": [
      |        "application/json"
      |    ],
      |    "produces": [
      |        "application/json"
      |    ],
      |    "paths": {
      |        "/save": {
      |            "post": {
      |                "parameters": [
      |                    {
      |                        "name": "body",
      |                        "in": "body",
      |                        "required": true,
      |                        "schema": {
      |                            "$ref": "#/definitions/user"
      |                        }
      |                    }
      |                ],
      |                "responses": {
      |                    "201": {
      |                        "description": "create user successful and return created user object",
      |                        "schema": {
      |                            "$ref": "#/definitions/user"
      |                        }
      |                    },
      |                    "500": {
      |                        "description": "unexpected error",
      |                        "schema": {
      |                            "$ref": "#/responses/unexpectedError"
      |                        }
      |                    }
      |                }
      |            }
      |        }
      |    },
      |    "definitions": {
      |        "user": {
      |            "type": "object",
      |            "properties": {
      |                "id": {
      |                    "type": "integer",
      |                    "description": "user ID"
      |                },
      |                "first_name": {
      |                    "type": "string"
      |                },
      |                "last_name": {
      |                    "type": "string"
      |                },
      |                "age": {
      |                    "type": "integer"
      |                },
      |                "career": {
      |                    "type": "string"
      |                }
      |            },
      |            "required": [
      |                "first_name",
      |                "last_name",
      |                "age"
      |            ]
      |        },
      |        "APIError": {
      |            "description": "content any error from API",
      |            "type": "object",
      |            "properties": {
      |                "errorCode": {
      |                    "description": "content error code relate to API",
      |                    "type": "string"
      |                },
      |                "errorMessage": {
      |                    "description": "content user-friendly error message",
      |                    "type": "string"
      |                }
      |            }
      |        }
      |    },
      |    "responses": {
      |        "unexpectedError": {
      |            "description": "unexpected error",
      |            "schema": {
      |                "$ref": "#/definitions/APIError"
      |            }
      |        },
      |        "invalidRequest": {
      |            "description": "invalid request",
      |            "schema": {
      |                "$ref": "#/definitions/APIError"
      |            }
      |        }
      |    },
      |    "parameters": {
      |        "userId": {
      |            "name": "userId",
      |            "in": "path",
      |            "required": true,
      |            "type": "string",
      |            "description": "user ID"
      |        }
      |    }
      |}""".stripMargin

  private val newUser =
    """
      |{
      |    "id": 111,
      |    "first_name": "Robert",
      |    "last_name": "Li",
      |    "age": 10,
      |    "career": "developer"
      |}""".stripMargin

}
