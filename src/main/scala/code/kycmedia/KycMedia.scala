package code.kycmedias

import java.util.Date

import code.model.{BankId, User}
import net.liftweb.util.SimpleInjector


object KycMedias extends SimpleInjector {

  val kycMediaProvider = new Inject(buildOne _) {}

  def buildOne: KycMediaProvider = MappedKycMediasProvider

}

trait KycMediaProvider {

  def getKycMedias(customerNumber: String) : List[KycMedia]

  def addKycMedias(id: String, customerNumber: String, `type`: String, url: String, date: Date, relatesToKycDocumentId: String, relatesToKycCheckId: String) : Boolean

}

trait KycMedia {
  def idKycMedia : String
  def customerNumber : String
  def `type` : String
  def url : String
  def date : Date
  def relatesToKycDocumentId : String
  def relatesToKycCheckId : String
}