package code.transactionChallenge

import net.liftweb.common.{Box, Full}
import net.liftweb.mapper.By
import net.liftweb.util.BCrypt

object MappedExpectedChallengeAnswerProvider extends ExpectedChallengeAnswerProvider {
  
  override def saveExpectedChallengeAnswer(
    challengeId: String,
    salt: String,
    challengeAnswerHashed: String
  ): Box[ExpectedChallengeAnswer] = 
    Full(
      MappedExpectedChallengeAnswer
        .create
        .mChallengeId(challengeId)
        .mSalt(salt)
        .mEncryptedAnswer(challengeAnswerHashed)
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
    val expectedHashedAnswer = expectedChallengeAnswer.encryptedAnswer
  
    Full(currentHashedAnswer==expectedHashedAnswer)
    
  }
}