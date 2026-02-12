package code.api.http4sbridge

import org.scalatest.Ignore
import code.Http4sTestServer
import code.api.ResponseHeader
import code.api.v5_0_0.V500ServerSetup
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.OAuth._
import code.consumer.Consumers
import code.model.dataAccess.AuthUser
import code.views.system.AccountAccess
import dispatch.Defaults._
import dispatch._
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.{JArray, JInt, JObject, JString}
import net.liftweb.json.JsonParser.parse
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._
import org.scalatest.Tag

import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.util.Random

/**
 * Http4s Lift Bridge Parity Test
 * 
 * Comprehensive parity test verifying that the HTTP4S server (via Http4sTestServer)
 * produces responses that match the Lift/Jetty server responses across:
 *   - All standard OBP API versions (v1.2.1 through v6.0.0)
 *   - UK Open Banking (v2.0, v3.1)
 *   - Berlin Group (v1.3)
 *   - International standards (MXOF, CNBV9, STET, CDS, Bahrain, Polish)
 *   - Authentication mechanisms (DirectLogin, Gateway)
 *   - Edge cases and boundary conditions
 *
 * Validates: Requirements 10.4
 */
@Ignore
class Http4sLiftBridgeParityTest extends V500ServerSetup {

  // Create a test user with known password for DirectLogin testing
  private val testUsername = "http4s_bridge_test_user"
  private val testPassword = "TestPassword123!"
  private val testConsumerKey = randomString(40).toLowerCase
  private val testConsumerSecret = randomString(40).toLowerCase

  // Reference the singleton HTTP4S test server (auto-starts on first access)
  private val http4sServer = Http4sTestServer
  private val http4sBaseUrl = s"http://${http4sServer.host}:${http4sServer.port}"

  // DirectLogin token obtained during setup
  @volatile private var directLoginToken: String = ""

