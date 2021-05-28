package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.users.{RemotedataUserInvitationProviderCaseClass, UserInvitation, UserInvitationProvider}
import net.liftweb.common._


object RemotedataUserInvitation extends ObpActorInit with UserInvitationProvider {

  val cc = RemotedataUserInvitationProviderCaseClass

  def createUserInvitation(firstName: String, lastName: String, email: String, company: String, country: String): Box[UserInvitation] =  getValueFromFuture(
    (actor ? cc.createUserInvitation(firstName, lastName, email, company, country)).mapTo[Box[UserInvitation]]
  )
}
