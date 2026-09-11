package code.api.UKOpenBanking.v4_0_1

import org.scalatest.Tag

// AUTO-GENERATED test suite for UK Open Banking Read/Write v4.0.1 (PaymentInitiation).
// Mirrors UKOpenBankingV310AisTests: one feature per endpoint, two scenarios
// (authenticated -> deterministic success code; unauthenticated -> 401).
// Endpoints are static spec-faithful scaffolds, so success codes are exact.
class UKOpenBankingV401PaymentInitiationTests extends UKOpenBankingV401ServerSetup {

  object UKOpenBankingV401PaymentInitiation extends Tag("UKOpenBankingV401PaymentInitiation")
  val emptyBody = "{}"

  Feature("UKOB v4.0.1 POST /pisp/domestic-payment-consents") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "domestic-payment-consents").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "domestic-payment-consents").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/domestic-payment-consents/CONSENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "domestic-payment-consents", "fake-consentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "domestic-payment-consents", "fake-consentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/domestic-payment-consents/CONSENT_ID/funds-confirmation") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "domestic-payment-consents", "fake-consentid", "funds-confirmation").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "domestic-payment-consents", "fake-consentid", "funds-confirmation").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/domestic-payments") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "domestic-payments").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "domestic-payments").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/domestic-payments/DOMESTIC_PAYMENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "domestic-payments", "fake-domesticpaymentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "domestic-payments", "fake-domesticpaymentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/domestic-payments/DOMESTIC_PAYMENT_ID/payment-details") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "domestic-payments", "fake-domesticpaymentid", "payment-details").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "domestic-payments", "fake-domesticpaymentid", "payment-details").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/domestic-scheduled-payment-consents") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "domestic-scheduled-payment-consents").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "domestic-scheduled-payment-consents").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/domestic-scheduled-payment-consents/CONSENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "domestic-scheduled-payment-consents", "fake-consentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "domestic-scheduled-payment-consents", "fake-consentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/domestic-scheduled-payments") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "domestic-scheduled-payments").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "domestic-scheduled-payments").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/domestic-scheduled-payments/DOMESTIC_SCHEDULED_PAYMENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "domestic-scheduled-payments", "fake-domesticscheduledpaymentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "domestic-scheduled-payments", "fake-domesticscheduledpaymentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/domestic-scheduled-payments/DOMESTIC_SCHEDULED_PAYMENT_ID/payment-details") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "domestic-scheduled-payments", "fake-domesticscheduledpaymentid", "payment-details").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "domestic-scheduled-payments", "fake-domesticscheduledpaymentid", "payment-details").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/domestic-standing-order-consents") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "domestic-standing-order-consents").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "domestic-standing-order-consents").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/domestic-standing-order-consents/CONSENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "domestic-standing-order-consents", "fake-consentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "domestic-standing-order-consents", "fake-consentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/domestic-standing-orders") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "domestic-standing-orders").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "domestic-standing-orders").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/domestic-standing-orders/DOMESTIC_STANDING_ORDER_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "domestic-standing-orders", "fake-domesticstandingorderid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "domestic-standing-orders", "fake-domesticstandingorderid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/domestic-standing-orders/DOMESTIC_STANDING_ORDER_ID/payment-details") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "domestic-standing-orders", "fake-domesticstandingorderid", "payment-details").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "domestic-standing-orders", "fake-domesticstandingorderid", "payment-details").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/file-payment-consents") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "file-payment-consents").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "file-payment-consents").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/file-payment-consents/CONSENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "file-payment-consents", "fake-consentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "file-payment-consents", "fake-consentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/file-payment-consents/CONSENT_ID/file") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "file-payment-consents", "fake-consentid", "file").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "file-payment-consents", "fake-consentid", "file").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/file-payment-consents/CONSENT_ID/file") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "file-payment-consents", "fake-consentid", "file").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "file-payment-consents", "fake-consentid", "file").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/file-payments") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "file-payments").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "file-payments").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/file-payments/FILE_PAYMENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "file-payments", "fake-filepaymentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "file-payments", "fake-filepaymentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/file-payments/FILE_PAYMENT_ID/payment-details") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "file-payments", "fake-filepaymentid", "payment-details").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "file-payments", "fake-filepaymentid", "payment-details").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/file-payments/FILE_PAYMENT_ID/report-file") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "file-payments", "fake-filepaymentid", "report-file").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "file-payments", "fake-filepaymentid", "report-file").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/international-payment-consents") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "international-payment-consents").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "international-payment-consents").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/international-payment-consents/CONSENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "international-payment-consents", "fake-consentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "international-payment-consents", "fake-consentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/international-payment-consents/CONSENT_ID/funds-confirmation") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "international-payment-consents", "fake-consentid", "funds-confirmation").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "international-payment-consents", "fake-consentid", "funds-confirmation").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/international-payments") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "international-payments").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "international-payments").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/international-payments/INTERNATIONAL_PAYMENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "international-payments", "fake-internationalpaymentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "international-payments", "fake-internationalpaymentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/international-payments/INTERNATIONAL_PAYMENT_ID/payment-details") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "international-payments", "fake-internationalpaymentid", "payment-details").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "international-payments", "fake-internationalpaymentid", "payment-details").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/international-scheduled-payment-consents") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "international-scheduled-payment-consents").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "international-scheduled-payment-consents").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/international-scheduled-payment-consents/CONSENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "international-scheduled-payment-consents", "fake-consentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "international-scheduled-payment-consents", "fake-consentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/international-scheduled-payment-consents/CONSENT_ID/funds-confirmation") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "international-scheduled-payment-consents", "fake-consentid", "funds-confirmation").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "international-scheduled-payment-consents", "fake-consentid", "funds-confirmation").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/international-scheduled-payments") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "international-scheduled-payments").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "international-scheduled-payments").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/international-scheduled-payments/INTERNATIONAL_SCHEDULED_PAYMENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "international-scheduled-payments", "fake-internationalscheduledpaymentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "international-scheduled-payments", "fake-internationalscheduledpaymentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/international-scheduled-payments/INTERNATIONAL_SCHEDULED_PAYMENT_ID/payment-details") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "international-scheduled-payments", "fake-internationalscheduledpaymentid", "payment-details").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "international-scheduled-payments", "fake-internationalscheduledpaymentid", "payment-details").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/international-standing-order-consents") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "international-standing-order-consents").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "international-standing-order-consents").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/international-standing-order-consents/CONSENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "international-standing-order-consents", "fake-consentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "international-standing-order-consents", "fake-consentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /pisp/international-standing-orders") {
    Scenario("authenticated -> 201", UKOpenBankingV401PaymentInitiation) {
      postAuthed(emptyBody, "pisp", "international-standing-orders").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      postUnauthed(emptyBody, "pisp", "international-standing-orders").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/international-standing-orders/INTERNATIONAL_STANDING_ORDER_PAYMENT_ID") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "international-standing-orders", "fake-internationalstandingorderpaymentid").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "international-standing-orders", "fake-internationalstandingorderpaymentid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 GET /pisp/international-standing-orders/INTERNATIONAL_STANDING_ORDER_PAYMENT_ID/payment-details") {
    Scenario("authenticated -> 200", UKOpenBankingV401PaymentInitiation) {
      getAuthed("pisp", "international-standing-orders", "fake-internationalstandingorderpaymentid", "payment-details").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401PaymentInitiation) {
      getUnauthed("pisp", "international-standing-orders", "fake-internationalstandingorderpaymentid", "payment-details").code should equal(401)
    }
  }
}
