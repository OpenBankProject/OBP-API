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

class JsonSchemaValidationTest extends V400ServerSetup {
  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createJsonSchemaValidation))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateJsonSchemaValidation))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.deleteJsonSchemaValidation))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getJsonSchemaValidation))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getAllJsonSchemaValidations))
  object ApiEndpoint6 extends Tag(nameOf(Implementations4_0_0.getAllJsonSchemaValidationsPublic))

  object ApiEndpointCreateFx extends Tag(nameOf(Implementations2_2_0.createFx))

  lazy val bankId = randomBankId
  private val mockOperationId = "MOCK_OPERATION_ID"

  feature(s"test JSON Schema Validation endpoints version $VersionOfApi - Unauthenticated access") {
    scenario(s"We will call the endpoint $ApiEndpoint1 without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).POST
      val response= makePostRequest(request, jsonSchemaFooBar)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).PUT
      val response= makePutRequest(request, jsonSchemaFooBar)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).DELETE
      val response= makeDeleteRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).GET
      val response= makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario(s"We will call the endpoint $ApiEndpoint5 without user credentials", ApiEndpoint5, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" ).GET
      val response= makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test JSON Schema Validation endpoints version $VersionOfApi - Unauthorized access") {
    scenario(s"We will call the endpoint $ApiEndpoint1 without required role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).POST <@ user1
      val response= makePostRequest(request, jsonSchemaFooBar)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canCreateJsonSchemaValidation")
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 without required role", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).PUT <@ user1
      val response= makePutRequest(request, jsonSchemaFooBar)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canUpdateJsonSchemaValidation")
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 without required role", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).DELETE <@ user1
      val response= makeDeleteRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canDeleteJsonSchemaValidation")
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 without required role", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canGetJsonSchemaValidation")
    }

    scenario(s"We will call the endpoint $ApiEndpoint5 without required role", ApiEndpoint5, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" ).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$canGetJsonSchemaValidation")
    }
  }

  feature(s"test JSON Schema Validation endpoints version $VersionOfApi - Authorized access") {
    scenario(s"We will call the endpoint $ApiEndpoint1 with required role", ApiEndpoint1, VersionOfApi) {
      addEntitlement(canCreateJsonSchemaValidation)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).POST <@ user1
      val response= makePostRequest(request, jsonSchemaFooBar)
      Then("We should get a 201")
      response.code should equal(201)
      val validation = response.body
      validation \ "operation_id" should equal (JString(mockOperationId))
      validation \ "json_schema" should equal (json.parse(jsonSchemaFooBar))
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 with required role", ApiEndpoint2, VersionOfApi) {
      addOneValidation(jsonSchemaFooBar, mockOperationId)
      addEntitlement(canUpdateJsonSchemaValidation)
      // change the root.title to " This is a new Title "
      val newJsonSchema = jsonSchemaFooBar.replaceFirst("""("title":\s*")[^"]+("\s*,)""", "$1 This is a new Title $2")

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).PUT <@ user1
      val response= makePutRequest(request, newJsonSchema)
      Then("We should get a 200")
      response.code should equal(200)
      val validation = response.body
      validation \ "operation_id" should equal (JString(mockOperationId))
      validation \ "json_schema" should equal (json.parse(newJsonSchema))
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 with required role", ApiEndpoint3, VersionOfApi) {
      addOneValidation(jsonSchemaFooBar, mockOperationId)
      addEntitlement(canDeleteJsonSchemaValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).DELETE <@ user1
      val response= makeDeleteRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      response.body should equal(JBool(true))
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 with required role", ApiEndpoint4, VersionOfApi) {
      addOneValidation(jsonSchemaFooBar, mockOperationId)
      addEntitlement(canGetJsonSchemaValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      val validation = response.body
      validation \ "operation_id" should equal (JString(mockOperationId))
      validation \ "json_schema" should equal (json.parse(jsonSchemaFooBar))
    }

    scenario(s"We will call the endpoint $ApiEndpoint5 with required role", ApiEndpoint5, VersionOfApi) {
      addOneValidation(jsonSchemaFooBar, mockOperationId)
      addEntitlement(canGetJsonSchemaValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" ).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      val validations = response.body \ "json_schema_validations"
      validations shouldBe a [JArray]

      val validation = validations(0)
      validation \ "operation_id" should equal (JString(mockOperationId))
      validation \ "json_schema" should equal (json.parse(jsonSchemaFooBar))
    }

    scenario(s"We will call the endpoint $ApiEndpoint6 anonymously", ApiEndpoint6, VersionOfApi) {
      addOneValidation(jsonSchemaFooBar, mockOperationId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "endpoints" / "json-schema-validations" ).GET
      val response= makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      val validations = response.body \ "json_schema_validations"
      validations shouldBe a [JArray]

      val validation = validations(0)
      validation \ "operation_id" should equal (JString(mockOperationId))
      validation \ "json_schema" should equal (json.parse(jsonSchemaFooBar))
    }
  }

  feature(s"test JSON Schema Validation endpoints version $VersionOfApi - Wrong request") {
    scenario(s"We will call the endpoint $ApiEndpoint1 with wrong format json-schema", ApiEndpoint1, VersionOfApi) {
      addEntitlement(canCreateJsonSchemaValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).POST <@ user1
      val response= makePostRequest(request, """{"name": "wrong json-schema"}""")
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(JsonSchemaIllegal)
      message should include("$.$schema: is missing but it is required")
    }

    scenario(s"We will call the endpoint $ApiEndpoint1 with exists operationId", ApiEndpoint1, VersionOfApi) {
      addOneValidation(jsonSchemaFooBar, mockOperationId)

      When("We make a request v4.0.0")
      addEntitlement(canCreateJsonSchemaValidation)
      val request = (v4_0_0_Request / "management" / "json-schema-validations" / mockOperationId).POST <@ user1
      val response = makePostRequest(request, jsonSchemaFooBar)
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(OperationIdExistsError)
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 with not exists operationId", ApiEndpoint2, VersionOfApi) {
      addEntitlement(canUpdateJsonSchemaValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).PUT <@ user1
      val response= makePutRequest(request, jsonSchemaFooBar)
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(JsonSchemaValidationNotFound)
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 with required role", ApiEndpoint3, VersionOfApi) {
      addEntitlement(canDeleteJsonSchemaValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).DELETE <@ user1
      val response = makeDeleteRequest(request)
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(JsonSchemaValidationNotFound)
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 with required role", ApiEndpoint4, VersionOfApi) {
      addEntitlement(canGetJsonSchemaValidation)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "json-schema-validations" /  mockOperationId).GET <@ user1
      val response= makeGetRequest(request)
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(JsonSchemaValidationNotFound)
    }

  }


  feature(s"test JSON Schema Validation endpoints version $VersionOfApi - Validate static endpoint request body") {
    scenario(s"We will call the endpoint $ApiEndpointCreateFx with invalid Fx", VersionOfApi) {
      addOneValidation(jsonSchemaCreateFx, "OBPv2.2.0-createFx")
      addEntitlement(canCreateFxRate, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId /  "fx").PUT <@ user1
      val response= makePutRequest(request, wrongFx)
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(InvalidRequestPayload)
      message should include("$.from_currency_code: does not have a value in the enumeration [EUR, USD]")
      message should include("$.to_currency_code: does not have a value in the enumeration [EUR, USD]")
    }

    scenario(s"We will call the endpoint $ApiEndpointCreateFx with valid Fx", VersionOfApi) {
      addOneValidation(jsonSchemaCreateFx, "OBPv2.2.0-createFx")
      addEntitlement(canCreateFxRate, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId /  "fx").PUT <@ user1
      val response= makePutRequest(request, correctFx)
      Then("We should get a 201")
      response.code should equal(201)
    }

  }

  feature(s"test JSON Schema Validation endpoints version $VersionOfApi - Validate dynamic entity endpoint request body") {
    scenario(s"We will call the endpoint $ApiEndpoint1 with invalid FooBar", ApiEndpoint1, VersionOfApi) {
      addOneValidation(jsonSchemaFooBar, "OBPv4.0.0-dynamicEntity_createFooBar")
      addDynamicEntity()
      addStringEntitlement("CanCreateDynamicEntity_FooBar", bankId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId /  "FooBar").POST <@ user1
      val response= makePostRequest(request, wrongFooBar)
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(InvalidRequestPayload)
      message should include("$.number: must have a minimum value of 10")
    }

    scenario(s"We will call the endpoint $ApiEndpoint1 with valid FooBar", ApiEndpoint1, VersionOfApi) {
      addOneValidation(jsonSchemaFooBar, "OBPv4.0.0-dynamicEntity_createFooBar")
      addDynamicEntity()
      addStringEntitlement("CanCreateDynamicEntity_FooBar", bankId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId /  "FooBar").POST <@ user1
      val response= makePostRequest(request, correctFooBar)
      Then("We should get a 201")
      response.code should equal(201)
    }

  }

  feature(s"test JSON Schema Validation endpoints version $VersionOfApi - Validate dynamic endpoints endpoint request body") {
    scenario("We will call the endpoint /dynamic/save with invalid FooBar", VersionOfApi) {
      addOneValidation(jsonSchemaDynamicEndpoint, "OBPv4.0.0-dynamicEndpoint_POST_save")
      addDynamicEndpoints()
      addStringEntitlement("CanCreateDynamicEndpoint_User469")

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "dynamic" / "save").POST <@ user1
      val response= makePostRequest(request, wrongUser)
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(InvalidRequestPayload)
      message should include("$.id: string found, integer expected")
      message should include("$.first_name: does not match the regex pattern [A-Z]\\w+")
      message should include("$.age: must have a maximum value of 150")
    }

    scenario("We will call the endpoint /dynamic/save with valid FooBar", ApiEndpoint1, VersionOfApi) {
      addOneValidation(jsonSchemaDynamicEndpoint, "OBPv4.0.0-dynamicEndpoint_POST_save")
      addDynamicEndpoints()
      addStringEntitlement("CanCreateDynamicEndpoint_User469")

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "dynamic" /  "save").POST <@ user1
      val response= makePostRequest(request, correctUser)
      Then("We should get a 201")
      response.code should equal(201)
    }

  }

  private def addEntitlement(role: ApiRole, bankId: String = "") = addStringEntitlement(role.toString, bankId)
  private def addStringEntitlement(role: String, bankId: String = "") = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, role)

  // prepare one JSON Schema Validation for update, delete and get
  private def addOneValidation(schema: String, operationId: String): APIResponse = {
    addEntitlement(canCreateJsonSchemaValidation)
    val request = (v4_0_0_Request / "management" / "json-schema-validations" / operationId).POST <@ user1
    val response = makePostRequest(request, schema)
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

  private val jsonSchemaCreateFx =
    """
      |{
      |    "$schema":"http://json-schema.org/draft-07/schema",
      |    "$id":"http://example.com/example.json",
      |    "type":"object",
      |    "title":"The root schema",
      |    "description":"The root schema comprises the entire JSON document.",
      |    "examples":[{
      |      "bank_id":"gh.29.uk",
      |      "from_currency_code":"EUR",
      |      "to_currency_code":"USD",
      |      "conversion_value":1.136305,
      |      "inverse_conversion_value":0.8800454103431737,
      |      "effective_date":"2017-09-19T00:00:00Z"
      |    }],
      |    "required":["bank_id","from_currency_code","to_currency_code"],
      |    "properties":{
      |      "bank_id":{
      |        "type":"string"
      |      },
      |      "from_currency_code":{
      |        "enum":["EUR","USD"],
      |        "type":"string"
      |      },
      |      "to_currency_code":{
      |        "enum":["EUR","USD"],
      |        "type":"string"
      |      }
      |    },
      |    "additionalProperties":true
      |  }
      |""".stripMargin

  private val wrongFx =
    """
      |{
      |    "bank_id": "gh.29.uk",
      |    "from_currency_code": "ABC",
      |    "to_currency_code": "DEF",
      |    "conversion_value": 1.136305,
      |    "inverse_conversion_value": 0.8800454103431737,
      |    "effective_date": "2017-09-19T00:00:00Z"
      |}
      |""".stripMargin

  private val correctFx =
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

  private val wrongFooBar = """{  "name":"James Brown",  "number":8}"""
  private val correctFooBar = """{  "name":"James Brown",  "number":200}"""

  private val swagger =
    """
      |{
      |    "swagger": "2.0",
      |    "info": {
      |        "version": "0.0.1",
      |        "title": "User Infomation for json-schema validation",
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

  private val jsonSchemaDynamicEndpoint =
    """
      |{
      |    "$schema": "http://json-schema.org/draft-07/schema",
      |    "$id": "http://example.com/example.json",
      |    "type": "object",
      |    "title": "The root schema",
      |    "description": "The root schema comprises the entire JSON document.",
      |    "examples": [
      |        {
      |            "id": 1,
      |            "first_name": "string",
      |            "last_name": "string",
      |            "age": 1
      |        }
      |    ],
      |    "required": [
      |        "id",
      |        "first_name",
      |        "last_name",
      |        "age",
      |        "career"
      |    ],
      |    "properties": {
      |        "id": {
      |            "type": "integer"
      |        },
      |        "first_name": {
      |            "pattern": "[A-Z]\\w+",
      |            "type": "string"
      |        },
      |        "last_name": {
      |            "type": "string"
      |        },
      |        "age": {
      |            "maximum": 150,
      |            "minimum": 1,
      |            "type": "integer"
      |        },
      |        "career": {
      |            "type": "string"
      |        }
      |    },
      |    "additionalProperties": true
      |}
      |""".stripMargin

  private val wrongUser =
    """
      |{
      |    "id": "wrong_id",
      |    "first_name": "xx",
      |    "last_name": "dd",
      |    "age": 200,
      |    "career": "developer"
      |}""".stripMargin

  private val correctUser =
    """
      |{
      |    "id": 111,
      |    "first_name": "Robert",
      |    "last_name": "Li",
      |    "age": 10,
      |    "career": "developer"
      |}""".stripMargin

}
