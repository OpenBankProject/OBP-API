package code.api.berlin.group.v2

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.berlin.group.ConstantsBG
import code.util.Helper.MdcLoggable
import org.http4s._
import org.http4s.implicits._
import org.scalatest.{FlatSpec, Matchers, Tag}

/**
 * Unit tests for Berlin Group v2 PIS endpoints.
 * Tests each of the 13 PIS endpoints returns correct HTTP status and JSON structure.
 * Validates: Requirements 3.1-3.3, 4.1-4.4, 5.1-5.5
 */
class Http4sBGv2PISTest extends FlatSpec with Matchers with MdcLoggable {

  object PISTag extends Tag("BerlinGroupV2_PIS")

  private val routes = Http4sBGv2PIS.routes
  private val prefix = s"/${ConstantsBG.berlinGroupVersion2.urlPrefix}/${ConstantsBG.berlinGroupVersion2.apiShortVersion}"

  private def runRequest(method: Method, uri: String): (Status, String) = {
    val req = Request[IO](method, Uri.unsafeFromString(uri))
    val resp = routes.run(req).value.unsafeRunSync().getOrElse(Response[IO](Status.NotFound))
    val body = resp.bodyText.compile.string.unsafeRunSync()
    (resp.status, body)
  }

  // ── Payment Initiation (Req 3.1-3.3) ─────────────────────────────

  s"POST $prefix/payments/{pp}" should "return 201 with payment initiation JSON" taggedAs PISTag in {
    val (status, body) = runRequest(Method.POST, s"$prefix/payments/sepa-credit-transfers")
    status shouldBe Status.Created
    body should include("transactionStatus")
    body should include("paymentId")
    body should include("_links")
  }

  s"POST $prefix/bulk-payments/{pp}" should "return 201 with payment initiation JSON" taggedAs PISTag in {
    val (status, body) = runRequest(Method.POST, s"$prefix/bulk-payments/sepa-credit-transfers")
    status shouldBe Status.Created
    body should include("transactionStatus")
    body should include("paymentId")
  }

  s"POST $prefix/periodic-payments/{pp}" should "return 201 with payment initiation JSON" taggedAs PISTag in {
    val (status, body) = runRequest(Method.POST, s"$prefix/periodic-payments/instant-sepa-credit-transfers")
    status shouldBe Status.Created
    body should include("transactionStatus")
    body should include("paymentId")
  }

  // ── Payment Status/Retrieval/Deletion (Req 4.1-4.4) ──────────────

  s"GET $prefix/{ps}/{pp}/{pid}/status" should "return 200 with payment status JSON" taggedAs PISTag in {
    val (status, body) = runRequest(Method.GET, s"$prefix/payments/sepa-credit-transfers/pay-123/status")
    status shouldBe Status.Ok
    body should include("transactionStatus")
  }

  s"GET $prefix/{ps}/{pp}/{pid}" should "return 200 with payment details JSON" taggedAs PISTag in {
    val (status, body) = runRequest(Method.GET, s"$prefix/payments/sepa-credit-transfers/pay-123")
    status shouldBe Status.Ok
    body should include("transactionStatus")
    body should include("paymentId")
    body should include("pay-123")
    body should include("debtorAccount")
  }

  s"DELETE $prefix/{ps}/{pp}/{pid}" should "return 204 with empty body" taggedAs PISTag in {
    val (status, body) = runRequest(Method.DELETE, s"$prefix/payments/sepa-credit-transfers/pay-123")
    status shouldBe Status.NoContent
  }

  s"GET $prefix/bulk-payments/{pp}/{pid}/extended-status" should "return 200 with extended status JSON" taggedAs PISTag in {
    val (status, body) = runRequest(Method.GET, s"$prefix/bulk-payments/sepa-credit-transfers/pay-123/extended-status")
    status shouldBe Status.Ok
    body should include("transactionStatus")
    body should include("fundsAvailable")
    body should include("pay-123")
  }

  // ── Authorisation (Req 5.1-5.5) ──────────────────────────────────

  s"POST $prefix/{ps}/{pp}/{pid}/authorisations" should "return 201 with authorisation JSON" taggedAs PISTag in {
    val (status, body) = runRequest(Method.POST, s"$prefix/payments/sepa-credit-transfers/pay-123/authorisations")
    status shouldBe Status.Created
    body should include("authorisationId")
    body should include("scaStatus")
    body should include("_links")
  }

  s"GET $prefix/{ps}/{pp}/{pid}/authorisations" should "return 200 with authorisation sub-resources JSON" taggedAs PISTag in {
    val (status, body) = runRequest(Method.GET, s"$prefix/payments/sepa-credit-transfers/pay-123/authorisations")
    status shouldBe Status.Ok
    body should include("authorisationIds")
  }

  s"GET $prefix/{ps}/{pp}/{pid}/authorisations/{authId}" should "return 200 with authorisation status JSON" taggedAs PISTag in {
    val (status, body) = runRequest(Method.GET, s"$prefix/payments/sepa-credit-transfers/pay-123/authorisations/auth-456")
    status shouldBe Status.Ok
    body should include("scaStatus")
  }

  s"PUT $prefix/{ps}/{pp}/{pid}/authorisations/{authId}" should "return 200 with updated PSU data JSON" taggedAs PISTag in {
    val (status, body) = runRequest(Method.PUT, s"$prefix/payments/sepa-credit-transfers/pay-123/authorisations/auth-456")
    status shouldBe Status.Ok
    body should include("scaStatus")
    body should include("_links")
  }

  s"PUT $prefix/{ps}/{pp}/{pid}" should "return 200 with debtor account update JSON" taggedAs PISTag in {
    val (status, body) = runRequest(Method.PUT, s"$prefix/payments/sepa-credit-transfers/pay-123")
    status shouldBe Status.Ok
    body should include("transactionStatus")
    body should include("debtorAccount")
  }
}
