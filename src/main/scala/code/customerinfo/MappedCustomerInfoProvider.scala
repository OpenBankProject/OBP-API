package code.customerinfo

import java.util.Date

import code.model.{BankId, User}
import code.model.dataAccess.APIUser
import net.liftweb.common.Box
import net.liftweb.mapper._

object MappedCustomerInfoProvider extends CustomerInfoProvider {
  override def getInfo(bankId : BankId, user: User): Box[CustomerInfo] = {
    MappedCustomerInfo.find(
      By(MappedCustomerInfo.mUser, user.apiId.value),
      By(MappedCustomerInfo.mBank, bankId.value))
  }
}

class MappedCustomerInfo extends CustomerInfo with LongKeyedMapper[MappedCustomerInfo] with IdPK with CreatedUpdated {

  def getSingleton = MappedCustomerInfo

  object mUser extends MappedLongForeignKey(this, APIUser)
  object mBank extends MappedText(this)

  object mNumber extends MappedText(this)
  object mMobileNumber extends MappedText(this)
  object mLegalName extends MappedText(this)
  object mEmail extends MappedEmail(this, 200)
  object mFaceImageUrl extends MappedText(this)
  object mFaceImageTime extends MappedDateTime(this)

  override val number: String = mNumber.get
  override val mobileNumber: String = mMobileNumber.get
  override val legalName: String = mLegalName.get
  override val email: String = mEmail.get
  override val faceImage: CustomerFaceImage = new CustomerFaceImage {
    override val date: Date = mFaceImageTime.get
    override val url: String = mFaceImageUrl.get
  }
}

object MappedCustomerInfo extends MappedCustomerInfo with LongKeyedMetaMapper[MappedCustomerInfo] {
  //one customer info per bank for each api user
  override def dbIndexes = UniqueIndex(mUser, mBank) :: super.dbIndexes
}