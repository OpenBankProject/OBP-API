package code.kycmedias

import java.util.Date

import code.util.{UUIDString}
import net.liftweb.common.{Box,Full}
import net.liftweb.mapper._

object MappedKycMediasProvider extends KycMediaProvider {

  override def getKycMedias(customerNumber: String): List[MappedKycMedia] = {
    MappedKycMedia.findAll(
      By(MappedKycMedia.mCustomerNumber, customerNumber),
      OrderBy(MappedKycMedia.updatedAt, Descending))
  }


  override def addKycMedias(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, url: String, date: Date, relatesToKycDocumentId: String, relatesToKycCheckId: String): Box[KycMedia] = {
    val kyc_media = MappedKycMedia.find(By(MappedKycMedia.mId, id)) match {
      case Full(media) => media
        .mId(id)
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mCustomerNumber(customerNumber)
        .mType(`type`)
        .mUrl(url)
        .mDate(date)
        .mRelatesToKycDocumentId(relatesToKycDocumentId)
        .mRelatesToKycCheckId(relatesToKycCheckId)
        .saveMe()
      case _ => MappedKycMedia.create
        .mId(id)
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mCustomerNumber(customerNumber)
        .mType(`type`)
        .mUrl(url)
        .mDate(date)
        .mRelatesToKycDocumentId(relatesToKycDocumentId)
        .mRelatesToKycCheckId(relatesToKycCheckId)
        .saveMe()
    }
    Full(kyc_media)
  }
}

class MappedKycMedia extends KycMedia
with LongKeyedMapper[MappedKycMedia] with IdPK with CreatedUpdated {

  def getSingleton = MappedKycMedia

  object mBankId extends UUIDString(this)
  object mCustomerId extends UUIDString(this)

  object mId extends UUIDString(this)
  object mCustomerNumber extends UUIDString(this)
  object mType extends MappedString(this, 50)
  object mUrl extends MappedString(this, 255) // Long enough for a URL ? 2000 might be safer
  object mDate extends MappedDateTime(this)
  object mRelatesToKycDocumentId extends MappedString(this, 255)
  object mRelatesToKycCheckId extends MappedString(this, 255)


  override def bankId: String = mBankId.get
  override def customerId: String = mCustomerId.get
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