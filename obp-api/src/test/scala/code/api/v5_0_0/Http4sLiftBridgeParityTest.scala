package code.api.v5_0_0

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.ResponseHeader
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createSystemViewJsonV500
import code.api.v5_0_0.ViewJsonV500
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateSystemView, CanDeleteSystemView, CanGetSystemView, CanUpdateSystemView}
import code.api.util.http4s.Http4sLiftWebBridge
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.model.dataAccess.AuthUser
import code.views.system.AccountAccess
import com.openbankproject.commons.model.UpdateViewJSON
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonParser.parse
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._
import org.http4s.{Header, Headers, Method, Request, Status, Uri}
import org.scalatest.Tag
import org.typelevel.ci.CIString

class Http4sLiftBridgeParityTest extends V500ServerSetup {

  // Create a test user with known password for DirectLogin testing
  private val testUsername = "http4s_bridge_test_user"
  private val testPassword = "TestPassword123!"
  private val testConsumerKey = randomString(40).toLowerCase
  private val testConsumerSecret = randomString(40).toLowerCase

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

  object Http4sLiftBridgeParityTag extends Tag("Http4sLiftBridgeParity")

  private val http4sRoutes = Http4sLiftWebBridge.withStandardHeaders(Http4sLiftWebBridge.routes).orNotFound

  private def toHttp4sRequest(reqData: ReqData): Request[IO] = {
    val method = Method.fromString(reqData.method).getOrElse(Method.GET)
    val base = Request[IO](method = method, uri = Uri.unsafeFromString(reqData.url))
    val withHeaders = reqData.headers.foldLeft(base) { case (req, (key, value)) =>
      req.putHeaders(Header.Raw(CIString(key), value))
    }
    if (reqData.body.trim.nonEmpty) withHeaders.withEntity(reqData.body) else withHeaders
  }

  private def runHttp4s(reqData: ReqData): (Status, JValue, Headers) = {
    val response = http4sRoutes.run(toHttp4sRequest(reqData)).unsafeRunSync()
    val body = response.as[String].unsafeRunSync()
    val json = if (body.trim.isEmpty) JObject(Nil) else parse(body)
    (response.status, json, response.headers)
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

  private def assertCorrelationId(headers: Headers): Unit = {
    val header = headers.headers.find(_.name.toString.equalsIgnoreCase(ResponseHeader.`Correlation-Id`))
    header.isDefined shouldBe true
    header.map(_.value.trim.nonEmpty).getOrElse(false) shouldBe true
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
    val reqData = extractParamsAndHeaders(liftReq, "", "")
    val (http4sStatus, http4sJson, http4sHeaders) = runHttp4s(reqData)

    liftResponse.code should equal(http4sStatus.code)
    jsonKeysLower(liftResponse.body) should equal(jsonKeysLower(http4sJson))
    assertCorrelationId(http4sHeaders)
  }

  private def runUkOpenBankingAccountsParity(version: String): Unit = {
    val liftReq = (baseRequest / "open-banking" / version / "accounts").GET <@(user1)
    val liftResponse = makeGetRequest(liftReq)
    val reqData = extractParamsAndHeaders(liftReq, "", "")
    val (http4sStatus, _, http4sHeaders) = runHttp4s(reqData)

    liftResponse.code should equal(http4sStatus.code)
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
      val (http4sStatus, http4sJson, http4sHeaders) = runHttp4s(reqData)

      liftResponse.code should equal(http4sStatus.code)
      // Berlin Group responses can differ in top-level keys while still being valid.
      assertCorrelationId(http4sHeaders)
    }

    scenario("DirectLogin parity - missing auth header", Http4sLiftBridgeParityTag) {
      val liftReq = (baseRequest / "my" / "logins" / "direct").POST
      val liftResponse = makePostRequest(liftReq, "")
      val reqData = extractParamsAndHeaders(liftReq, "", "")
      val (http4sStatus, http4sJson, http4sHeaders) = runHttp4s(reqData)

      liftResponse.code should equal(http4sStatus.code)
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

      val reqData = ReqData(
        url = s"http://${server.host}:${server.port}/my/logins/direct",
        method = "POST",
        body = "",
        body_encoding = "UTF-8",
        headers = Map(
          "Authorization" -> directLoginHeader,
          "Content-Type" -> "application/json"
        ),
        query_params = Map.empty,
        form_params = Map.empty
      )
      val (http4sStatus, http4sJson, http4sHeaders) = runHttp4s(reqData)

      // Both should return 201 Created
      liftResponse.code should equal(201)
      http4sStatus.code should equal(201)
      liftResponse.code should equal(http4sStatus.code)
      
      // Both should have a token field
      hasField(http4sJson, "token") shouldBe true
      assertCorrelationId(http4sHeaders)
    }

    scenario("System views CRUD parity", Http4sLiftBridgeParityTag) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemView.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemView.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateSystemView.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemView.toString)

