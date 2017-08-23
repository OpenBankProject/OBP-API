package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.transactionChallenge.{ExpectedChallengeAnswer, ExpectedChallengeAnswerProvider, RemotedataExpectedChallengeAnswerProviderCaseClasses}
import net.liftweb.common._

object RemotedataExpectedChallengeAnswerProvider extends ObpActorInit with ExpectedChallengeAnswerProvider {
  
  val cc = RemotedataExpectedChallengeAnswerProviderCaseClasses
  
  override def saveExpectedChallengeAnswer(challengeId: String, salt: String, expectedAnswer: String): Box[ExpectedChallengeAnswer] =
    extractFutureToBox(actor ? cc.saveExpectedChallengeAnswer(challengeId: String, salt: String, expectedAnswer: String))
  
  override def getExpectedChallengeAnswer(challengeId: String): Box[ExpectedChallengeAnswer] = 
    extractFutureToBox(actor ? cc.getExpectedChallengeAnswer(challengeId: String))
  
  override def validateChallengeAnswerInOBPSide(challengeId: String, challengeAnswer: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.validateChallengeAnswerInOBPSide(challengeId: String, challengeAnswer: String))
  
}
