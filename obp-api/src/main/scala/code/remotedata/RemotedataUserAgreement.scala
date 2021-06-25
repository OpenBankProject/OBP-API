package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.users.{RemotedataUserAgreementProviderCaseClass, UserAgreement, UserAgreementProvider}
import net.liftweb.common._


object RemotedataUserAgreement extends ObpActorInit with UserAgreementProvider {

  val cc = RemotedataUserAgreementProviderCaseClass

  def createOrUpdateUserAgreement(userId: String, summary: String, agreementText: String, acceptMarketingInfo: Boolean): Box[UserAgreement] =  getValueFromFuture(
    (actor ? cc.createOrUpdateUserAgreement(userId, summary, agreementText, acceptMarketingInfo)).mapTo[Box[UserAgreement]]
  )
}
