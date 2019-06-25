package code.api.berlin.group.model.consent

import java.util.Date

import code.api.util.ErrorMessages
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.SimpleInjector

trait BerlinGroupConsent {
  def consentId: String
  def userId: String
  def status: String
  def recurringIndicator: Boolean
  def validUntil: Date
  def frequencyPerDay : Int
  def combinedServiceIndicator: Boolean
}

/*
//Note: get this from BerlinGroup Swagger file: https://www.berlin-group.org/nextgenpsd2-downloads `psd2-api 1.3.3 20190412`
    consentStatus:
      description: |
        This is the overall lifecycle status of the consent.

        Valid values are:
          - 'received': The consent data have been received and are technically correct. 
            The data is not authorised yet.
          - 'rejected': The consent data have been rejected e.g. since no successful authorisation has taken place.
          - 'valid': The consent is accepted and valid for GET account data calls and others as specified in the consent object.
          - 'revokedByPsu': The consent has been revoked by the PSU towards the ASPSP.
          - 'expired': The consent expired.
          - 'terminatedByTpp': The corresponding TPP has terminated the consent by applying the DELETE method to the consent resource.

        The ASPSP might add further codes. These codes then shall be contained in the ASPSP's documentation of the XS2A interface 
        and has to be added to this API definition as well.
      type: string
      enum:
      - "received"
      - "rejected"
      - "valid"
      - "revokedByPsu"
      - "expired"
      - "terminatedByTpp"
 */
object BerlinGroupConsentStatus extends Enumeration {
  type ConsentStatus = Value
  val RECEIVED, REJECTED, VALID, REVOKEDBYPSU, EXPIRED, TERMINATEDBYTPP = Value
}

object BerlinGroupConsents extends SimpleInjector {
  val consentProvider = new Inject(buildOne _) {}

  def buildOne: BerlinGroupConsentProvider = MappedBerlinGroupConsentProvider
}

trait BerlinGroupConsentProvider {
  def getConsentByConsentId(consentId: String): Box[BerlinGroupConsent]

  def getConsentsByUser(userId: String): List[BerlinGroupConsent]

  def createConsent(
    user: User,
    recurringIndicator: Boolean,
    validUntil: Date,
    frequencyPerDay: Int,
    combinedServiceIndicator: Boolean): Box[BerlinGroupConsent]

  def revoke(consentId: String): Box[BerlinGroupConsent]
}


object MappedBerlinGroupConsentProvider extends BerlinGroupConsentProvider {
  override def getConsentByConsentId(consentId: String): Box[MappedBerlinGroupConsent] = {
    MappedBerlinGroupConsent.find(
      By(MappedBerlinGroupConsent.mConsentId, consentId)
    )
  }

  override def getConsentsByUser(userId: String): List[MappedBerlinGroupConsent] = {
    MappedBerlinGroupConsent.findAll(By(MappedBerlinGroupConsent.mUserId, userId))
  }

  override def createConsent(
    user: User,
    recurringIndicator: Boolean,
    validUntil: Date,
    frequencyPerDay: Int,
    combinedServiceIndicator: Boolean
  ): Box[MappedBerlinGroupConsent] = {
    tryo {
      MappedBerlinGroupConsent
        .create
        .mUserId(user.userId)
        .mStatus(BerlinGroupConsentStatus.RECEIVED.toString)
        .mRecurringIndicator(recurringIndicator)
        .mValidUntil(validUntil)
        .mFrequencyPerDay(frequencyPerDay)
        .mCombinedServiceIndicator(combinedServiceIndicator)
      .saveMe()
    }
  }

  override def revoke(consentId: String): Box[MappedBerlinGroupConsent] = {
    MappedBerlinGroupConsent.find(By(MappedBerlinGroupConsent.mConsentId, consentId)) match {
      case Full(consent) =>
        tryo(consent
          .mStatus(BerlinGroupConsentStatus.REVOKEDBYPSU.toString)
          .saveMe())
      case Empty =>
        Empty ?~! ErrorMessages.ConsentNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }
}






