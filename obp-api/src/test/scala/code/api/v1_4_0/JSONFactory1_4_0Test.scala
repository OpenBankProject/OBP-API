package code.api.v1_4_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.usersJsonV400

import java.util.Date
import code.api.util.APIUtil.ResourceDoc
import code.api.util.{APIUtil, ExampleValue}
import code.api.v1_4_0.JSONFactory1_4_0.ResourceDocJson
import code.api.v3_0_0.OBPAPI3_0_0
import code.setup.DefaultUsers
import net.liftweb.json.Extraction.decompose
import net.liftweb.json._
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject

import scala.collection.mutable;

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

class JSONFactory1_4_0Test  extends V140ServerSetup with DefaultUsers {
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
      val resourceDoc: ResourceDoc = OBPAPI3_0_0.allResourceDocs(5)
      val result: ResourceDocJson = JSONFactory1_4_0.createLocalisedResourceDocJson(resourceDoc,false, None, 
        urlParameters, "JSON request body fields:", "JSON response body fields:")
    }

    scenario("createResourceDocsJson should work well, no exception is good enough") {
      val resourceDoc: mutable.Seq[ResourceDoc] = OBPAPI3_0_0.allResourceDocs
      val result = JSONFactory1_4_0.createResourceDocsJson(resourceDoc.toList, false, None)
    }

    scenario("createTypedBody should work well, no exception is good enough") {
      val inputCaseClass = AllCases()
      val result = JSONFactory1_4_0.createTypedBody(inputCaseClass)
//      logger.debug(prettyRender(decompose(inputCaseClass)))
//      logger.debug("---------------")
//      logger.debug(prettyRender(result))
    }

    scenario("validate all the resourceDocs json schema, no exception is good enough") {
      val resourceDocsRaw= OBPAPI3_0_0.allResourceDocs
      val resourceDocs = JSONFactory1_4_0.createResourceDocsJson(resourceDocsRaw.toList,false, None)

      for{
        resourceDoc <- resourceDocs.resource_docs if (resourceDoc.request_verb != "DELETE")
        json <- List(compactRender(decompose(resourceDoc.success_response_body)))
        jsonSchema <- List(compactRender(resourceDoc.typed_success_response_body))
      } yield {
        val rawSchema = new JSONObject(jsonSchema)
        val schema = SchemaLoader.load(rawSchema)
        schema.validate(new JSONObject(json))
      }
    }

  }
  
}
