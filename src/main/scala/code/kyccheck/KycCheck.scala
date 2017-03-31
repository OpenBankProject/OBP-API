package code.kycchecks

import java.util.Date
import net.liftweb.util.SimpleInjector
import net.liftweb.common.{Box}


object KycChecks extends SimpleInjector {

  val kycCheckProvider = new Inject(buildOne _) {}

  def buildOne: KycCheckProvider = MappedKycChecksProvider

}

trait KycCheckProvider {

  def getKycChecks(customerId: String) : List[KycCheck]

  def addKycChecks(bankId: String, customerId: String, id: String, customerNumber: String, date: Date, how: String, staffUserId: String, mStaffName: String, mSatisfied: Boolean, comments: String) : Box[KycCheck]

}

trait KycCheck {
  def bankId: String
  def customerId: String
  def idKycCheck : String
  def customerNumber : String
  def date : Date
  def how : String
  def staffUserId : String
  def staffName : String
  def satisfied: Boolean
  def comments : String
}