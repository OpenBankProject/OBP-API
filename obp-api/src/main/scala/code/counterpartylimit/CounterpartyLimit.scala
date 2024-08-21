package code.counterpartylimit

import code.api.util.APIUtil
import com.openbankproject.commons.util.JsonAble
import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box
import scala.concurrent.Future

object CounterpartyLimitProvider extends SimpleInjector {
  val counterpartyLimit = new Inject(buildOne _) {}
  def buildOne: CounterpartyLimitProviderTrait =  MappedCounterpartyLimitProvider
}

trait CounterpartyLimitProviderTrait {
  def getCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String
  ): Future[Box[CounterpartyLimitTrait]]
  
  def deleteCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String
  ): Future[Box[Boolean]]
  
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
    maxNumberOfYearlyTransactions: Int): Future[Box[CounterpartyLimitTrait]]
}

trait CounterpartyLimitTrait extends JsonAble{
  def counterpartyLimitId: String
  def bankId: String
  def accountId: String
  def viewId: String
  def counterpartyId: String
  
  def currency: String
  def maxSingleAmount: Int
  def maxMonthlyAmount: Int
  def maxNumberOfMonthlyTransactions: Int
  def maxYearlyAmount: Int
  def maxNumberOfYearlyTransactions: Int
}

