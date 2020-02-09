package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.transactionChallenge.{ExpectedChallengeAnswer, ExpectedChallengeAnswerProvider, RemotedataExpectedChallengeAnswerProviderCaseClasses}
import net.liftweb.common._

object RemotedataExpectedChallengeAnswerProvider extends ObpActorInit with ExpectedChallengeAnswerProvider {
  
  val cc = RemotedataExpectedChallengeAnswerProviderCaseClasses
  
  override def saveExpectedChallengeAnswer(challengeId: String, salt: String, expectedAnswer: String, expectedUserId: String): Box[ExpectedChallengeAnswer] = getValueFromFuture(
    (actor ? cc.saveExpectedChallengeAnswer(challengeId, salt, expectedAnswer, expectedUserId)).mapTo[Box[ExpectedChallengeAnswer]]
  )
  
  override def getExpectedChallengeAnswer(challengeId: String): Box[ExpectedChallengeAnswer] = getValueFromFuture(
    (actor ? cc.getExpectedChallengeAnswer(challengeId: String)).mapTo[Box[ExpectedChallengeAnswer]]
  )
  
  override def validateChallengeAnswerInOBPSide(challengeId: String, challengeAnswer: String): Box[Boolean] = getValueFromFuture(
    (actor ? cc.validateChallengeAnswerInOBPSide(challengeId: String, challengeAnswer: String)).mapTo[Box[Boolean]]
  )
  
}
