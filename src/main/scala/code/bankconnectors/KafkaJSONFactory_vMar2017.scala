/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.bankconnectors

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import code.fx.FXRate
import code.metadata.counterparties.CounterpartyTrait
import code.model.dataAccess.MappedBankAccountData
import code.model._
import code.transactionrequests.TransactionRequestTypeCharge
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.today


case class KafkaInboundBank(
  bankId: String,
  name: String,
  logo: String,
  url: String
)

case class InboundUser(
  email: String,
  password: String,
  display_name: String
)

case class KafkaInboundAccountData(
  banks: List[KafkaInboundBank],
  users: List[InboundUser],
  accounts: List[KafkaInboundAccount]
)

// We won't need this. TODO clean up.
case class KafkaInboundData(
  banks: List[KafkaInboundBank],
  users: List[InboundUser],
  accounts: List[KafkaInboundAccount],
  transactions: List[KafkaInboundTransaction],
  branches: List[KafkaInboundBranch],
  atms: List[KafkaInboundAtm],
  products: List[KafkaInboundProduct],
  crm_events: List[KafkaInboundCrmEvent]
)


case class GetUser(
  action: String,
  version: String,
  username: String,
  password: String
)

case class UpdateUserAccountViews(
  action: String,
  version: String,
  username: String,
  userId: String,
  bankId: String
)

case class GetBanks(
  action: String,
  version: String,
  username: String,
  userId: String
)

case class GetBank(
  action: String,
  version: String,
  bankId: String,
  userId: String,
  username: String
)

case class GetChallengeThreshold(
  action: String,
  version: String,
  bankId: String,
  accountId: String,
  viewId: String,
  transactionRequestType: String,
  currency: String,
  userId: String,
  username: String
)

case class GetChargeLevel(
  action: String,
  version: String,
  bankId: String,
  accountId: String,
  viewId: String,
  userId: String,
  username: String,
  transactionRequestType: String,
  currency: String
)

case class CreateChallenge(
  action: String,
  version: String,
  bankId: String,
  accountId: String,
  userId: String,
  username: String,
  transactionRequestType: String,
  transactionRequestId: String
)

case class ValidateChallengeAnswer(
  action: String,
  version: String,
  userId: String,
  username: String,
  challengeId: String,
  hashOfSuppliedAnswer: String
)

case class GetTransaction(
  action: String,
  version: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String,
  transactionId: String
)

case class GetTransactions(
  action: String,
  version: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String,
  queryParams: String
)

case class GetBankAccount(
  action: String,
  version: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String
)

case class GetBankAccounts(
  action: String,
  version: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String
)

case class GetAccountByNumber(
  action: String,
  version: String,
  userId: String,
  username: String,
  bankId: String,
  number: String
)

case class SaveTransaction(
  action: String,
  version: String,
  userId: String,
  username: String,
  
  // fromAccount
  fromAccountName: String,
  fromAccountId: String,
  fromAccountBankId: String,
  
  // transaction details
  transactionId: String,
  transactionRequestType: String,
  transactionAmount: String,
  transactionCurrency: String,
  transactionChargePolicy: String,
  transactionChargeAmount: String,
  transactionChargeCurrency: String,
  transactionDescription: String,
  transactionPostedDate: String,
  
  // toAccount or toCounterparty
  toCounterpartyId: String,
  toCounterpartyName: String,
  toCounterpartyCurrency: String,
  toCounterpartyRoutingAddress: String,
  toCounterpartyRoutingScheme: String,
  toCounterpartyBankRoutingAddress: String,
  toCounterpartyBankRoutingScheme: String
)

case class GetTransactionRequestStatusesImpl(
  action: String,
  version: String
)

case class GetCurrentFxRate(
  action: String,
  version: String,
  userId: String,
  username: String,
  fromCurrencyCode: String,
  toCurrencyCode: String
)

case class GetTransactionRequestTypeCharge(
  action: String,
  version: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String,
  viewId: String,
  transactionRequestType: String
)

case class KafkaInboundValidatedUser(email: String,
  displayName: String)


case class GetCounterpartyByIban(
  action: String,
  version: String,
  userId: String,
  username: String,
  otherAccountRoutingAddress: String,
  otherAccountRoutingScheme: String
)

case class GetCounterpartyByCounterpartyId(
  action: String,
  version: String,
  userId: String,
  username: String,
  counterpartyId: String
)

