package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.users.{MappedUserAgreementProvider, RemotedataUserAgreementProviderCaseClass}
import code.util.Helper.MdcLoggable

class RemotedataUserAgreementActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedUserAgreementProvider
  val cc = RemotedataUserAgreementProviderCaseClass

  def receive: PartialFunction[Any, Unit] = {

    case cc.createUserAgreement(userId: String, summary: String, agreementText: String) =>
      logger.debug(s"createUserAgreement($userId, $summary, $agreementText)")
      sender ! (mapper.createUserAgreement(userId, summary, agreementText))
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

