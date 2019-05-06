package code.kycdocuments

import java.util.Date

import com.openbankproject.commons.model.KycDocument
import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box


object KycDocuments extends SimpleInjector {

  val kycDocumentProvider = new Inject(buildOne _) {}

  def buildOne: KycDocumentProvider = MappedKycDocumentsProvider

}

trait KycDocumentProvider {

  def getKycDocuments(customerId: String) : List[KycDocument]

  def addKycDocuments(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, number: String, issueDate: Date, issuePlace: String, expiryDate: Date) : Box[KycDocument]

}