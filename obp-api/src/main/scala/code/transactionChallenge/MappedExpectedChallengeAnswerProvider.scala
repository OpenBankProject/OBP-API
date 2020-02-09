package code.transactionChallenge

import net.liftweb.common.{Box, Full}
import net.liftweb.mapper.By
import org.mindrot.jbcrypt.BCrypt
import net.liftweb.util.Helpers.tryo

object MappedExpectedChallengeAnswerProvider extends ExpectedChallengeAnswerProvider {
  
  override def saveExpectedChallengeAnswer(
    challengeId: String,
    transactionRequestId: String,
    salt: String,
    expectedAnswer: String,
    expectedUserId: String
  ): Box[ExpectedChallengeAnswer] = 
    tryo (
      MappedExpectedChallengeAnswer
        .create
        .mChallengeId(challengeId)
        .mTransactionRequestId(transactionRequestId)
        .mSalt(salt)
        .mExpectedAnswer(expectedAnswer)
        .mExpectedUserId(expectedUserId)
        .saveMe()
    )
  
  override def getExpectedChallengeAnswer(challengeId: String): Box[MappedExpectedChallengeAnswer] =
      MappedExpectedChallengeAnswer.find(By(MappedExpectedChallengeAnswer.mChallengeId,challengeId))
  
  override def validateChallengeAnswerInOBPSide(
    challengeId: String,
    challengeAnswer: String,
    userId: Option[String]
  ): Box[Boolean] = {
    
    val expectedChallengeAnswer = getExpectedChallengeAnswer(challengeId).openOrThrowException("No expectedChallengeAnswer, just for debug !!!")
    
    val currentHashedAnswer = BCrypt.hashpw(challengeAnswer, expectedChallengeAnswer.salt).substring(0, 44)
    val expectedHashedAnswer = expectedChallengeAnswer.expectedAnswer

    userId match {
      case None => 
        if(currentHashedAnswer==expectedHashedAnswer) {
          expectedChallengeAnswer.mSuccessful(true).save
          Full(true)
        } else {
          Full(false)
        }
      case Some(id) => Full(currentHashedAnswer==expectedHashedAnswer && id==expectedChallengeAnswer.expectedUserId)
    }
  }
}