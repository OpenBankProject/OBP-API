/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package com.openbankproject.commons.model

import java.util.Date

import scala.collection.immutable.List
import scala.math.BigDecimal


trait Bank {
  def bankId: BankId

  def shortName: String

  def fullName: String

  def logoUrl: String

  def websiteUrl: String

  def bankRoutingScheme: String

  def bankRoutingAddress: String

  // TODO Add Group ?

  //SWIFT BIC banking code (globally unique)
  @deprecated("Please use bankRoutingScheme and bankRoutingAddress instead")
  def swiftBic: String

  //it's not entirely clear what this is/represents (BLZ in Germany?)
  @deprecated("Please use bankRoutingScheme and bankRoutingAddress instead")
  def nationalIdentifier: String
}

/**
 * Uniquely identifies a view
 */
case class ViewIdBankIdAccountId(viewId : ViewId, bankId : BankId, accountId : AccountId) {
  override def toString = s"view $viewId, for account: $accountId at bank $bankId"
}

/*
Examples of viewId are "owner", "accountant", "auditor" etc.
They are only unique for bank and account
 */
case class ViewId(val value : String) {
  override def toString = value
}

object ViewId {
  def unapply(id : String) = Some(ViewId(id))
}

case class TransactionId(val value : String) {
  override def toString = value
}

object TransactionId {
  def unapply(id : String) = Some(TransactionId(id))
}

object TransactionRequestType {
  def unapply(id : String) = Some(TransactionRequestType(id))
}

case class TransactionRequestType(val value : String) {
  override def toString = value
}

//Note: change case class -> trait, for kafka extends it
trait TransactionRequestStatus{
  def transactionRequestId : String
  def bulkTransactionsStatus: List[TransactionStatus]
}


trait TransactionStatus{
  def transactionId : String
  def transactionStatus: String
  def transactionTimestamp: String
}

case class TransactionRequestId(val value : String) {
  override def toString = value
}

object TransactionRequestId {
  def unapply(id : String) = Some(TransactionRequestId(id))
}

case class TransactionTypeId(val value : String) {
  override def toString = value
}

object TransactionTypeId {
  def unapply(id : String) = Some(TransactionTypeId(id))
}

case class AccountId(val value : String) {
  override def toString = value
}

object AccountId {
  def unapply(id : String) = Some(AccountId(id))
}

case class BankId(val value : String) {
  override def toString = value
}

object BankId {
  def unapply(id : String) = Some(BankId(id))
}

case class AccountRoutingAddress(val value: String) {
  override def toString = value
}

object AccountRoutingAddress {
  def unapply(id: String) = Some(AccountRoutingAddress(id))
}

case class CustomerId(val value : String) {
  override def toString = value
}

object CustomerId {
  def unapply(id : String) = Some(CustomerId(id))
}


// In preparation for use in Context (api links) To replace OtherAccountId
case class CounterpartyId(val value : String) {
  override def toString = value
}

object CounterpartyId {
  def unapply(id : String) = Some(CounterpartyId(id))
}


class AccountOwner(
  val id : String,
  val name : String
)

case class BankIdAccountId(bankId : BankId, accountId : AccountId)

trait BankAccount{
  def accountId : AccountId
  def accountType : String // (stored in the field "kind" on Mapper)
  //def productCode : String // TODO Add this shorter code.
  def balance : BigDecimal
  def currency : String
  def name : String // Is this used?
  def label : String
  @deprecated("Used the account scheme and address instead")
  def iban : Option[String]
  def number : String
  def bankId : BankId
  def lastUpdate : Date
  def branchId: String
  def accountRoutingScheme: String
  def accountRoutingAddress: String
  def accountRoutings: List[AccountRouting] // Introduced in v3.0.0
  def accountRules: List[AccountRule]

  @deprecated("Get the account holder(s) via owners")
  def accountHolder : String
}

//This class is used for propagate the BankAccount as the parameters over different methods.
case class BankAccountInMemory(
  //BankAccount Trait
  bankId: BankId,
  accountId: AccountId,
  accountType: String,
  balance: BigDecimal,
  currency: String,
  name: String,
  lastUpdate: Date,
  accountHolder: String,
  label: String,
  accountRoutingScheme: String,
  accountRoutingAddress: String,
  branchId: String,
  swift_bic: Option[String],
  iban: Option[String],
  number: String,
  accountRoutings: List[AccountRouting],
  accountRules: List[AccountRule]
) extends BankAccount

/*
The other bank account or counterparty in a transaction
as see from the perspective of the original party.
 */


// Note: See also CounterpartyTrait
case class Counterparty(
  
  @deprecated("older version, please first consider the V210, account scheme and address","05/05/2017")
  val nationalIdentifier: String, // This is the scheme a consumer would use to instruct a payment e.g. IBAN
  val kind: String, // Type of bank account.
  
  // The following fields started from V210
  val counterpartyId: String,
  val counterpartyName: String,
  val thisBankId: BankId, // i.e. the Account that sends/receives money to/from this Counterparty
  val thisAccountId: AccountId, // These 2 fields specify the account that uses this Counterparty
  val otherBankRoutingScheme: String, // This is the scheme a consumer would use to specify the bank e.g. BIC
  val otherBankRoutingAddress: Option[String], // The (BIC) value e.g. 67895
  val otherAccountRoutingScheme: String, // This is the scheme a consumer would use to instruct a payment e.g. IBAN
  val otherAccountRoutingAddress: Option[String], // The (IBAN) value e.g. 2349870987820374
  val otherAccountProvider: String, // hasBankId and hasAccountId would refer to an OBP account
  val isBeneficiary: Boolean // True if the originAccount can send money to the Counterparty
)

case class CounterpartyCore(
   kind:String,
   counterpartyId: String,
   counterpartyName: String,
   thisBankId: BankId, // i.e. the Account that sends/receives money to/from this Counterparty
   thisAccountId: AccountId, // These 2 fields specify the account that uses this Counterparty
   otherBankRoutingScheme: String, // This is the scheme a consumer would use to specify the bank e.g. BIC
   otherBankRoutingAddress: Option[String], // The (BIC) value e.g. 67895
   otherAccountRoutingScheme: String, // This is the scheme a consumer would use to instruct a payment e.g. IBAN
   otherAccountRoutingAddress: Option[String], // The (IBAN) value e.g. 2349870987820374
   otherAccountProvider: String, // hasBankId and hasAccountId would refer to an OBP account
   isBeneficiary: Boolean // True if the originAccount can send money to the Counterparty
)

case class TransactionCore(
  id: TransactionId,
  thisAccount: BankAccount,
  otherAccount: CounterpartyCore,
  transactionType: String,
  amount: BigDecimal,
  currency: String,
  description: Option[String],
  startDate: Date,
  finishDate: Date,
  balance: BigDecimal
)

case class AmountOfMoney (
  val currency: String,
  val amount: String
) extends AmountOfMoneyTrait

case class Iban(
  val iban: String
)

case class AccountRule(
  scheme: String, 
  value: String
)

case class AccountRouting(
  scheme: String,
  address: String
)

case class CoreAccount(
  id: String,
  label: String,
  bankId: String,
  accountType: String,
  accountRoutings: List[AccountRouting]
)

case class AccountHeld(
  id: String,
  bankId: String,
  number: String,
  accountRoutings: List[AccountRouting]
)

case class CounterpartyBespoke(
  key: String,
  value: String
)
