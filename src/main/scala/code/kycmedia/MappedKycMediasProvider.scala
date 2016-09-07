package code.kycmedias

import java.util.Date

import code.model.{BankId, User}
import code.model.dataAccess.APIUser
import code.util.{DefaultStringField}
import net.liftweb.mapper._

object MappedKycMediasProvider extends KycMediaProvider {

  override def getKycMedias(customerNumber: String): List[MappedKycMedia] = {
    MappedKycMedia.findAll(
      By(MappedKycMedia.mCustomerNumber, customerNumber),
      OrderBy(MappedKycMedia.updatedAt, Descending))
  }


  override def addKycMedias(id: String, customerNumber: String, `type`: String, url: String, date: Date, relatesToKycDocumentId: String, relatesToKycCheckId: String): Boolean = {
    MappedKycMedia.create
      .mId(id)
      .mCustomerNumber(customerNumber)
      .mType(`type`)
      .mUrl(url)
      .mDate(date)
      .mRelatesToKycDocumentId(relatesToKycDocumentId)
      .mRelatesToKycCheckId(relatesToKycCheckId)
      .save()
  }
}

class MappedKycMedia extends KycMedia
with LongKeyedMapper[MappedKycMedia] with IdPK with CreatedUpdated {

  def getSingleton = MappedKycMedia

  object user extends MappedLongForeignKey(this, APIUser)
  object bank extends DefaultStringField(this)

  object mId extends DefaultStringField(this)
  object mCustomerNumber extends DefaultStringField(this)
  object mType extends DefaultStringField(this)
  object mUrl extends DefaultStringField(this)
  object mDate extends MappedDateTime(this)
  object mRelatesToKycDocumentId extends DefaultStringField(this)
  object mRelatesToKycCheckId extends DefaultStringField(this)


  override def idKycMedia: String = mId.get
  override def customerNumber: String = mCustomerNumber.get
  override def `type`: String = mType.get
  override def url: String = mUrl.get
  override def date: Date = mDate.get
  override def relatesToKycDocumentId: String = mRelatesToKycDocumentId.get
  override def relatesToKycCheckId: String = mRelatesToKycCheckId.get
}

object MappedKycMedia extends MappedKycMedia with LongKeyedMetaMapper[MappedKycMedia] {
  override def dbIndexes = UniqueIndex(mId) :: super.dbIndexes
}