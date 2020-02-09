package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.transactionChallenge.{MappedExpectedChallengeAnswerProvider, RemotedataExpectedChallengeAnswerProviderCaseClasses}
import code.util.Helper.MdcLoggable

class RemotedataExpectedChallengeAnswerActor extends Actor with ObpActorHelper with MdcLoggable {
  
  val mapper = MappedExpectedChallengeAnswerProvider
  val cc = RemotedataExpectedChallengeAnswerProviderCaseClasses

  def receive = {

    case cc.saveExpectedChallengeAnswer(challengeId: String, salt: String, expectedAnswer: String, expectedUserId: String) =>
      logger.debug(s"saveExpectedChallengeAnswer($challengeId, $salt, $expectedAnswer, $expectedUserId)")
      sender ! (mapper.saveExpectedChallengeAnswer(challengeId, salt, expectedAnswer, expectedUserId))

    case cc.getExpectedChallengeAnswer(challengeId: String) =>
      logger.debug(s"getExpectedChallengeAnswer($challengeId)")
      sender ! (mapper.getExpectedChallengeAnswer(challengeId: String))

    case cc.validateChallengeAnswerInOBPSide(challengeId: String, challengeAnswer: String)=>
      logger.debug(s"validateChallengeAnswerInOBPSide($challengeId, $challengeAnswer)")
      sender ! (mapper.validateChallengeAnswerInOBPSide(challengeId: String, challengeAnswer: String))  

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


