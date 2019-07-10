package code.consent

import java.util.Date
import scala.util.Random
import code.api.util.ErrorMessages
import code.util.MappedUUID
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper.{MappedString, _}
import net.liftweb.util.Helpers.{now, tryo}

object MappedConsentProvider extends ConsentProvider {
  override def getConsentByConsentId(consentId: String): Box[MappedConsent] = {
    MappedConsent.find(
      By(MappedConsent.mConsentId, consentId)
    )
  }
  override def getConsentsByUser(userId: String): List[MappedConsent] = {
    MappedConsent.findAll(By(MappedConsent.mUserId, userId))
  }
  override def createConsent(user: User): Box[MappedConsent] = {
    tryo {
      MappedConsent
        .create
        .mUserId(user.userId)
        .mStatus(ConsentStatus.INITIATED.toString)
        .saveMe()
    }
  }
  override def createBerlinGroupConsent(
    user: User,
    recurringIndicator: Boolean,
    validUntil: Date,
    frequencyPerDay: Int,
    combinedServiceIndicator: Boolean) ={
    tryo {
      MappedConsent
        .create
        .mUserId(user.userId)
        .mStatus(ConsentStatus.RECEIVED.toString)
        .mRecurringIndicator(recurringIndicator)
        .mValidUntil(validUntil)
        .mFrequencyPerDay(frequencyPerDay)
        .mCombinedServiceIndicator(combinedServiceIndicator)
        .mLastActionDate(now) //maybe not right, but for the create we use the `now`, we need to update it later.
        .saveMe()
    }}
  override def updateBerlinGroupConsent(
                                         consentId: String,
    user: User,
    recurringIndicator: Boolean,
    validUntil: Date,
    frequencyPerDay: Int,
    combinedServiceIndicator: Boolean) ={
    MappedConsent.find(By(MappedConsent.mConsentId, consentId)) match {
      case Full(consent) =>
        tryo(consent
          .mUserId(user.userId)
          .mRecurringIndicator(recurringIndicator)
          .mValidUntil(validUntil)
          .mFrequencyPerDay(frequencyPerDay)
          .mCombinedServiceIndicator(combinedServiceIndicator)
          .mLastActionDate(now) //maybe not right, but for the create we use the `now`, we need to update it later.
          .saveMe()
        )
      case Empty =>
        Empty ?~! ErrorMessages.ConsentNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }
  
  override def setJsonWebToken(consentId: String, jwt: String): Box[MappedConsent] = {
    MappedConsent.find(By(MappedConsent.mConsentId, consentId)) match {
      case Full(consent) =>
        tryo(consent
          .mJsonWebToken(jwt)
          .saveMe())
      case Empty =>
        Empty ?~! ErrorMessages.ConsentNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    } 
  }  
  override def revoke(consentId: String): Box[MappedConsent] = {
    MappedConsent.find(By(MappedConsent.mConsentId, consentId)) match {
      case Full(consent) if consent.status == ConsentStatus.REVOKED.toString =>
        Failure(ErrorMessages.ConsentAlreadyRevoked)
      case Full(consent) =>
        tryo(consent
          .mStatus(ConsentStatus.REVOKED.toString)
          .saveMe())
      case Empty =>
        Empty ?~! ErrorMessages.ConsentNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    } 
  }  
  override def checkAnswer(consentId: String, challenge: String): Box[MappedConsent] = {
    MappedConsent.find(By(MappedConsent.mConsentId, consentId)) match {
      case Full(consent) =>
        consent.status match {
          case value if value == ConsentStatus.INITIATED.toString =>
            val status = if (consent.challenge == challenge) ConsentStatus.ACCEPTED.toString else ConsentStatus.REJECTED.toString
            tryo(consent.mStatus(status).saveMe())
          case _ =>
            Full(consent)
        }
      case Empty =>
        Empty ?~! ErrorMessages.ConsentNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    
  }
}

class MappedConsent extends Consent with LongKeyedMapper[MappedConsent] with IdPK with CreatedUpdated {

  def getSingleton = MappedConsent

  object mConsentId extends MappedUUID(this)
  object mUserId extends MappedString(this, 36)
  object mSecret extends MappedUUID(this)
  object mStatus extends MappedString(this, 20)
  object mChallenge extends MappedString(this, 10)  {
    override def defaultValue = Random.nextInt(99999999).toString()
  }
  object mJsonWebToken extends MappedString(this, 2048)

  //The following are added for BerlinGroup
  object mRecurringIndicator extends MappedBoolean(this)
  object mValidUntil extends MappedDate(this)
  object mFrequencyPerDay extends MappedInt(this)
  object mCombinedServiceIndicator extends MappedBoolean(this)
  object mLastActionDate extends MappedDate(this)

  override def consentId: String = mConsentId.get
  override def userId: String = mUserId.get
  override def secret: String = mSecret.get
  override def status: String = mStatus.get
  override def challenge: String = mChallenge.get
  override def jsonWebToken: String = mJsonWebToken.get

  override def recurringIndicator: Boolean = mRecurringIndicator.get
  override def validUntil = mValidUntil.get
  override def frequencyPerDay = mFrequencyPerDay.get
  override def combinedServiceIndicator = mCombinedServiceIndicator.get
  override def lastActionDate = mLastActionDate.get

}

object MappedConsent extends MappedConsent with LongKeyedMetaMapper[MappedConsent] {
  override def dbIndexes = UniqueIndex(mConsentId) :: super.dbIndexes
}
