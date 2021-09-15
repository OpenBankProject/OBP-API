package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.users.{RemotedataUserInvitationProviderCaseClass, UserInvitation, UserInvitationProvider}
import com.openbankproject.commons.model.BankId
import net.liftweb.common._


object RemotedataUserInvitation extends ObpActorInit with UserInvitationProvider {

  val cc = RemotedataUserInvitationProviderCaseClass

  def createUserInvitation(bankId: BankId, firstName: String, lastName: String, email: String, company: String, country: String, purpose: String): Box[UserInvitation] =  getValueFromFuture(
    (actor ? cc.createUserInvitation(bankId, firstName, lastName, email, company, country, purpose)).mapTo[Box[UserInvitation]]
  )
  def getUserInvitationBySecretLink(secretLink: Long): Box[UserInvitation] =  getValueFromFuture(
    (actor ? cc.getUserInvitationBySecretLink(secretLink)).mapTo[Box[UserInvitation]]
  )
  def updateStatusOfUserInvitation(userInvitationId: String, status: String): Box[Boolean] =  getValueFromFuture(
    (actor ? cc.updateStatusOfUserInvitation(userInvitationId, status)).mapTo[Box[Boolean]]
  )
  def scrambleUserInvitation(userInvitationId: String): Box[Boolean] =  getValueFromFuture(
    (actor ? cc.scrambleUserInvitation(userInvitationId)).mapTo[Box[Boolean]]
  )
  def getUserInvitation(bankId: BankId, secretLink: Long): Box[UserInvitation] =  getValueFromFuture(
    (actor ? cc.getUserInvitation(bankId, secretLink)).mapTo[Box[UserInvitation]]
  )
  def getUserInvitations(bankId: BankId): Box[List[UserInvitation]] =  getValueFromFuture(
    (actor ? cc.getUserInvitations(bankId)).mapTo[Box[List[UserInvitation]]]
  )
}
