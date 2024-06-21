package code.counterpartylimit

import code.util.{MappedUUID}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedCounterpartyLimitProvider extends CounterpartyLimitProviderTrait {
  
  def getAll(): Future[List[CounterpartyLimit]] = Future(CounterpartyLimit.findAll())
  def getByCounterpartyLimitId(counterpartyLimitId: String): Future[Box[CounterpartyLimit]] = Future {
    CounterpartyLimit.find(
      By(CounterpartyLimit.CounterpartyLimitId, counterpartyLimitId),
    )
  }
  def deleteByCounterpartyLimitId(counterpartyLimitId: String): Future[Box[Boolean]] = Future {
    CounterpartyLimit.find(
      By(CounterpartyLimit.CounterpartyLimitId, counterpartyLimitId),
    ).map(_.delete_!)
  }
  
  def createOrUpdateCounterpartyLimit(
    counterpartyLimitId:Option[String],
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String,
    maxSingleAmount: Int,
    maxMonthlyAmount: Int,
    maxNumberOfMonthlyTransactions: Int,
    maxYearlyAmount: Int,
    maxNumberOfYearlyTransactions: Int): Future[Box[CounterpartyLimit]]= Future {

    def createCounterpartyLimit(counterpartyLimit: CounterpartyLimit): Box[CounterpartyLimit] = {
      tryo {
        counterpartyLimit.BankId(bankId)
        counterpartyLimit.AccountId(accountId)
        counterpartyLimit.ViewId(viewId)
        counterpartyLimit.CounterpartyId(counterpartyId)
        counterpartyLimit.MaxSingleAmount(maxSingleAmount)
        counterpartyLimit.MaxMonthlyAmount(maxMonthlyAmount)
        counterpartyLimit.MaxNumberOfMonthlyTransactions(maxNumberOfMonthlyTransactions)
        counterpartyLimit.MaxYearlyAmount(maxYearlyAmount)
        counterpartyLimit.MaxNumberOfYearlyTransactions(maxNumberOfYearlyTransactions)
        counterpartyLimit.saveMe()
      }
    }

    def getCounterpartyLimit = CounterpartyLimit.find(
      By(CounterpartyLimit.CounterpartyLimitId, counterpartyLimitId.get)
    )
    
    val result = counterpartyLimitId match {
      case Some(_) => getCounterpartyLimit.map(createCounterpartyLimit).flatten
      case _ => createCounterpartyLimit(CounterpartyLimit.create)
    }
    result
  }
}

class CounterpartyLimit extends CounterpartyLimitTrait with LongKeyedMapper[CounterpartyLimit] with IdPK with CreatedUpdated {
  override def getSingleton = CounterpartyLimit
  
  object CounterpartyLimitId extends MappedUUID(this)
  
  object BankId extends MappedString(this, 255){
    override def dbNotNull_? = true
  }
  object AccountId extends MappedString(this, 255){
    override def dbNotNull_? = true
  }
  object ViewId extends MappedString(this, 255){
    override def dbNotNull_? = true
  }
  object CounterpartyId extends MappedString(this, 255){
    override def dbNotNull_? = true
  }
  
  
  object MaxSingleAmount extends MappedInt(this) {
    override def defaultValue = -1
  }
  object MaxMonthlyAmount extends MappedInt(this) {
    override def defaultValue = -1
  }
  object MaxNumberOfMonthlyTransactions extends MappedInt(this) {
    override def defaultValue = -1
  }
  object MaxYearlyAmount extends MappedInt(this) {
    override def defaultValue = -1
  }
  object MaxNumberOfYearlyTransactions extends MappedInt(this) {
    override def defaultValue = -1
  }

  def counterpartyLimitId: String = CounterpartyLimitId.get

  def bankId: String = BankId.get
  def accountId: String = AccountId.get
  def viewId: String = ViewId.get
  def counterpartyId: String = CounterpartyId.get

  def maxSingleAmount: Int = MaxSingleAmount.get
  def maxMonthlyAmount: Int = MaxMonthlyAmount.get
  def maxNumberOfMonthlyTransactions: Int = MaxNumberOfMonthlyTransactions.get
  def maxYearlyAmount: Int = MaxYearlyAmount.get
  def maxNumberOfYearlyTransactions: Int = MaxNumberOfYearlyTransactions.get

}

object CounterpartyLimit extends CounterpartyLimit with LongKeyedMetaMapper[CounterpartyLimit] {
  override def dbIndexes = UniqueIndex(CounterpartyLimitId) :: UniqueIndex(BankId, AccountId, ViewId, CounterpartyId) :: super.dbIndexes
}
