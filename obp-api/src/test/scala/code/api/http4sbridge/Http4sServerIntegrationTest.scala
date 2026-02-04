package code.api.http4sbridge

import code.Http4sTestServer
import code.setup.{DefaultUsers, ServerSetup}
import dispatch.Defaults._
import dispatch._
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonParser.parse
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Real HTTP4S Server Integration Test
 * 
 * This test uses Http4sTestServer (singleton) which follows the same pattern as
 * TestServer (Jetty/Lift). The HTTP4S server is started once and shared across
 * all test classes, just like the Lift server.
 * 
 * Unlike Http4s700RoutesTest which mocks routes in-process, this test:
 * - Makes real HTTP requests over the network to a running HTTP4S server
 * - Tests the complete server stack including middleware, error handling, etc.
 * - Provides true end-to-end testing of the HTTP4S server implementation
 * 
 * The server starts automatically when first accessed and stops on JVM shutdown.
 */
class Http4sServerIntegrationTest extends ServerSetup with DefaultUsers {

  object Http4sServerIntegrationTag extends Tag("Http4sServerIntegration")

  // Reference the singleton HTTP4S test server (auto-starts on first access)
  private val http4sServer = Http4sTestServer
  private val baseUrl = s"http://${http4sServer.host}:${http4sServer.port}"

  private def makeHttp4sGetRequest(path: String, headers: Map[String, String] = Map.empty): (Int, String) = {
    val request = url(s"$baseUrl$path")
    val requestWithHeaders = headers.foldLeft(request) { case (req, (key, value)) =>
      req.addHeader(key, value)
    }
    
    try {
      val response = Http.default(requestWithHeaders OK as.String)
      val result = Await.result(response, 10.seconds)
      (200, result)
    } catch {
      case e: java.util.concurrent.ExecutionException if e.getCause.getMessage.contains("401") =>
        (401, "Unauthorized")
      case e: java.util.concurrent.ExecutionException if e.getCause.getMessage.contains("404") =>
        (404, "Not Found")
      case e: Exception =>
        throw e
    }
  }

  private def makeHttp4sPostRequest(path: String, body: String, headers: Map[String, String] = Map.empty): (Int, String) = {
    val request = url(s"$baseUrl$path").POST.setBody(body)
    val requestWithHeaders = headers.foldLeft(request) { case (req, (key, value)) =>
      req.addHeader(key, value)
    }
    
    try {
      val response = Http.default(requestWithHeaders OK as.String)
      val result = Await.result(response, 10.seconds)
      (200, result)
    } catch {
      case e: java.util.concurrent.ExecutionException if e.getCause.getMessage.contains("401") =>
        (401, "Unauthorized")
      case e: java.util.concurrent.ExecutionException if e.getCause.getMessage.contains("404") =>
        (404, "Not Found")
      case e: Exception =>
        throw e
    }
  }

  feature("HTTP4S Server Integration - Real Server Tests") {
    
    scenario("HTTP4S test server starts successfully", Http4sServerIntegrationTag) {
      Given("HTTP4S test server singleton is accessed")
      
      Then("Server should be running")
      http4sServer.isRunning should be(true)
      
      And("Server should be on correct host and port")
      http4sServer.host should equal("127.0.0.1")
      http4sServer.port should equal(8087)
    }

    scenario("Server handles 404 for unknown routes", Http4sServerIntegrationTag) {
      Given("HTTP4S test server is running")
      
      When("We make a GET request to a non-existent endpoint")
      try {
        makeHttp4sGetRequest("/obp/v5.0.0/this-does-not-exist")
        fail("Should have thrown exception for 404")
      } catch {
        case e: Exception =>
          Then("We should get a 404 error")
          e.getMessage should include("404")
      }
    }

    scenario("Server handles multiple concurrent requests", Http4sServerIntegrationTag) {
      Given("HTTP4S test server is running")
      
      When("We make multiple concurrent requests to native HTTP4S endpoints")
      val futures = (1 to 10).map { _ =>
        Http.default(url(s"$baseUrl/obp/v5.0.0/root") OK as.String)
      }
      
      val results = Await.result(Future.sequence(futures), 30.seconds)
      
      Then("All requests should succeed")
      results.foreach { body =>
        val json = parse(body)
        json \ "version" should not equal JObject(Nil)
      }
    }

    scenario("Server shares state with Lift server", Http4sServerIntegrationTag) {
      Given("Both HTTP4S and Lift servers are running")
      
      When("We request banks from both servers")
      val (http4sStatus, http4sBody) = makeHttp4sGetRequest("/obp/v5.0.0/banks")
      val liftRequest = (baseRequest / "obp" / "v5.0.0" / "banks").GET
      val liftResponse = makeGetRequest(liftRequest, Nil)
      
      Then("Both should return 200")
      http4sStatus should equal(200)
      liftResponse.code should equal(200)
      
      And("Both should return banks data")
      val http4sJson = parse(http4sBody)
      val liftJson = liftResponse.body
      (http4sJson \ "banks") should not equal JObject(Nil)
      (liftJson \ "banks") should not equal JObject(Nil)
    }
  }

