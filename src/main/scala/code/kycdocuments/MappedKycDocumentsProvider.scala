package code.kycdocuments

import java.util.Date

import net.liftweb.common.{Box, Full}
import code.model.dataAccess.ResourceUser
import code.util.{DefaultStringField}
import net.liftweb.mapper._

object MappedKycDocumentsProvider extends KycDocumentProvider {

  // TODO Add bankId (customerNumber is not unique)
  override def getKycDocuments(customerId: String): List[MappedKycDocument] = {
    MappedKycDocument.findAll(
      By(MappedKycDocument.mCustomerId, customerId),
      OrderBy(MappedKycDocument.updatedAt, Descending))
  }


  override def addKycDocuments(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, number: String, issueDate: Date, issuePlace: String, expiryDate: Date): Box[MappedKycDocument] = {
    val kyc_document = MappedKycDocument.find(By(MappedKycDocument.mId, id)) match {
      case Full(document) => document
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mId(id)
        .mCustomerNumber(customerNumber)
        .mType(`type`)
        .mNumber(number)
        .mIssueDate(issueDate)
        .mIssuePlace(issuePlace)
        .mExpiryDate(expiryDate)
        .saveMe()
      case _ => MappedKycDocument.create
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mId(id)
        .mCustomerNumber(customerNumber)
        .mType(`type`)
        .mNumber(number)
        .mIssueDate(issueDate)
        .mIssuePlace(issuePlace)
        .mExpiryDate(expiryDate)
        .saveMe()
    }
    Full(kyc_document)
  }
}

class MappedKycDocument extends KycDocument
with LongKeyedMapper[MappedKycDocument] with IdPK with CreatedUpdated {

  def getSingleton = MappedKycDocument

  object user extends MappedLongForeignKey(this, ResourceUser)
  object mBankId extends MappedString(this, 255)
  object mCustomerId extends MappedString(this, 255)

  object mId extends DefaultStringField(this)
  object mCustomerNumber extends DefaultStringField(this)
  object mType extends DefaultStringField(this)
  object mNumber extends DefaultStringField(this)
  object mIssueDate extends MappedDateTime(this)
  object mIssuePlace extends DefaultStringField(this)
  object mExpiryDate extends MappedDateTime(this)


  override def bankId: String = mBankId.get
  override def customerId: String = mCustomerId.get
  override def idKycDocument: String = mId.get
  override def customerNumber: String = mCustomerNumber.get
  override def `type`: String = mType.get
  override def number: String = mNumber.get
  override def issueDate: Date = mIssueDate.get
  override def issuePlace: String = mIssuePlace.get
  override def expiryDate: Date = mExpiryDate.get
}

object MappedKycDocument extends MappedKycDocument with LongKeyedMetaMapper[MappedKycDocument] {
  override def dbIndexes = UniqueIndex(mId) :: super.dbIndexes
}