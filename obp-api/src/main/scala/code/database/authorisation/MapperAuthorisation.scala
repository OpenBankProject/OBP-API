package code.database.authorisation

import code.api.BerlinGroup.ScaStatus
import code.api.util.ErrorMessages
import code.consent.{ConsentStatus, MappedConsent}
import code.util.MappedUUID
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper.{BaseIndex, By, CreatedUpdated, IdPK, LongKeyedMapper, LongKeyedMetaMapper, MappedString, UniqueIndex}
import net.liftweb.util.Helpers.tryo


class Authorisation extends LongKeyedMapper[Authorisation] with IdPK with CreatedUpdated {
  def getSingleton = Authorisation
  // Enum: received, psuIdentified, psuAuthenticated, scaMethodSelected, started, finalised, failed, exempted
  object ScaStatus extends MappedString(this, 20)
  object AuthorisationId extends MappedUUID(this)
  object PaymentId extends MappedUUID(this)
  object ConsentId extends MappedUUID(this)
  // Enum: SMS_OTP, CHIP_OTP, PHOTO_OTP, PUSH_OTP
  object AuthenticationType extends MappedString(this, 10)
  object AuthenticationMethodId extends MappedString(this, 35)
  object ChallengeData extends MappedString(this, 1024)

  def scaStatus: String = ScaStatus.get
  def authorisationId: String = AuthorisationId.get
  def paymentId: String = PaymentId.get
  def consentId: String = ConsentId.get
  def authenticationType: String = AuthenticationType.get
  def authenticationMethodId: String = AuthenticationMethodId.get
  def challengeData: String = ChallengeData.get
}

object Authorisation extends Authorisation with LongKeyedMetaMapper[Authorisation] {
  override def dbIndexes: List[BaseIndex[Authorisation]] = UniqueIndex(AuthorisationId) :: super.dbIndexes
}

object MappedAuthorisationProvider extends AuthorisationProvider {
   override def getAuthorizationByAuthorizationId(paymentId: String, authorizationId: String): Box[Authorisation] = {
    val result: Box[Authorisation] = Authorisation.find(
      By(Authorisation.PaymentId, paymentId),
      By(Authorisation.AuthorisationId, authorizationId)
    )
     result
  }
  override def getAuthorizationByAuthorizationId(authorizationId: String): Box[Authorisation] = {
    val result: Box[Authorisation] = Authorisation.find(
      By(Authorisation.AuthorisationId, authorizationId)
    )
     result
  }
  
  override def getAuthorizationByPaymentId(paymentId: String): Box[List[Authorisation]] = {
    tryo(Authorisation.findAll(By(Authorisation.PaymentId, paymentId)))
  }
  override def getAuthorizationByConsentId(consentId: String): Box[List[Authorisation]] = {
    tryo(Authorisation.findAll(By(Authorisation.ConsentId, consentId)))
  }

  def createAuthorization(paymentId: String,
                          consentId: String,
                          authenticationType: String,
                          authenticationMethodId: String,
                          scaStatus: String,
                          challengeData: String
                         ): Box[Authorisation] = tryo {
    Authorisation
      .create
      .PaymentId(paymentId)
      .ConsentId(consentId)
      .AuthenticationType(authenticationType)
      .AuthenticationMethodId(authenticationMethodId)
      .ChallengeData(challengeData)
      .ScaStatus(scaStatus).saveMe()
  }

  def checkAnswer(paymentId: String, authorizationId: String, challengeData: String): Box[Authorisation] =
    getAuthorizationByAuthorizationId(paymentId: String, authorizationId: String) match {
      case Full(authorisation) =>
        authorisation.scaStatus match {
          case value if value == ScaStatus.received.toString =>
            val status = if (authorisation.challengeData == challengeData) ScaStatus.finalised.toString else ScaStatus.failed.toString
            tryo(authorisation.ScaStatus(status).saveMe())
          case _ => //make sure, only `received` can be processed, all others are invalid .
            Failure(s"${ErrorMessages.InvalidAuthorisationStatus}.It should be `received`, but now it is `${authorisation.scaStatus}`")
        }
      case Empty =>
        Empty ?~! s"${ErrorMessages.AuthorisationNotFound} Current PAYMENT_ID($paymentId) and AUTHORISATION_ID ($authorizationId),"
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
}




