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
package code.bankconnectors.vMar2017

import java.util.Date

import code.api.util.APIUtil
import code.api.util.APIUtil.{MessageDoc}
import code.fx.FXRate
import code.model._
import code.model.dataAccess.MappedBankAccountData
import code.transactionrequests.TransactionRequestTypeCharge
import com.openbankproject.commons.model.{CounterpartyTrait, _}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.today

import scala.collection.immutable.List



// Only used for import
case class InboundAccountData(
  banks: List[InboundBank],
  users: List[InboundUser],
  accounts: List[InboundAccount_vMar2017]
)

case class OutboundUserByUsernamePasswordBase(
  messageFormat: String,
  action: String,
  username: String,
  password: String
)extends OutboundMessageBase

case class OutboundAdapterInfo(
  messageFormat: String,
  action: String,
  date: String
) extends OutboundMessageBase

case class OutboundUserAccountViewsBase(
  messageFormat: String,
  action: String,
  username: String,
  userId: String,
  bankId: String
)extends OutboundMessageBase


case class OutboundBanksBase(
  messageFormat: String,
  action: String,
  username: String,
  userId: String
) extends OutboundMessageBase

case class OUTTBank(
  messageFormat: String,
  action: String,
  bankId: String,
  userId: String,
  username: String
)extends OutboundMessageBase

case class OutboundChallengeThresholdBase(
  messageFormat: String,
  action: String,
  bankId: String,
  accountId: String,
  viewId: String,
  transactionRequestType: String,
  currency: String,
  userId: String,
  username: String
) extends OutboundMessageBase

case class OutboundChargeLevelBase(
  messageFormat: String,
  action: String,
  bankId: String,
  accountId: String,
  viewId: String,
  userId: String,
  username: String,
  transactionRequestType: String,
  currency: String
) extends OutboundMessageBase

case class OutboundChallengeBase(
  messageFormat: String,
  action: String,
  bankId: String,
  accountId: String,
  userId: String,
  username: String,
  transactionRequestType: String,
  transactionRequestId: String
) extends OutboundMessageBase

case class OutboundChallengeAnswerBase(
  messageFormat: String,
  action: String,
  userId: String,
  username: String,
  challengeId: String,
  hashOfSuppliedAnswer: String
) extends OutboundMessageBase

case class OutboundTransactionQueryBase(
  messageFormat: String,
  action: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String,
  transactionId: String
) extends OutboundMessageBase

case class OutboundTransactionsQueryWithParamsBase(
  messageFormat: String,
  action: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String,
  queryParams: String
) extends OutboundMessageBase

case class OutboundBankAccountBase(
  messageFormat: String,
  action: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String
) extends OutboundMessageBase

case class OutboundBankAccountsBase(
  messageFormat: String,
  action: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String
) extends OutboundMessageBase

case class OutboundAccountByNumberBase(
  messageFormat: String,
  action: String,
  userId: String,
  username: String,
  bankId: String,
  number: String
) extends OutboundMessageBase

case class OutboundCounterpartyByIbanBase(
  messageFormat: String,
  action: String,
  userId: String,
  username: String,
  otherAccountRoutingAddress: String,
  otherAccountRoutingScheme: String
)  extends OutboundMessageBase

case class OutboundCounterpartyByCounterpartyIdBase(
  messageFormat: String,
  action: String,
  userId: String,
  username: String,
  counterpartyId: String
) extends OutboundMessageBase

case class OutboundSaveTransactionBase(
  messageFormat: String,
  action: String,
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
) extends OutboundMessageBase

case class OutboundTransactionRequestStatusesBase(
  messageFormat: String,
  action: String
) extends OutboundMessageBase

case class OutboundCurrentFxRateBase(
  messageFormat: String,
  action: String,
  userId: String,
  username: String,
  bankId: String,
  fromCurrencyCode: String,
  toCurrencyCode: String
) extends OutboundMessageBase

case class OutboundTransactionRequestTypeChargeBase(
  messageFormat: String,
  action: String,
  userId: String,
  username: String,
  bankId: String,
  accountId: String,
  viewId: String,
  transactionRequestType: String
) extends OutboundMessageBase

