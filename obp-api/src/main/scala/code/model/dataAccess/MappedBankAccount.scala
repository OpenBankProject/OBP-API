package code.model.dataAccess

import java.util.Date

import code.util.{AccountIdString, Helper, MappedAccountNumber, UUIDString}
import com.openbankproject.commons.model._
import net.liftweb.mapper._

import scala.collection.immutable.List

class MappedBankAccount extends BankAccount with LongKeyedMapper[MappedBankAccount] with IdPK with CreatedUpdated {

  override def getSingleton = MappedBankAccount

  object bank extends UUIDString(this)
  object theAccountId extends AccountIdString(this)
  object accountIban extends MappedString(this, 50)
  object accountCurrency extends MappedString(this, 10)
  object accountNumber extends MappedAccountNumber(this)

  @deprecated
  object holder extends MappedString(this, 100)

  //this is the smallest unit of currency! e.g. cents, yen, pence, øre, etc.
  object accountBalance extends MappedLong(this)

  object accountName extends MappedString(this, 255)
  object kind extends MappedString(this, 255) // This is the account type aka financial product name

  //object productCode extends MappedString(this, 255)

  object accountLabel extends MappedString(this, 255)

  //the last time this account was updated via hbci
  object accountLastUpdate extends MappedDateTime(this)
  
  object mAccountRoutingScheme extends MappedString(this, 32)
  object mAccountRoutingAddress extends MappedString(this, 128)
  object mBranchId extends UUIDString(this)

  object accountRuleScheme1 extends MappedString(this, 10)
  object accountRuleValue1 extends MappedLong(this)
  object accountRuleScheme2 extends MappedString(this, 10)
  object accountRuleValue2 extends MappedLong(this)

  override def accountId: AccountId = AccountId(theAccountId.get)
  override def iban: Option[String] = {
    val i = accountIban.get
    if(i.isEmpty) None else Some(i)
  }
  override def bankId: BankId = BankId(bank.get)
  override def currency: String = accountCurrency.get
  override def number: String = accountNumber.get
  override def balance: BigDecimal = Helper.smallestCurrencyUnitToBigDecimal(accountBalance.get, currency)
  override def name: String = accountName.get
  override def accountType: String = kind.get

  override def label: String = accountLabel.get
  override def accountHolder: String = holder.get
  override def lastUpdate : Date = accountLastUpdate.get

  def accountRoutingScheme: String = mAccountRoutingScheme.get
  def accountRoutingAddress: String = mAccountRoutingAddress.get
  def branchId: String = mBranchId.get

  def createAccountRule(scheme: String, value: Long) = {
    scheme match {
      case s: String if s.equalsIgnoreCase("") == false =>
        val v = Helper.smallestCurrencyUnitToBigDecimal(value, accountCurrency.get)
        List(AccountRule(scheme, v.toString()))
      case _ =>
        Nil
    }
  }
  override def accountRoutings: List[AccountRouting] = List(AccountRouting(mAccountRoutingScheme.get, mAccountRoutingAddress.get))
  override def accountRules: List[AccountRule] = createAccountRule(accountRuleScheme1.get, accountRuleValue1.get) :::
                                                  createAccountRule(accountRuleScheme2.get, accountRuleValue2.get)

}

object MappedBankAccount extends MappedBankAccount with LongKeyedMetaMapper[MappedBankAccount] {
  override def dbIndexes = UniqueIndex(bank, theAccountId) :: super.dbIndexes
}
