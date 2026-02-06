package code.api.berlin.group.v2

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.util.Helper.MdcLoggable
import org.http4s._
import org.http4s.implicits._
import org.scalatest.{FlatSpec, Matchers, Tag}

/**
 * Unit tests for Berlin Group v2 AIS endpoints.
 * Tests each of the 9 AIS endpoints returns correct HTTP status and JSON structure.
 * Validates: Requirements 1.1-1.5, 2.1-2.4
 */
class Http4sBGv2AISTest extends FlatSpec with Matchers with MdcLoggable {

  object AISTag extends Tag("BerlinGroupV2_AIS")

  private val routes = Http4sBGv2AIS.routes

  private def runRequest(method: Method, uri: String): (Status, String) = {
    val req = Request[IO](method, Uri.unsafeFromString(uri))
    val resp = routes.run(req).value.unsafeRunSync().getOrElse(Response[IO](Status.NotFound))
    val body = resp.bodyText.compile.string.unsafeRunSync()
    (resp.status, body)
  }

  // ── Account endpoints ─────────────────────────────────────────────

  "GET /v2/accounts" should "return 200 with account list JSON" taggedAs AISTag in {
    val (status, body) = runRequest(Method.GET, "/v2/accounts")
    status shouldBe Status.Ok
    body should include("accounts")
    body should include("resourceId")
    body should include("iban")
  }

  "GET /v2/accounts/{account-id}" should "return 200 with account details JSON" taggedAs AISTag in {
    val (status, body) = runRequest(Method.GET, "/v2/accounts/test-account-123")
    status shouldBe Status.Ok
    body should include("resourceId")
    body should include("test-account-123")
    body should include("cashAccountType")
  }

  "GET /v2/accounts/{account-id}/balances" should "return 200 with balance JSON" taggedAs AISTag in {
    val (status, body) = runRequest(Method.GET, "/v2/accounts/test-account-123/balances")
    status shouldBe Status.Ok
    body should include("balances")
    body should include("balanceAmount")
    body should include("balanceType")
  }

  "GET /v2/accounts/{account-id}/transactions" should "return 200 with transaction list JSON" taggedAs AISTag in {
    val (status, body) = runRequest(Method.GET, "/v2/accounts/test-account-123/transactions")
    status shouldBe Status.Ok
    body should include("booked")
    body should include("pending")
    body should include("transactionId")
  }

  "GET /v2/accounts/{account-id}/transactions/{txId}" should "return 200 with transaction details JSON" taggedAs AISTag in {
    val (status, body) = runRequest(Method.GET, "/v2/accounts/test-account-123/transactions/tx-456")
    status shouldBe Status.Ok
    body should include("transactionId")
    body should include("tx-456")
    body should include("transactionAmount")
  }

  // ── Card Account endpoints ────────────────────────────────────────

  "GET /v2/card-accounts" should "return 200 with card account list JSON" taggedAs AISTag in {
    val (status, body) = runRequest(Method.GET, "/v2/card-accounts")
    status shouldBe Status.Ok
    body should include("cardAccounts")
    body should include("maskedPan")
  }

  "GET /v2/card-accounts/{account-id}" should "return 200 with card account details JSON" taggedAs AISTag in {
    val (status, body) = runRequest(Method.GET, "/v2/card-accounts/card-123")
    status shouldBe Status.Ok
    body should include("resourceId")
    body should include("card-123")
    body should include("maskedPan")
  }

  "GET /v2/card-accounts/{account-id}/balances" should "return 200 with card balance JSON" taggedAs AISTag in {
    val (status, body) = runRequest(Method.GET, "/v2/card-accounts/card-123/balances")
    status shouldBe Status.Ok
    body should include("balances")
    body should include("balanceAmount")
  }

  "GET /v2/card-accounts/{account-id}/transactions" should "return 200 with card transaction list JSON" taggedAs AISTag in {
    val (status, body) = runRequest(Method.GET, "/v2/card-accounts/card-123/transactions")
    status shouldBe Status.Ok
    body should include("booked")
    body should include("transactionId")
  }
}