case class InboundValidatedUser(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  email: String,
  displayName: String
) extends InboundMessageBase

case class InboundCounterparty(
  errorCode: String,
  name: String,
  createdByUserId: String,
  thisBankId: String,
  thisAccountId: String,
  thisViewId: String,
  counterpartyId: String,
  otherBankRoutingScheme: String,
  otherBankRoutingAddress: String,
  otherAccountRoutingScheme: String,
  otherAccountRoutingAddress: String,
  otherBranchRoutingScheme: String,
  otherBranchRoutingAddress: String,
  isBeneficiary: Boolean
) extends InboundMessageBase

case class CounterpartyTrait2(counterparty: InboundCounterparty) extends CounterpartyTrait {
  
  def createdByUserId: String = counterparty.createdByUserId
  def name: String = counterparty.name
  def thisBankId: String = counterparty.thisBankId
  def thisAccountId: String = counterparty.thisAccountId
  def thisViewId: String = counterparty.thisViewId
  def counterpartyId: String = counterparty.counterpartyId
  def otherAccountRoutingScheme: String = counterparty.otherAccountRoutingScheme
  def otherAccountRoutingAddress: String = counterparty.otherAccountRoutingAddress
  def otherBankRoutingScheme: String = counterparty.otherBankRoutingScheme
  def otherBankRoutingAddress: String = counterparty.otherBankRoutingAddress
  def otherBranchRoutingScheme: String = counterparty.otherBranchRoutingScheme
  def otherBranchRoutingAddress: String = counterparty.otherBranchRoutingAddress
  def isBeneficiary: Boolean = counterparty.isBeneficiary
  def description: String = ""
  def otherAccountSecondaryRoutingScheme: String = ""
  def otherAccountSecondaryRoutingAddress: String = ""
  def bespoke: List[CounterpartyBespoke] = Nil
}

case class InboundBank(
  bankId: String,
  name: String,
  logo: String,
  url: String
)

case class Bank2(r: InboundBank) extends Bank {
  
  def fullName = r.name
  def shortName = r.name
  def logoUrl = r.logo
  def bankId = BankId(r.bankId)
  def nationalIdentifier = "None"
  def swiftBic = "None"
  def websiteUrl = r.url
  def bankRoutingScheme = "None"
  def bankRoutingAddress = "None"
}

case class InboundAccount_vMar2017(
  errorCode: String,
  accountId: String,
  bankId: String,
  label: String,
  number: String,
  `type`: String,
  balanceAmount: String,
  balanceCurrency: String,
  iban: String,
  owners: List[String],
  generatePublicView: Boolean,
  generateAccountantsView: Boolean,
  generateAuditorsView: Boolean,
  accountRoutingScheme: String  = "None",
  accountRoutingAddress: String  = "None",
  branchId: String  = "None"
)extends InboundMessageBase






case class BankAccount2(r: InboundAccount_vMar2017) extends BankAccount {
  
  def accountId: AccountId = AccountId(r.accountId)
  def accountType: String = r.`type`
  def balance: BigDecimal = BigDecimal(r.balanceAmount)
  def currency: String = r.balanceCurrency
  def name: String = r.owners.head
  def iban: Option[String] = Some(r.iban)
  def number: String = r.number
  def bankId: BankId = BankId(r.bankId)
  def lastUpdate: Date = APIUtil.DateWithMsFormat.parse(today.getTime.toString)
  def accountHolder: String = r.owners.head
  
  // Fields modifiable from OBP are stored in mapper
  def label: String = (for {
    d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, r.accountId))
  } yield {
    d.getLabel
  }).getOrElse(r.number)
  
  def accountRoutingScheme: String = r.accountRoutingScheme
  def accountRoutingAddress: String = r.accountRoutingAddress
  def accountRoutings: List[AccountRouting] = List()
  def branchId: String = r.branchId

  def accountRules: List[AccountRule] = List()
  
}

case class InboundFXRate(
  errorCode: String,
  bankId: String,
  fromCurrencyCode: String,
  toCurrencyCode: String,
  conversionValue: Double,
  inverseConversionValue: Double,
  effectiveDate: String
)extends InboundMessageBase

