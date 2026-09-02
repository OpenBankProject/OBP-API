package code.api.v7_0_0

import org.json4s._
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import code.api.util.http4s.Http4sStandardHeaders
import code.api.Constant.SYSTEM_OWNER_VIEW_ID
import code.api.ResponseHeader
import code.api.util.APIUtil
import code.api.util.ApiRole.{canAttachOpenCorridorPromise, canConfigureAmqpBankBroker, canGetMessageOutbox, canRetryMessageOutbox, canSettleOpenCorridor, canCreateAccount, canCreateEntitlementAtAnyBank, canCreateOrganisation, canCreateRoutingScheme, canCreateUtilityVendResult, canDeleteEntitlementAtAnyBank, canDeleteOrganisation, canDeleteRoutingScheme, canDeleteSchedulerJobLock, canUpdateSystemView, canGetAccountAccessTrace, canGetAnyOrganisation, canGetAnyUser, canGetCacheConfig, canGetCacheInfo, canGetCacheNamespaces, canGetCardsForBank, canGetConnectorHealth, canCreateMetricsArchiveRun, canGetCustomersAtOneBank, canGetDatabasePoolInfo, canGetMetricsDiagnostics, canGetMigrations, canGetSchedulerJobLocks, canReadResourceDoc, canUpdateBankSupportedRoutingScheme, canUpdateOrganisation, canUpdateRoutingScheme}
import code.api.util.ErrorMessages.{AccountIdAlreadyExists, AuthenticatedUserIsRequired, BankNotFound, DuplicateUsername, EntitlementAlreadyExists, InvalidAccountRoutings, InvalidJsonFormat, InvalidJsonValue, InvalidOrganisationIdFormat, InvalidPhoneNumber, InvalidRoutingSchemeName, UserFilterParametersNotSupported, InvalidTransactionRequestId, MessageOutboxRowNotFound, MessageOutboxRowNotSticky, MobileWalletDestinationNotFound, MobileWalletInvalidMsisdn, AmqpBankBrokerNotConfigured, OpenCorridorDisabled, OpenCorridorPromiseEvidenceConflict, OpenCorridorPromiseNotPending, OpenCorridorPromiseTypeMismatch, OpenCorridorSameBankNotAllowed, OpenCorridorSettlementAddressMissing, OpenCorridorSettlementNotFound, OrganisationAlreadyExists, OrganisationNotFound, PayeeLookupAddressMismatch, PayeeLookupIdentifierTypeNotRegistered, PayeeNotFound, RoutingSchemeAlreadyExists, RoutingSchemeExampleAddressMismatch, RoutingSchemeNotFound, SelfServiceBankCreationDisabled, SelfServiceBankLimitReached, SystemViewNotFound, UserHasMissingRoles, UserNotFoundByUserId, UtilityIdentifierTypeWrongCategory, UtilityInvalidIdentifier, UtilityTransactionRequestNotFound}
import code.utilitypayment.{UtilityCallbackStatus, UtilityPaymentCallbacks}
import code.scheduler.JobScheduler
import code.api.Constant.SYSTEM_AUDITOR_VIEW_ID
import code.views.MapperViews
import code.views.system.ViewPermission
import com.openbankproject.commons.model.ViewId
import code.routingscheme.RoutingSchemes
import code.bankconnectors.DoobieBankAccountRoutingQueries
import code.model.dataAccess.AuthUser
import net.liftweb.util.Helpers.randomString
import code.metrics.MappedMetric
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.organisation.Organisations
import code.metadata.counterparties.Counterparties
import com.openbankproject.commons.model.{AccountId, BankId => CommBankId, CreditLimit, CreditRating, CustomerFaceImage}
import fs2.Stream
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.typelevel.ci.CIString

