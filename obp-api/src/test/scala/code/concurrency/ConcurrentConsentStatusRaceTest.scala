package code.concurrency

import code.consent.{ConsentStatus, MappedConsent, MappedConsentProvider}
import code.context.{MappedUserAuthContextUpdate, MappedUserAuthContextUpdateProvider}
import net.liftweb.common.Full
import net.liftweb.mapper.By
import org.mindrot.jbcrypt.BCrypt

import java.util.{Date, UUID}
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * H1: MappedConsent.checkAnswer TOCTOU race.
 * H2: MappedUserAuthContextUpdate.checkAnswer TOCTOU race.
 * H3: MappedConsent.revoke vs concurrent checkAnswer race.
 *
 * THE HAZARD (all three share the same root cause):
 *   The status check ("is this consent INITIATED?") and the status write ("flip to ACCEPTED/REJECTED/REVOKED")
 *   are two separate SQL operations with no SELECT FOR UPDATE or conditional UPDATE between them.
 *   Two concurrent requests can both read INITIATED, both pass the guard, and both write a new status.
 *
 *   H1 / H2: Two concurrent correct answers → both callers get Full(consent_ACCEPTED) and proceed
 *   as if they independently answered a challenge. In a real SCA flow this means a single
 *   consent is double-authorised — both callers proceed past the challenge gate.
 *
 *   H3: A revoke call and a checkAnswer call race. The revoker writes REVOKED; the answerer loaded
 *   a stale INITIATED object and writes ACCEPTED on top, resurrecting the revoked consent.
 *
 * EXPECTED TO FAIL (all three) until a conditional UPDATE WHERE status='INITIATED' is used.
 * Tagged ConcurrencyRace.
 */
class ConcurrentConsentStatusRaceTest extends ConcurrentRaceSetup {

  private def mkConsent(answer: String): (String, String) = {
    val salt      = BCrypt.gensalt()
    val hashed    = BCrypt.hashpw(answer, salt).substring(0, 44)
    val consentId = UUID.randomUUID.toString
    MappedConsent.create
      .mConsentId(consentId)
      .mStatus(ConsentStatus.INITIATED.toString)
      .mChallenge(hashed)
      .mSalt(salt)
      .saveMe()
    (consentId, answer)
  }

  private def mkUserAuthContextUpdate(answer: String): String = {
    val id = UUID.randomUUID.toString
    MappedUserAuthContextUpdate.create
      .mUserAuthContextUpdateId(id)
      .mUserId(resourceUser1.userId)
      .mConsumerId("__conc_consumer")
      .mKey("__conc_key")
      .mValue("__conc_value")
      .mChallenge(answer)
      .mStatus(com.openbankproject.commons.model.UserAuthContextUpdateStatus.INITIATED.toString)
      .saveMe()
    id
  }

  private def consentStatus(consentId: String): String =
    MappedConsent.find(By(MappedConsent.mConsentId, consentId))
      .map(_.status).getOrElse("missing")

  private def uacStatus(id: String): String =
    MappedUserAuthContextUpdate.find(By(MappedUserAuthContextUpdate.mUserAuthContextUpdateId, id))
      .map(_.status).getOrElse("missing")

