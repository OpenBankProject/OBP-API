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

import code.api.berlin.group.ConstantsBG
import code.bankconnectors.DoobieConsentSchedulerQueries
import code.consent.{ConsentStatus, MappedConsent}
import net.liftweb.mapper.By

import java.util.{Date, UUID}

/**
 * Simulates a scheduler-vs-HTTP state-machine conflict on consent status. The test reproduces the
 * race deterministically (load stale → revoke → stale save) rather than concurrently, because the
 * hazard is structural: ConsentScheduler.expiredBerlinGroupConsents() calls .save on a detached
 * in-memory object with no conditional-update guard, so ANY intervening HTTP write that changes
 * the status between the scheduler's findAll and its .save will be silently overwritten.
 *
 *  J. Scheduler stale-save resurrects a revoked consent — the scheduler reads consents with
 *     status='valid' into memory, then iterates. Between that query and the final .save, an HTTP
 *     REVOKE call flips the status to 'terminatedByTpp'. The scheduler's stale object still holds
 *     status='valid', so its .save overwrites 'terminatedByTpp' back to 'expired', silently
 *     resurrecting a consent that the user or TPP explicitly revoked.
 *
 *  U. Same hazard in the UNFINISHED-consents task — ConsentScheduler.unfinishedBerlinGroupConsents
 *     reads consents with status='received', then later .save(status='rejected') on the stale
 *     in-memory object. A concurrent HTTP status change (e.g. the consent being authorised /
 *     revoked) committed in the window is overwritten back to 'rejected'.
 *
 * EXPECTED TO FAIL while the scheduler's save is unconditional. Tagged ConcurrencyRace.
 */
class ConcurrentConsentRaceTest extends ConcurrentRaceSetup {

  feature("Consent status finality under scheduler-vs-HTTP concurrent update") {

    scenario("J: a stale scheduler save must not overwrite a terminal consent status", ConcurrencyRace) {
      Given("a Berlin Group consent with status=valid and validUntil in the past")
      val consentId = UUID.randomUUID.toString
      MappedConsent.create
        .mConsentId(consentId)
        .mStatus(ConsentStatus.valid.toString)
        .mApiStandard(ConstantsBG.berlinGroupVersion1.apiStandard)
        .mValidUntil(new Date(1000L))
        .saveMe()

      When("the scheduler loads the consent into memory (replicating expiredBerlinGroupConsents findAll)")
      // The scheduler calls MappedConsent.findAll(...) and holds a list of in-memory objects.
      // This staleConsent represents one such object loaded BEFORE the revoke below.
      val staleConsent = MappedConsent.find(By(MappedConsent.mConsentId, consentId))
        .openOrThrowException("test consent must exist after creation")

      And("the HTTP revoke endpoint runs concurrently, flipping status to terminatedByTpp")
      MappedConsent.find(By(MappedConsent.mConsentId, consentId))
        .foreach { c =>
          c.mStatus(ConsentStatus.terminatedByTpp.toString)
            .mStatusUpdateDateTime(new Date())
            .saveMe()
        }
      val afterRevoke = MappedConsent.find(By(MappedConsent.mConsentId, consentId))
        .map(_.status).getOrElse("missing")

      And("the scheduler attempts to expire its stale copy via the guarded conditional update")
      DoobieConsentSchedulerQueries.conditionallyExpireValidBerlinGroupConsent(
        consentPrimaryKey = staleConsent.id.get,
        newNote      = ""
      )

      Then("the final status must remain terminatedByTpp — the revoke must survive the stale save")
      val finalStatus = MappedConsent.find(By(MappedConsent.mConsentId, consentId))
        .map(_.status).getOrElse("missing")
      withClue(
        s"afterRevoke=$afterRevoke finalStatus=$finalStatus: " +
        s"ConsentScheduler.expiredBerlinGroupConsents calls .save on a stale in-memory MappedConsent " +
        s"with no conditional-update guard (no WHERE status='valid'); the stale save overwrites any " +
        s"concurrent status change and resurrects a consent the user explicitly revoked — "
      ) {
        finalStatus should equal(ConsentStatus.terminatedByTpp.toString)
      }
    }

    scenario("U: the unfinished-consents scheduler task must not overwrite a concurrent status change", ConcurrencyRace) {
      Given("a Berlin Group consent with status=received (the unfinished-task selector)")
      val consentId = UUID.randomUUID.toString
      MappedConsent.create
        .mConsentId(consentId)
        .mStatus(ConsentStatus.received.toString)
        .mApiStandard(ConstantsBG.berlinGroupVersion1.apiStandard)
        .saveMe()

      When("the scheduler loads the consent into memory (replicating unfinishedBerlinGroupConsents findAll)")
      val staleConsent = MappedConsent.find(By(MappedConsent.mConsentId, consentId))
        .openOrThrowException("test consent must exist after creation")

      And("the HTTP path concurrently flips status to REVOKED and commits it")
      MappedConsent.find(By(MappedConsent.mConsentId, consentId))
        .foreach { c =>
          c.mStatus(ConsentStatus.REVOKED.toString)
            .mStatusUpdateDateTime(new Date())
            .saveMe()
        }
      val afterChange = MappedConsent.find(By(MappedConsent.mConsentId, consentId))
        .map(_.status).getOrElse("missing")

      And("the scheduler attempts to reject its stale copy via the guarded conditional update")
      DoobieConsentSchedulerQueries.conditionallyUpdateStatus(
        consentPrimaryKey = staleConsent.id.get,
        guardStatus  = ConsentStatus.received.toString,
        newStatus    = ConsentStatus.rejected.toString,
        newNote      = ""
      )

      Then("the final status must remain REVOKED — the committed change must survive the stale save")
      val finalStatus = MappedConsent.find(By(MappedConsent.mConsentId, consentId))
        .map(_.status).getOrElse("missing")
      withClue(
        s"afterChange=$afterChange finalStatus=$finalStatus: " +
        s"ConsentScheduler.unfinishedBerlinGroupConsents calls .save on a stale in-memory MappedConsent " +
        s"with no conditional-update guard (no WHERE status='received'); the stale save clobbers the " +
        s"concurrently-committed status — "
      ) {
        finalStatus should equal(ConsentStatus.REVOKED.toString)
      }
    }
  }
}
