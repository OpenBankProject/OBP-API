package code.kycstatuses

import java.util.Date

import code.model.{BankId, User}
import code.model.dataAccess.APIUser
import code.util.{DefaultStringField}
import net.liftweb.mapper._

object MappedKycStatusesProvider extends KycStatusProvider {

  override def getKycStatuses(customerNumber: String): List[MappedKycStatus] = {
    MappedKycStatus.findAll(
      By(MappedKycStatus.mCustomerNumber, customerNumber),
      OrderBy(MappedKycStatus.updatedAt, Descending))
  }


  override def addKycStatus(customerNumber: String, ok: Boolean, date: Date): Boolean = {
    MappedKycStatus.create
      .mCustomerNumber(customerNumber)
      .mOk(ok)
      .mDate(date)
      .save()
  }
}

class MappedKycStatus extends KycStatus
with LongKeyedMapper[MappedKycStatus] with IdPK with CreatedUpdated {

  def getSingleton = MappedKycStatus

  object user extends MappedLongForeignKey(this, APIUser)
  object bank extends DefaultStringField(this)

  object mCustomerNumber extends DefaultStringField(this)
  object mOk extends MappedBoolean(this)
  object mDate extends MappedDateTime(this)



  override def customerNumber: String = mCustomerNumber.get
  override def ok: Boolean = mOk.get
  override def date: Date = mDate.get

}

object MappedKycStatus extends MappedKycStatus with LongKeyedMetaMapper[MappedKycStatus] {
  override def dbIndexes = UniqueIndex(mCustomerNumber) :: super.dbIndexes
}