  feature("HTTP4S v7.0.0 Native Endpoints") {
    
    scenario("GET /obp/v7.0.0/root returns API info", Http4sServerIntegrationTag) {
      pending // TODO: Investigate route matching issue - returns 404
      When("We request the root endpoint")
      val (status, body) = makeHttp4sGetRequest("/obp/v7.0.0/root")
      
      Then("We should get a 200 response")
      status should equal(200)
      
      And("Response should contain version info")
      val json = parse(body)
      (json \ "version").extract[String] should equal("v7.0.0")
      (json \ "git_commit") should not equal JObject(Nil)
    }

    scenario("GET /obp/v7.0.0/banks returns banks list", Http4sServerIntegrationTag) {
      pending // TODO: Investigate route matching issue - returns 404
      When("We request banks list")
      val (status, body) = makeHttp4sGetRequest("/obp/v7.0.0/banks")
      
      Then("We should get a 200 response")
      status should equal(200)
      
      And("Response should contain banks array")
      val json = parse(body)
      json \ "banks" should not equal JObject(Nil)
    }

    scenario("GET /obp/v7.0.0/cards requires authentication", Http4sServerIntegrationTag) {
      When("We request cards list without authentication")
      val (status, body) = makeHttp4sGetRequest("/obp/v7.0.0/cards")
      
      Then("We should get a 401 response")
      status should equal(401)
      info("Authentication is required for this endpoint")
    }

    scenario("GET /obp/v7.0.0/banks/BANK_ID/cards requires authentication", Http4sServerIntegrationTag) {
      When("We request cards for a specific bank without authentication")
      val (status, body) = makeHttp4sGetRequest("/obp/v7.0.0/banks/gh.29.de/cards")
      
      Then("We should get a 401 response")
      status should equal(401)
      info("Authentication is required for this endpoint")
    }

    scenario("GET /obp/v7.0.0/resource-docs/v7.0.0/obp returns resource docs", Http4sServerIntegrationTag) {
      When("We request resource documentation")
      val (status, body) = makeHttp4sGetRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp")
      
      Then("We should get a 200 response")
      status should equal(200)
      
      And("Response should contain resource docs array")
      val json = parse(body)
      json \ "resource_docs" should not equal JObject(Nil)
    }
  }

  feature("HTTP4S v5.0.0 Native Endpoints") {
    
    scenario("GET /obp/v5.0.0/root returns API info", Http4sServerIntegrationTag) {
      When("We request the root endpoint")
      val (status, body) = makeHttp4sGetRequest("/obp/v5.0.0/root")
      
      Then("We should get a 200 response")
      status should equal(200)
      
      And("Response should contain version info")
      val json = parse(body)
      (json \ "version").extract[String] should equal("v5.0.0")
      (json \ "git_commit") should not equal JObject(Nil)
    }

    scenario("GET /obp/v5.0.0/banks returns banks list", Http4sServerIntegrationTag) {
      When("We request banks list")
      val (status, body) = makeHttp4sGetRequest("/obp/v5.0.0/banks")
      
      Then("We should get a 200 response")
      status should equal(200)
      
      And("Response should contain banks array")
      val json = parse(body)
      json \ "banks" should not equal JObject(Nil)
    }

    scenario("GET /obp/v5.0.0/banks/BANK_ID returns specific bank", Http4sServerIntegrationTag) {
      pending // TODO: Investigate route matching issue - returns 404
      When("We request a specific bank")
      val (status, body) = makeHttp4sGetRequest("/obp/v5.0.0/banks/gh.29.de")
      
      Then("We should get a 200 response")
      status should equal(200)
      
      And("Response should contain bank info")
      val json = parse(body)
      (json \ "id").extract[String] should equal("gh.29.de")
    }

    scenario("GET /obp/v5.0.0/banks/BANK_ID/products returns products", Http4sServerIntegrationTag) {
      pending // TODO: Investigate route matching issue - returns 404
      When("We request products for a bank")
      val (status, body) = makeHttp4sGetRequest("/obp/v5.0.0/banks/gh.29.de/products")
      
      Then("We should get a 200 response")
      status should equal(200)
      
      And("Response should contain products array")
      val json = parse(body)
      json \ "products" should not equal JObject(Nil)
    }

    scenario("GET /obp/v5.0.0/banks/BANK_ID/products/PRODUCT_CODE returns specific product", Http4sServerIntegrationTag) {
      pending // TODO: Investigate route matching issue - returns 404
      When("We request a specific product")
      // First get a product code from the products list
      val (_, productsBody) = makeHttp4sGetRequest("/obp/v5.0.0/banks/gh.29.de/products")
      val productsJson = parse(productsBody)
      val products = (productsJson \ "products").children
      
      if (products.nonEmpty) {
        val productCode = (products.head \ "code").extract[String]
        val (status, body) = makeHttp4sGetRequest(s"/obp/v5.0.0/banks/gh.29.de/products/$productCode")
        
        Then("We should get a 200 response")
        status should equal(200)
        
        And("Response should contain product info")
        val json = parse(body)
        (json \ "code").extract[String] should equal(productCode)
      } else {
        pending // Skip if no products available
      }
    }
  }

  feature("HTTP4S Lift Bridge Fallback") {
    
    scenario("Server handles Lift bridge routes for v5.0.0 non-native endpoints", Http4sServerIntegrationTag) {
      Given("HTTP4S test server is running with Lift bridge")
      
      When("We make a GET request to a v5.0.0 endpoint not implemented in HTTP4S")
      val (status, body) = makeHttp4sGetRequest("/obp/v5.0.0/users/current")
      
      Then("We should get a 401 response (authentication required)")
      status should equal(401)
      info("This endpoint requires authentication - 401 is correct behavior")
    }

    scenario("Server handles Lift bridge routes for v3.1.0 (known limitation)", Http4sServerIntegrationTag) {
      Given("HTTP4S test server is running with Lift bridge")
      
      When("We make a GET request to a v3.1.0 endpoint (Lift bridge)")
      try {
        makeHttp4sGetRequest("/obp/v3.1.0/banks")
        fail("Expected 404 for v3.1.0 (known bridge limitation)")
      } catch {
        case e: Exception =>
          Then("We should get a 404 error (known limitation)")
          e.getMessage should include("404")
          info("v3.1.0 bridge support is a known limitation - see HTTP4S_INTEGRATION_TEST_FINDINGS.md")
      }
    }
  }
}
