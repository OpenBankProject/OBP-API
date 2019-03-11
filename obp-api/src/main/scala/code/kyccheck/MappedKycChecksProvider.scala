package code.kycchecks

import java.util.Date
import code.model.dataAccess.ResourceUser
import code.util.{UUIDString}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._

object MappedKycChecksProvider extends KycCheckProvider {

  override def getKycChecks(customerId: String): List[MappedKycCheck] = {
    MappedKycCheck.findAll(
      By(MappedKycCheck.mCustomerId, customerId),
      OrderBy(MappedKycCheck.updatedAt, Descending))
  }


  override def addKycChecks(bankId: String, customerId: String, id: String, customerNumber: String, date: Date, how: String, staffUserId: String, mStaffName: String, mSatisfied: Boolean, comments: String): Box[KycCheck] = {
    val kyc_check = MappedKycCheck.find(By(MappedKycCheck.mId, id)) match {
      case Full(check) => check
        .mId(id)
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mCustomerNumber(customerNumber)
        .mDate(date)
        .mHow(how)
        .mStaffUserId(staffUserId)
        .mStaffName(mStaffName)
        .mSatisfied(mSatisfied)
        .mComments(comments)
        .saveMe()
      case _ => MappedKycCheck.create
        .mId(id)
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mCustomerNumber(customerNumber)
        .mDate(date)
        .mHow(how)
        .mStaffUserId(staffUserId)
        .mStaffName(mStaffName)
        .mSatisfied(mSatisfied)
        .mComments(comments)
        .saveMe()
    }
    Full(kyc_check)
  }
}

class MappedKycCheck extends KycCheck
with LongKeyedMapper[MappedKycCheck] with IdPK with CreatedUpdated {

  def getSingleton = MappedKycCheck

  object user extends MappedLongForeignKey(this, ResourceUser)
  object mBankId extends UUIDString(this)
  object mCustomerId extends UUIDString(this)

  object mId extends UUIDString(this)
  object mCustomerNumber extends MappedString(this, 50)
  object mDate extends MappedDateTime(this)
  object mHow extends MappedString(this, 32)
  object mStaffUserId extends MappedString(this, 64)
  object mStaffName extends MappedString(this, 64)
  object mSatisfied extends MappedBoolean(this)
  object mComments extends MappedString(this, 2000)


  override def bankId: String = mBankId.get
  override def customerId: String = mCustomerId.get
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