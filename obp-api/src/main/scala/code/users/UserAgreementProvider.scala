package code.users

import java.util.Date

import code.api.util.APIUtil
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object UserAgreementProvider extends SimpleInjector {

  val userAgreementProvider = new Inject(buildOne _) {}

  def buildOne: UserAgreementProvider = MappedUserAgreementProvider

}

trait UserAgreementProvider {
  def createUserAgreement(userId: String, agreementType: String, agreementText: String): Box[UserAgreement]
  def getLastUserAgreement(userId: String, agreementType: String): Box[UserAgreement]
}

trait UserAgreementTrait {
  def userInvitationId: String
  def userId: String
  def agreementType: String
  def agreementText: String
  def agreementHash: String
  def date: Date
}