package code.api.ResourceDocs1_4_0

import code.api.util.APIUtil.{ApiVersion, ResourceDoc}
import code.api.v2_1_0.OBPAPI2_1_0
import code.api.v2_2_0.OBPAPI2_2_0
import code.api.v3_0_0.OBPAPI3_0_0
import code.util.Helper.MdcLoggable
import org.scalatest._

import scala.collection.mutable.ArrayBuffer

class SwaggerFactoryUnitTest extends FeatureSpec
  with Matchers
  with MdcLoggable {
  
  feature("Test SwaggerJSONFactory.translateEntity ") {
    scenario("A simple example to explain how to use SwaggerJSONFactory.translateEntity ") {
      val translateCaseClassToSwaggerFormatString: String = SwaggerJSONFactory.translateEntity(SwaggerDefinitionsJSON.license )
      logger.debug("{"+translateCaseClassToSwaggerFormatString+"}")
      translateCaseClassToSwaggerFormatString should not include("$colon")
    }
  
    scenario("Procee the List[Case Class] in translateEntity function") {
      val translateCaseClassToSwaggerFormatString: String = SwaggerJSONFactory.translateEntity(SwaggerDefinitionsJSON.postCounterpartyJSON)
      logger.debug("{"+translateCaseClassToSwaggerFormatString+"}")
      translateCaseClassToSwaggerFormatString should not include("$colon")
    }
    
    scenario("Test all the case classes in SwaggerDefinitionsJSON") {
      val allSwaggerDefinitionCaseClasses = SwaggerDefinitionsJSON.allFields

      val listNestingMissDefinition: List[String] =
        for (e <- allSwaggerDefinitionCaseClasses.toList if e!= null)
          yield {
            SwaggerJSONFactory.translateEntity(e)
          }
      logger.debug(listNestingMissDefinition)

      listNestingMissDefinition.toString() should not include("$colon")

    }
  
    scenario("Test all V300, V220 and V210, exampleRequestBodies and successResponseBodies") {
  
      val resourceDocList: ArrayBuffer[ResourceDoc] = OBPAPI3_0_0.allResourceDocs ++ OBPAPI2_2_0.allResourceDocs++ OBPAPI2_1_0.allResourceDocs
  
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
      
      listOfExampleRequestBodyDefinition.toString() should not include("$colon")
      listOfSuccessRequestBodyDefinition.toString() should not include("$colon")
    
    }
  }
  
}
