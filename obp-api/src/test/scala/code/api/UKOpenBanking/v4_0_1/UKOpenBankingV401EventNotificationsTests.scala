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

// AUTO-GENERATED test suite for UK Open Banking Read/Write v4.0.1 (EventNotifications).
// Mirrors UKOpenBankingV310AisTests: one feature per endpoint, two scenarios
// (authenticated -> deterministic success code; unauthenticated -> 401).
// Endpoints are static spec-faithful scaffolds, so success codes are exact.
class UKOpenBankingV401EventNotificationsTests extends UKOpenBankingV401ServerSetup {

  object UKOpenBankingV401EventNotifications extends Tag("UKOpenBankingV401EventNotifications")
  val emptyBody = "{}"

  feature("UKOB v4.0.1 POST /event-notifications") {
    scenario("authenticated -> 201", UKOpenBankingV401EventNotifications) {
      postAuthed(emptyBody, "event-notifications").code should equal(201)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401EventNotifications) {
      postUnauthed(emptyBody, "event-notifications").code should equal(401)
    }
  }
}
