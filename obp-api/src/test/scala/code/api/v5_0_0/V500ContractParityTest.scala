package code.api.v5_0_0

import org.scalatest.Ignore
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.{JArray, JField, JObject, JString}
import net.liftweb.json.JsonParser.parse
import org.http4s.{Method, Request, Status, Uri}
import org.http4s.Header
import org.typelevel.ci.CIString
import org.scalatest.Tag

@Ignore
class V500ContractParityTest extends V500ServerSetup {

  object V500ContractParityTag extends Tag("V500ContractParity")

  private def http4sRunAndParseJson(path: String): (Status, JValue) = {
    val request = Request[IO](
      method = Method.GET,
      uri = Uri.unsafeFromString(path)
    )
    val response = Http4s500.wrappedRoutesV500ServicesWithJsonNotFound.orNotFound.run(request).unsafeRunSync()
    val body = response.as[String].unsafeRunSync()
    val json = if (body.trim.isEmpty) JObject(Nil) else parse(body)
    (response.status, json)
  }

  private def toFieldMap(fields: List[JField]): Map[String, JValue] = {
    fields.map(field => field.name -> field.value).toMap
  }

  private def getStringField(json: JValue, key: String): Option[String] = {
    json match {
      case JObject(fields) =>
        toFieldMap(fields).get(key) match {
          case Some(JString(v)) => Some(v)
          case _ => None
        }
      case _ => None
    }
  }

  feature("V500 liftweb vs http4s parity") {

    scenario("root returns consistent status and key fields", V500ContractParityTag) {
      val liftResponse = makeGetRequest((v5_0_0_Request / "root").GET)
      val (http4sStatus, http4sJson) = http4sRunAndParseJson("/obp/v5.0.0/root")

      liftResponse.code should equal(http4sStatus.code)

      liftResponse.body match {
        case JObject(fields) =>
          val keys = fields.map(_.name)
          keys should contain("version")
          keys should contain("version_status")
          keys should contain("git_commit")
          keys should contain("connector")
        case _ =>
          fail("Expected liftweb JSON object for root endpoint")
      }

      http4sJson match {
        case JObject(fields) =>
          val keys = fields.map(_.name)
          keys should contain("version")
          keys should contain("version_status")
          keys should contain("git_commit")
          keys should contain("connector")
        case _ =>
          fail("Expected http4s JSON object for root endpoint")
      }
    }

    scenario("banks returns consistent status and banks array shape", V500ContractParityTag) {
      val liftResponse = makeGetRequest((v5_0_0_Request / "banks").GET)
      val (http4sStatus, http4sJson) = http4sRunAndParseJson("/obp/v5.0.0/banks")

      liftResponse.code should equal(http4sStatus.code)

      liftResponse.body match {
        case JObject(fields) =>
          toFieldMap(fields).get("banks") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected liftweb banks field to be an array")
          }
        case _ =>
          fail("Expected liftweb JSON object for banks endpoint")
      }

      http4sJson match {
        case JObject(fields) =>
          toFieldMap(fields).get("banks") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected http4s banks field to be an array")
          }
        case _ =>
          fail("Expected http4s JSON object for banks endpoint")
      }
    }

    scenario("bank returns consistent status and bank id", V500ContractParityTag) {
      val bankId = APIUtil.defaultBankId
      val liftResponse = makeGetRequest((v5_0_0_Request / "banks" / bankId).GET)
      val (http4sStatus, http4sJson) = http4sRunAndParseJson(s"/obp/v5.0.0/banks/$bankId")

      liftResponse.code should equal(http4sStatus.code)

      getStringField(liftResponse.body, "id") shouldBe Some(bankId)
      getStringField(http4sJson, "id") shouldBe Some(bankId)
    }

    scenario("products list returns consistent status and products array shape", V500ContractParityTag) {
      val bankId = APIUtil.defaultBankId
      val liftResponse = makeGetRequest((v5_0_0_Request / "banks" / bankId / "products").GET)
      val (http4sStatus, http4sJson) = http4sRunAndParseJson(s"/obp/v5.0.0/banks/$bankId/products")

      liftResponse.code should equal(http4sStatus.code)

      liftResponse.body match {
        case JObject(fields) =>
          toFieldMap(fields).get("products") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected liftweb products field to be an array")
          }
        case _ =>
          fail("Expected liftweb JSON object for products endpoint")
      }

      http4sJson match {
        case JObject(fields) =>
          toFieldMap(fields).get("products") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected http4s products field to be an array")
          }
        case _ =>
          fail("Expected http4s JSON object for products endpoint")
      }
    }

    scenario("product returns consistent 404 for missing product", V500ContractParityTag) {
      val bankId = APIUtil.defaultBankId
      val productCode = "DOES_NOT_EXIST"
      val liftResponse = makeGetRequest((v5_0_0_Request / "banks" / bankId / "products" / productCode).GET)
      val (http4sStatus, http4sJson) = http4sRunAndParseJson(s"/obp/v5.0.0/banks/$bankId/products/$productCode")

      liftResponse.code should equal(http4sStatus.code)

      liftResponse.body match {
        case JObject(fields) =>
          toFieldMap(fields).get("message").isDefined shouldBe true
        case _ =>
          fail("Expected liftweb JSON object for missing product error")
      }

      http4sJson match {
        case JObject(fields) =>
          toFieldMap(fields).get("message").isDefined shouldBe true
        case _ =>
          fail("Expected http4s JSON object for missing product error")
      }
    }

    scenario("private accounts endpoint is served (proxy parity)", V500ContractParityTag) {
      val bankId = APIUtil.defaultBankId
      val liftResponse = getPrivateAccounts(bankId, user1)
      val liftReq = (v5_0_0_Request / "banks" / bankId / "accounts" / "private").GET <@(user1)
      val reqData = extractParamsAndHeaders(liftReq, "", "")

      val baseRequest = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"/obp/v5.0.0/banks/$bankId/accounts/private")
      )
      val request = reqData.headers.foldLeft(baseRequest) { case (r, (k, v)) =>
        r.putHeaders(Header.Raw(CIString(k), v))
      }

      val response = Http4s500.wrappedRoutesV500ServicesWithJsonNotFound.orNotFound.run(request).unsafeRunSync()
      val http4sStatus = response.status
      val correlationHeader = response.headers.get(CIString("Correlation-Id"))
      val body = response.as[String].unsafeRunSync()
      val http4sJson = if (body.trim.isEmpty) JObject(Nil) else parse(body)

      liftResponse.code should equal(http4sStatus.code)
      correlationHeader.isDefined shouldBe true

      http4sJson match {
        case JObject(fields) =>
          toFieldMap(fields).get("accounts") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected accounts field to be an array")
          }
        case _ =>
          fail("Expected http4s JSON object for private accounts endpoint")
      }
    }
  }
}
