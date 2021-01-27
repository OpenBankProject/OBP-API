/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package com.openbankproject.commons.model

import java.util.Date

import com.openbankproject.commons.util.{OBPRequired, optional}

import scala.collection.immutable.List
import scala.math.BigDecimal


trait Bank {
  @OBPRequired
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

case class TransactionRequestType(value : String) {
  override def toString = value
}

case class TransactionRequestStatusValue(value : String) {
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

case class BankId(value : String) {
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
  def name : String // Is this used? -->It is used for BerlinGroup V1.3, it has the name in account response. 
                                        // `Name of the account given by the bank or the PSU in online-banking.`
  @optional
  def label: String
  def number : String
  def bankId : BankId
  //It means last transaction refresh date only used for HBCI now.
  def lastUpdate : Date
  def branchId: String
  def accountRoutings: List[AccountRouting] // Introduced in v3.0.0
  @optional
  def accountRules: List[AccountRule]
  @deprecated("Get the account holder(s) via owners")
  @optional
  def accountHolder : String
  //This field is really special, it used for OBP side to distinguish the different accounts from the core baning side.
  //Because of in OBP side, we just have one account table, no difference for different types of accounts. 
  //So here, we introduce the field for the OBP presentation layer to filter the accounts. 
  //also @`Reads a list of card accounts` in Berlin group V1.3 ..
  def attributes  : Option[List[Attribute]] = None
}

//This class is used for propagate the BankAccount as the parameters over different methods.
case class BankAccountInMemory(
  //BankAccount Trait
  bankId: BankId = BankId(""),
  accountId: AccountId = AccountId(""),
  accountType: String = "",
  balance: BigDecimal = 0,
  currency: String = "",
  name: String = "",
  lastUpdate: Date = new Date(),
  accountHolder: String = "",
  label: String = "",
  accountRoutingScheme: String = "",
  accountRoutingAddress: String = "",
  branchId: String = "",
  swift_bic: Option[String] = None,
  iban: Option[String] = None,
  number: String = "",
  accountRoutings: List[AccountRouting],
  accountRules: List[AccountRule] = Nil
) extends BankAccount

/*
The other bank account or counterparty in a transaction
as see from the perspective of the original party.
 */


// Note: See also CounterpartyTrait
case class Counterparty(
  
  @deprecated("older version, please first consider the V210, account scheme and address","05/05/2017")
  @optional
  val nationalIdentifier: String, // This is the scheme a consumer would use to instruct a payment e.g. IBAN
  val kind: String, // Type of bank account.
  
  // The following fields started from V210
  val counterpartyId: String,
  val counterpartyName: String,
  val thisBankId: BankId, // i.e. the Account that sends/receives money to/from this Counterparty
  val thisAccountId: AccountId, // These 2 fields specify the account that uses this Counterparty
  @optional
  val otherBankRoutingScheme: String, // This is the scheme a consumer would use to specify the bank e.g. BIC
  @optional
  val otherBankRoutingAddress: Option[String], // The (BIC) value e.g. 67895
  @optional
  val otherAccountRoutingScheme: String, // This is the scheme a consumer would use to instruct a payment e.g. IBAN
  @optional
  val otherAccountRoutingAddress: Option[String], // The (IBAN) value e.g. 2349870987820374
  @optional
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
  currency: String,
  amount: String
) extends AmountOfMoneyTrait
object AmountOfMoney extends Converter[AmountOfMoneyTrait, AmountOfMoney]

case class Iban(
  iban: String
)
case class IbanAddress(
  address: String
)
case class IbanChecker(
                 isValid: Boolean,
                 details: Option[IbanDetails]
)
case class IbanDetails(bic: String,
                       bank: String,
                       branch: String,
                       address: String,
                       city: String,
                       zip: String,
                       phone: String,
                       country: String,
                       countryIso: String,
                       sepaCreditTransfer: String,
                       sepaDirectDebit: String,
                       sepaSddCore: String,
                       sepaB2b: String,
                       sepaCardClearing: String
                      )

case class Attribute(
  name: String,
  `type`: String,
  value: String
)
case class AccountRule(
  scheme: String, 
  value: String
)

case class AccountRouting(
  scheme: String,
  address: String
)

trait BankAccountRoutingTrait {
  def bankId: BankId
  def accountId: AccountId
  def accountRouting: AccountRouting
}

case class CoreAccount(
  id: String,
  label: String,
  bankId: String,
  accountType: String,
  accountRoutings: List[AccountRouting]
)

case class AccountBalance(
  id: String,
  label: String,
  bankId: String,
  accountRoutings: List[AccountRouting],
  balance: AmountOfMoney
)

case class AccountsBalances(
  accounts: List[AccountBalance],
  overallBalance: AmountOfMoney,
  overallBalanceDate: Date
)

case class AccountHeld(
  id: String,
  label: String,
  bankId: String,
  number: String,
  accountRoutings: List[AccountRouting]
)

case class CounterpartyBespoke(
  key: String,
  value: String
)

case class CustomerDependant(
  dateOfBirth: Date
)

trait DoubleEntryBookTransactionTrait {
  def transactionRequestBankId: Option[BankId]
  def transactionRequestAccountId: Option[AccountId]
  def transactionRequestId: Option[TransactionRequestId]
  def debitTransactionBankId: BankId
  def debitTransactionAccountId: AccountId
  def debitTransactionId: TransactionId
  def creditTransactionBankId: BankId
  def creditTransactionAccountId: AccountId
  def creditTransactionId: TransactionId
}

case class DoubleEntryTransaction(
                                  transactionRequestBankId: Option[BankId],
                                  transactionRequestAccountId: Option[AccountId],
                                  transactionRequestId: Option[TransactionRequestId],
                                  debitTransactionBankId: BankId,
                                  debitTransactionAccountId: AccountId,
                                  debitTransactionId: TransactionId,
                                  creditTransactionBankId: BankId,
                                  creditTransactionAccountId: AccountId,
                                  creditTransactionId: TransactionId
                                 ) extends DoubleEntryBookTransactionTrait
object DoubleEntryTransaction extends Converter[DoubleEntryBookTransactionTrait, DoubleEntryTransaction]

