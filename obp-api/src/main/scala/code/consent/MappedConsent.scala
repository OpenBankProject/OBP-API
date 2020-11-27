package code.consent

import java.util.Date

import code.api.util.{APIUtil, Consent, ErrorMessages}
import code.consent.ConsentStatus.ConsentStatus
import code.util.MappedUUID
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper.{MappedString, _}
import net.liftweb.util.Helpers.{now, tryo}
import org.mindrot.jbcrypt.BCrypt

import scala.collection.immutable.List
import scala.util.Random

object MappedConsentProvider extends ConsentProvider {
  override def getConsentByConsentId(consentId: String): Box[MappedConsent] = {
    MappedConsent.find(
      By(MappedConsent.mConsentId, consentId)
    )
  }
  override def updateConsentStatus(consentId: String, status: ConsentStatus): Box[MappedConsent] = {
    MappedConsent.find(By(MappedConsent.mConsentId, consentId)) match {
      case Full(consent) =>
        tryo(consent
          .mStatus(status.toString)
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
  override def updateConsentUser(consentId: String, user: User): Box[MappedConsent] = {
    MappedConsent.find(By(MappedConsent.mConsentId, consentId)) match {
      case Full(consent) =>
        tryo(consent
          .mUserId(user.userId)
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
  override def getConsentsByUser(userId: String): List[MappedConsent] = {
    MappedConsent.findAll(By(MappedConsent.mUserId, userId))
  }
  override def createConsent(user: User, challengeAnswer: String): Box[MappedConsent] = {
    tryo {
      val salt = BCrypt.gensalt()
      val challengeAnswerHashed = BCrypt.hashpw(challengeAnswer, salt).substring(0, 44)
      MappedConsent
        .create
        .mUserId(user.userId)
        .mChallenge(challengeAnswerHashed)
        .mSalt(salt)
        .mStatus(ConsentStatus.INITIATED.toString)
        .saveMe()
    }
  }
  override def createBerlinGroupConsent(
    user: User,
    recurringIndicator: Boolean,
    validUntil: Date,
    frequencyPerDay: Int,
    combinedServiceIndicator: Boolean,
    apiStandard: Option[String],
    apiVersion: Option[String]) ={
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
        .mApiVersion(apiVersion.getOrElse(null))
        .mApiStandard(apiStandard.getOrElse(null))
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

  override def saveUKConsent(
    user: Option[User],
    bankId: Option[String],//for UK Open Banking endpoints, there is no BankId there.
    accountIds: Option[List[String]],//for UK Open Banking endpoints, there is no accountIds there.
    consumerId: Option[String],
    permissions: List[String],
    expirationDateTime: Date,
    transactionFromDateTime: Date,
    transactionToDateTime: Date,
    apiStandard: Option[String],
    apiVersion: Option[String]
  ) ={
    tryo {
      val consent = MappedConsent
        .create
        .mUserId(user.map(_.userId).getOrElse(null))
        .mStatus(ConsentStatus.AWAITINGAUTHORISATION.toString)
        .mExpirationDateTime(expirationDateTime)
        .mTransactionFromDateTime(transactionFromDateTime)
        .mTransactionToDateTime(transactionToDateTime)
        .mStatusUpdateDateTime(now)
        .mApiVersion(apiVersion.getOrElse(null))
        .mApiStandard(apiStandard.getOrElse(null))
        .saveMe()
      val jwt = Consent.createUKConsentJWT(
        user: Option[User],
        bankId: Option[String],
        accountIds: Option[List[String]],
        permissions: List[String],
        expirationDateTime: Date,
        transactionFromDateTime: Date,
        transactionToDateTime: Date,
        secret = consent.secret,
        consentId = consent.consentId,
        consumerId: Option[String]
      )
      setJsonWebToken(consent.consentId, jwt).head
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
  override def checkAnswer(consentId: String, challengeAnswer: String): Box[MappedConsent] = {
    def isAnswerCorrect(expectedAnswerHashed: String, answer: String, salt: String) = {
      val challengeAnswerHashed = BCrypt.hashpw(answer, salt).substring(0, 44)
      val scaEnabled = APIUtil.getPropsAsBoolValue("consents.sca.enabled", true)
      if(scaEnabled) {
        expectedAnswerHashed == challengeAnswerHashed
      } else {
        true
      }
    }
    MappedConsent.find(By(MappedConsent.mConsentId, consentId)) match {
      case Full(consent) =>
        consent.status match {
          case value if value == ConsentStatus.INITIATED.toString =>
            val status = 
              if (isAnswerCorrect(consent.challenge, challengeAnswer, consent.mSalt.get)) ConsentStatus.ACCEPTED.toString 
              else ConsentStatus.REJECTED.toString
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

  //the following are the obp consent.
  object mConsentId extends MappedUUID(this)
  object mUserId extends MappedString(this, 36)
  object mSecret extends MappedUUID(this)
  object mStatus extends MappedString(this, 40)
  object mChallenge extends MappedString(this, 50)  {
    override def defaultValue = Random.nextInt(99999999).toString()
  }
  object mSalt extends MappedString(this, 50)  {
    override def defaultValue = BCrypt.gensalt()
  }
  object mJsonWebToken extends MappedText(this)
  
  object mApiStandard extends MappedString(this, 50)
  object mApiVersion extends MappedString(this, 50)

  //The following are added for BerlinGroup.
  object mRecurringIndicator extends MappedBoolean(this)
  object mValidUntil extends MappedDate(this)
  object mFrequencyPerDay extends MappedInt(this)
  object mCombinedServiceIndicator extends MappedBoolean(this)
  object mLastActionDate extends MappedDate(this)

  //The following are added for UK OpenBanking.
  object mExpirationDateTime extends MappedDateTime(this)
  object mTransactionFromDateTime extends MappedDateTime(this)
  object mTransactionToDateTime extends MappedDateTime(this)
  object mStatusUpdateDateTime extends MappedDateTime(this)

  override def consentId: String = mConsentId.get
  override def userId: String = mUserId.get
  override def secret: String = mSecret.get
  override def status: String = mStatus.get
  // The hashed challenge using the OpenBSD bcrypt scheme
  // The salt to hash with (generated using BCrypt.gensalt)
  override def challenge: String = mChallenge.get
  override def jsonWebToken: String = mJsonWebToken.get
  
  override def apiStandard: String = mApiStandard.get
  override def apiVersion: String = mApiVersion.get

  override def recurringIndicator: Boolean = mRecurringIndicator.get
  override def validUntil = mValidUntil.get
  override def frequencyPerDay = mFrequencyPerDay.get
  override def combinedServiceIndicator = mCombinedServiceIndicator.get
  override def lastActionDate = mLastActionDate.get

  override def expirationDateTime = mExpirationDateTime.get
  override def transactionFromDateTime= mTransactionFromDateTime.get    
  override def transactionToDateTime= mTransactionToDateTime.get    
  override def creationDateTime= createdAt.get    
  override def statusUpdateDateTime= mStatusUpdateDateTime.get    

}

object MappedConsent extends MappedConsent with LongKeyedMetaMapper[MappedConsent] {
  override def dbIndexes = UniqueIndex(mConsentId) :: super.dbIndexes
}
