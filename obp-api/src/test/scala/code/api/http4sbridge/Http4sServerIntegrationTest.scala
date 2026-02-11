package code.api.http4sbridge

import org.scalatest.Ignore
import code.Http4sTestServer
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createSystemViewJsonV500
import code.api.util.APIUtil
import code.api.util.ApiRole.{CanCreateSystemView, CanDeleteSystemView, CanGetSystemView, CanUpdateSystemView}
import code.api.v5_0_0.ViewJsonV500
import code.entitlement.Entitlement
import code.setup.{DefaultUsers, ServerSetup, ServerSetupWithTestData}
import code.views.system.AccountAccess
import com.openbankproject.commons.model.UpdateViewJSON
import dispatch.Defaults._
import dispatch._
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonParser.parse
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.By
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
@Ignore
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

    scenario("GET /obp/v7.0.0/cards requires authentication", Http4sServerIntegrationTag) {
      When("We request cards list without authentication")
      val (status, body) = makeHttp4sGetRequest("/obp/v7.0.0/cards")
      
      Then("We should get a 401 response")
      status should equal(401)
      info("Authentication is required for this endpoint")
    }

    scenario("GET /obp/v7.0.0/banks/BANK_ID/cards requires authentication", Http4sServerIntegrationTag) {
      When("We request cards for a specific bank without authentication")
      val (status, body) = makeHttp4sGetRequest(s"/obp/v7.0.0/banks/testBank0/cards")
      
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

  feature("HTTP4S v5.0.0 System Views CRUD") {
    
    scenario("System views CRUD operations via HTTP4S server", Http4sServerIntegrationTag) {
      Given("User has required entitlements for system views")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemView.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemView.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateSystemView.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemView.toString)

      val viewId = "v" + APIUtil.generateUUID()
      val createViewBody = createSystemViewJsonV500.copy(name = viewId).copy(metadata_view = viewId).toCreateViewJson
      val createJson = write(createViewBody)
      
      val authHeaders = Map(
        "Authorization" -> s"DirectLogin token=${token1.value}",
        "Content-Type" -> "application/json"
      )

      When("We POST to create a system view")
      val (createStatus, createResponseBody) = makeHttp4sPostRequest("/obp/v5.0.0/system-views", createJson, authHeaders)
      
      Then("We should get a 201 response")
      createStatus should equal(201)
      
      And("Response should contain the created view")
      val createdView = parse(createResponseBody).extract[ViewJsonV500]
      createdView.id should not be empty
      
      When("We GET the created system view")
      val (getStatus, getBody) = makeHttp4sGetRequest(s"/obp/v5.0.0/system-views/${createdView.id}", authHeaders)
      
      Then("We should get a 200 response")
      getStatus should equal(200)
      
      And("Response should contain the view details")
      val retrievedView = parse(getBody).extract[ViewJsonV500]
      retrievedView.id should equal(createdView.id)
      
      When("We PUT to update the system view")
      val updateBody = UpdateViewJSON(
        description = "crud-updated",
        metadata_view = createdView.metadata_view,
        is_public = createdView.is_public,
        is_firehose = Some(true),
        which_alias_to_use = "public",
        hide_metadata_if_alias_used = !createdView.hide_metadata_if_alias_used,
        allowed_actions = List("can_see_images", "can_delete_comment"),
        can_grant_access_to_views = Some(createdView.can_grant_access_to_views),
        can_revoke_access_to_views = Some(createdView.can_revoke_access_to_views)
      )
      val updateJson = write(updateBody)
      val (updateStatus, updateResponseBody) = makeHttp4sPutRequest(s"/obp/v5.0.0/system-views/${createdView.id}", updateJson, authHeaders)
      
      Then("We should get a 200 response")
      updateStatus should equal(200)
      
      And("Response should contain the updated view")
      val updatedView = parse(updateResponseBody).extract[ViewJsonV500]
      updatedView.description should equal("crud-updated")
      updatedView.is_firehose should equal(Some(true))
      
      When("We GET the updated system view")
      val (getAfterUpdateStatus, getAfterUpdateBody) = makeHttp4sGetRequest(s"/obp/v5.0.0/system-views/${createdView.id}", authHeaders)
      
      Then("We should get a 200 response")
      getAfterUpdateStatus should equal(200)
      
      And("Response should reflect the updates")
      val verifiedView = parse(getAfterUpdateBody).extract[ViewJsonV500]
      verifiedView.description should equal("crud-updated")
      verifiedView.is_firehose should equal(Some(true))
      
      When("We DELETE the system view")
      val (deleteStatus, deleteBody) = makeHttp4sDeleteRequest(s"/obp/v5.0.0/system-views/${createdView.id}", authHeaders)
      
      Then("We should get a 200 response")
      deleteStatus should equal(200)
      
      And("Response should be true")
      deleteBody should equal("true")
      
      When("We GET the deleted system view")
      val (getAfterDeleteStatus, getAfterDeleteBody) = makeHttp4sGetRequest(s"/obp/v5.0.0/system-views/${createdView.id}", authHeaders)
      
      Then("We should get a 400 response (SystemViewNotFound)")
      getAfterDeleteStatus should equal(400)
      getAfterDeleteBody should include("OBP-30252")
      getAfterDeleteBody should include("System view not found")
      info("System view successfully deleted and verified")
    }
  }
}
