package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.users.{RemotedataUserInvitationProviderCaseClass, UserInvitation, UserInvitationProvider}
import net.liftweb.common._


object RemotedataUserInvitation extends ObpActorInit with UserInvitationProvider {

  val cc = RemotedataUserInvitationProviderCaseClass

  def createUserInvitation(firstName: String, lastName: String, email: String, company: String, country: String, purpose: String): Box[UserInvitation] =  getValueFromFuture(
    (actor ? cc.createUserInvitation(firstName, lastName, email, company, country, purpose)).mapTo[Box[UserInvitation]]
  )
  def getUserInvitation(secretLink: Long): Box[UserInvitation] =  getValueFromFuture(
    (actor ? cc.getUserInvitation(secretLink)).mapTo[Box[UserInvitation]]
  )
}
