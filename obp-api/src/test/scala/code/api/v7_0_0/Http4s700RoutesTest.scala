package code.api.v7_0_0

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import code.api.util.http4s.Http4sLiftWebBridge
import code.api.Constant.SYSTEM_OWNER_VIEW_ID
import code.api.ResponseHeader
import code.api.util.APIUtil
import code.api.util.ApiRole.{canCreateEntitlementAtAnyBank, canCreateOrganisation, canCreateRoutingScheme, canDeleteEntitlementAtAnyBank, canDeleteOrganisation, canDeleteRoutingScheme, canGetAccountAccessTrace, canGetAnyOrganisation, canGetAnyUser, canGetCacheConfig, canGetCacheInfo, canGetCacheNamespaces, canGetCardsForBank, canGetConnectorHealth, canGetCustomersAtOneBank, canGetDatabasePoolInfo, canGetMigrations, canReadResourceDoc, canUpdateBankSupportedRoutingScheme, canUpdateOrganisation, canUpdateRoutingScheme}
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, BankNotFound, EntitlementAlreadyExists, InvalidOrganisationIdFormat, InvalidRoutingSchemeName, MobileWalletDestinationNotFound, MobileWalletInvalidMsisdn, OrganisationAlreadyExists, OrganisationNotFound, PayeeLookupAddressMismatch, PayeeLookupIdentifierTypeNotRegistered, PayeeNotFound, RoutingSchemeAlreadyExists, RoutingSchemeExampleAddressMismatch, RoutingSchemeNotFound, UserHasMissingRoles, UserNotFoundByUserId}
import code.routingscheme.RoutingSchemeX
import code.model.dataAccess.BankAccountRouting
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.organisation.OrganisationX
import code.metadata.counterparties.Counterparties
import com.openbankproject.commons.model.{BankId => CommBankId, CreditLimit, CreditRating, CustomerFaceImage}
import fs2.Stream
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.typelevel.ci.CIString

import java.util.Date
import code.setup.ServerSetupWithTestData
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.{JArray, JBool, JField, JObject, JString}
import net.liftweb.json.JsonParser.parse
import org.scalatest.Tag

/**
 * HTTP4S v7.0.0 Routes Test
 *
 * Drives Http4s700.wrappedRoutesV700Services (routes + ResourceDocMiddleware) in-process —
 * no TCP, no server startup.  Auth/role scenarios are ~5 ms each; DB-touching 200 scenarios
 * stay at ~400 ms but there are far fewer of them.
 *
 * CORS preflight behaviour is tested at the server level in Http4sServerIntegrationTest —
 * the CORS middleware sits above Http4s700 in the Http4sServer pipeline and is not reachable
 * from here.
 */
class Http4s700RoutesTest extends ServerSetupWithTestData {

  object Http4s700RoutesTag extends Tag("Http4s700Routes")

  implicit val runtime: IORuntime = IORuntime.global
  private val app = Http4s700.wrappedRoutesV700Services.orNotFound

  private def run(
    method: Method,
    path: String,
    headers: Map[String, String] = Map.empty,
    body: String = ""
  ): (Int, JValue, Map[String, String]) = {
    val uri     = Uri.unsafeFromString(path)
    val allHdrs = if (body.nonEmpty) headers + ("Content-Type" -> "application/json") else headers
    val hdrs    = Headers(allHdrs.map { case (k, v) => Header.Raw(CIString(k), v) }.toList)
    val bodyStream: fs2.Stream[IO, Byte] =
      if (body.nonEmpty) Stream.emits(body.getBytes("UTF-8")).covary[IO] else Stream.empty
    val req      = Request[IO](method, uri, headers = hdrs, body = bodyStream)
    val baseResp = app.run(req).unsafeRunSync()
    // Mirror Http4sApp: apply standard response headers (Correlation-Id, Cache-Control, etc.)
    val resp     = Http4sLiftWebBridge.ensureStandardHeaders(req, baseResp)
    val bodyStr  = resp.bodyText.compile.string.unsafeRunSync()
    val json = try {
      if (bodyStr.trim.isEmpty) JObject(Nil) else parse(bodyStr)
    } catch { case _: Exception => JObject(Nil) }
    val respHeaders = resp.headers.headers.map(h => h.name.toString -> h.value).toMap
    (resp.status.code, json, respHeaders)
  }

  private def makeHttpRequest(
    path: String,
    headers: Map[String, String] = Map.empty
  ): (Int, JValue, Map[String, String]) = run(Method.GET, path, headers)

  private def makeHttpRequestWithBody(
    method: String,
    path: String,
    body: String,
    headers: Map[String, String] = Map.empty
  ): (Int, JValue, Map[String, String]) = {
    val m = method.toUpperCase match {
      case "PUT" => Method.PUT
      case _     => Method.POST
    }
    run(m, path, headers, body)
  }

