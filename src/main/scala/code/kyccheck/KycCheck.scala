package code.kycchecks

import java.util.Date
import net.liftweb.util.SimpleInjector


object KycChecks extends SimpleInjector {

  val kycCheckProvider = new Inject(buildOne _) {}

  def buildOne: KycCheckProvider = MappedKycChecksProvider

}

trait KycCheckProvider {

  def getKycChecks(customerNumber: String) : List[KycCheck]

  def addKycChecks(id: String, customerNumber: String, date: Date, how: String, staffUserId: String, mStaffName: String, mSatisfied: Boolean, comments: String) : Boolean

}

trait KycCheck {
  def idKycCheck : String
  def customerNumber : String
  def date : Date
  def how : String
  def staffUserId : String
  def staffName : String
  def satisfied: Boolean
  def comments : String
}