package code.api.UKOpenBanking.v4_0_1

import org.scalatest.Tag

// AUTO-GENERATED test suite for UK Open Banking Read/Write v4.0.1 (Events).
// Mirrors UKOpenBankingV310AisTests: one feature per endpoint, two scenarios
// (authenticated -> deterministic success code; unauthenticated -> 401).
// Endpoints are static spec-faithful scaffolds, so success codes are exact.
class UKOpenBankingV401EventsTests extends UKOpenBankingV401ServerSetup {

  object UKOpenBankingV401Events extends Tag("UKOpenBankingV401Events")
  val emptyBody = "{}"

  Feature("UKOB v4.0.1 GET /event-subscriptions") {
    Scenario("authenticated -> 200", UKOpenBankingV401Events) {
      getAuthed("event-subscriptions").code should equal(200)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401Events) {
      getUnauthed("event-subscriptions").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /event-subscriptions") {
    Scenario("authenticated -> 201", UKOpenBankingV401Events) {
      postAuthed(emptyBody, "event-subscriptions").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401Events) {
      postUnauthed(emptyBody, "event-subscriptions").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 PUT /event-subscriptions/EVENT_SUBSCRIPTION_ID") {
    Scenario("authenticated -> 201", UKOpenBankingV401Events) {
      putAuthed(emptyBody, "event-subscriptions", "fake-eventsubscriptionid").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401Events) {
      putUnauthed(emptyBody, "event-subscriptions", "fake-eventsubscriptionid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 DELETE /event-subscriptions/EVENT_SUBSCRIPTION_ID") {
    Scenario("authenticated -> 204", UKOpenBankingV401Events) {
      deleteAuthed("event-subscriptions", "fake-eventsubscriptionid").code should equal(204)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401Events) {
      deleteUnauthed("event-subscriptions", "fake-eventsubscriptionid").code should equal(401)
    }
  }
  Feature("UKOB v4.0.1 POST /events") {
    Scenario("authenticated -> 201", UKOpenBankingV401Events) {
      postAuthed(emptyBody, "events").code should equal(201)
    }
    Scenario("unauthenticated -> 401", UKOpenBankingV401Events) {
      postUnauthed(emptyBody, "events").code should equal(401)
    }
  }
}
