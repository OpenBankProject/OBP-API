package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.transactionChallenge.{MappedExpectedChallengeAnswerProvider, RemotedataExpectedChallengeAnswerProviderCaseClasses}
import code.util.Helper.MdcLoggable

class RemotedataExpectedChallengeAnswerActor extends Actor with ObpActorHelper with MdcLoggable {
  
  val mapper = MappedExpectedChallengeAnswerProvider
  val cc = RemotedataExpectedChallengeAnswerProviderCaseClasses

  def receive = {

    case cc.saveExpectedChallengeAnswer(challengeId: String, transactionRequestId: String, salt: String, expectedAnswer: String, expectedUserId: String) =>
      logger.debug(s"saveExpectedChallengeAnswer($challengeId, $transactionRequestId, $salt, $expectedAnswer, $expectedUserId)")
      sender ! (mapper.saveExpectedChallengeAnswer(challengeId, transactionRequestId, salt, expectedAnswer, expectedUserId))

    case cc.getExpectedChallengeAnswer(challengeId: String) =>
      logger.debug(s"getExpectedChallengeAnswer($challengeId)")
      sender ! (mapper.getExpectedChallengeAnswer(challengeId: String))

    case cc.validateChallengeAnswerInOBPSide(challengeId: String, challengeAnswer: String, userId: Option[String])=>
      logger.debug(s"validateChallengeAnswerInOBPSide($challengeId, $challengeAnswer, $userId)")
      sender ! (mapper.validateChallengeAnswerInOBPSide(challengeId, challengeAnswer, userId))  

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


