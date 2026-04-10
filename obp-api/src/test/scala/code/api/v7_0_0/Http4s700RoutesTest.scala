package code.api.v7_0_0

import code.Http4sTestServer
import code.api.Constant.SYSTEM_OWNER_VIEW_ID
import code.api.ResponseHeader
import code.api.util.APIUtil
import code.api.util.ApiRole.{canCreateEntitlementAtAnyBank, canDeleteEntitlementAtAnyBank, canGetAnyUser, canGetCardsForBank, canGetCustomersAtOneBank, canReadResourceDoc}
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, BankNotFound, UserHasMissingRoles}
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.metadata.counterparties.Counterparties
import com.openbankproject.commons.model.{BankId => CommBankId, CreditLimit, CreditRating, CustomerFaceImage}

import java.util.Date
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

  private def makeHttpRequestWithBody(
    method: String,
    path: String,
    body: String,
    headers: Map[String, String] = Map.empty
  ): (Int, JValue, Map[String, String]) = {
    val base = url(s"$baseUrl$path")
    val withHeaders = (headers + ("Content-Type" -> "application/json")).foldLeft(base) {
      case (req, (key, value)) => req.addHeader(key, value)
    }
    val methodReq = method.toUpperCase match {
      case "POST" => withHeaders.POST << body
      case "PUT"  => withHeaders.PUT  << body
      case _      => withHeaders << body
    }

    try {
      val response = Http.default(
        methodReq.setHeader("Accept", "*/*") > as.Response(p =>
          (p.getStatusCode, p.getResponseBody, p.getHeaders.iterator().asScala.map(e => e.getKey -> e.getValue).toMap)
        )
      )
      val (statusCode, responseBody, responseHeaders) = Await.result(response, 10.seconds)
      val json = if (responseBody.trim.isEmpty) JObject(Nil) else parse(responseBody)
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
      case "POST"    => withHeaders.POST
      case "PUT"     => withHeaders.PUT
      case "DELETE"  => withHeaders.DELETE
      case "OPTIONS" => withHeaders.OPTIONS
      case "PATCH"   => withHeaders.PATCH
      case "HEAD"    => withHeaders.HEAD
      case _         => withHeaders
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

  /** Create a MappedCustomer row directly for a given bank. Returns the new customer ID. */
  private def createTestCustomer(bankId: String): String =
    CustomerX.customerProvider.vend.addCustomer(
      bankId = CommBankId(bankId),
      number = APIUtil.generateUUID(),
      legalName = "Test Customer",
      mobileNumber = "+49123456789",
      email = "testcustomer@example.com",
      faceImage = CustomerFaceImage(new Date(), ""),
      dateOfBirth = new Date(),
      relationshipStatus = "Single",
      dependents = 0,
      dobOfDependents = Nil,
      highestEducationAttained = "Bachelor",
      employmentStatus = "Employed",
      kycStatus = false,
      lastOkDate = new Date(),
      creditRating = Some(CreditRating("AAA", "Standard")),
      creditLimit = Some(CreditLimit("EUR", "10000.00")),
      title = "Mr",
      branchId = "",
      nameSuffix = ""
    ).openOrThrowException("Expected customer to be created").customerId

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

  // ─── CORS preflight ──────────────────────────────────────────────────────────

  feature("Http4s700 CORS preflight") {

    scenario("OPTIONS /obp/v7.0.0/banks returns 204 with CORS headers without Lift overhead", Http4s700RoutesTag) {
      Given("OPTIONS /obp/v7.0.0/banks — a browser preflight request")
      val (statusCode, _, headers) = makeHttpRequestWithMethod("OPTIONS", "/obp/v7.0.0/banks")

      Then("Response is 204 No Content with all required CORS headers")
      statusCode shouldBe 204
      headers.find { case (k, _) => k.equalsIgnoreCase("Access-Control-Allow-Origin") }
        .map(_._2) shouldBe Some("*")
      hasHeader(headers, "Access-Control-Allow-Methods") shouldBe true
      hasHeader(headers, "Access-Control-Allow-Headers") shouldBe true
      hasHeader(headers, "Access-Control-Allow-Credentials") shouldBe true
    }

    scenario("OPTIONS /obp/v7.0.0/cards returns 204 with CORS headers", Http4s700RoutesTag) {
      Given("OPTIONS /obp/v7.0.0/cards — preflight for an authenticated endpoint")
      val (statusCode, _, headers) = makeHttpRequestWithMethod("OPTIONS", "/obp/v7.0.0/cards")

      Then("Response is 204 No Content — no auth required for preflight")
      statusCode shouldBe 204
      hasHeader(headers, "Access-Control-Allow-Origin") shouldBe true
    }

    scenario("OPTIONS /obp/v7.0.0/banks/BANK_ID/cards returns 204 with CORS headers", Http4s700RoutesTag) {
      Given("OPTIONS /obp/v7.0.0/banks/BANK_ID/cards — preflight for a nested endpoint")
      val bankId = testBankId1.value
      val (statusCode, _, headers) = makeHttpRequestWithMethod("OPTIONS", s"/obp/v7.0.0/banks/$bankId/cards")

      Then("Response is 204 No Content with CORS headers")
      statusCode shouldBe 204
      hasHeader(headers, "Access-Control-Allow-Origin") shouldBe true
    }
  }

  // ─── routing priority guard ───────────────────────────────────────────────────
  //
  // allRoutes is built by sorting ResourceDocs by URL segment count (descending),
  // so most-specific routes win automatically. These scenarios verify the sort
  // produces the correct outcome. Add one scenario per new route to keep CI coverage.

  feature("Http4s700 routing priority") {

    scenario("GET /banks/BANK_ID/cards is served by getCardsForBank, not getBanks", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/cards without auth")
      val bankId = testBankId1.value

      When("Making HTTP request — if getBanks shadowed getCardsForBank this would return 200 with banks array")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/cards")

      Then("Response is 401 (auth required) — proving getCardsForBank matched, not getBanks")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(AuthenticatedUserIsRequired)
            case _ =>
              fail("Expected message field — if this is a banks list, getBanks is shadowing getCardsForBank")
          }
        case _ =>
          fail("Expected JSON object")
      }
    }

    scenario("GET /banks returns banks list, not intercepted by getCardsForBank", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks without auth")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/banks")

      Then("Response is 200 with banks array — proving getBanks matched")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("banks") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected banks array — getBanks may not have matched")
          }
        case _ =>
          fail("Expected JSON object")
      }
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

  // ─── getCurrentUser ───────────────────────────────────────────────────────────

  feature("Http4s700 getCurrentUser endpoint") {

    scenario("Reject unauthenticated access to /users/current", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/users/current with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/users/current")

      Then("Response is 401 with AuthenticatedUserIsRequired message")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(AuthenticatedUserIsRequired)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return user info JSON when authenticated", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/users/current with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/users/current", headers)

      Then("Response is 200 with user_id, username, email fields")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val keys = fields.map(_.name)
          keys should contain("user_id")
          keys should contain("username")
          keys should contain("email")
        case _ => fail("Expected JSON object for getCurrentUser")
      }
    }

    scenario("Returned user_id matches the authenticated user", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/users/current with DirectLogin header for resourceUser1")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/users/current", headers)

      Then("Response contains user_id equal to resourceUser1.userId")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("user_id") match {
            case Some(JString(uid)) => uid shouldBe resourceUser1.userId
            case _ => fail("Expected user_id field as JSON string")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ─── getBank ─────────────────────────────────────────────────────────────────

  feature("Http4s700 getBank endpoint") {

    scenario("Return bank info JSON without authentication", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID with no auth headers")
      val bankId = testBankId1.value
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId")

      Then("Response is 200 with bank_id, full_name fields")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val fieldMap = toFieldMap(fields)
          fieldMap.get("bank_id") match {
            case Some(JString(id)) => id shouldBe bankId
            case _ => fail(s"Expected bank_id field as JSON string, got: ${fields.map(_.name)}")
          }
          val keys = fields.map(_.name)
          keys should contain("full_name")
        case _ => fail("Expected JSON object for getBank")
      }
    }

    scenario("Return 404 when bank does not exist", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/non-existing-bank with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/banks/non-existing-bank-id")

      Then("Response is 404 with BankNotFound message")
      statusCode shouldBe 404
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(BankNotFound)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ─── getCoreAccountById ───────────────────────────────────────────────────────

  feature("Http4s700 getCoreAccountById endpoint") {

    scenario("Reject unauthenticated access to core account", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/my/banks/BANK_ID/accounts/ACCOUNT_ID/account with no auth")
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/my/banks/$bankId/accounts/$accountId/account")

      Then("Response is 401")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(AuthenticatedUserIsRequired)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return core account JSON when authenticated and account owner", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/my/banks/BANK_ID/accounts/ACCOUNT_ID/account with DirectLogin header")
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/my/banks/$bankId/accounts/$accountId/account", headers)

      Then("Response is 200 with account_id and balance fields")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val keys = fields.map(_.name)
          keys should contain("account_id")
          keys should contain("balance")
        case _ => fail("Expected JSON object for getCoreAccountById")
      }
    }
  }

  // ─── getPrivateAccountByIdFull ────────────────────────────────────────────────

  feature("Http4s700 getPrivateAccountByIdFull endpoint") {

    scenario("Reject unauthenticated access to full account", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account with no auth")
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val viewId = SYSTEM_OWNER_VIEW_ID
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/$viewId/account")

      Then("Response is 401")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(AuthenticatedUserIsRequired)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return full account JSON when authenticated with view access", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/owner/account with DirectLogin header")
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val viewId = SYSTEM_OWNER_VIEW_ID
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/$viewId/account", headers)

      Then("Response is 200 with id, views_available, and balance fields")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val keys = fields.map(_.name)
          keys should contain("id")
          keys should contain("views_available")
          keys should contain("balance")
        case _ => fail("Expected JSON object for getPrivateAccountByIdFull")
      }
    }
  }

  // ─── getExplicitCounterpartyById ─────────────────────────────────────────────

  feature("Http4s700 getExplicitCounterpartyById endpoint") {

    scenario("Reject unauthenticated access to counterparty", Http4s700RoutesTag) {
      Given("GET .../counterparties/COUNTERPARTY_ID with no auth")
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val viewId = SYSTEM_OWNER_VIEW_ID
      val (statusCode, json, _) = makeHttpRequest(
        s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/$viewId/counterparties/some-id"
      )

      Then("Response is 401")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(AuthenticatedUserIsRequired)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return counterparty JSON when authenticated and counterparty exists", Http4s700RoutesTag) {
      Given("A counterparty (with metadata) created on testAccountId0 in testBankId1")
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val viewId = SYSTEM_OWNER_VIEW_ID
      val counterparty = createCounterparty(bankId, accountId, accountId, isBeneficiary = true, resourceUser1.userId)
      val counterpartyId = counterparty.counterpartyId
      // getMetadata requires a MappedCounterpartyMetadata row — createCounterparty does not create one
      Counterparties.counterparties.vend.getOrCreateMetadata(
        com.openbankproject.commons.model.BankId(bankId),
        com.openbankproject.commons.model.AccountId(accountId),
        counterpartyId,
        counterparty.name
      ).openOrThrowException("Expected counterparty metadata to be created")

      When("GET .../counterparties/COUNTERPARTY_ID with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(
        s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/$viewId/counterparties/$counterpartyId",
        headers
      )

      Then("Response is 200 with counterparty_id field")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("counterparty_id") match {
            case Some(JString(id)) => id shouldBe counterpartyId
            case _ => fail("Expected counterparty_id field as JSON string")
          }
        case _ => fail("Expected JSON object for getExplicitCounterpartyById")
      }
    }
  }

  // ─── deleteEntitlement ────────────────────────────────────────────────────────

  feature("Http4s700 deleteEntitlement endpoint") {

    scenario("Reject unauthenticated DELETE to /entitlements/ENTITLEMENT_ID", Http4s700RoutesTag) {
      Given("DELETE /obp/v7.0.0/entitlements/some-id with no auth")
      val (statusCode, json, _) = makeHttpRequestWithMethod("DELETE", "/obp/v7.0.0/entitlements/some-id")

      Then("Response is 401")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(AuthenticatedUserIsRequired)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 403 when authenticated but missing canDeleteEntitlementAtAnyBank role", Http4s700RoutesTag) {
      Given("DELETE /obp/v7.0.0/entitlements/some-id without the required role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithMethod("DELETE", "/obp/v7.0.0/entitlements/some-id", headers)

      Then("Response is 403 with UserHasMissingRoles message")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canDeleteEntitlementAtAnyBank.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 204 when authenticated with role and entitlement exists", Http4s700RoutesTag) {
      Given("An entitlement created for resourceUser1 and canDeleteEntitlementAtAnyBank granted")
      addEntitlement("", resourceUser1.userId, canDeleteEntitlementAtAnyBank.toString)
      val targetEntitlement = Entitlement.entitlement.vend
        .addEntitlement(testBankId1.value, resourceUser1.userId, canGetCardsForBank.toString)
        .openOrThrowException("Expected entitlement to be created")

      When("DELETE /obp/v7.0.0/entitlements/{entitlementId} with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, _, _) = makeHttpRequestWithMethod(
        "DELETE", s"/obp/v7.0.0/entitlements/${targetEntitlement.entitlementId}", headers)

      Then("Response is 204 No Content")
      statusCode shouldBe 204
    }

    scenario("Return 204 even when entitlement ID does not exist (idempotent)", Http4s700RoutesTag) {
      Given("canDeleteEntitlementAtAnyBank role granted and a non-existent entitlement ID")
      addEntitlement("", resourceUser1.userId, canDeleteEntitlementAtAnyBank.toString)

      When("DELETE /obp/v7.0.0/entitlements/non-existent-id with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, _, _) = makeHttpRequestWithMethod(
        "DELETE", "/obp/v7.0.0/entitlements/non-existent-entitlement-id-xyz", headers)

      Then("Response is 204 — delete is idempotent")
      statusCode shouldBe 204
    }
  }

  // ─── addEntitlement ───────────────────────────────────────────────────────────

  feature("Http4s700 addEntitlement endpoint") {

    scenario("Reject unauthenticated POST to /users/USER_ID/entitlements", Http4s700RoutesTag) {
      Given("POST /obp/v7.0.0/users/USER_ID/entitlements with no auth")
      val body = s"""{"bank_id":"${testBankId1.value}","role_name":"CanGetAnyUser"}"""
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/users/${resourceUser1.userId}/entitlements", body)

      Then("Response is 401")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(AuthenticatedUserIsRequired)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 403 when authenticated but missing canCreateEntitlementAtAnyBank role", Http4s700RoutesTag) {
      Given("POST /obp/v7.0.0/users/USER_ID/entitlements without the required role")
      val body = s"""{"bank_id":"","role_name":"CanGetAnyUser"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/users/${resourceUser1.userId}/entitlements", body, headers)

      Then("Response is 403 with UserHasMissingRoles message")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(UserHasMissingRoles)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 201 with entitlement JSON when authenticated with role and valid body", Http4s700RoutesTag) {
      Given("canCreateEntitlementAtAnyBank role granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canCreateEntitlementAtAnyBank.toString)

      When("POST /obp/v7.0.0/users/USER_ID/entitlements with a valid body")
      val body = s"""{"bank_id":"","role_name":"CanGetAnyUser"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/users/${resourceUser1.userId}/entitlements", body, headers)

      Then("Response is 201 with entitlement_id, role_name fields")
      statusCode shouldBe 201
      json match {
        case JObject(fields) =>
          val keys = fields.map(_.name)
          keys should contain("entitlement_id")
          keys should contain("role_name")
          toFieldMap(fields).get("role_name") match {
            case Some(JString(role)) => role shouldBe "CanGetAnyUser"
            case _ => fail("Expected role_name as JSON string")
          }
        case _ => fail("Expected JSON object for addEntitlement")
      }
    }

    scenario("Return 400 when role_name is not a valid API role", Http4s700RoutesTag) {
      Given("canCreateEntitlementAtAnyBank role granted and an invalid role_name in body")
      addEntitlement("", resourceUser1.userId, canCreateEntitlementAtAnyBank.toString)

      When("POST /obp/v7.0.0/users/USER_ID/entitlements with invalid role_name")
      val body = s"""{"bank_id":"","role_name":"NotARealRole"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, _, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/users/${resourceUser1.userId}/entitlements", body, headers)

      Then("Response is 400")
      statusCode shouldBe 400
    }
  }

  // ─── getFeatures ──────────────────────────────────────────────────────────────

  feature("Http4s700 getFeatures endpoint") {

    scenario("Return features JSON without authentication", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/features with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/features")

      Then("Response is 200 with feature boolean fields")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val keys = fields.map(_.name)
          keys should contain("allow_public_views")
          keys should contain("allow_direct_login")
          keys should contain("allow_oauth2_login")
        case _ => fail("Expected JSON object for getFeatures")
      }
    }

    scenario("allow_direct_login reflects props value", Http4s700RoutesTag) {
      Given("allow_direct_login prop set to true")
      setPropsValues("allow_direct_login" -> "true")

      When("GET /obp/v7.0.0/features")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/features")

      Then("Response contains allow_direct_login = true")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("allow_direct_login") shouldBe Some(JBool(true))
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ─── getScannedApiVersions ────────────────────────────────────────────────────

  feature("Http4s700 getScannedApiVersions endpoint") {

    scenario("Return scanned_api_versions array without authentication", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/api/versions with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/api/versions")

      Then("Response is 200 with scanned_api_versions array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("scanned_api_versions") match {
            case Some(JArray(versions)) =>
              versions should not be empty
            case _ => fail("Expected scanned_api_versions array")
          }
        case _ => fail("Expected JSON object for getScannedApiVersions")
      }
    }

    scenario("Version entries contain required fields", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/api/versions")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/api/versions")

      Then("Each version entry has url_prefix, api_standard, fully_qualified_version, is_active")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("scanned_api_versions") match {
            case Some(JArray(versions)) =>
              versions.headOption match {
                case Some(JObject(vFields)) =>
                  val keys = vFields.map(_.name)
                  keys should contain("url_prefix")
                  keys should contain("api_standard")
                  keys should contain("fully_qualified_version")
                  keys should contain("is_active")
                case _ => fail("Expected version entry to be a JSON object")
              }
            case _ => fail("Expected scanned_api_versions array")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ─── getConnectors ────────────────────────────────────────────────────────────

  feature("Http4s700 getConnectors endpoint") {

    scenario("Return connectors array without authentication", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/system/connectors with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/connectors")

      Then("Response is 200 with connectors array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("connectors") match {
            case Some(JArray(connectors)) =>
              connectors should not be empty
            case _ => fail("Expected connectors array")
          }
        case _ => fail("Expected JSON object for getConnectors")
      }
    }

    scenario("Connector entries contain required fields", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/system/connectors")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/connectors")

      Then("Each connector has connector_name and is_available_in_method_routing fields")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("connectors") match {
            case Some(JArray(connectors)) =>
              connectors.headOption match {
                case Some(JObject(cFields)) =>
                  val keys = cFields.map(_.name)
                  keys should contain("connector_name")
                  keys should contain("is_available_in_method_routing")
                case _ => fail("Expected connector entry to be a JSON object")
              }
            case _ => fail("Expected connectors array")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ─── getProviders ─────────────────────────────────────────────────────────────

  feature("Http4s700 getProviders endpoint") {

    scenario("Reject unauthenticated access to /providers", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/providers with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/providers")

      Then("Response is 401 with AuthenticatedUserIsRequired message")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(AuthenticatedUserIsRequired)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return providers array when authenticated", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/providers with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/providers", headers)

      Then("Response is 200 with providers array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("providers") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected providers array")
          }
        case _ => fail("Expected JSON object for getProviders")
      }
    }
  }

  // ─── getUsers ─────────────────────────────────────────────────────────────────

  feature("Http4s700 getUsers endpoint") {

    scenario("Reject unauthenticated access to /users", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/users with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/users")

      Then("Response is 401 with AuthenticatedUserIsRequired message")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(AuthenticatedUserIsRequired)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 403 when authenticated but missing canGetAnyUser role", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/users with DirectLogin header but no role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/users", headers)

      Then("Response is 403 with UserHasMissingRoles")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canGetAnyUser.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return users list when authenticated with canGetAnyUser role", Http4s700RoutesTag) {
      Given("canGetAnyUser role granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canGetAnyUser.toString)

      When("GET /obp/v7.0.0/users with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/users", headers)

      Then("Response is 200 with users array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("users") match {
            case Some(JArray(users)) =>
              users should not be empty
            case _ => fail("Expected users array")
          }
        case _ => fail("Expected JSON object for getUsers")
      }
    }
  }

  // ─── getCustomersAtOneBank ────────────────────────────────────────────────────

  feature("Http4s700 getCustomersAtOneBank endpoint") {

    scenario("Reject unauthenticated access to /banks/BANK_ID/customers", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/customers with no auth headers")
      val bankId = testBankId1.value
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/customers")

      Then("Response is 401 with AuthenticatedUserIsRequired message")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(AuthenticatedUserIsRequired)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 403 when authenticated but missing canGetCustomersAtOneBank role", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/customers without the required role")
      val bankId = testBankId1.value
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/customers", headers)

      Then("Response is 403 with UserHasMissingRoles")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canGetCustomersAtOneBank.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return customers list when authenticated with canGetCustomersAtOneBank role", Http4s700RoutesTag) {
      Given("canGetCustomersAtOneBank role and a customer at the bank")
      val bankId = testBankId1.value
      addEntitlement(bankId, resourceUser1.userId, canGetCustomersAtOneBank.toString)
      createTestCustomer(bankId)

      When("GET /obp/v7.0.0/banks/BANK_ID/customers with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/customers", headers)

      Then("Response is 200 with customers array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("customers") match {
            case Some(JArray(customers)) =>
              customers should not be empty
            case _ => fail("Expected customers array")
          }
        case _ => fail("Expected JSON object for getCustomersAtOneBank")
      }
    }
  }

  // ─── getCustomerByCustomerId ──────────────────────────────────────────────────

  feature("Http4s700 getCustomerByCustomerId endpoint") {

    scenario("Reject unauthenticated access to /banks/BANK_ID/customers/CUSTOMER_ID", Http4s700RoutesTag) {
      Given("GET .../customers/some-id with no auth")
      val bankId = testBankId1.value
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/customers/some-customer-id")

      Then("Response is 401")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(AuthenticatedUserIsRequired)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return customer JSON when authenticated with canGetCustomersAtOneBank role", Http4s700RoutesTag) {
      Given("A customer created at testBankId1 and canGetCustomersAtOneBank role granted")
      val bankId = testBankId1.value
      addEntitlement(bankId, resourceUser1.userId, canGetCustomersAtOneBank.toString)
      val customerId = CustomerX.customerProvider.vend.addCustomer(
        bankId = CommBankId(bankId),
        number = APIUtil.generateUUID(),
        legalName = "Jane Doe",
        mobileNumber = "+49987654321",
        email = "jane@example.com",
        faceImage = CustomerFaceImage(new Date(), ""),
        dateOfBirth = new Date(),
        relationshipStatus = "Married",
        dependents = 1,
        dobOfDependents = Nil,
        highestEducationAttained = "Master",
        employmentStatus = "Employed",
        kycStatus = true,
        lastOkDate = new Date(),
        creditRating = None,
        creditLimit = None,
        title = "Ms",
        branchId = "",
        nameSuffix = ""
      ).openOrThrowException("Expected customer to be created").customerId

      When("GET /obp/v7.0.0/banks/BANK_ID/customers/CUSTOMER_ID with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/customers/$customerId", headers)

      Then("Response is 200 with customer_id field")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("customer_id") match {
            case Some(JString(id)) => id shouldBe customerId
            case _ => fail("Expected customer_id field as JSON string")
          }
        case _ => fail("Expected JSON object for getCustomerByCustomerId")
      }
    }
  }

  // ─── getAccountsAtBank ───────────────────────────────────────────────────────

  feature("Http4s700 getAccountsAtBank endpoint") {

    scenario("Reject unauthenticated access to /banks/BANK_ID/accounts", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/accounts with no auth headers")
      val bankId = testBankId1.value
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/accounts")

      Then("Response is 401 with AuthenticatedUserIsRequired message")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(AuthenticatedUserIsRequired)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return accounts list when authenticated with access to the bank", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/accounts with DirectLogin header")
      val bankId = testBankId1.value
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/accounts", headers)

      Then("Response is 200 with accounts array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("accounts") match {
            case Some(JArray(accounts)) =>
              accounts should not be empty
            case _ => fail("Expected accounts array")
          }
        case _ => fail("Expected JSON object for getAccountsAtBank")
      }
    }

    scenario("Account entries contain required fields", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/accounts with DirectLogin header")
      val bankId = testBankId1.value
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/accounts", headers)

      Then("Each account entry has account_id, bank_id, label, views_available")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("accounts") match {
            case Some(JArray(accounts)) =>
              accounts.headOption match {
                case Some(JObject(aFields)) =>
                  val keys = aFields.map(_.name)
                  keys should contain("account_id")
                  keys should contain("bank_id")
                  keys should contain("label")
                  keys should contain("views_available")
                case _ => fail("Expected account entry to be a JSON object")
              }
            case _ => fail("Expected accounts array")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 404 for non-existent bank", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/non-existing-bank/accounts with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/banks/non-existing-bank-xyz/accounts", headers)

      Then("Response is 404 with BankNotFound message")
      statusCode shouldBe 404
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(BankNotFound)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }
}
