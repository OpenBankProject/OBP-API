package code.kycdocuments

import java.util.Date

import code.model.{BankId, User}
import code.model.dataAccess.APIUser
import code.util.{DefaultStringField}
import net.liftweb.mapper._

object MappedKycDocumentsProvider extends KycDocumentProvider {

  // TODO Add bankId (customerNumber is not unique)
  override def getKycDocuments(customerNumber: String): List[MappedKycDocument] = {
    MappedKycDocument.findAll(
      By(MappedKycDocument.mCustomerNumber, customerNumber),
      OrderBy(MappedKycDocument.updatedAt, Descending))
  }


  override def addKycDocuments(id: String, customerNumber: String, `type`: String, number: String, issueDate: Date, issuePlace: String, expiryDate: Date): Boolean = {
    MappedKycDocument.create
      .mId(id)
      .mCustomerNumber(customerNumber)
      .mType(`type`)
      .mNumber(number)
      .mIssueDate(issueDate)
      .mIssuePlace(issuePlace)
      .mExpiryDate(expiryDate)
      .save()
  }
}

class MappedKycDocument extends KycDocument
with LongKeyedMapper[MappedKycDocument] with IdPK with CreatedUpdated {

  def getSingleton = MappedKycDocument

  object user extends MappedLongForeignKey(this, APIUser)
  object bank extends DefaultStringField(this)

  object mId extends DefaultStringField(this)
  object mCustomerNumber extends DefaultStringField(this)
  object mType extends DefaultStringField(this)
  object mNumber extends DefaultStringField(this)
  object mIssueDate extends MappedDateTime(this)
  object mIssuePlace extends DefaultStringField(this)
  object mExpiryDate extends MappedDateTime(this)


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