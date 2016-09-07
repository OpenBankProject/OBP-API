package code.kycchecks

import java.util.Date

import code.model.{BankId, User}
import code.model.dataAccess.APIUser
import code.util.{DefaultStringField}
import net.liftweb.mapper._

object MappedKycChecksProvider extends KycCheckProvider {

  override def getKycChecks(customerNumber: String): List[MappedKycCheck] = {
    MappedKycCheck.findAll(
      By(MappedKycCheck.mCustomerNumber, customerNumber),
      OrderBy(MappedKycCheck.updatedAt, Descending))
  }


  override def addKycChecks(id: String, customerNumber: String, date: Date, how: String, staffUserId: String, mStaffName: String, mSatisfied: Boolean, comments: String): Boolean = {
    MappedKycCheck.create
      .mId(id)
      .mCustomerNumber(customerNumber)
      .mDate(date)
      .mHow(how)
      .mStaffUserId(staffUserId)
      .mStaffName(mStaffName)
      .mSatisfied(mSatisfied)
      .mComments(comments)
      .save()
  }
}

class MappedKycCheck extends KycCheck
with LongKeyedMapper[MappedKycCheck] with IdPK with CreatedUpdated {

  def getSingleton = MappedKycCheck

  object user extends MappedLongForeignKey(this, APIUser)
  object bank extends DefaultStringField(this)

  object mId extends DefaultStringField(this)
  object mCustomerNumber extends DefaultStringField(this)
  object mDate extends MappedDateTime(this)
  object mHow extends DefaultStringField(this)
  object mStaffUserId extends DefaultStringField(this)
  object mStaffName extends DefaultStringField(this)
  object mSatisfied extends MappedBoolean(this)
  object mComments extends DefaultStringField(this)



  override def idKycCheck: String = mId.get
  override def customerNumber: String = mCustomerNumber.get
  override def date: Date = mDate.get
  override def how: String = mHow.get
  override def staffUserId: String = mStaffUserId.get
  override def staffName: String = mStaffName.get
  override def satisfied: Boolean = mSatisfied.get
  override def comments: String = mComments.get
}

object MappedKycCheck extends MappedKycCheck with LongKeyedMetaMapper[MappedKycCheck] {
  override def dbIndexes = UniqueIndex(mId) :: super.dbIndexes
}