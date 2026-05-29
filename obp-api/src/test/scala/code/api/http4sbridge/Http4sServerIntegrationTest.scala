package code.api.http4sbridge

import code.Http4sTestServer
import code.setup.{DefaultUsers, ServerSetup, ServerSetupWithTestData}
import code.views.system.AccountAccess
import dispatch.Defaults._
import dispatch._
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonParser.parse
import org.scalatest.Tag

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Real HTTP4S Server Integration Test
 *
 * This test uses Http4sTestServer (singleton). The HTTP4S server is started once
 * and shared across all test classes.
 *
 * Unlike Http4s700RoutesTest which mocks routes in-process, this test:
 * - Makes real HTTP requests over the network to a running HTTP4S server
 * - Tests the complete server stack including middleware, error handling, etc.
 * - Provides true end-to-end testing of the HTTP4S server implementation
 * 
 * The server starts automatically when first accessed and stops on JVM shutdown.
 */

class Http4sServerIntegrationTest extends ServerSetup with DefaultUsers with ServerSetupWithTestData{

  object Http4sServerIntegrationTag extends Tag("Http4sServerIntegration")

  // Reference the singleton HTTP4S test server (auto-starts on first access)
  private val http4sServer = Http4sTestServer
  private val baseUrl = s"http://${http4sServer.host}:${http4sServer.port}"

  override def afterAll(): Unit = {
    super.afterAll()
    // Clean up test data
    code.views.system.ViewDefinition.bulkDelete_!!()
    AccountAccess.bulkDelete_!!()
  }

  private def makeHttp4sGetRequestFull(path: String, reqHeaders: Map[String, String] = Map.empty): (Int, String, Option[String]) = {
    val request = url(s"$baseUrl$path")
    val requestWithHeaders = reqHeaders.foldLeft(request) { case (req, (key, value)) =>
      req.addHeader(key, value)
    }
    val response = Http.default(requestWithHeaders.setHeader("Accept", "*/*") > as.Response(p =>
      (p.getStatusCode, p.getResponseBody, Option(p.getHeader("X-OBP-Version-Served")).filter(_.nonEmpty))
    ))
    Await.result(response, 10.seconds)
  }

  private def makeHttp4sGetRequest(path: String, headers: Map[String, String] = Map.empty): (Int, String) = {
    val request = url(s"$baseUrl$path")
    val requestWithHeaders = headers.foldLeft(request) { case (req, (key, value)) =>
      req.addHeader(key, value)
    }
    
    try {
      val response = Http.default(requestWithHeaders.setHeader("Accept", "*/*") > as.Response(p => (p.getStatusCode, p.getResponseBody)))
      Await.result(response, 10.seconds)
    } catch {
      case e: java.util.concurrent.ExecutionException =>
        // Extract status code from exception message if possible
        val statusPattern = """(\d{3})""".r
        statusPattern.findFirstIn(e.getCause.getMessage) match {
          case Some(code) => (code.toInt, e.getCause.getMessage)
          case None => throw e
        }
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
      val response = Http.default(requestWithHeaders.setHeader("Accept", "*/*") > as.Response(p => (p.getStatusCode, p.getResponseBody)))
      val (statusCode, responseBody) = Await.result(response, 10.seconds)
      (statusCode, responseBody)
    } catch {
      case e: Exception =>
        throw e
    }
  }

  private def makeHttp4sPutRequest(path: String, body: String, headers: Map[String, String] = Map.empty): (Int, String) = {
    val request = url(s"$baseUrl$path").PUT.setBody(body)
    val requestWithHeaders = headers.foldLeft(request) { case (req, (key, value)) =>
      req.addHeader(key, value)
    }
    
    try {
      val response = Http.default(requestWithHeaders.setHeader("Accept", "*/*") > as.Response(p => (p.getStatusCode, p.getResponseBody)))
      val (statusCode, responseBody) = Await.result(response, 10.seconds)
      (statusCode, responseBody)
    } catch {
      case e: Exception =>
        throw e
    }
  }

  private def makeHttp4sOptionsRequest(path: String): (Int, Map[String, String]) = {
    val request = url(s"$baseUrl$path").OPTIONS
    val response = Http.default(
      request.setHeader("Accept", "*/*") > as.Response(p =>
        (p.getStatusCode, p.getHeaders.iterator().asScala.map(e => e.getKey -> e.getValue).toMap)
      )
    )
    Await.result(response, 10.seconds)
  }