case class KafkaInboundCounterparty(
  name: String,
  created_by_user_id: String,
  this_bank_id: String,
  this_account_id: String,
  this_view_id: String,
  counterparty_id: String,
  other_bank_routing_scheme: String,
  other_account_routing_scheme: String,
  other_bank_routing_address: String,
  other_account_routing_address: String,
  is_beneficiary: Boolean
)

case class KafkaCounterparty(counterparty: KafkaInboundCounterparty) extends CounterpartyTrait {
  
  def createdByUserId: String = counterparty.created_by_user_id
  def name: String = counterparty.name
  def thisBankId: String = counterparty.this_bank_id
  def thisAccountId: String = counterparty.this_account_id
  def thisViewId: String = counterparty.this_view_id
  def counterpartyId: String = counterparty.counterparty_id
  def otherAccountRoutingScheme: String = counterparty.other_account_routing_scheme
  def otherAccountRoutingAddress: String = counterparty.other_account_routing_address
  def otherBankRoutingScheme: String = counterparty.other_bank_routing_scheme
  def otherBankRoutingAddress: String = counterparty.other_bank_routing_address
  def isBeneficiary: Boolean = counterparty.is_beneficiary
}

case class KafkaBank(r: KafkaInboundBank) extends Bank {
  
  def fullName = r.name
  def shortName = r.name
  def logoUrl = r.logo
  def bankId = BankId(r.bankId)
  def nationalIdentifier = "None"
  //TODO
  def swiftBic = "None"
  //TODO
  def websiteUrl = r.url
}

case class KafkaBankAccount(r: KafkaInboundAccount) extends BankAccount {
  
  def accountId: AccountId = AccountId(r.accountId)
  def accountType: String = r.`type`
  def balance: BigDecimal = BigDecimal(r.balanceAmount)
  def currency: String = r.balanceCurrency
  def name: String = r.owners.head
  def swift_bic: Option[String] = Some("swift_bic")
  //TODO
  def iban: Option[String] = Some(r.iban)
  def number: String = r.number
  def bankId: BankId = BankId(r.bankId)
  def lastUpdate: Date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(today.getTime.toString)
  def accountHolder: String = r.owners.head
  
  // Fields modifiable from OBP are stored in mapper
  def label: String = (for {
    d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, r.accountId))
  } yield {
    d.getLabel
  }).getOrElse(r.number)
  
}

case class KafkaFXRate(kafkaInboundFxRate: KafkaInboundFXRate) extends FXRate {
  
  def fromCurrencyCode: String = kafkaInboundFxRate.from_currency_code
  def toCurrencyCode: String = kafkaInboundFxRate.to_currency_code
  def conversionValue: Double = kafkaInboundFxRate.conversion_value
  def inverseConversionValue: Double = kafkaInboundFxRate.inverse_conversion_value
  //TODO need to add error handling here for String --> Date transfer
  def effectiveDate: Date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(kafkaInboundFxRate.effective_date)
}

case class KafkaTransactionRequestTypeCharge(kafkaInboundTransactionRequestTypeCharge: KafkaInboundTransactionRequestTypeCharge) extends TransactionRequestTypeCharge {
  
  def transactionRequestTypeId: String = kafkaInboundTransactionRequestTypeCharge.transaction_request_type_id
  def bankId: String = kafkaInboundTransactionRequestTypeCharge.bank_id
  def chargeCurrency: String = kafkaInboundTransactionRequestTypeCharge.charge_currency
  def chargeAmount: String = kafkaInboundTransactionRequestTypeCharge.charge_amount
  def chargeSummary: String = kafkaInboundTransactionRequestTypeCharge.charge_summary
}

case class KafkaTransactionRequestStatus(kafkaInboundTransactionRequestStatus: KafkaInboundTransactionRequestStatus) extends TransactionRequestStatus {
  
  override def transactionRequestid: String = kafkaInboundTransactionRequestStatus.transactionRequestId
  override def bulkTransactionsStatus: List[TransactionStatus] = kafkaInboundTransactionRequestStatus.bulkTransactionsStatus
}


