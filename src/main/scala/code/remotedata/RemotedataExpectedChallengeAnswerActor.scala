package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.transactionChallenge.{MappedExpectedChallengeAnswerProvider, RemotedataExpectedChallengeAnswerProviderCaseClasses}
import code.util.Helper.MdcLoggable

class RemotedataExpectedChallengeAnswerActor extends Actor with ObpActorHelper with MdcLoggable {
  
  val mapper = MappedExpectedChallengeAnswerProvider
  val cc = RemotedataExpectedChallengeAnswerProviderCaseClasses

  def receive = {

    case cc.saveExpectedChallengeAnswer(challengeId: String, salt: String, expectedAnswer: String) =>
      logger.debug("saveExpectedChallengeAnswer(" + challengeId +","+salt+","+expectedAnswer+")")
      sender ! extractResult(mapper.saveExpectedChallengeAnswer(challengeId: String, salt: String, expectedAnswer: String))

    case cc.getExpectedChallengeAnswer(challengeId: String) =>
      logger.debug("getExpectedChallengeAnswer(" + challengeId+")")
      sender ! extractResult(mapper.getExpectedChallengeAnswer(challengeId: String))

    case cc.validateChallengeAnswerInOBPSide(challengeId: String, challengeAnswer: String)=>
      logger.debug("validateChallengeAnswerInOBPSide(" + challengeId+","+challengeAnswer+")")
      sender ! extractResult(mapper.validateChallengeAnswerInOBPSide(challengeId: String, challengeAnswer: String))  

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


