/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package code.concurrency

import code.api.util.APIUtil.OAuth._

import scala.concurrent.duration._

/**
 * Verifies the request-scoped connection machinery (RequestScopeConnection + the Hikari pool)
 * holds up under concurrency. Unlike the A–F business-write suites, these are expected to PASS:
 * they confirm an already-implemented safeguard, so a red bar here signals a regression, not a
 * newly-surfaced hazard.
 *
 *  G1. Pool back-pressure — firing more concurrent requests than the pool size must queue and
 *      complete, never deadlock or surface a pool-exhaustion 500.
 *  G2. Per-request context isolation — under load, every request must read back its OWN
 *      authenticated context, exercising the childValue=null guard that stops a worker thread
 *      from inheriting another request's connection proxy (RequestScopeConnection.scala).
 */
class ConcurrentConnectionMechanismTest extends ConcurrentRaceSetup {

  feature("Request-scoped connection management under concurrency") {

    scenario("G1: concurrent requests exceeding the pool must all complete (queue, not deadlock)", ConcurrencyRace) {
      Given("more concurrent authenticated requests than the hikari pool size (test pool = 20)")
      val n = 30

      When(s"$n GET /users/current are fired at once")
      val responses = fireConcurrently(n, 120.seconds) { _ =>
        makeGetRequestAsync((v4_0_0_Request / "users" / "current").GET <@ user1)
      }

      Then("all must complete with HTTP 200 — none time out or fail with pool exhaustion")
      val byCode = responses.groupBy(_.code).map { case (k, v) => k -> v.size }
      withClue(s"status distribution=$byCode (expected all 200) — ") {
        responses.size should equal(n)
        responses.foreach(r => r.code should equal(200))
      }
    }

    scenario("G2: high concurrency must not bleed request context across connections", ConcurrencyRace) {
      Given("many concurrent GET /users/current as user1")
      val n              = 20
      val expectedUserId = resourceUser1.userId

      When(s"$n requests read the current-user context concurrently")
      val responses = fireConcurrently(n, 120.seconds) { _ =>
        makeGetRequestAsync((v4_0_0_Request / "users" / "current").GET <@ user1)
      }

      Then("every response must be 200 and carry user1's own user_id (no stale/bled context)")
      val bad = responses.filterNot { r =>
        r.code == 200 && (r.body \ "user_id").values.toString == expectedUserId
      }
      withClue(s"responses with wrong/missing user_id or non-200: " +
        s"${bad.map(r => r.code -> (r.body \ "user_id").values.toString)} — ") {
        bad shouldBe empty
      }
    }
  }
}
