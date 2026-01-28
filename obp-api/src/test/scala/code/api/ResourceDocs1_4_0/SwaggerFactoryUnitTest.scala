package code.api.ResourceDocs1_4_0

import code.api.util.APIUtil.ResourceDoc
import code.api.v1_4_0.V140ServerSetup
import code.api.v2_1_0.OBPAPI2_1_0
import code.api.v2_2_0.OBPAPI2_2_0
import code.api.v3_0_0.OBPAPI3_0_0
import code.api.v3_1_0.OBPAPI3_1_0
import code.api.v4_0_0.OBPAPI4_0_0
import code.api.v5_0_0.OBPAPI5_0_0
import code.api.v5_1_0.OBPAPI5_1_0
import code.api.v6_0_0.OBPAPI6_0_0
import code.util.Helper.MdcLoggable

import scala.collection.mutable.ArrayBuffer

// Test case classes for JSON escaping tests
case class TestWithQuotes(name: String, description: String)
case class TestWithNewlines(text: String)
case class AbacRule(rule: String)

class SwaggerFactoryUnitTest extends V140ServerSetup with MdcLoggable {

  feature("Unit tests for the translateEntity method") {
    scenario("Test the $colon faild case") {
      val translateCaseClassToSwaggerFormatString: String =
        SwaggerJSONFactory.translateEntity(SwaggerDefinitionsJSON.license)
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("$colon")
    }
    scenario("Test the the List[Case Class] in translateEntity function") {
      val translateCaseClassToSwaggerFormatString: String =
        SwaggerJSONFactory.translateEntity(
          SwaggerDefinitionsJSON.postCounterpartyJSON
        )
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("$colon")
    }

    scenario("Test `null` in translateEntity function") {
      val translateCaseClassToSwaggerFormatString: String =
        SwaggerJSONFactory.translateEntity(
          SwaggerDefinitionsJSON.counterpartyMetadataJson
        )
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("$colon")
    }

    scenario(
      "Test `SecondaryIdentification: Option[String] = None,` in translateEntity function"
    ) {
      val translateCaseClassToSwaggerFormatString: String =
        SwaggerJSONFactory.translateEntity(
          SwaggerDefinitionsJSON.accountInnerJsonUKOpenBanking_v200
            .copy(SecondaryIdentification = Some("1111"))
        )
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      // This optional type should be "1111", should not contain Some(1111)
      translateCaseClassToSwaggerFormatString should not include ("""Some(1111)""")
    }

    scenario(
      "Test `product_attributes = Some(List(productAttributeResponseJson))` in translateEntity function"
    ) {
      val translateCaseClassToSwaggerFormatString: String =
        SwaggerJSONFactory.translateEntity(
          SwaggerDefinitionsJSON.productJsonV310
        )
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("""/definitions/scala.Some""")
      translateCaseClassToSwaggerFormatString should not include ("""$colon""")
    }

    scenario("Test `enumeration` for translateEntity function") {
      val translateCaseClassToSwaggerFormatString: String =
        SwaggerJSONFactory.translateEntity(
          SwaggerDefinitionsJSON.cardAttributeCommons
        )
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("""/definitions/Val""")
    }
  }
  feature(
    "Test all V300, V220 and V210, exampleRequestBodies and successResponseBodies and all the case classes in SwaggerDefinitionsJSON"
  ) {
    scenario("Test all the case classes") {
      val resourceDocList: ArrayBuffer[ResourceDoc] = ArrayBuffer.empty
      OBPAPI6_0_0.allResourceDocs ++
        OBPAPI5_1_0.allResourceDocs ++
        OBPAPI5_0_0.allResourceDocs ++
        OBPAPI4_0_0.allResourceDocs ++
        OBPAPI3_1_0.allResourceDocs ++
        OBPAPI3_0_0.allResourceDocs ++
        OBPAPI2_2_0.allResourceDocs ++
        OBPAPI2_1_0.allResourceDocs

      // Translate every entity(JSON Case Class) in a list to appropriate swagger format
      val listOfExampleRequestBodyDefinition =
        for (e <- resourceDocList if e.exampleRequestBody != null)
          yield {
            SwaggerJSONFactory.translateEntity(e.exampleRequestBody)
          }

      val listOfSuccessRequestBodyDefinition =
        for (e <- resourceDocList if e.successResponseBody != null)
          yield {
            SwaggerJSONFactory.translateEntity(e.successResponseBody)
          }

      val listNestedMissingDefinition: List[String] =
        SwaggerDefinitionsJSON.allFields
          .map(SwaggerJSONFactory.translateEntity)
          .toList

      val allStrings =
        listOfExampleRequestBodyDefinition ++ listOfSuccessRequestBodyDefinition ++ listNestedMissingDefinition
      // All of the following are invalid value in Swagger, if any of them exist,
      // need check how you create the case class object in SwaggerDefinitionsJSON.json.
      allStrings.toString() should not include ("Nil$")
      allStrings.toString() should not include ("JArray")
      allStrings.toString() should not include ("JBool")
      allStrings.toString() should not include ("JInt")
      allStrings.toString() should not include ("JNothing")
      allStrings.toString() should not include ("JNull")
      allStrings.toString() should not include ("None$")
      allStrings.toString() should not include ("definitions/scala.Some")

      logger.debug(allStrings)
    }
  }

  feature("Test JSON escaping robustness in Swagger generation") {
    scenario("Test quotes in example values are properly escaped") {
      val testObj = TestWithQuotes(
        name = "Test with \"quotes\"",
        description = "Has 'single' and \"double\" quotes"
      )
      val result = SwaggerJSONFactory.translateEntity(testObj)
      noException should be thrownBy {
        net.liftweb.json.parse("{" + result + "}")
      }
      result should include("\\\"")
    }

    scenario("Test newlines and special chars are properly escaped") {
      val testObj = TestWithNewlines(text = "Line 1\nLine 2\tTab")
      val result = SwaggerJSONFactory.translateEntity(testObj)
      noException should be thrownBy {
        net.liftweb.json.parse("{" + result + "}")
      }
      result should include("\\n")
    }

    scenario("Test ABAC rule-like strings with escaped quotes") {
      val testObj = AbacRule(rule = """user.emailAddress.contains(\"admin\")""")
      val result = SwaggerJSONFactory.translateEntity(testObj)
      noException should be thrownBy {
        net.liftweb.json.parse("{" + result + "}")
      }
    }

    scenario("Test error messages with special characters") {
      import code.api.v1_4_0.JSONFactory1_4_0
      val mockResourceDoc = JSONFactory1_4_0.ResourceDocJson(
        operation_id = "testOp",
        implemented_by = JSONFactory1_4_0.ImplementedByJson("1.0.0", "test"),
        request_verb = "GET",
        request_url = "/test",
        summary = "Test",
        description = "Test desc",
        description_markdown = "Test desc",
        example_request_body = null,
        success_response_body = SwaggerDefinitionsJSON.bankJSON,
        error_response_bodies = List("OBP-10000"),
        tags = List("Test"),
        typed_request_body = net.liftweb.json.JNothing,
        typed_success_response_body = net.liftweb.json.JNothing,
        roles = Some(List()),
        is_featured = false,
        special_instructions = "",
        specified_url = "/obp/v4.0.0/test",
        connector_methods = List(),
        created_by_bank_id = None
      )
      noException should be thrownBy {
        SwaggerJSONFactory.loadDefinitions(
          List(mockResourceDoc),
          SwaggerDefinitionsJSON.allFields.take(10)
        )
      }
    }
  }
}
