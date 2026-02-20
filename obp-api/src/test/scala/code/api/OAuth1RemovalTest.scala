package code.api

import code.api.v4_0_0.V400ServerSetup
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * Test suite to verify OAuth 1.0a endpoints have been removed and return 404.
 * 
 * **Validates: Requirements 1.4**
 * 
 * This test suite ensures that all OAuth 1.0a endpoints are no longer accessible
 * after the OAuth 1.0a removal feature has been implemented.
 */
class OAuth1RemovalTest extends V400ServerSetup {
  
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object OAuth1Removal extends Tag("OAuth1Removal")

  feature("OAuth 1.0a endpoints should return 404 after removal") {
    
    scenario("POST /oauth/initiate should return 404 Not Found", OAuth1Removal, VersionOfApi) {
      When("We make a POST request to /oauth/initiate")
      val request = baseRequest / "oauth" / "initiate"
      val response = makePostRequest(request, "")
      
      Then("We should get a 404")
      response.code should equal(404)
    }
    
    scenario("POST /oauth/token should return 404 Not Found", OAuth1Removal, VersionOfApi) {
      When("We make a POST request to /oauth/token")
      val request = baseRequest / "oauth" / "token"
      val response = makePostRequest(request, "")
      
      Then("We should get a 404")
      response.code should equal(404)
    }
    
    scenario("GET /oauth/authorize should return 404 Not Found", OAuth1Removal, VersionOfApi) {
      When("We make a GET request to /oauth/authorize")
      val request = baseRequest / "oauth" / "authorize"
      val response = makeGetRequest(request)
      
      Then("We should get a 404")
      response.code should equal(404)
    }
  }
}