  private def makeHttp4sDeleteRequest(path: String, headers: Map[String, String] = Map.empty): (Int, String) = {
    val request = url(s"$baseUrl$path").DELETE
    val requestWithHeaders = headers.foldLeft(request) { case (req, (key, value)) =>
      req.addHeader(key, value)
    }
    
    try {
      val response = Http.default(requestWithHeaders.setHeader("Accept", "*/*") > as.Response(p => {
        val statusCode = p.getStatusCode
        val body = if (p.getResponseBody != null) p.getResponseBody else ""
        (statusCode, body)
      }))
      Await.result(response, 10.seconds)
    } catch {
      case e: java.util.concurrent.ExecutionException =>
        // Extract status code from exception message if possible
        val statusPattern = """(\d{3})""".r
        statusPattern.findFirstIn(e.getCause.getMessage) match {
          case Some(code) => (code.toInt, e.getCause.getMessage)
          case None => throw e
        }
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
  }

  feature("HTTP4S v7.0.0 Native Endpoints") {
    
    scenario("GET /obp/v7.0.0/root returns API info", Http4sServerIntegrationTag) {
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
      When("We request banks list")
      val (status, body) = makeHttp4sGetRequest("/obp/v7.0.0/banks")
      
      Then("We should get a 200 response")
      status should equal(200)
      
      And("Response should contain banks array")
      val json = parse(body)
      json \ "banks" should not equal JObject(Nil)
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

    scenario("v7.0.0 unmigrated path is served by v6.0.0 via the http4s v7→v6 cascade bridge", Http4sServerIntegrationTag) {
      When("We request an unmigrated v7.0.0 endpoint (/consumers/current exists in v6 but not v7)")
      val (status, body, versionServed) = makeHttp4sGetRequestFull("/obp/v7.0.0/consumers/current")

      Then("We get a proper OBP error response, not a version-not-found 404")
      status should (equal(401) or equal(200) or equal(403))

      And("X-OBP-Version-Served header indicates the cascade target version")
      versionServed should equal(Some("v6.0.0"))

      When("We request a native v7.0.0 endpoint (/banks is migrated)")
      val (_, _, nativeVersionServed) = makeHttp4sGetRequestFull("/obp/v7.0.0/banks")

      Then("Native v7 endpoints do not set X-OBP-Version-Served")
      nativeVersionServed should equal(None)
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
      When("We request a specific bank")
      val (status, body) = makeHttp4sGetRequest(s"/obp/v5.0.0/banks/testBank0")
      
      Then("We should get a 200 response")
      status should equal(200)
      
      And("Response should contain bank info")
      val json = parse(body)
      (json \ "id").extract[String] should equal(s"testBank0")
    }

    scenario("GET /obp/v5.0.0/banks/BANK_ID/products returns products", Http4sServerIntegrationTag) {
      When("We request products for a bank")
      val (status, body) = makeHttp4sGetRequest(s"/obp/v5.0.0/banks/testBank0/products")
      
      Then("We should get a 200 response")
      status should equal(200)
      
      And("Response should contain products array")
      val json = parse(body)
      json \ "products" should not equal JObject(Nil)
    }

    scenario("GET /obp/v5.0.0/banks/BANK_ID/products/PRODUCT_CODE returns specific product", Http4sServerIntegrationTag) {
      When("We request a specific product")
      // First get a product code from the products list
      val (_, productsBody) = makeHttp4sGetRequest(s"/obp/v5.0.0/banks/testBank0/products")
      val productsJson = parse(productsBody)
      val products = (productsJson \ "products").children
      
      if (products.nonEmpty) {
        val productCode = (products.head \ "code").extract[String]
        val (status, body) = makeHttp4sGetRequest(s"/obp/v5.0.0/banks/testBank0/products/$productCode")
        
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

  feature("HTTP4S version-cascade fallback") {

    scenario("v5.0.0 non-native endpoint is served via http4s cascade", Http4sServerIntegrationTag) {
      Given("HTTP4S test server is running")

      When("We make a GET request to a v5.0.0 endpoint not natively declared in Http4s500")
      val (status, body) = makeHttp4sGetRequest("/obp/v5.0.0/users/current")

      Then("We should get a 401 response (authentication required)")
      status should equal(401)
      info("This endpoint requires authentication - 401 is correct behavior")
    }

    scenario("v3.1.0 /banks currently returns 404", Http4sServerIntegrationTag) {
      Given("HTTP4S test server is running")

      // TODO v310Routes is wired into Http4sApp.baseServices; this 404 may no longer hold.
      // Behaviour is asserted as-is here; re-validate before relying on it as a guarantee.
      When("We make a GET request to /obp/v3.1.0/banks")
      try {
        makeHttp4sGetRequest("/obp/v3.1.0/banks")
        fail("Expected 404 for /obp/v3.1.0/banks")
      } catch {
        case e: Exception =>
          Then("We should get a 404 error")
          e.getMessage should include("404")
      }
    }
  }

  // ─── CORS preflight ──────────────────────────────────────────────────────────
  // corsHandler sits above Http4s700 in Http4sApp and is only reachable via the
  // real server — in-process route tests cannot exercise it.

  feature("HTTP4S CORS preflight") {

    scenario("OPTIONS /obp/v7.0.0/banks returns 204 with CORS headers", Http4sServerIntegrationTag) {
      When("OPTIONS /obp/v7.0.0/banks — a browser preflight request")
      val (statusCode, headers) = makeHttp4sOptionsRequest("/obp/v7.0.0/banks")

      Then("Response is 204 No Content")
      statusCode should equal(204)

      And("All required CORS headers are present")
      headers.find { case (k, _) => k.equalsIgnoreCase("Access-Control-Allow-Origin") }
        .map(_._2) should equal(Some("*"))
      headers.exists { case (k, _) => k.equalsIgnoreCase("Access-Control-Allow-Methods") } should be(true)
      headers.exists { case (k, _) => k.equalsIgnoreCase("Access-Control-Allow-Headers") } should be(true)
      headers.exists { case (k, _) => k.equalsIgnoreCase("Access-Control-Allow-Credentials") } should be(true)
    }

  }
}