case class FXRate2(inboundFxRate: InboundFXRate) extends FXRate {

  def bankId: BankId = BankId(inboundFxRate.bankId)
  def fromCurrencyCode: String = inboundFxRate.fromCurrencyCode
  def toCurrencyCode: String = inboundFxRate.toCurrencyCode
  def conversionValue: Double = inboundFxRate.conversionValue
  def inverseConversionValue: Double = inboundFxRate.inverseConversionValue
  //TODO need to add error handling here for String --> Date transfer
  def effectiveDate: Date = APIUtil.DateWithMsFormat.parse(inboundFxRate.effectiveDate)
}

case class InboundTransactionRequestTypeCharge(
  errorCode: String,
  transactionRequestType: String,
  bankId: String,
  chargeCurrency: String,
  chargeAmount: String,
  chargeSummary: String
)extends InboundMessageBase

case class TransactionRequestTypeCharge2(inboundTransactionRequestTypeCharge: InboundTransactionRequestTypeCharge) extends TransactionRequestTypeCharge {

  def transactionRequestTypeId: String = inboundTransactionRequestTypeCharge.transactionRequestType
  def bankId: String = inboundTransactionRequestTypeCharge.bankId
  def chargeCurrency: String = inboundTransactionRequestTypeCharge.chargeCurrency
  def chargeAmount: String = inboundTransactionRequestTypeCharge.chargeAmount
  def chargeSummary: String = inboundTransactionRequestTypeCharge.chargeSummary
}

case class InboundTransactionStatus(
  transactionId: String,
  transactionStatus: String,
  transactionTimestamp: String
) extends TransactionStatus

case class InboundTransactionRequestStatus(
  transactionRequestId: String,
  bulkTransactionsStatus: List[InboundTransactionStatus]
)

case class TransactionRequestStatus2(kafkaInboundTransactionRequestStatus: InboundTransactionRequestStatus) extends TransactionRequestStatus {

  override def transactionRequestId: String = kafkaInboundTransactionRequestStatus.transactionRequestId
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
  * @param line1       Line 1 of Address
  * @param line2       Line 2 of Address
  * @param line3       Line 3 of Address
  * @param city         City
  * @param county       County i.e. Division of State
  * @param state        State i.e. Division of Country
  * @param postCode    Post Code or Zip Code
  * @param countryCode 2 letter country code: ISO 3166-1 alpha-2
  */
case class InboundAddress(
  line1: String,
  line2: String,
  line3: String,
  city: String,
  county: String, // Division of State
  state: String, // Division of Country
  postCode: String,
  countryCode: String
)

case class InboundLocation(
  latitude: Double,
  longitude: Double
)


//InboundTransaction --> InternalTransaction -->OutboundTransaction
case class InternalTransaction(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
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
)extends InboundMessageBase

case class InboundAtm(
  id: String,
  bankId: String,
  name: String,
  address: InboundAddress,
  location: InboundLocation,
  meta: InboundMeta
)

case class InboundProduct(
  bankId: String,
  code: String,
  name: String,
  category: String,
  family: String,
  superFamily: String,
  moreInfoUrl: String,
  meta: InboundMeta
)


case class InboundCrmEvent(
  id: String, // crmEventId
  bankId: String,
  customer: InboundCustomer,
  category: String,
  detail: String,
  channel: String,
  actualDate: String
)

case class InboundCustomer(
  name: String,
  number: String // customer number, also known as ownerId (owner of accounts) aka API User?
)

case class InboundTransactionId(
  errorCode: String,
  transactionId: String
)extends InboundMessageBase

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
  errorCode: String,
  limit: String,
  currency: String
)extends InboundMessageBase


case class InboundCreateChallange(
  errorCode: String,
  challengeId: String
)extends InboundMessageBase

case class InboundValidateChallangeAnswer(
  answer: String,
  errorCode: String
)extends InboundMessageBase

case class InboundChargeLevel(
  errorCode: String,
  currency: String,
  amount: String
)extends InboundMessageBase


object JsonFactory_vMar2017 {
  // moved to Json factory v2.0.0
}


