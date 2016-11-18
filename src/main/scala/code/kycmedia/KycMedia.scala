package code.kycmedias

import java.util.Date

import code.model.{BankId, User}
import net.liftweb.util.SimpleInjector
import net.liftweb.common.{Box}


object KycMedias extends SimpleInjector {

  val kycMediaProvider = new Inject(buildOne _) {}

  def buildOne: KycMediaProvider = MappedKycMediasProvider

}

trait KycMediaProvider {

  def getKycMedias(customerNumber: String) : List[KycMedia]

  def addKycMedias(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, url: String, date: Date, relatesToKycDocumentId: String, relatesToKycCheckId: String) : Box[KycMedia]

}

trait KycMedia {
  def bankId: String
  def customerId: String
  def idKycMedia : String
  def customerNumber : String
  def `type` : String
  def url : String
  def date : Date
  def relatesToKycDocumentId : String
  def relatesToKycCheckId : String
}