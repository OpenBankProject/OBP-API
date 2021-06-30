package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.users.{MappedUserInvitationProvider, RemotedataUserInvitationProviderCaseClass}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.BankId

class RemotedataUserInvitationActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedUserInvitationProvider
  val cc = RemotedataUserInvitationProviderCaseClass

  def receive: PartialFunction[Any, Unit] = {

    case cc.createUserInvitation(bankId: BankId, firstName: String, lastName: String, email: String, company: String, country: String, purpose: String) =>
      logger.debug(s"createUserInvitation($bankId, $firstName, $lastName, $email, $company, $country, $purpose)")
      sender ! (mapper.createUserInvitation(bankId, firstName, lastName, email, company, country, purpose))
      
    case cc.getUserInvitationBySecretLink(secretLink: Long) =>
      logger.debug(s"getUserInvitationBySecretLink($secretLink)")
      sender ! (mapper.getUserInvitationBySecretLink(secretLink))
      
    case cc.updateStatusOfUserInvitation(userInvitationId: String, status: String) =>
      logger.debug(s"updateStatusOfUserInvitation($userInvitationId, $status)")
      sender ! (mapper.updateStatusOfUserInvitation(userInvitationId, status)) 
      
    case cc.scrambleUserInvitation(userInvitationId: String) =>
      logger.debug(s"scrambleUserInvitation($userInvitationId)")
      sender ! (mapper.scrambleUserInvitation(userInvitationId))  
      
    case cc.getUserInvitation(bankId: BankId, secretLink: Long) =>
      logger.debug(s"getUserInvitation($bankId, $secretLink)")
      sender ! (mapper.getUserInvitation(bankId, secretLink)) 
      
    case cc.getUserInvitations(bankId: BankId) =>
      logger.debug(s"getUserInvitations($bankId)")
      sender ! (mapper.getUserInvitations(bankId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

