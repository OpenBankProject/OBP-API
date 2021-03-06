package code.users

import code.api.util.APIUtil
import code.remotedata.{RemotedataUserAgreement, RemotedataUserInvitation}
import com.openbankproject.commons.model.BankId
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
  def createOrUpdateUserAgreement(userId: String, summary: String, agreementText: String, acceptMarketingInfo: Boolean): Box[UserAgreement]
}

class RemotedataUserAgreementProviderCaseClass {
  case class createOrUpdateUserAgreement(userId: String, summary: String, agreementText: String, acceptMarketingInfo: Boolean)
}

object RemotedataUserAgreementProviderCaseClass extends RemotedataUserAgreementProviderCaseClass

trait UserAgreementTrait {
  def userInvitationId: String
  def userId: String
  def summary: String
  def agreementText: String
  def agreementHash: String
  def acceptMarketingInfo: Boolean
}