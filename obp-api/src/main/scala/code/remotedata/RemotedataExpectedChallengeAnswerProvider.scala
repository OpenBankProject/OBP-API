package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.transactionChallenge.{ExpectedChallengeAnswer, ExpectedChallengeAnswerProvider, RemotedataExpectedChallengeAnswerProviderCaseClasses}
import net.liftweb.common._

object RemotedataExpectedChallengeAnswerProvider extends ObpActorInit with ExpectedChallengeAnswerProvider {
  
  val cc = RemotedataExpectedChallengeAnswerProviderCaseClasses
  
  override def saveExpectedChallengeAnswer(challengeId: String, 
                                           transactionRequestId: String, 
                                           salt: String, 
                                           expectedAnswer: String, 
                                           expectedUserId: String): Box[ExpectedChallengeAnswer] = 
    getValueFromFuture(
      (actor ? cc.saveExpectedChallengeAnswer(challengeId, transactionRequestId, salt, expectedAnswer, expectedUserId))
        .mapTo[Box[ExpectedChallengeAnswer]]
    )
  
  override def getExpectedChallengeAnswer(challengeId: String): Box[ExpectedChallengeAnswer] = getValueFromFuture(
    (actor ? cc.getExpectedChallengeAnswer(challengeId: String)).mapTo[Box[ExpectedChallengeAnswer]]
  )
  
  override def validateChallengeAnswer(challengeId: String, challengeAnswer: String, userId: Option[String]): Box[Boolean] = getValueFromFuture(
    (actor ? cc.validateChallengeAnswer(challengeId, challengeAnswer, userId)).mapTo[Box[Boolean]]
  )
  
}
