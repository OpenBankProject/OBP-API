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
package code.concurrency

import code.api.JedisMethod
import code.api.cache.Redis
import code.api.cache.Redis.{QuotaCounted, QuotaDecision, QuotaRefused}
import code.setup.RedisTestTarget

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Redis.quotaCheckAndCount: the one Lua script behind the consumer quota
 * (RateLimitingUtil.underCallLimits). It checks every period's limit and, only when all pass,
 * counts the call in every period, atomically. These scenarios pin the contract the Scala side
 * relies on and close hazard X of CONCURRENCY_HAZARDS.md (the former GET-then-INCR gate).
 *
 * Needs a reachable Redis, like ConcurrentRateLimiterRaceTest.
 */
class ConcurrentConsumerQuotaScriptTest extends ConcurrentRaceSetup {

  private def redisUp: Boolean = Redis.isRedisReady

  private def freshKeys(n: Int): List[String] = {
    val base = "__conc_quota_" + UUID.randomUUID.toString.take(8) + "_"
    (1 to n).map(i => base + i).toList
  }

  private def count(key: String): Long = Redis.use(JedisMethod.GET, key).map(_.toLong).getOrElse(0L)
  private def ttl(key: String): Long = Redis.use(JedisMethod.TTL, key).map(_.toLong).getOrElse(-2L)

  private def call(keys: List[String], limits: List[Long], seconds: List[Int]): QuotaDecision =
    Redis.quotaCheckAndCount(keys, limits, seconds)

  feature("Redis.quotaCheckAndCount checks every period and counts the call in one atomic script") {

    scenario("Q1: a call under every limit is counted in every period, unlimited periods included", ConcurrencyRace) {
      RedisTestTarget.requireReachable(redisUp, "Q1")
      Given("three periods: unlimited, 100 per 60s, 1000 per 3600s, counters absent")
      val keys = freshKeys(3)
      When("one call is checked and counted")
      val decision = call(keys, List(-1L, 100L, 1000L), List(1, 60, 3600))
      Then("it is counted, and every key holds 1 with its period as TTL")
      decision match {
        case QuotaCounted(countsAndTtls) =>
          countsAndTtls.map(_._1) should equal(List(1L, 1L, 1L))
          countsAndTtls.map(_._2).zip(List(1, 60, 3600)).foreach { case (t, p) => t should (be > 0L and be <= p.toLong) }
        case other => fail(s"expected QuotaCounted, got $other")
      }
      keys.map(count) should equal(List(1L, 1L, 1L))
      And("a second call makes them 2")
      call(keys, List(-1L, 100L, 1000L), List(1, 60, 3600)) match {
        case QuotaCounted(countsAndTtls) => countsAndTtls.map(_._1) should equal(List(2L, 2L, 2L))
        case other => fail(s"expected QuotaCounted, got $other")
      }
    }

    scenario("Q2: the shortest exhausted period refuses, with its count and TTL, and nothing is counted", ConcurrencyRace) {
      RedisTestTarget.requireReachable(redisUp, "Q2")
      Given("two periods limited to 2 per 60s and 2 per 3600s, each counter already at 2")
      val keys = freshKeys(2)
      call(keys, List(2L, 2L), List(60, 3600))
      call(keys, List(2L, 2L), List(60, 3600))
      When("a third call is checked")
      val decision = call(keys, List(2L, 2L), List(60, 3600))
      Then("the first (shortest) period refuses, reporting a count of 2 and a live TTL")
      decision match {
        case QuotaRefused(index, c, t) =>
          index should equal(0)
          c should equal(2L)
          t should (be > 0L and be <= 60L)
        case other => fail(s"expected QuotaRefused, got $other")
      }
      And("neither counter moved: a refused call costs nothing")
      keys.map(count) should equal(List(2L, 2L))
    }

    scenario("Q3: a later period at its limit refuses even though earlier periods have room", ConcurrencyRace) {
      RedisTestTarget.requireReachable(redisUp, "Q3")
      Given("per-minute 100 and per-hour 1, with one call already counted")
      val keys = freshKeys(2)
      call(keys, List(100L, 1L), List(60, 3600))
      When("a second call is checked")
      val decision = call(keys, List(100L, 1L), List(60, 3600))
      Then("the per-hour period (index 1) refuses and the per-minute counter stays at 1")
      decision match {
        case QuotaRefused(index, c, _) =>
          index should equal(1)
          c should equal(1L)
        case other => fail(s"expected QuotaRefused, got $other")
      }
      keys.map(count) should equal(List(1L, 1L))
    }

    scenario("Q4: a limit of 0 refuses before reading the value, and reports a TTL of -2 for an absent key", ConcurrencyRace) {
      RedisTestTarget.requireReachable(redisUp, "Q4")
      Given("an unlimited per-second period and a blocked per-minute period, no counters")
      val keys = freshKeys(2)
      When("a call is checked")
      val decision = call(keys, List(-1L, 0L), List(1, 60))
      Then("the blocked period refuses with count 0 and the TTL Redis reports for a missing key")
      decision should equal(QuotaRefused(1, 0L, -2L))
      And("the unlimited period was not counted either")
      keys.map(count) should equal(List(0L, 0L))
    }

    scenario("Q5: a counter without an expiry is treated as expired on the check and recreated on the count", ConcurrencyRace) {
      RedisTestTarget.requireReachable(redisUp, "Q5")
      Given("a counter stuck at 50 with no TTL under a limit of 10")
      val keys = freshKeys(1)
      Redis.use(JedisMethod.SET, keys.head, value = Some("50"))
      ttl(keys.head) should equal(-1L)
      When("a call is checked and counted")
      val decision = call(keys, List(10L), List(60))
      Then("it is served (the stuck key counts as 0) and the key is recreated at 1 with a TTL")
      decision match {
        case QuotaCounted((c, t) :: Nil) =>
          c should equal(1L)
          t should (be > 0L and be <= 60L)
        case other => fail(s"expected QuotaCounted, got $other")
      }
      count(keys.head) should equal(1L)
    }

    scenario("Q6: at most `limit` of many concurrent callers pass the gate (hazard X closed)", ConcurrencyRace) {
      RedisTestTarget.requireReachable(redisUp, "Q6")
      Given("a per-minute limit of 5, an unlimited per-hour period, and 20 concurrent callers")
      val keys  = freshKeys(2)
      val limit = 5L
      val n     = 20
      val passed = new AtomicInteger(0)
      When(s"$n threads check-and-count through the script at the same time")
      // Before this script the gate was GET (check) then INCR (count) as two round trips, so many
      // callers read the same low count, all passed `count + 1 <= limit`, and all incremented.
      val results = runConcurrentWithBarrier(n) { _ =>
        call(keys, List(limit, -1L), List(60, 3600)) match {
          case QuotaCounted(_) => passed.incrementAndGet(); true
          case QuotaRefused(_, _, _) => false
        }
      }
      Then(s"exactly $limit callers were served and the counters hold exactly $limit")
      results.count(_.isFailure) should equal(0)
      withClue(s"passed=${passed.get()} limit=$limit: the check and the count run in one atomic script, so no interleaving can let more than `limit` through - ") {
        passed.get().toLong should equal(limit)
      }
      keys.map(count) should equal(List(limit, limit))
    }
  }
}
