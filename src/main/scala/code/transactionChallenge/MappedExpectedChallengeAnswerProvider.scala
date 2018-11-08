package code.transactionChallenge

import net.liftweb.common.{Box, Full}
import net.liftweb.mapper.By
import net.liftweb.util.BCrypt
import net.liftweb.util.Helpers.tryo

object MappedExpectedChallengeAnswerProvider extends ExpectedChallengeAnswerProvider {
  
  override def saveExpectedChallengeAnswer(
    challengeId: String,
    salt: String,
    expectedAnswer: String
  ): Box[ExpectedChallengeAnswer] = 
    tryo (
      MappedExpectedChallengeAnswer
        .create
        .mChallengeId(challengeId)
        .mSalt(salt)
        .mExpectedAnswer(expectedAnswer)
        .saveMe()
    )
  
  override def getExpectedChallengeAnswer(challengeId: String): Box[ExpectedChallengeAnswer] =
      MappedExpectedChallengeAnswer.find(By(MappedExpectedChallengeAnswer.mChallengeId,challengeId))
  
  override def validateChallengeAnswerInOBPSide(
    challengeId: String,
    challengeAnswer: String
  ): Box[Boolean] = {
    
    val expectedChallengeAnswer = getExpectedChallengeAnswer(challengeId).openOrThrowException("No expectedChallengeAnswer, just for debug !!!")
    
    val currentHashedAnswer = BCrypt.hashpw(challengeAnswer, expectedChallengeAnswer.salt).substring(0, 44)
    val expectedHashedAnswer = expectedChallengeAnswer.expectedAnswer
  
    Full(currentHashedAnswer==expectedHashedAnswer)
    
  }
}