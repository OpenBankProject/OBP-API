package code.api.ResourceDocs1_4_0

import org.json4s._
import org.json4s.native.JsonMethods.parse
import code.api.util.APIUtil.ResourceDoc
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v1_4_0.V140ServerSetup
import code.api.v2_1_0.OBPAPI2_1_0
import code.api.v2_2_0.OBPAPI2_2_0
import code.api.v3_0_0.OBPAPI3_0_0
import code.api.v3_1_0.OBPAPI3_1_0
import code.api.v4_0_0.OBPAPI4_0_0
import code.api.v5_0_0.OBPAPI5_0_0
import code.api.v5_1_0.OBPAPI5_1_0
import code.api.v6_0_0.OBPAPI6_0_0
import code.api.v7_0_0.Http4s700
import code.util.Helper.MdcLoggable

import scala.collection.mutable.ArrayBuffer

// Test case classes for JSON escaping tests
case class TestWithQuotes(name: String, description: String)
case class TestWithNewlines(text: String)
case class AbacRule(rule: String)

class SwaggerFactoryUnitTest extends V140ServerSetup with MdcLoggable {

  Feature("Unit tests for the translateEntity method") {
    Scenario("Test the $colon faild case") {
      val translateCaseClassToSwaggerFormatString: String =
        SwaggerJSONFactory.translateEntity(SwaggerDefinitionsJSON.license)
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("$colon")
    }
    Scenario("Test the the List[Case Class] in translateEntity function") {
      val translateCaseClassToSwaggerFormatString: String =
        SwaggerJSONFactory.translateEntity(
          SwaggerDefinitionsJSON.postCounterpartyJSON
        )
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("$colon")
    }

    Scenario("Test `null` in translateEntity function") {
      val translateCaseClassToSwaggerFormatString: String =
        SwaggerJSONFactory.translateEntity(
          SwaggerDefinitionsJSON.counterpartyMetadataJson
        )
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("$colon")
    }

    Scenario(
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

    Scenario(
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

    Scenario("Test `enumeration` for translateEntity function") {
      val translateCaseClassToSwaggerFormatString: String =
        SwaggerJSONFactory.translateEntity(
          SwaggerDefinitionsJSON.cardAttributeCommons
        )
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("""/definitions/Val""")
    }
  }
  Feature(
    "Test all V300, V220 and V210, exampleRequestBodies and successResponseBodies and all the case classes in SwaggerDefinitionsJSON"
  ) {
    Scenario("Test all the case classes") {
      // The concatenation used to be written as a bare expression with `resourceDocList` left as
      // the empty buffer it was initialised to, so every assertion below ran over an empty list and
      // could not fail. Bind it.
      val resourceDocList: ArrayBuffer[ResourceDoc] =
        // allResourceDocs, not resourceDocs: the latter is an ArrayBuffer filled by
        // Implementations7_0_0's body, and touching Http4s700 alone does not initialise that
        // nested object - so `resourceDocs` reads empty unless some other suite happened to
        // serve a v7 request first. allResourceDocs forces it (see its own comment).
        Http4s700.allResourceDocs ++
          OBPAPI6_0_0.allResourceDocs ++
          OBPAPI5_1_0.allResourceDocs ++
          OBPAPI5_0_0.allResourceDocs ++
          OBPAPI4_0_0.allResourceDocs ++
          OBPAPI3_1_0.allResourceDocs ++
          OBPAPI3_0_0.allResourceDocs ++
          OBPAPI2_2_0.allResourceDocs ++
          OBPAPI2_1_0.allResourceDocs

      resourceDocList.size should be > 500

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

      // Guard before use, not decoration: allFields is collected reflectively, and when
      // ReflectUtils could not see a Scala-3-compiled object's members it returned an empty list.
      // Every scenario that maps over it then passed by doing nothing, so the definitions it is
      // meant to contribute went missing without a single red test. The number is deliberately a
      // floor well under the ~777 members declared, not an exact count: this must fail when the
      // collector breaks, not every time someone adds a field.
      withClue("SwaggerDefinitionsJSON.allFields is empty or nearly so - the reflective collector " +
        "is not seeing the object's members, and every check that maps over it is passing " +
        "vacuously: ") {
        SwaggerDefinitionsJSON.allFields.size should be >= 100
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

    Scenario("No published property is a $ref to a boxed primitive or to Object") {
      // Those names are never definitions - nothing here publishes a definition called `Long` or
      // `Object` - so such a property is a dangling reference: a generated client resolves it to
      // nothing, and the field's real type is gone from the document.
      //
      // It is the exact shape a field takes when buildSwaggerSchema cannot tell what type it is,
      // and under Scala 3 that happens by default rather than by accident. scala-reflect reads
      // ScalaSig, an attribute only Scala 2 classes carry; on a Scala 3 class it falls back to the
      // class file's Java generic signature, where a value type cannot be a type argument -
      // `Option[Long]` is emitted as `scala.Option<java.lang.Object>`. refineErasedTypeArgument
      // recovers the type from the example value, which leaves exactly one hole: a field whose
      // example is None carries no value to recover it from, and lands back on
      // {"$ref":"#/definitions/Object"}.
      //
      // So this doubles as the check that every Option-of-a-value-type field reachable from a
      // resource doc's example bodies actually has an example. That is not a documentation nicety
      // here; it is what the field's published type is derived from.
      val resourceDocList: ArrayBuffer[ResourceDoc] =
        // allResourceDocs, not resourceDocs: the latter is an ArrayBuffer filled by
        // Implementations7_0_0's body, and touching Http4s700 alone does not initialise that
        // nested object - so `resourceDocs` reads empty unless some other suite happened to
        // serve a v7 request first. allResourceDocs forces it (see its own comment).
        Http4s700.allResourceDocs ++
          OBPAPI6_0_0.allResourceDocs ++
          OBPAPI5_1_0.allResourceDocs ++
          OBPAPI5_0_0.allResourceDocs ++
          OBPAPI4_0_0.allResourceDocs ++
          OBPAPI3_1_0.allResourceDocs ++
          OBPAPI3_0_0.allResourceDocs ++
          OBPAPI2_2_0.allResourceDocs ++
          OBPAPI2_1_0.allResourceDocs

      resourceDocList.size should be > 500

      val notDefinitions =
        Set("Object", "Boolean", "Integer", "Long", "Float", "Double", "Short", "Byte", "Character")

      def refsIn(schema: JValue): List[String] = schema match {
        case JObject(fields) =>
          fields.flatMap {
            case ("$ref", JString(target)) => List(target.substring(target.lastIndexOf('/') + 1))
            case (_, v) => refsIn(v)
          }
        case _ => Nil
      }

      // Built the way the server builds it (Http4sResourceDocs' swagger branch), not by calling
      // translateEntity per example body. Most definitions are reached only as the target of a
      // $ref from another one - AllConsentJsonV510 is published because ConsentsJsonV510 holds a
      // list of it, never as a body in its own right - and a per-body walk translates only the
      // bodies, so it cannot see them. loadDefinitions does the nested walk, so this is the whole
      // published surface rather than its top layer.
      val resourceDocJsonList =
        JSONFactory1_4_0.createResourceDocsJson(resourceDocList.toList, isVersion4OrHigher = true, None).resource_docs
      val definitions =
        SwaggerJSONFactory.loadDefinitions(resourceDocJsonList, SwaggerDefinitionsJSON.allFields) \\ "definitions" match {
          case JObject(defs) => defs
          case other => fail(s"expected a definitions object, got: ${other.getClass.getSimpleName}")
        }

      definitions.size should be > 100

      val offenders = definitions.flatMap {
        case (definitionName, JObject(body)) =>
          val properties =
            body.collectFirst { case ("properties", JObject(props)) => props }.getOrElse(Nil)
          properties.flatMap { case (fieldName, schema) =>
            refsIn(schema).filter(notDefinitions).map(bad => s"$definitionName.$fieldName -> $$ref:$bad")
          }
        case _ => Nil
      }.distinct.sorted

      withClue(
        s"${offenders.size} field(s) publish a dangling $$ref. A field of an erased type (an Option " +
        "of a value type, or a collection of one) takes its published type from its example value, " +
        "so an offender here almost always means that field's example is None or absent - give it a " +
        s"real value:\n${offenders.mkString("\n")}\n") {
        offenders shouldBe empty
      }
    }
  }

  Feature("Test JSON escaping robustness in Swagger generation") {
    Scenario("Test quotes in example values are properly escaped") {
      val testObj = TestWithQuotes(
        name = "Test with \"quotes\"",
        description = "Has 'single' and \"double\" quotes"
      )
      val result = SwaggerJSONFactory.translateEntity(testObj)
      noException should be thrownBy {
        com.openbankproject.commons.util.JsonAliases.parse("{" + result + "}")
      }
      result should include("\\\"")
    }

    Scenario("Test newlines and special chars are properly escaped") {
      val testObj = TestWithNewlines(text = "Line 1\nLine 2\tTab")
      val result = SwaggerJSONFactory.translateEntity(testObj)
      noException should be thrownBy {
        com.openbankproject.commons.util.JsonAliases.parse("{" + result + "}")
      }
      result should include("\\n")
    }

    Scenario("Test ABAC rule-like strings with escaped quotes") {
      val testObj = AbacRule(rule = """user.emailAddress.contains(\"admin\")""")
      val result = SwaggerJSONFactory.translateEntity(testObj)
      noException should be thrownBy {
        com.openbankproject.commons.util.JsonAliases.parse("{" + result + "}")
      }
    }

    Scenario("Test error messages with special characters") {
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
        typed_request_body = org.json4s.JNothing,
        typed_success_response_body = org.json4s.JNothing,
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
