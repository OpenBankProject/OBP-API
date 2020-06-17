package code.directdebit

import java.util.Date

import code.api.util.APIUtil
import code.util.UUIDString
import com.openbankproject.commons.model.DirectDebitTrait
import net.liftweb.common.Box
import net.liftweb.mapper._

object MappedDirectDebitProvider extends DirectDebitProvider {
  def createDirectDebit(bankId: String,
                        accountId: String,
                        customerId: String,
                        userId: String,
                        counterpartyId: String,
                        dateSigned: Date,
                        dateStarts: Date,
                        dateExpires: Option[Date]
                       ): Box[DirectDebit] = Box.tryo {
    DirectDebit.create
      .BankId(bankId)
      .AccountId(accountId)
      .CustomerId(customerId)
      .UserId(userId)
      .CounterpartyId(counterpartyId)
      .DateSigned(dateSigned)
      .DateStarts(dateStarts)
      .DateExpires(if (dateExpires.isDefined) dateExpires.get else null)
      .Active(true)
      .saveMe()
  }
  def getDirectDebitsByBankAccount(bankId: String, accountId: String): List[DirectDebit] = {
    DirectDebit.findAll(
      By(DirectDebit.BankId, bankId),
      By(DirectDebit.AccountId, accountId),
      OrderBy(DirectDebit.updatedAt, Descending))
  }
  def getDirectDebitsByCustomer(customerId: String): List[DirectDebit] = {
    DirectDebit.findAll(
      By(DirectDebit.CustomerId, customerId),
      OrderBy(DirectDebit.updatedAt, Descending))
  }
  def getDirectDebitsByUser(userId: String): List[DirectDebit] = {
    DirectDebit.findAll(
      By(DirectDebit.UserId, userId),
      OrderBy(DirectDebit.updatedAt, Descending))
  }
}

class DirectDebit extends DirectDebitTrait with LongKeyedMapper[DirectDebit] with IdPK with CreatedUpdated {

  def getSingleton: DirectDebit.type = DirectDebit

  object DirectDebitId extends UUIDString(this) {
    override def defaultValue = APIUtil.generateUUID()
  }
  object BankId extends UUIDString(this)
  object AccountId extends UUIDString(this)
  object CustomerId extends UUIDString(this)
  object UserId extends UUIDString(this)
  object CounterpartyId extends UUIDString(this)
  object DateSigned extends MappedDateTime(this)
  object DateCancelled extends MappedDateTime(this)
  object DateStarts extends MappedDateTime(this)
  object DateExpires extends MappedDateTime(this)
  object Active extends MappedBoolean(this)
  
  override def directDebitId: String = DirectDebitId.get
  override def bankId: String = BankId.get
  override def accountId: String = AccountId.get
  override def customerId: String = CustomerId.get
  override def userId: String = UserId.get
  override def counterpartyId: String = CounterpartyId.get
  override def dateSigned: Date = DateSigned.get
  override def dateCancelled: Date = DateCancelled.get
  override def dateExpires: Date = DateExpires.get
  override def dateStarts: Date = DateStarts.get
  override def active: Boolean = Active.get
}

object DirectDebit extends DirectDebit with LongKeyedMetaMapper[DirectDebit] {
  override def dbIndexes: List[BaseIndex[DirectDebit]] = UniqueIndex(BankId, AccountId, CustomerId, CounterpartyId) :: super.dbIndexes
}