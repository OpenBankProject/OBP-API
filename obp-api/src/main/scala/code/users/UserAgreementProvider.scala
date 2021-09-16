package code.users

import java.util.Date

import code.api.util.APIUtil
import code.remotedata.RemotedataUserAgreement
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object UserAgreementProvider extends SimpleInjector {

  val userAgreementProvider = new Inject(buildOne _) {}

  def buildOne: UserAgreementProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedUserAgreementProvider
      case true => RemotedataUserAgreement     // We will use Akka as a middleware
    }

}

trait UserAgreementProvider {
  def createOrUpdateUserAgreement(userId: String, agreementType: String, agreementText: String): Box[UserAgreement]
  def createUserAgreement(userId: String, agreementType: String, agreementText: String): Box[UserAgreement]
  def getUserAgreement(userId: String, agreementType: String): Box[UserAgreement]
}

class RemotedataUserAgreementProviderCaseClass {
  case class createOrUpdateUserAgreement(userId: String, agreementType: String, agreementText: String)
  case class createUserAgreement(userId: String, agreementType: String, agreementText: String)
  case class getUserAgreement(userId: String, agreementType: String)
}

object RemotedataUserAgreementProviderCaseClass extends RemotedataUserAgreementProviderCaseClass

trait UserAgreementTrait {
  def userInvitationId: String
  def userId: String
  def agreementType: String
  def agreementText: String
  def agreementHash: String
  def date: Date
}