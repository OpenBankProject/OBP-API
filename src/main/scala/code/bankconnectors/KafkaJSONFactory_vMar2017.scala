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

import code.api.util.APIUtil.{MessageDoc}
import code.fx.FXRate
import code.metadata.counterparties.CounterpartyTrait
import code.model.dataAccess.MappedBankAccountData
import code.model._
import code.transactionrequests.TransactionRequestTypeCharge
import net.liftweb.json.JsonAST.JValue
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.today


//TODO this may be moved to connector trait as before : 99348d866c968dcc859c8ac525e06566d630523c
case class InboundBank(
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

// Only used for import
case class InboundAccountData(
  banks: List[InboundBank],
  users: List[InboundUser],
  accounts: List[InboundAccount]
)

case class OutboundUserByUsernamePassword(
  action: String,
  version: String,
  username: String,
  password: String
)

case class OutboundUserAccountViews(
  action: String,
  version: String,
  username: String,
  userId: String,
  bankId: String
)

case class OutboundBanks(
  action: String,
  version: String,
  username: String,
  userId: String
)

case class OUTTBank(
  action: String,
  version: String,
  bankId: String,
  userId: String,
  username: String
)

case class OutboundChallengeThreshold(
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

case class OutboundChargeLevel(
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

case class OutboundChallenge(
  action: String,
  version: String,
  bankId: String,
  accountId: String,
  userId: String,
  username: String,
  transactionRequestType: String,
  transactionRequestId: String
)

case class OutboundChallengeAnswer(
  action: String,
  version: String,
  userId: String,
  username: String,
  challengeId: String,
  hashOfSuppliedAnswer: String
)

case class OUTTTransaction(
  action: String,
  version: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String,
  transactionId: String
)

case class OutboundTransactionsQueryWithParams(
  action: String,
  version: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String,
  queryParams: String
)

case class OutboundBankAccount(
  action: String,
  version: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String
)

case class OutboundBankAccounts(
  action: String,
  version: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String
)

case class OutboundAccountByNumber(
  action: String,
  version: String,
  userId: String,
  username: String,
  bankId: String,
  number: String
)

case class OutboundSaveTransaction(
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

case class OutboundTransactionRequestStatuses(
  action: String,
  version: String
)

case class OutboundCurrentFxRate(
  action: String,
  version: String,
  userId: String,
  username: String,
  fromCurrencyCode: String,
  toCurrencyCode: String
)

case class OutboundTransactionRequestTypeCharge(
  action: String,
  version: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String,
  viewId: String,
  transactionRequestType: String
)

case class InboundValidatedUser(email: String,
  displayName: String)


case class OutboundCounterpartyByIban(
  action: String,
  version: String,
  userId: String,
  username: String,
  otherAccountRoutingAddress: String,
  otherAccountRoutingScheme: String
)

case class OutboundCounterpartyByCounterpartyId(
  action: String,
  version: String,
  userId: String,
  username: String,
  counterpartyId: String
)


// Don't use Snake case for Kafka messages
case class InboundCounterpartySnake(
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

case class InboundCounterparty(counterparty: InboundCounterpartySnake) extends CounterpartyTrait {
  
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

case class Bank2(r: InboundBank) extends Bank {
  
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

case class BankAccount2(r: InboundAccount) extends BankAccount {
  
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

// TODO Get rid of Snake Case version of this.
case class InboundFXRateCamelCase(kafkaInboundFxRate: InboundFXRate) extends FXRate {
  
  def fromCurrencyCode: String = kafkaInboundFxRate.from_currency_code
  def toCurrencyCode: String = kafkaInboundFxRate.to_currency_code
  def conversionValue: Double = kafkaInboundFxRate.conversion_value
  def inverseConversionValue: Double = kafkaInboundFxRate.inverse_conversion_value
  //TODO need to add error handling here for String --> Date transfer
  def effectiveDate: Date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(kafkaInboundFxRate.effective_date)
}

case class InboundTransactionRequestTypeChargeCamelCase(kafkaInboundTransactionRequestTypeCharge: InboundTransactionRequestTypeCharge) extends TransactionRequestTypeCharge {

  def transactionRequestTypeId: String = kafkaInboundTransactionRequestTypeCharge.transaction_request_type_id
  def bankId: String = kafkaInboundTransactionRequestTypeCharge.bank_id
  def chargeCurrency: String = kafkaInboundTransactionRequestTypeCharge.charge_currency
  def chargeAmount: String = kafkaInboundTransactionRequestTypeCharge.charge_amount
  def chargeSummary: String = kafkaInboundTransactionRequestTypeCharge.charge_summary
}

case class InboundTransactionRequestStatus2(kafkaInboundTransactionRequestStatus: InboundTransactionRequestStatus) extends TransactionRequestStatus {

  override def transactionRequestid: String = kafkaInboundTransactionRequestStatus.transactionRequestId
  override def bulkTransactionsStatus: List[TransactionStatus] = kafkaInboundTransactionRequestStatus.bulkTransactionsStatus
}


/** Bank Branches
  *
  * @param id       Uniquely identifies the Branch within the Bank. SHOULD be url friendly (no spaces etc.) Used in URLs
  * @param bankId  MUST match bank_id in Banks
  * @param name     Informal name for the Branch
  * @param address  Address
  * @param location Geolocation
  * @param meta     Meta information including the license this information is published under
  * @param lobby    Info about when the lobby doors are open
  * @param driveUp  Info about when automated facilities are open e.g. cash point machine
  */
case class InboundBranch(
                          id: String,
                          bankId: String,
                          name: String,
                          address: InboundAddress,
                          location: InboundLocation,
                          meta: InboundMeta,
                          lobby: Option[InboundLobby],
                          driveUp: Option[InboundDriveUp]
)

case class InboundLicense(
  id: String,
  name: String
)

case class InboundMeta(
  license: InboundLicense
)

case class InboundLobby(
  hours: String
)

case class InboundDriveUp(
  hours: String
)

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
case class InboundAddress(
  line_1: String,
  line_2: String,
  line_3: String,
  city: String,
  county: String, // Division of State
  state: String, // Division of Country
  post_code: String,
  country_code: String
)

case class InboundLocation(
  latitude: Double,
  longitude: Double
)


// TODO Be consistent use camelCase

case class InboundAccount(
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
  generate_auditors_view: Boolean
)

//InboundTransaction --> InternalTransaction -->OutboundTransaction
case class InternalTransaction(
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

case class InboundAtm(
  id: String,
  bank_id: String,
  name: String,
  address: InboundAddress,
  location: InboundLocation,
  meta: InboundMeta
)

case class InboundProduct(
  bank_id: String,
  code: String,
  name: String,
  category: String,
  family: String,
  super_family: String,
  more_info_url: String,
  meta: InboundMeta
)


case class InboundCrmEvent(
  id: String, // crmEventId
  bank_id: String,
  customer: InboundCustomer,
  category: String,
  detail: String,
  channel: String,
  actual_date: String
)

case class InboundCustomer(
  name: String,
  number: String // customer number, also known as ownerId (owner of accounts) aka API User?
)

case class InboundTransactionId(
  transactionId: String
)

case class OutboundTransaction(
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

case class InboundChallengeLevel(
  limit: String,
  currency: String
)

case class InboundTransactionRequestStatus(
  transactionRequestId: String,
  bulkTransactionsStatus: List[InboundTransactionStatus]
)

case class InboundTransactionStatus(
  transactionId: String,
  transactionStatus: String,
  transactionTimestamp: String
) extends TransactionStatus

case class OutboundCreateChallange(challengeId: String)

case class InboundValidateChallangeAnswer(answer: String)

case class InboundChargeLevel(
  currency: String,
  amount: String
)

// TODO Don't use snake_case in Kafka else we have to duplicate the case classes
case class InboundFXRate(
  from_currency_code: String,
  to_currency_code: String,
  conversion_value: Double,
  inverse_conversion_value: Double,
  effective_date: String
)

case class InboundTransactionRequestTypeCharge(
  transaction_request_type_id: String,
  bank_id: String,
  charge_currency: String,
  charge_amount: String,
  charge_summary: String
)

// Used to describe the Kafka message requests parameters for documentation in Json
case class MessageDocJson(
    action: String,
    connector_version: String,
    description: String,
    example_inbound_message: JValue,
    example_outbound_message: JValue,
    error_response_messages: List[JValue]
)

// Creates the json resource_docs
case class MessageDocsJson(message_docs: List[MessageDocJson])

object KafkaJSONFactory_vMar2017 {
  
  def createMessageDocsJson(messageDocsList: List[MessageDoc]): MessageDocsJson = {
    MessageDocsJson(messageDocsList.map(createMessageDocJson))
  }
  
  def createMessageDocJson(rd: MessageDoc): MessageDocJson = {
    MessageDocJson(
      action = rd.action,
      connector_version = rd.connectorVersion,
      description = rd.description,
      example_inbound_message = rd.exampleInboundMessage,
      example_outbound_message = rd.exampleOutboundMessage,
      error_response_messages = rd.errorResponseMessages
    )
  }
  
}


