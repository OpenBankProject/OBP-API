package code.customer

import java.util.Date

import code.model.{BankId, User}
import code.model.dataAccess.APIUser
import code.util.DefaultStringField
import net.liftweb.common.Box
import net.liftweb.mapper._

object MappedCustomerProvider extends CustomerProvider {

  override def getCustomer(bankId : BankId, user: User): Box[Customer] = {
    MappedCustomer.find(
      By(MappedCustomer.mUser, user.apiId.value),
      By(MappedCustomer.mBank, bankId.value))
  }

  override def getUser(bankId: BankId, customerNumber: String): Box[User] = {
    MappedCustomer.find(
      By(MappedCustomer.mBank, bankId.value),
      By(MappedCustomer.mNumber, customerNumber)
    ).flatMap(_.mUser.obj)
  }
}

class MappedCustomer extends Customer with LongKeyedMapper[MappedCustomer] with IdPK with CreatedUpdated {

  def getSingleton = MappedCustomer

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

object MappedCustomer extends MappedCustomer with LongKeyedMetaMapper[MappedCustomer] {
  //one customer info per bank for each api user
  override def dbIndexes = UniqueIndex(mBank, mNumber) :: UniqueIndex(mUser, mBank) :: super.dbIndexes
}