  feature("Consent and UserAuthContextUpdate status transitions must be atomic") {

    scenario("H1: two concurrent correct answers to the same consent must not both succeed", ConcurrencyRace) {
      Given("a consent in INITIATED state with a known challenge answer")
      val (consentId, answer) = mkConsent("test-answer-h1")

      When("2 threads concurrently submit the correct answer")
      // Both threads load INITIATED, both pass the guard, both write ACCEPTED.
      // The hazard is that both return Full — two callers get the green light.
      val results = runConcurrentWithBarrier(2) { _ =>
        MappedConsentProvider.checkAnswer(consentId, answer)
      }

      Then("exactly one call should succeed; the other must get a non-INITIATED rejection")
      val successes = results.collect { case scala.util.Success(Full(_)) => 1 }
      val finalSt   = consentStatus(consentId)
      withClue(
        s"successes=${successes.size} finalStatus=$finalSt: " +
        s"MappedConsent.checkAnswer reads status and writes new status as two separate SQL operations " +
        s"with no SELECT FOR UPDATE — both threads pass the INITIATED guard before either commits, " +
        s"both get Full(ACCEPTED) and proceed through the SCA gate — "
      ) {
        successes should have size 1
        finalSt   should equal(ConsentStatus.ACCEPTED.toString)
      }
    }

    scenario("H2: two concurrent correct answers to the same UserAuthContextUpdate must not both succeed", ConcurrencyRace) {
      Given("a UserAuthContextUpdate in INITIATED state with known plain-text challenge")
      // mChallenge is VARCHAR(10) — keep the answer within the column limit.
      val answer = "h2ans"
      val updateId = mkUserAuthContextUpdate(answer)

      When("2 threads concurrently submit the correct challenge")
      val results = runConcurrentWithBarrier(2) { _ =>
        Await.result(MappedUserAuthContextUpdateProvider.checkAnswer(updateId, answer), 10.seconds)
      }

      Then("exactly one must succeed; the race must not allow double-authorisation")
      val successes = results.collect { case scala.util.Success(Full(_)) => 1 }
      val finalSt   = uacStatus(updateId)
      withClue(
        s"successes=${successes.size} finalStatus=$finalSt: " +
        s"MappedUserAuthContextUpdateProvider.checkAnswer checks status then writes status in two " +
        s"separate SQL operations — both threads see INITIATED and both write ACCEPTED — "
      ) {
        successes should have size 1
        finalSt   should equal(com.openbankproject.commons.model.UserAuthContextUpdateStatus.ACCEPTED.toString)
      }
    }

    scenario("H3: a concurrent revoke must not be overwritten by a racing checkAnswer", ConcurrencyRace) {
      Given("a consent in INITIATED state")
      val (consentId, answer) = mkConsent("test-answer-h3")
      val n = 2

      When("one thread revokes the consent while another concurrently answers the challenge correctly")
      // Thread 0 revokes; Thread 1 answers. Both load the consent before either commits.
      // The answerer holds a stale INITIATED object and writes ACCEPTED after the revoker commits REVOKED.
      val results = runConcurrentWithBarrier(n) { i =>
        if (i == 0) MappedConsentProvider.revoke(consentId)
        else        MappedConsentProvider.checkAnswer(consentId, answer)
      }

      Then("the final status must be REVOKED — a revocation must survive a concurrent answer")
      val finalSt = consentStatus(consentId)
      withClue(
        s"finalStatus=$finalSt results=${results.map(_.isSuccess)}: " +
        s"revoke() and checkAnswer() both do find-then-saveMe with no conditional UPDATE guard; " +
        s"the answerer's write of ACCEPTED can land after the revoker's REVOKED commit, " +
        s"resurrecting a consent the user explicitly revoked — "
      ) {
        finalSt should equal(ConsentStatus.REVOKED.toString)
      }
    }

    scenario("M5: the skip-SCA accept-write must not overwrite a concurrent revoke (shouldSkipConsentSca)", ConcurrencyRace) {
      Given("a consent in INITIATED state (just created, SCA about to be skipped)")
      // Http4s310's createConsent path, when (grantor,grantee) is in skipConsentScaForConsumerIdPairs,
      // does: MappedConsent.find(byId).map(_.mStatus(ACCEPTED).saveMe()) — an UNCONDITIONAL write,
      // no `WHERE mstatus='INITIATED'` guard.
      val (consentId, _) = mkConsent("m5-unused-answer")
      val n = 2

      When("one thread runs the skip-SCA accept-write while another concurrently revokes the consent")
      val results = runConcurrentWithBarrier(n) { i =>
        if (i == 0) {
          // Replicate the exact production write in Http4s310 (the unconditional skip-SCA accept).
          MappedConsent.find(By(MappedConsent.mConsentId, consentId))
            .map(_.mStatus(ConsentStatus.ACCEPTED.toString).saveMe())
        } else {
          MappedConsentProvider.revoke(consentId)
        }
      }

      Then("the final status must be REVOKED — an explicit revoke must win over an auto-accept")
      val finalSt = consentStatus(consentId)
      withClue(
        s"finalStatus=$finalSt results=${results.map(_.isSuccess)}: " +
        s"the skip-SCA path writes mStatus(ACCEPTED).saveMe() unconditionally; racing a revoke, the " +
        s"auto-accept can land last and resurrect a revoked consent. Fix: conditional " +
        s"UPDATE mstatus=ACCEPTED WHERE mstatus='INITIATED' so the accept is a no-op once revoked — "
      ) {
        finalSt should equal(ConsentStatus.REVOKED.toString)
      }
    }
  }
}