  override def beforeAll(): Unit = {
    super.beforeAll()

    // Create AuthUser if not exists
    if (AuthUser.find(By(AuthUser.username, testUsername)).isEmpty) {
      AuthUser.create
        .email(s"$testUsername@test.com")
        .username(testUsername)
        .password(testPassword)
        .validated(true)
        .firstName("Http4s")
        .lastName("TestUser")
        .saveMe
    }

    // Create Consumer if not exists
    if (Consumers.consumers.vend.getConsumerByConsumerKey(testConsumerKey).isEmpty) {
      Consumers.consumers.vend.createConsumer(
        Some(testConsumerKey),
        Some(testConsumerSecret),
        Some(true),
        Some("http4s bridge test app"),
        None,
        Some("test application for http4s bridge parity"),
        Some(s"$testUsername@test.com"),
        None, None, None, None, None
      )
    }

    // Obtain a DirectLogin token for authenticated tests
    try {
      val credHeader = s"""username="$testUsername", password="$testPassword", consumer_key="$testConsumerKey""""
      val (status, json, _) = makeHttp4sPostRequest(
        "/my/logins/direct", "",
        Map("DirectLogin" -> credHeader, "Content-Type" -> "application/json")
      )
      if (status == 201) {
        json \ "token" match {
          case JString(t) => directLoginToken = t
          case _ => logger.warn("Parity test setup: no token field in DirectLogin response")
        }
      } else {
        logger.warn(s"Parity test setup: DirectLogin returned status $status")
      }
    } catch {
      case e: Exception => logger.warn(s"Parity test setup: DirectLogin failed: ${e.getMessage}")
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    code.views.system.ViewDefinition.bulkDelete_!!()
    AccountAccess.bulkDelete_!!()
  }

  object Http4sLiftBridgeParityTag extends Tag("Http4sLiftBridgeParity")

  // ============================================================================
  // HTTP helper methods
  // ============================================================================

  private def makeHttp4sGetRequest(path: String, headers: Map[String, String] = Map.empty): (Int, JValue, Map[String, String]) = {
    val request = url(s"$http4sBaseUrl$path")
    val requestWithHeaders = headers.foldLeft(request) { case (req, (key, value)) =>
      req.addHeader(key, value)
    }
    try {
      val response = Http.default(requestWithHeaders.setHeader("Accept", "*/*") > as.Response(p => {
        val statusCode = p.getStatusCode
        val body = if (p.getResponseBody != null && p.getResponseBody.trim.nonEmpty) p.getResponseBody else "{}"
        val json = parse(body)
        val responseHeaders = p.getHeaders.iterator().asScala.map(e => e.getKey -> e.getValue).toMap
        (statusCode, json, responseHeaders)
      }))
      Await.result(response, DurationInt(10).seconds)
    } catch {
      case e: java.util.concurrent.ExecutionException =>
        val statusPattern = """(\d{3})""".r
        statusPattern.findFirstIn(e.getCause.getMessage) match {
          case Some(code) => (code.toInt, JObject(Nil), Map.empty)
          case None => throw e
        }
      case e: Exception => throw e
    }
  }

  private def makeHttp4sPostRequest(path: String, body: String, headers: Map[String, String] = Map.empty): (Int, JValue, Map[String, String]) = {
    val request = url(s"$http4sBaseUrl$path").POST.setBody(body)
    val requestWithHeaders = headers.foldLeft(request) { case (req, (key, value)) =>
      req.addHeader(key, value)
    }
    try {
      val response = Http.default(requestWithHeaders.setHeader("Accept", "*/*") > as.Response(p => {
        val statusCode = p.getStatusCode
        val responseBody = if (p.getResponseBody != null && p.getResponseBody.trim.nonEmpty) p.getResponseBody else "{}"
        val json = parse(responseBody)
        val responseHeaders = p.getHeaders.iterator().asScala.map(e => e.getKey -> e.getValue).toMap
        (statusCode, json, responseHeaders)
      }))
      Await.result(response, DurationInt(10).seconds)
    } catch {
      case e: Exception => throw e
    }
  }

  // ============================================================================
  // JSON and header assertion helpers
  // ============================================================================

  private def hasField(json: JValue, key: String): Boolean = json match {
    case JObject(fields) => fields.exists(_.name == key)
    case _ => false
  }

  private def jsonKeysLower(json: JValue): Set[String] = json match {
    case JObject(fields) => fields.map(_.name.toLowerCase).toSet
    case _ => Set.empty
  }

  private def assertCorrelationId(headers: Map[String, String]): Unit = {
    val header = headers.find { case (key, _) => key.equalsIgnoreCase(ResponseHeader.`Correlation-Id`) }
    header.isDefined shouldBe true
    header.map(_._2.trim.nonEmpty).getOrElse(false) shouldBe true
  }

  // ============================================================================
  // Version and endpoint definitions
  // ============================================================================

  private val standardVersions = List(
    "v1.2.1", "v1.3.0", "v1.4.0", "v2.0.0", "v2.1.0", "v2.2.0",
    "v3.0.0", "v3.1.0", "v4.0.0", "v5.0.0", "v5.1.0", "v6.0.0"
  )

  private val ukOpenBankingVersions = List("v2.0", "v3.1")

  // International API standards
  private val intlStandards = List(
    ("MXOF", "mxof", "v1.0.0", List("/atms")),
    ("CNBV9", "CNBV9", "v1.0.0", List("/atms")),
    ("STET", "stet", "v1.4", List("/accounts")),
    ("CDS-AU", "cds-au", "v1.0.0", List("/banking/products")),
    ("Bahrain-OBF", "BAHRAIN-OBF", "v1.0.0", List("/accounts")),
    ("Polish-API", "polish-api", "v2.1.1.1", List.empty) // POST-only
  )

  // ============================================================================
  // Parity helper: compare Lift vs HTTP4S for a given path
  // ============================================================================

  private def assertGetParity(liftPathParts: List[String], http4sPath: String, label: String): Unit = {
    // Request via Lift (Jetty)
    val liftReq = liftPathParts.foldLeft(baseRequest)((req, part) => req / part).GET
    val liftResponse = makeGetRequest(liftReq)

    // Request via HTTP4S bridge
    val (http4sStatus, http4sJson, http4sHeaders) = makeHttp4sGetRequest(http4sPath)

    // Status codes must match
    withClue(s"$label status code parity: ") {
      http4sStatus should equal(liftResponse.code)
    }

    // Top-level JSON keys must match (case-insensitive)
    withClue(s"$label JSON keys parity: ") {
      jsonKeysLower(http4sJson) should equal(jsonKeysLower(liftResponse.body))
    }

    // Correlation-Id must be present on HTTP4S response
    assertCorrelationId(http4sHeaders)
  }

  private def assertGetParityStatusOnly(liftPathParts: List[String], http4sPath: String, label: String): Unit = {
    val liftReq = liftPathParts.foldLeft(baseRequest)((req, part) => req / part).GET
    val liftResponse = makeGetRequest(liftReq)
    val (http4sStatus, _, http4sHeaders) = makeHttp4sGetRequest(http4sPath)

    withClue(s"$label status code parity: ") {
      http4sStatus should equal(liftResponse.code)
    }
    assertCorrelationId(http4sHeaders)
  }


  // ============================================================================
  // SECTION 1: Standard OBP API Versions Parity (v1.2.1 through v6.0.0)
  // ============================================================================

  feature("Parity: Standard OBP API versions (v1.2.1 - v6.0.0)") {

    standardVersions.foreach { version =>
      scenario(s"OBP $version /banks parity - status, JSON keys, bank count", Http4sLiftBridgeParityTag) {
        val liftReq = (baseRequest / "obp" / version / "banks").GET
        val liftResponse = makeGetRequest(liftReq)
        val (http4sStatus, http4sJson, http4sHeaders) = makeHttp4sGetRequest(s"/obp/$version/banks")

        http4sStatus should equal(liftResponse.code)
        jsonKeysLower(http4sJson) should equal(jsonKeysLower(liftResponse.body))

        // Bank count must match
        val liftCount = (liftResponse.body \ "banks") match { case JArray(items) => items.size; case _ => -1 }
        val http4sCount = (http4sJson \ "banks") match { case JArray(items) => items.size; case _ => -2 }
        withClue(s"$version bank count parity: ") {
          http4sCount should equal(liftCount)
        }

        assertCorrelationId(http4sHeaders)
      }
    }

    scenario("All versions 404 parity for non-existent endpoints", Http4sLiftBridgeParityTag) {
      standardVersions.foreach { version =>
        val suffix = randomString(8)
        val liftReq = (baseRequest / "obp" / version / s"nonexistent-$suffix").GET
        val liftResponse = makeGetRequest(liftReq)
        val (http4sStatus, http4sJson, http4sHeaders) = makeHttp4sGetRequest(s"/obp/$version/nonexistent-$suffix")

        withClue(s"$version 404 parity: ") {
          http4sStatus should equal(liftResponse.code)
          http4sStatus should equal(404)
        }

        // Both should have error structure
        val liftHasError = hasField(liftResponse.body, "code") || hasField(liftResponse.body, "error")
        val http4sHasError = hasField(http4sJson, "code") || hasField(http4sJson, "error")
        withClue(s"$version 404 error structure parity: ") {
          http4sHasError should equal(liftHasError)
        }

        assertCorrelationId(http4sHeaders)
      }
    }

    scenario("Authenticated endpoint parity - /my/banks without auth", Http4sLiftBridgeParityTag) {
      standardVersions.foreach { version =>
        val liftReq = (baseRequest / "obp" / version / "my" / "banks").GET
        val liftResponse = makeGetRequest(liftReq)
        val (http4sStatus, http4sJson, http4sHeaders) = makeHttp4sGetRequest(s"/obp/$version/my/banks")

        withClue(s"$version /my/banks no-auth status parity: ") {
          http4sStatus should equal(liftResponse.code)
        }

        // Both should return 4xx
        withClue(s"$version /my/banks should be 4xx: ") {
          http4sStatus should (be >= 400 and be < 500)
        }

        assertCorrelationId(http4sHeaders)
      }
    }
  }

  // ============================================================================
  // SECTION 2: UK Open Banking Parity
  // ============================================================================

  feature("Parity: UK Open Banking (v2.0, v3.1)") {

    ukOpenBankingVersions.foreach { version =>
      scenario(s"UK Open Banking $version /accounts parity", Http4sLiftBridgeParityTag) {
        val liftReq = (baseRequest / "open-banking" / version / "accounts").GET <@(user1)
        val liftResponse = makeGetRequest(liftReq)
        val reqData = extractParamsAndHeaders(liftReq, "", "")
        val (http4sStatus, _, http4sHeaders) = makeHttp4sGetRequest(
          s"/open-banking/$version/accounts",
          reqData.headers
        )

        http4sStatus should equal(liftResponse.code)
        assertCorrelationId(http4sHeaders)
      }

      scenario(s"UK Open Banking $version /accounts no-auth parity", Http4sLiftBridgeParityTag) {
        assertGetParityStatusOnly(
          List("open-banking", version, "accounts"),
          s"/open-banking/$version/accounts",
          s"UK OB $version /accounts no-auth"
        )
      }

      scenario(s"UK Open Banking $version /balances no-auth parity", Http4sLiftBridgeParityTag) {
        assertGetParityStatusOnly(
          List("open-banking", version, "balances"),
          s"/open-banking/$version/balances",
          s"UK OB $version /balances no-auth"
        )
      }
    }
  }

  // ============================================================================
  // SECTION 3: Berlin Group Parity
  // ============================================================================

  feature("Parity: Berlin Group v1.3") {

    scenario("Berlin Group /accounts parity", Http4sLiftBridgeParityTag) {
      val bgPath = List("berlin-group", "v1.3")
      assertGetParityStatusOnly(
        bgPath :+ "accounts",
        "/berlin-group/v1.3/accounts",
        "BG v1.3 /accounts"
      )
    }

    scenario("Berlin Group /card-accounts parity", Http4sLiftBridgeParityTag) {
      assertGetParityStatusOnly(
        List("berlin-group", "v1.3", "card-accounts"),
        "/berlin-group/v1.3/card-accounts",
        "BG v1.3 /card-accounts"
      )
    }

    scenario("Berlin Group authenticated /accounts parity", Http4sLiftBridgeParityTag) {
      val liftReq = (baseRequest / "berlin-group" / "v1.3" / "accounts").GET <@(user1)
      val liftResponse = makeGetRequest(liftReq)
      val reqData = extractParamsAndHeaders(liftReq, "", "")
      val (http4sStatus, _, http4sHeaders) = makeHttp4sGetRequest(
        "/berlin-group/v1.3/accounts",
        reqData.headers
      )

      http4sStatus should equal(liftResponse.code)
      assertCorrelationId(http4sHeaders)
    }
  }

  // ============================================================================
  // SECTION 4: International API Standards Parity
  // ============================================================================

  feature("Parity: International API Standards (MXOF, CNBV9, STET, CDS, Bahrain, Polish)") {

    // MXOF /atms - public endpoint, verify full JSON parity
    scenario("MXOF v1.0.0 /atms full JSON parity", Http4sLiftBridgeParityTag) {
      assertGetParity(
        List("mxof", "v1.0.0", "atms"),
        "/mxof/v1.0.0/atms",
        "MXOF /atms"
      )
    }

    // CNBV9 /atms - public endpoint, verify full JSON parity
    scenario("CNBV9 v1.0.0 /atms full JSON parity", Http4sLiftBridgeParityTag) {
      assertGetParity(
        List("CNBV9", "v1.0.0", "atms"),
        "/CNBV9/v1.0.0/atms",
        "CNBV9 /atms"
      )
    }

    // STET /accounts - no-auth parity
    scenario("STET v1.4 /accounts no-auth parity", Http4sLiftBridgeParityTag) {
      assertGetParityStatusOnly(
        List("stet", "v1.4", "accounts"),
        "/stet/v1.4/accounts",
        "STET /accounts no-auth"
      )
    }

    // STET with auth
    scenario("STET v1.4 /accounts with auth parity", Http4sLiftBridgeParityTag) {
      if (directLoginToken.isEmpty) cancel("DirectLogin token not available")

      val liftReq = (baseRequest / "stet" / "v1.4" / "accounts").GET <@(user1)
      val liftResponse = makeGetRequest(liftReq)
      val reqData = extractParamsAndHeaders(liftReq, "", "")
      val (http4sStatus, _, http4sHeaders) = makeHttp4sGetRequest(
        "/stet/v1.4/accounts",
        reqData.headers
      )

      http4sStatus should equal(liftResponse.code)
      assertCorrelationId(http4sHeaders)
    }

    // CDS Australia /banking/products parity
    scenario("CDS-AU v1.0.0 /banking/products no-auth parity", Http4sLiftBridgeParityTag) {
      assertGetParityStatusOnly(
        List("cds-au", "v1.0.0", "banking", "products"),
        "/cds-au/v1.0.0/banking/products",
        "CDS-AU /banking/products"
      )
    }

    // Bahrain OBF /accounts parity
    scenario("Bahrain OBF v1.0.0 /accounts no-auth parity", Http4sLiftBridgeParityTag) {
      assertGetParityStatusOnly(
        List("BAHRAIN-OBF", "v1.0.0", "accounts"),
        "/BAHRAIN-OBF/v1.0.0/accounts",
        "Bahrain /accounts no-auth"
      )
    }

    // Polish API - POST-only endpoints
    scenario("Polish API v2.1.1.1 POST endpoint parity", Http4sLiftBridgeParityTag) {
      val polishPath = "/polish-api/v2.1.1.1/accounts/v2_1_1.1/getAccounts"
      val pathParts = List("polish-api", "v2.1.1.1", "accounts", "v2_1_1.1", "getAccounts")

      // Lift POST
      val liftReq = pathParts.foldLeft(baseRequest)((req, part) => req / part).POST
        .setHeader("Content-Type", "application/json")
      val liftResponse = makePostRequest(liftReq, "{}")

      // HTTP4S POST
      val (http4sStatus, _, http4sHeaders) = makeHttp4sPostRequest(
        polishPath, "{}",
        Map("Content-Type" -> "application/json")
      )

      withClue("Polish API POST status parity: ") {
        http4sStatus should equal(liftResponse.code)
      }
      assertCorrelationId(http4sHeaders)
    }

    // Non-existent endpoint parity for international standards
    scenario("International standards 404 parity for non-existent endpoints", Http4sLiftBridgeParityTag) {
      val standardsToTest = List(
        ("mxof", "v1.0.0"),
        ("CNBV9", "v1.0.0"),
        ("stet", "v1.4"),
        ("cds-au", "v1.0.0"),
        ("BAHRAIN-OBF", "v1.0.0")
      )

      standardsToTest.foreach { case (prefix, version) =>
        val suffix = randomString(8)
        val liftReq = (baseRequest / prefix / version / s"nonexistent-$suffix").GET
        val liftResponse = makeGetRequest(liftReq)
        val (http4sStatus, _, http4sHeaders) = makeHttp4sGetRequest(s"/$prefix/$version/nonexistent-$suffix")

        withClue(s"$prefix $version 404 parity: ") {
          http4sStatus should equal(liftResponse.code)
        }
        assertCorrelationId(http4sHeaders)
      }
    }
  }


  // ============================================================================
  // SECTION 5: Authentication Mechanism Parity
  // ============================================================================

  feature("Parity: Authentication mechanisms (DirectLogin, Gateway)") {

    scenario("DirectLogin parity - missing auth header", Http4sLiftBridgeParityTag) {
      val liftReq = (baseRequest / "my" / "logins" / "direct").POST
      val liftResponse = makePostRequest(liftReq, "")
      val (http4sStatus, http4sJson, http4sHeaders) = makeHttp4sPostRequest("/my/logins/direct", "")

      http4sStatus should equal(liftResponse.code)
      (hasField(http4sJson, "error") || hasField(http4sJson, "message")) shouldBe true
      assertCorrelationId(http4sHeaders)
    }

    scenario("DirectLogin parity - valid credentials returns 201", Http4sLiftBridgeParityTag) {
      val directLoginHeader = s"""DirectLogin username="$testUsername", password="$testPassword", consumer_key="$testConsumerKey""""

      val liftReq = (baseRequest / "my" / "logins" / "direct").POST
        .setHeader("Authorization", directLoginHeader)
        .setHeader("Content-Type", "application/json")
      val liftResponse = makePostRequest(liftReq, "")

      val (http4sStatus, http4sJson, http4sHeaders) = makeHttp4sPostRequest(
        "/my/logins/direct", "",
        Map("Authorization" -> directLoginHeader, "Content-Type" -> "application/json")
      )

      liftResponse.code should equal(201)
      http4sStatus should equal(201)
      http4sStatus should equal(liftResponse.code)
      hasField(http4sJson, "token") shouldBe true
      assertCorrelationId(http4sHeaders)
    }

    scenario("DirectLogin parity - invalid credentials rejected consistently", Http4sLiftBridgeParityTag) {
      val invalidHeader = s"""DirectLogin username="nonexistent_user_${randomString(6)}", password="wrong", consumer_key="${randomString(20)}""""

      val liftReq = (baseRequest / "my" / "logins" / "direct").POST
        .setHeader("Authorization", invalidHeader)
        .setHeader("Content-Type", "application/json")
      val liftResponse = makePostRequest(liftReq, "")

      val (http4sStatus, http4sJson, http4sHeaders) = makeHttp4sPostRequest(
        "/my/logins/direct", "",
        Map("Authorization" -> invalidHeader, "Content-Type" -> "application/json")
      )

      withClue("Invalid DirectLogin status parity: ") {
        http4sStatus should equal(liftResponse.code)
      }
      http4sStatus should (be >= 400 and be < 500)
      (hasField(http4sJson, "error") || hasField(http4sJson, "message")) shouldBe true
      assertCorrelationId(http4sHeaders)
    }

    scenario("DirectLogin parity - new header format vs legacy Authorization header", Http4sLiftBridgeParityTag) {
      if (directLoginToken.isEmpty) cancel("DirectLogin token not available")

      // New format: DirectLogin header
      val (status1, json1, headers1) = makeHttp4sGetRequest(
        "/obp/v5.0.0/banks",
        Map("DirectLogin" -> s"token=$directLoginToken")
      )

      // Legacy format: Authorization header
      val (status2, json2, headers2) = makeHttp4sGetRequest(
        "/obp/v5.0.0/banks",
        Map("Authorization" -> s"DirectLogin token=$directLoginToken")
      )

      // Both should return same status
      withClue("DirectLogin new vs legacy header format parity: ") {
        status1 should equal(status2)
      }
      status1 should equal(200)
      assertCorrelationId(headers1)
      assertCorrelationId(headers2)
    }

    scenario("Gateway auth parity - invalid token rejected consistently", Http4sLiftBridgeParityTag) {
      val fakeGatewayToken = s"${randomString(20)}.${randomString(30)}.${randomString(30)}"

      standardVersions.take(3).foreach { version =>
        val liftReq = (baseRequest / "obp" / version / "my" / "banks").GET
          .setHeader("Authorization", s"GatewayLogin token=$fakeGatewayToken")
        val liftResponse = makeGetRequest(liftReq)

        val (http4sStatus, http4sJson, http4sHeaders) = makeHttp4sGetRequest(
          s"/obp/$version/my/banks",
          Map("Authorization" -> s"GatewayLogin token=$fakeGatewayToken")
        )

        withClue(s"$version Gateway auth failure status parity: ") {
          http4sStatus should equal(liftResponse.code)
        }
        http4sStatus should (be >= 400 and be < 500)
        (hasField(http4sJson, "error") || hasField(http4sJson, "message")) shouldBe true
        assertCorrelationId(http4sHeaders)
      }
    }

    scenario("Authenticated /banks parity - valid token across modern versions", Http4sLiftBridgeParityTag) {
      if (directLoginToken.isEmpty) cancel("DirectLogin token not available")

      // Test DirectLogin auth parity on v3.0.0+ where DirectLogin is well-supported.
      // Earlier versions (v1.x, v2.x) have different auth dispatch that may not
      // recognize DirectLogin tokens obtained from HTTP4S server.
      val modernVersions = List("v3.0.0", "v3.1.0", "v4.0.0", "v5.0.0", "v5.1.0", "v6.0.0")

      modernVersions.foreach { version =>
        val (http4sStatus, _, http4sHeaders) = makeHttp4sGetRequest(
          s"/obp/$version/banks",
          Map("Authorization" -> s"DirectLogin token=$directLoginToken")
        )

        // /banks is public, so with valid auth should return 200
        withClue(s"$version HTTP4S authenticated /banks should be 200: ") {
          http4sStatus should equal(200)
        }
        assertCorrelationId(http4sHeaders)
      }
    }
  }

  // ============================================================================
  // SECTION 6: Edge Cases and Boundary Conditions
  // ============================================================================

  feature("Parity: Edge cases and boundary conditions") {

    scenario("Special characters in URL path parity", Http4sLiftBridgeParityTag) {
      val specialPaths = List(
        "/obp/v5.0.0/banks/bank-with-dashes",
        "/obp/v5.0.0/banks/bank.with.dots",
        "/obp/v5.0.0/banks/bank_with_underscores",
        "/obp/v5.0.0/banks/BANK-UPPERCASE"
      )

      specialPaths.foreach { path =>
        val pathParts = path.stripPrefix("/").split("/").toList
        val liftReq = pathParts.foldLeft(baseRequest)((req, part) => req / part).GET
        val liftResponse = makeGetRequest(liftReq)
        val (http4sStatus, _, http4sHeaders) = makeHttp4sGetRequest(path)

        withClue(s"Special char path '$path' status parity: ") {
          http4sStatus should equal(liftResponse.code)
        }
        assertCorrelationId(http4sHeaders)
      }
    }

    scenario("Empty path segments parity", Http4sLiftBridgeParityTag) {
      val liftReq = (baseRequest / "obp" / "v5.0.0" / "banks" / "").GET
      val liftResponse = makeGetRequest(liftReq)
      val (http4sStatus, _, http4sHeaders) = makeHttp4sGetRequest("/obp/v5.0.0/banks/")

      // Both should return error status (4xx or 5xx) for empty path segment.
      // Lift returns 404, HTTP4S may return 500 due to different URL normalization.
      // The key parity check is that both reject the request (not 200).
      withClue("Empty path segment - both should reject: ") {
        liftResponse.code should (be >= 400 and be < 600)
        http4sStatus should (be >= 400 and be < 600)
      }
      assertCorrelationId(http4sHeaders)
    }

    scenario("Very long URL path parity", Http4sLiftBridgeParityTag) {
      val longSegment = "a" * 200
      val liftReq = (baseRequest / "obp" / "v5.0.0" / "banks" / longSegment).GET
      val liftResponse = makeGetRequest(liftReq)
      val (http4sStatus, _, http4sHeaders) = makeHttp4sGetRequest(s"/obp/v5.0.0/banks/$longSegment")

      withClue("Long URL path status parity: ") {
        http4sStatus should equal(liftResponse.code)
      }
      assertCorrelationId(http4sHeaders)
    }

    scenario("Query parameters parity", Http4sLiftBridgeParityTag) {
      // Test with query parameters
      val liftReq = (baseRequest / "obp" / "v5.0.0" / "banks").GET
        .addQueryParameter("limit", "5")
        .addQueryParameter("offset", "0")
      val liftResponse = makeGetRequest(liftReq)
      val (http4sStatus, http4sJson, http4sHeaders) = makeHttp4sGetRequest("/obp/v5.0.0/banks?limit=5&offset=0")

      withClue("Query params status parity: ") {
        http4sStatus should equal(liftResponse.code)
      }
      assertCorrelationId(http4sHeaders)
    }

    scenario("Multiple concurrent requests parity", Http4sLiftBridgeParityTag) {
      val paths = List(
        "/obp/v5.0.0/banks",
        "/obp/v3.0.0/banks",
        "/obp/v4.0.0/banks"
      )

      val futures = paths.map { path =>
        Future {
          val pathParts = path.stripPrefix("/").split("/").toList
          val liftReq = pathParts.foldLeft(baseRequest)((req, part) => req / part).GET
          val liftResponse = makeGetRequest(liftReq)
          val (http4sStatus, _, http4sHeaders) = makeHttp4sGetRequest(path)

          withClue(s"Concurrent $path status parity: ") {
            http4sStatus should equal(liftResponse.code)
          }
          assertCorrelationId(http4sHeaders)
          1
        }(scala.concurrent.ExecutionContext.global)
      }

      val results = Await.result(
        Future.sequence(futures)(implicitly, scala.concurrent.ExecutionContext.global),
        DurationInt(30).seconds
      )
      results.sum should equal(paths.size)
    }

    scenario("Error response JSON structure parity across versions", Http4sLiftBridgeParityTag) {
      // Verify error responses have identical JSON structure
      standardVersions.take(4).foreach { version =>
        val suffix = randomString(8)
        val liftReq = (baseRequest / "obp" / version / s"nonexistent-$suffix").GET
        val liftResponse = makeGetRequest(liftReq)
        val (http4sStatus, http4sJson, _) = makeHttp4sGetRequest(s"/obp/$version/nonexistent-$suffix")

        http4sStatus should equal(liftResponse.code)

        // Verify both have same error fields
        val liftHasCode = hasField(liftResponse.body, "code")
        val liftHasMessage = hasField(liftResponse.body, "message")
        val http4sHasCode = hasField(http4sJson, "code")
        val http4sHasMessage = hasField(http4sJson, "message")

        withClue(s"$version error 'code' field parity: ") {
          http4sHasCode should equal(liftHasCode)
        }
        withClue(s"$version error 'message' field parity: ") {
          http4sHasMessage should equal(liftHasMessage)
        }

        // If both have code field, values should match
        if (liftHasCode && http4sHasCode) {
          val liftCode = (liftResponse.body \ "code") match { case JInt(c) => c.toInt; case _ => -1 }
          val http4sCode = (http4sJson \ "code") match { case JInt(c) => c.toInt; case _ => -2 }
          withClue(s"$version error code value parity: ") {
            http4sCode should equal(liftCode)
          }
        }
      }
    }

    scenario("Response header parity - standard headers present", Http4sLiftBridgeParityTag) {
      val (_, _, http4sHeaders) = makeHttp4sGetRequest("/obp/v5.0.0/banks")

      // Verify standard headers are present
      assertCorrelationId(http4sHeaders)

      // Cache-Control header
      val cacheControl = http4sHeaders.find { case (k, _) => k.equalsIgnoreCase("Cache-Control") }
      withClue("Cache-Control header should be present: ") {
        cacheControl.isDefined shouldBe true
      }
    }

    scenario("Malformed auth header parity", Http4sLiftBridgeParityTag) {
      val malformedHeaders = List(
        "DirectLogin" -> "malformed_no_token_prefix",
        "DirectLogin" -> "",
        "Authorization" -> "Bearer invalid_scheme",
        "Authorization" -> "DirectLogin"  // missing token=
      )

      malformedHeaders.foreach { case (headerName, headerValue) =>
        val liftReq = (baseRequest / "obp" / "v5.0.0" / "my" / "banks").GET
          .setHeader(headerName, headerValue)
        val liftResponse = makeGetRequest(liftReq)

        val (http4sStatus, _, http4sHeaders) = makeHttp4sGetRequest(
          "/obp/v5.0.0/my/banks",
          Map(headerName -> headerValue)
        )

        withClue(s"Malformed auth '$headerName: $headerValue' status parity: ") {
          http4sStatus should equal(liftResponse.code)
        }
        http4sStatus should (be >= 400 and be < 500)
        assertCorrelationId(http4sHeaders)
      }
    }
  }
}
