package code.kycdocuments

import java.util.Date
import net.liftweb.util.SimpleInjector


object KycDocuments extends SimpleInjector {

  val kycDocumentProvider = new Inject(buildOne _) {}

  def buildOne: KycDocumentProvider = MappedKycDocumentsProvider

}

trait KycDocumentProvider {

  def getKycDocuments(customerNumber: String) : List[KycDocument]

  def addKycDocuments(id: String, customerNumber: String, `type`: String, number: String, issueDate: Date, issuePlace: String, expiryDate: Date) : Boolean

}

trait KycDocument {
  def idKycDocument : String
  def customerNumber : String
  def `type` : String
  def number : String
  def issueDate : Date
  def issuePlace : String
  def expiryDate : Date
}