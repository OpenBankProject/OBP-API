package code.standingorders

import java.util.Date

import code.api.util.APIUtil
import code.util.Helper.convertToSmallestCurrencyUnits
import code.util.{Helper, UUIDString}
import net.liftweb.common.Box
import net.liftweb.mapper._

import scala.math.BigDecimal

object MappedStandingOrderProvider extends StandingOrderProvider {
  def createStandingOrder(bankId: String,
                          accountId: String,
                          customerId: String,
                          userId: String,
                          couterpartyId: String,
                          amountValue: BigDecimal,
                          amountCurrency: String,
                          whenFrequency: String,
                          whenDetail: String,
                          dateSigned: Date,
                          dateStarts: Date,
                          dateExpires: Option[Date]
                         ): Box[StandingOrder] = Box.tryo {
    StandingOrder.create
      .BankId(bankId)
      .AccountId(accountId)
      .CustomerId(customerId)
      .UserId(userId)
      .CouterpartyId(couterpartyId)
      .AmountValue(convertToSmallestCurrencyUnits(amountValue, amountCurrency))
      .AmountCurrency(amountCurrency)
      .WhenFrequency(whenFrequency)
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
  object CouterpartyId extends UUIDString(this)
  object AmountValue extends MappedLong(this)
  object AmountCurrency extends MappedString(this, 3)
  object WhenFrequency extends MappedString(this, 50)
  object WhenDetail extends MappedString(this, 50)
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
  override def counterpartyId: String = CouterpartyId.get
  override def amountValue: BigDecimal = Helper.smallestCurrencyUnitToBigDecimal(AmountValue.get, AmountCurrency.get)
  override def amountCurrency: String = AmountCurrency.get
  override def whenFrequency: String = WhenFrequency.get
  override def whenDetail: String = WhenDetail.get
  override def dateSigned: Date = DateSigned.get
  override def dateCancelled: Date = DateCancelled.get
  override def dateExpires: Date = DateExpires.get
  override def dateStarts: Date = DateStarts.get
  override def active: Boolean = Active.get
}

object StandingOrder extends StandingOrder with LongKeyedMetaMapper[StandingOrder] {
  override def dbIndexes: List[BaseIndex[StandingOrder]] = super.dbIndexes
}