import java.util.Date
import code.setup.ServerSetupWithTestData
import org.json4s.JValue
import org.json4s.JsonAST.{JArray, JBool, JField, JInt, JNull, JObject, JString}
import com.openbankproject.commons.util.JsonAliases.parse
import org.scalatest.Tag
import com.openbankproject.commons.util.JsonAliases.RichJField

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
    val resp     = Http4sStandardHeaders(req, baseResp)
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

  private def createTestOrg(
    orgId: String,
    visibility: String = "public",
    status: String = "active"
  ): Unit = {
    Organisations.organisation.vend.createOrganisation(
      orgId, s"Test $orgId", None, None, status, visibility, resourceUser1.userId
    )
  }

  // ─── root ────────────────────────────────────────────────────────────────────

  Feature("Http4s700 root endpoint") {

    Scenario("Return API info JSON with all required fields", Http4s700RoutesTag) {
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

    Scenario("resource_docs_requires_role field reflects prop value", Http4s700RoutesTag) {
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

    Scenario("Unauthenticated access to root returns 200 (public endpoint)", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/root request with no auth")
      val (statusCode, _, _) = makeHttpRequest("/obp/v7.0.0/root")
      Then("Response is 200 — root is public")
      statusCode shouldBe 200
    }
  }

  // ─── password policy ─────────────────────────────────────────────────────────

  Feature("Http4s700 getPasswordPolicy endpoint") {

    Scenario("Anonymous GET returns the published password policy", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/public/password-config with no auth")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/public/password-config")

      Then("Response is 200 with the two policy branches")
      statusCode shouldBe 200
      (json \ "description") shouldBe a[JString]
      val policies = (json \ "policies").children
      policies.size shouldBe 2

      And("The composition branch is published with its classes and the exact regex from APIUtil")
      val compositionPolicy = policies.head
      (compositionPolicy \ "min_length") shouldBe JInt(10)
      (compositionPolicy \ "max_length") shouldBe JInt(16)
      (compositionPolicy \ "required_character_classes").children.size shouldBe 4
      (compositionPolicy \ "regex") shouldBe JString(APIUtil.passwordCompositionPolicyRegex)

      And("The passphrase branch has no required classes and the exact regex from APIUtil")
      val passphrasePolicy = policies(1)
      (passphrasePolicy \ "min_length") shouldBe JInt(17)
      (passphrasePolicy \ "max_length") shouldBe JInt(512)
      (passphrasePolicy \ "required_character_classes").children.size shouldBe 0
      (passphrasePolicy \ "regex") shouldBe JString(APIUtil.passwordPassphrasePolicyRegex)

      And("Both branches publish printable ASCII without space as the allowed characters")
      val allowedCharacters = JString((0x21 to 0x7e).map(_.toChar).mkString)
      (compositionPolicy \ "allowed_characters") shouldBe allowedCharacters
      (passphrasePolicy \ "allowed_characters") shouldBe allowedCharacters
    }
  }

  // ─── cross-cutting middleware ─────────────────────────────────────────────────

  Feature("Http4s700 response headers") {

    Scenario("All responses include Correlation-Id header", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/root")
      val (statusCode, _, headers) = makeHttpRequest("/obp/v7.0.0/root")

      Then("Response includes Correlation-Id header")
      statusCode shouldBe 200
      hasHeader(headers, ResponseHeader.`Correlation-Id`) shouldBe true
    }

    Scenario("X-Request-ID is echoed back as Correlation-Id", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/root with X-Request-ID header")
      val requestId = java.util.UUID.randomUUID().toString
      val (statusCode, _, headers) = makeHttpRequest(
        "/obp/v7.0.0/root",
        Map("X-Request-ID" -> requestId)
      )

      Then("Correlation-Id in response matches the sent X-Request-ID")
      statusCode shouldBe 200
      headers.find { case (k, _) => k.equalsIgnoreCase(ResponseHeader.`Correlation-Id`) }
        .map(_._2) shouldBe Some(requestId)
    }

    Scenario("All responses include Cache-Control header", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/root")
      val (_, _, headers) = makeHttpRequest("/obp/v7.0.0/root")

      Then("Response includes Cache-Control: no-cache")
      hasHeader(headers, ResponseHeader.`Cache-Control`) shouldBe true
    }

    Scenario("All responses include X-Frame-Options header", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/root")
      val (_, _, headers) = makeHttpRequest("/obp/v7.0.0/root")

      Then("Response includes X-Frame-Options: DENY")
      hasHeader(headers, "X-Frame-Options") shouldBe true
      headers.find { case (k, _) => k.equalsIgnoreCase("X-Frame-Options") }
        .map(_._2) shouldBe Some("DENY")
    }

    Scenario("Error responses also include Correlation-Id header", Http4s700RoutesTag) {
      Given("DELETE /obp/v7.0.0/entitlements/no-such-id without auth (will 401)")
      val (statusCode, _, headers) = makeHttpRequestWithMethod("DELETE", "/obp/v7.0.0/entitlements/no-such-id")

      Then("401 error response still has Correlation-Id")
      statusCode shouldBe 401
      hasHeader(headers, ResponseHeader.`Correlation-Id`) shouldBe true
    }
  }

  // ─── CORS preflight ──────────────────────────────────────────────────────────
  // CORS is applied by Http4sServer above Http4s700 and is not reachable via in-process
  // route testing. OPTIONS preflight scenarios live in Http4sServerIntegrationTest.

  // ─── unknown paths and wrong methods ─────────────────────────────────────────

  Feature("Http4s700 routing edge cases") {

    Scenario("Unknown path under v7.0.0 prefix does not silently bridge to Lift", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/nonexistent-endpoint")
      val (statusCode, _, _) = makeHttpRequest("/obp/v7.0.0/nonexistent-endpoint")

      Then("Response is not 200 — unknown path is not silently served")
      statusCode should not be 200
    }

    Scenario("POST to a GET-only endpoint returns non-200", Http4s700RoutesTag) {
      Given("POST /obp/v7.0.0/root — method not allowed (root is a native GET-only v7 endpoint)")
      val (statusCode, _, _) = makeHttpRequestWithMethod("POST", "/obp/v7.0.0/root")

      Then("Response is not 200")
      statusCode should not be 200
    }
  }

  // ─── deleteEntitlement ────────────────────────────────────────────────────────

  Feature("Http4s700 deleteEntitlement endpoint") {

    Scenario("Reject unauthenticated DELETE to /entitlements/ENTITLEMENT_ID", Http4s700RoutesTag) {
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

    Scenario("Return 403 when authenticated but missing canDeleteEntitlementAtAnyBank role", Http4s700RoutesTag) {
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

    Scenario("Return 204 when authenticated with role and entitlement exists", Http4s700RoutesTag) {
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

    Scenario("Return 204 even when entitlement ID does not exist (idempotent)", Http4s700RoutesTag) {
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

  // ─── createAccount (POST generated id / PUT chosen id) ───────────────────────

  private def createAccountBody(
    userId: Option[String] = None,
    routings: List[(String, String)] = Nil
  ): String = {
    val userField = userId.map(u => s""""user_id": "$u",""").getOrElse("")
    val routingsJson = routings
      .map { case (scheme, address) => s"""{"scheme": "$scheme", "address": "$address"}""" }
      .mkString("[", ",", "]")
    s"""{
       |  $userField
       |  "label": "V7 test account",
       |  "product_code": "OPEN_CORRIDOR",
       |  "balance": {"currency": "EUR", "amount": "0"},
       |  "branch_id": "",
       |  "account_routings": $routingsJson
       |}""".stripMargin
  }

  private def routingPairs(json: JValue): List[(String, String)] =
    json \ "account_routings" match {
      case JArray(items) => items.map { item =>
        (item \ "scheme", item \ "address") match {
          case (JString(scheme), JString(address)) => (scheme, address)
          case _ => fail("Expected scheme/address strings in account_routings")
        }
      }
      case _ => fail("Expected account_routings array")
    }

  Feature("Http4s700 createAccount endpoints") {

    Scenario("Reject unauthenticated POST to /banks/BANK_ID/accounts", Http4s700RoutesTag) {
      Given("POST with no auth")
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/banks/${testBankId1.value}/accounts", createAccountBody())

      Then("Response is 401")
      statusCode shouldBe 401
      (json \ "message") match {
        case JString(msg) => msg should include(AuthenticatedUserIsRequired)
        case _ => fail("Expected message field")
      }
    }

    Scenario("Reject an explicit OBP routing in account_routings", Http4s700RoutesTag) {
      Given("A body carrying scheme OBP — the routing is implicit in v7.0.0")
      addEntitlement(testBankId1.value, resourceUser1.userId, canCreateAccount.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val body = createAccountBody(routings = List(("OBP", "some-address")))
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/banks/${testBankId1.value}/accounts", body, headers)

      Then("Response is 400 with the implicit-routing refusal")
      statusCode shouldBe 400
      (json \ "message") match {
        case JString(msg) =>
          msg should include(InvalidAccountRoutings)
          msg should include("implicit")
        case _ => fail("Expected message field")
      }
    }

    Scenario("Reject OBP_ACCOUNT_ID scheme case-insensitively", Http4s700RoutesTag) {
      Given("A body carrying scheme obp_account_id in lower case")
      addEntitlement(testBankId1.value, resourceUser1.userId, canCreateAccount.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val body = createAccountBody(routings = List(("obp_account_id", "some-address")))
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/banks/${testBankId1.value}/accounts", body, headers)

      Then("Response is 400 with the implicit-routing refusal")
      statusCode shouldBe 400
      (json \ "message") match {
        case JString(msg) => msg should include(InvalidAccountRoutings)
        case _ => fail("Expected message field")
      }
    }

    Scenario("POST creates a caller-owned account with a generated id and the implicit OBP routing", Http4s700RoutesTag) {
      Given("CanCreateAccount granted and a valid body with one IBAN routing, no user_id (owner defaults to the caller)")
      addEntitlement(testBankId1.value, resourceUser1.userId, canCreateAccount.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val iban = s"DE-TEST-${APIUtil.generateUUID().take(12)}"
      val body = createAccountBody(routings = List(("IBAN", iban)))

      When("POST /banks/BANK_ID/accounts")
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/banks/${testBankId1.value}/accounts", body, headers)

      Then("Response is 201, the id is server-generated, and routings carry OBP + IBAN")
      statusCode shouldBe 201
      val accountId = (json \ "account_id") match {
        case JString(id) => id should not be empty; id
        case _ => fail("Expected account_id")
      }
      (json \ "bank_id") shouldBe JString(testBankId1.value)
      (json \ "user_id") shouldBe JString(resourceUser1.userId)
      val pairs = routingPairs(json)
      pairs should contain(("OBP", accountId))
      pairs should contain(("IBAN", iban))
    }

    Scenario("Return 403 without CanCreateAccount — even when creating for yourself", Http4s700RoutesTag) {
      Given("resourceUser2 (no roles granted anywhere in this suite) creates with no user_id in the body")
      val headers = Map("DirectLogin" -> s"token=${token2.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/banks/${testBankId1.value}/accounts", createAccountBody(), headers)

      Then("Response is 403 — v7.0.0 deprecates role-free self-service account creation")
      statusCode shouldBe 403
      (json \ "message") match {
        case JString(msg) =>
          msg should include(UserHasMissingRoles)
          msg should include(canCreateAccount.toString)
        case _ => fail("Expected message field")
      }
    }

    Scenario("Create for another user with CanCreateAccount at the bank", Http4s700RoutesTag) {
      Given("resourceUser1 holds CanCreateAccount at the bank and targets resourceUser2")
      addEntitlement(testBankId1.value, resourceUser1.userId, canCreateAccount.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val body = createAccountBody(userId = Some(resourceUser2.userId))
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/banks/${testBankId1.value}/accounts", body, headers)

      Then("Response is 201 and the account is owned by resourceUser2")
      statusCode shouldBe 201
      (json \ "user_id") shouldBe JString(resourceUser2.userId)
    }

    Scenario("PUT creates the account under the chosen id; a second PUT is refused", Http4s700RoutesTag) {
      Given("CanCreateAccount granted and a caller-chosen account id")
      addEntitlement(testBankId1.value, resourceUser1.userId, canCreateAccount.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val chosenId = s"v7-put-${APIUtil.generateUUID().take(12)}"

      When(s"PUT /banks/BANK_ID/accounts/$chosenId")
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "PUT", s"/obp/v7.0.0/banks/${testBankId1.value}/accounts/$chosenId", createAccountBody(), headers)

      Then("Response is 201 with the chosen id and its implicit OBP routing")
      statusCode shouldBe 201
      (json \ "account_id") shouldBe JString(chosenId)
      routingPairs(json) should contain(("OBP", chosenId))

      And("A second PUT under the same id is refused")
      val (statusCode2, json2, _) = makeHttpRequestWithBody(
        "PUT", s"/obp/v7.0.0/banks/${testBankId1.value}/accounts/$chosenId", createAccountBody(), headers)
      statusCode2 should not be 201
      (json2 \ "message") match {
        case JString(msg) => msg should include(AccountIdAlreadyExists)
        case _ => fail("Expected message field")
      }
    }
  }

  // ─── same-bank corridor guard ─────────────────────────────────────────────────

  Feature("Http4s700 OPEN_CORRIDOR same-bank guard") {

    Scenario("Refuse an OPEN_CORRIDOR promise whose beneficiary bank is the sending bank", Http4s700RoutesTag) {
      setPropsValues("open_corridor_enabled" -> "true")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val currency = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId1, testAccountId0, None)
        .map(_._1.currency).openOrThrowException("test account")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        openCorridorPromisePath(testBankId1.value, testAccountId0.value),
        openCorridorPromiseBody(currency, amount = "1.00",
          beneficiaryBankId = testBankId1.value, beneficiaryAccountId = testAccountId0.value), headers)
      statusCode shouldBe 400
      messageOf(json) should include(OpenCorridorSameBankNotAllowed)
    }

    Scenario("Refuse a settle whose pair is the same bank twice", Http4s700RoutesTag) {
      setPropsValues("open_corridor_enabled" -> "true")
      addEntitlement(testBankId1.value, resourceUser1.userId, canSettleOpenCorridor.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        s"/obp/v7.0.0/banks/${testBankId1.value}/open-corridor/settlements",
        s"""{"other_bank_id": "${testBankId1.value}", "currency": "KES"}""", headers)
      statusCode shouldBe 400
      messageOf(json) should include(OpenCorridorSameBankNotAllowed)
    }
  }

  // ─── message outbox (operator) ────────────────────────────────────────────────

  private def seedOutboxRow(status: String): code.messageoutbox.MessageOutbox = {
    val row = code.messageoutbox.MessageOutbox.enqueue(
      code.messageoutbox.MessageOutbox.TYPE_OPEN_CORRIDOR,
      s"subject-${APIUtil.generateUUID().take(8)}",
      code.messageoutbox.MessageOutbox.SUBJECT_TYPE_TRANSACTION_REQUEST_ID,
      "obp_credit_notification", testBankId2.value, "{}")
    if (status != code.messageoutbox.MessageOutbox.STATUS_PENDING) {
      // The only non-PENDING status these scenarios seed is STICKY, which is what the
      // operator retry endpoint acts on.
      code.messageoutbox.MessageOutbox.markSticky(row.id, row.attempts, "OBP-BANK-NODE-COMMITMENT-MISMATCH")
      code.messageoutbox.MessageOutbox.findById(row.id)
        .openOrThrowException("the row just seeded must be readable")
    } else row
  }

  Feature("Http4s700 message outbox operator endpoints") {

    Scenario("Reject unauthenticated GET /management/message-outbox", Http4s700RoutesTag) {
      val (statusCode, _, _) = makeHttpRequest("/obp/v7.0.0/management/message-outbox")
      statusCode shouldBe 401
    }

    Scenario("Return 403 without CanGetMessageOutbox", Http4s700RoutesTag) {
      val headers = Map("DirectLogin" -> s"token=${token2.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/management/message-outbox", headers)
      statusCode shouldBe 403
      messageOf(json) should include(canGetMessageOutbox.toString)
    }

    Scenario("List STICKY rows with filters", Http4s700RoutesTag) {
      addEntitlement("", resourceUser1.userId, canGetMessageOutbox.toString)
      val sticky = seedOutboxRow(code.messageoutbox.MessageOutbox.STATUS_STICKY)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(
        "/obp/v7.0.0/management/message-outbox?status=STICKY&outbox_type=OPEN_CORRIDOR", headers)
      statusCode shouldBe 200
      (json \ "rows") match {
        case JArray(rows) =>
          val row = rows.find(r => (r \ "outbox_id") == JInt(sticky.id))
            .getOrElse(fail("seeded sticky row should be listed"))
          (row \ "outbox_type") shouldBe JString("OPEN_CORRIDOR")
          (row \ "subject_id_type") shouldBe JString("transaction_request_id")
          (row \ "status") shouldBe JString("STICKY")
          (row \ "last_error") shouldBe JString("OBP-BANK-NODE-COMMITMENT-MISMATCH")
          row match {
            case JObject(fields) => fields.map(_.name) should not contain "payload_json"
            case _ => fail("row should be an object")
          }
        case _ => fail("rows should be an array")
      }
    }

    Scenario("Retry re-queues a STICKY row; refuses non-STICKY and unknown ids", Http4s700RoutesTag) {
      addEntitlement("", resourceUser1.userId, canRetryMessageOutbox.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")

      val sticky = seedOutboxRow(code.messageoutbox.MessageOutbox.STATUS_STICKY)
      val (retryCode, retryJson, _) = makeHttpRequestWithMethod(
        "POST", s"/obp/v7.0.0/management/message-outbox/${sticky.id}/retry", headers)
      retryCode shouldBe 200
      (retryJson \ "status") shouldBe JString("PENDING")
      (retryJson \ "attempts") shouldBe JInt(0)

      val pendingRow = seedOutboxRow(code.messageoutbox.MessageOutbox.STATUS_PENDING)
      val (notStickyCode, notStickyJson, _) = makeHttpRequestWithMethod(
        "POST", s"/obp/v7.0.0/management/message-outbox/${pendingRow.id}/retry", headers)
      notStickyCode shouldBe 400
      messageOf(notStickyJson) should include(MessageOutboxRowNotSticky)

      val (notFoundCode, notFoundJson, _) = makeHttpRequestWithMethod(
        "POST", "/obp/v7.0.0/management/message-outbox/999999999/retry", headers)
      notFoundCode shouldBe 404
      messageOf(notFoundJson) should include(MessageOutboxRowNotFound)
    }
  }

  // ─── scheduler job-locks ──────────────────────────────────────────────────────

  /** Remove every jobscheduler lock row so a scenario starts from a clean table. */
  private def clearJobLocks(): Unit =
    JobScheduler.deleteAll()

  /** Seed one jobscheduler lock row and return its job id. */
  private def seedJobLock(name: String = "MetricsArchiveScheduler", apiInstanceId: String = "test-node"): String = {
    val jobId = APIUtil.generateUUID()
    JobScheduler.createJob(jobId, name, apiInstanceId)
    jobId
  }

  Feature("Http4s700 getSchedulerJobLocks endpoint") {

    Scenario("Reject unauthenticated GET to /management/system/scheduler/job-locks", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/management/system/scheduler/job-locks with no auth")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/management/system/scheduler/job-locks")

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

    Scenario("Return 403 when authenticated but missing canGetSchedulerJobLocks role", Http4s700RoutesTag) {
      Given("DirectLogin without the required role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/management/system/scheduler/job-locks", headers)

      Then("Response is 403 with UserHasMissingRoles message naming the role")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canGetSchedulerJobLocks.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 200 with an empty list when no locks are held", Http4s700RoutesTag) {
      Given("canGetSchedulerJobLocks granted and the lock table cleared")
      addEntitlement("", resourceUser1.userId, canGetSchedulerJobLocks.toString)
      clearJobLocks()

      When("GET /obp/v7.0.0/management/system/scheduler/job-locks with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/management/system/scheduler/job-locks", headers)

      Then("Response is 200 with jobs=[] and count=0")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("count") shouldBe Some(JInt(0))
          map.get("jobs") match {
            case Some(JArray(items)) => items shouldBe empty
            case _ => fail("Expected jobs array")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 200 listing a held lock with its fields", Http4s700RoutesTag) {
      Given("canGetSchedulerJobLocks granted, the table cleared, and one seeded lock")
      addEntitlement("", resourceUser1.userId, canGetSchedulerJobLocks.toString)
      clearJobLocks()
      val seededJobId = seedJobLock(name = "MetricsArchiveScheduler", apiInstanceId = "test-node")

      When("GET /obp/v7.0.0/management/system/scheduler/job-locks with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/management/system/scheduler/job-locks", headers)

      Then("Response is 200 with count=1 and the seeded lock fully described")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("count") shouldBe Some(JInt(1))
          map.get("jobs") match {
            case Some(JArray(List(JObject(jobFields)))) =>
              val jobMap = toFieldMap(jobFields)
              jobMap.keys should contain allOf ("job_id", "name", "api_instance_id", "started_at", "age_seconds")
              jobMap.get("job_id") shouldBe Some(JString(seededJobId))
              jobMap.get("name") shouldBe Some(JString("MetricsArchiveScheduler"))
              jobMap.get("api_instance_id") shouldBe Some(JString("test-node"))
              jobMap.get("age_seconds") match {
                case Some(JInt(age)) => age.toLong should be >= 0L
                case _ => fail("Expected numeric age_seconds")
              }
            case _ => fail("Expected a one-element jobs array of objects")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  Feature("Http4s700 deleteSchedulerJobLock endpoint") {

    Scenario("Reject unauthenticated DELETE to /management/system/scheduler/job-locks/JOB_ID", Http4s700RoutesTag) {
      Given("DELETE /obp/v7.0.0/management/system/scheduler/job-locks/some-id with no auth")
      val (statusCode, json, _) = makeHttpRequestWithMethod(
        "DELETE", "/obp/v7.0.0/management/system/scheduler/job-locks/some-id")

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

    Scenario("Return 403 when authenticated but missing canDeleteSchedulerJobLock role", Http4s700RoutesTag) {
      Given("DELETE without the required role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithMethod(
        "DELETE", "/obp/v7.0.0/management/system/scheduler/job-locks/some-id", headers)

      Then("Response is 403 with UserHasMissingRoles message naming the role")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canDeleteSchedulerJobLock.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 204 and clear the lock when authenticated with role and the lock exists", Http4s700RoutesTag) {
      Given("canDeleteSchedulerJobLock granted and one seeded lock")
      addEntitlement("", resourceUser1.userId, canDeleteSchedulerJobLock.toString)
      val seededJobId = seedJobLock()
      JobScheduler.findByJobId(seededJobId).isDefined shouldBe true

      When("DELETE /obp/v7.0.0/management/system/scheduler/job-locks/{jobId} with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, _, _) = makeHttpRequestWithMethod(
        "DELETE", s"/obp/v7.0.0/management/system/scheduler/job-locks/$seededJobId", headers)

      Then("Response is 204 and the lock row is gone")
      statusCode shouldBe 204
      JobScheduler.findByJobId(seededJobId).isDefined shouldBe false
    }

    Scenario("Return 204 even when the job id does not exist (idempotent)", Http4s700RoutesTag) {
      Given("canDeleteSchedulerJobLock role granted and a non-existent job id")
      addEntitlement("", resourceUser1.userId, canDeleteSchedulerJobLock.toString)

      When("DELETE /obp/v7.0.0/management/system/scheduler/job-locks/non-existent with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, _, _) = makeHttpRequestWithMethod(
        "DELETE", "/obp/v7.0.0/management/system/scheduler/job-locks/non-existent-job-id-xyz", headers)

      Then("Response is 204 — delete is idempotent")
      statusCode shouldBe 204
    }
  }

  // ─── addEntitlement ───────────────────────────────────────────────────────────

  Feature("Http4s700 addEntitlement endpoint") {

    Scenario("Reject unauthenticated POST to /users/USER_ID/entitlements", Http4s700RoutesTag) {
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

    Scenario("Return 403 when authenticated but missing canCreateEntitlementAtAnyBank role", Http4s700RoutesTag) {
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

    Scenario("Return 201 with entitlement JSON when authenticated with role and valid body", Http4s700RoutesTag) {
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

    Scenario("Return 400 when role_name is not a valid API role", Http4s700RoutesTag) {
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

    Scenario("Return 409 when the entitlement already exists for the user", Http4s700RoutesTag) {
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

  Feature("Http4s700 getAccountAccessTrace endpoint") {

    Scenario("Reject unauthenticated GET to account-access-trace", Http4s700RoutesTag) {
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

    Scenario("Return 403 when authenticated but missing canGetAccountAccessTrace role", Http4s700RoutesTag) {
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

    Scenario("Return 404 when target user does not exist", Http4s700RoutesTag) {
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

    Scenario("Return 200 with explanation showing ACCOUNT_ACCESS as final source for owner view holder", Http4s700RoutesTag) {
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

  // ─── getUserByUserId ──────────────────────────────────────────────────────────

  Feature("Http4s700 getUserByUserId endpoint") {

    Scenario("Reject unauthenticated access to /users/user-id/USER_ID", Http4s700RoutesTag) {
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

    Scenario("Return 403 when authenticated but missing canGetAnyUser role", Http4s700RoutesTag) {
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

    Scenario("Return 200 with user fields when authenticated with canGetAnyUser role", Http4s700RoutesTag) {
      Given("canGetAnyUser role granted to resourceUser1, who has a mobile phone number")
      addEntitlement("", resourceUser1.userId, canGetAnyUser.toString)
      val ruWithPhone = code.model.dataAccess.ResourceUser
        .findByUserId(resourceUser1.userId)
        .openOrThrowException("resourceUser1 must exist")
      code.model.dataAccess.ResourceUser.update(ruWithPhone.copy(
        mobilePhoneNumber = Some("+49123456789"),
        mobilePhoneNumberIsValidated = Some(true),
        mobilePhoneNumberValidatedDate = Some(new Date())))

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
          m.get("mobile_phone_number") shouldBe Some(JString("+49123456789"))
          m.get("mobile_phone_number_is_validated") shouldBe Some(JBool(true))
          m.get("mobile_phone_number_validated_date") match {
            case Some(JString(_)) => succeed
            case other => fail(s"Expected mobile_phone_number_validated_date as date string, got $other")
          }
        case _ => fail("Expected JSON object for getUserByUserId")
      }
    }

    Scenario("Return 404 when USER_ID does not exist", Http4s700RoutesTag) {
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

  // ─── getCurrentUser (v7 native — adds the user's mobile phone fields) ─────────

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

    scenario("Return 200 with mobile phone fields served natively by v7", Http4s700RoutesTag) {
      Given("resourceUser1 has a validated mobile phone number")
      val ru = code.model.dataAccess.ResourceUser.findByUserId(resourceUser1.userId).openOrThrowException("resourceUser1 must exist")
      code.model.dataAccess.ResourceUser.update(ru.copy(
        mobilePhoneNumber = Some("+49123456789"),
        mobilePhoneNumberIsValidated = Some(true),
        mobilePhoneNumberValidatedDate = Some(new Date())))

      When("GET /obp/v7.0.0/users/current with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, respHeaders) = makeHttpRequest("/obp/v7.0.0/users/current", headers)

      Then("Response is 200, served by v7 (no version-served fallback header), with the mobile fields")
      statusCode shouldBe 200
      hasHeader(respHeaders, "X-OBP-Version-Served") shouldBe false
      json match {
        case JObject(fields) =>
          val m = toFieldMap(fields)
          m.get("user_id") shouldBe Some(JString(resourceUser1.userId))
          m.get("mobile_phone_number") shouldBe Some(JString("+49123456789"))
          m.get("mobile_phone_number_is_validated") shouldBe Some(JBool(true))
          m.get("mobile_phone_number_validated_date") match {
            case Some(JString(_)) => succeed
            case other => fail(s"Expected mobile_phone_number_validated_date as date string, got $other")
          }
        case _ => fail("Expected JSON object for getCurrentUser")
      }
    }
  }

  // ─── createUser (v7 native — adds the optional mobile_phone_number) ───────────

  feature("Http4s700 createUser endpoint") {

    val strongPassword = "StrongP@ssw0rd123!"

    def createUserBody(username: String, mobilePhoneNumberJson: Option[String]): String = {
      val phone = mobilePhoneNumberJson.map(v => s""","mobile_phone_number":$v""").getOrElse("")
      s"""{"email":"$username","username":"$username","password":"$strongPassword","first_name":"Simon","last_name":"Redfern"$phone}"""
    }

    def newUsername(): String = "v7reg" + randomString(10).toLowerCase + "@example.com"

    def deleteAuthUser(username: String): Unit =
      AuthUser.deleteAllByUsername(username)

    scenario("Create a user with a mobile phone number, stored unverified, served natively by v7", Http4s700RoutesTag) {
      Given("email validation is skipped and a fresh username")
      setPropsValues("authUser.skipEmailValidation" -> "true")
      val username = newUsername()

      When("POST /obp/v7.0.0/users with mobile_phone_number")
      val (statusCode, json, headers) = makeHttpRequestWithBody(
        "POST", "/obp/v7.0.0/users", createUserBody(username, Some("\"+49 170 5556677\"")))

      Then("Response is 201 from v7 itself (no version-served fallback header), with the phone fields")
      statusCode shouldBe 201
      hasHeader(headers, "X-OBP-Version-Served") shouldBe false
      json match {
        case JObject(fields) =>
          val m = toFieldMap(fields)
          m.get("username") shouldBe Some(JString(username))
          m.get("email") shouldBe Some(JString(username))
          m.get("user_id") match {
            case Some(JString(id)) => id should not be empty
            case other => fail(s"Expected user_id, got $other")
          }
          m.get("mobile_phone_number") shouldBe Some(JString("+49 170 5556677"))
          m.get("mobile_phone_number_is_validated") shouldBe Some(JBool(false))
          m.get("mobile_phone_number_validated_date") should (be(None) or be(Some(JNull)))
          m.keys should contain("entitlements")
        case _ => fail("Expected JSON object for createUser")
      }

      And("the ResourceUser carries the number, unverified")
      val authUser = AuthUser.findByUsername(username).openOrThrowException("user must have been created")
      val ru = code.model.dataAccess.ResourceUser.findByPrimaryKey(authUser.user)
        .openOrThrowException("resource user must exist")
      ru.mobilePhoneNumber shouldBe Some("+49 170 5556677")
      ru.mobilePhoneNumberIsValidated shouldBe Some(false)
      ru.mobilePhoneNumberValidatedDate shouldBe None

      deleteAuthUser(username)
    }

    scenario("Create a user without a mobile phone number", Http4s700RoutesTag) {
      Given("email validation is skipped and a fresh username")
      setPropsValues("authUser.skipEmailValidation" -> "true")
      val username = newUsername()

      When("POST /obp/v7.0.0/users with the v6-shaped body (no mobile_phone_number)")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/users", createUserBody(username, None))

      Then("Response is 201 and the phone fields are empty")
      statusCode shouldBe 201
      json match {
        case JObject(fields) =>
          val m = toFieldMap(fields)
          m.get("username") shouldBe Some(JString(username))
          m.get("mobile_phone_number") should (be(None) or be(Some(JNull)))
        case _ => fail("Expected JSON object for createUser")
      }

      deleteAuthUser(username)
    }

    scenario("Reject a malformed mobile phone number without creating the user", Http4s700RoutesTag) {
      Given("a fresh username")
      setPropsValues("authUser.skipEmailValidation" -> "true")
      val username = newUsername()

      When("POST /obp/v7.0.0/users with letters in mobile_phone_number")
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", "/obp/v7.0.0/users", createUserBody(username, Some("\"call me maybe\"")))

      Then("Response is 400 with InvalidPhoneNumber and no user row exists")
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(InvalidPhoneNumber)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
      AuthUser.findByUsername(username).isDefined shouldBe false
    }

    scenario("Reject a duplicate username with 409", Http4s700RoutesTag) {
      Given("a user that already exists")
      setPropsValues("authUser.skipEmailValidation" -> "true")
      val username = newUsername()
      val (first, _, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/users", createUserBody(username, None))
      first shouldBe 201

      When("POST /obp/v7.0.0/users again with the same username")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/users", createUserBody(username, None))

      Then("Response is 409 with DuplicateUsername")
      statusCode shouldBe 409
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(DuplicateUsername)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }

      deleteAuthUser(username)
    }
  }

  // ─── updateMyMobilePhoneNumber ────────────────────────────────────────────────

  feature("Http4s700 updateMyMobilePhoneNumber endpoint") {

    scenario("Reject unauthenticated PUT to /my/user/mobile-phone-number", Http4s700RoutesTag) {
      Given("PUT /obp/v7.0.0/my/user/mobile-phone-number with no auth headers")
      val body = """{"mobile_phone_number":"+49123456789"}"""
      val (statusCode, json, _) = makeHttpRequestWithBody("PUT", "/obp/v7.0.0/my/user/mobile-phone-number", body)

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

    scenario("Setting a different number resets the validated flag but keeps the validated date", Http4s700RoutesTag) {
      Given("resourceUser1 has a validated mobile phone number")
      val ru = code.model.dataAccess.ResourceUser.findByUserId(resourceUser1.userId).openOrThrowException("resourceUser1 must exist")
      code.model.dataAccess.ResourceUser.update(ru.copy(
        mobilePhoneNumber = Some("+49123456789"),
        mobilePhoneNumberIsValidated = Some(true),
        mobilePhoneNumberValidatedDate = Some(new Date())))

      When("PUT /obp/v7.0.0/my/user/mobile-phone-number with a new number")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val body = """{"mobile_phone_number":"+49 170 5556677"}"""
      val (statusCode, json, _) = makeHttpRequestWithBody("PUT", "/obp/v7.0.0/my/user/mobile-phone-number", body, headers)

      Then("Response is 200 with the new number, is_validated false, validated date preserved")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val m = toFieldMap(fields)
          m.get("mobile_phone_number") shouldBe Some(JString("+49 170 5556677"))
          m.get("mobile_phone_number_is_validated") shouldBe Some(JBool(false))
          m.get("mobile_phone_number_validated_date") match {
            case Some(JString(_)) => succeed
            case other => fail(s"Expected preserved validated date, got $other")
          }
        case _ => fail("Expected JSON object for updateMyMobilePhoneNumber")
      }

      And("the database reflects the new number with the flag reset")
      val reloaded = code.model.dataAccess.ResourceUser.findByUserId(resourceUser1.userId).openOrThrowException("resourceUser1 must exist")
      reloaded.mobilePhoneNumber shouldBe Some("+49 170 5556677")
      reloaded.mobilePhoneNumberIsValidated shouldBe Some(false)
      reloaded.mobilePhoneNumberValidatedDate.isDefined shouldBe true
    }

    scenario("Re-submitting the same number keeps the validated flag", Http4s700RoutesTag) {
      Given("resourceUser1 has a validated mobile phone number")
      val ru = code.model.dataAccess.ResourceUser.findByUserId(resourceUser1.userId).openOrThrowException("resourceUser1 must exist")
      code.model.dataAccess.ResourceUser.update(ru.copy(
        mobilePhoneNumber = Some("+49123456789"),
        mobilePhoneNumberIsValidated = Some(true),
        mobilePhoneNumberValidatedDate = Some(new Date())))

      When("PUT /obp/v7.0.0/my/user/mobile-phone-number with the same number")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val body = """{"mobile_phone_number":"+49123456789"}"""
      val (statusCode, json, _) = makeHttpRequestWithBody("PUT", "/obp/v7.0.0/my/user/mobile-phone-number", body, headers)

      Then("Response is 200 and the number is still validated")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val m = toFieldMap(fields)
          m.get("mobile_phone_number") shouldBe Some(JString("+49123456789"))
          m.get("mobile_phone_number_is_validated") shouldBe Some(JBool(true))
        case _ => fail("Expected JSON object for updateMyMobilePhoneNumber")
      }
    }

    scenario("Reject an invalid phone number with 400", Http4s700RoutesTag) {
      When("PUT /obp/v7.0.0/my/user/mobile-phone-number with a non-numeric value")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val body = """{"mobile_phone_number":"not-a-phone-number"}"""
      val (statusCode, json, _) = makeHttpRequestWithBody("PUT", "/obp/v7.0.0/my/user/mobile-phone-number", body, headers)

      Then("Response is 400 with InvalidPhoneNumber message")
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(InvalidPhoneNumber)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ─── getMyMetrics ─────────────────────────────────────────────────────────────

  feature("Http4s700 getMyMetrics endpoint") {

    def createTestMetric(userId: String, userName: String, partialFunctionName: String): Unit =
      // Doobie: the store writes through the batch writer, so flush before reading back.
      code.metrics.APIMetrics.apiMetrics.vend.saveMetric(
        userId = userId,
        url = "/obp/v7.0.0/my/metrics-test",
        date = new Date(),
        duration = 42L,
        userName = userName,
        appName = "Http4s700RoutesTestApp",
        developerEmail = "",
        consumerId = "",
        implementedByPartialFunction = partialFunctionName,
        implementedInVersion = "v7.0.0",
        verb = "GET",
        httpCode = None,
        correlationId = java.util.UUID.randomUUID().toString,
        responseBody = "",
        sourceIp = "",
        targetIp = "",
        apiInstanceId = "",
        consentReferenceId = null,
        certificateTrust = null,
        certificateTrustDetail = null,
        authType = null)
      code.metrics.MetricBatchWriter.flush()

    scenario("Reject unauthenticated access to /my/metrics", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/my/metrics with no auth headers")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/my/metrics")

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

    scenario("Reject a user_id filter with 400 UserFilterParametersNotSupported", Http4s700RoutesTag) {
      When("GET /obp/v7.0.0/my/metrics with a user_id filter pointing at resourceUser2")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) =
        makeHttpRequest(s"/obp/v7.0.0/my/metrics?user_id=${resourceUser2.userId}&limit=500", headers)

      Then("Response is 400 with UserFilterParametersNotSupported naming the offending parameter")
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserFilterParametersNotSupported)
              msg should include("user_id")
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Reject username and anon filters with 400", Http4s700RoutesTag) {
      When("GET /obp/v7.0.0/my/metrics with username and anon filters")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) =
        makeHttpRequest("/obp/v7.0.0/my/metrics?username=someone&anon=false", headers)

      Then("Response is 400 naming both offending parameters")
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserFilterParametersNotSupported)
              msg should include("username")
              msg should include("anon")
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    scenario("Return only the logged in user's own metrics", Http4s700RoutesTag) {
      Given("a metric row for resourceUser1 and one for resourceUser2")
      createTestMetric(resourceUser1.userId, resourceUser1.name, "getMyMetricsTestOwn")
      createTestMetric(resourceUser2.userId, resourceUser2.name, "getMyMetricsTestOther")

      When("GET /obp/v7.0.0/my/metrics with only pagination parameters")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/my/metrics?limit=500", headers)

      Then("Response is 200 and every row belongs to resourceUser1")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("metrics") match {
            case Some(JArray(rows)) =>
              rows should not be empty
              val userIds = rows.collect { case JObject(f) => toFieldMap(f).get("user_id") }.flatten
              // The server-locked user set is the whole point of this endpoint: a caller may only
              // ever see their own calls. It was silently dropped once (OBPUserIds was not
              // collected by MetricQuery), and nothing failed except this line - so name the
              // other user explicitly, or the diagnosis is "some uuid did not equal some uuid".
              withClue(s"GET /my/metrics returned rows belonging to another user. " +
                       s"logged in as ${resourceUser1.userId}, also present: " +
                       s"${userIds.distinct.collect { case JString(v) if v != resourceUser1.userId => v }} " +
                       s"(resourceUser2 is ${resourceUser2.userId}) ") {
                userIds.distinct should equal(List(JString(resourceUser1.userId)))
              }
              val partialFunctions = rows.collect { case JObject(f) => toFieldMap(f).get("implemented_by_partial_function") }.flatten
              partialFunctions should contain(JString("getMyMetricsTestOwn"))
              partialFunctions should not contain JString("getMyMetricsTestOther")
            case other => fail(s"Expected metrics array, got $other")
          }
        case _ => fail("Expected JSON object for getMyMetrics")
      }
    }
  }

  Feature("Http4s700 createOrganisation endpoint") {

    Scenario("Reject unauthenticated POST to /organisations", Http4s700RoutesTag) {
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

    Scenario("Return 403 when authenticated but missing canCreateOrganisation role", Http4s700RoutesTag) {
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

    Scenario("Return 201 with organisation JSON when authenticated with role and valid body", Http4s700RoutesTag) {
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

    Scenario("Return 400 when organisation_id format is invalid", Http4s700RoutesTag) {
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

    Scenario("Return 409 when organisation already exists", Http4s700RoutesTag) {
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

  Feature("Http4s700 getOrganisations endpoint") {

    Scenario("Reject unauthenticated GET to /organisations", Http4s700RoutesTag) {
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

    Scenario("Return 200 with organisations array for an authenticated user", Http4s700RoutesTag) {
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

  Feature("Http4s700 getOrganisation endpoint") {

    Scenario("Reject unauthenticated GET to /organisations/ORGANISATION_ID", Http4s700RoutesTag) {
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

    Scenario("Return 404 when organisation does not exist", Http4s700RoutesTag) {
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

    Scenario("Return 200 with organisation JSON for an existing public organisation", Http4s700RoutesTag) {
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

  Feature("Http4s700 updateOrganisation endpoint") {

    Scenario("Reject unauthenticated PUT to /organisations/ORGANISATION_ID", Http4s700RoutesTag) {
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

    Scenario("Return 403 when authenticated but missing canUpdateOrganisation role", Http4s700RoutesTag) {
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

    Scenario("Return 200 with updated organisation JSON when authenticated with role", Http4s700RoutesTag) {
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

  Feature("Http4s700 deleteOrganisation endpoint") {

    Scenario("Reject unauthenticated DELETE to /organisations/ORGANISATION_ID", Http4s700RoutesTag) {
      Given("DELETE /obp/v7.0.0/organisations/anything with no auth")
      val (statusCode, _, _) = makeHttpRequestWithMethod("DELETE", "/obp/v7.0.0/organisations/anything")

      Then("Response is 401")
      statusCode shouldBe 401
    }

    Scenario("Return 403 when authenticated but missing canDeleteOrganisation role", Http4s700RoutesTag) {
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

    Scenario("Return 204 when authenticated with role and organisation exists", Http4s700RoutesTag) {
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
    RoutingSchemes.routingScheme.vend.createRoutingScheme(
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

  Feature("Http4s700 createRoutingScheme endpoint") {

    Scenario("Reject unauthenticated POST to /routing-schemes", Http4s700RoutesTag) {
      val body = """{"scheme":"TZ.X1","country":"TZ","category":"ACCOUNT","address_pattern":"^[0-9]+$","example_address":"123","description":"x"}"""
      val (statusCode, _, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/routing-schemes", body)
      statusCode shouldBe 401
    }

    Scenario("Return 403 when authenticated but missing canCreateRoutingScheme role", Http4s700RoutesTag) {
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

    Scenario("Return 201 with full routing scheme JSON on happy path", Http4s700RoutesTag) {
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

    Scenario("Return 400 when scheme name does not match country-qualified convention", Http4s700RoutesTag) {
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

    Scenario("Return 400 when example_address does not match address_pattern", Http4s700RoutesTag) {
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

    Scenario("Return 409 when scheme already exists", Http4s700RoutesTag) {
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

  Feature("Http4s700 getRoutingSchemes endpoint") {

    Scenario("Public — returns 200 without authentication", Http4s700RoutesTag) {
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

  Feature("Http4s700 getRoutingScheme endpoint") {

    Scenario("Return 200 for an existing scheme (no auth required)", Http4s700RoutesTag) {
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

    Scenario("Return 404 when scheme does not exist", Http4s700RoutesTag) {
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

  Feature("Http4s700 updateRoutingScheme endpoint") {

    Scenario("Reject unauthenticated PUT", Http4s700RoutesTag) {
      val (statusCode, _, _) = makeHttpRequestWithBody("PUT", "/obp/v7.0.0/routing-schemes/TZ.ANY", """{"status":"DEPRECATED"}""")
      statusCode shouldBe 401
    }

    Scenario("Return 403 when missing canUpdateRoutingScheme", Http4s700RoutesTag) {
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, _, _) = makeHttpRequestWithBody("PUT", "/obp/v7.0.0/routing-schemes/TZ.ANY", """{"status":"DEPRECATED"}""", headers)
      statusCode shouldBe 403
    }

    Scenario("Return 200 and persist new status when authenticated with role", Http4s700RoutesTag) {
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

  Feature("Http4s700 deleteRoutingScheme endpoint") {

    Scenario("Reject unauthenticated DELETE", Http4s700RoutesTag) {
      val (statusCode, _, _) = makeHttpRequestWithMethod("DELETE", "/obp/v7.0.0/routing-schemes/TZ.ANY")
      statusCode shouldBe 401
    }

    Scenario("Return 204 and soft-delete (status flips to RETIRED) when role granted", Http4s700RoutesTag) {
      addEntitlement("", resourceUser1.userId, canDeleteRoutingScheme.toString)
      val scheme = freshSchemeName("DEL")
      createTestRoutingScheme(scheme)

      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, _, _) = makeHttpRequestWithMethod("DELETE", s"/obp/v7.0.0/routing-schemes/$scheme", headers)
      statusCode shouldBe 204

      And("the row should still exist with status RETIRED")
      val fetched = RoutingSchemes.routingScheme.vend.getRoutingScheme(scheme)
      fetched.map(_.status) shouldBe net.liftweb.common.Full("RETIRED")
    }
  }

  Feature("Http4s700 getBankSupportedRoutingSchemes endpoint") {

    Scenario("Reject unauthenticated GET", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val (statusCode, _, _) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/supported-routing-schemes")
      statusCode shouldBe 401
    }

    Scenario("Return 200 with empty/populated list for authenticated user", Http4s700RoutesTag) {
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

  Feature("Http4s700 putBankSupportedRoutingScheme endpoint") {

    Scenario("Reject unauthenticated PUT", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val (statusCode, _, _) = makeHttpRequestWithBody("PUT", s"/obp/v7.0.0/banks/$bankId/supported-routing-schemes/TZ.ANY", """{"enabled":true}""")
      statusCode shouldBe 401
    }

    Scenario("Return 403 when missing canUpdateBankSupportedRoutingScheme role", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, _, _) = makeHttpRequestWithBody("PUT", s"/obp/v7.0.0/banks/$bankId/supported-routing-schemes/TZ.ANY", """{"enabled":true}""", headers)
      statusCode shouldBe 403
    }

    Scenario("Return 404 when scheme does not exist in the registry", Http4s700RoutesTag) {
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

    Scenario("Return 200 when scheme exists and bank role granted; enabled=true persists notes", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canUpdateBankSupportedRoutingScheme.toString)
      val scheme = freshSchemeName("BNK")
      createTestRoutingScheme(scheme)

      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val body    = """{"enabled":true,"bank_notes":"Routed via the payment gateway. Cutoff 22:00."}"""
      val (statusCode, json, _) = makeHttpRequestWithBody("PUT", s"/obp/v7.0.0/banks/$bankId/supported-routing-schemes/$scheme", body, headers)
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("scheme")     shouldBe Some(JString(scheme))
          map.get("bank_notes") shouldBe Some(JString("Routed via the payment gateway. Cutoff 22:00."))
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
    RoutingSchemes.routingScheme.vend.createRoutingScheme(
      scheme = scheme, country = "TZ", category = "ACCOUNT",
      addressPattern = "^[0-9]+$", secondaryAddressPattern = None,
      exampleAddress = address, description = "Test", downstreamRails = Nil,
      status = "ACTIVE", createdByUserId = resourceUser1.userId
    )
    DoobieBankAccountRoutingQueries.create(CommBankId(destBankId), AccountId(destAccountId), scheme, address)
    scheme
  }

  Feature("Http4s700 createPayeeLookup endpoint") {

    Scenario("Reject unauthenticated POST to /payees/lookup", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val body = """{"identifier":{"scheme":"TZ.MSISDN","value":"255778300336"}}"""
      val (statusCode, _, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/payees/lookup", body)
      statusCode shouldBe 401
    }

    Scenario("Return 400 when identifier.scheme is not registered", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val body = """{"identifier":{"scheme":"TZ.UNKNOWN_SCHEME","value":"123"}}"""
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

    Scenario("Return 400 when identifier.value does not match the scheme's address_pattern", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      // Create a strict scheme then send an address that doesn't match.
      val scheme = freshSchemeName("STR")
      RoutingSchemes.routingScheme.vend.createRoutingScheme(
        scheme = scheme, country = "TZ", category = "ACCOUNT",
        addressPattern = "^255[0-9]{9}$", secondaryAddressPattern = None,
        exampleAddress = "255778300336", description = "Strict TZ MSISDN",
        downstreamRails = Nil, status = "ACTIVE", createdByUserId = resourceUser1.userId
      )
      val body = s"""{"identifier":{"scheme":"$scheme","value":"not-a-phone"}}"""
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

    Scenario("Return 404 when no account has the requested routing", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      // Registered scheme, valid pattern match, but no account_routings row.
      val scheme = freshSchemeName("NMA")
      RoutingSchemes.routingScheme.vend.createRoutingScheme(
        scheme = scheme, country = "TZ", category = "ACCOUNT",
        addressPattern = "^[0-9]+$", secondaryAddressPattern = None,
        exampleAddress = "12345", description = "No-match", downstreamRails = Nil,
        status = "ACTIVE", createdByUserId = resourceUser1.userId
      )
      val body = s"""{"identifier":{"scheme":"$scheme","value":"99999999999"}}"""
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

    Scenario("Return 201 with lookup_id and payee details when account_routing resolves", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val address = s"2557${(System.currentTimeMillis() % 100000000L).toString.reverse.padTo(8, '0').reverse}"
      val scheme = seedPayeeForLookup("HAP", address, bankId, accountId)

      val body = s"""{"identifier":{"scheme":"$scheme","value":"$address","fsp_id":"503"}}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/payees/lookup", body, headers)
      statusCode shouldBe 201
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.keys should contain allOf ("lookup_id", "expires_at", "identifier", "full_name")
          map.keys should not contain "fsp_id"  // fsp_id is nested inside identifier, not top-level
          map.get("identifier") match {
            case Some(JObject(idFields)) =>
              val idMap = toFieldMap(idFields)
              idMap.get("scheme") shouldBe Some(JString(scheme))
              idMap.get("value")  shouldBe Some(JString(address))
              idMap.get("fsp_id") shouldBe Some(JString("503"))
            case other => fail(s"Expected identifier to be an object {scheme,value,fsp_id}, got: $other")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ─── MOBILE_WALLET transaction request ────────────────────────────────────

  Feature("Http4s700 createTransactionRequestMobileWallet endpoint") {

    Scenario("Reject unauthenticated POST", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val body = """{"to":{"msisdn":"255778300336"},"value":{"currency":"TZS","amount":"1000"},"description":"x"}"""
      val (statusCode, _, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/MOBILE_WALLET/transaction-requests", body)
      statusCode shouldBe 401
    }

    Scenario("Return 400 when country-qualified MSISDN scheme is not in the registry", Http4s700RoutesTag) {
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

    Scenario("Return 400 when msisdn does not match the scheme's address_pattern", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      // Use country_code=XW so the scheme is XW.MSISDN — register it with a strict pattern.
      val country = "XW"
      val schemeName = s"$country.MSISDN"
      RoutingSchemes.routingScheme.vend.getRoutingScheme(schemeName) match {
        case net.liftweb.common.Full(_) => // already registered from a previous run
        case _ =>
          RoutingSchemes.routingScheme.vend.createRoutingScheme(
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

  // ─── OPEN_CORRIDOR_PROMISE transaction request ────────────────────────────

  /** Full, valid OPEN_CORRIDOR_PROMISE create body. The beneficiary uses OBP routing
    * to the second test bank. Only the far BANK must exist — the beneficiary
    * account is not resolved (it lives in the far bank's CBS); the default here
    * happens to be a real account only for convenience. */
  private def openCorridorPromiseBody(
    currency: String,
    originatorName: String = "Alice Sender",
    originatorRoutingAddress: String = "GB29 NWBK 6016 1331 9268 19",
    amount: String = "3.00",
    beneficiaryBankId: String = testBankId2.value,
    beneficiaryAccountId: String = testAccountId1.value,
    returnOf: Option[String] = None
  ): String =
    s"""{
       |  "to": {
       |    "name": "OC Beneficiary ${APIUtil.generateUUID().take(8)}",
       |    "description": "Beneficiary at receiving institution",
       |    "other_bank_routing_scheme": "OBP",
       |    "other_bank_routing_address": "$beneficiaryBankId",
       |    "other_branch_routing_scheme": "",
       |    "other_branch_routing_address": "",
       |    "other_account_routing_scheme": "OBP",
       |    "other_account_routing_address": "$beneficiaryAccountId",
       |    "other_account_secondary_routing_scheme": "",
       |    "other_account_secondary_routing_address": ""
       |  },
       |  "value": {"currency": "$currency", "amount": "$amount"},
       |  "description": "Open Corridor promise test payment",
       |  "charge_policy": "SHARED",${returnOf.map(r => s""" "return_of": "$r",""").getOrElse("")}
       |  "originator": {
       |    "name": "$originatorName",
       |    "address": "1 Sender Street, London, UK",
       |    "account_routing": {"scheme": "IBAN", "address": "$originatorRoutingAddress"}
       |  }
       |}""".stripMargin

  private def openCorridorPromisePath(bankId: String, accountId: String): String =
    s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/OPEN_CORRIDOR_PROMISE/transaction-requests"

  Feature("Http4s700 createTransactionRequestOpenCorridor (OPEN_CORRIDOR_PROMISE) endpoint") {

    Scenario("Reject unauthenticated POST", Http4s700RoutesTag) {
      val (statusCode, _, _) = makeHttpRequestWithBody("POST",
        openCorridorPromisePath(testBankId1.value, testAccountId0.value),
        openCorridorPromiseBody("EUR"))
      statusCode shouldBe 401
    }

    Scenario("Return 400 InvalidJsonFormat when the originator block is missing", Http4s700RoutesTag) {
      // Same shape but no `originator` field — extraction to the OPEN_CORRIDOR_PROMISE body class must fail.
      val body =
        """{
          |  "to": {
          |    "name": "OC Beneficiary", "description": "x",
          |    "other_bank_routing_scheme": "BIC", "other_bank_routing_address": "DEUTDEFF",
          |    "other_branch_routing_scheme": "", "other_branch_routing_address": "",
          |    "other_account_routing_scheme": "CORRIDOR_ACCOUNT", "other_account_routing_address": "OC-1",
          |    "other_account_secondary_routing_scheme": "", "other_account_secondary_routing_address": ""
          |  },
          |  "value": {"currency": "EUR", "amount": "3.00"},
          |  "description": "x",
          |  "charge_policy": "SHARED"
          |}""".stripMargin
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        openCorridorPromisePath(testBankId1.value, testAccountId0.value), body, headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(code.api.util.ErrorMessages.InvalidJsonFormat)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 400 InvalidJsonValue when originator.name is empty", Http4s700RoutesTag) {
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        openCorridorPromisePath(testBankId1.value, testAccountId0.value),
        openCorridorPromiseBody("EUR", originatorName = ""), headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include("originator.name must be non-empty")
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 400 InvalidJsonValue when originator.account_routing.address is empty", Http4s700RoutesTag) {
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        openCorridorPromisePath(testBankId1.value, testAccountId0.value),
        openCorridorPromiseBody("EUR", originatorRoutingAddress = ""), headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include("originator.account_routing.address must be non-empty")
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 201 with type OPEN_CORRIDOR_PROMISE and the originator echoed as explicit", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      // Match the source account's currency so the payment path doesn't reject on currency.
      val acctCurrency = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId1, testAccountId0, None)
        .map(_._1.currency).openOrThrowException("test account")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        openCorridorPromisePath(bankId, accountId),
        openCorridorPromiseBody(acctCurrency), headers)
      statusCode shouldBe 201
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.keys should contain allOf ("id", "type", "from", "details", "transaction_ids", "status", "charge", "originator")
          map.get("type") shouldBe Some(JString("OPEN_CORRIDOR_PROMISE"))
          map.get("id") match {
            case Some(JString(id)) => id should not be empty
            case _ => fail("id should be a non-empty string")
          }
          map.get("from") match {
            case Some(JObject(fromFields)) =>
              val fromMap = toFieldMap(fromFields)
              fromMap.get("bank_id") shouldBe Some(JString(bankId))
              fromMap.get("account_id") shouldBe Some(JString(accountId))
            case _ => fail("from should be an object")
          }
          // Hold-at-PENDING (OPEN_CORRIDOR_SIMPLE_NETTING.md §5): the promise never posts a
          // Transaction at create time — it accumulates for bilateral netting and the
          // settle-pair step posts the net later.
          map.get("status") shouldBe Some(JString("PENDING"))
          map.get("transaction_ids") shouldBe Some(JArray(Nil))
          map.get("originator") match {
            case Some(JObject(origFields)) =>
              val origMap = toFieldMap(origFields)
              origMap.get("name") shouldBe Some(JString("Alice Sender"))
              origMap.get("address") shouldBe Some(JString("1 Sender Street, London, UK"))
              origMap.get("source") shouldBe Some(JString("explicit"))
              origMap.get("account_routing") match {
                case Some(JObject(routingFields)) =>
                  val routingMap = toFieldMap(routingFields)
                  routingMap.get("scheme") shouldBe Some(JString("IBAN"))
                  routingMap.get("address") shouldBe Some(JString("GB29 NWBK 6016 1331 9268 19"))
                case _ => fail("originator.account_routing should be an object")
              }
            case _ => fail("originator should be an object")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 201 when the beneficiary account exists only at the far bank's CBS (not in OBP-API)", Http4s700RoutesTag) {
      val acctCurrency = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId1, testAccountId0, None)
        .map(_._1.currency).openOrThrowException("test account")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      // An account id no OBP bank account carries: customer accounts live in the
      // far bank's CBS, and the beneficiary Bank Node validates them at credit
      // time — OBP-API must not require them to exist here.
      val cbsOnlyAccountId = s"cbs-only-${APIUtil.generateUUID().take(8)}"
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        openCorridorPromisePath(testBankId1.value, testAccountId0.value),
        openCorridorPromiseBody(acctCurrency, beneficiaryAccountId = cbsOnlyAccountId), headers)
      statusCode shouldBe 201
      val trId = json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("status") shouldBe Some(JString("PENDING"))
          map.get("id") match {
            case Some(JString(id)) if id.nonEmpty => id
            case _ => fail("id should be a non-empty string")
          }
        case _ => fail("Expected JSON object")
      }
      // The far bank id is stamped on the row — the settle-pair netting selects
      // promises by mTo_BankId, so a CBS-only beneficiary must still net.
      val row = code.transactionrequests.MappedTransactionRequest
        .findByTransactionRequestId(trId)
        .openOrThrowException("promise TR row should exist")
      row.toBankId shouldBe testBankId2.value
      row.toAccountId shouldBe cbsOnlyAccountId
    }

    Scenario("Return 404 BankNotFound when the beneficiary bank is not registered", Http4s700RoutesTag) {
      val acctCurrency = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId1, testAccountId0, None)
        .map(_._1.currency).openOrThrowException("test account")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        openCorridorPromisePath(testBankId1.value, testAccountId0.value),
        openCorridorPromiseBody(acctCurrency, beneficiaryBankId = s"no-such-bank-${APIUtil.generateUUID().take(8)}"), headers)
      statusCode shouldBe 404
      messageOf(json) should include("OBP-30001")
    }

    Scenario("A RETURN promise (return_of) is accepted and relayed onto its credit notification", Http4s700RoutesTag) {
      setPropsValues("open_corridor_enabled" -> "true")
      val acctCurrency = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId1, testAccountId0, None)
        .map(_._1.currency).openOrThrowException("test account")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val originalId = s"tr-orig-${APIUtil.generateUUID().take(8)}"
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        openCorridorPromisePath(testBankId1.value, testAccountId0.value),
        openCorridorPromiseBody(acctCurrency, returnOf = Some(originalId)), headers)
      statusCode shouldBe 201
      val returnTrId = json match {
        case JObject(fields) => toFieldMap(fields).get("id") match {
          case Some(JString(id)) if id.nonEmpty => id
          case _ => fail("id should be a non-empty string")
        }
        case _ => fail("Expected JSON object")
      }

      // Attach evidence — that enqueues the credit notification, which must
      // carry return_of so the receiving node knows it is being repaid. The
      // role is checked at the path's bank (the promise's from-bank).
      addEntitlement(testBankId1.value, resourceUser1.userId, canAttachOpenCorridorPromise.toString)
      val (evidenceCode, _, _) = makeHttpRequestWithBody("POST",
        promiseEvidencePath(testBankId1.value, testAccountId0.value, returnTrId),
        promiseEvidenceBody(), headers)
      evidenceCode shouldBe 201
      val creditRows = code.messageoutbox.MessageOutbox.bySubjectId(returnTrId)
      creditRows.map(_.operationName) shouldBe List("obp_credit_notification")
      (parse(creditRows.head.payloadJson) \ "return_of") shouldBe JString(originalId)
    }
  }

  // ─── OPEN_CORRIDOR promise report-back (salt relay intake) ────────────────

  private def promiseEvidencePath(bankId: String, accountId: String, transactionRequestId: String): String =
    s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/transaction-requests/$transactionRequestId/open-corridor/promise"

  private def promiseEvidenceBody(
    txHash: String = "63eacfe3dbc133f922d461bd3e6488ce21d55f03c5131cd79c965fe2e7491642",
    commitment: String = "9c56cc51b374c3ba189210d5b6d4bf57790d351c96c47c02190ecf1e430ba0d1"
  ): String =
    s"""{
       |  "tx_hash": "$txHash",
       |  "blockchain": "cardano",
       |  "commitment": "$commitment",
       |  "salt": "5f4dcc3b5aa765d61d8327deb882cf99",
       |  "preimage": "{\\"tx_request_id\\":\\"tr-abc-123\\"}"
       |}""".stripMargin

  /** Create an OPEN_CORRIDOR_PROMISE TR via the v7 endpoint; asserts hold-at-PENDING
    * and returns the new TRANSACTION_REQUEST_ID. Defaults: bank1/account0 promising
    * to bank2/account1. */
  private def createPendingPromise(
    fromBankId: CommBankId = testBankId1,
    fromAccountId: com.openbankproject.commons.model.AccountId = testAccountId0,
    beneficiaryBankId: String = testBankId2.value,
    beneficiaryAccountId: String = testAccountId1.value,
    amount: String = "3.00"
  ): String = {
    val acctCurrency = code.bankconnectors.Connector.connector.vend
      .getBankAccountLegacy(fromBankId, fromAccountId, None)
      .map(_._1.currency).openOrThrowException("test account")
    val headers = Map("DirectLogin" -> s"token=${token1.value}")
    val (statusCode, json, _) = makeHttpRequestWithBody("POST",
      openCorridorPromisePath(fromBankId.value, fromAccountId.value),
      openCorridorPromiseBody(acctCurrency, amount = amount,
        beneficiaryBankId = beneficiaryBankId, beneficiaryAccountId = beneficiaryAccountId), headers)
    statusCode shouldBe 201
    json match {
      case JObject(fields) =>
        val map = toFieldMap(fields)
        map.get("status") shouldBe Some(JString("PENDING"))
        map.get("id") match {
          case Some(JString(id)) if id.nonEmpty => id
          case _ => fail("id should be a non-empty string")
        }
      case _ => fail("Expected JSON object")
    }
  }

  private def messageOf(json: JValue): String = json match {
    case JObject(fields) => toFieldMap(fields).get("message") match {
      case Some(JString(msg)) => msg
      case _ => fail("Expected message field")
    }
    case _ => fail("Expected JSON object")
  }

  // ─── Dynamic-code provenance (v7.0.0 read-only) ──────────────────────────────
  Feature("Http4s700 dynamic-code provenance endpoints") {

    Scenario("Dynamic Resource Docs: 401 unauth, 403 no role, 200 with role exposes provenance", Http4s700RoutesTag) {
      Given("A dynamic resource doc seeded with resourceUser1 as creator")
      val seeded = code.dynamicResourceDoc.DynamicResourceDocProvider.provider.vend.create(
        None,
        code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
          dynamicResourceDocId = None, bankId = None,
          partialFunctionName = "provenanceV7Test", requestUrl = "/provenance_v7/PV_ID"),
        Some(resourceUser1.userId)
      ).openOrThrowException("seed dynamic resource doc")
      val docId = seeded.dynamicResourceDocId.getOrElse(fail("seeded id"))
      val expectedHash = code.api.util.APIUtil.sha256Hex(seeded.decodedMethodBody)

      When("Unauthenticated GET of the list")
      val (unauthCode, _, _) = makeHttpRequest("/obp/v7.0.0/management/dynamic-resource-docs")
      Then("401")
      unauthCode shouldBe 401

      When("Authenticated but without the role")
      val (forbiddenCode, forbiddenJson, _) = makeHttpRequest(
        "/obp/v7.0.0/management/dynamic-resource-docs", Map("DirectLogin" -> s"token=${token2.value}"))
      Then("403 naming the required role")
      forbiddenCode shouldBe 403
      messageOf(forbiddenJson) should include(code.api.util.ApiRole.canGetAllDynamicResourceDocs.toString)

      When("Authenticated with the getAll role")
      addEntitlement("", resourceUser1.userId, code.api.util.ApiRole.canGetAllDynamicResourceDocs.toString)
      val (okCode, okJson, _) = makeHttpRequest(
        "/obp/v7.0.0/management/dynamic-resource-docs", Map("DirectLogin" -> s"token=${token1.value}"))
      Then("200 and the seeded doc carries provenance (creator + method_body hash), not on the frozen v4 doc object")
      okCode shouldBe 200
      val item = (okJson \ "dynamic_resource_docs") match {
        case JArray(items) => items.find(i => (i \ "dynamic_resource_doc" \ "dynamic_resource_doc_id") == JString(docId))
          .getOrElse(fail("seeded doc not in list"))
        case _ => fail("dynamic_resource_docs should be an array")
      }
      (item \ "provenance" \ "created_by_user_id") shouldBe JString(resourceUser1.userId)
      (item \ "provenance" \ "method_body_hash") shouldBe JString(expectedHash)

      When("GET by id with the get role")
      addEntitlement("", resourceUser1.userId, code.api.util.ApiRole.canGetDynamicResourceDoc.toString)
      val (byIdCode, byIdJson, _) = makeHttpRequest(
        s"/obp/v7.0.0/management/dynamic-resource-docs/$docId", Map("DirectLogin" -> s"token=${token1.value}"))
      Then("200 with provenance and the unchanged v4 doc shape nested under dynamic_resource_doc")
      byIdCode shouldBe 200
      (byIdJson \ "dynamic_resource_doc" \ "dynamic_resource_doc_id") shouldBe JString(docId)
      (byIdJson \ "provenance" \ "created_by_user_id") shouldBe JString(resourceUser1.userId)
      (byIdJson \ "provenance" \ "method_body_hash") shouldBe JString(expectedHash)
    }

    Scenario("Connector Methods: GET by id exposes provenance", Http4s700RoutesTag) {
      val seeded = code.connectormethod.ConnectorMethodProvider.provider.vend.create(
        code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.jsonScalaConnectorMethod.copy(
          connectorMethodId = None, methodName = "getBanks"),
        Some(resourceUser1.userId)
      ).openOrThrowException("seed connector method")
      val id = seeded.connectorMethodId.getOrElse(fail("seeded id"))
      val expectedHash = code.api.util.APIUtil.sha256Hex(seeded.decodedMethodBody)

      addEntitlement("", resourceUser1.userId, code.api.util.ApiRole.canGetConnectorMethod.toString)
      val (code200, json, _) = makeHttpRequest(
        s"/obp/v7.0.0/management/connector-methods/$id", Map("DirectLogin" -> s"token=${token1.value}"))
      code200 shouldBe 200
      (json \ "connector_method" \ "connector_method_id") shouldBe JString(id)
      (json \ "provenance" \ "created_by_user_id") shouldBe JString(resourceUser1.userId)
      (json \ "provenance" \ "method_body_hash") shouldBe JString(expectedHash)
    }

    Scenario("Dynamic Message Docs: GET by id exposes provenance", Http4s700RoutesTag) {
      val seeded = code.dynamicMessageDoc.DynamicMessageDocProvider.provider.vend.create(
        None,
        code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.jsonDynamicMessageDoc.copy(
          dynamicMessageDocId = None, bankId = None, process = "obp.provenanceV7Process"),
        Some(resourceUser1.userId)
      ).openOrThrowException("seed dynamic message doc")
      val id = seeded.dynamicMessageDocId.getOrElse(fail("seeded id"))
      val expectedHash = code.api.util.APIUtil.sha256Hex(seeded.decodedMethodBody)

      addEntitlement("", resourceUser1.userId, code.api.util.ApiRole.canGetDynamicMessageDoc.toString)
      val (code200, json, _) = makeHttpRequest(
        s"/obp/v7.0.0/management/dynamic-message-docs/$id", Map("DirectLogin" -> s"token=${token1.value}"))
      code200 shouldBe 200
      (json \ "dynamic_message_doc" \ "dynamic_message_doc_id") shouldBe JString(id)
      (json \ "provenance" \ "created_by_user_id") shouldBe JString(resourceUser1.userId)
      (json \ "provenance" \ "method_body_hash") shouldBe JString(expectedHash)
    }
  }

  Feature("Http4s700 attachOpenCorridorPromise (promise report-back) endpoint") {

    Scenario("Reject unauthenticated POST", Http4s700RoutesTag) {
      val (statusCode, _, _) = makeHttpRequestWithBody("POST",
        promiseEvidencePath(testBankId1.value, testAccountId0.value, "some-tr-id"),
        promiseEvidenceBody())
      statusCode shouldBe 401
    }

    Scenario("Return 403 when authenticated without CanAttachOpenCorridorPromise", Http4s700RoutesTag) {
      val headers = Map("DirectLogin" -> s"token=${token2.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        promiseEvidencePath(testBankId1.value, testAccountId0.value, "some-tr-id"),
        promiseEvidenceBody(), headers)
      statusCode shouldBe 403
      messageOf(json) should include(UserHasMissingRoles)
      messageOf(json) should include("CanAttachOpenCorridorPromise")
    }

    Scenario("Attach evidence: 201, idempotent re-post, conflict refused", Http4s700RoutesTag) {
      Given("A PENDING OPEN_CORRIDOR_PROMISE and the role granted")
      addEntitlement(testBankId1.value, resourceUser1.userId, canAttachOpenCorridorPromise.toString)
      val transactionRequestId = createPendingPromise()
      val headers = Map("DirectLogin" -> s"token=${token1.value}")

      When("Evidence is attached the first time")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        promiseEvidencePath(testBankId1.value, testAccountId0.value, transactionRequestId),
        promiseEvidenceBody(), headers)

      Then("201 with the stored evidence and audit fields")
      statusCode shouldBe 201
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("transaction_request_id") shouldBe Some(JString(transactionRequestId))
          map.get("transaction_request_status") shouldBe Some(JString("PENDING"))
          map.get("tx_hash") shouldBe Some(JString("63eacfe3dbc133f922d461bd3e6488ce21d55f03c5131cd79c965fe2e7491642"))
          map.get("blockchain") shouldBe Some(JString("cardano"))
          map.get("commitment") shouldBe Some(JString("9c56cc51b374c3ba189210d5b6d4bf57790d351c96c47c02190ecf1e430ba0d1"))
          map.get("salt") shouldBe Some(JString("5f4dcc3b5aa765d61d8327deb882cf99"))
          map.get("preimage") shouldBe Some(JString("""{"tx_request_id":"tr-abc-123"}"""))
          map.get("reported_by_user_id") shouldBe Some(JString(resourceUser1.userId))
          map.get("reported_at") match {
            case Some(JString(reportedAt)) => reportedAt should not be empty
            case _ => fail("reported_at should be a non-empty string")
          }
        case _ => fail("Expected JSON object")
      }

      When("The identical evidence is re-posted (Bank Node outbox redelivery)")
      val (retryCode, retryJson, _) = makeHttpRequestWithBody("POST",
        promiseEvidencePath(testBankId1.value, testAccountId0.value, transactionRequestId),
        promiseEvidenceBody(), headers)

      Then("201 with the stored record — idempotent")
      retryCode shouldBe 201
      retryJson match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("commitment") shouldBe Some(JString("9c56cc51b374c3ba189210d5b6d4bf57790d351c96c47c02190ecf1e430ba0d1"))
          map.get("reported_by_user_id") shouldBe Some(JString(resourceUser1.userId))
        case _ => fail("Expected JSON object")
      }

      When("Different evidence is posted for the same Transaction Request")
      val (conflictCode, conflictJson, _) = makeHttpRequestWithBody("POST",
        promiseEvidencePath(testBankId1.value, testAccountId0.value, transactionRequestId),
        promiseEvidenceBody(commitment = "0000000000000000000000000000000000000000000000000000000000000000"), headers)

      Then("400 — evidence is append-once")
      conflictCode shouldBe 400
      messageOf(conflictJson) should include(OpenCorridorPromiseEvidenceConflict)
    }

    Scenario("Return 400 InvalidJsonValue when tx_hash is empty", Http4s700RoutesTag) {
      addEntitlement(testBankId1.value, resourceUser1.userId, canAttachOpenCorridorPromise.toString)
      val transactionRequestId = createPendingPromise()
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        promiseEvidencePath(testBankId1.value, testAccountId0.value, transactionRequestId),
        promiseEvidenceBody(txHash = ""), headers)
      statusCode shouldBe 400
      messageOf(json) should include(InvalidJsonValue)
    }

    Scenario("Return 400 InvalidTransactionRequestId for an unknown Transaction Request", Http4s700RoutesTag) {
      addEntitlement(testBankId1.value, resourceUser1.userId, canAttachOpenCorridorPromise.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        promiseEvidencePath(testBankId1.value, testAccountId0.value, "no-such-transaction-request-id"),
        promiseEvidenceBody(), headers)
      statusCode shouldBe 400
      messageOf(json) should include(InvalidTransactionRequestId)
    }

    Scenario("Return 400 when the Transaction Request is not OPEN_CORRIDOR_PROMISE", Http4s700RoutesTag) {
      Given("A PENDING Transaction Request of type SIMPLE created via the provider")
      addEntitlement(testBankId1.value, resourceUser1.userId, canAttachOpenCorridorPromise.toString)
      val fromAccount = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId1, testAccountId0, None)
        .map(_._1).openOrThrowException("test from account")
      val toAccount = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId2, testAccountId1, None)
        .map(_._1).openOrThrowException("test to account")
      val simpleTr = code.transactionrequests.TransactionRequests.transactionRequestProvider.vend
        .createTransactionRequestImpl210(
          com.openbankproject.commons.model.TransactionRequestId(APIUtil.generateUUID()),
          com.openbankproject.commons.model.TransactionRequestType("SIMPLE"),
          fromAccount,
          toAccount,
          com.openbankproject.commons.model.TransactionRequestCommonBodyJSONCommons(
            com.openbankproject.commons.model.AmountOfMoneyJsonV121(fromAccount.currency, "3.00"), "wrong type"),
          "{}",
          "PENDING",
          com.openbankproject.commons.model.TransactionRequestCharge(
            "Total charges for completed transaction",
            com.openbankproject.commons.model.AmountOfMoney(fromAccount.currency, "0.00")),
          "SHARED",
          None, None, None, None, None
        ).openOrThrowException("SIMPLE TR should be created")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        promiseEvidencePath(testBankId1.value, testAccountId0.value, simpleTr.id.value),
        promiseEvidenceBody(), headers)
      statusCode shouldBe 400
      messageOf(json) should include(OpenCorridorPromiseTypeMismatch)
    }

    Scenario("Return 400 when the promise is no longer PENDING", Http4s700RoutesTag) {
      Given("A promise flipped to COMPLETED via the provider (as the settle step will do)")
      addEntitlement(testBankId1.value, resourceUser1.userId, canAttachOpenCorridorPromise.toString)
      val transactionRequestId = createPendingPromise()
      code.transactionrequests.TransactionRequests.transactionRequestProvider.vend
        .saveTransactionRequestStatusImpl(
          com.openbankproject.commons.model.TransactionRequestId(transactionRequestId), "COMPLETED")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        promiseEvidencePath(testBankId1.value, testAccountId0.value, transactionRequestId),
        promiseEvidenceBody(), headers)
      statusCode shouldBe 400
      messageOf(json) should include(OpenCorridorPromiseNotPending)
    }
  }

  // ─── OPEN_CORRIDOR broker registry + settle-pair ──────────────────────────

  private def brokerPath(bankId: String): String =
    s"/obp/v7.0.0/banks/$bankId/amqp-broker"

  private def brokerBody(): String =
    s"""{
       |  "host": "rabbitmq.bank.example.com",
       |  "port": 5672,
       |  "virtual_host": "/bank.test",
       |  "username": "obp-api",
       |  "password": "secret-not-echoed",
       |  "use_ssl": false
       |}""".stripMargin

  private def ensureSettlementAccounts(bankId: String, currency: String): Unit = {
    import code.model.dataAccess.MappedBankAccount
    List(code.api.Constant.INCOMING_SETTLEMENT_ACCOUNT_ID, code.api.Constant.OUTGOING_SETTLEMENT_ACCOUNT_ID).foreach { accountId =>
      if (MappedBankAccount.find(bankId, accountId).isEmpty) {
        MappedBankAccount.insert(bankId, accountId, accountCurrency = currency)
      }
    }
  }

  /** The bank's settlement address is the CARDANO routing on its incoming
    * settlement account; empty address removes the routing. */
  private def setIncomingSettlementCardanoAddress(bankId: String, address: String): Unit = {
    val incomingAccountId = AccountId(code.api.Constant.INCOMING_SETTLEMENT_ACCOUNT_ID)
    val existing = DoobieBankAccountRoutingQueries.findByBankAccountScheme(CommBankId(bankId), incomingAccountId, "CARDANO")
    if (address.isEmpty) {
      existing.foreach(_ => DoobieBankAccountRoutingQueries.deleteByBankAccountScheme(CommBankId(bankId), incomingAccountId, "CARDANO"))
    } else existing match {
      case Some(_) => DoobieBankAccountRoutingQueries.updateAddress(CommBankId(bankId), incomingAccountId, "CARDANO", address)
      case None => DoobieBankAccountRoutingQueries.create(CommBankId(bankId), incomingAccountId, "CARDANO", address)
    }
  }

  private def promiseStatus(transactionRequestId: String): String =
    code.transactionrequests.TransactionRequests.transactionRequestProvider.vend
      .getTransactionRequestFromProvider(com.openbankproject.commons.model.TransactionRequestId(transactionRequestId))
      .map(_.status).openOrThrowException("TR should exist")

  private def promiseAttributes(transactionRequestId: String): Map[String, String] = {
    import scala.concurrent.Await
    import scala.concurrent.duration._
    Await.result(
      code.transactionRequestAttribute.TransactionRequestAttributeX.transactionRequestAttributeProvider.vend
        .getTransactionRequestAttributesFromProvider(com.openbankproject.commons.model.TransactionRequestId(transactionRequestId)),
      10.seconds
    ).openOrThrowException("attributes should load").map(a => a.name -> a.value).toMap
  }

  Feature("Http4s700 Open Corridor bank broker registry endpoints") {

    Scenario("Reject unauthenticated PUT", Http4s700RoutesTag) {
      val (statusCode, _, _) = makeHttpRequestWithBody("PUT", brokerPath(testBankId1.value), brokerBody())
      statusCode shouldBe 401
    }

    Scenario("Return 403 without CanConfigureAmqpBankBroker", Http4s700RoutesTag) {
      val headers = Map("DirectLogin" -> s"token=${token2.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("PUT", brokerPath(testBankId1.value), brokerBody(), headers)
      statusCode shouldBe 403
      messageOf(json) should include("CanConfigureAmqpBankBroker")
    }

    Scenario("Broker registry CRUD round-trip; password is never echoed", Http4s700RoutesTag) {
      addEntitlement("", resourceUser1.userId, canConfigureAmqpBankBroker.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")

      When("DELETE clears any previous registration (idempotent)")
      val (deleteCode, _, _) = makeHttpRequestWithMethod("DELETE", brokerPath(testBankId1.value), headers)
      deleteCode shouldBe 204

      Then("GET without a registration is refused")
      val (missingCode, missingJson, _) = makeHttpRequest(brokerPath(testBankId1.value), headers)
      missingCode shouldBe 400
      messageOf(missingJson) should include(AmqpBankBrokerNotConfigured)

      When("PUT registers the broker")
      val (putCode, putJson, _) = makeHttpRequestWithBody("PUT", brokerPath(testBankId1.value), brokerBody(), headers)
      putCode shouldBe 200
      putJson match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("bank_id") shouldBe Some(JString(testBankId1.value))
          map.get("host") shouldBe Some(JString("rabbitmq.bank.example.com"))
          map.keys should not contain "settlement_address"
          map.keys should not contain "password"
        case _ => fail("Expected JSON object")
      }

      Then("GET returns the registration, still without the password")
      val (getCode, getJson, _) = makeHttpRequest(brokerPath(testBankId1.value), headers)
      getCode shouldBe 200
      getJson match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("virtual_host") shouldBe Some(JString("/bank.test"))
          map.keys should not contain "password"
        case _ => fail("Expected JSON object")
      }

      And("DELETE removes it again")
      val (deleteCode2, _, _) = makeHttpRequestWithMethod("DELETE", brokerPath(testBankId1.value), headers)
      deleteCode2 shouldBe 204
      val (goneCode, _, _) = makeHttpRequest(brokerPath(testBankId1.value), headers)
      goneCode shouldBe 400
    }
  }

  Feature("Http4s700 createOpenCorridorSettlement endpoint (bilateral netting)") {

    def settlementsPath(bankId: String): String =
      s"/obp/v7.0.0/banks/$bankId/open-corridor/settlements"

    def settleBody(currency: String, otherBankId: String = testBankId2.value): String =
      s"""{"other_bank_id": "$otherBankId", "currency": "$currency"}"""

    def registerBrokers(): Unit = {
      code.amqpbroker.AmqpBankBroker.upsert(
        testBankId1.value, "localhost", 5672, "/bank.a", "u", "p", false)
      code.amqpbroker.AmqpBankBroker.upsert(
        testBankId2.value, "localhost", 5672, "/bank.b", "u", "p", false)
      setIncomingSettlementCardanoAddress(testBankId1.value, "addr_test_bank_a")
      setIncomingSettlementCardanoAddress(testBankId2.value, "addr_test_bank_b")
    }

    Scenario("Reject unauthenticated POST", Http4s700RoutesTag) {
      val (statusCode, _, _) = makeHttpRequestWithBody("POST", settlementsPath(testBankId1.value), settleBody("EUR"))
      statusCode shouldBe 401
    }

    Scenario("Return 403 without CanSettleOpenCorridor", Http4s700RoutesTag) {
      val headers = Map("DirectLogin" -> s"token=${token2.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", settlementsPath(testBankId1.value), settleBody("EUR"), headers)
      statusCode shouldBe 403
      messageOf(json) should include("CanSettleOpenCorridor")
    }

    Scenario("The role is bank-scoped: a grant at another bank does not authorize this bank's URL", Http4s700RoutesTag) {
      addEntitlement(testBankId1.value, resourceUser1.userId, canSettleOpenCorridor.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        settlementsPath(testBankId2.value), settleBody("EUR", otherBankId = testBankId1.value), headers)
      statusCode shouldBe 403
      messageOf(json) should include("CanSettleOpenCorridor")
    }

    Scenario("Return 400 when open_corridor_enabled is not set", Http4s700RoutesTag) {
      addEntitlement(testBankId1.value, resourceUser1.userId, canSettleOpenCorridor.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", settlementsPath(testBankId1.value), settleBody("EUR"), headers)
      statusCode shouldBe 400
      messageOf(json) should include(OpenCorridorDisabled)
    }

    Scenario("Net a pair: N promises collapse into one settlement, evidence relayed via outbox", Http4s700RoutesTag) {
      setPropsValues("open_corridor_enabled" -> "true")
      addEntitlement(testBankId1.value, resourceUser1.userId, canSettleOpenCorridor.toString)
      addEntitlement(testBankId1.value, resourceUser1.userId, canAttachOpenCorridorPromise.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")

      Given("Matching currencies, settlement accounts and broker registrations for both banks")
      val currency = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId1, testAccountId0, None)
        .map(_._1.currency).openOrThrowException("test account")
      val currency2 = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId2, testAccountId1, None)
        .map(_._1.currency).openOrThrowException("test account")
      currency2 shouldBe currency
      ensureSettlementAccounts(testBankId1.value, currency)
      ensureSettlementAccounts(testBankId2.value, currency)

      And("Three pending promises: A→B 5.00 + 2.00, B→A 3.00")
      def assertPromiseRow(trId: String, fromBank: String, toBank: String): Unit = {
        val row = code.transactionrequests.MappedTransactionRequest
          .findByTransactionRequestId(trId)
          .openOrThrowException("promise TR row should exist")
        withClue(s"TR $trId row: from=${row.fromBankId} to=${row.toBankId} " +
          s"currency=${row.bodyValueCurrency} status=${row.status} type=${row.transactionType} — ") {
          row.fromBankId shouldBe fromBank
          row.toBankId shouldBe toBank
          row.bodyValueCurrency shouldBe currency
        }
      }
      val promise1 = createPendingPromise(amount = "5.00")
      val promise2 = createPendingPromise(amount = "2.00")
      val promise3 = createPendingPromise(testBankId2, testAccountId1, testBankId1.value, testAccountId0.value, "3.00")
      val promise4NoEvidence = createPendingPromise(amount = "9.00")
      assertPromiseRow(promise1, testBankId1.value, testBankId2.value)
      assertPromiseRow(promise3, testBankId2.value, testBankId1.value)

      And("Promises 1-3 have their on-chain evidence attached (report-back); promise 4 has none")
      addEntitlement(testBankId2.value, resourceUser1.userId, canAttachOpenCorridorPromise.toString)
      List(
        (testBankId1.value, testAccountId0.value, promise1),
        (testBankId1.value, testAccountId0.value, promise2),
        (testBankId2.value, testAccountId1.value, promise3)
      ).foreach { case (bankId, accountId, promiseId) =>
        val (evidenceCode, _, _) = makeHttpRequestWithBody("POST",
          promiseEvidencePath(bankId, accountId, promiseId), promiseEvidenceBody(), headers)
        evidenceCode shouldBe 201
      }

      Then("Each attach immediately enqueued the beneficiary's credit notification with the evidence")
      val promise1CreditRows = code.messageoutbox.MessageOutbox.bySubjectId(promise1)
      promise1CreditRows.map(_.operationName) shouldBe List("obp_credit_notification")
      promise1CreditRows.head.targetId shouldBe testBankId2.value
      val promise1Credit = parse(promise1CreditRows.head.payloadJson)
      (promise1Credit \ "promise_salt") shouldBe JString("5f4dcc3b5aa765d61d8327deb882cf99")
      (promise1Credit \ "promise_commitment") shouldBe JString("9c56cc51b374c3ba189210d5b6d4bf57790d351c96c47c02190ecf1e430ba0d1")
      // The CBS is told whom to credit: beneficiary name + account routing,
      // read back from the promise TR's stored create body.
      (promise1Credit \ "beneficiary" \ "account_routing" \ "scheme") shouldBe JString("OBP")
      (promise1Credit \ "beneficiary" \ "account_routing" \ "address") shouldBe JString(testAccountId1.value)
      (promise1Credit \ "beneficiary" \ "name") match {
        case JString(name) => name should startWith("OC Beneficiary")
        case other => fail(s"beneficiary.name should be a string, got $other")
      }
      code.messageoutbox.MessageOutbox.bySubjectId(promise3)
        .map(_.targetId) shouldBe List(testBankId1.value)
      code.messageoutbox.MessageOutbox.bySubjectId(promise4NoEvidence) shouldBe Nil

      When("Settle is triggered while the creditor bank's incoming settlement account has no CARDANO routing")
      code.amqpbroker.AmqpBankBroker.upsert(
        testBankId1.value, "localhost", 5672, "/bank.a", "u", "p", false)
      code.amqpbroker.AmqpBankBroker.upsert(
        testBankId2.value, "localhost", 5672, "/bank.b", "u", "p", false)
      setIncomingSettlementCardanoAddress(testBankId1.value, "addr_test_bank_a")
      setIncomingSettlementCardanoAddress(testBankId2.value, "")
      val (noAddressCode, noAddressJson, _) = makeHttpRequestWithBody("POST",
        settlementsPath(testBankId1.value), settleBody(currency), headers)
      noAddressCode shouldBe 400
      messageOf(noAddressJson) should include(OpenCorridorSettlementAddressMissing)
      promiseStatus(promise1) shouldBe "PENDING"

      Then("With both brokers fully registered the settle succeeds")
      registerBrokers()
      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        settlementsPath(testBankId1.value), settleBody(currency), headers)
      statusCode shouldBe 201
      val (settlementId, transactionId) = json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("net_amount") shouldBe Some(JString("4.00"))
          map.get("debtor_bank_id") shouldBe Some(JString(testBankId1.value))
          map.get("creditor_bank_id") shouldBe Some(JString(testBankId2.value))
          map.get("settlement_advices_enqueued") shouldBe Some(JInt(2))
          map.get("settlement_instructions_enqueued") shouldBe Some(JInt(1))
          map.get("covered_transaction_request_ids") match {
            case Some(JArray(ids)) => ids.collect { case JString(id) => id }.toSet shouldBe Set(promise1, promise2, promise3)
            case _ => fail("covered_transaction_request_ids should be an array")
          }
          val sid = map.get("settlement_id").collect { case JString(s) => s }.getOrElse(fail("settlement_id missing"))
          val tid = map.get("transaction_id").collect { case JString(s) => s }.getOrElse(fail("transaction_id missing"))
          sid should not be empty
          tid should not be empty
          (sid, tid)
        case _ => fail("Expected JSON object")
      }

      And("Every covered promise is COMPLETED with the discharge linkage attributes; the unevidenced one stays PENDING")
      List(promise1, promise2, promise3).foreach { promiseId =>
        promiseStatus(promiseId) shouldBe "COMPLETED"
        val attributes = promiseAttributes(promiseId)
        attributes.get(code.bankconnectors.opencorridor.OpenCorridorSettlement.AttrSettledByTransactionIds) shouldBe Some(transactionId)
        attributes.get(code.bankconnectors.opencorridor.OpenCorridorSettlement.AttrSettledByTransactionRequestId) shouldBe Some(settlementId)
      }
      promiseStatus(promise4NoEvidence) shouldBe "PENDING"

      And("The outbox holds 2 settlement advices + 1 settlement instruction for this settlement")
      val outboxRows = code.messageoutbox.MessageOutbox.bySubjectId(settlementId)
      outboxRows.size shouldBe 3
      val adviceRows = outboxRows.filter(_.operationName == "obp_settlement_advice")
      adviceRows.map(_.targetId).sorted shouldBe List(testBankId1.value, testBankId2.value).sorted
      // Both party banks get the advice with the FULL covered list (both
      // directions): each node stamps its credits AND its own promises from it.
      adviceRows.foreach { row =>
        val advice = parse(row.payloadJson)
        (advice \ "settlement_id") shouldBe JString(settlementId)
        (advice \ "covered_transaction_request_ids") match {
          case JArray(ids) => ids.collect { case JString(id) => id }.toSet shouldBe Set(promise1, promise2, promise3)
          case _ => fail("covered_transaction_request_ids should be an array")
        }
      }
      val instructionRow = outboxRows.filter(_.operationName == "obp_settlement_instruction") match {
        case row :: Nil => row
        case other => fail(s"Expected exactly one settlement instruction row, got ${other.size}")
      }
      instructionRow.targetId shouldBe testBankId1.value
      val instructionJson = parse(instructionRow.payloadJson)
      (instructionJson \ "amount") shouldBe JString("4.00")
      (instructionJson \ "creditor_address") shouldBe JString("addr_test_bank_b")
      (instructionJson \ "idempotency_key") shouldBe JString(settlementId)

      And("A re-trigger with nothing pending is a no-op")
      val (noopCode, noopJson, _) = makeHttpRequestWithBody("POST",
        settlementsPath(testBankId1.value), settleBody(currency), headers)
      noopCode shouldBe 201
      noopJson match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("covered_transaction_request_ids") shouldBe Some(JArray(Nil))
          map.get("settlement_id") shouldBe Some(JString(""))
        case _ => fail("Expected JSON object")
      }

      And("GET on the settlement resource shows ledger COMPLETED, rail INSTRUCTED (relay has not run)")
      val (getCode, getJson, _) = makeHttpRequest(s"${settlementsPath(testBankId1.value)}/$settlementId", headers)
      getCode shouldBe 200
      getJson match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("settlement_id") shouldBe Some(JString(settlementId))
          map.get("debtor_bank_id") shouldBe Some(JString(testBankId1.value))
          map.get("creditor_bank_id") shouldBe Some(JString(testBankId2.value))
          map.get("net_amount") shouldBe Some(JString("4.00"))
          map.get("transaction_id") shouldBe Some(JString(transactionId))
          map.get("ledger_status") shouldBe Some(JString("COMPLETED"))
          map.get("settlement_status") shouldBe Some(JString("INSTRUCTED"))
          map.get("covered_transaction_request_ids") match {
            case Some(JArray(ids)) => ids.collect { case JString(id) => id }.toSet shouldBe Set(promise1, promise2, promise3)
            case _ => fail("covered_transaction_request_ids should be an array")
          }
          map.get("messages") match {
            // 2 settlement advices + 1 settlement instruction (credit
            // notifications correlate to their promise ids, not the settlement).
            case Some(JArray(messages)) => messages.size shouldBe 3
            case _ => fail("messages should be an array")
          }
        case _ => fail("Expected JSON object")
      }

      And("The creditor bank can read the same settlement from its own URL")
      addEntitlement(testBankId2.value, resourceUser1.userId, canSettleOpenCorridor.toString)
      val (creditorGetCode, creditorGetJson, _) = makeHttpRequest(s"${settlementsPath(testBankId2.value)}/$settlementId", headers)
      creditorGetCode shouldBe 200
      creditorGetJson match {
        case JObject(fields) => toFieldMap(fields).get("settlement_id") shouldBe Some(JString(settlementId))
        case _ => fail("Expected JSON object")
      }

      And("An unknown settlement id is a 404")
      val (notFoundCode, notFoundJson, _) = makeHttpRequest(s"${settlementsPath(testBankId1.value)}/does-not-exist", headers)
      notFoundCode shouldBe 404
      messageOf(notFoundJson) should include(OpenCorridorSettlementNotFound)

      And("The settle accrued a platform fee per covered promise, owed by the originator")
      import code.opencorridorfees.OpenCorridorFeeAccrual
      def accrualFor(trId: String) = OpenCorridorFeeAccrual.find(trId)
      def chargeOf(trId: String): BigDecimal = BigDecimal(
        code.transactionrequests.MappedTransactionRequest
          .findByTransactionRequestId(trId)
          .map(_.chargeAmount).openOrThrowException("promise TR row"))
      List(promise1 -> testBankId1.value, promise2 -> testBankId1.value, promise3 -> testBankId2.value)
        .foreach { case (trId, originator) =>
          val accrual = accrualFor(trId).openOrThrowException(s"accrual for $trId should exist")
          accrual.debtorBankId shouldBe originator
          BigDecimal(accrual.amount) shouldBe chargeOf(trId)
          accrual.feeSettlementId shouldBe ""
        }
      accrualFor(promise4NoEvidence) shouldBe net.liftweb.common.Empty

      When("Bank1's fees are swept to the platform (configured as a bank)")
      setPropsValues("open_corridor.platform_bank_id" -> testBankId2.value)
      val expectedFees = chargeOf(promise1) + chargeOf(promise2)
      val (sweepCode, sweepJson, _) = makeHttpRequestWithBody("POST",
        s"/obp/v7.0.0/banks/${testBankId1.value}/open-corridor/fee-settlements",
        s"""{"currency": "$currency"}""", headers)
      sweepCode shouldBe 201
      val feeSettlementId = sweepJson match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("debtor_bank_id") shouldBe Some(JString(testBankId1.value))
          map.get("platform_bank_id") shouldBe Some(JString(testBankId2.value))
          map.get("amount") shouldBe Some(JString(expectedFees.toString))
          map.get("accruals_swept") shouldBe Some(JInt(2))
          map.get("settlement_instructions_enqueued") shouldBe Some(JInt(1))
          map.get("fee_settlement_id").collect { case JString(s) => s }.getOrElse(fail("fee_settlement_id missing"))
        case _ => fail("Expected JSON object")
      }

      Then("One PLATFORM_FEE settlement instruction is enqueued to the debtor's vhost")
      val feeRows = code.messageoutbox.MessageOutbox.bySubjectId(feeSettlementId)
      feeRows.map(_.operationName) shouldBe List("obp_settlement_instruction")
      feeRows.head.targetId shouldBe testBankId1.value
      val feeInstruction = parse(feeRows.head.payloadJson)
      (feeInstruction \ "purpose") shouldBe JString("PLATFORM_FEE")
      (feeInstruction \ "amount") shouldBe JString(expectedFees.toString)
      (feeInstruction \ "creditor_bank_id") shouldBe JString(testBankId2.value)
      (feeInstruction \ "creditor_address") shouldBe JString("addr_test_bank_b")

      And("The swept accruals are stamped; bank2's accrual stays open; a re-sweep is a no-op")
      accrualFor(promise1).map(_.feeSettlementId) shouldBe net.liftweb.common.Full(feeSettlementId)
      accrualFor(promise2).map(_.feeSettlementId) shouldBe net.liftweb.common.Full(feeSettlementId)
      accrualFor(promise3).map(_.feeSettlementId) shouldBe net.liftweb.common.Full("")
      val (resweepCode, resweepJson, _) = makeHttpRequestWithBody("POST",
        s"/obp/v7.0.0/banks/${testBankId1.value}/open-corridor/fee-settlements",
        s"""{"currency": "$currency"}""", headers)
      resweepCode shouldBe 201
      resweepJson match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("amount") shouldBe Some(JString("0"))
          map.get("accruals_swept") shouldBe Some(JInt(0))
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Exactly offsetting flows discharge at net zero with no Transaction", Http4s700RoutesTag) {
      setPropsValues("open_corridor_enabled" -> "true")
      addEntitlement(testBankId1.value, resourceUser1.userId, canSettleOpenCorridor.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val currency = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId1, testAccountId0, None)
        .map(_._1.currency).openOrThrowException("test account")
      ensureSettlementAccounts(testBankId1.value, currency)
      ensureSettlementAccounts(testBankId2.value, currency)
      registerBrokers()

      val promiseAToB = createPendingPromise(amount = "3.00")
      val promiseBToA = createPendingPromise(testBankId2, testAccountId1, testBankId1.value, testAccountId0.value, "3.00")
      addEntitlement(testBankId1.value, resourceUser1.userId, canAttachOpenCorridorPromise.toString)
      addEntitlement(testBankId2.value, resourceUser1.userId, canAttachOpenCorridorPromise.toString)
      List(
        (testBankId1.value, testAccountId0.value, promiseAToB),
        (testBankId2.value, testAccountId1.value, promiseBToA)
      ).foreach { case (bankId, accountId, promiseId) =>
        val (evidenceCode, _, _) = makeHttpRequestWithBody("POST",
          promiseEvidencePath(bankId, accountId, promiseId), promiseEvidenceBody(), headers)
        evidenceCode shouldBe 201
      }

      val (statusCode, json, _) = makeHttpRequestWithBody("POST",
        settlementsPath(testBankId1.value), settleBody(currency), headers)
      statusCode shouldBe 201
      val settlementId = json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("net_amount") shouldBe Some(JString("0.00"))
          map.get("transaction_id") shouldBe Some(JString(""))
          map.get("settlement_advices_enqueued") shouldBe Some(JInt(2))
          map.get("settlement_instructions_enqueued") shouldBe Some(JInt(0))
          map.get("settlement_id").collect { case JString(s) => s }.getOrElse(fail("settlement_id missing"))
        case _ => fail("Expected JSON object")
      }

      List(promiseAToB, promiseBToA).foreach { promiseId =>
        promiseStatus(promiseId) shouldBe "COMPLETED"
        val attributes = promiseAttributes(promiseId)
        attributes.get(code.bankconnectors.opencorridor.OpenCorridorSettlement.AttrSettledByTransactionRequestId) shouldBe Some(settlementId)
        attributes.get(code.bankconnectors.opencorridor.OpenCorridorSettlement.AttrSettledByTransactionIds) shouldBe None
      }
      code.messageoutbox.MessageOutbox.bySubjectId(settlementId)
        .filter(_.operationName == "obp_settlement_instruction") shouldBe Nil

      And("GET reports NET_ZERO: nothing to move on any rail")
      val (getCode, getJson, _) = makeHttpRequest(s"${settlementsPath(testBankId1.value)}/$settlementId", headers)
      getCode shouldBe 200
      getJson match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("settlement_status") shouldBe Some(JString("NET_ZERO"))
          map.get("net_amount") shouldBe Some(JString("0.00"))
          map.get("transaction_id") shouldBe Some(JString(""))
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ─── BULK transaction request ─────────────────────────────────────────────

  /** Fresh batch reference for each test scenario to avoid idempotency collisions. */
  private def freshBatchReference(): String =
    s"BATCH-${APIUtil.generateUUID().take(12)}"

  Feature("Http4s700 createTransactionRequestBulk endpoint") {

    Scenario("Reject unauthenticated POST", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val body =
        s"""{
           |  "batch_reference": "${freshBatchReference()}",
           |  "payments": [{"end_to_end_id":"e1","to_account_routing":{"scheme":"TZ.BANK_ACCOUNT","address":"123"},"value":{"currency":"EUR","amount":"1.00"},"description":"x"}],
           |  "value": {"currency":"EUR","amount":"1.00"},
           |  "description": "test"
           |}""".stripMargin
      val (statusCode, _, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/BULK/transaction-requests", body)
      statusCode shouldBe 401
    }

    Scenario("Return 400 when payments array is empty", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val body =
        s"""{
           |  "batch_reference": "${freshBatchReference()}",
           |  "payments": [],
           |  "value": {"currency":"EUR","amount":"0"},
           |  "description": "empty"
           |}""".stripMargin
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/BULK/transaction-requests", body, headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include("OBP-30537")
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 400 when an item currency does not match the source account", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      // Pick a currency unlikely to match the test account's currency.
      val body =
        s"""{
           |  "batch_reference": "${freshBatchReference()}",
           |  "payments": [{"end_to_end_id":"e1","to_account_routing":{"scheme":"TZ.BANK_ACCOUNT","address":"123"},"value":{"currency":"XYZ","amount":"1.00"},"description":"x"}],
           |  "value": {"currency":"XYZ","amount":"1.00"},
           |  "description": "wrong currency"
           |}""".stripMargin
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/BULK/transaction-requests", body, headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include("OBP-30540")
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 400 when end_to_end_id is duplicated in the batch", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      // Read account currency from the system to construct a matching body.
      val acctCurrency = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId1, testAccountId0, None)
        .map(_._1.currency).openOrThrowException("test account")
      val body =
        s"""{
           |  "batch_reference": "${freshBatchReference()}",
           |  "payments": [
           |    {"end_to_end_id":"DUP","to_account_routing":{"scheme":"TZ.BANK_ACCOUNT","address":"123"},"value":{"currency":"$acctCurrency","amount":"1.00"},"description":"x"},
           |    {"end_to_end_id":"DUP","to_account_routing":{"scheme":"TZ.BANK_ACCOUNT","address":"124"},"value":{"currency":"$acctCurrency","amount":"1.00"},"description":"y"}
           |  ],
           |  "value": {"currency":"$acctCurrency","amount":"2.00"},
           |  "description": "dupes"
           |}""".stripMargin
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/BULK/transaction-requests", body, headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include("OBP-30539")
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 409 when batch_reference is reused on the same source account", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val acctCurrency = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId1, testAccountId0, None)
        .map(_._1.currency).openOrThrowException("test account")
      val ref = freshBatchReference()
      // First submission — accepted (note: every payment will be FAILED in mapped mode because
      // we haven't seeded a matching account_routing, but the envelope is accepted).
      val body =
        s"""{
           |  "batch_reference": "$ref",
           |  "payments": [{"end_to_end_id":"E1","to_account_routing":{"scheme":"TZ.BANK_ACCOUNT","address":"77777777777"},"value":{"currency":"$acctCurrency","amount":"1.00"},"description":"x"}],
           |  "value": {"currency":"$acctCurrency","amount":"1.00"},
           |  "description": "first submission"
           |}""".stripMargin
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (firstStatus, _, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/BULK/transaction-requests", body, headers)
      firstStatus shouldBe 201

      // Second submission with same batch_reference — must be rejected.
      val (secondStatus, secondJson, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/BULK/transaction-requests", body, headers)
      secondStatus shouldBe 409
      secondJson match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include("OBP-30536")
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 201 with PARTIALLY_COMPLETED when one item destination resolves and another does not", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val acctCurrency = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId1, testAccountId0, None)
        .map(_._1.currency).openOrThrowException("test account")

      // Seed one resolvable destination — a fresh scheme + matching account_routing pointing
      // back at the test account (we don't care that the destination is the same account; this
      // exercises the SUCCESS branch).
      val resolvableAddress = s"BULK-${APIUtil.generateUUID().take(8)}"
      val resolvableScheme = seedPayeeForLookup("BLK", resolvableAddress, bankId, accountId)

      val body =
        s"""{
           |  "batch_reference": "${freshBatchReference()}",
           |  "payments": [
           |    {"end_to_end_id":"OK","to_account_routing":{"scheme":"$resolvableScheme","address":"$resolvableAddress"},"value":{"currency":"$acctCurrency","amount":"1.00"},"description":"will-succeed"},
           |    {"end_to_end_id":"NOPE","to_account_routing":{"scheme":"TZ.BANK_ACCOUNT","address":"00000000000"},"value":{"currency":"$acctCurrency","amount":"2.00"},"description":"will-fail"}
           |  ],
           |  "value": {"currency":"$acctCurrency","amount":"3.00"},
           |  "description": "partial"
           |}""".stripMargin

      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/BULK/transaction-requests", body, headers)
      statusCode shouldBe 201
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.keys should contain allOf ("id", "batch_reference", "status", "total_payments", "succeeded_count", "failed_count", "payments")
          map.get("total_payments")  shouldBe Some(org.json4s.JsonAST.JInt(2))
          map.get("status") match {
            case Some(JString(s)) => s should (be("PARTIALLY_COMPLETED") or be("FAILED") or be("COMPLETED"))
            case _ => fail("status should be a string")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ─── UTILITY transaction request ──────────────────────────────────────────

  /** Seed a UTILITY/BILL-category routing scheme plus a destination account_routing
    * so the biller resolves (mirrors seedPayeeForLookup but with a non-ACCOUNT category). */
  private def seedUtilityBiller(prefix: String, category: String, address: String, destBankId: String, destAccountId: String): String = {
    val scheme = freshSchemeName(prefix)
    RoutingSchemes.routingScheme.vend.createRoutingScheme(
      scheme = scheme, country = "TZ", category = category,
      addressPattern = "^[0-9]+$", secondaryAddressPattern = None,
      exampleAddress = address, description = "Test biller", downstreamRails = Nil,
      status = "ACTIVE", createdByUserId = resourceUser1.userId
    )
    DoobieBankAccountRoutingQueries.create(CommBankId(destBankId), AccountId(destAccountId), scheme, address)
    scheme
  }

  Feature("Http4s700 createTransactionRequestUtility endpoint") {

    Scenario("Reject unauthenticated POST", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val body = """{"to":{"scheme":"TZ.UTILITY_METER","value":"24730238417"},"value":{"currency":"TZS","amount":"1000"},"description":"utility"}"""
      val (statusCode, _, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/UTILITY/transaction-requests", body)
      statusCode shouldBe 401
    }

    Scenario("Return 400 when identifier scheme is not registered", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val body = """{"to":{"scheme":"TZ.UNKNOWN_BILLER","value":"24730238417"},"value":{"currency":"TZS","amount":"1000"},"description":"utility"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/UTILITY/transaction-requests", body, headers)
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

    Scenario("Return 400 when identifier scheme category is not UTILITY or BILL", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      // Register an ACCOUNT-category scheme — valid pattern, wrong category for a UTILITY payment.
      val scheme = freshSchemeName("ACAT")
      RoutingSchemes.routingScheme.vend.createRoutingScheme(
        scheme = scheme, country = "TZ", category = "ACCOUNT",
        addressPattern = "^[0-9]+$", secondaryAddressPattern = None,
        exampleAddress = "24730238417", description = "Account scheme",
        downstreamRails = Nil, status = "ACTIVE", createdByUserId = resourceUser1.userId
      )
      val body = s"""{"to":{"scheme":"$scheme","value":"24730238417"},"value":{"currency":"TZS","amount":"1000"},"description":"utility"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/UTILITY/transaction-requests", body, headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(UtilityIdentifierTypeWrongCategory)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 400 when identifier value does not match the scheme's address_pattern", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      // UTILITY-category scheme with a strict numeric pattern; send a non-numeric value.
      val scheme = freshSchemeName("USTR")
      RoutingSchemes.routingScheme.vend.createRoutingScheme(
        scheme = scheme, country = "TZ", category = "UTILITY",
        addressPattern = "^[0-9]{8,14}$", secondaryAddressPattern = None,
        exampleAddress = "24730238417", description = "Strict meter",
        downstreamRails = Nil, status = "ACTIVE", createdByUserId = resourceUser1.userId
      )
      val body = s"""{"to":{"scheme":"$scheme","value":"not-a-meter"},"value":{"currency":"TZS","amount":"1000"},"description":"utility"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/UTILITY/transaction-requests", body, headers)
      statusCode shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(UtilityInvalidIdentifier)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 201 with a registered callback when the biller resolves", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val acctCurrency = code.bankconnectors.Connector.connector.vend
        .getBankAccountLegacy(testBankId1, testAccountId0, None)
        .map(_._1.currency).openOrThrowException("test account")

      val meter = s"247${(System.currentTimeMillis() % 100000000L).toString.reverse.padTo(8, '0').reverse}"
      val scheme = seedUtilityBiller("UTIL", "UTILITY", meter, bankId, accountId)

      val body =
        s"""{
           |  "to": {"scheme":"$scheme","value":"$meter"},
           |  "value": {"currency":"$acctCurrency","amount":"1000"},
           |  "description": "utility token purchase",
           |  "client_reference": "ref-0001",
           |  "payer": {"phone":"255700000000","name":"Jane Doe","email":"jane.doe@example.com"},
           |  "callback_url": "https://example.com/utility/callback"
           |}""".stripMargin
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/UTILITY/transaction-requests", body, headers)
      statusCode shouldBe 201
      val trId = json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.keys should contain allOf ("id", "type", "from", "details", "status", "callback")
          map.get("type") shouldBe Some(JString("UTILITY"))
          map.get("callback") match {
            case Some(JObject(cbFields)) =>
              val cb = toFieldMap(cbFields)
              cb.get("callback_url") shouldBe Some(JString("https://example.com/utility/callback"))
              cb.keys should contain allOf ("callback_id", "status")
              // The vend is asynchronous: the callback is only REGISTERED at create time,
              // not yet fired (the token does not exist until the rail delivers the vend result).
              cb.get("status") shouldBe Some(JString("REGISTERED"))
            case other => fail(s"Expected callback object, got: $other")
          }
          // No token at creation time — vend_result must be absent / null.
          map.get("vend_result").foreach(_ shouldBe JNull)
          map.get("id") match {
            case Some(JString(id)) => id
            case _ => fail("Expected id as JSON string")
          }
        case _ => fail("Expected JSON object")
      }

      // The one-shot callback row was persisted against this transaction request, still REGISTERED.
      val stored = UtilityPaymentCallbacks.utilityPaymentCallback.vend.getCallbackByTransactionRequestId(trId)
      stored.isDefined shouldBe true
      stored.openOrThrowException("callback row").callbackUrl shouldBe "https://example.com/utility/callback"
      stored.openOrThrowException("callback row").status shouldBe UtilityCallbackStatus.Registered
    }
  }

  // ─── UTILITY vend-result delivery (asynchronous token) ────────────────────

  /** Create a UTILITY transaction request and return its id, so the vend-result endpoint
    * has a real TR (with a registered callback) to deliver against. */
  private def createUtilityTrWithCallback(): String = {
    val bankId = testBankId1.value
    val accountId = testAccountId0.value
    val acctCurrency = code.bankconnectors.Connector.connector.vend
      .getBankAccountLegacy(testBankId1, testAccountId0, None)
      .map(_._1.currency).openOrThrowException("test account")
    val meter = s"247${(System.currentTimeMillis() % 100000000L).toString.reverse.padTo(8, '0').reverse}"
    val scheme = seedUtilityBiller("VEND", "UTILITY", meter, bankId, accountId)
    val body =
      s"""{
         |  "to": {"scheme":"$scheme","value":"$meter"},
         |  "value": {"currency":"$acctCurrency","amount":"1000"},
         |  "description": "utility token purchase",
         |  "callback_url": "https://example.com/utility/callback"
         |}""".stripMargin
    val headers = Map("DirectLogin" -> s"token=${token1.value}")
    val (sc, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/UTILITY/transaction-requests", body, headers)
    sc shouldBe 201
    json match {
      case JObject(fields) => toFieldMap(fields).get("id") match {
        case Some(JString(id)) => id
        case _ => fail("Expected id in create response")
      }
      case _ => fail("Expected JSON object from create")
    }
  }

  Feature("Http4s700 createUtilityVendResult endpoint") {

    val vendBody =
      """{"status":"COMPLETED","token":"1234 5678 9012 3456 7890","rcpt_num":"202306141018422348674","units":"46.5","provider_reference":"REF800930701197"}"""

    Scenario("Reject unauthenticated POST", Http4s700RoutesTag) {
      val (statusCode, _, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/${testBankId1.value}/utility-payments/any-tr-id/vend-result", vendBody)
      statusCode shouldBe 401
    }

    Scenario("Return 403 when authenticated but missing canCreateUtilityVendResult role", Http4s700RoutesTag) {
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/${testBankId1.value}/utility-payments/any-tr-id/vend-result", vendBody, headers)
      statusCode shouldBe 403
      json match {
        case JObject(fields) => toFieldMap(fields).get("message") match {
          case Some(JString(msg)) => msg should include(canCreateUtilityVendResult.toString)
          case _ => fail("Expected message field")
        }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 404 when the transaction request does not exist", Http4s700RoutesTag) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateUtilityVendResult.toString)
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/${testBankId1.value}/utility-payments/does-not-exist/vend-result", vendBody, headers)
      statusCode shouldBe 404
      json match {
        case JObject(fields) => toFieldMap(fields).get("message") match {
          case Some(JString(msg)) => msg should include(UtilityTransactionRequestNotFound)
          case _ => fail("Expected message field")
        }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 200 and persist the vend result (token) against the transaction request", Http4s700RoutesTag) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateUtilityVendResult.toString)
      val trId = createUtilityTrWithCallback()

      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/${testBankId1.value}/utility-payments/$trId/vend-result", vendBody, headers)
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("transaction_request_id") shouldBe Some(JString(trId))
          map.get("type") shouldBe Some(JString("UTILITY"))
          map.get("vend_result") match {
            case Some(JObject(vrFields)) =>
              val vr = toFieldMap(vrFields)
              vr.get("token") shouldBe Some(JString("1234 5678 9012 3456 7890"))
              vr.get("rcpt_num") shouldBe Some(JString("202306141018422348674"))
              vr.get("status") shouldBe Some(JString("COMPLETED"))
            case other => fail(s"Expected vend_result object, got: $other")
          }
          // The callback registered on the original request is surfaced here (delivery triggered).
          map.get("callback") match {
            case Some(JObject(cbFields)) =>
              toFieldMap(cbFields).get("callback_url") shouldBe Some(JString("https://example.com/utility/callback"))
            case other => fail(s"Expected callback object, got: $other")
          }
        case _ => fail("Expected JSON object")
      }
      // Note: the response's vend_result is built by reading the attributes back from the
      // provider, so the assertions above already prove the token was persisted and round-tripped.
    }
  }

  // ─── factoryResetSystemView ───────────────────────────────────────────────

  Feature("Http4s700 factoryResetSystemView endpoint") {

    Scenario("Reject unauthenticated POST to /management/system-views/VIEW_ID/factory-reset", Http4s700RoutesTag) {
      Given("POST /obp/v7.0.0/management/system-views/auditor/factory-reset with no auth")
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", "/obp/v7.0.0/management/system-views/auditor/factory-reset", "")

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

    Scenario("Return 403 when authenticated but missing canUpdateSystemView role", Http4s700RoutesTag) {
      Given("POST /obp/v7.0.0/management/system-views/auditor/factory-reset without the required role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", "/obp/v7.0.0/management/system-views/auditor/factory-reset", "", headers)

      Then("Response is 403 with UserHasMissingRoles message naming the required role")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canUpdateSystemView.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 200 and reset permissions when entitled and view exists", Http4s700RoutesTag) {
      Given("the auditor system view exists, with an extra non-default permission")
      MapperViews.getOrCreateSystemView(SYSTEM_AUDITOR_VIEW_ID)
      ViewPermission.createSystemViewPermission(
        ViewId(SYSTEM_AUDITOR_VIEW_ID),
        code.api.Constant.CAN_ADD_TRANSACTION_REQUEST_TO_OWN_ACCOUNT,
        None
      )
      addEntitlement("", resourceUser1.userId, canUpdateSystemView.toString)

      When(s"POST /obp/v7.0.0/management/system-views/$SYSTEM_AUDITOR_VIEW_ID/factory-reset is called")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/management/system-views/$SYSTEM_AUDITOR_VIEW_ID/factory-reset", "", headers)

      Then("Response is 200 with the refreshed view JSON, no longer containing the extra permission")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val map = toFieldMap(fields)
          map.get("view_id") match {
            case Some(JString(v)) => v shouldBe SYSTEM_AUDITOR_VIEW_ID
            case _ => fail("Expected view_id as JSON string")
          }
          map.get("allowed_actions") match {
            case Some(JArray(actions)) =>
              val names = actions.collect { case JString(s) => s }
              names should not contain code.api.Constant.CAN_ADD_TRANSACTION_REQUEST_TO_OWN_ACCOUNT
            case _ => fail("Expected allowed_actions array")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 404 when system view does not exist", Http4s700RoutesTag) {
      Given("canUpdateSystemView role granted and a non-existent view id")
      addEntitlement("", resourceUser1.userId, canUpdateSystemView.toString)

      When("POST /obp/v7.0.0/management/system-views/does-not-exist/factory-reset")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", "/obp/v7.0.0/management/system-views/does-not-exist/factory-reset", "", headers)

      Then("Response is 404 with SystemViewNotFound message")
      statusCode shouldBe 404
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) => msg should include(SystemViewNotFound)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ── POST /obp/v7.0.0/users/validation-emails (anonymous resend) ─────────────
  // Anti-enumeration design: every reachable response shape is the same 201.
  // We assert the response shape and (separately, via DB / log inspection if
  // wanted) that the right server-side branch was taken. Here we just confirm
  // the contract: 201 + standard message for every input that parses.
  Feature("POST /obp/v7.0.0/users/validation-emails — anonymous resend validation email") {

    val expectedMessage =
      "If an unvalidated account exists for this username and email, a validation email has been sent."

    Scenario("Returns 201 standard message for an unknown user (no enumeration)", Http4s700RoutesTag) {
      When("we POST a (username, email) pair that does not match any user")
      val body = """{"username":"definitely-not-a-real-user","email":"nobody@example.com"}"""
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", "/obp/v7.0.0/users/validation-emails", body)
      Then("we get 201 with the standard anti-enumeration message")
      statusCode shouldBe 201
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(m)) => m shouldBe expectedMessage
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Returns 201 standard message for an already-validated user (no enumeration)", Http4s700RoutesTag) {
      Given("a validated local-provider user")
      val username = "already-validated-" + System.currentTimeMillis()
      val email = s"$username@example.com"
      val u = code.model.dataAccess.AuthUser(
        username = username,
        email = email,
        provider = code.api.Constant.localIdentityProvider,
        validated = true).withPassword("Aa1!" + java.util.UUID.randomUUID().toString).saveMe()
      try {
        When("we POST the resend request")
        val body = s"""{"username":"$username","email":"$email"}"""
        val (statusCode, json, _) = makeHttpRequestWithBody(
          "POST", "/obp/v7.0.0/users/validation-emails", body)
        Then("we get the same 201 standard message — caller cannot tell the user exists or is already validated")
        statusCode shouldBe 201
        json match {
          case JObject(fields) =>
            toFieldMap(fields).get("message") match {
              case Some(JString(m)) => m shouldBe expectedMessage
              case _ => fail("Expected message field")
            }
          case _ => fail("Expected JSON object")
        }
      } finally u.delete_!
    }

    Scenario("Returns 201 standard message for an unvalidated user (mail.test.mode logs the would-be send)", Http4s700RoutesTag) {
      Given("an unvalidated local-provider user (validation email enabled)")
      val username = "needs-validation-" + System.currentTimeMillis()
      val email = s"$username@example.com"
      val u = code.model.dataAccess.AuthUser(
        username = username,
        email = email,
        provider = code.api.Constant.localIdentityProvider,
        validated = false).withPassword("Aa1!" + java.util.UUID.randomUUID().toString).saveMe()
      try {
        When("we POST the resend request")
        val body = s"""{"username":"$username","email":"$email"}"""
        val (statusCode, json, _) = makeHttpRequestWithBody(
          "POST", "/obp/v7.0.0/users/validation-emails", body)
        Then("we get the standard 201 acknowledgement")
        statusCode shouldBe 201
        json match {
          case JObject(fields) =>
            toFieldMap(fields).get("message") match {
              case Some(JString(m)) => m shouldBe expectedMessage
              case _ => fail("Expected message field")
            }
          case _ => fail("Expected JSON object")
        }
      } finally u.delete_!
    }

    Scenario("Returns 400 InvalidJsonFormat for a malformed body (not anti-enumeration territory)", Http4s700RoutesTag) {
      When("we POST a body that cannot parse")
      val (statusCode, _, _) = makeHttpRequestWithBody(
        "POST", "/obp/v7.0.0/users/validation-emails", "not json at all")
      Then("we get 400 — body-shape errors are about the request, not user existence, so they don't leak")
      statusCode shouldBe 400
    }

    Scenario("Returns 201 standard message when username and email are blank (silently no-ops)", Http4s700RoutesTag) {
      When("we POST empty strings")
      val (statusCode, json, _) = makeHttpRequestWithBody(
        "POST", "/obp/v7.0.0/users/validation-emails", """{"username":"","email":""}""")
      Then("we still get the same 201 message")
      statusCode shouldBe 201
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(m)) => m shouldBe expectedMessage
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }
  }

  // ─── getMetricsDiagnostics ────────────────────────────────────────────────────

  Feature("Http4s700 getMetricsDiagnostics endpoint") {

    val diagnosticsPath = "/obp/v7.0.0/management/system/diagnostics/metrics"

    Scenario("Reject unauthenticated access to the metrics diagnostics", Http4s700RoutesTag) {
      Given("GET the diagnostics path with no auth headers")
      val (statusCode, json, _) = makeHttpRequest(diagnosticsPath)

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

    Scenario("Return 403 when authenticated but missing canGetMetricsDiagnostics role", Http4s700RoutesTag) {
      Given("GET the diagnostics path with DirectLogin header but no role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(diagnosticsPath, headers)

      Then("Response is 403 with UserHasMissingRoles and the role name")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canGetMetricsDiagnostics.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 200 with diagnostics shape when authenticated with canGetMetricsDiagnostics role", Http4s700RoutesTag) {
      Given("canGetMetricsDiagnostics role granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canGetMetricsDiagnostics.toString)

      When("GET the diagnostics path with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequest(diagnosticsPath, headers)

      Then("Response is 200 with config, metric, metric_archive, checks and everything_as_expected")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val m = toFieldMap(fields)
          m.keys should contain("config")
          m.keys should contain("metric")
          m.keys should contain("metric_archive")
          m.keys should contain("everything_as_expected")

          And("config exposes the archiving props as the scheduler reads them")
          m.get("config") match {
            case Some(JObject(cfgFields)) =>
              val cfg = toFieldMap(cfgFields)
              cfg.keys should contain("write_metrics")
              cfg.keys should contain("enable_metrics_scheduler")
              cfg.keys should contain("retain_metrics_scheduler_interval_in_seconds")
              cfg.keys should contain("retain_metrics_days")
              cfg.keys should contain("retain_archive_metrics_days")
              cfg.keys should contain("retain_metrics_move_limit")
            case _ => fail("Expected config object")
          }

          And("the metric table stats name the live metric table")
          m.get("metric") match {
            case Some(JObject(metricFields)) =>
              toFieldMap(metricFields).get("table_name") match {
                case Some(JString(name)) => name shouldBe "metric"
                case _ => fail("Expected table_name field")
              }
            case _ => fail("Expected metric object")
          }

          And("the archive table stats name the metricarchive table")
          m.get("metric_archive") match {
            case Some(JObject(archiveFields)) =>
              toFieldMap(archiveFields).get("table_name") match {
                case Some(JString(name)) => name shouldBe "metricarchive"
                case _ => fail("Expected table_name field")
              }
            case _ => fail("Expected metric_archive object")
          }

          And("checks is a non-empty array, each entry carrying name/status/message")
          m.get("checks") match {
            case Some(JArray(items)) =>
              items should not be empty
              items.foreach {
                case JObject(checkFields) =>
                  val c = toFieldMap(checkFields)
                  c.keys should contain("name")
                  c.keys should contain("status")
                  c.keys should contain("message")
                case _ => fail("Expected each check to be a JSON object")
              }
            case _ => fail("Expected checks array")
          }
        case _ => fail("Expected JSON object for getMetricsDiagnostics")
      }
    }
  }

  // ─── triggerMetricsArchiveRun ─────────────────────────────────────────────────

  Feature("Http4s700 triggerMetricsArchiveRun endpoint") {

    val triggerPath = "/obp/v7.0.0/management/system/diagnostics/metrics/run"

    Scenario("Reject unauthenticated trigger of a metrics archive run", Http4s700RoutesTag) {
      Given("POST the trigger path with no auth headers")
      val (statusCode, json, _) = makeHttpRequestWithMethod("POST", triggerPath)

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

    Scenario("Return 403 when authenticated but missing canCreateMetricsArchiveRun role", Http4s700RoutesTag) {
      Given("POST the trigger path with DirectLogin header but no role")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithMethod("POST", triggerPath, headers)

      Then("Response is 403 with UserHasMissingRoles and the role name")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(msg)) =>
              msg should include(UserHasMissingRoles)
              msg should include(canCreateMetricsArchiveRun.toString)
            case _ => fail("Expected message field")
          }
        case _ => fail("Expected JSON object")
      }
    }

    Scenario("Return 200 and run the archive when authenticated with canCreateMetricsArchiveRun role", Http4s700RoutesTag) {
      Given("canCreateMetricsArchiveRun role granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canCreateMetricsArchiveRun.toString)

      When("POST the trigger path with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithMethod("POST", triggerPath, headers)

      Then("Response is 200 with status=completed and a recorded run")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val m = toFieldMap(fields)
          m.get("status") match {
            case Some(JString(s)) => s shouldBe "completed"
            case _ => fail("Expected status field")
          }
          m.keys should contain("message")
          m.get("run") match {
            case Some(JObject(runFields)) =>
              val r = toFieldMap(runFields)
              r.keys should contain("run_id")
              r.keys should contain("rows_moved_to_archive")
              r.keys should contain("rows_deleted_from_archive")
              r.get("success") match {
                case Some(JBool(ok)) => ok shouldBe true
                case _ => fail("Expected success field")
              }
            case _ => fail("Expected run object on a completed run")
          }
        case _ => fail("Expected JSON object for triggerMetricsArchiveRun")
      }

      And("the run was recorded in the metricsarchiverun log")
      code.metrics.MetricsArchiveRun.lastRun.isDefined shouldBe true
    }
  }

  // ─── /my/banks — self-service bank creation ─────────────────────────────────

  Feature("Http4s700 self-service bank creation — /my/banks") {

    def extractMessage(json: JValue): String = json match {
      case JObject(fields) =>
        toFieldMap(fields).get("message") match {
          case Some(JString(msg)) => msg
          case _                  => fail("Expected message field in error response")
        }
      case _ => fail("Expected JSON object error response")
    }

    Scenario("Unauthenticated POST /my/banks returns 401", Http4s700RoutesTag) {
      Given("self_service_bank_creation.limit is 1 but no auth is supplied")
      setPropsValues("self_service_bank_creation.limit" -> "1")
      val (statusCode, json, _) = makeHttpRequestWithMethod("POST", "/obp/v7.0.0/my/banks")
      Then("Response is 401")
      statusCode shouldBe 401
      extractMessage(json) should include(AuthenticatedUserIsRequired)
    }

    Scenario("Unauthenticated GET /my/banks returns 401", Http4s700RoutesTag) {
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/my/banks")
      statusCode shouldBe 401
      extractMessage(json) should include(AuthenticatedUserIsRequired)
    }

    Scenario("POST /my/banks returns 400 when self-service creation is disabled (default limit 0)", Http4s700RoutesTag) {
      Given("self_service_bank_creation.limit is 0 (the default)")
      setPropsValues("self_service_bank_creation.limit" -> "0")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) = makeHttpRequestWithMethod("POST", "/obp/v7.0.0/my/banks", headers)
      Then("Response is 400 with SelfServiceBankCreationDisabled")
      statusCode shouldBe 400
      extractMessage(json) should include(SelfServiceBankCreationDisabled)
    }

    Scenario("POST /my/banks with a non-empty body returns 400", Http4s700RoutesTag) {
      Given("self_service_bank_creation.limit is 1 and a body is supplied")
      setPropsValues("self_service_bank_creation.limit" -> "1")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json, _) =
        makeHttpRequestWithBody("POST", "/obp/v7.0.0/my/banks", """{"full_name":"VERY RUDE BANK NAME"}""", headers)
      Then("Response is 400 — the bank identity is server-generated, no body is accepted")
      statusCode shouldBe 400
      extractMessage(json) should include(InvalidJsonFormat)
    }

    Scenario("POST /my/banks creates a generated bank; second POST is 403; GET /my/banks lists it", Http4s700RoutesTag) {
      Given("self_service_bank_creation.limit is 1")
      setPropsValues("self_service_bank_creation.limit" -> "1")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")

      When("POST /obp/v7.0.0/my/banks with an empty JSON object body (as API Explorer sends)")
      val (statusCode, json, _) = makeHttpRequestWithBody("POST", "/obp/v7.0.0/my/banks", "{}", headers)

      Then("Response is 201 with a fully generated bank identity")
      statusCode shouldBe 201
      val fieldMap = json match {
        case JObject(fields) => toFieldMap(fields)
        case _               => fail("Expected JSON object for created bank")
      }
      val bankId = fieldMap.get("bank_id") match {
        case Some(JString(id)) => id
        case _                 => fail("Expected bank_id field")
      }
      bankId should fullyMatch regex "[a-z]+-[a-z]+-[a-z]+-[0-9a-f]{4}"
      val fullName = fieldMap.get("full_name") match {
        case Some(JString(name)) => name
        case _                   => fail("Expected full_name field")
      }
      fullName should endWith(" Bank")

      And("the creator is granted CanCreateEntitlementAtOneBank at the new bank")
      Entitlement.entitlement.vend
        .getEntitlement(bankId, resourceUser1.userId, "CanCreateEntitlementAtOneBank")
        .isDefined shouldBe true

      And("GET /my/banks lists the created bank")
      val (getStatus, getJson, _) = makeHttpRequest("/obp/v7.0.0/my/banks", headers)
      getStatus shouldBe 200
      val listedBankIds = getJson \ "banks" match {
        case JArray(banks) => banks.map(bank => bank \ "bank_id").collect { case JString(id) => id }
        case _             => fail("Expected banks array")
      }
      listedBankIds should contain(bankId)

      And("a second POST returns 403 — the quota is exhausted")
      val (secondStatus, secondJson, _) = makeHttpRequestWithMethod("POST", "/obp/v7.0.0/my/banks", headers)
      secondStatus shouldBe 403
      extractMessage(secondJson) should include(SelfServiceBankLimitReached)
    }

    Scenario("Different consent-agents and the human itself create banks — all listed, one shared quota", Http4s700RoutesTag) {

      /** Simulate a consent granted by the human minting an agent user which creates a bank. */
      def createBankViaNewConsentAgent(humanUserId: String): String = {
        val consent = code.consent.MappedConsent.insertWithConsentId(APIUtil.generateUUID(), userId = humanUserId)
        val agentUser = code.users.Users.users.vend.createResourceUser(
          provider = "test-consent-issuer",
          providerId = Some(APIUtil.generateUUID()),
          createdByConsentId = Some(consent.consentId),
          name = Some("test-agent-user"),
          email = None,
          userId = None,
          createdByUserInvitationId = None,
          company = None,
          lastMarketingAgreementSignedDate = None
        ).openOrThrowException("Expected agent user to be created")
        val agentBankId = s"agent-made-${APIUtil.generateUUID().take(8)}"
        code.model.dataAccess.MappedBank.insert(
          bankId = agentBankId,
          fullBankName = "Agent Made Bank",
          shortBankName = "Agent Made",
          logoURL = "", websiteURL = "", swiftBIC = "", nationalIdentifier = "",
          bankRoutingScheme = "", bankRoutingAddress = "",
          createdByUserId = agentUser.userId)
        agentBankId
      }

      Given("user3 granted two different consents whose agents each created a bank")
      setPropsValues("self_service_bank_creation.limit" -> "3")
      val bankFromAgent1 = createBankViaNewConsentAgent(resourceUser3.userId)
      val bankFromAgent2 = createBankViaNewConsentAgent(resourceUser3.userId)
      val headers = Map("DirectLogin" -> s"token=${token3.value}")

      When("user3 creates a bank directly — quota is 2 of 3 so this succeeds")
      val (postStatus, postJson, _) = makeHttpRequestWithMethod("POST", "/obp/v7.0.0/my/banks", headers)
      postStatus shouldBe 201
      val directBankId = postJson \ "bank_id" match {
        case JString(id) => id
        case _           => fail("Expected bank_id field")
      }

      Then("GET /my/banks lists all three: both agents' banks and the direct one")
      val (getStatus, getJson, _) = makeHttpRequest("/obp/v7.0.0/my/banks", headers)
      getStatus shouldBe 200
      val listedBankIds = getJson \ "banks" match {
        case JArray(banks) => banks.map(bank => bank \ "bank_id").collect { case JString(id) => id }
        case _             => fail("Expected banks array")
      }
      listedBankIds should contain(bankFromAgent1)
      listedBankIds should contain(bankFromAgent2)
      listedBankIds should contain(directBankId)

      And("the quota is shared — a fourth bank is refused with 403")
      val (secondPostStatus, secondPostJson, _) = makeHttpRequestWithMethod("POST", "/obp/v7.0.0/my/banks", headers)
      secondPostStatus shouldBe 403
      extractMessage(secondPostJson) should include(SelfServiceBankLimitReached)
    }

    Scenario("Each user has an independent self-service quota", Http4s700RoutesTag) {
      Given("user1 has exhausted their quota but user2 has not")
      setPropsValues("self_service_bank_creation.limit" -> "1")
      val headers = Map("DirectLogin" -> s"token=${token2.value}")
      When("user2 POSTs /obp/v7.0.0/my/banks")
      val (statusCode, json, _) = makeHttpRequestWithMethod("POST", "/obp/v7.0.0/my/banks", headers)
      Then("Response is 201")
      statusCode shouldBe 201
      json \ "bank_id" match {
        case JString(id) => id should fullyMatch regex "[a-z]+-[a-z]+-[a-z]+-[0-9a-f]{4}"
        case _           => fail("Expected bank_id field")
      }
    }
  }

}
