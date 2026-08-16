package code.api.berlin.group.v2

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.berlin.group.ConstantsBG
import code.util.Helper.MdcLoggable
import org.http4s._
import org.http4s.implicits._
import org.scalatest.Tag
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for Berlin Group v2 PIIS endpoint.
 * Tests POST /v2/funds-confirmations returns correct HTTP status and JSON structure.
 * Validates: Requirements 6.1
 */
class Http4sBGv2PIISTest extends AnyFlatSpec with Matchers with MdcLoggable {

  object PIISTag extends Tag("BerlinGroupV2_PIIS")

  private val routes = Http4sBGv2PIIS.routes
  private val prefix = s"/${ConstantsBG.berlinGroupVersion2.urlPrefix}/${ConstantsBG.berlinGroupVersion2.apiShortVersion}"

  private def runRequest(method: Method, uri: String): (Status, String) = {
    val req = Request[IO](method, Uri.unsafeFromString(uri))
    val resp = routes.run(req).value.unsafeRunSync().getOrElse(Response[IO](Status.NotFound))
    val body = resp.bodyText.compile.string.unsafeRunSync()
    (resp.status, body)
  }

  s"POST $prefix/funds-confirmations" should "return 200 with funds confirmation JSON" taggedAs PIISTag in {
    val (status, body) = runRequest(Method.POST, s"$prefix/funds-confirmations")
    status shouldBe Status.Ok
    body should include("fundsAvailable")
  }
}
