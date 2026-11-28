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

import code.api.util.APIUtil

/**
 * Provider-/util-layer counter races that do not touch the DB.
 *
 *  AA. In-memory future counter lost-update — APIUtil.incrementFutureCounter reads the
 *      (calls, openFutures) tuple from a ConcurrentHashMap via getOrDefault, then put()s
 *      back tuple+1. getOrDefault and put are two separate CHM operations with no atomic
 *      compute/merge, so N concurrent increments each read the same starting tuple and
 *      overwrite each other — fewer increments land than calls made. This counter only
 *      drives open-futures back-off logging (no banking impact), so it is included for
 *      completeness; the same read-modify-write shape on a DB counter is what makes H/K
 *      dangerous.
 *
 * Asserts the correct count, so EXPECTED TO FAIL while the CHM access is non-atomic.
 * Tagged ConcurrencyRace.
 */
class ConcurrentProviderRaceTest extends ConcurrentRaceSetup {

  Feature("In-memory counter atomicity under concurrency") {

    Scenario("AA: N concurrent incrementFutureCounter calls must each land", ConcurrencyRace) {
      Given("a fresh service-counter key")
      val serviceName = "__conc_future_counter_aa"
      APIUtil.serviceNameCountersMap.remove(serviceName)
      val n = 8

      When(s"$n concurrent incrementFutureCounter calls hit the same key")
      runConcurrentWithBarrier(n) { _ =>
        APIUtil.incrementFutureCounter(serviceName)
      }

      Then("the call counter must equal N — every increment must land, no lost-updates")
      val (callCounter, _) = APIUtil.serviceNameCountersMap.getOrDefault(serviceName, (0, 0))
      withClue(
        s"callCounter=$callCounter (expected=$n): getOrDefault + put in incrementFutureCounter is a " +
        s"non-atomic read-modify-write on a ConcurrentHashMap; concurrent callers read the same tuple " +
        s"and overwrite each other — "
      ) {
        callCounter should equal(n)
      }
    }
  }
}
