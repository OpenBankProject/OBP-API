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
    consentId: Option[String], // Note: consentId and transactionRequestId and basketId are exclusive here.
    basketId: Option[String], // Note: consentId and transactionRequestId and basketId are exclusive here.
    authenticationMethodId: Option[String],
    challengeType: String,
    ) =>
      logger.debug(s"saveChallenge($challengeId, $transactionRequestId, $salt, $expectedAnswer, $expectedUserId)")
      sender ! (mapper.saveChallenge(challengeId, transactionRequestId, salt, expectedAnswer, expectedUserId, scaMethod, scaStatus, consentId, basketId, authenticationMethodId, challengeType: String))

    case cc.getChallenge(challengeId: String) =>
      logger.debug(s"getChallenge($challengeId)")
      sender ! (mapper.getChallenge(challengeId: String))

    case cc.validateChallenge(challengeId: String, challengeAnswer: String, userId: Option[String])=>
      logger.debug(s"validateChallenge($challengeId, $challengeAnswer, $userId)")
      sender ! (mapper.validateChallenge(challengeId, challengeAnswer, userId))  
      
    case cc.getChallengesByTransactionRequestId(transactionRequestId: String)=>
      logger.debug(s"getChallengesByTransactionRequestId($transactionRequestId)")
      sender ! (mapper.getChallengesByTransactionRequestId(transactionRequestId))
      
    case cc.getChallengesByConsentId(consentId: String)=>
      logger.debug(s"getChallengesByConsentId($consentId)")
      sender ! (mapper.getChallengesByConsentId(consentId))  

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


