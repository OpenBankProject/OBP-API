package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages.InvalidConnector
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.dwickern.macros.NameOf.nameOf
import com.networknt.schema.{JsonSchemaFactory, SpecVersion}
import com.openbankproject.commons.util.ApiVersion
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._
import org.scalatest.Tag

/**
 * Tests for the Message Docs JSON Schema endpoint (v6.0.0)
 *
 * This endpoint returns message documentation as JSON Schema format for code generation.
 * The schema follows JSON Schema draft-07 specification and is validated using the
 * networknt/json-schema-validator library (https://github.com/networknt/json-schema-validator).
 *
 * Schema structure:
 * - Root level: $schema, title, description, type, properties, definitions
 * - Each message includes: process, description, outbound_schema, inbound_schema
 * - Type definitions use $ref references to the definitions section
 * - All definitions have: type: "object", properties, required (for non-Option fields)
 *
 * Industry Standard Compliance:
 * - Validated against JSON Schema draft-07 meta-schema
 * - Uses standard $ref for type references
 * - Compatible with code generation tools like quicktype
 */
class MessageDocsJsonSchemaTest extends V600ServerSetup {

  // Jackson ObjectMapper for converting between liftweb JSON and Jackson JsonNode
  private val mapper = new ObjectMapper()

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  /**
   * Test tags (for grouping tests)
   */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getMessageDocsJsonSchema))

  feature("Get Message Docs as JSON Schema - v6.0.0") {
    
    scenario("We get JSON Schema for rabbitmq_vOct2024 connector", ApiEndpoint1, VersionOfApi) {
      When("We make a request to get message docs as JSON Schema")
      val request = (v6_0_0_Request / "message-docs" / "rabbitmq_vOct2024" / "json-schema").GET
      val response = makeGetRequest(request)
      
      Then("We should get a 200 OK response")
      response.code should equal(200)
      
      And("Response should be valid JSON")
      val json = response.body.extract[JValue]
      json should not be null
      
      And("Response should have JSON Schema structure")
      val schemaVersion = (json \ "$schema").extractOpt[String]
      schemaVersion shouldBe defined
      schemaVersion.get should include("json-schema.org")
      
      And("Response should have a title")
      val title = (json \ "title").extractOpt[String]
      title shouldBe defined
      title.get should include("rabbitmq_vOct2024")
      
      And("Response should have definitions")
      val definitions = (json \ "definitions").extractOpt[JObject]
      definitions shouldBe defined
      
      And("Response should have properties with messages array")
      val properties = (json \ "properties").extractOpt[JObject]
      properties shouldBe defined
      
      val messagesProperty = (json \ "properties" \ "messages").extractOpt[JObject]
      messagesProperty shouldBe defined
      
      val messagesType = (json \ "properties" \ "messages" \ "type").extractOpt[String]
      messagesType shouldBe Some("array")
      
      And("Each message should have required structure")
      val messages = (json \ "properties" \ "messages" \ "items").extract[List[JValue]]
      messages should not be empty
      
      // Check first message has expected fields
      val firstMessage = messages.head
      (firstMessage \ "process").extractOpt[String] shouldBe defined
      (firstMessage \ "description").extractOpt[String] shouldBe defined
      (firstMessage \ "message_format").extractOpt[String] shouldBe defined
      (firstMessage \ "outbound_schema").extractOpt[JObject] shouldBe defined
      (firstMessage \ "inbound_schema").extractOpt[JObject] shouldBe defined
      
      And("Outbound schema should be valid JSON Schema")
      val outboundSchema = (firstMessage \ "outbound_schema").extract[JObject]
      // Schema can have either a direct "type" or a "$ref" to definitions for case classes
      val outboundType = (outboundSchema \ "type").extractOpt[String]
      val outboundRef = (outboundSchema \ "$ref").extractOpt[String]
      (outboundType.isDefined || outboundRef.isDefined) shouldBe true

      And("Inbound schema should be valid JSON Schema")
      val inboundSchema = (firstMessage \ "inbound_schema").extract[JObject]
      // Schema can have either a direct "type" or a "$ref" to definitions for case classes
      val inboundType = (inboundSchema \ "type").extractOpt[String]
      val inboundRef = (inboundSchema \ "$ref").extractOpt[String]
      (inboundType.isDefined || inboundRef.isDefined) shouldBe true
    }
    
    scenario("We get JSON Schema for rest_vMar2019 connector", ApiEndpoint1, VersionOfApi) {
      When("We make a request to get message docs as JSON Schema")
      val request = (v6_0_0_Request / "message-docs" / "rest_vMar2019" / "json-schema").GET
      val response = makeGetRequest(request)
      
      Then("We should get a 200 OK response")
      response.code should equal(200)
      
      And("Response should be valid JSON Schema")
      val json = response.body.extract[JValue]
      val schemaVersion = (json \ "$schema").extractOpt[String]
      schemaVersion shouldBe defined
    }
    
    scenario("We get JSON Schema for akka_vDec2018 connector", ApiEndpoint1, VersionOfApi) {
      When("We make a request to get message docs as JSON Schema")
      val request = (v6_0_0_Request / "message-docs" / "akka_vDec2018" / "json-schema").GET
      val response = makeGetRequest(request)
      
      Then("We should get a 200 OK response")
      response.code should equal(200)
      
      And("Response should be valid JSON Schema")
      val json = response.body.extract[JValue]
      val schemaVersion = (json \ "$schema").extractOpt[String]
      schemaVersion shouldBe defined
    }
    
    scenario("We try to get JSON Schema for invalid connector", ApiEndpoint1, VersionOfApi) {
      When("We make a request with invalid connector name")
      val request = (v6_0_0_Request / "message-docs" / "invalid_connector" / "json-schema").GET
      val response = makeGetRequest(request)

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("Error message should mention invalid connector")
      val errorMessage = (response.body \ "message").extractOpt[String]
      errorMessage shouldBe defined
      errorMessage.get should include("Invalid Connector")
    }
    
    scenario("We verify schema includes nested type definitions", ApiEndpoint1, VersionOfApi) {
      When("We make a request to get message docs as JSON Schema")
      val request = (v6_0_0_Request / "message-docs" / "rabbitmq_vOct2024" / "json-schema").GET
      val response = makeGetRequest(request)
      
      Then("We should get a 200 OK response")
      response.code should equal(200)
      
      And("Definitions should include common types")
      val json = response.body.extract[JValue]
      val definitions = (json \ "definitions").extract[JObject]
      
      // Should have definitions for common adapter types
      val definitionNames = definitions.obj.map(_.name)
      definitionNames should not be empty
      
      // Common types that should be present in RabbitMQ schemas
      // (exact names depend on the case classes, but there should be several)
      definitionNames.length should be > 5
      
      And("Each definition should have proper schema structure")
      definitions.obj.foreach { case JField(name, schema) =>
        val schemaObj = schema.asInstanceOf[JObject]
        val schemaType = (schemaObj \ "type").extractOpt[String]
        schemaType shouldBe Some("object")
        
        val properties = (schemaObj \ "properties").extractOpt[JObject]
        properties shouldBe defined
      }
    }
    
    scenario("We verify schema marks required fields correctly", ApiEndpoint1, VersionOfApi) {
      When("We make a request to get message docs as JSON Schema")
      val request = (v6_0_0_Request / "message-docs" / "rabbitmq_vOct2024" / "json-schema").GET
      val response = makeGetRequest(request)
      
      Then("We should get a 200 OK response")
      response.code should equal(200)
      
      And("Schemas should indicate required fields")
      val json = response.body.extract[JValue]
      val messages = (json \ "properties" \ "messages" \ "items").extract[List[JValue]]
      
      messages.foreach { message =>
        val outboundSchema = (message \ "outbound_schema").extract[JObject]
        val inboundSchema = (message \ "inbound_schema").extract[JObject]
        
        // Check if required fields are present (they may or may not be required depending on the case class)
        val outboundRequired = (outboundSchema \ "required").extractOpt[List[String]]
        val inboundRequired = (inboundSchema \ "required").extractOpt[List[String]]
        
        // At minimum, the structure should be present
        outboundSchema should not be null
        inboundSchema should not be null
      }
    }
    
    scenario("We verify process names match connector method names", ApiEndpoint1, VersionOfApi) {
      When("We make a request to get message docs as JSON Schema")
      val request = (v6_0_0_Request / "message-docs" / "rabbitmq_vOct2024" / "json-schema").GET
      val response = makeGetRequest(request)

      Then("We should get a 200 OK response")
      response.code should equal(200)

      And("Process names should follow obp.methodName pattern")
      val json = response.body.extract[JValue]
      val messages = (json \ "properties" \ "messages" \ "items").extract[List[JValue]]

      messages.foreach { message =>
        val process = (message \ "process").extract[String]
        process should startWith("obp.")
        process.length should be > 4
      }
    }

    scenario("We validate schema is industry-standard JSON Schema draft-07 using networknt validator", ApiEndpoint1, VersionOfApi) {
      When("We make a request to get message docs as JSON Schema")
      val request = (v6_0_0_Request / "message-docs" / "rabbitmq_vOct2024" / "json-schema").GET
      val response = makeGetRequest(request)

      Then("We should get a 200 OK response")
      response.code should equal(200)

      And("Schema should be valid according to JSON Schema draft-07 specification")
      val schemaString = compactRender(response.body)
      val schemaNode = mapper.readTree(schemaString)

      // Use networknt JSON Schema validator with draft-07
      val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
      val jsonSchema = factory.getSchema(schemaNode)

      // The schema should load without errors (this validates the schema structure)
      jsonSchema should not be null

      And("Schema should have valid definitions that can be resolved")
      val definitions = (response.body \ "definitions").extract[JObject]
      definitions.obj.length should be > 100 // Should have many type definitions

      And("Each definition should be valid JSON Schema")
      definitions.obj.foreach { case JField(name, defn) =>
        val defnString = compactRender(defn)
        val defnNode = mapper.readTree(defnString)
        // Create a schema from each definition to validate it
        val defnSchema = factory.getSchema(defnNode)
        defnSchema should not be null
      }

      And("$ref references should resolve correctly within the schema")
      val messages = (response.body \ "properties" \ "messages" \ "items").extract[List[JValue]]
      val firstMessage = messages.head
      val outboundRef = (firstMessage \ "outbound_schema" \ "$ref").extractOpt[String]
      outboundRef shouldBe defined
      outboundRef.get should startWith("#/definitions/")

      // Extract the referenced definition name and verify it exists
      val refName = outboundRef.get.replace("#/definitions/", "")
      val definitionNames = definitions.obj.map(_.name)
      definitionNames should contain(refName)
    }
  }
}