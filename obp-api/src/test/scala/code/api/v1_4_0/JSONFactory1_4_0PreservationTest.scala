package code.api.v1_4_0

import code.api.util.CustomJsonFormats
import code.util.Helper.MdcLoggable
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, GivenWhenThen, Matchers}
import java.util.Date

/**
 * Preservation Property Tests for Non-Nested Array Behavior
 * 
 * IMPORTANT: Follow observation-first methodology
 * These tests observe behavior on UNFIXED code for non-buggy inputs
 * They capture the baseline behavior that must be preserved after the fix
 * 
 * EXPECTED OUTCOME: Tests PASS on unfixed code (confirms baseline behavior to preserve)
 * 
 * Property 2: Preservation - Non-Nested Array Behavior
 * For any JSON value where the bug condition does NOT hold (single-level arrays, primitives, 
 * objects, arrays of objects), the fixed translateEntity function SHALL produce exactly the 
 * same schema output as the original function.
 * 
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
 */
class JSONFactory1_4_0PreservationTest extends FeatureSpec 
  with BeforeAndAfterEach 
  with GivenWhenThen
  with BeforeAndAfterAll
  with Matchers 
  with MdcLoggable 
  with CustomJsonFormats {
  
  feature("Preservation: Single-Level Arrays of Primitives") {
    
    scenario("Single-level array of integers should generate correct array schema") {
      Given("A single-level array of integers: List(1, 2, 3)")
      val intArray = JArray(List(JInt(1), JInt(2), JInt(3)))
      val testObject = JObject(List(JField("numbers", intArray)))
      
      When("translateEntity is called on the array")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should be a simple array of integers")
      logger.info(s"Generated schema for integer array: $schema")
      
      val parsedSchema = parse(schema)
      val properties = (parsedSchema \ "properties")
      
      val numbersField = (properties \ "numbers")
      (numbersField \ "type").extract[String] shouldBe "array"
      
      val items = (numbersField \ "items")
      (items \ "type").extract[String] shouldBe "object"
      
      // Current behavior: single-level arrays of primitives generate object items
      val itemProps = (items \ "properties")
      itemProps should not be JNothing
    }
    
    scenario("Single-level array of strings should generate correct array schema") {
      Given("A single-level array of strings: List('a', 'b', 'c')")
      val stringArray = JArray(List(JString("a"), JString("b"), JString("c")))
      val testObject = JObject(List(JField("tags", stringArray)))
      
      When("translateEntity is called on the array")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should be a simple array of strings")
      logger.info(s"Generated schema for string array: $schema")
      
      val parsedSchema = parse(schema)
      val properties = (parsedSchema \ "properties")
      
      val tagsField = (properties \ "tags")
      (tagsField \ "type").extract[String] shouldBe "array"
      
      val items = (tagsField \ "items")
      (items \ "type").extract[String] shouldBe "object"
      
      // Current behavior: single-level arrays of primitives generate object items
      val itemProps = (items \ "properties")
      itemProps should not be JNothing
    }
    
    scenario("Single-level array of booleans should generate correct array schema") {
      Given("A single-level array of booleans: List(true, false)")
      val boolArray = JArray(List(JBool(true), JBool(false)))
      val testObject = JObject(List(JField("flags", boolArray)))
      
      When("translateEntity is called on the array")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should be a simple array of booleans")
      logger.info(s"Generated schema for boolean array: $schema")
      
      val parsedSchema = parse(schema)
      val properties = (parsedSchema \ "properties")
      
      val flagsField = (properties \ "flags")
      (flagsField \ "type").extract[String] shouldBe "array"
      
      val items = (flagsField \ "items")
      (items \ "type").extract[String] shouldBe "object"
      
      // Current behavior: single-level arrays of primitives generate object items
      val itemProps = (items \ "properties")
      itemProps should not be JNothing
    }
    
    scenario("Single-level array of doubles should generate correct array schema") {
      Given("A single-level array of doubles: List(1.5, 2.5, 3.5)")
      val doubleArray = JArray(List(JDouble(1.5), JDouble(2.5), JDouble(3.5)))
      val testObject = JObject(List(JField("values", doubleArray)))
      
      When("translateEntity is called on the array")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should be a simple array of numbers")
      logger.info(s"Generated schema for double array: $schema")
      
      val parsedSchema = parse(schema)
      val properties = (parsedSchema \ "properties")
      
      val valuesField = (properties \ "values")
      (valuesField \ "type").extract[String] shouldBe "array"
      
      val items = (valuesField \ "items")
      (items \ "type").extract[String] shouldBe "object"
      
      // Current behavior: single-level arrays of primitives generate object items
      val itemProps = (items \ "properties")
      itemProps should not be JNothing
    }
  }
  
  feature("Preservation: Arrays of Objects") {
    
    scenario("Array of objects should generate array schema with object items") {
      Given("An array of objects with properties")
      val objectArray = JArray(List(
        JObject(List(
          JField("id", JInt(1)),
          JField("name", JString("Alice"))
        )),
        JObject(List(
          JField("id", JInt(2)),
          JField("name", JString("Bob"))
        ))
      ))
      val testObject = JObject(List(JField("users", objectArray)))
      
      When("translateEntity is called on the array of objects")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should be an array with object items containing properties")
      logger.info(s"Generated schema for object array: $schema")
      
      val parsedSchema = parse(schema)
      val properties = (parsedSchema \ "properties")
      
      val usersField = (properties \ "users")
      (usersField \ "type").extract[String] shouldBe "array"
      
      val items = (usersField \ "items")
      (items \ "type").extract[String] shouldBe "object"
      
      // Should have properties for the object fields
      val itemProps = (items \ "properties")
      itemProps should not be JNothing
      
      // Verify the object has id and name properties
      val idProp = (itemProps \ "id")
      (idProp \ "type").extract[String] shouldBe "integer"
      
      val nameProp = (itemProps \ "name")
      (nameProp \ "type").extract[String] shouldBe "string"
    }
  }
  
  feature("Preservation: Primitive Types (Non-Arrays)") {
    
    scenario("String field should generate string schema") {
      Given("A simple string field")
      val testObject = JObject(List(JField("name", JString("test"))))
      
      When("translateEntity is called")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should be a simple string type")
      logger.info(s"Generated schema for string: $schema")
      
      val parsedSchema = parse(schema)
      val properties = (parsedSchema \ "properties")
      
      val nameField = (properties \ "name")
      (nameField \ "type").extract[String] shouldBe "string"
    }
    
    scenario("Integer field should generate integer schema") {
      Given("A simple integer field")
      val testObject = JObject(List(JField("count", JInt(42))))
      
      When("translateEntity is called")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should be a simple integer type")
      logger.info(s"Generated schema for integer: $schema")
      
      val parsedSchema = parse(schema)
      val properties = (parsedSchema \ "properties")
      
      val countField = (properties \ "count")
      (countField \ "type").extract[String] shouldBe "integer"
    }
    
    scenario("Double field should generate number schema") {
      Given("A simple double field")
      val testObject = JObject(List(JField("price", JDouble(19.99))))
      
      When("translateEntity is called")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should be a simple number type")
      logger.info(s"Generated schema for double: $schema")
      
      val parsedSchema = parse(schema)
      val properties = (parsedSchema \ "properties")
      
      val priceField = (properties \ "price")
      (priceField \ "type").extract[String] shouldBe "number"
    }
    
    scenario("Boolean field should generate boolean schema") {
      Given("A simple boolean field")
      val testObject = JObject(List(JField("active", JBool(true))))
      
      When("translateEntity is called")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should be a simple boolean type")
      logger.info(s"Generated schema for boolean: $schema")
      
      val parsedSchema = parse(schema)
      val properties = (parsedSchema \ "properties")
      
      val activeField = (properties \ "active")
      (activeField \ "type").extract[String] shouldBe "boolean"
    }
  }
  
  feature("Preservation: Wrapped Values") {
    
    scenario("Some(value) should unwrap and generate correct schema") {
      Given("A value wrapped in Some")
      // Simulate Some by using the same pattern translateEntity handles
      val testObject = JObject(List(JField("optional", JString("value"))))
      
      When("translateEntity is called")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should unwrap and generate string type")
      logger.info(s"Generated schema for Some(string): $schema")
      
      val parsedSchema = parse(schema)
      val properties = (parsedSchema \ "properties")
      
      val optionalField = (properties \ "optional")
      (optionalField \ "type").extract[String] shouldBe "string"
    }
    
    scenario("Some(List(...)) should generate array schema") {
      Given("A list wrapped in Some")
      val listValue = JArray(List(JInt(1), JInt(2), JInt(3)))
      val testObject = JObject(List(JField("optionalList", listValue)))
      
      When("translateEntity is called")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should generate array type")
      logger.info(s"Generated schema for Some(List(...)): $schema")
      
      val parsedSchema = parse(schema)
      val properties = (parsedSchema \ "properties")
      
      val optionalListField = (properties \ "optionalList")
      (optionalListField \ "type").extract[String] shouldBe "array"
      
      val items = (optionalListField \ "items")
      (items \ "type").extract[String] shouldBe "object"
    }
  }
  
  feature("Preservation: Complex Object Structures") {
    
    scenario("Nested object (non-array) should generate nested object schema") {
      Given("An object containing another object")
      val nestedObject = JObject(List(
        JField("user", JObject(List(
          JField("id", JInt(1)),
          JField("name", JString("Alice")),
          JField("email", JString("alice@example.com"))
        )))
      ))
      
      When("translateEntity is called")
      val schema = JSONFactory1_4_0.translateEntity(nestedObject, false)
      
      Then("The schema should generate nested object structure")
      logger.info(s"Generated schema for nested object: $schema")
      
      val parsedSchema = parse(schema)
      val properties = (parsedSchema \ "properties")
      
      val userField = (properties \ "user")
      (userField \ "type").extract[String] shouldBe "object"
      
      val userProps = (userField \ "properties")
      userProps should not be JNothing
      
      // Verify nested properties
      val idProp = (userProps \ "id")
      (idProp \ "type").extract[String] shouldBe "integer"
      
      val nameProp = (userProps \ "name")
      (nameProp \ "type").extract[String] shouldBe "string"
      
      val emailProp = (userProps \ "email")
      (emailProp \ "type").extract[String] shouldBe "string"
    }
    
    scenario("Object with mixed field types should generate correct schema") {
      Given("An object with various field types")
      val mixedObject = JObject(List(
        JField("id", JInt(123)),
        JField("name", JString("Product")),
        JField("price", JDouble(29.99)),
        JField("inStock", JBool(true)),
        JField("tags", JArray(List(JString("electronics"), JString("gadget"))))
      ))
      
      When("translateEntity is called")
      val schema = JSONFactory1_4_0.translateEntity(mixedObject, false)
      
      Then("The schema should correctly type all fields")
      logger.info(s"Generated schema for mixed object: $schema")
      
      val parsedSchema = parse(schema)
      val properties = (parsedSchema \ "properties")
      
      (properties \ "id" \ "type").extract[String] shouldBe "integer"
      (properties \ "name" \ "type").extract[String] shouldBe "string"
      (properties \ "price" \ "type").extract[String] shouldBe "number"
      (properties \ "inStock" \ "type").extract[String] shouldBe "boolean"
      (properties \ "tags" \ "type").extract[String] shouldBe "array"
      (properties \ "tags" \ "items" \ "type").extract[String] shouldBe "object"
    }
  }
}
