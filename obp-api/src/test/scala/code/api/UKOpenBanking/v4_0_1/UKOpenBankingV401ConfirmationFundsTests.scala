package code.api.UKOpenBanking.v4_0_1

import org.scalatest.Tag

// AUTO-GENERATED test suite for UK Open Banking Read/Write v4.0.1 (ConfirmationFunds).
// Mirrors UKOpenBankingV310AisTests: one feature per endpoint, two scenarios
// (authenticated -> deterministic success code; unauthenticated -> 401).
// Endpoints are static spec-faithful scaffolds, so success codes are exact.
class UKOpenBankingV401ConfirmationFundsTests extends UKOpenBankingV401ServerSetup {

  object UKOpenBankingV401ConfirmationFunds extends Tag("UKOpenBankingV401ConfirmationFunds")
  val emptyBody = "{}"

  Feature("UKOB v4.0.1 POST /cbpii/funds-confirmation-consents") {
    Scenario("authenticated -> 201", UKOpenBankingV401ConfirmationFunds) {
      postAuthed(emptyBody, "cbpii", "funds-confirmation-consents").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401ConfirmationFunds) {
      postUnauthed(emptyBody, "cbpii", "funds-confirmation-consents").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /cbpii/funds-confirmation-consents/CONSENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401ConfirmationFunds) {
      getAuthed("cbpii", "funds-confirmation-consents", "fake-consentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401ConfirmationFunds) {
      getUnauthed("cbpii", "funds-confirmation-consents", "fake-consentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 DELETE /cbpii/funds-confirmation-consents/CONSENT_ID") {
    Scenario("authenticated -> 204", UKOpenBankingV401ConfirmationFunds) {
      deleteAuthed("cbpii", "funds-confirmation-consents", "fake-consentid").code should equal(204)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401ConfirmationFunds) {
      deleteUnauthed("cbpii", "funds-confirmation-consents", "fake-consentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /cbpii/funds-confirmations") {
    Scenario("authenticated -> 201", UKOpenBankingV401ConfirmationFunds) {
      postAuthed(emptyBody, "cbpii", "funds-confirmations").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401ConfirmationFunds) {
      postUnauthed(emptyBody, "cbpii", "funds-confirmations").code should equal(401)
    }
  }
}
