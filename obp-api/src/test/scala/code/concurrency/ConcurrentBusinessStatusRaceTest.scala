package code.concurrency

import code.accountaccessrequest.{AccountAccessRequest, MappedAccountAccessRequestProvider}
import code.accountapplication.{MappedAccountApplication, MappedAccountApplicationProvider}
import code.transactionChallenge.MappedChallengeProvider
import com.openbankproject.commons.model.enums.AccountAccessRequestStatus
import com.openbankproject.commons.model.ProductCode
import net.liftweb.common.{Failure, Full}
import org.mindrot.jbcrypt.BCrypt

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * M1: Scheduler-path updateTransactionRequestStatus bypasses lockTransactionRequest (structural).
 * M2: AccountAccessRequest.updateStatus — no terminal state guard (unconditional find+saveMe).
 * M3: MappedAccountApplication.updateStatus — ACCEPTED guard is an in-memory check only.
 *
 * M1 (structural, not reproduced by concurrent test):
 *   Http4s400's challenge-answer path calls DoobieTransactionRequestQueries.lockTransactionRequest
 *   before processing payment — this acquires a DB-level row lock, making the INITIATED→COMPLETED
 *   transition atomic. BUT Http4s510's updateTransactionRequestStatus endpoint calls
 *   MappedTransactionRequestProvider.updateTransactionRequestStatus which does a plain
 *   find+mStatus(status).saveMe() with no lock. A concurrent scheduler call racing a challenge-
 *   answer can overwrite COMPLETED with a stale status, reversing a completed payment.
 *
 * M2 (testable):
 *   AccountAccessRequest.updateStatus does `AccountAccessRequest.find(…).flatMap { r => tryo { r.Status(status).saveMe() } }`.
 *   There is NO terminal-state guard — an already-APPROVED request can be flipped to DECLINED (or
 *   vice versa) by a concurrent or later admin action. This is a last-writer-wins race with no
 *   idempotency protection.
 *
 * M3 (testable):
 *   MappedAccountApplicationProvider.updateStatus checks `if accountApplication.status == "ACCEPTED"
 *   → Failure` as a guard against re-processing ACCEPTED applications. But the guard reads status
 *   from the in-memory object loaded by the find() call that precedes the check. Two concurrent
 *   calls, one wanting ACCEPTED and one wanting REJECTED, both load status="REQUESTED", both pass
 *   the guard, and both write — the last one wins non-deterministically. A legitimate ACCEPTED
 *   transition can be overwritten by a concurrent REJECTED.
 *
 * M3b (testable, deterministic):
 *   The sequential form of M3, and the reason M3 stayed intermittently red after the conditional
 *   UPDATE was introduced. That UPDATE guarded on the status the caller had just loaded, so it only
 *   caught the interleaving where both callers read the same old value. When the two calls serialise,
 *   the second one loads what the first wrote and its guard matches — the decision is overwritten
 *   with no error. M3b reproduces that with two ordinary sequential calls, no threads involved.
 *
 * All four are fixed. M2/M3/M3b/M4 now guard on a fixed starting state
 * (INITIATED / REQUESTED / successful_c=false) so each decision can only be taken once.
 * Tagged ConcurrencyRace.
 */
class ConcurrentBusinessStatusRaceTest extends ConcurrentRaceSetup {

