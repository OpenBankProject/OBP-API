package code.api.v3_1_0

import net.liftweb.json.JValue
import org.scalatest.Tag
import io.swagger.parser.OpenAPIParser
import java.util
import code.api.ResourceDocs1_4_0.ResourceDocsV140ServerSetup
import code.api.util.{ApiVersion, CustomJsonFormats}
import code.api.v1_4_0.JSONFactory1_4_0.ImplementedByJson
import net.liftweb.json

import scala.collection.immutable.List
class ResourceDocsTest extends ResourceDocsV140ServerSetup with CustomJsonFormats{

  object VersionOfApi extends Tag(ApiVersion.v1_4_0.toString)
  object ApiEndpoint1 extends Tag("Get Swagger ResourceDoc")
  object ApiEndpoint2 extends Tag("Get OBP ResourceDoc ")


  feature(s"test ${ApiEndpoint1.name} ") {

    case class RoleJson (
      role: String,
      requires_bank_id: Boolean
    )

    //This case class is for API_Explorer, it should make sure api_explorer can get proper doc.
    case class ResourceDocJson(operation_id: String,
      request_verb: String,
      request_url: String,
      summary: String, // Summary of call should be 120 characters max
      description: String,      // Description of call in markdown
      example_request_body: JValue,  // An example request body
      success_response_body: JValue, // Success response body
      error_response_bodies: List[String],
      implemented_by: ImplementedByJson,
      is_core : Boolean,
      is_psd2 : Boolean,
      is_obwg : Boolean, // This may be tracking isCore
      tags : List[String],
      roles: List[RoleJson],
      is_featured: Boolean,
      special_instructions: String,
      specified_url: String // This is the URL that we want people to call.
    )

    case class ResourceDocsJson (resource_docs : List[ResourceDocJson])


    scenario(s"We will test ${ApiEndpoint1.name} Api -v4.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v4.0.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      responseGetObp.body.extract[ResourceDocsJson]
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v3.1.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v3.1.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      responseGetObp.body.extract[ResourceDocsJson]
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v3.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v3.0.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      responseGetObp.body.extract[ResourceDocsJson]
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v2.2.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v2.2.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      responseGetObp.body.extract[ResourceDocsJson]
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v2.1.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v2.1.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      responseGetObp.body.extract[ResourceDocsJson]
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v2.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v2.0.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      responseGetObp.body.extract[ResourceDocsJson]
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v1.4.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v1.4.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      responseGetObp.body.extract[ResourceDocsJson]
    }
    
    scenario(s"We will test ${ApiEndpoint1.name} Api -v1.3.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v1.3.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      responseGetObp.body.extract[ResourceDocsJson]
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v1.2.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v1.2.1" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      responseGetObp.body.extract[ResourceDocsJson]
    }
  }

  feature(s"test ${ApiEndpoint2.name} ") {
    scenario(s"We will test ${ApiEndpoint2.name} Api - v4.0.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v4.0.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)
      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      if (!errors.isEmpty) logger.info(s"Here is the wrong swagger json:    $swaggerJsonString")
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v3.1.1", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v3.1.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)

      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v3.0.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v3.0.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)
      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v2.2.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v2.2.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)

      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v2.1.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v2.1.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)
      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v2.0.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v2.0.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)

      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v1.4.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v1.4.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)

      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v1.3.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v1.3.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)

      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v1.2.1", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v1.2.1" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)

      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }
    
  }
  
  
  
  
  //Note: it is tricky to validate the swagger string, I just find this : https://github.com/swagger-api/swagger-parser/issues/718
  //So follow it to call the `Validate` method:
  //https://github.com/OpenAPITools/openapi-generator/blob/master/modules/openapi-generator-cli/src/main/java/org/openapitools/codegen/cmd/Validate.java#L46
  def ValidateSwaggerString (swaggerJsonString: String)= {
    val result = new OpenAPIParser().readContents(swaggerJsonString, null, null)
    val messageList: util.List[String] = result.getMessages()

    val errors = new util.HashSet[String](messageList)
    val warnings = new util.HashSet[String]

    val sb = new StringBuilder

    if (!errors.isEmpty) {
      sb.append("Errors:").append(System.lineSeparator)
      errors.forEach((msg: String) => sb.append("\t-").append(msg).append(System.lineSeparator))
    }

    if (!warnings.isEmpty) {
      sb.append("Warnings: ").append(System.lineSeparator)
      warnings.forEach((msg: String) => sb.append("\t-").append(msg).append(System.lineSeparator))
    }

    if (!errors.isEmpty) {
      sb.append(System.lineSeparator)
      sb.append("[error] Spec has ").append(errors.size).append(" errors.")
      System.err.println(sb.toString)
      System.exit(1)
    }
    else if (!warnings.isEmpty) {
      sb.append(System.lineSeparator)
      sb.append("[info] Spec has ").append(warnings.size).append(" recommendation(s).")
    }
    else { // we say "issues" here rather than "errors" to account for both errors and issues.
      sb.append("No validation issues detected.")
    }
    val allMessages = sb.toString
    logger.info(s"validatedSwaggerResult.errors $errors")
    logger.info(s"validatedSwaggerResult.warnings $warnings")
    logger.info(s"validatedSwaggerResult.allMessages $allMessages")
    
    (errors, warnings, allMessages)
  }
} 
