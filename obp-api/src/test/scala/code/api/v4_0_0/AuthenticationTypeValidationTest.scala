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

class AuthenticationTypeValidationTest extends V400ServerSetup {
  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createAuthenticationTypeValidation))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateAuthenticationTypeValidation))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.deleteAuthenticationTypeValidation))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getAuthenticationTypeValidation))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getAllAuthenticationTypeValidations))
  object ApiEndpoint6 extends Tag(nameOf(Implementations4_0_0.getAllAuthenticationTypeValidationsPublic))

  object ApiEndpointCreateFx extends Tag(nameOf(Implementations2_2_0.createFx))

  lazy val bankId = randomBankId
  private val mockOperationId = "MOCK_OPERATION_ID"

  feature(s"test AuthenticationTypeValidation endpoints version $VersionOfApi - Unauthenticated access") {
    scenario(s"We will call the endpoint $ApiEndpoint1 without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).POST
      val response= makePostRequest(request, allowedDirectLogin)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).PUT
      val response= makePutRequest(request, allowedDirectLogin)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).DELETE
      val response= makeDeleteRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).GET
      val response= makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario(s"We will call the endpoint $ApiEndpoint5 without user credentials", ApiEndpoint5, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" ).GET
      val response= makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test AuthenticationTypeValidation endpoints version $VersionOfApi - Unauthorized access") {
    scenario(s"We will call the endpoint $ApiEndpoint1 without required role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).POST <@ user1
      val response= makePostRequest(request, allowedDirectLogin)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canCreateAuthenticationTypeValidation")
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 without required role", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).PUT <@ user1
      val response= makePutRequest(request, allowedDirectLogin)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canUpdateAuthenticationTypeValidation")
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 without required role", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).DELETE <@ user1
      val response= makeDeleteRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canDeleteAuthenticationValidation")
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 without required role", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canGetAuthenticationTypeValidation")
    }

    scenario(s"We will call the endpoint $ApiEndpoint5 without required role", ApiEndpoint5, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" ).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canGetAuthenticationTypeValidation")
    }
  }

  feature(s"test AuthenticationTypeValidation endpoints version $VersionOfApi - Authorized access") {
    scenario(s"We will call the endpoint $ApiEndpoint1 with required role", ApiEndpoint1, VersionOfApi) {
      grantEntitlement(canCreateAuthenticationTypeValidation)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).POST <@ user1
      val response= makePostRequest(request, allowedDirectLogin)
      Then("We should get a 201")
      response.code should equal(201)
      val authTypeValidation = response.body
      authTypeValidation \ "operation_id" should equal (JString(mockOperationId))
      authTypeValidation \ "allowed_authentication_types" should equal (json.parse(allowedDirectLogin))
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 with required role", ApiEndpoint2, VersionOfApi) {
      addOneAuthenticationTypeValidation(allowedDirectLogin, mockOperationId)
      grantEntitlement(canUpdateAuthenticationTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).PUT <@ user1
      val response= makePutRequest(request, allowedAll)
      Then("We should get a 200")
      response.code should equal(200)
      val authTypeValidation = response.body
      authTypeValidation \ "operation_id" should equal (JString(mockOperationId))
      authTypeValidation \ "allowed_authentication_types" should equal (json.parse(allowedAll))
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 with required role", ApiEndpoint3, VersionOfApi) {
      addOneAuthenticationTypeValidation(allowedDirectLogin, mockOperationId)
      grantEntitlement(canDeleteAuthenticationValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).DELETE <@ user1
      val response= makeDeleteRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      response.body should equal(JBool(true))
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 with required role", ApiEndpoint4, VersionOfApi) {
      addOneAuthenticationTypeValidation(allowedDirectLogin, mockOperationId)
      grantEntitlement(canGetAuthenticationTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      val authTypeValidation = response.body
      authTypeValidation \ "operation_id" should equal (JString(mockOperationId))
      authTypeValidation \ "allowed_authentication_types" should equal (json.parse(allowedDirectLogin))
    }

    scenario(s"We will call the endpoint $ApiEndpoint5 with required role", ApiEndpoint5, VersionOfApi) {
      addOneAuthenticationTypeValidation(allowedDirectLogin, mockOperationId)
      grantEntitlement(canGetAuthenticationTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" ).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      val authTypeValidations = response.body \ "authentication_types_validations"
      authTypeValidations shouldBe a [JArray]

      val authTypeValidation = authTypeValidations(0)
      authTypeValidation \ "operation_id" should equal (JString(mockOperationId))
      authTypeValidation \ "allowed_authentication_types" should equal (json.parse(allowedDirectLogin))
    }

    scenario(s"We will call the endpoint $ApiEndpoint6 anonymously", ApiEndpoint6, VersionOfApi) {
      addOneAuthenticationTypeValidation(allowedDirectLogin, mockOperationId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "endpoints" / "authentication-type-validations" ).GET
      val response= makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      val authTypeValidations = response.body \ "authentication_types_validations"
      authTypeValidations shouldBe a [JArray]

      val authTypeValidation = authTypeValidations(0)
      authTypeValidation \ "operation_id" should equal (JString(mockOperationId))
      authTypeValidation \ "allowed_authentication_types" should equal (json.parse(allowedDirectLogin))
    }
  }

  feature(s"test AuthenticationTypeValidation endpoints version $VersionOfApi - Wrong request") {
    scenario(s"We will call the endpoint $ApiEndpoint1 with wrong auth type name", ApiEndpoint1, VersionOfApi) {
      grantEntitlement(canCreateAuthenticationTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).POST <@ user1
      val response= makePostRequest(request, """["wrong_auth_name"]""")
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthenticationTypeNameIllegal)
      message should include("Allowed Authentication Type names: [")
    }

    scenario(s"We will call the endpoint $ApiEndpoint1 with exists operationId", ApiEndpoint1, VersionOfApi) {
      addOneAuthenticationTypeValidation(allowedDirectLogin, mockOperationId)

      When("We make a request v4.0.0")
      grantEntitlement(canCreateAuthenticationTypeValidation)
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" / mockOperationId).POST <@ user1
      val response = makePostRequest(request, allowedDirectLogin)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(OperationIdExistsError)
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 with not exists operationId", ApiEndpoint2, VersionOfApi) {
      grantEntitlement(canUpdateAuthenticationTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).PUT <@ user1
      val response= makePutRequest(request, allowedDirectLogin)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthenticationTypeValidationNotFound)
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 with required role", ApiEndpoint3, VersionOfApi) {
      grantEntitlement(canDeleteAuthenticationValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).DELETE <@ user1
      val response = makeDeleteRequest(request)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthenticationTypeValidationNotFound)
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 with required role", ApiEndpoint4, VersionOfApi) {
      grantEntitlement(canGetAuthenticationTypeValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "authentication-type-validations" /  mockOperationId).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthenticationTypeValidationNotFound)
    }

  }


  feature(s"test AuthenticationTypeValidation endpoints version $VersionOfApi - Validate static endpoint request body") {
    scenario(s"We will call the endpoint $ApiEndpointCreateFx with invalid Fx", VersionOfApi) {
      addOneAuthenticationTypeValidation(allowedDirectLogin, "OBPv2.2.0-createFx")
      grantEntitlement(canCreateFxRate, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId /  "fx").PUT <@ user1
      val response= makePutRequest(request, newFx)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthenticationTypeIllegal)
      message should include("allowed authentication types: [DirectLogin]")
      message should include("current request auth type: OAuth1.0a")
    }

    scenario(s"We will call the endpoint $ApiEndpointCreateFx with valid Fx", VersionOfApi) {
      addOneAuthenticationTypeValidation(allowedAll, "OBPv2.2.0-createFx")
      grantEntitlement(canCreateFxRate, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId /  "fx").PUT <@ user1
      val response= makePutRequest(request, newFx)
      Then("We should get a 201")
      response.code should equal(201)
    }

  }

  feature(s"test AuthenticationTypeValidation endpoints version $VersionOfApi - Validate dynamic entity endpoint request body") {
    scenario(s"We will call the endpoint $ApiEndpoint1 with invalid FooBar", ApiEndpoint1, VersionOfApi) {
      addOneAuthenticationTypeValidation(allowedDirectLogin, "OBPv4.0.0-dynamicEntity_createFooBar")
      addDynamicEntity()
      addStringEntitlement("CanCreateDynamicEntity_FooBar", bankId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId /  "FooBar").POST <@ user1
      val response= makePostRequest(request, newFooBar)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthenticationTypeIllegal)
      message should include("allowed authentication types: [DirectLogin]")
      message should include("current request auth type: OAuth1.0a")
    }

    scenario(s"We will call the endpoint $ApiEndpoint1 with valid FooBar", ApiEndpoint1, VersionOfApi) {
      addOneAuthenticationTypeValidation(allowedAll, "OBPv4.0.0-dynamicEntity_createFooBar")
      addDynamicEntity()
      addStringEntitlement("CanCreateDynamicEntity_FooBar", bankId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId /  "FooBar").POST <@ user1
      val response= makePostRequest(request, newFooBar)
      Then("We should get a 201")
      response.code should equal(201)
    }

  }

  feature(s"test AuthenticationTypeValidation endpoints version $VersionOfApi - Validate dynamic endpoints endpoint request body") {
    scenario("We will call the endpoint /dynamic/save with invalid FooBar", VersionOfApi) {
      addOneAuthenticationTypeValidation(allowedDirectLogin, "OBPv4.0.0-dynamicEndpoint_POST_save")
      addDynamicEndpoints()
      addStringEntitlement("CanCreateDynamicEndpoint_User469")

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "dynamic" / "save").POST <@ user1
      val response= makePostRequest(request, newUser)
      Then("We should get a 400")
      response.code should equal(400)
      val authTypeValidation = response.body
      val message = (authTypeValidation \ "message").asInstanceOf[JString].s

      message should include(AuthenticationTypeIllegal)
      message should include("allowed authentication types: [DirectLogin]")
      message should include("current request auth type: OAuth1.0a")
    }

    scenario("We will call the endpoint /dynamic/save with valid FooBar", ApiEndpoint1, VersionOfApi) {
      addOneAuthenticationTypeValidation(allowedAll, "OBPv4.0.0-dynamicEndpoint_POST_save")
      addDynamicEndpoints()
      addStringEntitlement("CanCreateDynamicEndpoint_User469")

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "dynamic" /  "save").POST <@ user1
      val response= makePostRequest(request, newUser)
      Then("We should get a 201")
      response.code should equal(201)
    }

  }

  private def grantEntitlement(role: ApiRole, bankId: String = "") = addStringEntitlement(role.toString, bankId)
  private def addStringEntitlement(role: String, bankId: String = "") = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, role)

  // prepare one AuthenticationTypeValidation for update, delete and get
  private def addOneAuthenticationTypeValidation(allowedAuthTypes: String, operationId: String): APIResponse = {
    grantEntitlement(canCreateAuthenticationTypeValidation)
    val request = (v4_0_0_Request / "management" / "authentication-type-validations" / operationId).POST <@ user1
    val response = makePostRequest(request, allowedAuthTypes)
    response.code should equal(201)

    response
  }
  // prepare one dynamic entity FooBar
  private def addDynamicEntity(): APIResponse = {
    grantEntitlement(canCreateDynamicEntity)
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
    grantEntitlement(canCreateDynamicEndpoint)
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
