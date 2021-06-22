package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.users.{RemotedataUserAgreementProviderCaseClass, UserAgreement, UserAgreementProvider}
import net.liftweb.common._


object RemotedataUserAgreement extends ObpActorInit with UserAgreementProvider {

  val cc = RemotedataUserAgreementProviderCaseClass

  def createUserAgreement(userId: String, summary: String, agreementText: String): Box[UserAgreement] =  getValueFromFuture(
    (actor ? cc.createUserAgreement(userId, summary, agreementText)).mapTo[Box[UserAgreement]]
  )
}
