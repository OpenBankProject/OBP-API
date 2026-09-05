package code.api.v1_4_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.usersJsonV400
import code.api.Constant
import java.util.Date
import code.api.util.APIUtil.ResourceDoc
import code.api.util.{APIUtil, ExampleValue}
import code.api.util.CustomJsonFormats
import code.api.v1_4_0.JSONFactory1_4_0.ResourceDocJson
import org.json4s.Extraction.decompose
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.{JsonSchemaFactory, SpecVersion}

import scala.collection.mutable;
import code.api.util.http4s.Http4sResourceDocAggregation

case class OneClass(
  string : String = "String",
  strings : List[String] = List("String")
)

case class TwoClass(
  string : String = "String",
  strings : List[String] = List("String"),
  oneClass: OneClass = OneClass(),
  oneClasses: List[OneClass] = List(OneClass())
)

case class AllCases(
  string: String = "String",
  strings: List[String] = List("String"),
  date: Date = APIUtil.DateWithDayExampleObject,
  dates: List[Date] = List(APIUtil.DateWithDayExampleObject),
  boolean: Boolean = true,
  booleans: List[Boolean]= List(true),
  int: Int = 3,
  ints: List[Int]= List(3),
  nestClass: OneClass = OneClass(),
  nestClasses: List[OneClass] = List(OneClass()),
  nestClassTwo: TwoClass = TwoClass(),
  nestClassesTwo: List[TwoClass] = List(TwoClass()),
  jvalue: JValue= APIUtil.defaultJValue,
  jvalues: List[JValue]= List(APIUtil.defaultJValue)
)

class JSONFactory1_4_0Test extends code.setup.ServerSetup {
  override implicit val formats: Formats = CustomJsonFormats.formats
  feature("Test JSONFactory1_4_0") {

    scenario("prepareDescription should work well, extract the parameters from URL") {
      val description = JSONFactory1_4_0.prepareDescription("BANK_ID", Nil)
      description.contains("[BANK_ID](/glossary#Bank.bank_id): gh.29.uk") should be (true)
    }
    
    scenario("prepareJsonFieldDescription should work well - users object") {
      val usersJson = usersJsonV400
      val description = JSONFactory1_4_0.prepareJsonFieldDescription(usersJson, "response", "JSON request body fields:", "JSON response body fields:")
      description.contains(
        """
          |JSON response body fields:
          |
          |*user_id = 9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1,
          |
          |*email = felixsmith@example.com,
          |
          |*provider_id = ,
          |
          |*provider = ,
          |
          |*username = felixsmith,
          |
          |*entitlements = ,
          |
          |*views = ,
          |
          |*agreements = ,
          |
          |*is_deleted = false,
          |
          |*last_marketing_agreement_signed_date = ,
          |
          |*is_locked = false
          |    
          |""".stripMargin
      ) should be (false)
      println(description)
    }

    val urlParameters = "URL Parameters:"
    scenario("PrepareUrlParameterDescription should work well, extract the parameters from URL") {
      val requestUrl1 = "/obp/v4.0.0/banks/BANK_ID/accounts/account_ids/private"
      val requestUrl1Description = JSONFactory1_4_0.prepareUrlParameterDescription(requestUrl1,urlParameters)
      requestUrl1Description contains ("[BANK_ID]") should be (true)
      val requestUrl2 = "/obp/v4.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID"
      val requestUrl2Description = JSONFactory1_4_0.prepareUrlParameterDescription(requestUrl2, urlParameters)
      requestUrl2Description contains ("[BANK_ID]") should be (true)
      requestUrl2Description contains ("[ACCOUNT_ID]") should be (true)
      requestUrl2Description contains ("[VIEW_ID]") should be (true)

      val requestUrl3 = "/obp/v4.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID?date=2020-11-11"
      val requestUrl3Description = JSONFactory1_4_0.prepareUrlParameterDescription(requestUrl3, urlParameters)

      requestUrl2Description shouldEqual(requestUrl3Description)
    }

    scenario("getExampleTitleAndValueTuple should work well") {
      val value  = JSONFactory1_4_0.getExampleFieldValue("BANK_ID")
      value should be (ExampleValue.bankIdExample.value)
    }

    scenario("getGlossaryItemTitle should work well") {
      val value  = JSONFactory1_4_0.getGlossaryItemTitle("BANK_ID")
      value should be ("Bank.bank_id")
    }
    
    scenario("createResourceDocJson should work well,  no exception is good enough") {
      val resourceDoc: ResourceDoc = Http4sResourceDocAggregation.v300(5)
      val result: ResourceDocJson = JSONFactory1_4_0.createLocalisedResourceDocJson(resourceDoc,false, None, 
        includeTechnology = false, urlParameters, "JSON request body fields:", "JSON response body fields:")
    }

    scenario("createResourceDocsJson should work well, no exception is good enough") {
      val resourceDoc: mutable.Seq[ResourceDoc] = Http4sResourceDocAggregation.v300
      val result = JSONFactory1_4_0.createResourceDocsJson(resourceDoc.toList, false, None)
    }

    scenario("Technology field should be None unless includeTechnology=true") {
      // All versions are now on http4s — use any http4s doc.
      val http4sDoc: ResourceDoc = Http4sResourceDocAggregation.v121.head
      val json1 = JSONFactory1_4_0.createLocalisedResourceDocJson(http4sDoc, false, None, includeTechnology = false, urlParameters, "JSON request body fields:", "JSON response body fields:")
      json1.implemented_by.technology shouldBe None

      val json2 = JSONFactory1_4_0.createLocalisedResourceDocJson(http4sDoc, false, None, includeTechnology = true, urlParameters, "JSON request body fields:", "JSON response body fields:")
      json2.implemented_by.technology shouldBe Some(Constant.TECHNOLOGY_HTTP4S)
    }

    scenario("Technology field should be http4s when includeTechnology=true and doc is http4s") {
      val http4sDoc: ResourceDoc = code.api.v7_0_0.Http4s700.resourceDocs.head
      val json = JSONFactory1_4_0.createLocalisedResourceDocJson(http4sDoc, true, None, includeTechnology = true, urlParameters, "JSON request body fields:", "JSON response body fields:")
      json.implemented_by.technology shouldBe Some(Constant.TECHNOLOGY_HTTP4S)
    }

    scenario("createTypedBody should work well, no exception is good enough") {
      val inputCaseClass = AllCases()
      val result = JSONFactory1_4_0.createTypedBody(inputCaseClass)
//      logger.debug(prettyRender(decompose(inputCaseClass)))
//      logger.debug("---------------")
//      logger.debug(prettyRender(result))
    }

    scenario("validate all the resourceDocs json schema, no exception is good enough") {
      val resourceDocsRaw= Http4sResourceDocAggregation.v300
      val resourceDocs = JSONFactory1_4_0.createResourceDocsJson(resourceDocsRaw.toList,false, None)
      val mapper = new ObjectMapper()
      val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4)

      for{
        resourceDoc <- resourceDocs.resource_docs if (resourceDoc.request_verb != "DELETE")
        json <- List(compactRender(decompose(resourceDoc.success_response_body)))
        jsonSchema <- List(compactRender(resourceDoc.typed_success_response_body))
      } yield {
        val schema = schemaFactory.getSchema(mapper.readTree(jsonSchema))
        val errors = schema.validate(mapper.readTree(json))
        assert(errors.isEmpty, s"JSON Schema validation failed: ${errors}")
      }
    }

  }
  
}
