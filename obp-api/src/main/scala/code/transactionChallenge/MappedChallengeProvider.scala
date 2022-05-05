package code.transactionChallenge

import code.api.util.APIUtil.transactionRequestChallengeTtl
import code.api.util.{APIUtil, ErrorMessages}
import com.openbankproject.commons.model.{ChallengeTrait, ErrorMessage}
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers
import org.mindrot.jbcrypt.BCrypt
import net.liftweb.util.Helpers.tryo

import scala.compat.Platform

object MappedChallengeProvider extends ChallengeProvider {
  
  override def saveChallenge(
    challengeId: String,
    transactionRequestId: String,
    salt: String,
    expectedAnswer: String,
    expectedUserId: String,
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],
    consentId: Option[String], // Note: consentId and transactionRequestId are exclusive here.
    authenticationMethodId: Option[String], 
  ): Box[ChallengeTrait] = 
    tryo (
      MappedExpectedChallengeAnswer
        .create
        .mChallengeId(challengeId)
        .mTransactionRequestId(transactionRequestId)
        .mSalt(salt)
        .mExpectedAnswer(expectedAnswer)
        .mExpectedUserId(expectedUserId)
        .mScaMethod(scaMethod.map(_.toString).getOrElse(""))
        .mScaStatus(scaStatus.map(_.toString).getOrElse(""))
        .mConsentId(consentId.getOrElse(""))
        .mAuthenticationMethodId(expectedUserId)
        .saveMe()
    )
  
  override def getChallenge(challengeId: String): Box[MappedExpectedChallengeAnswer] =
      MappedExpectedChallengeAnswer.find(By(MappedExpectedChallengeAnswer.mChallengeId,challengeId))

  override def getChallengesByTransactionRequestId(transactionRequestId: String): Box[List[ChallengeTrait]] =
    Full(MappedExpectedChallengeAnswer.findAll(By(MappedExpectedChallengeAnswer.mTransactionRequestId,transactionRequestId)))
  
  override def getChallengesByConsentId(consentId: String): Box[List[ChallengeTrait]] =
    Full(MappedExpectedChallengeAnswer.findAll(By(MappedExpectedChallengeAnswer.mConsentId,consentId)))
  
  override def validateChallenge(
    challengeId: String,
    challengeAnswer: String,
    userId: Option[String]
  ): Box[ChallengeTrait] = {
    
    val challenge = getChallenge(challengeId).openOrThrowException(s"${ErrorMessages.InvalidChallengeAnswer}")
    val currentAttemptCounterValue = challenge.attemptCounter 
    //We update the counter anyway.
    challenge.mAttemptCounter(currentAttemptCounterValue+1).saveMe()
    
    val createDateTime = challenge.createdAt.get
    val challengeTTL : Long = Helpers.seconds(APIUtil.transactionRequestChallengeTtl)
    
    val expiredDateTime: Long = createDateTime.getTime+challengeTTL
    val currentTime: Long = Platform.currentTime
    
    //TODO, add column maxAttemptsAllowed (Int) to `mappedexpectedchallengeanswer`  table instead of this hardcode number 3
    if(currentAttemptCounterValue <3){
      if(expiredDateTime > currentTime) {
        val currentHashedAnswer = BCrypt.hashpw(challengeAnswer, challenge.salt).substring(0, 44)
        val expectedHashedAnswer = challenge.expectedAnswer
    
        userId match {
          case None => 
            if(currentHashedAnswer==expectedHashedAnswer) {
              tryo{challenge.mSuccessful(true).mScaStatus(StrongCustomerAuthenticationStatus.finalised.toString).saveMe()}
            } else {
              Failure(s"${ErrorMessages.InvalidChallengeAnswer}")
            }
          case Some(id) =>
            if(currentHashedAnswer==expectedHashedAnswer && id==challenge.expectedUserId) {
              tryo{challenge.mSuccessful(true).mScaStatus(StrongCustomerAuthenticationStatus.finalised.toString).saveMe()}
            } else {
              Failure(s"${ErrorMessages.InvalidChallengeAnswer}")
            }
        }
      }else{
        Failure(s"${ErrorMessages.OneTimePasswordExpired} Current expiration time is $transactionRequestChallengeTtl seconds")
      }
  }else{
      Failure(s"${ErrorMessages.AllowedAttemptsUsedUp}")
    }
  }
}