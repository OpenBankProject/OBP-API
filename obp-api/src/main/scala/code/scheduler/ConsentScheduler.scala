package code.scheduler

import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil
import code.consent.{ConsentStatus, MappedConsent}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiStandards, ApiVersion}
import net.liftweb.common.Full
import net.liftweb.mapper.{By, By_<}

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import scala.util.{Failure, Success, Try}


object ConsentScheduler extends MdcLoggable {
  val dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.ENGLISH)
  def currentDate = dateFormat.format(new Date())

  // Starts multiple scheduled tasks with different intervals
  def startAll(): Unit = {
    var initialDelay = 0
    // Berlin Group
    APIUtil.getPropsAsIntValue("berlin_group_outdated_consents_interval_in_seconds") match {
      case Full(interval) if interval > 0 =>
        val time = APIUtil.getPropsAsIntValue("berlin_group_outdated_consents_time_in_seconds", 300)
        SchedulerUtil.startTask(interval = interval, () => unfinishedBerlinGroupConsents(time)) // Runs periodically
        initialDelay = initialDelay + 10
      case _ =>
        logger.warn("|---> Skipping unfinishedBerlinGroupConsents task: berlin_group_outdated_consents_interval_in_seconds not set or invalid")
    }

    APIUtil.getPropsAsIntValue("berlin_group_expired_consents_interval_in_seconds") match {
      case Full(interval) if interval > 0 =>
        SchedulerUtil.startTask(interval = interval, () => expiredBerlinGroupConsents(), initialDelay) // Delay for 10 seconds
        initialDelay = initialDelay + 10
      case _ =>
        logger.warn("|---> Skipping expiredBerlinGroupConsents task: berlin_group_expired_consents_interval_in_seconds not set or invalid")
    }

    // Open Bank Project
    APIUtil.getPropsAsIntValue("obp_expired_consents_interval_in_seconds") match {
      case Full(interval) if interval > 0 =>
        SchedulerUtil.startTask(interval = interval, () => expiredObpConsents(), initialDelay) // Delay for 10 seconds
        initialDelay = initialDelay + 10
      case _ =>
        logger.warn("|---> Skipping expiredObpConsents task: obp_expired_consents_interval_in_seconds not set or invalid")
    }
  }



  private def unfinishedBerlinGroupConsents(seconds: Int): Unit = {
    Try {
      logger.debug("|---> Checking for outdated Berlin Group consents...")

      val outdatedConsents = MappedConsent.findAll(
        By(MappedConsent.mStatus, ConsentStatus.received.toString),
        By(MappedConsent.mApiStandard, ConstantsBG.berlinGroupVersion1.apiStandard),
        By_<(MappedConsent.updatedAt, SchedulerUtil.someSecondsAgo(seconds))
      )

      logger.debug(s"|---> Found ${outdatedConsents.size} outdated consents")

      outdatedConsents.foreach { consent =>
        Try {
          val message = s"|---> Changed status from ${consent.status} to ${ConsentStatus.rejected} for consent ID: ${consent.id}"
          val newNote = s"$currentDate\n$message\n" + Option(consent.note).getOrElse("") // Prepend to existing note if any
          consent
            .mStatus(ConsentStatus.rejected.toString)
            .mNote(newNote)
            .mStatusUpdateDateTime(new Date())
            .save
          logger.warn(message)
        } match {
          case Failure(ex) => logger.error(s"Failed to update consent ID: ${consent.id}", ex)
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

      val expiredConsentsLowerCase: List[MappedConsent] = MappedConsent.findAll(
        By(MappedConsent.mStatus, ConsentStatus.valid.toString),
        By(MappedConsent.mApiStandard, ConstantsBG.berlinGroupVersion1.apiStandard),
        By_<(MappedConsent.mValidUntil, new Date())
      )

      val expiredConsentsUpperCase: List[MappedConsent] = MappedConsent.findAll(
        By(MappedConsent.mStatus, ConsentStatus.valid.toString.toUpperCase()), // Handle uppercase as well; should appear only during the transition period
        By(MappedConsent.mApiStandard, ConstantsBG.berlinGroupVersion1.apiStandard),
        By_<(MappedConsent.mValidUntil, new Date())
      )

      val expiredConsents = expiredConsentsLowerCase ::: expiredConsentsUpperCase

      logger.debug(s"|---> Found ${expiredConsents.size} expired consents")

      expiredConsents.foreach { consent =>
        Try {
          val message = s"|---> Changed status from ${consent.status} to ${ConsentStatus.expired} for consent ID: ${consent.id}"
          val newNote = s"$currentDate\n$message\n" + Option(consent.note).getOrElse("") // Prepend to existing note if any
          consent
            .mStatus(ConsentStatus.expired.toString)
            .mNote(newNote)
            .mStatusUpdateDateTime(new Date())
            .save
          logger.warn(message)
        } match {
          case Failure(ex) => logger.error(s"Failed to update consent ID: ${consent.id}", ex)
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

      val expiredConsents = MappedConsent.findAll(
        By(MappedConsent.mStatus, ConsentStatus.ACCEPTED.toString),
        By(MappedConsent.mApiStandard, ApiStandards.obp.toString),
        By_<(MappedConsent.mValidUntil, new Date())
      )

      logger.debug(s"|---> Found ${expiredConsents.size} expired consents")

      expiredConsents.foreach { consent =>
        Try {
          val message = s"|---> Changed status from ${consent.status} to ${ConsentStatus.EXPIRED.toString} for consent ID: ${consent.id}"
          val newNote = s"$currentDate\n$message\n" + Option(consent.note).getOrElse("") // Prepend to existing note if any
          consent
            .mStatus(ConsentStatus.EXPIRED.toString)
            .mNote(newNote)
            .mStatusUpdateDateTime(new Date())
            .save
          logger.warn(message)
        } match {
          case Failure(ex) => logger.error(s"Failed to update consent ID: ${consent.id}", ex)
          case Success(_) => // Already logged
        }
      }
    } match {
      case Failure(ex) => logger.error("Error in expiredObpConsents!", ex)
      case Success(_) => logger.debug("|---> Task executed successfully")
    }
  }

}
