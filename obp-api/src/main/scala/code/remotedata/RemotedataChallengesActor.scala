package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.transactionChallenge.{MappedChallengeProvider, RemotedataChallengeProviderCaseClasses}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus

class RemotedataChallengesActor extends Actor with ObpActorHelper with MdcLoggable {
  
  val mapper = MappedChallengeProvider
  val cc = RemotedataChallengeProviderCaseClasses

  def receive = {

    case cc.saveChallenge(challengeId: String, transactionRequestId: String, salt: String, expectedAnswer: String, expectedUserId: String,
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],
    consentId: Option[String], // Note: consentId and transactionRequestId are exclusive here.
    authenticationMethodId: Option[String]
    ) =>
      logger.debug(s"saveChallenge($challengeId, $transactionRequestId, $salt, $expectedAnswer, $expectedUserId)")
      sender ! (mapper.saveChallenge(challengeId, transactionRequestId, salt, expectedAnswer, expectedUserId, scaMethod, scaStatus, consentId, authenticationMethodId))

    case cc.getChallenge(challengeId: String) =>
      logger.debug(s"getChallenge($challengeId)")
      sender ! (mapper.getChallenge(challengeId: String))

    case cc.validateChallenge(challengeId: String, challengeAnswer: String, userId: Option[String])=>
      logger.debug(s"validateChallenge($challengeId, $challengeAnswer, $userId)")
      sender ! (mapper.validateChallenge(challengeId, challengeAnswer, userId))  
      
    case cc.getChallengesByConsentId(consentId: String)=>
      logger.debug(s"validateChallenge($consentId)")
      sender ! (mapper.getChallengesByConsentId(consentId))  

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


