package code.kycdocuments

import java.util.Date
import net.liftweb.util.SimpleInjector
import net.liftweb.common.{Box}


object KycDocuments extends SimpleInjector {

  val kycDocumentProvider = new Inject(buildOne _) {}

  def buildOne: KycDocumentProvider = MappedKycDocumentsProvider

}

trait KycDocumentProvider {

  def getKycDocuments(customerId: String) : List[KycDocument]

  def addKycDocuments(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, number: String, issueDate: Date, issuePlace: String, expiryDate: Date) : Box[KycDocument]

}

trait KycDocument {
  def bankId: String
  def customerId: String
  def idKycDocument : String
  def customerNumber : String
  def `type` : String
  def number : String
  def issueDate : Date
  def issuePlace : String
  def expiryDate : Date
}