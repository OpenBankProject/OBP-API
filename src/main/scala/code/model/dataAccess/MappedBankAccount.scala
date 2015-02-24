package code.model.dataAccess

import java.math.MathContext

import code.model._
import code.util.{MappedUUID, Helper}
import net.liftweb.mapper._

class MappedBankAccount extends BankAccount with LongKeyedMapper[MappedBankAccount] with IdPK with CreatedUpdated {

  override def getSingleton = MappedBankAccount

  object bank extends MappedString(this, 255)
  object theAccountId extends MappedString(this, 255)
  object accountIban extends MappedString(this, 50)
  object accountCurrency extends MappedString(this, 10)
  object accountSwiftBic extends MappedString(this, 50)
  object accountNumber extends MappedString(this, 50)

  @deprecated
  object holder extends MappedString(this, 100)

  @deprecated
  object accUUID extends MappedUUID(this)

  //this is the smallest unit of currency! e.g. cents, yen, pence, øre, etc.
  object accountBalance extends MappedLong(this)

  object accountName extends MappedString(this, 255)
  object kind extends MappedString(this, 40)
  object accountLabel extends MappedString(this, 255)

  //the last time this account was updated via hbci
  object lastUpdate extends MappedDateTime(this)

  override def uuid = accUUID.get
  override def accountId: AccountId = AccountId(theAccountId.get)
  override def iban: Option[String] = {
    val i = accountIban.get
    if(i.isEmpty) None else Some(i)
  }
  override def bankId: BankId = BankId(bank.get)
  override def currency: String = accountCurrency.get
  override def swift_bic: Option[String] = {
    val sb = accountSwiftBic.get
    if(sb.isEmpty) None else Some(sb)
  }
  override def number: String = accountNumber.get
  override def balance: BigDecimal = Helper.smallestCurrencyUnitToBigDecimal(accountBalance.get, currency)
  override def name: String = accountName.get
  override def accountType: String = kind.get
  override def label: String = accountLabel.get
  override def accountHolder: String = holder.get
}

object MappedBankAccount extends MappedBankAccount with LongKeyedMetaMapper[MappedBankAccount] {
  override def dbIndexes = UniqueIndex(bank, theAccountId) :: super.dbIndexes
}
