package code.customerinfo

import java.util.Date

import code.model.{BankId, User}
import code.model.dataAccess.APIUser
import code.util.DefaultStringField
import net.liftweb.common.Box
import net.liftweb.mapper._

object MappedCustomerInfoProvider extends CustomerInfoProvider {

  override def getInfo(bankId : BankId, user: User): Box[CustomerInfo] = {
    MappedCustomerInfo.find(
      By(MappedCustomerInfo.mUser, user.apiId.value),
      By(MappedCustomerInfo.mBank, bankId.value))
  }

  override def getUser(bankId: BankId, customerNumber: String): Box[User] = {
    MappedCustomerInfo.find(
      By(MappedCustomerInfo.mBank, bankId.value),
      By(MappedCustomerInfo.mNumber, customerNumber)
    ).flatMap(_.mUser.obj)
  }
}

class MappedCustomerInfo extends CustomerInfo with LongKeyedMapper[MappedCustomerInfo] with IdPK with CreatedUpdated {

  def getSingleton = MappedCustomerInfo

  object mUser extends MappedLongForeignKey(this, APIUser)
  object mBank extends DefaultStringField(this)

  object mNumber extends DefaultStringField(this)
  object mMobileNumber extends DefaultStringField(this)
  object mLegalName extends DefaultStringField(this)
  object mEmail extends MappedEmail(this, 200)
  object mFaceImageUrl extends DefaultStringField(this)
  object mFaceImageTime extends MappedDateTime(this)

  override def number: String = mNumber.get
  override def mobileNumber: String = mMobileNumber.get
  override def legalName: String = mLegalName.get
  override def email: String = mEmail.get
  override def faceImage: CustomerFaceImage = new CustomerFaceImage {
    override def date: Date = mFaceImageTime.get
    override def url: String = mFaceImageUrl.get
  }
}

object MappedCustomerInfo extends MappedCustomerInfo with LongKeyedMetaMapper[MappedCustomerInfo] {
  //one customer info per bank for each api user
  override def dbIndexes = UniqueIndex(mBank, mNumber) :: UniqueIndex(mUser, mBank) :: super.dbIndexes
}