  private def makeHttpRequestWithMethod(
    method: String,
    path: String,
    headers: Map[String, String] = Map.empty
  ): (Int, JValue, Map[String, String]) = {
    val m = method.toUpperCase match {
      case "POST"   => Method.POST
      case "PUT"    => Method.PUT
      case "DELETE" => Method.DELETE
      case "PATCH"  => Method.PATCH
      case "HEAD"   => Method.HEAD
      case _        => Method.GET
    }
    run(m, path, headers)
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

    scenario("Serve v6.0.0 resource docs when v6.0.0 requested via v7 endpoint", Http4s700RoutesTag) {
      // Previously returned 400 — fixed by delegating to ImplementationsResourceDocs.getResourceDocsList
      Given("GET /obp/v7.0.0/resource-docs/v6.0.0/obp?functions=getBanks — filtered to avoid timeout")
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making HTTP request to server")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/v6.0.0/obp?functions=getBanks")

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
          fail("Expected JSON object")
      }
    }

    scenario("Return 400 for an unrecognised API version string", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/not-a-version/obp")
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making HTTP request to server")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/not-a-version/obp")

      Then("Response is 400 with error message containing the bad version string")
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include("not-a-version")
            case _ =>
              fail("Expected message field")
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
      Given("GET /obp/v7.0.0/users/current without auth (will 401)")
      val (statusCode, _, headers) = makeHttpRequest("/obp/v7.0.0/users/current")

      Then("401 error response still has Correlation-Id")
      statusCode shouldBe 401
      hasHeader(headers, ResponseHeader.`Correlation-Id`) shouldBe true
    }
  }

  // ─── CORS preflight ──────────────────────────────────────────────────────────
  // CORS is applied by Http4sServer above Http4s700 and is not reachable via in-process
  // route testing. OPTIONS preflight scenarios live in Http4sServerIntegrationTest.

  // ─── routing priority guard ───────────────────────────────────────────────────
  //
  // allRoutes is built by sorting ResourceDocs by URL segment count (descending),
  // so most-specific routes win automatically. These scenarios verify the sort
  // produces the correct outcome. Add one scenario per new route to keep CI coverage.

  feature("Http4s700 routing priority") {

    scenario("GET /banks returns banks list", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks without auth")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/banks")

      Then("Response is 200 with banks array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("banks") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected banks array")
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

    scenario("Return 409 when the entitlement already exists for the user", Http4s700RoutesTag) {
      Given("canCreateEntitlementAtAnyBank role granted and the target entitlement already created")
      addEntitlement("", resourceUser1.userId, canCreateEntitlementAtAnyBank.toString)
      addEntitlement("", resourceUser1.userId, canGetAnyUser.toString)

      When("POST /obp/v7.0.0/users/USER_ID/entitlements with the same (bank_id, role_name)")
      val body = s"""{"bank_id":"","role_name":"CanGetAnyUser"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/users/${resourceUser1.userId}/entitlements", body, headers)

      Then("Response is 409 with EntitlementAlreadyExists message")
      statusCode shouldBe 409
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(EntitlementAlreadyExists)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ─── getAccountAccessTrace ────────────────────────────────────────────────────

  feature("Http4s700 getAccountAccessTrace endpoint") {

    scenario("Reject unauthenticated GET to account-access-trace", Http4s700RoutesTag) {
      Given("GET account-access-trace with no auth")
      val bankId    = testBankId1.value
      val accountId = testAccountId0.value
      val viewId    = SYSTEM_OWNER_VIEW_ID
      val targetUser = resourceUser1.userId
      val (statusCode, json, _) = makeHttpRequest(
        s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/views/$viewId/users/$targetUser/account-access-trace")

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

    scenario("Return 403 when authenticated but missing canGetAccountAccessTrace role", Http4s700RoutesTag) {
      Given("DirectLogin without the required role")
      val bankId    = testBankId1.value
      val accountId = testAccountId0.value
      val viewId    = SYSTEM_OWNER_VIEW_ID
      val targetUser = resourceUser1.userId
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(
        s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/views/$viewId/users/$targetUser/account-access-trace", headers)

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

    scenario("Return 404 when target user does not exist", Http4s700RoutesTag) {
      Given("canGetAccountAccessTrace granted to caller, missing target user_id in path")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canGetAccountAccessTrace.toString)
      val bankId    = testBankId1.value
      val accountId = testAccountId0.value
      val viewId    = SYSTEM_OWNER_VIEW_ID
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(
        s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/views/$viewId/users/no-such-user-xyz/account-access-trace", headers)

      Then("Response is 404 with UserNotFoundByUserId message")
      statusCode shouldBe 404
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(UserNotFoundByUserId)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 200 with explanation showing ACCOUNT_ACCESS as final source for owner view holder", Http4s700RoutesTag) {
      Given("canGetAccountAccessTrace granted; target user (resourceUser1) has the system owner view")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canGetAccountAccessTrace.toString)
      val bankId    = testBankId1.value
      val accountId = testAccountId0.value
      val viewId    = SYSTEM_OWNER_VIEW_ID
      val targetUser = resourceUser1.userId
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(
        s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/views/$viewId/users/$targetUser/account-access-trace", headers)

      Then("Response is 200 with the explanation shape and has_access=true")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.keys should contain allOf (
            "user_id", "bank_id", "account_id", "view_id",
            "has_access", "access_source",
            "account_access_trace", "entitlement_trace", "abac_trace"
          )
          map.get("user_id")             shouldBe Some(JString(targetUser))
          map.get("bank_id")             shouldBe Some(JString(bankId))
          map.get("account_id")          shouldBe Some(JString(accountId))
          map.get("view_id")             shouldBe Some(JString(viewId))
          map.get("has_access")      shouldBe Some(JBool(true))
          map.get("access_source") shouldBe Some(JString("ACCOUNT_ACCESS"))
          map.get("account_access_trace") match {
            case Some(JObject(traceFields)) =>
              val tm = toFieldMap(traceFields)
              tm.get("has_account_access_for_view") shouldBe Some(JBool(true))
              tm.get("account_access_view_ids") match {
                case Some(JArray(views)) => views should contain(JString(viewId))
                case _ => fail("Expected account_access_view_ids array")
              }
            case _ => fail("Expected account_access_trace object")
          }
          map.get("abac_trace") match {
            case Some(JObject(abacFields)) =>
              val am = toFieldMap(abacFields)
              am.keys should contain allOf ("policy", "allow_abac_account_access", "standalone_abac_result", "rules_evaluated")
              am.get("policy") shouldBe Some(JString("account-access"))
            case _ => fail("Expected abac_trace object")
          }
        case _ => fail("Expected JSON object")
      }
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

  // ─── getUserByUserId ──────────────────────────────────────────────────────────

  feature("Http4s700 getUserByUserId endpoint") {

    scenario("Reject unauthenticated access to /users/user-id/USER_ID", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/users/user-id/USER_ID with no auth headers")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/users/user-id/${resourceUser1.userId}")

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
      Given("GET /obp/v7.0.0/users/user-id/USER_ID with DirectLogin header but no role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/users/user-id/${resourceUser1.userId}", headers)

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

    scenario("Return 200 with user fields when authenticated with canGetAnyUser role", Http4s700RoutesTag) {
      Given("canGetAnyUser role granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canGetAnyUser.toString)

      When(s"GET /obp/v7.0.0/users/user-id/${resourceUser1.userId} with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/users/user-id/${resourceUser1.userId}", headers)

      Then("Response is 200 with user_id, username, email fields")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val m = toFieldMap(fields)
          m.get("user_id") match {
            case Some(JString(id)) => id shouldBe resourceUser1.userId
            case _ => fail("Expected user_id field")
          }
          m.keys should contain("username")
          m.keys should contain("email")
          m.keys should contain("entitlements")
        case _ => fail("Expected JSON object for getUserByUserId")
      }
    }

    scenario("Return 404 when USER_ID does not exist", Http4s700RoutesTag) {
      Given("canGetAnyUser role granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canGetAnyUser.toString)

      When("GET /obp/v7.0.0/users/user-id/non-existing-user-id with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/users/user-id/non-existing-user-id-xyz", headers)

      Then("Response is 404 with UserNotFoundByUserId message")
      statusCode shouldBe 404
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(UserNotFoundByUserId)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
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

  // ─── getCacheConfig ──────────────────────────────────────────────────────────

  feature("Http4s700 getCacheConfig endpoint") {

    scenario("Reject unauthenticated access to /system/cache/config", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/system/cache/config with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/cache/config")

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

    scenario("Return 403 when authenticated but missing canGetCacheConfig role", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/system/cache/config with DirectLogin header but no role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/cache/config", headers)

      Then("Response is 403 with UserHasMissingRoles")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canGetCacheConfig.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return cache config when authenticated with canGetCacheConfig role", Http4s700RoutesTag) {
      Given("canGetCacheConfig role granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canGetCacheConfig.toString)

      When("GET /obp/v7.0.0/system/cache/config with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/cache/config", headers)

      Then("Response is 200 with redis_status, in_memory_status, instance_id fields")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val m = toFieldMap(fields)
          m.keys should contain("redis_status")
          m.keys should contain("in_memory_status")
          m.keys should contain("instance_id")
        case _ => fail("Expected JSON object for getCacheConfig")
      }
    }
  }

  // ─── getCacheInfo ────────────────────────────────────────────────────────────

  feature("Http4s700 getCacheInfo endpoint") {

    scenario("Reject unauthenticated access to /system/cache/info", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/system/cache/info with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/cache/info")

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

    scenario("Return 403 when authenticated but missing canGetCacheInfo role", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/system/cache/info with DirectLogin header but no role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/cache/info", headers)

      Then("Response is 403 with UserHasMissingRoles")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canGetCacheInfo.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return cache info when authenticated with canGetCacheInfo role", Http4s700RoutesTag) {
      Given("canGetCacheInfo role granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canGetCacheInfo.toString)

      When("GET /obp/v7.0.0/system/cache/info with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/cache/info", headers)

      Then("Response is 200 with namespaces, total_keys, redis_available fields")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val m = toFieldMap(fields)
          m.keys should contain("namespaces")
          m.keys should contain("total_keys")
          m.keys should contain("redis_available")
        case _ => fail("Expected JSON object for getCacheInfo")
      }
    }
  }

  // ─── getDatabasePoolInfo ─────────────────────────────────────────────────────

  feature("Http4s700 getDatabasePoolInfo endpoint") {

    scenario("Reject unauthenticated access to /system/database/pool", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/system/database/pool with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/database/pool")

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

    scenario("Return 403 when authenticated but missing canGetDatabasePoolInfo role", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/system/database/pool with DirectLogin header but no role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/database/pool", headers)

      Then("Response is 403 with UserHasMissingRoles")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canGetDatabasePoolInfo.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return pool info when authenticated with canGetDatabasePoolInfo role", Http4s700RoutesTag) {
      Given("canGetDatabasePoolInfo role granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canGetDatabasePoolInfo.toString)

      When("GET /obp/v7.0.0/system/database/pool with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/database/pool", headers)

      Then("Response is 200 with pool_name, active_connections, maximum_pool_size fields")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val m = toFieldMap(fields)
          m.keys should contain("pool_name")
          m.keys should contain("active_connections")
          m.keys should contain("maximum_pool_size")
        case _ => fail("Expected JSON object for getDatabasePoolInfo")
      }
    }
  }

  // ─── getStoredProcedureConnectorHealth ───────────────────────────────────────

  feature("Http4s700 getStoredProcedureConnectorHealth endpoint") {

    scenario("Reject unauthenticated access to stored_procedure_vDec2019/health", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/system/connectors/stored_procedure_vDec2019/health with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/connectors/stored_procedure_vDec2019/health")

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

    scenario("Return 403 when authenticated but missing canGetConnectorHealth role", Http4s700RoutesTag) {
      Given("GET stored_procedure_vDec2019/health with DirectLogin header but no role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/connectors/stored_procedure_vDec2019/health", headers)

      Then("Response is 403 with UserHasMissingRoles")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canGetConnectorHealth.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    // Note: no 200 scenario — StoredProcedureUtils init block requires stored_procedure_connector.*
    // props that are not set in the test environment. The route is correctly wired (auth passes),
    // but the Future would fail when StoredProcedureUtils is first accessed, returning 500.
  }

  // ─── getMigrations ───────────────────────────────────────────────────────────

  feature("Http4s700 getMigrations endpoint") {

    scenario("Reject unauthenticated access to /system/migrations", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/system/migrations with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/migrations")

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

    scenario("Return 403 when authenticated but missing canGetMigrations role", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/system/migrations with DirectLogin header but no role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/migrations", headers)

      Then("Response is 403 with UserHasMissingRoles")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canGetMigrations.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return migrations list when authenticated with canGetMigrations role", Http4s700RoutesTag) {
      Given("canGetMigrations role granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canGetMigrations.toString)

      When("GET /obp/v7.0.0/system/migrations with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/migrations", headers)

      Then("Response is 200 with migration_script_logs field")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).keys should contain("migration_script_logs")
        case _ => fail("Expected JSON object for getMigrations")
      }
    }
  }

  // ─── getCacheNamespaces ──────────────────────────────────────────────────────

  feature("Http4s700 getCacheNamespaces endpoint") {

    scenario("Reject unauthenticated access to /system/cache/namespaces", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/system/cache/namespaces with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/cache/namespaces")

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

    scenario("Return 403 when authenticated but missing canGetCacheNamespaces role", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/system/cache/namespaces with DirectLogin header but no role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/cache/namespaces", headers)

      Then("Response is 403 with UserHasMissingRoles")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canGetCacheNamespaces.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return cache namespaces when authenticated with canGetCacheNamespaces role", Http4s700RoutesTag) {
      Given("canGetCacheNamespaces role granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canGetCacheNamespaces.toString)

      When("GET /obp/v7.0.0/system/cache/namespaces with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/system/cache/namespaces", headers)

      Then("Response is 200 with namespaces array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("namespaces") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected namespaces array")
          }
        case _ => fail("Expected JSON object for getCacheNamespaces")
      }
    }
  }

  // ─── Organisations ────────────────────────────────────────────────────────

  /** Create an Organisation directly via the model layer for test setup. */
  private def createTestOrg(
    orgId: String,
    visibility: String = "public",
    status: String = "active"
  ): Unit = {
    OrganisationX.organisation.vend.createOrganisation(
      orgId, s"Test $orgId", None, None, status, visibility, resourceUser1.userId
    )
  }

  feature("Http4s700 createOrganisation endpoint") {

    scenario("Reject unauthenticated POST to /organisations", Http4s700RoutesTag) {
      Given("POST /obp/v7.0.0/organisations with no auth")
      val body = """{"organisation_id":"test-org-401","name":"X"}"""
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/organisations", body)

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

    scenario("Return 403 when authenticated but missing canCreateOrganisation role", Http4s700RoutesTag) {
      Given("DirectLogin without the required role")
      val body = """{"organisation_id":"test-org-403","name":"X"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/organisations", body, headers)

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

    scenario("Return 201 with organisation JSON when authenticated with role and valid body", Http4s700RoutesTag) {
      Given("canCreateOrganisation granted to caller")
      addEntitlement("", resourceUser1.userId, canCreateOrganisation.toString)
      val orgId = s"test-org-${APIUtil.generateUUID().take(8)}"
      val body = s"""{"organisation_id":"$orgId","name":"Test Org"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")

      When(s"POST /obp/v7.0.0/organisations with organisation_id=$orgId")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/organisations", body, headers)

      Then("Response is 201 with the expected fields")
      statusCode shouldBe 201
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.keys should contain allOf ("organisation_id", "name", "status", "visibility", "created_by_user_id")
          map.get("organisation_id") shouldBe Some(JString(orgId))
          map.get("status")          shouldBe Some(JString("active"))
          map.get("visibility")      shouldBe Some(JString("public"))
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 400 when organisation_id format is invalid", Http4s700RoutesTag) {
      Given("canCreateOrganisation granted; organisation_id contains an invalid character")
      addEntitlement("", resourceUser1.userId, canCreateOrganisation.toString)
      val body = """{"organisation_id":"bad id with spaces","name":"X"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")

      When("POST /obp/v7.0.0/organisations with invalid id")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/organisations", body, headers)

      Then("Response is 400 with InvalidOrganisationIdFormat message")
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(InvalidOrganisationIdFormat)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 409 when organisation already exists", Http4s700RoutesTag) {
      Given("an organisation already exists; canCreateOrganisation granted")
      addEntitlement("", resourceUser1.userId, canCreateOrganisation.toString)
      val orgId = s"dup-org-${APIUtil.generateUUID().take(8)}"
      createTestOrg(orgId)

      When("POST /obp/v7.0.0/organisations with the same organisation_id")
      val body = s"""{"organisation_id":"$orgId","name":"X"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/organisations", body, headers)

      Then("Response is 409 with OrganisationAlreadyExists message")
      statusCode shouldBe 409
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(OrganisationAlreadyExists)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  feature("Http4s700 getOrganisations endpoint") {

    scenario("Reject unauthenticated GET to /organisations", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/organisations with no auth")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/organisations")

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

    scenario("Return 200 with organisations array for an authenticated user", Http4s700RoutesTag) {
      Given("an organisation exists")
      val orgId = s"list-org-${APIUtil.generateUUID().take(8)}"
      createTestOrg(orgId)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")

      When("GET /obp/v7.0.0/organisations")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/organisations", headers)

      Then("Response is 200 with an organisations array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("organisations") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected organisations array")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  feature("Http4s700 getOrganisation endpoint") {

    scenario("Reject unauthenticated GET to /organisations/ORGANISATION_ID", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/organisations/anything with no auth")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/organisations/anything")

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

    scenario("Return 404 when organisation does not exist", Http4s700RoutesTag) {
      Given("an authenticated user; organisation_id that does not exist")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")

      When("GET /obp/v7.0.0/organisations/no-such-org-xyz")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/organisations/no-such-org-xyz", headers)

      Then("Response is 404 with OrganisationNotFound message")
      statusCode shouldBe 404
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(OrganisationNotFound)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 200 with organisation JSON for an existing public organisation", Http4s700RoutesTag) {
      Given("a public organisation exists")
      val orgId = s"get-org-${APIUtil.generateUUID().take(8)}"
      createTestOrg(orgId, visibility = "public")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")

      When(s"GET /obp/v7.0.0/organisations/$orgId")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/organisations/$orgId", headers)

      Then("Response is 200 with the expected fields")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("organisation_id") shouldBe Some(JString(orgId))
          map.get("visibility")      shouldBe Some(JString("public"))
        case _ => fail("Expected JSON object")
      }
    }
  }

  feature("Http4s700 updateOrganisation endpoint") {

    scenario("Reject unauthenticated PUT to /organisations/ORGANISATION_ID", Http4s700RoutesTag) {
      Given("PUT /obp/v7.0.0/organisations/anything with no auth")
      val body = """{"name":"New Name"}"""
      val (statusCode, json, _) = makeHttpRequestWithBody("PUT", "/obp/v7.0.0/organisations/anything", body)

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

    scenario("Return 403 when authenticated but missing canUpdateOrganisation role", Http4s700RoutesTag) {
      Given("DirectLogin without the required role")
      val body = """{"name":"New Name"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("PUT", "/obp/v7.0.0/organisations/anything", body, headers)

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

    scenario("Return 200 with updated organisation JSON when authenticated with role", Http4s700RoutesTag) {
      Given("an organisation exists; canUpdateOrganisation granted")
      addEntitlement("", resourceUser1.userId, canUpdateOrganisation.toString)
      val orgId = s"upd-org-${APIUtil.generateUUID().take(8)}"
      createTestOrg(orgId)

      When(s"PUT /obp/v7.0.0/organisations/$orgId with a new name")
      val body = """{"name":"Updated Name"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("PUT", s"/obp/v7.0.0/organisations/$orgId", body, headers)

      Then("Response is 200 and name reflects the update")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("organisation_id") shouldBe Some(JString(orgId))
          map.get("name")            shouldBe Some(JString("Updated Name"))
        case _ => fail("Expected JSON object")
      }
    }
  }

  feature("Http4s700 deleteOrganisation endpoint") {

    scenario("Reject unauthenticated DELETE to /organisations/ORGANISATION_ID", Http4s700RoutesTag) {
      Given("DELETE /obp/v7.0.0/organisations/anything with no auth")
      val (statusCode, _, _) = makeHttpRequestWithMethod("DELETE", "/obp/v7.0.0/organisations/anything")

      Then("Response is 401")
      statusCode shouldBe 401
    }

    scenario("Return 403 when authenticated but missing canDeleteOrganisation role", Http4s700RoutesTag) {
      Given("DirectLogin without the required role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithMethod("DELETE", "/obp/v7.0.0/organisations/anything", headers)

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

    scenario("Return 204 when authenticated with role and organisation exists", Http4s700RoutesTag) {
      Given("an organisation exists; canDeleteOrganisation granted")
      addEntitlement("", resourceUser1.userId, canDeleteOrganisation.toString)
      val orgId = s"del-org-${APIUtil.generateUUID().take(8)}"
      createTestOrg(orgId)

      When(s"DELETE /obp/v7.0.0/organisations/$orgId")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, _, _) = makeHttpRequestWithMethod("DELETE", s"/obp/v7.0.0/organisations/$orgId", headers)

      Then("Response is 204 with no body")
      statusCode shouldBe 204
    }
  }

  // ─── Routing Schemes ──────────────────────────────────────────────────────

  /** Create a routing scheme directly via the model layer for test setup. */
  private def createTestRoutingScheme(scheme: String, country: String = "TZ"): Unit = {
    RoutingSchemeX.routingScheme.vend.createRoutingScheme(
      scheme = scheme,
      country = country,
      category = "ACCOUNT",
      addressPattern = "^[0-9]{3,20}$",
      secondaryAddressPattern = None,
      exampleAddress = "12345678",
      description = s"Test scheme $scheme",
      downstreamRails = List("TEST"),
      status = "ACTIVE",
      createdByUserId = resourceUser1.userId
    )
  }

  /** Returns a fresh, unique scheme name in the TZ namespace. */
  private def freshSchemeName(prefix: String = "TST"): String =
    s"TZ.${prefix}_${APIUtil.generateUUID().take(6).toUpperCase}"

  feature("Http4s700 createRoutingScheme endpoint") {

    scenario("Reject unauthenticated POST to /routing-schemes", Http4s700RoutesTag) {
      val body = """{"scheme":"TZ.X1","country":"TZ","category":"ACCOUNT","address_pattern":"^[0-9]+$","example_address":"123","description":"x"}"""
      val (statusCode, _, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/routing-schemes", body)
      statusCode shouldBe 401
    }

    scenario("Return 403 when authenticated but missing canCreateRoutingScheme role", Http4s700RoutesTag) {
      val body = """{"scheme":"TZ.X2","country":"TZ","category":"ACCOUNT","address_pattern":"^[0-9]+$","example_address":"123","description":"x"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/routing-schemes", body, headers)
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

    scenario("Return 201 with full routing scheme JSON on happy path", Http4s700RoutesTag) {
      addEntitlement("", resourceUser1.userId, canCreateRoutingScheme.toString)
      val scheme = freshSchemeName("OK")
      val body = s"""{"scheme":"$scheme","country":"TZ","category":"ACCOUNT","address_pattern":"^255[0-9]{9}$$","example_address":"255778300336","description":"Test MSISDN","downstream_rails":["TIPS"]}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")

      val (statusCode, json, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/routing-schemes", body, headers)
      statusCode shouldBe 201
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.keys should contain allOf ("scheme", "country", "category", "address_pattern", "example_address", "status", "created_by_user_id")
          map.get("scheme")   shouldBe Some(JString(scheme))
          map.get("country")  shouldBe Some(JString("TZ"))
          map.get("category") shouldBe Some(JString("ACCOUNT"))
          map.get("status")   shouldBe Some(JString("ACTIVE"))
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 400 when scheme name does not match country-qualified convention", Http4s700RoutesTag) {
      addEntitlement("", resourceUser1.userId, canCreateRoutingScheme.toString)
      val body = """{"scheme":"msisdn_tz","country":"TZ","category":"ACCOUNT","address_pattern":"^[0-9]+$","example_address":"123","description":"x"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/routing-schemes", body, headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(InvalidRoutingSchemeName)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 400 when example_address does not match address_pattern", Http4s700RoutesTag) {
      addEntitlement("", resourceUser1.userId, canCreateRoutingScheme.toString)
      val scheme = freshSchemeName("MIS")
      // Pattern requires exactly 9 digits; example is letters.
      val body = s"""{"scheme":"$scheme","country":"TZ","category":"ACCOUNT","address_pattern":"^[0-9]{9}$$","example_address":"not-numeric","description":"x"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/routing-schemes", body, headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(RoutingSchemeExampleAddressMismatch)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 409 when scheme already exists", Http4s700RoutesTag) {
      addEntitlement("", resourceUser1.userId, canCreateRoutingScheme.toString)
      val scheme = freshSchemeName("DUP")
      createTestRoutingScheme(scheme)

      val body = s"""{"scheme":"$scheme","country":"TZ","category":"ACCOUNT","address_pattern":"^[0-9]+$$","example_address":"123","description":"x"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/routing-schemes", body, headers)
      statusCode shouldBe 409
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(RoutingSchemeAlreadyExists)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  feature("Http4s700 getRoutingSchemes endpoint") {

    scenario("Public — returns 200 without authentication", Http4s700RoutesTag) {
      val scheme = freshSchemeName("LST")
      createTestRoutingScheme(scheme)

      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/routing-schemes")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.keys should contain allOf ("routing_schemes", "pagination")
          map.get("routing_schemes") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected routing_schemes array")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  feature("Http4s700 getRoutingScheme endpoint") {

    scenario("Return 200 for an existing scheme (no auth required)", Http4s700RoutesTag) {
      val scheme = freshSchemeName("GET")
      createTestRoutingScheme(scheme)

      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/routing-schemes/$scheme")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("scheme") shouldBe Some(JString(scheme))
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 404 when scheme does not exist", Http4s700RoutesTag) {
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/routing-schemes/TZ.DOES_NOT_EXIST")
      statusCode shouldBe 404
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(RoutingSchemeNotFound)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  feature("Http4s700 updateRoutingScheme endpoint") {

    scenario("Reject unauthenticated PUT", Http4s700RoutesTag) {
      val (statusCode, _, _) = makeHttpRequestWithBody("PUT", "/obp/v7.0.0/routing-schemes/TZ.ANY", """{"status":"DEPRECATED"}""")
      statusCode shouldBe 401
    }

    scenario("Return 403 when missing canUpdateRoutingScheme", Http4s700RoutesTag) {
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, _, _) = makeHttpRequestWithBody("PUT", "/obp/v7.0.0/routing-schemes/TZ.ANY", """{"status":"DEPRECATED"}""", headers)
      statusCode shouldBe 403
    }

    scenario("Return 200 and persist new status when authenticated with role", Http4s700RoutesTag) {
      addEntitlement("", resourceUser1.userId, canUpdateRoutingScheme.toString)
      val scheme = freshSchemeName("UPD")
      createTestRoutingScheme(scheme)

      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val body    = """{"status":"DEPRECATED","description":"updated"}"""
      val (statusCode, json, _) = makeHttpRequestWithBody("PUT", s"/obp/v7.0.0/routing-schemes/$scheme", body, headers)
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("status")      shouldBe Some(JString("DEPRECATED"))
          map.get("description") shouldBe Some(JString("updated"))
        case _ => fail("Expected JSON object")
      }
    }
  }

  feature("Http4s700 deleteRoutingScheme endpoint") {

    scenario("Reject unauthenticated DELETE", Http4s700RoutesTag) {
      val (statusCode, _, _) = makeHttpRequestWithMethod("DELETE", "/obp/v7.0.0/routing-schemes/TZ.ANY")
      statusCode shouldBe 401
    }

    scenario("Return 204 and soft-delete (status flips to RETIRED) when role granted", Http4s700RoutesTag) {
      addEntitlement("", resourceUser1.userId, canDeleteRoutingScheme.toString)
      val scheme = freshSchemeName("DEL")
      createTestRoutingScheme(scheme)

      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, _, _) = makeHttpRequestWithMethod("DELETE", s"/obp/v7.0.0/routing-schemes/$scheme", headers)
      statusCode shouldBe 204

      And("the row should still exist with status RETIRED")
      val fetched = RoutingSchemeX.routingScheme.vend.getRoutingScheme(scheme)
      fetched.map(_.status) shouldBe net.liftweb.common.Full("RETIRED")
    }
  }

  feature("Http4s700 getBankSupportedRoutingSchemes endpoint") {

    scenario("Reject unauthenticated GET", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val (statusCode, _, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/supported-routing-schemes")
      statusCode shouldBe 401
    }

    scenario("Return 200 with empty/populated list for authenticated user", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/supported-routing-schemes", headers)
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("bank_id") shouldBe Some(JString(bankId))
          map.get("supported_routing_schemes") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected supported_routing_schemes array")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  feature("Http4s700 putBankSupportedRoutingScheme endpoint") {

    scenario("Reject unauthenticated PUT", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val (statusCode, _, _) = makeHttpRequestWithBody("PUT", s"/obp/v7.0.0/banks/$bankId/supported-routing-schemes/TZ.ANY", """{"enabled":true}""")
      statusCode shouldBe 401
    }

    scenario("Return 403 when missing canUpdateBankSupportedRoutingScheme role", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, _, _) = makeHttpRequestWithBody("PUT", s"/obp/v7.0.0/banks/$bankId/supported-routing-schemes/TZ.ANY", """{"enabled":true}""", headers)
      statusCode shouldBe 403
    }

    scenario("Return 404 when scheme does not exist in the registry", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canUpdateBankSupportedRoutingScheme.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("PUT", s"/obp/v7.0.0/banks/$bankId/supported-routing-schemes/TZ.NOT_REGISTERED", """{"enabled":true}""", headers)
      statusCode shouldBe 404
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(RoutingSchemeNotFound)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 200 when scheme exists and bank role granted; enabled=true persists notes", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canUpdateBankSupportedRoutingScheme.toString)
      val scheme = freshSchemeName("BNK")
      createTestRoutingScheme(scheme)

      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val body    = """{"enabled":true,"bank_notes":"Routed via Gateway X. Cutoff 22:00."}"""
      val (statusCode, json, _) = makeHttpRequestWithBody("PUT", s"/obp/v7.0.0/banks/$bankId/supported-routing-schemes/$scheme", body, headers)
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("scheme")     shouldBe Some(JString(scheme))
          map.get("bank_notes") shouldBe Some(JString("Routed via Gateway X. Cutoff 22:00."))
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ─── Payee Lookup ─────────────────────────────────────────────────────────

  /**
   * Register a fresh routing scheme AND attach a matching account_routings entry
   * to the named account, so getBankAccountByRouting(scheme, address) resolves.
   * Returns the scheme name.
   */
  private def seedPayeeForLookup(prefix: String, address: String, destBankId: String, destAccountId: String): String = {
    val scheme = freshSchemeName(prefix)
    RoutingSchemeX.routingScheme.vend.createRoutingScheme(
      scheme = scheme, country = "TZ", category = "ACCOUNT",
      addressPattern = "^[0-9]+$", secondaryAddressPattern = None,
      exampleAddress = address, description = "Test", downstreamRails = Nil,
      status = "ACTIVE", createdByUserId = resourceUser1.userId
    )
    BankAccountRouting.create
      .BankId(destBankId)
      .AccountId(destAccountId)
      .AccountRoutingScheme(scheme)
      .AccountRoutingAddress(address)
      .saveMe()
    scheme
  }

  feature("Http4s700 createPayeeLookup endpoint") {

    scenario("Reject unauthenticated POST to /payees/lookup", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val body = """{"identifier_type":"TZ.MSISDN","identifier":"255778300336"}"""
      val (statusCode, _, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/payees/lookup", body)
      statusCode shouldBe 401
    }

    scenario("Return 400 when identifier_type is not registered", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val body = """{"identifier_type":"TZ.UNKNOWN_SCHEME","identifier":"123"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/payees/lookup", body, headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(PayeeLookupIdentifierTypeNotRegistered)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 400 when identifier does not match the scheme's address_pattern", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      // Create a strict scheme then send an address that doesn't match.
      val scheme = freshSchemeName("STR")
      RoutingSchemeX.routingScheme.vend.createRoutingScheme(
        scheme = scheme, country = "TZ", category = "ACCOUNT",
        addressPattern = "^255[0-9]{9}$", secondaryAddressPattern = None,
        exampleAddress = "255778300336", description = "Strict TZ MSISDN",
        downstreamRails = Nil, status = "ACTIVE", createdByUserId = resourceUser1.userId
      )
      val body = s"""{"identifier_type":"$scheme","identifier":"not-a-phone"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/payees/lookup", body, headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(PayeeLookupAddressMismatch)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 404 when no account has the requested routing", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      // Registered scheme, valid pattern match, but no account_routings row.
      val scheme = freshSchemeName("NMA")
      RoutingSchemeX.routingScheme.vend.createRoutingScheme(
        scheme = scheme, country = "TZ", category = "ACCOUNT",
        addressPattern = "^[0-9]+$", secondaryAddressPattern = None,
        exampleAddress = "12345", description = "No-match", downstreamRails = Nil,
        status = "ACTIVE", createdByUserId = resourceUser1.userId
      )
      val body = s"""{"identifier_type":"$scheme","identifier":"99999999999"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/payees/lookup", body, headers)
      statusCode shouldBe 404
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(PayeeNotFound)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 201 with lookup_id and payee details when account_routing resolves", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val address = s"2557${(System.currentTimeMillis() % 100000000L).toString.reverse.padTo(8, '0').reverse}"
      val scheme = seedPayeeForLookup("HAP", address, bankId, accountId)

      val body = s"""{"identifier_type":"$scheme","identifier":"$address","fsp_id":"503"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/payees/lookup", body, headers)
      statusCode shouldBe 201
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.keys should contain allOf ("lookup_id", "expires_at", "identifier_type", "identifier", "full_name")
          map.get("identifier_type") shouldBe Some(JString(scheme))
          map.get("identifier")      shouldBe Some(JString(address))
          map.get("fsp_id")          shouldBe Some(JString("503"))
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ─── MOBILE_WALLET transaction request ────────────────────────────────────

  feature("Http4s700 createTransactionRequestMobileWallet endpoint") {

    scenario("Reject unauthenticated POST", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val body = """{"to":{"msisdn":"255778300336"},"value":{"currency":"TZS","amount":"1000"},"description":"x"}"""
      val (statusCode, _, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/MOBILE_WALLET/transaction-requests", body)
      statusCode shouldBe 401
    }

    scenario("Return 400 when country-qualified MSISDN scheme is not in the registry", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      // country_code=ZZ ⇒ scheme=ZZ.MSISDN which we never register.
      val body = """{"to":{"msisdn":"255778300336"},"value":{"currency":"TZS","amount":"1000"},"description":"x","country_code":"ZZ"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/MOBILE_WALLET/transaction-requests", body, headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(PayeeLookupIdentifierTypeNotRegistered)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return 400 when msisdn does not match the scheme's address_pattern", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      // Use country_code=XW so the scheme is XW.MSISDN — register it with a strict pattern.
      val country = "XW"
      val schemeName = s"$country.MSISDN"
      RoutingSchemeX.routingScheme.vend.getRoutingScheme(schemeName) match {
        case net.liftweb.common.Full(_) => // already registered from a previous run
        case _ =>
          RoutingSchemeX.routingScheme.vend.createRoutingScheme(
            scheme = schemeName, country = country, category = "ACCOUNT",
            addressPattern = "^999[0-9]{9}$", secondaryAddressPattern = None,
            exampleAddress = "999778300336", description = "Test only",
            downstreamRails = Nil, status = "ACTIVE", createdByUserId = resourceUser1.userId
          )
      }
      val body = s"""{"to":{"msisdn":"not-a-phone"},"value":{"currency":"TZS","amount":"1000"},"description":"x","country_code":"$country"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/MOBILE_WALLET/transaction-requests", body, headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(MobileWalletInvalidMsisdn)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

}
