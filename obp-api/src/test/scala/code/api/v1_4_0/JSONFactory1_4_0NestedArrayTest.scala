package code.api.v1_4_0

import code.api.util.CustomJsonFormats
import code.util.Helper.MdcLoggable
import net.liftweb.json._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, GivenWhenThen, Matchers}

/**
 * Bug Condition Exploration Test for Nested Array Schema Generation
 * 
 * CRITICAL: This test MUST FAIL on unfixed code - failure confirms the bug exists
 * DO NOT attempt to fix the test or the code when it fails
 * 
 * This test encodes the expected behavior - it will validate the fix when it passes after implementation
 * GOAL: Surface counterexamples that demonstrate the bug exists
 * 
 * Bug Condition: When translateEntity encounters a JArray containing another JArray,
 * it incorrectly generates nested objects with "arr" properties instead of proper nested array schema.
 * 
 * Expected Behavior: Nested arrays should generate {"type": "array", "items": {"type": "array", ...}}
 * without object wrappers.
 */
class JSONFactory1_4_0NestedArrayTest extends FeatureSpec 
  with BeforeAndAfterEach 
  with GivenWhenThen
  with BeforeAndAfterAll
  with Matchers 
  with MdcLoggable 
  with CustomJsonFormats {
  
  feature("Bug Condition: Nested Array Schema Generation") {
    
    scenario("2-level nested array should generate correct nested array schema") {
      Given("A 2-level nested JArray: JArray(List(JArray(List(JInt(42)))))")
      val nestedArray = JArray(List(JArray(List(JInt(42)))))
      val testObject = JObject(List(JField("coordinates", nestedArray)))
      
      When("translateEntity is called on the nested array")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should contain nested array types without object wrappers")
      logger.info(s"Generated schema for 2-level nested array: {$schema}")
      
      // Expected: {"type": "array", "items": {"type": "array", "items": {"type": "integer"}}}
      // Current (buggy): Contains "type": "object" with "properties": {"arr": ...}
      
      // Check that schema does NOT contain the buggy pattern with "arr" property
      schema should not include """"arr":"""
      
      // Check that schema contains proper nested array structure
      schema should include (""""type": "array"""")
      
      // Parse the schema to verify structure
      val parsedSchema = parse(schema)
      
      val coordinatesField = (parsedSchema \ "properties" \ "coordinates")
      (coordinatesField \ "type").extract[String] shouldBe "array"
      
      val itemsLevel1 = (coordinatesField \ "items")
      (itemsLevel1 \ "type").extract[String] shouldBe "array"
      
      // Should NOT contain "properties" with "arr" key
      (itemsLevel1 \ "properties" \ "arr") shouldBe JNothing
      
      val itemsLevel2 = (itemsLevel1 \ "items")
      (itemsLevel2 \ "type").extract[String] shouldBe "integer"
    }
    
    scenario("3-level nested array should generate correct nested array schema") {
      Given("A 3-level nested JArray: JArray(List(JArray(List(JArray(List(JString('value')))))))")
      val nestedArray = JArray(List(JArray(List(JArray(List(JString("value")))))))
      val testObject = JObject(List(JField("data", nestedArray)))
      
      When("translateEntity is called on the nested array")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should contain 3 levels of nested array types")
      logger.info(s"Generated schema for 3-level nested array: {$schema}")
      
      // Check that schema does NOT contain the buggy pattern with "arr" property
      schema should not include """"arr":"""
      
      val parsedSchema = parse(schema)
      
      val dataField = (parsedSchema \ "properties" \ "data")
      (dataField \ "type").extract[String] shouldBe "array"
      
      val itemsLevel1 = (dataField \ "items")
      (itemsLevel1 \ "type").extract[String] shouldBe "array"
      (itemsLevel1 \ "properties" \ "arr") shouldBe JNothing
      
      val itemsLevel2 = (itemsLevel1 \ "items")
      (itemsLevel2 \ "type").extract[String] shouldBe "array"
      (itemsLevel2 \ "properties" \ "arr") shouldBe JNothing
      
      val itemsLevel3 = (itemsLevel2 \ "items")
      (itemsLevel3 \ "type").extract[String] shouldBe "string"
    }
    
    scenario("4-level GeoJSON MultiPolygon coordinates should generate correct nested array schema") {
      Given("A 4-level nested JArray representing GeoJSON MultiPolygon coordinates")
      val coordinates = JArray(List(
        JArray(List(
          JArray(List(
            JArray(List(JDouble(102.0), JDouble(2.0))),
            JArray(List(JDouble(103.0), JDouble(2.0))),
            JArray(List(JDouble(103.0), JDouble(3.0))),
            JArray(List(JDouble(102.0), JDouble(3.0))),
            JArray(List(JDouble(102.0), JDouble(2.0)))
          ))
        ))
      ))
      val testObject = JObject(List(JField("coordinates", coordinates)))
      
      When("translateEntity is called on the GeoJSON coordinates")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should contain 4 levels of nested array types terminating in number")
      logger.info(s"Generated schema for GeoJSON MultiPolygon: {$schema}")
      
      // Check that schema does NOT contain the buggy pattern with "arr" property
      schema should not include """"arr":"""
      
      val parsedSchema = parse(schema)
      
      val coordinatesField = (parsedSchema \ "properties" \ "coordinates")
      (coordinatesField \ "type").extract[String] shouldBe "array"
      
      val itemsLevel1 = (coordinatesField \ "items")
      (itemsLevel1 \ "type").extract[String] shouldBe "array"
      (itemsLevel1 \ "properties" \ "arr") shouldBe JNothing
      
      val itemsLevel2 = (itemsLevel1 \ "items")
      (itemsLevel2 \ "type").extract[String] shouldBe "array"
      (itemsLevel2 \ "properties" \ "arr") shouldBe JNothing
      
      val itemsLevel3 = (itemsLevel2 \ "items")
      (itemsLevel3 \ "type").extract[String] shouldBe "array"
      (itemsLevel3 \ "properties" \ "arr") shouldBe JNothing
      
      val itemsLevel4 = (itemsLevel3 \ "items")
      (itemsLevel4 \ "type").extract[String] shouldBe "number"
      
      // Verify that position arrays (innermost arrays) have minItems and maxItems constraints
      // Per RFC 7946 Section 3.1.1:
      // - "A position is an array of numbers. There MUST be two or more elements." (minItems: 2)
      // - "Altitude or elevation MAY be included as an optional third element." (allows 3 elements)
      // - "Implementations SHOULD NOT extend positions beyond three elements" (maxItems: 3)
      // Therefore: minItems: 2, maxItems: 3 (supports 2D and 3D coordinates)
      (itemsLevel3 \ "minItems").extractOpt[Int] shouldBe Some(2)
      (itemsLevel3 \ "maxItems").extractOpt[Int] shouldBe Some(3)
    }
    
    scenario("Empty nested array should be handled gracefully") {
      Given("An empty nested JArray: JArray(List(JArray(List())))")
      val emptyNestedArray = JArray(List(JArray(List())))
      val testObject = JObject(List(JField("empty", emptyNestedArray)))
      
      When("translateEntity is called on the empty nested array")
      val schema = JSONFactory1_4_0.translateEntity(testObject, false)
      
      Then("The schema should handle the empty nested array gracefully")
      logger.debug(s"Generated schema for empty nested array: $schema")
      
      val parsedSchema = parse(schema)
      
      val emptyField = (parsedSchema \ "properties" \ "empty")
      (emptyField \ "type").extract[String] shouldBe "array"
      
      val itemsLevel1 = (emptyField \ "items")
      (itemsLevel1 \ "type").extract[String] shouldBe "array"
      
      // Should NOT contain "properties" with "arr" key
      (itemsLevel1 \ "properties" \ "arr") shouldBe JNothing
    }
  }
}
