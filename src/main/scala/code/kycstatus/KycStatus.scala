package code.kycstatuses

import java.util.Date

import code.model.{BankId, User}
import net.liftweb.util.SimpleInjector


object KycStatuses extends SimpleInjector {

  val kycStatusProvider = new Inject(buildOne _) {}

  def buildOne: KycStatusProvider = MappedKycStatusesProvider

}

trait KycStatusProvider {

  def getKycStatuses(customerNumber: String) : List[KycStatus]

  def addKycStatus(customerNumber: String, ok: Boolean, date: Date) : Boolean

}

trait KycStatus {
  def customerNumber : String
  def ok : Boolean
  def date : Date
}