package code.api.ResourceDocs1_4_0

import code.api.util.APIUtil.{ApiVersion, ResourceDoc}
import code.api.v2_1_0.OBPAPI2_1_0
import code.api.v2_2_0.OBPAPI2_2_0
import code.api.v3_0_0.OBPAPI3_0_0
import code.util.Helper.MdcLoggable
import org.scalatest._

import scala.collection.mutable.ArrayBuffer

class SwaggerFactoryUnitTest extends FlatSpec
  with Matchers
  with MdcLoggable {
  
  "SwaggerJSONFactory.translateEntity" should "translate simple case class not incude wired string" in {
      val translateCaseClassToSwaggerFormatString: String = SwaggerJSONFactory.translateEntity(SwaggerDefinitionsJSON.license )
      logger.debug("{"+translateCaseClassToSwaggerFormatString+"}")
      translateCaseClassToSwaggerFormatString should not include("$colon")
    }
  
  it should ("Procee the List[Case Class] in translateEntity function") in{
      val translateCaseClassToSwaggerFormatString: String = SwaggerJSONFactory.translateEntity(SwaggerDefinitionsJSON.postCounterpartyJSON)
      logger.debug("{"+translateCaseClassToSwaggerFormatString+"}")
      translateCaseClassToSwaggerFormatString should not include("$colon")
    }

  it should ("Test all the case classes in SwaggerDefinitionsJSON") in{
      val allSwaggerDefinitionCaseClasses = SwaggerDefinitionsJSON.allFields

      val listNestingMissDefinition: List[String] =
        for (e <- allSwaggerDefinitionCaseClasses.toList if e!= null)
          yield {
            SwaggerJSONFactory.translateEntity(e)
          }
      logger.debug(listNestingMissDefinition)

      listNestingMissDefinition.toString() should not include("$colon")

    }

  it should ("Test all V300, V220 and V210, exampleRequestBodies and successResponseBodies") in {

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
      logger.debug(listOfExampleRequestBodyDefinition)
      logger.debug(listOfExampleRequestBodyDefinition)
    }
  
}
