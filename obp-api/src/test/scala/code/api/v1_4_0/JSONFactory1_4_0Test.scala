package code.api.v1_4_0

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
      val description = JSONFactory1_4_0.prepareDescription("BANK_ID")
      description.contains("[BANK_ID](/glossary#Bank.bank_id): gh.29.uk") should be (true)
    }
    
    scenario("PrepareUrlParameterDescription should work well, extract the parameters from URL") {
      val requestUrl1 = "/obp/v4.0.0/banks/BANK_ID/accounts/account_ids/private"
      val requestUrl1Description = JSONFactory1_4_0.prepareUrlParameterDescription(requestUrl1)
      requestUrl1Description contains ("[BANK_ID]") should be (true)
      val requestUrl2 = "/obp/v4.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID"
      val requestUrl2Description = JSONFactory1_4_0.prepareUrlParameterDescription(requestUrl2)
      requestUrl2Description contains ("[BANK_ID]") should be (true)
      requestUrl2Description contains ("[ACCOUNT_ID]") should be (true)
      requestUrl2Description contains ("[VIEW_ID]") should be (true)
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
      val result: ResourceDocJson = JSONFactory1_4_0.createResourceDocJson(resourceDoc)
    }

    scenario("createResourceDocsJson should work well, no exception is good enough") {
      val resourceDoc: mutable.Seq[ResourceDoc] = OBPAPI3_0_0.allResourceDocs
      val result = JSONFactory1_4_0.createResourceDocsJson(resourceDoc.toList)
    }

    scenario("createTypedBody should work well, no exception is good enough") {
      val inputCaseClass = AllCases()
      val result = JSONFactory1_4_0.createTypedBody(inputCaseClass)
//      logger.debug(prettyRender(decompose(inputCaseClass)))
//      logger.debug("---------------")
//      logger.debug(prettyRender(result))
    }

    scenario("validate all the resouceDocs json schema, no exception is good enough") {
      val resourceDocsRaw= OBPAPI3_0_0.allResourceDocs
      val resourceDocs = JSONFactory1_4_0.createResourceDocsJson(resourceDocsRaw.toList)

      for{
        resouceDoc <- resourceDocs.resource_docs
        json <- List(compactRender(decompose(resouceDoc.success_response_body)))
        jsonSchema <- List(compactRender(resouceDoc.typed_success_response_body))
      } yield {
        val rawSchema = new JSONObject(jsonSchema)
        val schema = SchemaLoader.load(rawSchema)
        schema.validate(new JSONObject(json))
      }
    }

  }
  
}
