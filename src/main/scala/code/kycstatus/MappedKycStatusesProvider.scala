package code.kycstatuses

import java.util.Date

import code.model.dataAccess.ResourceUser
import code.util.{UUIDString}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper.{By, _}

object MappedKycStatusesProvider extends KycStatusProvider {

  override def getKycStatuses(customerId: String): List[MappedKycStatus] = {
    MappedKycStatus.findAll(
      By(MappedKycStatus.mCustomerId, customerId),
      OrderBy(MappedKycStatus.updatedAt, Descending))
  }


  override def addKycStatus(bankId: String, customerId: String, customerNumber: String, ok: Boolean, date: Date): Box[KycStatus] = {
    val kyc_status = MappedKycStatus.find(By(MappedKycStatus.mBankId, bankId), By(MappedKycStatus.mCustomerId, customerId)) match {
      case Full(status) => status
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mCustomerNumber(customerNumber)
        .mOk(ok)
        .mDate(date)
        .saveMe()
      case _ => MappedKycStatus.create
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mCustomerNumber(customerNumber)
        .mOk(ok)
        .mDate(date)
        .saveMe()
    }
    Full(kyc_status)
  }
}

class MappedKycStatus extends KycStatus
with LongKeyedMapper[MappedKycStatus] with IdPK with CreatedUpdated {

  def getSingleton = MappedKycStatus

  object user extends MappedLongForeignKey(this, ResourceUser)
  object mBankId extends UUIDString(this)
  object mCustomerId extends UUIDString(this)

  object mCustomerNumber extends MappedString(this, 64)
  object mOk extends MappedBoolean(this)
  object mDate extends MappedDateTime(this)


  override def bankId: String = mBankId.get
  override def customerId: String = mCustomerId.get
  override def customerNumber: String = mCustomerNumber.get
  override def ok: Boolean = mOk.get
  override def date: Date = mDate.get

}

object MappedKycStatus extends MappedKycStatus with LongKeyedMetaMapper[MappedKycStatus] {
  override def dbIndexes = super.dbIndexes
}