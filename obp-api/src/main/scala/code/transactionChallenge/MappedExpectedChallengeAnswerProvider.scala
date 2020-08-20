package code.transactionChallenge

import code.api.util.ErrorMessages
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper.By
import org.mindrot.jbcrypt.BCrypt
import net.liftweb.util.Helpers.tryo

object MappedExpectedChallengeAnswerProvider extends ExpectedChallengeAnswerProvider {
  
  override def saveExpectedChallengeAnswer(
    challengeId: String,
    transactionRequestId: String,
    salt: String,
    expectedAnswer: String,
    expectedUserId: String,
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],
    consentId: Option[String], // Note: consentId and transactionRequestId are exclusive here.
    authenticationMethodId: Option[String], 
  ): Box[ExpectedChallengeAnswer] = 
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
  
  override def getExpectedChallengeAnswer(challengeId: String): Box[MappedExpectedChallengeAnswer] =
      MappedExpectedChallengeAnswer.find(By(MappedExpectedChallengeAnswer.mChallengeId,challengeId))

  override def getExpectedChallengeAnswersByTransactionRequestId(transactionRequestId: String): Box[List[ExpectedChallengeAnswer]] =
    Full(MappedExpectedChallengeAnswer.findAll(By(MappedExpectedChallengeAnswer.mTransactionRequestId,transactionRequestId)))
  
  override def validateChallengeAnswer(
    challengeId: String,
    challengeAnswer: String,
    userId: Option[String]
  ): Box[ExpectedChallengeAnswer] = {
    
    val expectedChallengeAnswer = getExpectedChallengeAnswer(challengeId).openOrThrowException(s"${ErrorMessages.InvalidChallengeAnswer}")
    
    val currentHashedAnswer = BCrypt.hashpw(challengeAnswer, expectedChallengeAnswer.salt).substring(0, 44)
    val expectedHashedAnswer = expectedChallengeAnswer.expectedAnswer

    userId match {
      case None => 
        if(currentHashedAnswer==expectedHashedAnswer) {
          tryo{expectedChallengeAnswer.mSuccessful(true).mScaStatus(StrongCustomerAuthenticationStatus.finalised.toString).saveMe()}
        } else {
          Failure(s"${ErrorMessages.InvalidChallengeAnswer}")
        }
      case Some(id) =>
        if(currentHashedAnswer==expectedHashedAnswer && id==expectedChallengeAnswer.expectedUserId) {
          tryo{expectedChallengeAnswer.mSuccessful(true).mScaStatus(StrongCustomerAuthenticationStatus.finalised.toString).saveMe()}
        } else {
          Failure(s"${ErrorMessages.InvalidChallengeAnswer}")
        }
    }
  }
}