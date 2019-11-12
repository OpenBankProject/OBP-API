package code.standingorders

import java.util.Date

import code.api.util.APIUtil
import code.util.UUIDString
import net.liftweb.common.Box
import net.liftweb.mapper._

object MappedStandingOrderProvider extends StandingOrderProvider {
  def createStandingOrder(bankId: String,
                        accountId: String,
                        customerId: String,
                        userId: String,
                        dateSigned: Date,
                        dateStarts: Date,
                        dateExpires: Option[Date]
                       ): Box[StandingOrder] = Box.tryo {
    StandingOrder.create
      .BankId(bankId)
      .AccountId(accountId)
      .CustomerId(customerId)
      .UserId(userId)
      .DateSigned(dateSigned)
      .DateStarts(dateStarts)
      .DateExpires(if (dateExpires.isDefined) dateExpires.get else null)
      .Active(true)
      .saveMe()
  }
  def getStandingOrdersByBankAccount(bankId: String, accountId: String): List[StandingOrder] = {
    StandingOrder.findAll(
      By(StandingOrder.BankId, bankId),
      By(StandingOrder.AccountId, accountId),
      OrderBy(StandingOrder.updatedAt, Descending))
  }
  def getStandingOrdersByCustomer(customerId: String): List[StandingOrder] = {
    StandingOrder.findAll(
      By(StandingOrder.CustomerId, customerId),
      OrderBy(StandingOrder.updatedAt, Descending))
  }
  def getStandingOrdersByUser(userId: String): List[StandingOrder] = {
    StandingOrder.findAll(
      By(StandingOrder.UserId, userId),
      OrderBy(StandingOrder.updatedAt, Descending))
  }
}

class StandingOrder extends StandingOrderTrait with LongKeyedMapper[StandingOrder] with IdPK with CreatedUpdated {

  def getSingleton: StandingOrder.type = StandingOrder

  object StandingOrderId extends UUIDString(this) {
    override def defaultValue = APIUtil.generateUUID()
  }
  object BankId extends UUIDString(this)
  object AccountId extends UUIDString(this)
  object CustomerId extends UUIDString(this)
  object UserId extends UUIDString(this)
  object DateSigned extends MappedDateTime(this)
  object DateCancelled extends MappedDateTime(this)
  object DateStarts extends MappedDateTime(this)
  object DateExpires extends MappedDateTime(this)
  object Active extends MappedBoolean(this)
  
  override def standingOrderId: String = StandingOrderId.get
  override def bankId: String = BankId.get
  override def accountId: String = AccountId.get
  override def customerId: String = CustomerId.get
  override def userId: String = UserId.get
  override def dateSigned: Date = DateSigned.get
  override def dateCancelled: Date = DateCancelled.get
  override def dateExpires: Date = DateExpires.get
  override def dateStarts: Date = DateStarts.get
  override def active: Boolean = Active.get
}

object StandingOrder extends StandingOrder with LongKeyedMetaMapper[StandingOrder] {
  override def dbIndexes: List[BaseIndex[StandingOrder]] = UniqueIndex(BankId, AccountId, CustomerId) :: super.dbIndexes
}