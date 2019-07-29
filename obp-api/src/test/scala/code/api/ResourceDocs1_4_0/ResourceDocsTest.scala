package code.api.v3_1_0

import code.api.ResourceDocs1_4_0.ResourceDocsV140ServerSetup
import code.api.util.{ApiVersion, CustomJsonFormats}
import code.api.v1_4_0.JSONFactory1_4_0.ImplementedByJson
import net.liftweb.json.JValue
import org.scalatest.Tag

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
      //TODO, should have more tests to make sure swagger file is validated .
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v3.1.1", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v3.1.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      //TODO, should have more tests to make sure swagger file is validated .
    }
    
  }
} 
