package code.api.UKOpenBanking.v3_1_0

import org.scalatest.Tag

/**
 * UK Open Banking v3.1 — PIS (Payment Initiation) + Funds Confirmation family.
 *
 * All of these are stubs: the Lift handler calls authenticatedAccess(cc) and
 * returns a hard-coded mock JSON tuple (default 200). POST handlers ignore the
 * body, so any valid JSON ("{}") matches Lift's JsonPost extractor.
 *
 *   - authenticated   -> 200
 *   - unauthenticated -> 401
 *
 * Any handler that turns out to return a different status (e.g. 201/204) is
 * calibrated against the Lift baseline in Phase 1.
 */
class UKOpenBankingV310PisTests extends UKOpenBankingV310ServerSetup {

  object UKOpenBankingV310Pis extends Tag("UKOpenBankingV310Pis")

  private val emptyBody = "{}"
  private val fakeId = "fake-id"

  // Helper: assert authed -> 200 and unauthed -> 401 for a GET path.
  private def checkGet(segments: String*): Unit = {
    Scenario(s"GET /${segments.mkString("/")} authenticated -> 200", UKOpenBankingV310Pis) {
      getAuthed(segments: _*).code should equal(200)
    }
    Scenario(s"GET /${segments.mkString("/")} unauthenticated -> 401", UKOpenBankingV310Pis) {
      getUnauthed(segments: _*).code should equal(401)
    }
  }
  // Helper: assert authed -> 200 and unauthed -> 401 for a POST path.
  private def checkPost(segments: String*): Unit = {
    Scenario(s"POST /${segments.mkString("/")} authenticated -> 200", UKOpenBankingV310Pis) {
      postAuthed(emptyBody, segments: _*).code should equal(200)
    }
    Scenario(s"POST /${segments.mkString("/")} unauthenticated -> 401", UKOpenBankingV310Pis) {
      postUnauthed(emptyBody, segments: _*).code should equal(401)
    }
  }
  // Helper: assert authed -> 204 and unauthed -> 401 for a DELETE path.
  // (UK consent DELETE handlers return HttpCode.`204`.)
  private def checkDelete(segments: String*): Unit = {
    Scenario(s"DELETE /${segments.mkString("/")} authenticated -> 204", UKOpenBankingV310Pis) {
      deleteAuthed(segments: _*).code should equal(204)
    }
    Scenario(s"DELETE /${segments.mkString("/")} unauthenticated -> 401", UKOpenBankingV310Pis) {
      deleteUnauthed(segments: _*).code should equal(401)
    }
  }

  // ── DomesticPaymentsApi (5) ────────────────────────────────────────
  Feature("UKOB v3.1 Domestic Payments") {
    checkPost("domestic-payment-consents")
    checkPost("domestic-payments")
    checkGet("domestic-payment-consents", fakeId)
    checkGet("domestic-payment-consents", fakeId, "funds-confirmation")
    checkGet("domestic-payments", fakeId)
  }

  // ── DomesticScheduledPaymentsApi (4) ───────────────────────────────
  Feature("UKOB v3.1 Domestic Scheduled Payments") {
    checkPost("domestic-scheduled-payment-consents")
    checkPost("domestic-scheduled-payments")
    checkGet("domestic-scheduled-payment-consents", fakeId)
    checkGet("domestic-scheduled-payments", fakeId)
  }

  // ── DomesticStandingOrdersApi (4) ──────────────────────────────────
  Feature("UKOB v3.1 Domestic Standing Orders") {
    checkPost("domestic-standing-order-consents")
    checkPost("domestic-standing-orders")
    checkGet("domestic-standing-order-consents", fakeId)
    checkGet("domestic-standing-orders", fakeId)
  }

  // ── FilePaymentsApi (7) ────────────────────────────────────────────
  Feature("UKOB v3.1 File Payments") {
    checkPost("file-payment-consents")
    checkPost("file-payment-consents", fakeId, "file")
    checkPost("file-payments")
    checkGet("file-payment-consents", fakeId)
    checkGet("file-payment-consents", fakeId, "file")
    checkGet("file-payments", fakeId)
    checkGet("file-payments", fakeId, "report-file")
  }

  // ── FundsConfirmationsApi (4) ──────────────────────────────────────
  Feature("UKOB v3.1 Funds Confirmations") {
    checkPost("funds-confirmation-consents")
    checkPost("funds-confirmations")
    checkDelete("funds-confirmation-consents", fakeId)
    checkGet("funds-confirmation-consents", fakeId)
  }

  // ── InternationalPaymentsApi (5) ───────────────────────────────────
  Feature("UKOB v3.1 International Payments") {
    checkPost("international-payment-consents")
    checkPost("international-payments")
    checkGet("international-payment-consents", fakeId)
    checkGet("international-payment-consents", fakeId, "funds-confirmation")
    checkGet("international-payments", fakeId)
  }

  // ── InternationalScheduledPaymentsApi (5) ──────────────────────────
  Feature("UKOB v3.1 International Scheduled Payments") {
    checkPost("international-scheduled-payment-consents")
    checkPost("international-scheduled-payments")
    checkGet("international-scheduled-payment-consents", fakeId)
    checkGet("international-scheduled-payment-consents", fakeId, "funds-confirmation")
    checkGet("international-scheduled-payments", fakeId)
  }

  // ── InternationalStandingOrdersApi (4) ─────────────────────────────
  Feature("UKOB v3.1 International Standing Orders") {
    checkPost("international-standing-order-consents")
    checkPost("international-standing-orders")
    checkGet("international-standing-order-consents", fakeId)
    checkGet("international-standing-orders", fakeId)
  }

}
