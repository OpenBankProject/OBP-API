package code.api.ResourceDocs1_4_0

import code.api.util.APIUtil.ResourceDoc
import code.api.v1_4_0.V140ServerSetup
import code.api.v2_1_0.OBPAPI2_1_0
import code.api.v2_2_0.OBPAPI2_2_0
import code.api.v3_0_0.OBPAPI3_0_0
import code.api.v3_1_0.OBPAPI3_1_0
import code.api.v4_0_0.OBPAPI4_0_0
import code.util.Helper.MdcLoggable

import scala.collection.mutable.ArrayBuffer

class SwaggerFactoryUnitTest extends V140ServerSetup with MdcLoggable {

  feature("Unit tests for the translateEntity method") {
    scenario("Test the $colon faild case") {
      val translateCaseClassToSwaggerFormatString: String = SwaggerJSONFactory.translateEntity(SwaggerDefinitionsJSON.license)
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("$colon")
    }
    scenario("Test the the List[Case Class] in translateEntity function") {
      val translateCaseClassToSwaggerFormatString: String = SwaggerJSONFactory.translateEntity(SwaggerDefinitionsJSON.postCounterpartyJSON)
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("$colon")
    }

    scenario("Test `null` in translateEntity function") {
      val translateCaseClassToSwaggerFormatString: String = SwaggerJSONFactory.translateEntity(SwaggerDefinitionsJSON.counterpartyMetadataJson)
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("$colon")
    }

    scenario("Test `SecondaryIdentification: Option[String] = None,` in translateEntity function") {
      val translateCaseClassToSwaggerFormatString: String = SwaggerJSONFactory.translateEntity(SwaggerDefinitionsJSON.accountInnerJsonUKOpenBanking_v200.copy(SecondaryIdentification = Some("1111")))
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      //This optional type should be "1111", should not contain Some(1111)
      translateCaseClassToSwaggerFormatString should not include ("""Some(1111)""")
    }

    scenario("Test `product_attributes = Some(List(productAttributeResponseJson))` in translateEntity function") {
      val translateCaseClassToSwaggerFormatString: String = SwaggerJSONFactory.translateEntity(SwaggerDefinitionsJSON.productJsonV310)
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("""/definitions/scala.Some""")
      translateCaseClassToSwaggerFormatString should not include ("""$colon""")
    }
    
    scenario("Test `enumeration` for translateEntity function") {
      val translateCaseClassToSwaggerFormatString: String = SwaggerJSONFactory.translateEntity(SwaggerDefinitionsJSON.cardAttributeCommons)
      logger.debug("{" + translateCaseClassToSwaggerFormatString + "}")
      translateCaseClassToSwaggerFormatString should not include ("""/definitions/Val""")
    }
  }
  feature("Test all V300, V220 and V210, exampleRequestBodies and successResponseBodies and all the case classes in SwaggerDefinitionsJSON") {
    scenario("Test all the case classes") {
      val resourceDocList: ArrayBuffer[ResourceDoc] = OBPAPI3_1_0.allResourceDocs ++ OBPAPI3_0_0.allResourceDocs ++ OBPAPI2_2_0.allResourceDocs ++ OBPAPI2_1_0.allResourceDocs

      //Translate every entity(JSON Case Class) in a list to appropriate swagger format
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

      val listNestedMissingDefinition: List[String] = SwaggerDefinitionsJSON.allFields
        .map(SwaggerJSONFactory.translateEntity)
        .toList

      val allStrings = listOfExampleRequestBodyDefinition ++ listOfSuccessRequestBodyDefinition ++ listNestedMissingDefinition
      //All of the following are invalid value in Swagger, if any of them exist, 
      //need check how you create the case class object in SwaggerDefinitionsJSON.json. 
      allStrings.toString() should not include ("$colon") // This happened when use the primitive types. eg: val b = List("tesobe"), the List can not be find for now. 
      allStrings.toString() should not include ("Nil$")
      allStrings.toString() should not include ("JArray")
      allStrings.toString() should not include ("JBool")
      allStrings.toString() should not include ("JInt")
      allStrings.toString() should not include ("JNothing")
      allStrings.toString() should not include ("JNull")
      allStrings.toString() should not include ("JObject")
      allStrings.toString() should not include ("JString")
      allStrings.toString() should not include ("None$")
      allStrings.toString() should not include ("definitions/scala.Some")

      logger.debug(allStrings)
    }
  }
 
}
