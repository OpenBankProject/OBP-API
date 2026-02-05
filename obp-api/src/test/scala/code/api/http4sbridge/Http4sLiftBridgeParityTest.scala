package code.api.http4sbridge

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
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonParser.parse
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._
import org.scalatest.Tag

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/**
 * Http4s Lift Bridge Parity Test
 * 
 * This test verifies that the HTTP4S server (via Http4sTestServer) produces
 * responses that match the Lift/Jetty server responses across different API versions
 * and authentication methods.
 * 
 * Unlike the previous implementation that ran the bridge in-process (which had
 * LiftRules inconsistency issues), this test uses Http4sTestServer to test the
 * real HTTP4S server over the network, matching production behavior.
 */
class Http4sLiftBridgeParityTest extends V500ServerSetup {

  // Create a test user with known password for DirectLogin testing
  private val testUsername = "http4s_bridge_test_user"
  private val testPassword = "TestPassword123!"
  private val testConsumerKey = randomString(40).toLowerCase
  private val testConsumerSecret = randomString(40).toLowerCase

  // Reference the singleton HTTP4S test server (auto-starts on first access)
  private val http4sServer = Http4sTestServer
  private val http4sBaseUrl = s"http://${http4sServer.host}:${http4sServer.port}"

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
  }

  override def afterAll(): Unit = {
    super.afterAll()
    // Clean up test data
    code.views.system.ViewDefinition.bulkDelete_!!()
    AccountAccess.bulkDelete_!!()
  }

  object Http4sLiftBridgeParityTag extends Tag("Http4sLiftBridgeParity")

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
        // Extract status code from exception message if possible
        val statusPattern = """(\d{3})""".r
        statusPattern.findFirstIn(e.getCause.getMessage) match {
          case Some(code) => (code.toInt, JObject(Nil), Map.empty)
          case None => throw e
        }
      case e: Exception =>
        throw e
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
      case e: Exception =>
        throw e
    }
  }

  private def hasField(json: JValue, key: String): Boolean = {
    json match {
      case JObject(fields) => fields.exists(_.name == key)
      case _ => false
    }
  }

  private def jsonKeys(json: JValue): Set[String] = {
    json match {
      case JObject(fields) => fields.map(_.name).toSet
      case _ => Set.empty
    }
  }

  private def jsonKeysLower(json: JValue): Set[String] = {
    jsonKeys(json).map(_.toLowerCase)
  }

  private def assertCorrelationId(headers: Map[String, String]): Unit = {
    val header = headers.find { case (key, _) => key.equalsIgnoreCase(ResponseHeader.`Correlation-Id`) }
    header.isDefined shouldBe true
    header.map(_._2.trim.nonEmpty).getOrElse(false) shouldBe true
  }

  private val standardVersions = List(
    "v1.2.1",
    "v1.3.0",
    "v1.4.0",
    "v2.0.0",
    "v2.1.0",
    "v2.2.0",
    "v3.0.0",
    "v3.1.0",
    "v4.0.0",
    "v5.0.0",
    "v5.1.0",
    "v6.0.0"
  )

  private val ukOpenBankingVersions = List("v2.0", "v3.1")

  private def runBanksParity(version: String): Unit = {
    val liftReq = (baseRequest / "obp" / version / "banks").GET
    val liftResponse = makeGetRequest(liftReq)
    val (http4sStatus, http4sJson, http4sHeaders) = makeHttp4sGetRequest(s"/obp/$version/banks")

    http4sStatus should equal(liftResponse.code)
    jsonKeysLower(http4sJson) should equal(jsonKeysLower(liftResponse.body))
    assertCorrelationId(http4sHeaders)
  }

  private def runUkOpenBankingAccountsParity(version: String): Unit = {
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

  feature("Http4s liftweb bridge parity across versions and auth") {
    standardVersions.foreach { version =>
      scenario(s"OBP $version banks parity", Http4sLiftBridgeParityTag) {
        runBanksParity(version)
      }
    }

    ukOpenBankingVersions.foreach { version =>
      scenario(s"UK Open Banking $version accounts parity", Http4sLiftBridgeParityTag) {
        runUkOpenBankingAccountsParity(version)
      }
    }

    scenario("Berlin Group accounts parity", Http4sLiftBridgeParityTag) {
      val berlinPath = ConstantsBG.berlinGroupVersion1.apiShortVersion.split("/").toList
      val base = berlinPath.foldLeft(baseRequest) { case (req, part) => req / part }
      val liftReq = (base / "accounts").GET <@(user1)
      val liftResponse = makeGetRequest(liftReq)
      val reqData = extractParamsAndHeaders(liftReq, "", "")
      val berlinPathStr = berlinPath.mkString("/", "/", "")
      val (http4sStatus, http4sJson, http4sHeaders) = makeHttp4sGetRequest(
        s"$berlinPathStr/accounts",
        reqData.headers
      )

      http4sStatus should equal(liftResponse.code)
      // Berlin Group responses can differ in top-level keys while still being valid.
      assertCorrelationId(http4sHeaders)
    }

    scenario("DirectLogin parity - missing auth header", Http4sLiftBridgeParityTag) {
      val liftReq = (baseRequest / "my" / "logins" / "direct").POST
      val liftResponse = makePostRequest(liftReq, "")
      val (http4sStatus, http4sJson, http4sHeaders) = makeHttp4sPostRequest("/my/logins/direct", "")

      http4sStatus should equal(liftResponse.code)
      (hasField(http4sJson, "error") || hasField(http4sJson, "message")) shouldBe true
      assertCorrelationId(http4sHeaders)
    }

    scenario("DirectLogin parity - with valid credentials returns 201", Http4sLiftBridgeParityTag) {
      // Use the test user with known password created in beforeAll
      val directLoginHeader = s"""DirectLogin username="$testUsername", password="$testPassword", consumer_key="$testConsumerKey""""

      val liftReq = (baseRequest / "my" / "logins" / "direct").POST
        .setHeader("Authorization", directLoginHeader)
        .setHeader("Content-Type", "application/json")

      val liftResponse = makePostRequest(liftReq, "")

      val (http4sStatus, http4sJson, http4sHeaders) = makeHttp4sPostRequest(
        "/my/logins/direct",
        "",
        Map(
          "Authorization" -> directLoginHeader,
          "Content-Type" -> "application/json"
        )
      )

      // Both should return 201 Created
      liftResponse.code should equal(201)
      http4sStatus should equal(201)
      http4sStatus should equal(liftResponse.code)
      
      // Both should have a token field
      hasField(http4sJson, "token") shouldBe true
      assertCorrelationId(http4sHeaders)
    }
  }
}
