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

import code.entitlement.MappedEntitlement
import code.model.dataAccess.MappedBankAccount
import code.setup.{APIResponse, DefaultUsers, OBPReq, ServerSetupWithTestData}
import com.openbankproject.commons.model.{AccountId, BankId}
import org.scalatest.Tag

import java.util.concurrent.{CyclicBarrier, Executors, TimeUnit}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

/**
 * Tag for the concurrency-race simulations.
 *
 * The business-write suites (A balance, B state-machine, C/D/F duplicate creation) assert
 * the THEORETICALLY-CORRECT outcome, so while the underlying read-modify-write / check-then-insert
 * races remain unfixed they are EXPECTED TO FAIL — the red bar, with its "expected vs actual" clue,
 * is the evidence the hazard is real. The connection-mechanism suites (G) instead VERIFY that an
 * already-implemented safeguard holds, so they are expected to pass; a red bar there is a regression.
 *
 * Either way these must be isolated from the CI main flow:
 *   run only these:   mvn ... scalatest:test -DtagsToInclude=code.concurrency.ConcurrencyRace -DfailIfNoTests=false
 *   exclude from CI:  mvn ... scalatest:test -DtagsToExclude=code.concurrency.ConcurrencyRace
 */
object ConcurrencyRace extends Tag("code.concurrency.ConcurrencyRace")

/**
 * Shared helpers for the concurrent-race suites: two fan-out primitives (HTTP and
 * provider-layer) plus direct-DB state assertions that bypass any read cache.
 *
 * Pitfalls these suites must respect (see the plan file for the full list):
 *  - The test DB is H2 in-memory. Application-level read-modify-write / check-then-insert races
 *    do NOT depend on DB isolation and CAN reproduce on H2, but H2's table locks may serialise
 *    some writes and lower the hit rate — assertions print the observed values so a red bar is
 *    self-documenting; raise N or use runConcurrentWithBarrier if a run comes back spuriously green.
 *  - The whole JVM shares one server, one H2 DB and one Hikari pool (forkMode=once). Use dedicated
 *    bank/account/user ids and keep the concurrency count modest (≤ ~30) so the pool is not
 *    exhausted for sibling suites.
 *  - Concurrent use of the shared OkHttp client can briefly corrupt a pooled connection; retries
 *    are handled by OBPReq / SendServerRequests.
 */
trait ConcurrentRaceSetup extends ServerSetupWithTestData with DefaultUsers {

  // Future.sequence below only schedules the join; each async request helper in
  // SendServerRequests carries its own ExecutionContext for the actual HTTP I/O.
  private implicit val raceEc: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  def v4_0_0_Request: OBPReq = baseRequest / "obp" / "v4.0.0"
  def v3_0_0_Request: OBPReq = baseRequest / "obp" / "v3.0.0"
  def v2_0_0_Request: OBPReq = baseRequest / "obp" / "v2.0.0"

  /** System owner view — present on every test account, carries all read permissions. */
  val SystemOwnerViewId = "owner"

  /**
   * Build `n` requests with `mk` and run them concurrently over the shared HTTP client,
   * awaiting all results. `mk` is invoked once per index, so each request is constructed
   * and (when the caller applies `<@`) OAuth-signed independently — a distinct nonce per
   * request. This is a real parallel fan-out, not one signed request replayed n times
   * (which the server's nonce check would reject).
   */
  def fireConcurrently[T](n: Int, timeout: FiniteDuration = 90.seconds)(mk: Int => Future[T]): List[T] =
    Await.result(Future.sequence((0 until n).map(mk)), timeout).toList

  /**
   * Run `task` on `n` dedicated threads that all wait at a barrier before entering the
   * critical section together, so concurrent check-then-act windows actually overlap
   * (H2's table locks otherwise tend to serialise un-barriered writes and hide the race).
   * Each invocation's result is wrapped in a Try, so a constraint violation or thrown
   * exception is observable rather than aborting the whole fan-out.
   *
   * Used for provider-layer races whose contended code is a getOrCreate method rather
   * than an HTTP endpoint (account holders, counterparty metadata).
   */
  def runConcurrentWithBarrier[T](n: Int, timeout: FiniteDuration = 60.seconds)(task: Int => T): List[Try[T]] = {
    val pool    = Executors.newFixedThreadPool(n)
    val taskEc  = scala.concurrent.ExecutionContext.fromExecutorService(pool)
    val barrier = new CyclicBarrier(n)
    try {
      val futs = (0 until n).map { i =>
        Future {
          barrier.await(timeout.toMillis, TimeUnit.MILLISECONDS)
          Try(task(i))
        }(taskEc)
      }
      Await.result(Future.sequence(futs), timeout).toList
    } finally {
      pool.shutdownNow()
      ()
    }
  }

  /** Balance persisted on the account row, read straight from the DB (no cache, no HTTP). */
  def dbAccountBalance(bankId: BankId, accountId: AccountId): Long =
    MappedBankAccount
      .find(bankId.value, accountId.value)
      .map(_.accountBalance)
      .getOrElse(fail(s"account row not found: ${bankId.value}/${accountId.value}"))

  /** Number of entitlement rows for one (bank,user,role) triple, straight from the DB. */
  def dbEntitlementCount(bankId: String, userId: String, roleName: String): Long =
    MappedEntitlement.count(bankId, userId, roleName)
}