      val viewId = "v" + APIUtil.generateUUID()
      val createBody = createSystemViewJsonV500.copy(name = viewId).copy(metadata_view = viewId).toCreateViewJson
      val createJson = write(createBody)

      val liftCreateReq = (baseRequest / "system-views").POST <@(user1)
      val liftCreateResponse = makePostRequest(liftCreateReq, createJson)
      val createReqData = extractParamsAndHeaders(
        liftCreateReq,
        createJson,
        "UTF-8",
        Map("Content-Type" -> "application/json")
      )
      val (http4sCreateStatus, http4sCreateJson, http4sCreateHeaders) = runHttp4s(createReqData)
      liftCreateResponse.code should equal(http4sCreateStatus.code)
      jsonKeysLower(liftCreateResponse.body) should equal(jsonKeysLower(http4sCreateJson))
      assertCorrelationId(http4sCreateHeaders)
      val createdView = liftCreateResponse.body.extract[ViewJsonV500]

      val liftGetReq = (baseRequest / "system-views" / createdView.id).GET <@(user1)
      val liftGetResponse = makeGetRequest(liftGetReq)
      val getReqData = extractParamsAndHeaders(liftGetReq, "", "UTF-8")
      val (http4sGetStatus, http4sGetJson, http4sGetHeaders) = runHttp4s(getReqData)
      liftGetResponse.code should equal(http4sGetStatus.code)
      jsonKeysLower(liftGetResponse.body) should equal(jsonKeysLower(http4sGetJson))
      assertCorrelationId(http4sGetHeaders)

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
      val liftUpdateReq = (baseRequest / "system-views" / createdView.id).PUT <@(user1)
      val liftUpdateResponse = makePutRequest(liftUpdateReq, updateJson)
      val updateReqData = extractParamsAndHeaders(
        liftUpdateReq,
        updateJson,
        "UTF-8",
        Map("Content-Type" -> "application/json")
      )
      val (http4sUpdateStatus, http4sUpdateJson, http4sUpdateHeaders) = runHttp4s(updateReqData)
      liftUpdateResponse.code should equal(http4sUpdateStatus.code)
      jsonKeysLower(liftUpdateResponse.body) should equal(jsonKeysLower(http4sUpdateJson))
      assertCorrelationId(http4sUpdateHeaders)

      val liftGetAfterUpdateReq = (baseRequest / "system-views" / createdView.id).GET <@(user1)
      val liftGetAfterUpdateResponse = makeGetRequest(liftGetAfterUpdateReq)
      val getAfterUpdateReqData = extractParamsAndHeaders(liftGetAfterUpdateReq, "", "UTF-8")
      val (http4sGetAfterUpdateStatus, http4sGetAfterUpdateJson, http4sGetAfterUpdateHeaders) = runHttp4s(getAfterUpdateReqData)
      liftGetAfterUpdateResponse.code should equal(http4sGetAfterUpdateStatus.code)
      jsonKeysLower(liftGetAfterUpdateResponse.body) should equal(jsonKeysLower(http4sGetAfterUpdateJson))
      assertCorrelationId(http4sGetAfterUpdateHeaders)

      AccountAccess.findAll(
        By(AccountAccess.view_id, createdView.id),
        By(AccountAccess.user_fk, resourceUser1.id.get)
      ).forall(_.delete_!)
      val liftDeleteReq = (baseRequest / "system-views" / createdView.id).DELETE <@(user1)
      val liftDeleteResponse = makeDeleteRequest(liftDeleteReq)
      val deleteReqData = extractParamsAndHeaders(liftDeleteReq, "", "UTF-8")
      val (http4sDeleteStatus, _, http4sDeleteHeaders) = runHttp4s(deleteReqData)
      liftDeleteResponse.code should equal(http4sDeleteStatus.code)
      assertCorrelationId(http4sDeleteHeaders)

      val liftGetAfterDeleteReq = (baseRequest / "system-views" / createdView.id).GET <@(user1)
      val liftGetAfterDeleteResponse = makeGetRequest(liftGetAfterDeleteReq)
      val getAfterDeleteReqData = extractParamsAndHeaders(liftGetAfterDeleteReq, "", "UTF-8")
      val (http4sGetAfterDeleteStatus, _, http4sGetAfterDeleteHeaders) = runHttp4s(getAfterDeleteReqData)
      liftGetAfterDeleteResponse.code should equal(http4sGetAfterDeleteStatus.code)
      assertCorrelationId(http4sGetAfterDeleteHeaders)
    }
  }
}
