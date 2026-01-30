package code.api.v5_0_0

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.ResponseHeader
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.OAuth._
import code.api.util.http4s.Http4sLiftWebBridge
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonParser.parse
import org.http4s.{Header, Headers, Method, Request, Status, Uri}
import org.scalatest.Tag
import org.typelevel.ci.CIString

class Http4sLiftBridgeParityTest extends V500ServerSetup {

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

  feature("Http4s liftweb bridge parity across versions and auth") {

    scenario("legacy v2.0.0 banks parity", Http4sLiftBridgeParityTag) {
      val liftResponse = makeGetRequest((baseRequest / "obp" / "v2.0.0" / "banks").GET)
      val reqData = extractParamsAndHeaders((baseRequest / "obp" / "v2.0.0" / "banks").GET, "", "")
      val (http4sStatus, http4sJson, http4sHeaders) = runHttp4s(reqData)

      liftResponse.code should equal(http4sStatus.code)
      hasField(http4sJson, "banks") shouldBe true
      assertCorrelationId(http4sHeaders)
    }

    scenario("UK Open Banking accounts parity", Http4sLiftBridgeParityTag) {
      val liftReq = (baseRequest / "open-banking" / "v2.0" / "accounts").GET <@(user1)
      val liftResponse = makeGetRequest(liftReq)
      val reqData = extractParamsAndHeaders(liftReq, "", "")
      val (http4sStatus, http4sJson, http4sHeaders) = runHttp4s(reqData)

      liftResponse.code should equal(http4sStatus.code)
      assertCorrelationId(http4sHeaders)
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

    scenario("DirectLogin parity", Http4sLiftBridgeParityTag) {
      val liftReq = (baseRequest / "my" / "logins" / "direct").POST
      val liftResponse = makePostRequest(liftReq, "")
      val reqData = extractParamsAndHeaders(liftReq, "", "")
      val (http4sStatus, http4sJson, http4sHeaders) = runHttp4s(reqData)

      liftResponse.code should equal(http4sStatus.code)
      (hasField(http4sJson, "error") || hasField(http4sJson, "message")) shouldBe true
      assertCorrelationId(http4sHeaders)
    }
  }
}
