package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.transactionChallenge.{MappedExpectedChallengeAnswerProvider, RemotedataExpectedChallengeAnswerProviderCaseClasses}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus

class RemotedataExpectedChallengeAnswerActor extends Actor with ObpActorHelper with MdcLoggable {
  
  val mapper = MappedExpectedChallengeAnswerProvider
  val cc = RemotedataExpectedChallengeAnswerProviderCaseClasses

  def receive = {

    case cc.saveExpectedChallengeAnswer(challengeId: String, transactionRequestId: String, salt: String, expectedAnswer: String, expectedUserId: String,
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],
    consentId: Option[String], // Note: consentId and transactionRequestId are exclusive here.
    authenticationMethodId: Option[String]
    ) =>
      logger.debug(s"saveExpectedChallengeAnswer($challengeId, $transactionRequestId, $salt, $expectedAnswer, $expectedUserId)")
      sender ! (mapper.saveExpectedChallengeAnswer(challengeId, transactionRequestId, salt, expectedAnswer, expectedUserId, scaMethod, scaStatus, consentId, authenticationMethodId))

    case cc.getExpectedChallengeAnswer(challengeId: String) =>
      logger.debug(s"getExpectedChallengeAnswer($challengeId)")
      sender ! (mapper.getExpectedChallengeAnswer(challengeId: String))

    case cc.validateChallengeAnswer(challengeId: String, challengeAnswer: String, userId: Option[String])=>
      logger.debug(s"validateChallengeAnswer($challengeId, $challengeAnswer, $userId)")
      sender ! (mapper.validateChallengeAnswer(challengeId, challengeAnswer, userId))  

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


