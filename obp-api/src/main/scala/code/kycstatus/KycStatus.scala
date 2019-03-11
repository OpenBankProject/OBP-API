package code.kycstatuses

import java.util.Date
import net.liftweb.util.SimpleInjector
import net.liftweb.common.{Box}


object KycStatuses extends SimpleInjector {

  val kycStatusProvider = new Inject(buildOne _) {}

  def buildOne: KycStatusProvider = MappedKycStatusesProvider

}

trait KycStatusProvider {

  def getKycStatuses(customerId: String) : List[KycStatus]

  def addKycStatus(bankId: String, customerId: String, customerNumber: String, ok: Boolean, date: Date) : Box[KycStatus]

}

trait KycStatus {
  def bankId: String
  def customerId: String
  def customerNumber : String
  def ok : Boolean
  def date : Date
}