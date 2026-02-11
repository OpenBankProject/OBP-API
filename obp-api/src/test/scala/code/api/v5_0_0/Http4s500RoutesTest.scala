package code.api.v5_0_0

import org.scalatest.Ignore
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.util.APIUtil
import code.setup.ServerSetupWithTestData
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.{JArray, JField, JObject}
import net.liftweb.json.JsonParser.parse
import org.http4s.{Method, Request, Status, Uri}
import org.scalatest.Tag

@Ignore
class Http4s500RoutesTest extends ServerSetupWithTestData {

  object Http4s500RoutesTag extends Tag("Http4s500Routes")

  private def runAndParseJson(request: Request[IO]): (Status, JValue) = {
    val response = Http4s500.wrappedRoutesV500Services.orNotFound.run(request).unsafeRunSync()
    val body = response.as[String].unsafeRunSync()
    val json = if (body.trim.isEmpty) JObject(Nil) else parse(body)
    (response.status, json)
  }

  private def toFieldMap(fields: List[JField]): Map[String, JValue] = {
    fields.map(field => field.name -> field.value).toMap
  }

  feature("Http4s500 root endpoint") {

    scenario("Return API info JSON", Http4s500RoutesTag) {
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v5.0.0/root")
      )

      val (status, json) = runAndParseJson(request)

      status shouldBe Status.Ok
      json match {
        case JObject(fields) =>
          val keys = fields.map(_.name)
          keys should contain("version")
          keys should contain("version_status")
          keys should contain("git_commit")
          keys should contain("connector")
        case _ =>
          fail("Expected JSON object for root endpoint")
      }
    }
  }

  feature("Http4s500 banks endpoint") {

    scenario("Return banks list JSON", Http4s500RoutesTag) {
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v5.0.0/banks")
      )

      val (status, json) = runAndParseJson(request)

      status shouldBe Status.Ok
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("banks") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected banks field to be an array")
          }
        case _ =>
          fail("Expected JSON object for banks endpoint")
      }
    }
  }

  feature("Http4s500 bank endpoint") {

    scenario("Return single bank JSON", Http4s500RoutesTag) {
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"/obp/v5.0.0/banks/${APIUtil.defaultBankId}")
      )

      val (status, json) = runAndParseJson(request)

      status shouldBe Status.Ok
      json match {
        case JObject(fields) =>
          val keys = fields.map(_.name)
          keys should contain("id")
          keys should contain("bank_code")
        case _ =>
          fail("Expected JSON object for get bank endpoint")
      }
    }
  }

  feature("Http4s500 products endpoints") {

    scenario("Return products list JSON", Http4s500RoutesTag) {
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"/obp/v5.0.0/banks/${APIUtil.defaultBankId}/products")
      )

      val (status, json) = runAndParseJson(request)

      status shouldBe Status.Ok
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("products") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected products field to be an array")
          }
        case _ =>
          fail("Expected JSON object for products endpoint")
      }
    }

    scenario("Return 404 for missing product", Http4s500RoutesTag) {
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"/obp/v5.0.0/banks/${APIUtil.defaultBankId}/products/DOES_NOT_EXIST")
      )

      val (status, json) = runAndParseJson(request)

      status shouldBe Status.NotFound
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") should not be empty
        case _ =>
          fail("Expected JSON object for error response")
      }
    }
  }
}
