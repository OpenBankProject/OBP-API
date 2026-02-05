package code.api.v5_0_0

import code.Http4sTestServer
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil
import code.api.util.ApiRole.{CanCreateSystemView, CanDeleteSystemView, CanGetSystemView, CanUpdateSystemView}
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, SystemViewNotFound, UserHasMissingRoles}
import code.setup.ServerSetupWithTestData
import code.views.system.AccountAccess
import dispatch.Defaults._
import dispatch._
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.json.JsonParser.parse
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.By
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * HTTP4S v5.0.0 System Views CRUD Integration Test
 * 
 * Tests the native HTTP4S implementation of system-views endpoints.
 * Uses Http4sTestServer for full integration testing through real HTTP requests.
 */
class Http4s500SystemViewsTest extends ServerSetupWithTestData {
  
  object Http4s500SystemViewsTag extends Tag("Http4s500SystemViews")

  // Use Http4sTestServer for full integration testing
  private val http4sServer = Http4sTestServer
  private val baseUrl = s"http://${http4sServer.host}:${http4sServer.port}"

  private def makeHttpRequest(
    method: String,
    path: String, 
    headers: Map[String, String] = Map.empty,
    body: Option[String] = None
  ): (Int, JValue) = {
    val request = url(s"$baseUrl$path")
    val requestWithHeaders = headers.foldLeft(request) { case (req, (key, value)) =>
      req.addHeader(key, value)
    }
    
    val finalRequest = method.toUpperCase match {
      case "GET" => requestWithHeaders
      case "POST" => requestWithHeaders.POST.setBody(body.getOrElse("")).setContentType("application/json", java.nio.charset.StandardCharsets.UTF_8)
      case "PUT" => requestWithHeaders.PUT.setBody(body.getOrElse("")).setContentType("application/json", java.nio.charset.StandardCharsets.UTF_8)
      case "DELETE" => requestWithHeaders.DELETE
      case _ => requestWithHeaders
    }
    
    try {
      val response = Http.default(finalRequest.setHeader("Accept", "*/*") > as.Response(p => (p.getStatusCode, p.getResponseBody)))
      val (statusCode, responseBody) = Await.result(response, 10.seconds)
      val json = if (responseBody.trim.isEmpty) JObject(Nil) else parse(responseBody)
      (statusCode, json)
    } catch {
      case e: java.util.concurrent.ExecutionException =>
        val statusPattern = """(\d{3})""".r
        statusPattern.findFirstIn(e.getCause.getMessage) match {
          case Some(code) => (code.toInt, JObject(Nil))
          case None => throw e
        }
      case e: Exception =>
        throw e
    }
  }

  private def toFieldMap(fields: List[JField]): Map[String, JValue] = {
    fields.map(field => field.name -> field.value).toMap
  }

  // Test data
  val randomSystemViewId = "a" + APIUtil.generateUUID()
  val postBodySystemViewJson = createSystemViewJsonV500
    .copy(name = randomSystemViewId)
    .copy(metadata_view = randomSystemViewId)
    .toCreateViewJson

