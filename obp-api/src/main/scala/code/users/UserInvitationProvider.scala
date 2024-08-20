package code.users

import com.openbankproject.commons.model.BankId
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object UserInvitationProvider extends SimpleInjector {

  val userInvitationProvider = new Inject(buildOne _) {}

  def buildOne: UserInvitationProvider = MappedUserInvitationProvider

}

trait UserInvitationProvider {
  def createUserInvitation(bankId: BankId, firstName: String, lastName: String, email: String, company: String, country: String, purpose: String): Box[UserInvitation]
  def getUserInvitationBySecretLink(secretLink: Long): Box[UserInvitation]
  def scrambleUserInvitation(userInvitationId: String): Box[Boolean]
  def updateStatusOfUserInvitation(userInvitationId: String, status: String): Box[Boolean]
  def getUserInvitation(bankId: BankId, secretLink: Long): Box[UserInvitation]
  def getUserInvitations(bankId: BankId): Box[List[UserInvitation]]
}

trait UserInvitationTrait {
  def userInvitationId: String
  def bankId: String
  def firstName: String
  def lastName: String
  def email: String
  def company: String
  def country: String
  def status: String
  def purpose: String
  def secretKey: Long
}