  Feature("Business-object status transitions must be atomic") {

    Scenario("M2: concurrent approve and decline of the same AccountAccessRequest must not both succeed", ConcurrencyRace) {
      Given("an AccountAccessRequest in INITIATED state")
      val seeded = AccountAccessRequest.insert(
        bankId = "__conc_m2_bank", accountId = "__conc_m2_acc", viewId = "owner",
        isSystemView = false, requestorUserId = resourceUser1.userId,
        targetUserId = resourceUser2.userId, businessJustification = "concurrency test")
      val requestIdActual = seeded.accountAccessRequestId

      When("two threads concurrently update the request — one to APPROVED, one to DECLINED")
      val n       = 2
      val results = runConcurrentWithBarrier(n) { i =>
        val newStatus = if (i == 0) "APPROVED" else "DECLINED"
        MappedAccountAccessRequestProvider.updateStatus(requestIdActual, newStatus, resourceUser1.userId, "concurrent-test")
      }

      val finalStatus = AccountAccessRequest
        .findByAccountAccessRequestId(requestIdActual)
        .map(_.status).getOrElse("missing")

      Then("the final status must be a deterministic terminal value, not an overwritten intermediate")
      withClue(
        s"finalStatus=$finalStatus results=${results.map(_.isSuccess)}: " +
        s"AccountAccessRequest.updateStatus does unconditional find+saveMe with no terminal-state " +
        s"guard — concurrent APPROVED/DECLINED calls both succeed; the last writer silently wins, " +
        s"meaning a legitimate APPROVED decision can be overwritten by a racing DECLINED — "
      ) {
        // The test currently FAILS because both calls return Full and the final status is
        // non-deterministic. After the fix (conditional UPDATE WHERE status='INITIATED'),
        // exactly one must succeed and the other must return a Failure.
        val successes = results.collect { case scala.util.Success(Full(_)) => 1 }
        successes should have size 1
      }
    }

    Scenario("M3: concurrent ACCEPTED and REJECTED transitions to the same AccountApplication must not both proceed", ConcurrencyRace) {
      Given("an AccountApplication in REQUESTED state")
      // Created through the provider rather than the store: the application id is generated on
      // insert, and REQUESTED is the only status a new application may start in.
      val appId = Await.result(
        MappedAccountApplicationProvider.createAccountApplication(
          ProductCode("__conc_m3_product"), Some(resourceUser1.userId), Some(UUID.randomUUID.toString)),
        10.seconds).openOrThrowException("expected the account application just created")
        .accountApplicationId

      When("Thread A wants to ACCEPT and Thread B wants to REJECT — both race")
      // Both load status="REQUESTED" before either commits.
      // The memory guard `if status == "ACCEPTED" → Failure` does NOT fire for either thread,
      // because both loaded "REQUESTED" — neither has committed ACCEPTED yet.
      // Thread A writes ACCEPTED, Thread B writes REJECTED; one overwrites the other.
      val n       = 2
      val results = runConcurrentWithBarrier(n) { i =>
        val newStatus = if (i == 0) "ACCEPTED" else "REJECTED"
        Await.result(MappedAccountApplicationProvider.updateStatus(appId, newStatus), 10.seconds)
      }

      val finalStatus = MappedAccountApplication.findById(appId).map(_.status).getOrElse("missing")

      Then("exactly one transition must succeed — concurrent ACCEPTED+REJECTED must not both write")
      withClue(
        s"finalStatus=$finalStatus results=${results.map(_.isSuccess)}: " +
        s"MappedAccountApplicationProvider.updateStatus checks `if status == ACCEPTED → Failure` on " +
        s"the in-memory loaded object, not in the DB — both threads load REQUESTED, both pass the " +
        s"guard, and both write; the ACCEPTED→REJECTED overwrite is silent and undetected — "
      ) {
        val successes = results.collect { case scala.util.Success(Full(_)) => 1 }
        successes should have size 1
      }
    }

    Scenario("M3b: a REJECTED AccountApplication must not be silently re-decided as ACCEPTED", ConcurrencyRace) {
      Given("an AccountApplication in REQUESTED state")
      // Created through the provider rather than the store: the application id is generated on
      // insert, and REQUESTED is the only status a new application may start in.
      val appId = Await.result(
        MappedAccountApplicationProvider.createAccountApplication(
          ProductCode("__conc_m3b_product"), Some(resourceUser1.userId), Some(UUID.randomUUID.toString)),
        10.seconds).openOrThrowException("expected the account application just created")
        .accountApplicationId

      When("it is REJECTED and then a second decision tries to ACCEPT it")
      // The deterministic (sequential) form of the M3 race. M3's threads only both write when the
      // barrier releases them close enough together to both read REQUESTED; when they serialise, the
      // second call reads the status the first one wrote. A guard keyed on that freshly-read status
      // matches its own read and lets the overwrite through, so the outcome below is exactly what M3
      // observes intermittently — reproduced here with no threads and no timing dependency.
      val rejected = Await.result(MappedAccountApplicationProvider.updateStatus(appId, "REJECTED"), 10.seconds)
      val accepted = Await.result(MappedAccountApplicationProvider.updateStatus(appId, "ACCEPTED"), 10.seconds)

      val finalStatus = MappedAccountApplication.findById(appId).map(_.status).getOrElse("missing")

      Then("only the first decision may take effect — the application stays REJECTED")
      withClue(
        s"rejected=$rejected accepted=$accepted finalStatus=$finalStatus: " +
        s"the decision on an account application is one-shot — it may only be taken from REQUESTED. " +
        s"Accepting an already-REJECTED application also creates a bank account, so a silent " +
        s"re-decision is not recoverable — "
      ) {
        rejected shouldBe a[Full[_]]
        accepted shouldBe a[Failure]
        finalStatus should equal("REJECTED")
      }
    }

    // M1 (Http4s510 updateTransactionRequestStatus lacks the row lock that Http4s400 has) is fixed at
    // the endpoint: it now calls DoobieTransactionRequestQueries.lockTransactionRequest within the
    // request transaction. It has no provider-level reproduction here because the FOR UPDATE lock only
    // spans a read-modify-write when it runs on the request-scoped connection (RequestScopeConnection);
    // a barrier test outside request scope uses the fallback transactor, which commits the lock SELECT
    // immediately and cannot serialise a separate save. Documented in CONCURRENCY_HAZARDS.md.

    Scenario("M4: concurrent correct challenge answers must flip Successful exactly once — no MFA double-spend", ConcurrencyRace) {
      Given("a transaction-request challenge seeded with a known correct answer")
      // Raise the attempt limit so the limit-guard never short-circuits the success path.
      setPropsValues("transactionRequests_challenge_max_allowed_attempts" -> "100")
      val challengeId = UUID.randomUUID.toString
      val salt        = BCrypt.gensalt()
      MappedChallengeProvider.saveChallenge(
        challengeId            = challengeId,
        transactionRequestId   = UUID.randomUUID.toString,
        salt                   = salt,
        expectedAnswer         = BCrypt.hashpw("123", salt).substring(0, 44),
        expectedUserId         = resourceUser1.userId,
        scaMethod              = None,
        scaStatus              = None,
        consentId              = None,
        basketId               = None,
        authenticationMethodId = None,
        challengeType          = "OBP_TRANSACTION_REQUEST_CHALLENGE"
      )
      val n = 2

      When(s"$n threads concurrently submit the CORRECT answer to the same challenge")
      // validateChallenge does: in-memory hash check, then challenge.Successful(true).ScaStatus(finalised).saveMe().
      // The success flip is not a compare-and-set: both correct answers pass the check and both flip
      // Successful=true, so both callers are told the SCA succeeded → the payment can execute twice.
      val results = runConcurrentWithBarrier(n) { _ =>
        MappedChallengeProvider.validateChallenge(
          challengeId     = challengeId,
          challengeAnswer = "123",
          userId          = Some(resourceUser1.userId)
        )
      }

      Then("exactly one validate may succeed — the second must be rejected (challenge already answered)")
      val successes = results.collect { case scala.util.Success(Full(_)) => 1 }
      withClue(
        s"successes=${successes.size} results=${results.map(_.isSuccess)}: " +
        s"validateChallenge flips Successful(true) via a plain saveMe after an in-memory hash check — " +
        s"two correct concurrent answers both flip it and both return Full, green-lighting the payment " +
        s"twice. Fix: conditional UPDATE successful=true WHERE successful=false (CAS); the loser gets " +
        s"0 rows → Failure — "
      ) {
        successes should have size 1
      }
    }
  }
}
