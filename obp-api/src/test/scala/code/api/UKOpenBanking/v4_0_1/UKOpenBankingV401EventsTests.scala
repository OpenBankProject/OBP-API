/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.UKOpenBanking.v4_0_1

import org.scalatest.Tag

// AUTO-GENERATED test suite for UK Open Banking Read/Write v4.0.1 (Events).
// Mirrors UKOpenBankingV310AisTests: one feature per endpoint, two scenarios
// (authenticated -> deterministic success code; unauthenticated -> 401).
// Endpoints are static spec-faithful scaffolds, so success codes are exact.
class UKOpenBankingV401EventsTests extends UKOpenBankingV401ServerSetup {

  object UKOpenBankingV401Events extends Tag("UKOpenBankingV401Events")
  val emptyBody = "{}"

  feature("UKOB v4.0.1 GET /event-subscriptions") {
    scenario("authenticated -> 200", UKOpenBankingV401Events) {
      getAuthed("event-subscriptions").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Events) {
      getUnauthed("event-subscriptions").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 POST /event-subscriptions") {
    scenario("authenticated -> 201", UKOpenBankingV401Events) {
      postAuthed(emptyBody, "event-subscriptions").code should equal(201)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Events) {
      postUnauthed(emptyBody, "event-subscriptions").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 PUT /event-subscriptions/EVENT_SUBSCRIPTION_ID") {
    scenario("authenticated -> 201", UKOpenBankingV401Events) {
      putAuthed(emptyBody, "event-subscriptions", "fake-eventsubscriptionid").code should equal(201)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Events) {
      putUnauthed(emptyBody, "event-subscriptions", "fake-eventsubscriptionid").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 DELETE /event-subscriptions/EVENT_SUBSCRIPTION_ID") {
    scenario("authenticated -> 204", UKOpenBankingV401Events) {
      deleteAuthed("event-subscriptions", "fake-eventsubscriptionid").code should equal(204)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Events) {
      deleteUnauthed("event-subscriptions", "fake-eventsubscriptionid").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 POST /events") {
    scenario("authenticated -> 201", UKOpenBankingV401Events) {
      postAuthed(emptyBody, "events").code should equal(201)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Events) {
      postUnauthed(emptyBody, "events").code should equal(401)
    }
  }
}
