package code.crm

import java.util.Date
import code.api.util.ErrorMessages._
import code.crm.CrmEvent._
import code.crm.CrmEvent.{CrmEvent, CrmEventId}
import code.customer.CustomerMessage
import code.model.BankId
import code.common.{AddressT, LicenseT, LocationT, MetaT}
import code.model.dataAccess.ResourceUser
import code.users.Users
import code.util.{UUIDString, MappedUUID}
import net.liftweb.common.Box
import net.liftweb.mapper._
import org.joda.time.Hours

import scala.util.Try

object MappedCrmEventProvider extends CrmEventProvider {

  // Get all events at a bank
  override protected def getEventsFromProvider(bankId: BankId): Option[List[CrmEvent]] = {
    Some(MappedCrmEvent.findAll(
      By(MappedCrmEvent.mBankId, bankId.value)
      )
    )
  }

  // Get events at a bank for one user
  override protected def getEventsFromProvider(bankId: BankId, user: ResourceUser): Option[List[CrmEvent]] =
    Some(MappedCrmEvent.findAll(
      By(MappedCrmEvent.mBankId, bankId.toString),
      By(MappedCrmEvent.mUserId, user)
      )
    )


  override protected def getEventFromProvider(crmEventId: CrmEventId): Option[CrmEvent] =
    MappedCrmEvent.find(
      By(MappedCrmEvent.mCrmEventId, crmEventId.value)
    )


}


class MappedCrmEvent extends CrmEvent with LongKeyedMapper[MappedCrmEvent] with IdPK with CreatedUpdated {

  override def getSingleton = MappedCrmEvent

  object mBankId extends UUIDString(this) // Maybe should be a foreign key (unless we expect different databases one day)
  object mUserId extends MappedLongForeignKey(this, ResourceUser) // The customer
  object mCrmEventId extends  MappedUUID(this)
  object mCategory extends MappedString(this, 32)
  object mDetail extends MappedString(this, 1024)
  object mChannel extends MappedString(this, 32)
  object mScheduledDate extends MappedDateTime(this)
  object mActualDate extends MappedDateTime(this)
  object mResult extends MappedString(this, 32)
  object mCustomerName extends MappedString(this, 64) // Instead we should have CustomerId here which points to Customer
  object mCustomerNumber extends MappedString(this, 64) //  Instead we should have CustomerId here which points to Customer

  override def bankId: BankId = BankId(mBankId.get)
  override def crmEventId: CrmEventId = CrmEventId(mCrmEventId.get)
  override def category: String = mCategory.get
  override def detail: String = mDetail.get
  override def channel: String = mChannel.get
  override def scheduledDate: Date = mScheduledDate.get
  override def actualDate: Date = mActualDate.get
  override def result: String = mResult.get
  override def user: ResourceUser = Users.users.vend.getResourceUserByResourceUserId(mUserId.get).openOrThrowException(attemptedToOpenAnEmptyBox)
  override def customerName : String = mCustomerName.get
  override def customerNumber : String = mCustomerNumber.get
}

object MappedCrmEvent extends MappedCrmEvent with LongKeyedMetaMapper[MappedCrmEvent] {
  // Note: Makes sense for event id to be unique in system
  override def dbIndexes = UniqueIndex(mCrmEventId) :: Index(mBankId) :: super.dbIndexes
}

