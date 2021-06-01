package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.users.{MappedUserInvitationProvider, RemotedataUserInvitationProviderCaseClass}
import code.util.Helper.MdcLoggable

class RemotedataUserInvitationActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedUserInvitationProvider
  val cc = RemotedataUserInvitationProviderCaseClass

  def receive: PartialFunction[Any, Unit] = {

    case cc.createUserInvitation(firstName: String, lastName: String, email: String, company: String, country: String, purpose: String) =>
      logger.debug(s"createUserCustomerLink($firstName, $lastName, $email, $company, $country, $purpose)")
      sender ! (mapper.createUserInvitation(firstName, lastName, email, company, country, purpose))
      
    case cc.getUserInvitation(secretLink: Long) =>
      logger.debug(s"createUserCustomerLink($secretLink)")
      sender ! (mapper.getUserInvitation(secretLink))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

