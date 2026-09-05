package code.api.UKOpenBanking.v4_0_1

import org.scalatest.Tag

// AUTO-GENERATED test suite for UK Open Banking Read/Write v4.0.1 (EventNotifications).
// Mirrors UKOpenBankingV310AisTests: one feature per endpoint, two scenarios
// (authenticated -> deterministic success code; unauthenticated -> 401).
// Endpoints are static spec-faithful scaffolds, so success codes are exact.
class UKOpenBankingV401EventNotificationsTests extends UKOpenBankingV401ServerSetup {

  object UKOpenBankingV401EventNotifications extends Tag("UKOpenBankingV401EventNotifications")
  val emptyBody = "{}"

  Feature("UKOB v4.0.1 POST /event-notifications") {
    Scenario("authenticated -> 201", UKOpenBankingV401EventNotifications) {
      postAuthed(emptyBody, "event-notifications").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401EventNotifications) {
      postUnauthed(emptyBody, "event-notifications").code should equal(401)
    }
  }
}
