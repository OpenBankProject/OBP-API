package code.consent

import scala.util.Random

import code.api.util.ErrorMessages
import code.util.MappedUUID
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MappedConsentProvider extends ConsentProvider {
  override def getConsentByConsentId(consentId: String): Box[MappedConsent] = {
    MappedConsent.find(
      By(MappedConsent.mConsentId, consentId)
    )
  }
  override def createConsent(): Box[MappedConsent] = {
    tryo {
      MappedConsent
        .create
        .mStatus(ConsentStatus.INITIATED.toString)
        .saveMe()
    }
  }  
  override def updateConsent(consentId: String, jwt: String): Box[MappedConsent] = {
    MappedConsent.find(By(MappedConsent.mConsentId, consentId)) match {
      case Full(consent) =>
        tryo(consent
          .mJsonWebToken(jwt)
          .mConsentId(consentId)
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

class MappedConsent extends Consent with LongKeyedMapper[MappedConsent] with IdPK with CreatedUpdated {

  def getSingleton = MappedConsent

  object mConsentId extends MappedUUID(this)
  object mSecret extends MappedUUID(this)
  object mStatus extends MappedString(this, 20)
  object mChallenge extends MappedString(this, 10)  {
    override def defaultValue = Random.nextInt(99999999).toString()
  }
  object mJsonWebToken extends MappedString(this, 1024)

  override def consentId: String = mConsentId.get
  override def secret: String = mSecret.get
  override def status: String = mStatus.get
  override def challenge: String = mChallenge.get
  override def jsonWebToken: String = mJsonWebToken.get

}

object MappedConsent extends MappedConsent with LongKeyedMetaMapper[MappedConsent] {
  override def dbIndexes = UniqueIndex(mConsentId) :: super.dbIndexes
}
