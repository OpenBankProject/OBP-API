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

package code.scheduler

import code.api.berlin.group.ConstantsBG
import code.api.util.{APIUtil, Consent}
import code.consent.{ConsentStatus, MappedConsent}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiStandards, ApiVersion}
import net.liftweb.common.Full

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import scala.util.{Failure, Success, Try}


object ConsentScheduler extends MdcLoggable {
  // SimpleDateFormat is not thread-safe; build a fresh instance per call (low frequency).
  def currentDate: String = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.ENGLISH).format(new Date())

  // Starts multiple scheduled tasks with different intervals.
  // All tasks are enabled by default. Set the interval prop to 0 to disable a task.
  // Default intervals use prime-ish offsets (599, 597, 595) to avoid tasks firing at the same time
  // and spreading load more evenly.
  def startAll(): Unit = {
    var initialDelay = 0
    // Berlin Group
    val bgOutdatedInterval = APIUtil.getPropsAsIntValue("berlin_group_outdated_consents_interval_in_seconds", 599)
    if (bgOutdatedInterval > 0) {
      val time = APIUtil.getPropsAsIntValue("berlin_group_outdated_consents_time_in_seconds", 300)
      SchedulerUtil.startTask(interval = bgOutdatedInterval, () => unfinishedBerlinGroupConsents(time))
      initialDelay = initialDelay + 10
    } else {
      logger.warn("|---> Skipping unfinishedBerlinGroupConsents task: berlin_group_outdated_consents_interval_in_seconds set to 0")
    }

    val bgExpiredInterval = APIUtil.getPropsAsIntValue("berlin_group_expired_consents_interval_in_seconds", 597)
    if (bgExpiredInterval > 0) {
      SchedulerUtil.startTask(interval = bgExpiredInterval, () => expiredBerlinGroupConsents(), initialDelay)
      initialDelay = initialDelay + 10
    } else {
      logger.warn("|---> Skipping expiredBerlinGroupConsents task: berlin_group_expired_consents_interval_in_seconds set to 0")
    }

    // Open Bank Project
    val obpExpiredInterval = APIUtil.getPropsAsIntValue("obp_expired_consents_interval_in_seconds", 595)
    if (obpExpiredInterval > 0) {
      SchedulerUtil.startTask(interval = obpExpiredInterval, () => expiredObpConsents(), initialDelay)
      initialDelay = initialDelay + 10
    } else {
      logger.warn("|---> Skipping expiredObpConsents task: obp_expired_consents_interval_in_seconds set to 0")
    }

    // OBP consents still INITIATED: their challenge was never answered (or could not be), so they are rejected
    // after a while instead of waiting forever. An app that reuses a pending consent is then not blocked by one.
    val obpUnfinishedInterval = APIUtil.getPropsAsIntValue("obp_unfinished_consents_interval_in_seconds", 593)
    if (obpUnfinishedInterval > 0) {
      val time = APIUtil.getPropsAsIntValue("obp_unfinished_consents_time_in_seconds", 21600)
      SchedulerUtil.startTask(interval = obpUnfinishedInterval, () => unfinishedObpConsents(time), initialDelay)
      initialDelay = initialDelay + 10
    } else {
      logger.warn("|---> Skipping unfinishedObpConsents task: obp_unfinished_consents_interval_in_seconds set to 0")
    }
    // UK Open Banking. checkUKConsent (ConsentUtil.scala) also reactively rejects an expired
    // AUTHORISED consent on every request regardless of this task's timing -- this proactive
    // sweep just keeps the stored status accurate for GET/dashboard purposes.
    val ukExpiredInterval = APIUtil.getPropsAsIntValue("uk_open_banking_expired_consents_interval_in_seconds", 601)
    if (ukExpiredInterval > 0) {
      SchedulerUtil.startTask(interval = ukExpiredInterval, () => expiredUKConsents(), initialDelay)
      initialDelay = initialDelay + 10
    } else {
      logger.warn("|---> Skipping expiredUKConsents task: uk_open_banking_expired_consents_interval_in_seconds set to 0")
    }
  }



  private def unfinishedBerlinGroupConsents(seconds: Int): Unit = {
    Try {
      logger.debug("|---> Checking for outdated Berlin Group consents...")

      val outdatedConsents = MappedConsent.findAllByStatusAndStandardNotUpdatedSince(
        ConsentStatus.received.toString, ConstantsBG.berlinGroupVersion1.apiStandard,
        SchedulerUtil.someSecondsAgo(seconds))

      logger.debug(s"|---> Found ${outdatedConsents.size} outdated consents")

      outdatedConsents.foreach { consent =>
        Try {
          val message = s"|---> Changed status from ${consent.status} to ${ConsentStatus.rejected} for consent ID: ${consent.consentPrimaryKey}"
          val newNote = s"$currentDate\n$message\n" + Option(consent.note).getOrElse("")
          val rows = code.bankconnectors.DoobieConsentSchedulerQueries.conditionallyUpdateStatus(
            consentPrimaryKey = consent.consentPrimaryKey,
            guardStatus  = ConsentStatus.received.toString,
            newStatus    = ConsentStatus.rejected.toString,
            newNote      = newNote
          )
          if (rows > 0) {
            // An expired consent must give its granted access back, exactly as a revoked one does.
            Consent.revokeConsentAccountAccess(consent)
            logger.warn(message)
          }
          else logger.debug(s"|---> Skipped stale update for consent ${consent.consentPrimaryKey}: status already changed")
        } match {
          case Failure(ex) => logger.error(s"Failed to update consent ID: ${consent.consentPrimaryKey}", ex)
          case Success(_) => // Already logged
        }
      }
    } match {
      case Failure(ex) => logger.error("Error in unfinishedBerlinGroupConsents!", ex)
      case Success(_) => logger.debug("|---> Task executed successfully")
    }
  }
  private def expiredBerlinGroupConsents(): Unit = {
    Try {
      logger.debug("|---> Checking for expired Berlin Group consents...")

      val expiredConsentsLowerCase: List[MappedConsent] = MappedConsent.findAllByStatusAndStandard(
        ConsentStatus.valid.toString, ConstantsBG.berlinGroupVersion1.apiStandard,
        Some("mvaliduntil"), Some(new Date()))

      // Handle uppercase as well; should appear only during the transition period
      val expiredConsentsUpperCase: List[MappedConsent] = MappedConsent.findAllByStatusAndStandard(
        ConsentStatus.valid.toString.toUpperCase(), ConstantsBG.berlinGroupVersion1.apiStandard,
        Some("mvaliduntil"), Some(new Date()))

      val expiredConsents = expiredConsentsLowerCase ::: expiredConsentsUpperCase

      logger.debug(s"|---> Found ${expiredConsents.size} expired consents")

      expiredConsents.foreach { consent =>
        Try {
          val message = s"|---> Changed status from ${consent.status} to ${ConsentStatus.expired} for consent ID: ${consent.consentPrimaryKey}"
          val newNote = s"$currentDate\n$message\n" + Option(consent.note).getOrElse("")
          val rows = code.bankconnectors.DoobieConsentSchedulerQueries.conditionallyExpireValidBerlinGroupConsent(
            consentPrimaryKey = consent.consentPrimaryKey,
            newNote      = newNote
          )
          if (rows > 0) {
            // An expired consent must give its granted access back, exactly as a revoked one does.
            Consent.revokeConsentAccountAccess(consent)
            logger.warn(message)
          }
          else logger.debug(s"|---> Skipped stale update for consent ${consent.consentPrimaryKey}: status already changed")
        } match {
          case Failure(ex) => logger.error(s"Failed to update consent ID: ${consent.consentPrimaryKey}", ex)
          case Success(_) => // Already logged
        }
      }
    } match {
      case Failure(ex) => logger.error("Error in expiredBerlinGroupConsents!", ex)
      case Success(_) => logger.debug("|---> Task executed successfully")
    }
  }
  private def expiredObpConsents(): Unit = {
    Try {
      logger.debug("|---> Checking for expired OBP consents...")

      val expiredConsents = MappedConsent.findAllByStatusAndStandard(
        ConsentStatus.ACCEPTED.toString, ApiStandards.obp.toString,
        Some("mvaliduntil"), Some(new Date()))

      logger.debug(s"|---> Found ${expiredConsents.size} expired consents")

      expiredConsents.foreach { consent =>
        Try {
          val message = s"|---> Changed status from ${consent.status} to ${ConsentStatus.EXPIRED.toString} for consent ID: ${consent.consentPrimaryKey}"
          val newNote = s"$currentDate\n$message\n" + Option(consent.note).getOrElse("")
          val rows = code.bankconnectors.DoobieConsentSchedulerQueries.conditionallyUpdateStatus(
            consentPrimaryKey = consent.consentPrimaryKey,
            guardStatus  = ConsentStatus.ACCEPTED.toString,
            newStatus    = ConsentStatus.EXPIRED.toString,
            newNote      = newNote
          )
          if (rows > 0) {
            // An expired consent must give its granted access back, exactly as a revoked one does.
            Consent.revokeConsentAccountAccess(consent)
            logger.warn(message)
          }
          else logger.debug(s"|---> Skipped stale update for OBP consent ${consent.consentPrimaryKey}: status already changed")
        } match {
          case Failure(ex) => logger.error(s"Failed to update consent ID: ${consent.consentPrimaryKey}", ex)
          case Success(_) => // Already logged
        }
      }
    } match {
      case Failure(ex) => logger.error("Error in expiredObpConsents!", ex)
      case Success(_) => logger.debug("|---> Task executed successfully")
    }
  }
  /**
   * Rejects OBP consents that are still INITIATED and have not changed for `seconds`. An INITIATED consent waits
   * only for the answer to its challenge; it never granted any access, so there is nothing to give back.
   */
  private[scheduler] def unfinishedObpConsents(seconds: Int): Unit = {
    Try {
      logger.debug("|---> Checking for unfinished OBP consents...")
      val unfinishedConsents = MappedConsent.findAllByStatusAndStandardNotUpdatedSince(
        ConsentStatus.INITIATED.toString, ApiStandards.obp.toString, SchedulerUtil.someSecondsAgo(seconds))
      logger.debug(s"|---> Found ${unfinishedConsents.size} unfinished OBP consents")
      unfinishedConsents.foreach { consent =>
        Try {
          val message = s"|---> Changed status from ${consent.status} to ${ConsentStatus.REJECTED} for consent ID: ${consent.consentPrimaryKey}: not accepted within $seconds seconds"
          val newNote = s"$currentDate\n$message\n" + Option(consent.note).getOrElse("")
          val rows = code.bankconnectors.DoobieConsentSchedulerQueries.conditionallyUpdateStatus(
            consentPrimaryKey = consent.consentPrimaryKey,
            guardStatus  = ConsentStatus.INITIATED.toString,
            newStatus    = ConsentStatus.REJECTED.toString,
            newNote      = newNote
          )
          if (rows > 0) logger.warn(message)
          else logger.debug(s"|---> Skipped stale update for OBP consent ${consent.consentPrimaryKey}: status already changed")
        } match {
          case Failure(ex) => logger.error(s"Failed to update consent ID: ${consent.consentPrimaryKey}", ex)
          case Success(_) => // Already logged
        }
      }
    } match {
      case Failure(ex) => logger.error("Error in unfinishedObpConsents!", ex)
      case Success(_) => logger.debug("|---> Task executed successfully")
    }
  }

  // private[code] so a test can run one sweep without waiting for the schedule.
  private[code] def expiredUKConsents(): Unit = {
    Try {
      logger.debug("|---> Checking for expired UK Open Banking consents...")

      // A null mExpirationDateTime (never set -- 0..1 per spec, open-ended if absent) never
      // matches By_< against a real Date, so perpetual consents are correctly never selected here.
      val expiredConsents = MappedConsent.findAllByStatusAndStandard(
        ConsentStatus.AUTHORISED.toString, Consent.ConsentStandardUK,
        Some("mexpirationdatetime"), Some(new Date()))

      logger.debug(s"|---> Found ${expiredConsents.size} expired consents")

      expiredConsents.foreach { consent =>
        Try {
          val message = s"|---> Changed status from ${consent.status} to ${ConsentStatus.EXPIRED.toString} for consent ID: ${consent.consentPrimaryKey}"
          val newNote = s"$currentDate\n$message\n" + Option(consent.note).getOrElse("")
          val rows = code.bankconnectors.DoobieConsentSchedulerQueries.conditionallyUpdateStatus(
            consentPrimaryKey = consent.consentPrimaryKey,
            guardStatus  = ConsentStatus.AUTHORISED.toString,
            newStatus    = ConsentStatus.EXPIRED.toString,
            newNote      = newNote
          )
          if (rows > 0) {
            // An expired consent must give its granted access back, exactly as a revoked one does.
            Consent.revokeConsentAccountAccess(consent)
            // Only UK consents are authorised through OBP_CONSENT_CHALLENGEs, so this is the one
            // expiry sweep that can leave such a challenge behind.
            Consent.removeConsentChallenges(consent.consentId)
            logger.warn(message)
          }
          else logger.debug(s"|---> Skipped stale update for UK consent ${consent.consentPrimaryKey}: status already changed")
        } match {
          case Failure(ex) => logger.error(s"Failed to update consent ID: ${consent.consentPrimaryKey}", ex)
          case Success(_) => // Already logged
        }
      }
    } match {
      case Failure(ex) => logger.error("Error in expiredUKConsents!", ex)
      case Success(_) => logger.debug("|---> Task executed successfully")
    }
  }

}
