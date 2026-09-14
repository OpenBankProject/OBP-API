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

// AUTO-GENERATED test suite for UK Open Banking Read/Write v4.0.1 (ConfirmationFunds).
// Mirrors UKOpenBankingV310AisTests: one feature per endpoint, two scenarios
// (authenticated -> deterministic success code; unauthenticated -> 401).
// Endpoints are static spec-faithful scaffolds, so success codes are exact.
class UKOpenBankingV401ConfirmationFundsTests extends UKOpenBankingV401ServerSetup {

  object UKOpenBankingV401ConfirmationFunds extends Tag("UKOpenBankingV401ConfirmationFunds")
  val emptyBody = "{}"

  feature("UKOB v4.0.1 POST /cbpii/funds-confirmation-consents") {
    scenario("authenticated -> 201", UKOpenBankingV401ConfirmationFunds) {
      postAuthed(emptyBody, "cbpii", "funds-confirmation-consents").code should equal(201)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401ConfirmationFunds) {
      postUnauthed(emptyBody, "cbpii", "funds-confirmation-consents").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /cbpii/funds-confirmation-consents/CONSENT_ID") {
    scenario("authenticated -> 200", UKOpenBankingV401ConfirmationFunds) {
      getAuthed("cbpii", "funds-confirmation-consents", "fake-consentid").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401ConfirmationFunds) {
      getUnauthed("cbpii", "funds-confirmation-consents", "fake-consentid").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 DELETE /cbpii/funds-confirmation-consents/CONSENT_ID") {
    scenario("authenticated -> 204", UKOpenBankingV401ConfirmationFunds) {
      deleteAuthed("cbpii", "funds-confirmation-consents", "fake-consentid").code should equal(204)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401ConfirmationFunds) {
      deleteUnauthed("cbpii", "funds-confirmation-consents", "fake-consentid").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 POST /cbpii/funds-confirmations") {
    scenario("authenticated -> 201", UKOpenBankingV401ConfirmationFunds) {
      postAuthed(emptyBody, "cbpii", "funds-confirmations").code should equal(201)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401ConfirmationFunds) {
      postUnauthed(emptyBody, "cbpii", "funds-confirmations").code should equal(401)
    }
  }
}