  feature("Http4s500 POST /system-views - Create System View") {

    scenario("Reject unauthenticated access", Http4s500SystemViewsTag) {
      Given("POST /obp/v5.0.0/system-views request without auth headers")
      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest(
        "POST",
        "/obp/v5.0.0/system-views",
        body = Some(write(postBodySystemViewJson))
      )

      Then("Response is 401 Unauthorized")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(AuthenticatedUserIsRequired)
            case _ =>
              fail("Expected message field for unauthorized response")
          }
        case _ =>
          fail("Expected JSON object for unauthorized response")
      }
    }

    scenario("Reject authenticated access without required role", Http4s500SystemViewsTag) {
      Given("POST /obp/v5.0.0/system-views request with auth but no CanCreateSystemView role")
      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json) = makeHttpRequest(
        "POST",
        "/obp/v5.0.0/system-views",
        headers,
        Some(write(postBodySystemViewJson))
      )

      Then("Response is 403 Forbidden")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(UserHasMissingRoles)
              message should include(CanCreateSystemView.toString)
            case _ =>
              fail("Expected message field for missing-role response")
          }
        case _ =>
          fail("Expected JSON object for missing-role response")
      }
    }

    scenario("Create system view when authenticated and entitled", Http4s500SystemViewsTag) {
      Given("POST /obp/v5.0.0/system-views request with auth and CanCreateSystemView role")
      addEntitlement("", resourceUser1.userId, CanCreateSystemView.toString)
      
      val viewId = "test_view_" + APIUtil.generateUUID()
      val createViewJson = postBodySystemViewJson.copy(name = viewId).copy(metadata_view = viewId)

      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json) = makeHttpRequest(
        "POST",
        "/obp/v5.0.0/system-views",
        headers,
        Some(write(createViewJson))
      )

      Then("Response is 201 Created with view details")
      statusCode shouldBe 201
      json match {
        case JObject(fields) =>
          val fieldMap = toFieldMap(fields)
          fieldMap.get("id") match {
            case Some(JString(id)) =>
              id shouldBe viewId
            case _ =>
              fail("Expected id field in created view response")
          }
        case _ =>
          fail("Expected JSON object for created view response")
      }
    }
  }

  feature("Http4s500 GET /system-views/{VIEW_ID} - Get System View") {

    scenario("Reject unauthenticated access", Http4s500SystemViewsTag) {
      Given("GET /obp/v5.0.0/system-views/VIEW_ID request without auth headers")
      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest(
        "GET",
        "/obp/v5.0.0/system-views/owner"
      )

      Then("Response is 401 Unauthorized")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(AuthenticatedUserIsRequired)
            case _ =>
              fail("Expected message field for unauthorized response")
          }
        case _ =>
          fail("Expected JSON object for unauthorized response")
      }
    }

    scenario("Reject authenticated access without required role", Http4s500SystemViewsTag) {
      Given("GET /obp/v5.0.0/system-views/VIEW_ID request with auth but no CanGetSystemView role")
      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json) = makeHttpRequest(
        "GET",
        "/obp/v5.0.0/system-views/owner",
        headers
      )

      Then("Response is 403 Forbidden")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(UserHasMissingRoles)
              message should include(CanGetSystemView.toString)
            case _ =>
              fail("Expected message field for missing-role response")
          }
        case _ =>
          fail("Expected JSON object for missing-role response")
      }
    }

    scenario("Get system view when authenticated and entitled", Http4s500SystemViewsTag) {
      Given("GET /obp/v5.0.0/system-views/VIEW_ID request with auth and CanGetSystemView role")
      
      // First create a view
      addEntitlement("", resourceUser1.userId, CanCreateSystemView.toString)
      val viewId = "test_view_get_" + APIUtil.generateUUID()
      val createViewJson = postBodySystemViewJson.copy(name = viewId).copy(metadata_view = viewId)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      makeHttpRequest("POST", "/obp/v5.0.0/system-views", headers, Some(write(createViewJson)))
      
      // Now get the view
      addEntitlement("", resourceUser1.userId, CanGetSystemView.toString)

      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest(
        "GET",
        s"/obp/v5.0.0/system-views/$viewId",
        headers
      )

      Then("Response is 200 OK with view details")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val fieldMap = toFieldMap(fields)
          fieldMap.get("id") match {
            case Some(JString(id)) =>
              id shouldBe viewId
            case _ =>
              fail("Expected id field in view response")
          }
        case _ =>
          fail("Expected JSON object for view response")
      }
    }

    scenario("Return 404 for non-existent view", Http4s500SystemViewsTag) {
      Given("GET /obp/v5.0.0/system-views/VIEW_ID request for non-existent view")
      addEntitlement("", resourceUser1.userId, CanGetSystemView.toString)

      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json) = makeHttpRequest(
        "GET",
        "/obp/v5.0.0/system-views/non_existent_view_id",
        headers
      )

      Then("Response is 404 Not Found")
      statusCode shouldBe 404
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(SystemViewNotFound)
            case _ =>
              fail("Expected message field for not found response")
          }
        case _ =>
          fail("Expected JSON object for not found response")
      }
    }
  }

  feature("Http4s500 PUT /system-views/{VIEW_ID} - Update System View") {

    scenario("Reject unauthenticated access", Http4s500SystemViewsTag) {
      Given("PUT /obp/v5.0.0/system-views/VIEW_ID request without auth headers")
      val updateJson = updateSystemViewJson500.copy(description = "Updated description")
      
      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest(
        "PUT",
        "/obp/v5.0.0/system-views/owner",
        body = Some(write(updateJson))
      )

      Then("Response is 401 Unauthorized")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(AuthenticatedUserIsRequired)
            case _ =>
              fail("Expected message field for unauthorized response")
          }
        case _ =>
          fail("Expected JSON object for unauthorized response")
      }
    }

    scenario("Reject authenticated access without required role", Http4s500SystemViewsTag) {
      Given("PUT /obp/v5.0.0/system-views/VIEW_ID request with auth but no CanUpdateSystemView role")
      val updateJson = updateSystemViewJson500.copy(description = "Updated description")
      
      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json) = makeHttpRequest(
        "PUT",
        "/obp/v5.0.0/system-views/owner",
        headers,
        Some(write(updateJson))
      )

      Then("Response is 403 Forbidden")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(UserHasMissingRoles)
              message should include(CanUpdateSystemView.toString)
            case _ =>
              fail("Expected message field for missing-role response")
          }
        case _ =>
          fail("Expected JSON object for missing-role response")
      }
    }

    scenario("Update system view when authenticated and entitled", Http4s500SystemViewsTag) {
      Given("PUT /obp/v5.0.0/system-views/VIEW_ID request with auth and CanUpdateSystemView role")
      
      // First create a view
      addEntitlement("", resourceUser1.userId, CanCreateSystemView.toString)
      val viewId = "test_view_update_" + APIUtil.generateUUID()
      val createViewJson = postBodySystemViewJson.copy(name = viewId).copy(metadata_view = viewId)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      makeHttpRequest("POST", "/obp/v5.0.0/system-views", headers, Some(write(createViewJson)))
      
      // Now update the view
      addEntitlement("", resourceUser1.userId, CanUpdateSystemView.toString)
      val updatedDescription = "Updated description for testing"
      val updateJson = updateSystemViewJson500.copy(
        description = updatedDescription,
        metadata_view = viewId,
        allowed_actions = List("can_see_images", "can_delete_comment")
      )

      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest(
        "PUT",
        s"/obp/v5.0.0/system-views/$viewId",
        headers,
        Some(write(updateJson))
      )

      Then("Response is 200 OK with updated view details")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val fieldMap = toFieldMap(fields)
          fieldMap.get("description") match {
            case Some(JString(desc)) =>
              desc shouldBe updatedDescription
            case _ =>
              fail("Expected description field in updated view response")
          }
        case _ =>
          fail("Expected JSON object for updated view response")
      }
    }
  }

  feature("Http4s500 DELETE /system-views/{VIEW_ID} - Delete System View") {

    scenario("Reject unauthenticated access", Http4s500SystemViewsTag) {
      Given("DELETE /obp/v5.0.0/system-views/VIEW_ID request without auth headers")
      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest(
        "DELETE",
        "/obp/v5.0.0/system-views/some_view"
      )

      Then("Response is 401 Unauthorized")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(AuthenticatedUserIsRequired)
            case _ =>
              fail("Expected message field for unauthorized response")
          }
        case _ =>
          fail("Expected JSON object for unauthorized response")
      }
    }

    scenario("Reject authenticated access without required role", Http4s500SystemViewsTag) {
      Given("DELETE /obp/v5.0.0/system-views/VIEW_ID request with auth but no CanDeleteSystemView role")
      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json) = makeHttpRequest(
        "DELETE",
        "/obp/v5.0.0/system-views/some_view",
        headers
      )

      Then("Response is 403 Forbidden")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(UserHasMissingRoles)
              message should include(CanDeleteSystemView.toString)
            case _ =>
              fail("Expected message field for missing-role response")
          }
        case _ =>
          fail("Expected JSON object for missing-role response")
      }
    }

    scenario("Delete system view when authenticated and entitled", Http4s500SystemViewsTag) {
      Given("DELETE /obp/v5.0.0/system-views/VIEW_ID request with auth and CanDeleteSystemView role")
      
      // First create a view
      addEntitlement("", resourceUser1.userId, CanCreateSystemView.toString)
      val viewId = "test_view_delete_" + APIUtil.generateUUID()
      val createViewJson = postBodySystemViewJson.copy(name = viewId).copy(metadata_view = viewId)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      makeHttpRequest("POST", "/obp/v5.0.0/system-views", headers, Some(write(createViewJson)))
      
      // Clean up any account access records
      AccountAccess.findAll(
        By(AccountAccess.view_id, viewId),
        By(AccountAccess.user_fk, resourceUser1.id.get)
      ).forall(_.delete_!)
      
      // Now delete the view
      addEntitlement("", resourceUser1.userId, CanDeleteSystemView.toString)

      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest(
        "DELETE",
        s"/obp/v5.0.0/system-views/$viewId",
        headers
      )

      Then("Response is 200 OK")
      statusCode shouldBe 200
    }
  }
}
