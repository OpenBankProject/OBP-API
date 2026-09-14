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

// AUTO-GENERATED test suite for UK Open Banking Read/Write v4.0.1 (Vrp).
// Mirrors UKOpenBankingV310AisTests: one feature per endpoint, two scenarios
// (authenticated -> deterministic success code; unauthenticated -> 401).
// Endpoints are static spec-faithful scaffolds, so success codes are exact.
class UKOpenBankingV401VrpTests extends UKOpenBankingV401ServerSetup {

  object UKOpenBankingV401Vrp extends Tag("UKOpenBankingV401Vrp")
  val emptyBody = "{}"

  feature("UKOB v4.0.1 POST /pisp/domestic-vrp-consents") {
    scenario("authenticated -> 201", UKOpenBankingV401Vrp) {
      postAuthed(emptyBody, "pisp", "domestic-vrp-consents").code should equal(201)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Vrp) {
      postUnauthed(emptyBody, "pisp", "domestic-vrp-consents").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /pisp/domestic-vrp-consents/CONSENT_ID") {
    scenario("authenticated -> 200", UKOpenBankingV401Vrp) {
      getAuthed("pisp", "domestic-vrp-consents", "fake-consentid").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Vrp) {
      getUnauthed("pisp", "domestic-vrp-consents", "fake-consentid").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 PUT /pisp/domestic-vrp-consents/CONSENT_ID") {
    scenario("authenticated -> 201", UKOpenBankingV401Vrp) {
      putAuthed(emptyBody, "pisp", "domestic-vrp-consents", "fake-consentid").code should equal(201)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Vrp) {
      putUnauthed(emptyBody, "pisp", "domestic-vrp-consents", "fake-consentid").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 DELETE /pisp/domestic-vrp-consents/CONSENT_ID") {
    scenario("authenticated -> 204", UKOpenBankingV401Vrp) {
      deleteAuthed("pisp", "domestic-vrp-consents", "fake-consentid").code should equal(204)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Vrp) {
      deleteUnauthed("pisp", "domestic-vrp-consents", "fake-consentid").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 PATCH /pisp/domestic-vrp-consents/CONSENT_ID") {
    scenario("authenticated -> 201", UKOpenBankingV401Vrp) {
      patchAuthed(emptyBody, "pisp", "domestic-vrp-consents", "fake-consentid").code should equal(201)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Vrp) {
      patchUnauthed(emptyBody, "pisp", "domestic-vrp-consents", "fake-consentid").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 POST /pisp/domestic-vrp-consents/CONSENT_ID/funds-confirmation") {
    scenario("authenticated -> 201", UKOpenBankingV401Vrp) {
      postAuthed(emptyBody, "pisp", "domestic-vrp-consents", "fake-consentid", "funds-confirmation").code should equal(201)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Vrp) {
      postUnauthed(emptyBody, "pisp", "domestic-vrp-consents", "fake-consentid", "funds-confirmation").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 POST /pisp/domestic-vrps") {
    scenario("authenticated -> 201", UKOpenBankingV401Vrp) {
      postAuthed(emptyBody, "pisp", "domestic-vrps").code should equal(201)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Vrp) {
      postUnauthed(emptyBody, "pisp", "domestic-vrps").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /pisp/domestic-vrps/DOMESTIC_V_R_P_ID") {
    scenario("authenticated -> 200", UKOpenBankingV401Vrp) {
      getAuthed("pisp", "domestic-vrps", "fake-domesticvrpid").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Vrp) {
      getUnauthed("pisp", "domestic-vrps", "fake-domesticvrpid").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /pisp/domestic-vrps/DOMESTIC_V_R_P_ID/payment-details") {
    scenario("authenticated -> 200", UKOpenBankingV401Vrp) {
      getAuthed("pisp", "domestic-vrps", "fake-domesticvrpid", "payment-details").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401Vrp) {
      getUnauthed("pisp", "domestic-vrps", "fake-domesticvrpid", "payment-details").code should equal(401)
    }
  }
}
