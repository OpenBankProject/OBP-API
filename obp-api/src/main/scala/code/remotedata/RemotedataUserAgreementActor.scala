package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.users.{MappedUserAgreementProvider, RemotedataUserAgreementProviderCaseClass}
import code.util.Helper.MdcLoggable

class RemotedataUserAgreementActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedUserAgreementProvider
  val cc = RemotedataUserAgreementProviderCaseClass

  def receive: PartialFunction[Any, Unit] = {

    case cc.createOrUpdateUserAgreement(userId: String, summary: String, agreementText: String, acceptMarketingInfo: Boolean) =>
      logger.debug(s"createOrUpdateUserAgreement($userId, $summary, $agreementText, $acceptMarketingInfo)")
      sender ! (mapper.createOrUpdateUserAgreement(userId, summary, agreementText, acceptMarketingInfo))
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

