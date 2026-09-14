/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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

  //the last time this account was updated via hbci [when transaction data was refreshed from the bank.]
  //It means last transaction refresh date only used for HBCI now.
  object accountLastUpdate extends MappedDateTime(this)

  object mBranchId extends UUIDString(this)

  object accountRuleScheme1 extends MappedString(this, 10)
  object accountRuleValue1 extends MappedLong(this)
  object accountRuleScheme2 extends MappedString(this, 10)
  object accountRuleValue2 extends MappedLong(this)

  override def accountId: AccountId = AccountId(theAccountId.get)
  override def bankId: BankId = BankId(bank.get)
  override def currency: String = accountCurrency.get.toUpperCase
  override def number: String = accountNumber.get
  override def balance: BigDecimal = Helper.smallestCurrencyUnitToBigDecimal(accountBalance.get, currency)
  override def name: String = accountName.get
  override def accountType: String = kind.get

  override def label: String = accountLabel.get
  override def accountHolder: String = holder.get
  override def lastUpdate : Date = accountLastUpdate.get
  
  def branchId: String = mBranchId.get

  def createAccountRule(scheme: String, value: Long) = {
    scheme match {
      case s: String if s.equalsIgnoreCase("") == false =>
        val v = Helper.smallestCurrencyUnitToBigDecimal(value, accountCurrency.get.toUpperCase)
        List(AccountRule(scheme, v.toString()))
      case _ =>
        Nil
    }
  }
  override def accountRoutings: List[AccountRouting] = {
    BankAccountRouting.findAll(By(BankAccountRouting.BankId, this.bankId.value),
      By(BankAccountRouting.AccountId, this.accountId.value))
      .map(_.accountRouting)
  }
  override def accountRules: List[AccountRule] = createAccountRule(accountRuleScheme1.get, accountRuleValue1.get) :::
                                                  createAccountRule(accountRuleScheme2.get, accountRuleValue2.get)

}

object MappedBankAccount extends MappedBankAccount with LongKeyedMetaMapper[MappedBankAccount] {
  override def dbIndexes = UniqueIndex(bank, theAccountId) :: super.dbIndexes
}
