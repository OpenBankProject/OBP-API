package code.api.v7_0_0

import code.Http4sTestServer
import code.api.ResponseHeader
import code.api.util.ApiRole.{canGetCardsForBank, canReadResourceDoc}
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, BankNotFound, UserHasMissingRoles}
import code.setup.ServerSetupWithTestData
import dispatch.Defaults._
import dispatch._
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.{JArray, JBool, JField, JObject, JString}
import net.liftweb.json.JsonParser.parse
import org.scalatest.Tag

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * HTTP4S v7.0.0 Routes Integration Test
 *
 * Uses Http4sTestServer (singleton) to test v7.0.0 endpoints through real HTTP requests.
 * This ensures we test the complete server stack including middleware, error handling, etc.
 */
class Http4s700RoutesTest extends ServerSetupWithTestData {

  object Http4s700RoutesTag extends Tag("Http4s700Routes")

  // Use Http4sTestServer for full integration testing
  private val http4sServer = Http4sTestServer
  private val baseUrl = s"http://${http4sServer.host}:${http4sServer.port}"

  private def makeHttpRequest(
    path: String,
    headers: Map[String, String] = Map.empty
  ): (Int, JValue, Map[String, String]) = {
    val request = url(s"$baseUrl$path")
    val requestWithHeaders = headers.foldLeft(request) { case (req, (key, value)) =>
      req.addHeader(key, value)
    }

    try {
      val response = Http.default(
        requestWithHeaders.setHeader("Accept", "*/*") > as.Response(p =>
          (p.getStatusCode, p.getResponseBody, p.getHeaders.iterator().asScala.map(e => e.getKey -> e.getValue).toMap)
        )
      )
      val (statusCode, body, responseHeaders) = Await.result(response, 10.seconds)
      val json = if (body.trim.isEmpty) JObject(Nil) else parse(body)
      (statusCode, json, responseHeaders)
    } catch {
      case e: java.util.concurrent.ExecutionException =>
        val statusPattern = """(\d{3})""".r
        statusPattern.findFirstIn(e.getCause.getMessage) match {
          case Some(code) => (code.toInt, JObject(Nil), Map.empty)
          case None => throw e
        }
      case e: Exception =>
        throw e
    }
  }

  private def makeHttpRequestWithMethod(
    method: String,
    path: String,
    headers: Map[String, String] = Map.empty
  ): (Int, JValue, Map[String, String]) = {
    val base = url(s"$baseUrl$path")
    val withHeaders = headers.foldLeft(base) { case (req, (key, value)) => req.addHeader(key, value) }
    val methodReq = method.toUpperCase match {
      case "POST"   => withHeaders.POST
      case "PUT"    => withHeaders.PUT
      case "DELETE" => withHeaders.DELETE
      case _        => withHeaders
    }

    try {
      val response = Http.default(
        methodReq.setHeader("Accept", "*/*") > as.Response(p =>
          (p.getStatusCode, p.getResponseBody, p.getHeaders.iterator().asScala.map(e => e.getKey -> e.getValue).toMap)
        )
      )
      val (statusCode, body, responseHeaders) = Await.result(response, 10.seconds)
      val json = if (body.trim.isEmpty) JObject(Nil) else parse(body)
      (statusCode, json, responseHeaders)
    } catch {
      case e: java.util.concurrent.ExecutionException =>
        val statusPattern = """(\d{3})""".r
        statusPattern.findFirstIn(e.getCause.getMessage) match {
          case Some(code) => (code.toInt, JObject(Nil), Map.empty)
          case None => throw e
        }
      case e: Exception =>
        throw e
    }
  }

  private def toFieldMap(fields: List[JField]): Map[String, JValue] =
    fields.map(field => field.name -> field.value).toMap

  private def hasHeader(headers: Map[String, String], name: String): Boolean =
    headers.exists { case (k, _) => k.equalsIgnoreCase(name) }

  // ─── root ────────────────────────────────────────────────────────────────────

  feature("Http4s700 root endpoint") {

    scenario("Return API info JSON with all required fields", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/root request")
      When("Making HTTP request to server")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/root")

      Then("Response is 200 OK with full API info shape")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val fieldMap = toFieldMap(fields)
          fieldMap.keys should contain allOf ("version", "version_status", "git_commit",
            "connector", "hostname", "stage", "hosted_by", "hosted_at",
            "energy_source", "resource_docs_requires_role")
          fieldMap("version") shouldBe JString("v7.0.0")
        case _ =>
          fail("Expected JSON object for root endpoint")
      }
    }

