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
along with this program, if not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.concurrency

import code.bulkpayment.{BulkBatchReference, MappedBulkPaymentProvider}
import net.liftweb.common.{Failure, Full}
import net.liftweb.mapper.By

import java.util.UUID

/**
 * C1: BulkPayment batch-reference check-then-claim race.
 *
 * THE HAZARD:
 *   BulkPaymentHandler.validateEnvelope calls isBatchReferenceUsed (a SELECT), and only if the
 *   reference is absent does the handler proceed to create the TransactionRequest and fan-out
 *   the payment legs. After the payments are created, Http4s700 calls claimBatchReference
 *   (an INSERT guarded by UniqueIndex(FromBankId, FromAccountId, BatchReference)).
 *
 *   The race window is between the SELECT in isBatchReferenceUsed and the INSERT in
 *   claimBatchReference. Two concurrent requests with the same (bankId, accountId, batchRef)
 *   both pass the SELECT, both build and persist the TransactionRequest, then race to INSERT.
 *   The losing INSERT hits the UniqueIndex and returns a Failure Box — but in Http4s700.scala
 *   the result of claimBatchReference is dropped inside a bare Future { claimBatchReference(...) }
 *   with no unboxFullOrFail check, so the second request silently continues and double-charges.
 *
 * WHAT THIS TEST SHOWS (guard-verification — both scenarios pass):
 *   The provider layer is already sound: claimBatchReference wraps saveMe in tryo and the
 *   UniqueIndex(FromBankId, FromAccountId, BatchReference) makes the duplicate INSERT fail, so the
 *   losing caller receives a Failure (C1a) and exactly one row survives (C1b). These two scenarios
 *   prove the *signal* exists for the call site to act on.
 *
 *   The actual bug was at the call site: Http4s700.createTransactionRequestBulk ran
 *   `Future { claimBatchReference(...) }` and DROPPED the Box, so the losing request silently fanned
 *   out a duplicate payment. The fix (this PR) claims the batch_reference BEFORE creating the parent
 *   TR or fanning out, and surfaces the Failure with unboxFullOrFail (409) so the loser aborts early.
 *   The concurrent-duplicate HTTP path is additionally covered by the sequential 409-reuse scenario
 *   in Http4s700RoutesTest ("Return 409 when batch_reference is reused on the same source account").
 *
 * Tagged ConcurrencyRace.
 */
class ConcurrentBulkPaymentRaceTest extends ConcurrentRaceSetup {

  private val provider = MappedBulkPaymentProvider

  feature("BulkPayment batch-reference idempotency guard") {

    scenario("C1a: claimBatchReference must return Failure when the reference already exists (DB constraint works)", ConcurrencyRace) {
      Given("a batch-reference row already exists for (bank, account, ref)")
      val bankId    = "__conc_bulk_bank_" + UUID.randomUUID.toString.take(8)
      val accountId = "__conc_bulk_acc_"  + UUID.randomUUID.toString.take(8)
      val batchRef  = "__conc_bulk_ref_"  + UUID.randomUUID.toString.take(8)
      val trId1     = UUID.randomUUID.toString
      val trId2     = UUID.randomUUID.toString

      val first = provider.claimBatchReference(bankId, accountId, batchRef, trId1)

      When("a second claimBatchReference is attempted with the same (bank, account, ref)")
      val second = provider.claimBatchReference(bankId, accountId, batchRef, trId2)

      Then("the second call must return a Failure — the UniqueIndex prevents duplicate claims")
      withClue(
        s"first=$first second=$second: the UniqueIndex on (FromBankId, FromAccountId, BatchReference) " +
        s"should cause the second INSERT to fail — "
      ) {
        first  shouldBe a [Full[_]]
        second shouldBe a [Failure]
      }
    }

    scenario("C1b: concurrent isBatchReferenceUsed + claimBatchReference must not silently allow both to proceed", ConcurrencyRace) {
      Given("no existing BulkBatchReference row for a fresh (bank, account, batchRef)")
      val bankId    = "__conc_bulk2_bank_" + UUID.randomUUID.toString.take(8)
      val accountId = "__conc_bulk2_acc_"  + UUID.randomUUID.toString.take(8)
      val batchRef  = "__conc_bulk2_ref_"  + UUID.randomUUID.toString.take(8)
      val n         = 2

      def rowCount: Long = BulkBatchReference.count(
        By(BulkBatchReference.FromBankId,     bankId),
        By(BulkBatchReference.FromAccountId,  accountId),
        By(BulkBatchReference.BatchReference, batchRef)
      )

      When(s"$n threads concurrently check isBatchReferenceUsed then call claimBatchReference")
      // This reproduces the check-then-act window:
      //   Thread A: isBatchReferenceUsed → false (passes guard)
      //   Thread B: isBatchReferenceUsed → false (passes guard — A hasn't committed yet)
      //   Thread A: claimBatchReference  → Full   (INSERT succeeds)
      //   Thread B: claimBatchReference  → Failure (UniqueIndex violation)
      //   Bug: Http4s700 wraps claimBatchReference in Future { ... } without checking the Box,
      //        so Thread B's Failure is silently dropped and the duplicate payment proceeds.
      val results = runConcurrentWithBarrier(n) { i =>
        val alreadyUsed = provider.isBatchReferenceUsed(bankId, accountId, batchRef)
        if (!alreadyUsed) {
          provider.claimBatchReference(bankId, accountId, batchRef, UUID.randomUUID.toString)
        } else {
          Failure("reference already used — correctly rejected before INSERT")
        }
      }

      Then("exactly one claim must succeed; the other must return Failure (not be silently swallowed)")
      val successes = results.collect { case scala.util.Success(Full(_))    => "ok" }
      val failures  = results.collect { case scala.util.Success(f: Failure) => f.msg }
      val rows      = rowCount
      withClue(
        s"successes=${successes.size} failures=${failures.size} dbRows=$rows: " +
        s"both threads passed isBatchReferenceUsed because neither had committed yet — " +
        s"the second claimBatchReference hit UniqueIndex and returned Failure, but in " +
        s"Http4s700 this Failure is dropped inside Future { claimBatchReference(...) } " +
        s"with no unboxFullOrFail — the duplicate payment request silently proceeds — "
      ) {
        rows      should equal(1L)
        successes should have size 1
        failures  should have size (n - 1)
      }
    }
  }
}
