package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.users.{MappedUserAgreementProvider, RemotedataUserAgreementProviderCaseClass}
import code.util.Helper.MdcLoggable

class RemotedataUserAgreementActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedUserAgreementProvider
  val cc = RemotedataUserAgreementProviderCaseClass

  def receive: PartialFunction[Any, Unit] = {

    case cc.createOrUpdateUserAgreement(userId: String, agreementType: String, agreementText: String) =>
      logger.debug(s"createOrUpdateUserAgreement($userId, $agreementType, $agreementText)")
      sender ! (mapper.createOrUpdateUserAgreement(userId, agreementType, agreementText))
      
    case cc.createUserAgreement(userId: String, agreementType: String, agreementText: String) =>
      logger.debug(s"createUserAgreement($userId, $agreementType, $agreementText)")
      sender ! (mapper.createUserAgreement(userId, agreementType, agreementText))
      
    case cc.getUserAgreement(userId: String, agreementType: String) =>
      logger.debug(s"getUserAgreement($userId, $agreementType)")
      sender ! (mapper.getUserAgreement(userId, agreementType))
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

