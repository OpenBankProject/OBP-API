package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.users.{RemotedataUserAgreementProviderCaseClass, UserAgreement, UserAgreementProvider}
import net.liftweb.common._


object RemotedataUserAgreement extends ObpActorInit with UserAgreementProvider {

  val cc = RemotedataUserAgreementProviderCaseClass

  def createOrUpdateUserAgreement(userId: String, agreementType: String, agreementText: String): Box[UserAgreement] =  getValueFromFuture(
    (actor ? cc.createOrUpdateUserAgreement(userId, agreementType, agreementText)).mapTo[Box[UserAgreement]]
  )
  def createUserAgreement(userId: String, agreementType: String, agreementText: String): Box[UserAgreement] =  getValueFromFuture(
    (actor ? cc.createOrUpdateUserAgreement(userId, agreementType, agreementText)).mapTo[Box[UserAgreement]]
  )
  def getUserAgreement(userId: String, agreementType: String): Box[UserAgreement] =  getValueFromFuture(
    (actor ? cc.getUserAgreement(userId, agreementType)).mapTo[Box[UserAgreement]]
  )
}