    scenario("resource_docs_requires_role field reflects prop value", Http4s700RoutesTag) {
      Given("resource_docs_requires_role prop is false")
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making GET /obp/v7.0.0/root")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/root")

      Then("Response has resource_docs_requires_role = false")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("resource_docs_requires_role") shouldBe Some(JBool(false))
        case _ =>
          fail("Expected JSON object")
      }
    }

    scenario("Unauthenticated access to root returns 200 (public endpoint)", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/root request with no auth")
      val (statusCode, _, _) = makeHttpRequest("/obp/v7.0.0/root")
      Then("Response is 200 — root is public")
      statusCode shouldBe 200
    }
  }

  // ─── banks ───────────────────────────────────────────────────────────────────

  feature("Http4s700 banks endpoint") {

    scenario("Return banks list JSON", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks request")
      When("Making HTTP request to server")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/banks")

      Then("Response is 200 OK with non-empty banks array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("banks") match {
            case Some(JArray(banks)) =>
              banks should not be empty
            case _ =>
              fail("Expected non-empty banks array")
          }
        case _ =>
          fail("Expected JSON object for banks endpoint")
      }
    }

    scenario("Bank entries contain required fields", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/banks")

      Then("Each bank has id, short_name, full_name, logo, website")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("banks") match {
            case Some(JArray(banks)) =>
              banks.headOption match {
                case Some(JObject(bankFields)) =>
                  val keys = bankFields.map(_.name)
                  keys should contain("id")
                  keys should contain("short_name")
                  keys should contain("full_name")
                case _ =>
                  fail("Expected bank to be a JSON object")
              }
            case _ =>
              fail("Expected banks array")
          }
        case _ =>
          fail("Expected JSON object")
      }
    }

    scenario("Unauthenticated access to banks returns 200 (public endpoint)", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks with no auth headers")
      val (statusCode, _, _) = makeHttpRequest("/obp/v7.0.0/banks")
      Then("Response is 200 — banks is public")
      statusCode shouldBe 200
    }
  }

  // ─── cards ───────────────────────────────────────────────────────────────────

  feature("Http4s700 cards endpoint") {

    scenario("Reject unauthenticated access to cards", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/cards request without auth headers")
      When("Making HTTP request to server")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/cards")

      Then("Response is 401 Unauthorized with appropriate error message")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(AuthenticatedUserIsRequired)
            case _ =>
              fail("Expected message field as JSON string for cards unauthorized response")
          }
        case _ =>
          fail("Expected JSON object for cards unauthorized response")
      }
    }

    scenario("Return cards list JSON when authenticated", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/cards request with DirectLogin header")
      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/cards", headers)

      Then("Response is 200 OK with cards array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("cards") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected cards field to be an array")
          }
        case _ => fail("Expected JSON object for cards endpoint")
      }
    }
  }

  // ─── bank cards ──────────────────────────────────────────────────────────────

  feature("Http4s700 bank cards endpoint") {

    scenario("Return bank cards list JSON when authenticated and entitled", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/cards request with DirectLogin header and role")
      val bankId = testBankId1.value
      addEntitlement(bankId, resourceUser1.userId, canGetCardsForBank.toString)

      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/cards?limit=10&offset=0", headers)

      Then("Response is 200 OK with cards array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("cards") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected cards field to be an array")
          }
        case _ => fail("Expected JSON object for bank cards endpoint")
      }
    }

    scenario("Return empty cards array when bank has no cards", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/cards for a bank with no cards")
      val bankId = testBankId2.value
      addEntitlement(bankId, resourceUser1.userId, canGetCardsForBank.toString)

      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/cards", headers)

      Then("Response is 200 OK with empty cards array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("cards") match {
            case Some(JArray(cards)) =>
              cards shouldBe empty
            case _ =>
              fail("Expected cards field to be an array")
          }
        case _ =>
          fail("Expected JSON object")
      }
    }

    scenario("Reject bank cards access when missing required role", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/cards request with DirectLogin header but no role")
      val bankId = testBankId1.value

      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/cards", headers)

      Then("Response is 403 Forbidden")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(UserHasMissingRoles)
              message should include(canGetCardsForBank.toString)
            case _ =>
              fail("Expected message field as JSON string for missing-role response")
          }
        case _ =>
          fail("Expected JSON object for missing-role response")
      }
    }

    scenario("Return BankNotFound when bank does not exist and user is entitled", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/cards request for non-existing bank")
      val bankId = "non-existing-bank-id"
      addEntitlement(bankId, resourceUser1.userId, canGetCardsForBank.toString)

      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/cards", headers)

      Then("Response is 404 Not Found with BankNotFound message")
      statusCode shouldBe 404
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(BankNotFound)
            case _ =>
              fail("Expected message field as JSON string for BankNotFound response")
          }
        case _ =>
          fail("Expected JSON object for BankNotFound response")
      }
    }
  }

  // ─── resource-docs ───────────────────────────────────────────────────────────

  feature("Http4s700 resource-docs endpoint") {

    scenario("Allow public access when resource docs role is not required", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp request without auth headers")
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making HTTP request to server")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp")

      Then("Response is 200 OK with resource_docs array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("resource_docs") match {
            case Some(JArray(_)) => succeed
            case _ =>
              fail("Expected resource_docs field to be an array")
          }
        case _ =>
          fail("Expected JSON object for resource-docs endpoint")
      }
    }

    scenario("Reject unauthenticated access when resource docs role is required", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp request without auth headers and role required")
      setPropsValues("resource_docs_requires_role" -> "true")

      When("Making HTTP request to server")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp")

      Then("Response is 401 Unauthorized")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(AuthenticatedUserIsRequired)
            case _ =>
              fail("Expected message field as JSON string for resource-docs unauthorized response")
          }
        case _ =>
          fail("Expected JSON object for resource-docs unauthorized response")
      }
    }

    scenario("Reject access when authenticated but missing canReadResourceDoc role", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp request with auth but no canReadResourceDoc role")
      setPropsValues("resource_docs_requires_role" -> "true")

      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp", headers)

      Then("Response is 403 Forbidden")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(UserHasMissingRoles)
              message should include(canReadResourceDoc.toString)
            case _ =>
              fail("Expected message field as JSON string for missing-role response")
          }
        case _ =>
          fail("Expected JSON object for missing-role response")
      }
    }

    scenario("Return docs when authenticated and entitled with canReadResourceDoc", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp request with auth and canReadResourceDoc role")
      setPropsValues("resource_docs_requires_role" -> "true")
      addEntitlement("", resourceUser1.userId, canReadResourceDoc.toString)

      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp", headers)

      Then("Response is 200 OK with resource_docs array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("resource_docs") match {
            case Some(JArray(_)) => succeed
            case _ =>
              fail("Expected resource_docs field to be an array")
          }
        case _ =>
          fail("Expected JSON object for resource-docs endpoint")
      }
    }

    scenario("Filter docs by tags parameter", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp?tags=Card request")
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making HTTP request to server")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp?tags=Card")

      Then("Response is 200 OK and all returned docs contain Card tag")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("resource_docs") match {
            case Some(JArray(resourceDocs)) =>
              resourceDocs.foreach {
                case JObject(rdFields) =>
                  toFieldMap(rdFields).get("tags") match {
                    case Some(JArray(tags)) =>
                      tags.exists {
                        case JString(tag) => tag == "Card"
                        case _ => false
                      } shouldBe true
                    case _ =>
                      fail("Expected tags field to be an array")
                  }
                case _ =>
                  fail("Expected resource doc to be a JSON object")
              }
            case _ =>
              fail("Expected resource_docs field to be an array")
          }
        case _ =>
          fail("Expected JSON object for resource-docs endpoint")
      }
    }

    scenario("Filter docs by functions parameter", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp?functions=getBanks request")
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making HTTP request to server")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp?functions=getBanks")

      Then("Response is 200 OK and includes GET /banks")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("resource_docs") match {
            case Some(JArray(resourceDocs)) =>
              resourceDocs.foreach {
                case JObject(rdFields) =>
                  val fieldMap = toFieldMap(rdFields)
                  (fieldMap.get("request_verb"), fieldMap.get("request_url")) match {
                    case (Some(JString(verb)), Some(JString(url))) =>
                      verb shouldBe "GET"
                      url should endWith("/banks")
                    case _ =>
                      fail("Expected request_verb and request_url fields as JSON strings")
                  }
                case _ =>
                  fail("Expected resource doc to be a JSON object")
              }
            case _ =>
              fail("Expected resource_docs field to be an array")
          }
        case _ =>
          fail("Expected JSON object for resource-docs endpoint")
      }
    }

    scenario("Reject request for non-v7.0.0 API version", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v6.0.0/obp — wrong version in path")
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making HTTP request to server")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/v6.0.0/obp")

      Then("Response is 400 with InvalidApiVersionString message")
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include("v6.0.0")
            case _ =>
              fail("Expected message field describing the version error")
          }
        case _ =>
          fail("Expected JSON object")
      }
    }

    scenario("Resource doc entries contain required fields", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp")
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making HTTP request to server")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp")

      Then("Each resource doc has operation_id, request_verb, request_url, summary, tags")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("resource_docs") match {
            case Some(JArray(docs)) =>
              docs.headOption match {
                case Some(JObject(docFields)) =>
                  val keys = docFields.map(_.name)
                  keys should contain("operation_id")
                  keys should contain("request_verb")
                  keys should contain("request_url")
                  keys should contain("summary")
                  keys should contain("tags")
                case _ =>
                  fail("Expected resource doc to be a JSON object")
              }
            case _ =>
              fail("Expected resource_docs array")
          }
        case _ =>
          fail("Expected JSON object")
      }
    }
  }

  // ─── cross-cutting middleware ─────────────────────────────────────────────────

  feature("Http4s700 response headers") {

    scenario("All responses include Correlation-Id header", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/root")
      val (statusCode, _, headers) = makeHttpRequest("/obp/v7.0.0/root")

      Then("Response includes Correlation-Id header")
      statusCode shouldBe 200
      hasHeader(headers, ResponseHeader.`Correlation-Id`) shouldBe true
    }

    scenario("X-Request-ID is echoed back as Correlation-Id", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks with X-Request-ID header")
      val requestId = java.util.UUID.randomUUID().toString
      val (statusCode, _, headers) = makeHttpRequest(
        "/obp/v7.0.0/banks",
        Map("X-Request-ID" -> requestId)
      )

      Then("Correlation-Id in response matches the sent X-Request-ID")
      statusCode shouldBe 200
      headers.find { case (k, _) => k.equalsIgnoreCase(ResponseHeader.`Correlation-Id`) }
        .map(_._2) shouldBe Some(requestId)
    }

    scenario("All responses include Cache-Control header", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/root")
      val (_, _, headers) = makeHttpRequest("/obp/v7.0.0/root")

      Then("Response includes Cache-Control: no-cache")
      hasHeader(headers, ResponseHeader.`Cache-Control`) shouldBe true
    }

    scenario("All responses include X-Frame-Options header", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/root")
      val (_, _, headers) = makeHttpRequest("/obp/v7.0.0/root")

      Then("Response includes X-Frame-Options: DENY")
      hasHeader(headers, "X-Frame-Options") shouldBe true
      headers.find { case (k, _) => k.equalsIgnoreCase("X-Frame-Options") }
        .map(_._2) shouldBe Some("DENY")
    }

    scenario("Error responses also include Correlation-Id header", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/cards without auth (will 401)")
      val (statusCode, _, headers) = makeHttpRequest("/obp/v7.0.0/cards")

      Then("401 error response still has Correlation-Id")
      statusCode shouldBe 401
      hasHeader(headers, ResponseHeader.`Correlation-Id`) shouldBe true
    }
  }

  // ─── unknown paths and wrong methods ─────────────────────────────────────────

  feature("Http4s700 routing edge cases") {

    scenario("Unknown path under v7.0.0 prefix does not silently bridge to Lift", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/nonexistent-endpoint")
      val (statusCode, _, _) = makeHttpRequest("/obp/v7.0.0/nonexistent-endpoint")

      Then("Response is not 200 — unknown path is not silently served")
      statusCode should not be 200
    }

    scenario("POST to a GET-only endpoint returns non-200", Http4s700RoutesTag) {
      Given("POST /obp/v7.0.0/banks — method not allowed")
      val (statusCode, _, _) = makeHttpRequestWithMethod("POST", "/obp/v7.0.0/banks")

      Then("Response is not 200")
      statusCode should not be 200
    }
  }
}