/** Bank Branches
  *
  * @param id       Uniquely identifies the Branch within the Bank. SHOULD be url friendly (no spaces etc.) Used in URLs
  * @param bank_id  MUST match bank_id in Banks
  * @param name     Informal name for the Branch
  * @param address  Address
  * @param location Geolocation
  * @param meta     Meta information including the license this information is published under
  * @param lobby    Info about when the lobby doors are open
  * @param driveUp  Info about when automated facilities are open e.g. cash point machine
  */
case class KafkaInboundBranch(
  id: String,
  bank_id: String,
  name: String,
  address: KafkaInboundAddress,
  location: KafkaInboundLocation,
  meta: KafkaInboundMeta,
  lobby: Option[KafkaInboundLobby],
  driveUp: Option[KafkaInboundDriveUp])

case class KafkaInboundLicense(
  id: String,
  name: String)

case class KafkaInboundMeta(
  license: KafkaInboundLicense)

case class KafkaInboundLobby(
  hours: String)

case class KafkaInboundDriveUp(
  hours: String)

/**
  *
  * @param line_1       Line 1 of Address
  * @param line_2       Line 2 of Address
  * @param line_3       Line 3 of Address
  * @param city         City
  * @param county       County i.e. Division of State
  * @param state        State i.e. Division of Country
  * @param post_code    Post Code or Zip Code
  * @param country_code 2 letter country code: ISO 3166-1 alpha-2
  */
case class KafkaInboundAddress(
  line_1: String,
  line_2: String,
  line_3: String,
  city: String,
  county: String, // Division of State
  state: String, // Division of Country
  post_code: String,
  country_code: String)

case class KafkaInboundLocation(
  latitude: Double,
  longitude: Double)


// TODO Be consistent use camelCase

case class KafkaInboundAccount(
  accountId: String,
  bankId: String,
  label: String,
  number: String,
  `type`: String,
  balanceAmount: String,
  balanceCurrency: String,
  iban: String,
  owners: List[String],
  generate_public_view: Boolean,
  generate_accountants_view: Boolean,
  generate_auditors_view: Boolean)

case class KafkaInboundTransaction(
  transactionId: String,
  accountId: String,
  amount: String,
  bankId: String,
  completedDate: String,
  counterpartyId: String,
  counterpartyName: String,
  currency: String,
  description: String,
  newBalanceAmount: String,
  newBalanceCurrency: String,
  postedDate: String,
  `type`: String,
  userId: String
)

case class KafkaInboundAtm(
  id: String,
  bank_id: String,
  name: String,
  address: KafkaInboundAddress,
  location: KafkaInboundLocation,
  meta: KafkaInboundMeta
)

case class KafkaInboundProduct(
  bank_id: String,
  code: String,
  name: String,
  category: String,
  family: String,
  super_family: String,
  more_info_url: String,
  meta: KafkaInboundMeta
)


case class KafkaInboundCrmEvent(
  id: String, // crmEventId
  bank_id: String,
  customer: KafkaInboundCustomer,
  category: String,
  detail: String,
  channel: String,
  actual_date: String
)

case class KafkaInboundCustomer(
  name: String,
  number: String // customer number, also known as ownerId (owner of accounts) aka API User?
)

case class KafkaInboundTransactionId(
  transactionId: String
)

case class KafkaOutboundTransaction(
  action: String,
  version: String,
  userId: String,
  userName: String,
  accountId: String,
  currency: String,
  amount: String,
  otherAccountId: String,
  otherAccountCurrency: String,
  transactionType: String)

case class KafkaInboundChallengeLevel(
  limit: String,
  currency: String
)

case class KafkaInboundTransactionRequestStatus(
  transactionRequestId: String,
  bulkTransactionsStatus: List[KafkaInboundTransactionStatus]
)

case class KafkaInboundTransactionStatus(
  transactionId: String,
  transactionStatus: String,
  transactionTimestamp: String
) extends TransactionStatus

case class KafkaInboundCreateChallange(challengeId: String)

case class KafkaInboundValidateChallangeAnswer(answer: String)

case class KafkaInboundChargeLevel(
  currency: String,
  amount: String
)

case class KafkaInboundFXRate(
  from_currency_code: String,
  to_currency_code: String,
  conversion_value: Double,
  inverse_conversion_value: Double,
  effective_date: String
)

case class KafkaInboundTransactionRequestTypeCharge(
  transaction_request_type_id: String,
  bank_id: String,
  charge_currency: String,
  charge_amount: String,
  charge_summary: String
)

object KafkaJSONFactory_vMar2017 {
  
}


