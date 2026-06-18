package code.counterpartylimit

import org.json4s._
import code.util.MappedUUID
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.json
import org.json4s.Formats
import org.json4s.JsonAST.{JString, JValue}
import org.json4s.JsonDSL._

import scala.concurrent.Future
import com.openbankproject.commons.model.CounterpartyLimitTrait

import java.math.MathContext

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
    maxSingleAmount: BigDecimal,
    maxMonthlyAmount: BigDecimal,
    maxNumberOfMonthlyTransactions: Int,
    maxYearlyAmount: BigDecimal,
    maxNumberOfYearlyTransactions: Int,
    maxTotalAmount: BigDecimal,
    maxNumberOfTransactions: Int)= Future {

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
        counterpartyLimit.MaxTotalAmount(maxTotalAmount)
        counterpartyLimit.MaxNumberOfTransactions(maxNumberOfTransactions)
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
  
  object Currency extends MappedString(this, 255)
  
  object MaxSingleAmount extends MappedDecimal(this, MathContext.DECIMAL64, 10){
    override def defaultValue = BigDecimal(0) // Default value for Amount
  }
  
  object MaxMonthlyAmount extends MappedDecimal(this, MathContext.DECIMAL64, 10){
    override def defaultValue = BigDecimal(0) // Default value for Amount
  }
  
  object MaxNumberOfMonthlyTransactions extends MappedInt(this) {
    override def defaultValue = -1
  }
  
  object MaxYearlyAmount extends MappedDecimal(this, MathContext.DECIMAL64, 10){
    override def defaultValue = BigDecimal(0) // Default value for Amount
  }
  object MaxNumberOfYearlyTransactions extends MappedInt(this) {
    override def defaultValue = -1
  }


  object MaxTotalAmount extends MappedDecimal(this, MathContext.DECIMAL64, 10){
    override def defaultValue = BigDecimal(0) // Default value for Amount
  }
  
  object MaxNumberOfTransactions extends MappedInt(this) {
    override def defaultValue = -1
  }
  
  def counterpartyLimitId: String = CounterpartyLimitId.get

  def bankId: String = BankId.get
  def accountId: String = AccountId.get
  def viewId: String = ViewId.get
  def counterpartyId: String = CounterpartyId.get
  def currency: String = Currency.get

  def maxSingleAmount: BigDecimal = MaxSingleAmount.get
  def maxMonthlyAmount: BigDecimal = MaxMonthlyAmount.get
  def maxNumberOfMonthlyTransactions: Int = MaxNumberOfMonthlyTransactions.get
  def maxYearlyAmount: BigDecimal = MaxYearlyAmount.get
  def maxNumberOfYearlyTransactions: Int = MaxNumberOfYearlyTransactions.get
  def maxTotalAmount: BigDecimal = MaxTotalAmount.get
  def maxNumberOfTransactions: Int = MaxNumberOfTransactions.get

  override def toJValue(implicit format: Formats): JValue = {
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
      ("max_number_of_yearly_transactions", maxNumberOfYearlyTransactions) ~
      ("max_total_amount", maxTotalAmount) ~
      ("max_number_of_transactions", maxNumberOfTransactions)
  }
}

object CounterpartyLimit extends CounterpartyLimit with LongKeyedMetaMapper[CounterpartyLimit] {
  override def dbIndexes = UniqueIndex(CounterpartyLimitId) :: UniqueIndex(BankId, AccountId, ViewId, CounterpartyId) :: super.dbIndexes
}
