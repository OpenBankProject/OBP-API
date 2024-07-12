package code.counterpartylimit

import code.util.MappedUUID
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.json
import net.liftweb.json.Formats
import net.liftweb.json.JsonAST.{JValue,JString}
import net.liftweb.json.JsonDSL._
import scala.concurrent.Future

object MappedCounterpartyLimitProvider extends CounterpartyLimitProviderTrait {
  
  def getCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String
  ): Future[Box[CounterpartyLimitTrait]] = Future {
    CounterpartyLimit.find(
      By(CounterpartyLimit.BankId, bankId),
      By(CounterpartyLimit.AccountId, accountId),
      By(CounterpartyLimit.ViewId, viewId),
      By(CounterpartyLimit.CounterpartyId, counterpartyId)
    )
  }
  
  def deleteCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String
  ): Future[Box[Boolean]] = Future {
    CounterpartyLimit.find(
      By(CounterpartyLimit.BankId, bankId),
      By(CounterpartyLimit.AccountId, accountId),
      By(CounterpartyLimit.ViewId, viewId),
      By(CounterpartyLimit.CounterpartyId, counterpartyId)
    ).map(_.delete_!)
  }
  
  def createOrUpdateCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String,
    currency: String,
    maxSingleAmount: Int,
    maxMonthlyAmount: Int,
    maxNumberOfMonthlyTransactions: Int,
    maxYearlyAmount: Int,
    maxNumberOfYearlyTransactions: Int)= Future {

    def createCounterpartyLimit(counterpartyLimit: CounterpartyLimit)= {
      tryo {
        counterpartyLimit.BankId(bankId)
        counterpartyLimit.AccountId(accountId)
        counterpartyLimit.ViewId(viewId)
        counterpartyLimit.CounterpartyId(counterpartyId)
        counterpartyLimit.Currency(currency)
        counterpartyLimit.MaxSingleAmount(maxSingleAmount)
        counterpartyLimit.MaxMonthlyAmount(maxMonthlyAmount)
        counterpartyLimit.MaxNumberOfMonthlyTransactions(maxNumberOfMonthlyTransactions)
        counterpartyLimit.MaxYearlyAmount(maxYearlyAmount)
        counterpartyLimit.MaxNumberOfYearlyTransactions(maxNumberOfYearlyTransactions)
        counterpartyLimit.saveMe()
      }
    }

    def getCounterpartyLimit = CounterpartyLimit.find(
      By(CounterpartyLimit.BankId, bankId),
      By(CounterpartyLimit.AccountId, accountId),
      By(CounterpartyLimit.ViewId, viewId),
      By(CounterpartyLimit.CounterpartyId, counterpartyId),
    )
    
    val result = getCounterpartyLimit match {
      case Full(counterpartyLimit) => createCounterpartyLimit(counterpartyLimit)
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
  
  object Currency extends MappedString(this, 255){
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
  def currency: String = Currency.get

  def maxSingleAmount: Int = MaxSingleAmount.get
  def maxMonthlyAmount: Int = MaxMonthlyAmount.get
  def maxNumberOfMonthlyTransactions: Int = MaxNumberOfMonthlyTransactions.get
  def maxYearlyAmount: Int = MaxYearlyAmount.get
  def maxNumberOfYearlyTransactions: Int = MaxNumberOfYearlyTransactions.get

  override def toJValue(implicit format: Formats): JValue ={
    ("counterparty_limit_id", counterpartyLimitId) ~ 
      ("bank_id", bankId) ~ 
      ("account_id",accountId) ~ 
      ("view_id",viewId) ~ 
      ("counterparty_id",counterpartyId) ~ 
      ("currency",currency) ~ 
      ("max_single_amount", maxSingleAmount) ~ 
      ("max_monthly_amount", maxMonthlyAmount) ~ 
      ("max_number_of_monthly_transactions", maxNumberOfMonthlyTransactions) ~ 
      ("max_yearly_amount", maxYearlyAmount) ~ 
      ("max_number_of_yearly_transactions", maxNumberOfYearlyTransactions)
  }
}

object CounterpartyLimit extends CounterpartyLimit with LongKeyedMetaMapper[CounterpartyLimit] {
  override def dbIndexes = UniqueIndex(CounterpartyLimitId) :: UniqueIndex(BankId, AccountId, ViewId, CounterpartyId) :: super.dbIndexes
}
