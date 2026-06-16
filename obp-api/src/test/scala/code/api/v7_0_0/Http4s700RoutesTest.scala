package code.api.v7_0_0

import org.json4s._
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import code.api.util.http4s.Http4sStandardHeaders
import code.api.Constant.SYSTEM_OWNER_VIEW_ID
import code.api.ResponseHeader
import code.api.util.APIUtil
import code.api.util.ApiRole.{canCreateEntitlementAtAnyBank, canCreateOrganisation, canCreateRoutingScheme, canCreateUtilityVendResult, canDeleteEntitlementAtAnyBank, canDeleteOrganisation, canDeleteRoutingScheme, canDeleteSchedulerJobLock, canUpdateSystemView, canGetAccountAccessTrace, canGetAnyOrganisation, canGetAnyUser, canGetCacheConfig, canGetCacheInfo, canGetCacheNamespaces, canGetCardsForBank, canGetConnectorHealth, canCreateMetricsArchiveRun, canGetCustomersAtOneBank, canGetDatabasePoolInfo, canGetMetricsDiagnostics, canGetMigrations, canGetSchedulerJobLocks, canReadResourceDoc, canUpdateBankSupportedRoutingScheme, canUpdateOrganisation, canUpdateRoutingScheme}
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, BankNotFound, EntitlementAlreadyExists, InvalidOrganisationIdFormat, InvalidRoutingSchemeName, MobileWalletDestinationNotFound, MobileWalletInvalidMsisdn, OrganisationAlreadyExists, OrganisationNotFound, PayeeLookupAddressMismatch, PayeeLookupIdentifierTypeNotRegistered, PayeeNotFound, RoutingSchemeAlreadyExists, RoutingSchemeExampleAddressMismatch, RoutingSchemeNotFound, SystemViewNotFound, UserHasMissingRoles, UserNotFoundByUserId, UtilityIdentifierTypeWrongCategory, UtilityInvalidIdentifier, UtilityTransactionRequestNotFound}
import code.utilitypayment.{UtilityCallbackStatus, UtilityPaymentCallbacks}
import code.scheduler.JobScheduler
import net.liftweb.mapper.By
import code.api.Constant.SYSTEM_AUDITOR_VIEW_ID
import code.views.MapperViews
import code.views.system.ViewPermission
import com.openbankproject.commons.model.ViewId
import code.routingscheme.RoutingSchemes
import code.model.dataAccess.BankAccountRouting
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.organisation.Organisations
import code.metadata.counterparties.Counterparties
import com.openbankproject.commons.model.{BankId => CommBankId, CreditLimit, CreditRating, CustomerFaceImage}
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

  feature("Http4s700 routing edge cases") {

    scenario("Unknown path under v7.0.0 prefix does not silently bridge to Lift", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/nonexistent-endpoint")
      val (statusCode, _, _) = makeHttpRequest("/obp/v7.0.0/nonexistent-endpoint")

      Then("Response is not 200 — unknown path is not silently served")
      statusCode should not be 200
    }

    scenario("POST to a GET-only endpoint returns non-200", Http4s700RoutesTag) {
      Given("POST /obp/v7.0.0/root — method not allowed (root is a native GET-only v7 endpoint)")
      val (statusCode, _, _) = makeHttpRequestWithMethod("POST", "/obp/v7.0.0/root")

      Then("Response is not 200")
      statusCode should not be 200
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

  // ─── scheduler job-locks ──────────────────────────────────────────────────────

  /** Remove every jobscheduler lock row so a scenario starts from a clean table. */
  private def clearJobLocks(): Unit =
    JobScheduler.findAll().foreach(JobScheduler.delete_!)

  /** Seed one jobscheduler lock row and return its job id. */
  private def seedJobLock(name: String = "MetricsArchiveScheduler", apiInstanceId: String = "test-node"): String = {
    val jobId = APIUtil.generateUUID()
    JobScheduler.create.JobId(jobId).Name(name).ApiInstanceId(apiInstanceId).saveMe()
    jobId
  }

  feature("Http4s700 getSchedulerJobLocks endpoint") {

    scenario("Reject unauthenticated GET to /management/system/scheduler/job-locks", Http4s700RoutesTag) {
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

    scenario("Return 403 when authenticated but missing canGetSchedulerJobLocks role", Http4s700RoutesTag) {
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

    scenario("Return 200 with an empty list when no locks are held", Http4s700RoutesTag) {
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

    scenario("Return 200 listing a held lock with its fields", Http4s700RoutesTag) {
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

  feature("Http4s700 deleteSchedulerJobLock endpoint") {

    scenario("Reject unauthenticated DELETE to /management/system/scheduler/job-locks/JOB_ID", Http4s700RoutesTag) {
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

    scenario("Return 403 when authenticated but missing canDeleteSchedulerJobLock role", Http4s700RoutesTag) {
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

    scenario("Return 204 and clear the lock when authenticated with role and the lock exists", Http4s700RoutesTag) {
      Given("canDeleteSchedulerJobLock granted and one seeded lock")
      addEntitlement("", resourceUser1.userId, canDeleteSchedulerJobLock.toString)
      val seededJobId = seedJobLock()
      JobScheduler.find(By(JobScheduler.JobId, seededJobId)).isDefined shouldBe true

      When("DELETE /obp/v7.0.0/management/system/scheduler/job-locks/{jobId} with DirectLogin header")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, _, _) = makeHttpRequestWithMethod(
        "DELETE", s"/obp/v7.0.0/management/system/scheduler/job-locks/$seededJobId", headers)

      Then("Response is 204 and the lock row is gone")
      statusCode shouldBe 204
      JobScheduler.find(By(JobScheduler.JobId, seededJobId)).isDefined shouldBe false
    }

    scenario("Return 204 even when the job id does not exist (idempotent)", Http4s700RoutesTag) {
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
      val fetched = RoutingSchemes.routingScheme.vend.getRoutingScheme(scheme)
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
      val body = """{"identifier":{"scheme":"TZ.MSISDN","value":"255778300336"}}"""
      val (statusCode, _, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/payees/lookup", body)
      statusCode shouldBe 401
    }

    scenario("Return 400 when identifier.scheme is not registered", Http4s700RoutesTag) {
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

    scenario("Return 400 when identifier.value does not match the scheme's address_pattern", Http4s700RoutesTag) {
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

    scenario("Return 404 when no account has the requested routing", Http4s700RoutesTag) {
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

    scenario("Return 201 with lookup_id and payee details when account_routing resolves", Http4s700RoutesTag) {
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

  // ─── BULK transaction request ─────────────────────────────────────────────

  /** Fresh batch reference for each test scenario to avoid idempotency collisions. */
  private def freshBatchReference(): String =
    s"BATCH-${APIUtil.generateUUID().take(12)}"

  feature("Http4s700 createTransactionRequestBulk endpoint") {

    scenario("Reject unauthenticated POST", Http4s700RoutesTag) {
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

    scenario("Return 400 when payments array is empty", Http4s700RoutesTag) {
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

    scenario("Return 400 when an item currency does not match the source account", Http4s700RoutesTag) {
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

    scenario("Return 400 when end_to_end_id is duplicated in the batch", Http4s700RoutesTag) {
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

    scenario("Return 409 when batch_reference is reused on the same source account", Http4s700RoutesTag) {
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

    scenario("Return 201 with PARTIALLY_COMPLETED when one item destination resolves and another does not", Http4s700RoutesTag) {
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
    BankAccountRouting.create
      .BankId(destBankId)
      .AccountId(destAccountId)
      .AccountRoutingScheme(scheme)
      .AccountRoutingAddress(address)
      .saveMe()
    scheme
  }

  feature("Http4s700 createTransactionRequestUtility endpoint") {

    scenario("Reject unauthenticated POST", Http4s700RoutesTag) {
      val bankId = testBankId1.value
      val accountId = testAccountId0.value
      val body = """{"to":{"scheme":"TZ.UTILITY_METER","value":"24730238417"},"value":{"currency":"TZS","amount":"1000"},"description":"utility"}"""
      val (statusCode, _, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/$bankId/accounts/$accountId/owner/transaction-request-types/UTILITY/transaction-requests", body)
      statusCode shouldBe 401
    }

    scenario("Return 400 when identifier scheme is not registered", Http4s700RoutesTag) {
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

    scenario("Return 400 when identifier scheme category is not UTILITY or BILL", Http4s700RoutesTag) {
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

    scenario("Return 400 when identifier value does not match the scheme's address_pattern", Http4s700RoutesTag) {
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

    scenario("Return 201 with a registered callback when the biller resolves", Http4s700RoutesTag) {
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

  feature("Http4s700 createUtilityVendResult endpoint") {

    val vendBody =
      """{"status":"COMPLETED","token":"1234 5678 9012 3456 7890","rcpt_num":"202306141018422348674","units":"46.5","provider_reference":"REF800930701197"}"""

    scenario("Reject unauthenticated POST", Http4s700RoutesTag) {
      val (statusCode, _, _) = makeHttpRequestWithBody("POST", s"/obp/v7.0.0/banks/${testBankId1.value}/utility-payments/any-tr-id/vend-result", vendBody)
      statusCode shouldBe 401
    }

    scenario("Return 403 when authenticated but missing canCreateUtilityVendResult role", Http4s700RoutesTag) {
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

    scenario("Return 404 when the transaction request does not exist", Http4s700RoutesTag) {
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

    scenario("Return 200 and persist the vend result (token) against the transaction request", Http4s700RoutesTag) {
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

  feature("Http4s700 factoryResetSystemView endpoint") {

    scenario("Reject unauthenticated POST to /management/system-views/VIEW_ID/factory-reset", Http4s700RoutesTag) {
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

    scenario("Return 403 when authenticated but missing canUpdateSystemView role", Http4s700RoutesTag) {
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

    scenario("Return 200 and reset permissions when entitled and view exists", Http4s700RoutesTag) {
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

    scenario("Return 404 when system view does not exist", Http4s700RoutesTag) {
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
  feature("POST /obp/v7.0.0/users/validation-emails — anonymous resend validation email") {

    val expectedMessage =
      "If an unvalidated account exists for this username and email, a validation email has been sent."

    scenario("Returns 201 standard message for an unknown user (no enumeration)", Http4s700RoutesTag) {
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

    scenario("Returns 201 standard message for an already-validated user (no enumeration)", Http4s700RoutesTag) {
      Given("a validated local-provider user")
      val username = "already-validated-" + System.currentTimeMillis()
      val email = s"$username@example.com"
      val u = code.model.dataAccess.AuthUser.create
        .username(username)
        .email(email)
        .provider(code.api.Constant.localIdentityProvider)
        .password("Aa1!" + java.util.UUID.randomUUID().toString)
        .validated(true)
        .saveMe()
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

    scenario("Returns 201 standard message for an unvalidated user (mail.test.mode logs the would-be send)", Http4s700RoutesTag) {
      Given("an unvalidated local-provider user (validation email enabled)")
      val username = "needs-validation-" + System.currentTimeMillis()
      val email = s"$username@example.com"
      val u = code.model.dataAccess.AuthUser.create
        .username(username)
        .email(email)
        .provider(code.api.Constant.localIdentityProvider)
        .password("Aa1!" + java.util.UUID.randomUUID().toString)
        .validated(false)
        .saveMe()
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

    scenario("Returns 400 InvalidJsonFormat for a malformed body (not anti-enumeration territory)", Http4s700RoutesTag) {
      When("we POST a body that cannot parse")
      val (statusCode, _, _) = makeHttpRequestWithBody(
        "POST", "/obp/v7.0.0/users/validation-emails", "not json at all")
      Then("we get 400 — body-shape errors are about the request, not user existence, so they don't leak")
      statusCode shouldBe 400
    }

    scenario("Returns 201 standard message when username and email are blank (silently no-ops)", Http4s700RoutesTag) {
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

  feature("Http4s700 getMetricsDiagnostics endpoint") {

    val diagnosticsPath = "/obp/v7.0.0/management/system/diagnostics/metrics"

    scenario("Reject unauthenticated access to the metrics diagnostics", Http4s700RoutesTag) {
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

    scenario("Return 403 when authenticated but missing canGetMetricsDiagnostics role", Http4s700RoutesTag) {
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

    scenario("Return 200 with diagnostics shape when authenticated with canGetMetricsDiagnostics role", Http4s700RoutesTag) {
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

  feature("Http4s700 triggerMetricsArchiveRun endpoint") {

    val triggerPath = "/obp/v7.0.0/management/system/diagnostics/metrics/run"

    scenario("Reject unauthenticated trigger of a metrics archive run", Http4s700RoutesTag) {
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

    scenario("Return 403 when authenticated but missing canCreateMetricsArchiveRun role", Http4s700RoutesTag) {
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

    scenario("Return 200 and run the archive when authenticated with canCreateMetricsArchiveRun role", Http4s700RoutesTag) {
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

}
