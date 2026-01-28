package code.api.v5_0_0

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.{JArray, JField, JObject}
import net.liftweb.json.JsonParser.parse
import org.http4s.{Method, Request, Status, Uri}
import org.scalatest.Tag

class V500ContractParityTest extends V500ServerSetup {

  object V500ContractParityTag extends Tag("V500ContractParity")

  private def http4sRunAndParseJson(path: String): (Status, JValue) = {
    val request = Request[IO](
      method = Method.GET,
      uri = Uri.unsafeFromString(path)
    )
    val response = Http4s500.wrappedRoutesV500Services.orNotFound.run(request).unsafeRunSync()
    val body = response.as[String].unsafeRunSync()
    val json = if (body.trim.isEmpty) JObject(Nil) else parse(body)
    (response.status, json)
  }

  private def toFieldMap(fields: List[JField]): Map[String, JValue] = {
    fields.map(field => field.name -> field.value).toMap
  }

  feature("V500 Lift vs http4s parity") {

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
          fail("Expected Lift JSON object for root endpoint")
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
            case _ => fail("Expected Lift banks field to be an array")
          }
        case _ =>
          fail("Expected Lift JSON object for banks endpoint")
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
  }
}

