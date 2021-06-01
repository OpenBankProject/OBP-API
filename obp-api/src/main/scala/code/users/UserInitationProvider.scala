package code.users

import code.api.util.APIUtil
import code.remotedata.RemotedataUserInvitation
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object UserInvitationProvider extends SimpleInjector {

  val userInvitationProvider = new Inject(buildOne _) {}

  def buildOne: UserInvitationProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedUserInvitationProvider
      case true => RemotedataUserInvitation     // We will use Akka as a middleware
    }

}

trait UserInvitationProvider {
  def createUserInvitation(firstName: String, lastName: String, email: String, company: String, country: String, purpose: String): Box[UserInvitation]
  def getUserInvitation(secretLink: Long): Box[UserInvitation]
}

class RemotedataUserInvitationProviderCaseClass {
  case class createUserInvitation(firstName: String, lastName: String, email: String, company: String, country: String, purpose: String)
  case class getUserInvitation(secretLink: Long)
}

object RemotedataUserInvitationProviderCaseClass extends RemotedataUserInvitationProviderCaseClass

trait UserInvitationTrait {
  def userInvitationId: String
  def firstName: String
  def lastName: String
  def email: String
  def company: String
  def country: String
  def status: String
  def purpose: String
  def secretLink: Long
}