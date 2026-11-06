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

package code.setup

import org.scalatest.Assertions

/**
 * Whether a Redis-dependent check may cancel itself, or has to run.
 *
 * `assume(Redis.isRedisReady)` cancels wherever no Redis is listening, and a cancelled check is
 * indistinguishable from a passing one in every report anybody reads. and `run_tests_parallel.sh` starts none and probes for none, so the rate-limiter races
 * and the cache-invalidation checks skip on every local run while reporting green. CI does declare
 * a redis service, but nothing fails if that block is dropped or the container never becomes
 * healthy - the suite would go back to skipping, silently, and the log line saying so is one nobody
 * reads.
 *
 * What is lost when they skip is not incidental: these are the only checks that exercise the
 * rate-limiter's Redis fast path and MethodRouting's cache invalidation under concurrency. Both are
 * shared-state races, which is precisely the class of defect a green unit suite cannot rule out.
 *
 * `OBP_TEST_REDIS_REQUIRED=true` turns the cancellation into a failure. CI sets it alongside the
 * service container; developers leave it unset and keep the skip.
 */
object RedisTestTarget {

  /** True when a missing Redis must fail rather than cancel. */
  def required: Boolean =
    sys.env.get("OBP_TEST_REDIS_REQUIRED").exists(_.trim.equalsIgnoreCase("true"))

  /**
   * Cancel the test when Redis is absent and optional; fail it when absent and required; return
   * normally when it is there.
   *
   * `required` is a parameter rather than a direct read of the environment for the same reason it
   * is one on PostgresTestTarget: the environment cannot be changed from inside a running JVM, so a
   * branch that only an environment variable can reach is a branch no test can enter - and an
   * unreachable branch in a guard is the very thing the guard exists to stop.
   */
  def requireReachable(reachable: Boolean, what: String, required: Boolean = required): Unit =
    if (!reachable) {
      if (required) {
        Assertions.fail(
          s"OBP_TEST_REDIS_REQUIRED=true but Redis is not reachable, so $what cannot run. These " +
          "checks are the only cover for the rate limiter's Redis path and MethodRouting cache " +
          "invalidation under concurrency, so they must not be skipped where they are required - " +
          "start Redis, or unset OBP_TEST_REDIS_REQUIRED to go back to skipping.")
      } else {
        Assertions.cancel(s"Redis not reachable - skipping $what")
      }
    }
}
