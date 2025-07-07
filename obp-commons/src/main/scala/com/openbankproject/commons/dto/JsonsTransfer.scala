/**
 * Open Bank Project - API
 * Copyright (C) 2011-2025, TESOBE GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Email: contact@tesobe.com
 * TESOBE GmbH.
 * Osloer Strasse 16/17
 * Berlin 13359, Germany
 *
 * This product includes software developed at
 * TESOBE (http://www.tesobe.com/)
 */

package com.openbankproject.commons.dto

import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import com.openbankproject.commons.model.enums.{TransactionRequestStatus, _}
import net.liftweb.json.{JObject, JValue}

import java.util.Date

trait InBoundTrait[T] {
  val inboundAdapterCallContext: InboundAdapterCallContext
  val status: Status
  val data: T
}

//--------generated


case class OutBoundGetBankAccountBalancesByAccountId (outboundAdapterCallContext: OutboundAdapterCallContext,
  accountId: AccountId) extends TopicTrait
case class InBoundGetBankAccountBalancesByAccountId (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[BankAccountBalanceTraitCommons]) extends InBoundTrait[List[BankAccountBalanceTraitCommons]]


case class OutBoundGetBankAccountBalanceById (outboundAdapterCallContext: OutboundAdapterCallContext,
  balanceId: BalanceId) extends TopicTrait
case class InBoundGetBankAccountBalanceById (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountBalanceTraitCommons) extends InBoundTrait[BankAccountBalanceTraitCommons]


case class OutBoundCreateOrUpdateBankAccountBalance (outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  balanceId: Option[BalanceId],
  balanceType: String,
  balanceAmount: BigDecimal) extends TopicTrait
case class InBoundCreateOrUpdateBankAccountBalance (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountBalanceTraitCommons) extends InBoundTrait[BankAccountBalanceTraitCommons]


case class OutBoundDeleteBankAccountBalance (outboundAdapterCallContext: OutboundAdapterCallContext,
  balanceId: BalanceId) extends TopicTrait
case class InBoundDeleteBankAccountBalance (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundGetRegulatedEntities (outboundAdapterCallContext: OutboundAdapterCallContext) extends TopicTrait
case class InBoundGetRegulatedEntities (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[RegulatedEntityTraitCommons]) extends InBoundTrait[List[RegulatedEntityTraitCommons]]


case class OutBoundGetRegulatedEntityByEntityId (outboundAdapterCallContext: OutboundAdapterCallContext,
  regulatedEntityId: String) extends TopicTrait
case class InBoundGetRegulatedEntityByEntityId (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: RegulatedEntityTraitCommons) extends InBoundTrait[RegulatedEntityTraitCommons]

case class OutBoundGetObpConnectorLoopback(outboundAdapterCallContext: OutboundAdapterCallContext) extends TopicTrait

case class InBoundGetObpConnectorLoopback(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: ObpApiLoopback) extends InBoundTrait[ObpApiLoopback]

case class OutBoundGetChallengeThreshold(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: String,
  accountId: String,
  viewId: String,
  transactionRequestType: String,
  currency: String,
  userId: String,
  username: String) extends TopicTrait

case class InBoundGetChallengeThreshold(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AmountOfMoney) extends InBoundTrait[AmountOfMoney]


case class OutBoundGetChargeLevel(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  viewId: ViewId,
  userId: String,
  username: String,
  transactionRequestType: String,
  currency: String) extends TopicTrait

case class InBoundGetChargeLevel(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AmountOfMoney) extends InBoundTrait[AmountOfMoney]

case class OutBoundGetChargeLevelC2(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  viewId: ViewId,
  userId: String,
  username: String,
  transactionRequestType: String,
  currency: String,
  amount: String,
  toAccountRoutings: List[AccountRouting],
  customAttributes: List[CustomAttribute]) extends TopicTrait

case class InBoundGetChargeLevelC2(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AmountOfMoney) extends InBoundTrait[AmountOfMoney]


case class OutBoundGetBank(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId) extends TopicTrait

case class InBoundGetBank(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankCommons) extends InBoundTrait[BankCommons]

case class OutBoundGetBankAccountsForUser(outboundAdapterCallContext: OutboundAdapterCallContext,
  provider: String, username: String) extends TopicTrait

case class InBoundGetBankAccountsForUser(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[InboundAccountCommons]) extends InBoundTrait[List[InboundAccountCommons]]


case class OutBoundGetBankAccount(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId) extends TopicTrait

case class InBoundGetBankAccount(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetCoreBankAccounts(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankIdAccountIds: List[BankIdAccountId]) extends TopicTrait

case class InBoundGetCoreBankAccounts(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CoreAccount]) extends InBoundTrait[List[CoreAccount]]

case class OutBoundGetBankAccountsBalances(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankIdAccountIds: List[BankIdAccountId]) extends TopicTrait

case class InBoundGetBankAccountsBalances(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AccountsBalances) extends InBoundTrait[AccountsBalances]

case class OutBoundGetBankAccountBalances(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankIdAccountId: BankIdAccountId) extends TopicTrait

case class InBoundGetBankAccountBalances(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AccountBalances) extends InBoundTrait[AccountBalances]

case class OutBoundGetCoreBankAccountsHeld(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankIdAccountIds: List[BankIdAccountId]) extends TopicTrait

case class InBoundGetCoreBankAccountsHeld(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[AccountHeld]) extends InBoundTrait[List[AccountHeld]]


case class OutBoundCheckBankAccountExists(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId) extends TopicTrait

case class InBoundCheckBankAccountExists(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetCounterpartyTrait(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  couterpartyId: String) extends TopicTrait

case class InBoundGetCounterpartyTrait(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyTraitCommons) extends InBoundTrait[CounterpartyTraitCommons]


case class OutBoundGetCounterpartyByCounterpartyId(outboundAdapterCallContext: OutboundAdapterCallContext,
  counterpartyId: CounterpartyId) extends TopicTrait

case class InBoundGetCounterpartyByCounterpartyId(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyTraitCommons) extends InBoundTrait[CounterpartyTraitCommons]


case class OutBoundGetCounterpartyByIban(outboundAdapterCallContext: OutboundAdapterCallContext,
  iban: String) extends TopicTrait

case class InBoundGetCounterpartyByIban(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyTraitCommons) extends InBoundTrait[CounterpartyTraitCommons]


case class OutBoundGetTransactions(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  limit: Int,
  offset: Int,
  fromDate: String,
  toDate: String) extends TopicTrait

case class InBoundGetTransactions(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[Transaction]) extends InBoundTrait[List[Transaction]]


case class OutBoundGetTransaction(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  transactionId: TransactionId) extends TopicTrait

case class InBoundGetTransaction(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Transaction) extends InBoundTrait[Transaction]

case class OutBoundMakePaymentv210(outboundAdapterCallContext: OutboundAdapterCallContext,
  fromAccount: BankAccount,
  toAccount: BankAccount,
  transactionRequestId: TransactionRequestId,
  transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
  amount: BigDecimal,
  description: String,
  transactionRequestType: TransactionRequestType,
  chargePolicy: String) extends TopicTrait

case class InBoundMakePaymentv210(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionId) extends InBoundTrait[TransactionId]

case class OutBoundNotifyTransactionRequest(outboundAdapterCallContext: OutboundAdapterCallContext,
  fromAccount: BankAccount, toAccount: BankAccount,
  transactionRequest: TransactionRequest) extends TopicTrait

case class InBoundNotifyTransactionRequest(inboundAdapterCallContext: InboundAdapterCallContext,
  status: Status, data: TransactionRequestStatusValue) extends InBoundTrait[TransactionRequestStatusValue]

case class OutBoundMakePaymentV400(outboundAdapterCallContext: OutboundAdapterCallContext,
  transactionRequest: TransactionRequest,
  reasons: Option[List[TransactionRequestReason]]) extends TopicTrait

case class InBoundMakePaymentV400(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionId) extends InBoundTrait[TransactionId]

case class OutBoundCancelPaymentV400(outboundAdapterCallContext: OutboundAdapterCallContext,
  transactionId: TransactionId) extends TopicTrait

case class InBoundCancelPaymentV400(inboundAdapterCallContext:
InboundAdapterCallContext,
  status: Status, data: CancelPayment) extends InBoundTrait[CancelPayment]


case class OutBoundCreateTransactionRequestv210(outboundAdapterCallContext: OutboundAdapterCallContext,
  initiator: User, //TODO FIXME
  viewId: ViewId,
  fromAccount: BankAccount,
  toAccount: BankAccount,
  transactionRequestType: TransactionRequestType,
  transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
  detailsPlain: String,
  chargePolicy: String, challengeType: Option[String], scaMethod: Option[SCA]) extends TopicTrait

case class InBoundCreateTransactionRequestv210(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundCreateTransactionAfterChallengeV210(outboundAdapterCallContext: OutboundAdapterCallContext,
  fromAccount: BankAccount,
  transactionRequest: TransactionRequest) extends TopicTrait

case class InBoundCreateTransactionAfterChallengeV210(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundGetBranch(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  branchId: BranchId) extends TopicTrait

case class InBoundGetBranch(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BranchTCommons) extends InBoundTrait[BranchTCommons]


case class OutBoundGetBranches(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId, limit: Int, offset: Int, fromDate: String, toDate: String) extends TopicTrait

case class InBoundGetBranches(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[BranchTCommons]) extends InBoundTrait[List[BranchTCommons]]


case class OutBoundGetAtm(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  atmId: AtmId) extends TopicTrait

case class InBoundGetAtm(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AtmTCommons) extends InBoundTrait[AtmTCommons]


case class OutBoundGetAtms(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId, limit: Int, offset: Int, fromDate: String, toDate: String) extends TopicTrait

case class InBoundGetAtms(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[AtmTCommons]) extends InBoundTrait[List[AtmTCommons]]


case class OutBoundCreateTransactionAfterChallengev300(outboundAdapterCallContext: OutboundAdapterCallContext,
  initiator: User, //TODO fixme
  fromAccount: BankAccount,
  transReqId: TransactionRequestId,
  transactionRequestType: TransactionRequestType) extends TopicTrait

case class InBoundCreateTransactionAfterChallengev300(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundMakePaymentv300(outboundAdapterCallContext: OutboundAdapterCallContext,
  initiator: User, //TODO fixme
  fromAccount: BankAccount,
  toAccount: BankAccount,
  toCounterparty: CounterpartyTrait,
  transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
  transactionRequestType: TransactionRequestType,
  chargePolicy: String) extends TopicTrait

case class InBoundMakePaymentv300(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionId) extends InBoundTrait[TransactionId]


case class OutBoundCreateTransactionRequestv300(outboundAdapterCallContext: OutboundAdapterCallContext,
  initiator: User, //TODO fixme
  viewId: ViewId,
  fromAccount: BankAccount,
  toAccount: BankAccount,
  toCounterparty: CounterpartyTrait,
  transactionRequestType: TransactionRequestType,
  transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
  detailsPlain: String,
  chargePolicy: String) extends TopicTrait

case class InBoundCreateTransactionRequestv300(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]

case class OutBoundCreateCustomer(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  legalName: String,
  mobileNumber: String,
  email: String,
  faceImage: CustomerFaceImageTrait,
  dateOfBirth: Date,
  relationshipStatus: String,
  dependents: Int,
  dobOfDependents: List[Date],
  highestEducationAttained: String,
  employmentStatus: String,
  kycStatus: Boolean,
  lastOkDate: Date,
  creditRating: Option[CreditRatingTrait],
  creditLimit: Option[AmountOfMoneyTrait],
  title: String,
  branchId: String,
  nameSuffix: String) extends TopicTrait

case class InBoundCreateCustomer(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerCommons) extends InBoundTrait[CustomerCommons]

case class OutBoundGetCustomersByUserId(outboundAdapterCallContext: OutboundAdapterCallContext,
  userId: String) extends TopicTrait

case class InBoundGetCustomersByUserId(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CustomerCommons]) extends InBoundTrait[List[CustomerCommons]]

case class OutBoundGetCustomerByCustomerNumber(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerNumber: String,
  bankId: BankId) extends TopicTrait

case class InBoundGetCustomerByCustomerNumber(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerCommons) extends InBoundTrait[CustomerCommons]


case class OutBoundGetCustomerAddress(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerId: String) extends TopicTrait

case class InBoundGetCustomerAddress(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CustomerAddressCommons]) extends InBoundTrait[List[CustomerAddressCommons]]


case class OutBoundCreateCustomerAddress(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerId: String,
  line1: String,
  line2: String,
  line3: String,
  city: String,
  county: String,
  state: String,
  postcode: String,
  countryCode: String,
  tags: String,
  status: String) extends TopicTrait

case class InBoundCreateCustomerAddress(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerAddressCommons) extends InBoundTrait[CustomerAddressCommons]


case class OutBoundUpdateCustomerAddress(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerAddressId: String,
  line1: String,
  line2: String,
  line3: String,
  city: String,
  county: String,
  state: String,
  postcode: String,
  countryCode: String,
  tags: String,
  status: String) extends TopicTrait

case class InBoundUpdateCustomerAddress(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerAddressCommons) extends InBoundTrait[CustomerAddressCommons]


case class OutBoundCreateTaxResidence(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerId: String,
  domain: String,
  taxNumber: String) extends TopicTrait

case class InBoundCreateTaxResidence(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TaxResidenceCommons) extends InBoundTrait[TaxResidenceCommons]

case class OutBoundGetBankAccountsBalancesByAccountIds (outboundAdapterCallContext: OutboundAdapterCallContext,
  accountIds: List[AccountId]) extends TopicTrait
case class InBoundGetBankAccountsBalancesByAccountIds (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[BankAccountBalanceTraitCommons]) extends InBoundTrait[List[BankAccountBalanceTraitCommons]]


case class OutBoundGetTaxResidence(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerId: String) extends TopicTrait

case class InBoundGetTaxResidence(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TaxResidenceCommons]) extends InBoundTrait[List[TaxResidenceCommons]]


case class OutBoundGetCustomers(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  limit: Int,
  offset: Int,
  fromDate: String,
  toDate: String) extends TopicTrait

case class InBoundGetCustomers(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CustomerCommons]) extends InBoundTrait[List[CustomerCommons]]


case class OutBoundGetCheckbookOrders(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: String,
  accountId: String) extends TopicTrait

case class InBoundGetCheckbookOrders(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CheckbookOrdersJson) extends InBoundTrait[CheckbookOrdersJson]


case class OutBoundGetStatusOfCreditCardOrder(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: String,
  accountId: String) extends TopicTrait

case class InBoundGetStatusOfCreditCardOrder(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CardObjectJson]) extends InBoundTrait[List[CardObjectJson]]


case class OutBoundCreateUserAuthContext(outboundAdapterCallContext: OutboundAdapterCallContext,
  userId: String,
  key: String,
  value: String) extends TopicTrait

case class InBoundCreateUserAuthContext(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: UserAuthContextCommons) extends InBoundTrait[UserAuthContextCommons]


case class OutBoundGetUserAuthContexts(outboundAdapterCallContext: OutboundAdapterCallContext,
  userId: String) extends TopicTrait

case class InBoundGetUserAuthContexts(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[UserAuthContextCommons]) extends InBoundTrait[List[UserAuthContextCommons]]


case class OutBoundCreateOrUpdateProductAttribute(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  productCode: ProductCode,
  productAttributeId: Option[String],
  name: String,
  productAttributeType: enums.ProductAttributeType.Value,
  value: String,
  isActive: Option[Boolean]) extends TopicTrait

case class InBoundCreateOrUpdateProductAttribute(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: ProductAttributeCommons) extends InBoundTrait[ProductAttributeCommons]


case class OutBoundGetProductAttributeById(outboundAdapterCallContext: OutboundAdapterCallContext,
  productAttributeId: String) extends TopicTrait

case class InBoundGetProductAttributeById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: ProductAttributeCommons) extends InBoundTrait[ProductAttributeCommons]


case class OutBoundGetProductAttributesByBankAndCode(outboundAdapterCallContext: OutboundAdapterCallContext,
  bank: BankId,
  productCode: ProductCode) extends TopicTrait

case class InBoundGetProductAttributesByBankAndCode(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ProductAttributeCommons]) extends InBoundTrait[List[ProductAttributeCommons]]


case class OutBoundCreateOrUpdateAccountAttribute(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  productCode: ProductCode,
  productAttributeId: Option[String],
  name: String,
  accountAttributeType: enums.AccountAttributeType.Value,
  value: String,
  productInstanceCode: Option[String] = None) extends TopicTrait

case class InBoundCreateOrUpdateAccountAttribute(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AccountAttributeCommons) extends InBoundTrait[AccountAttributeCommons]


case class OutBoundCreateAccountApplication(outboundAdapterCallContext: OutboundAdapterCallContext,
  productCode: ProductCode,
  userId: Option[String],
  customerId: Option[String]) extends TopicTrait

case class InBoundCreateAccountApplication(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AccountApplicationCommons) extends InBoundTrait[AccountApplicationCommons]


case class OutBoundGetAllAccountApplication(outboundAdapterCallContext: OutboundAdapterCallContext) extends TopicTrait

case class InBoundGetAllAccountApplication(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[AccountApplicationCommons]) extends InBoundTrait[List[AccountApplicationCommons]]


case class OutBoundGetAccountApplicationById(outboundAdapterCallContext: OutboundAdapterCallContext,
  accountApplicationId: String) extends TopicTrait

case class InBoundGetAccountApplicationById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AccountApplicationCommons) extends InBoundTrait[AccountApplicationCommons]


case class OutBoundUpdateAccountApplicationStatus(outboundAdapterCallContext: OutboundAdapterCallContext,
  accountApplicationId: String,
  status: String) extends TopicTrait

case class InBoundUpdateAccountApplicationStatus(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AccountApplicationCommons) extends InBoundTrait[AccountApplicationCommons]


case class OutBoundGetOrCreateProductCollection(outboundAdapterCallContext: OutboundAdapterCallContext,
  collectionCode: String,
  productCodes: List[String]) extends TopicTrait

case class InBoundGetOrCreateProductCollection(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ProductCollectionCommons]) extends InBoundTrait[List[ProductCollectionCommons]]


case class OutBoundGetProductCollection(outboundAdapterCallContext: OutboundAdapterCallContext,
  collectionCode: String) extends TopicTrait

case class InBoundGetProductCollection(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ProductCollectionCommons]) extends InBoundTrait[List[ProductCollectionCommons]]


case class OutBoundGetOrCreateProductCollectionItem(outboundAdapterCallContext: OutboundAdapterCallContext,
  collectionCode: String,
  memberProductCodes: List[String]) extends TopicTrait

case class InBoundGetOrCreateProductCollectionItem(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ProductCollectionItemCommons]) extends InBoundTrait[List[ProductCollectionItemCommons]]


case class OutBoundGetProductCollectionItem(outboundAdapterCallContext: OutboundAdapterCallContext,
  collectionCode: String) extends TopicTrait

case class InBoundGetProductCollectionItem(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ProductCollectionItemCommons]) extends InBoundTrait[List[ProductCollectionItemCommons]]


case class OutBoundGetProductCollectionItemsTree(outboundAdapterCallContext: OutboundAdapterCallContext,
  collectionCode: String,
  bankId: String) extends TopicTrait

case class ProductCollectionItemsTree(productCollectionItem: ProductCollectionItemCommons, product: ProductCommons, attributes: List[ProductAttributeCommons])

case class InBoundGetProductCollectionItemsTree(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ProductCollectionItemsTree])
  extends InBoundTrait[List[ProductCollectionItemsTree]]


case class OutBoundCreateMeeting(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  staffUser: User, //TODO fixme
  customerUser: User, //TODO fixme
  providerId: String,
  purposeId: String,
  when: Date,
  sessionId: String,
  customerToken: String,
  staffToken: String,
  creator: ContactDetails,
  invitees: List[Invitee]) extends TopicTrait

case class InBoundCreateMeeting(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: MeetingCommons) extends InBoundTrait[MeetingCommons]


case class OutBoundGetMeetings(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  user: User) extends TopicTrait //TODO fixme

case class InBoundGetMeetings(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[MeetingCommons]) extends InBoundTrait[List[MeetingCommons]]


case class OutBoundGetMeeting(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  user: User, //TODO fixme
  meetingId: String) extends TopicTrait

case class InBoundGetMeeting(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: MeetingCommons) extends InBoundTrait[MeetingCommons]

//create bound case classes
case class OutBoundCreateChallenge(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  userId: String,
  transactionRequestType: TransactionRequestType,
  transactionRequestId: String,
  scaMethod: Option[SCA]) extends TopicTrait

case class InBoundCreateChallenge(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: String) extends InBoundTrait[String]

case class OutBoundCreateCounterparty(outboundAdapterCallContext: OutboundAdapterCallContext, name: String, description: String, currency: String, createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String, otherAccountRoutingScheme: String, otherAccountRoutingAddress: String,
  otherAccountSecondaryRoutingScheme: String, otherAccountSecondaryRoutingAddress: String,
  otherBankRoutingScheme: String, otherBankRoutingAddress: String,
  otherBranchRoutingScheme: String, otherBranchRoutingAddress: String, isBeneficiary: Boolean,
  bespoke: List[CounterpartyBespoke]) extends TopicTrait

case class InBoundCreateCounterparty(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyTraitCommons) extends InBoundTrait[CounterpartyTraitCommons]

case class OutBoundGetTransactionRequests210(outboundAdapterCallContext: OutboundAdapterCallContext, initiator: User, fromAccount: BankAccount) extends TopicTrait

case class InBoundGetTransactionRequests210(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequest]) extends InBoundTrait[List[TransactionRequest]]

case class OutBoundGetTransactionsCore(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, accountId: AccountId, limit: Int, offset: Int, fromDate: String, toDate: String) extends TopicTrait

case class InBoundGetTransactionsCore(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionCore]) extends InBoundTrait[List[TransactionCore]]

//-------- return type are not Future--------------------------------------------------------------------------------------------------

case class OutBoundGetAdapterInfo(outboundAdapterCallContext: OutboundAdapterCallContext) extends TopicTrait

case class InBoundGetAdapterInfo(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: InboundAdapterInfoInternal) extends InBoundTrait[InboundAdapterInfoInternal]


case class OutBoundGetBanks(outboundAdapterCallContext: OutboundAdapterCallContext) extends TopicTrait

case class InBoundGetBanks(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[BankCommons]) extends InBoundTrait[List[BankCommons]]


case class OutBoundGetBankAccountsHeld(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankIdAccountIds: List[BankIdAccountId]) extends TopicTrait

case class InBoundGetBankAccountsHeld(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[AccountHeld]) extends InBoundTrait[List[AccountHeld]]


case class OutBoundGetCounterparties(outboundAdapterCallContext: OutboundAdapterCallContext,
  thisBankId: BankId,
  thisAccountId: AccountId,
  viewId: ViewId) extends TopicTrait

case class InBoundGetCounterparties(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CounterpartyTraitCommons]) extends InBoundTrait[List[CounterpartyTraitCommons]]


case class OutBoundMakeHistoricalPayment(outboundAdapterCallContext: OutboundAdapterCallContext,
  fromAccount: BankAccount,
  toAccount: BankAccount,
  posted: Date,
  completed: Date,
  amount: BigDecimal,
  currency: String,
  description: String,
  transactionRequestType: String,
  chargePolicy: String
) extends TopicTrait

case class InBoundMakeHistoricalPayment(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionId) extends InBoundTrait[TransactionId]

case class OutBoundGetCardAttributesFromProvider(outboundAdapterCallContext: OutboundAdapterCallContext,
  cardId: String
) extends TopicTrait

case class InBoundGetCardAttributesFromProvider(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CardAttributeCommons]) extends InBoundTrait[List[CardAttributeCommons]]

case class OutBoundGetCardAttributeById(outboundAdapterCallContext: OutboundAdapterCallContext,
  cardAttributeId: String
) extends TopicTrait

case class InBoundGetCardAttributeById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CardAttributeCommons) extends InBoundTrait[CardAttributeCommons]

case class OutBoundCreateOrUpdateCardAttribute(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: Option[BankId],
  cardId: Option[String],
  cardAttributeId: Option[String],
  name: String,
  cardAttributeType: CardAttributeType,
  value: String
) extends TopicTrait

case class InBoundCreateOrUpdateCardAttribute(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CardAttributeCommons) extends InBoundTrait[CardAttributeCommons]

case class OutBoundGetAccountAttributesByAccount(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId
) extends TopicTrait

case class InBoundGetAccountAttributesByAccount(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[AccountAttributeCommons]) extends InBoundTrait[List[AccountAttributeCommons]]

case class OutBoundCreateAccountAttributes(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  productCode: ProductCode,
  accountAttributes: List[ProductAttribute],
  productInstanceCode: Option[String] = None,
) extends TopicTrait

case class InBoundCreateAccountAttributes(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[AccountAttributeCommons]) extends InBoundTrait[List[AccountAttributeCommons]]


case class OutBoundGetAccountAttributeById(outboundAdapterCallContext: OutboundAdapterCallContext,
  accountAttributeId: String
) extends TopicTrait

case class InBoundGetAccountAttributeById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AccountAttributeCommons) extends InBoundTrait[AccountAttributeCommons]

case class OutBoundDeleteProductAttribute(outboundAdapterCallContext: OutboundAdapterCallContext,
  productAttributeId: String
) extends TopicTrait

case class InBoundDeleteProductAttribute(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundDeleteUserAuthContexts(outboundAdapterCallContext: OutboundAdapterCallContext,
  userId: String
) extends TopicTrait

case class InBoundDeleteUserAuthContexts(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundDeleteUserAuthContextById(outboundAdapterCallContext: OutboundAdapterCallContext,
  userAuthContextId: String
) extends TopicTrait

case class InBoundDeleteUserAuthContextById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundDeleteCustomerAddress(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerAddressId: String) extends TopicTrait

case class InBoundDeleteCustomerAddress(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundCreateUserAuthContextUpdate(outboundAdapterCallContext: OutboundAdapterCallContext,
  userId: String,
  key: String,
  value: String) extends TopicTrait

case class InBoundCreateUserAuthContextUpdate(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: UserAuthContextUpdateCommons) extends InBoundTrait[UserAuthContextUpdateCommons]

case class OutBoundDeleteTaxResidence(outboundAdapterCallContext: OutboundAdapterCallContext,
  taxResourceId: String) extends TopicTrait

case class InBoundDeleteTaxResidence(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundUpdateCustomerGeneralData(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerId: String,
  legalName: Option[String],
  faceImage: Option[CustomerFaceImageTrait],
  dateOfBirth: Option[Date],
  relationshipStatus: Option[String],
  dependents: Option[Int],
  highestEducationAttained: Option[String],
  employmentStatus: Option[String],
  title: Option[String],
  branchId: Option[String],
  nameSuffix: Option[String]
) extends TopicTrait

case class InBoundUpdateCustomerGeneralData(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerCommons) extends InBoundTrait[CustomerCommons]

case class OutBoundUpdateCustomerCreditData(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerId: String,
  creditRating: Option[String],
  creditSource: Option[String],
  creditLimit: Option[AmountOfMoney]) extends TopicTrait

case class InBoundUpdateCustomerCreditData(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerCommons) extends InBoundTrait[CustomerCommons]

case class OutBoundUpdateCustomerScaData(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerId: String,
  mobileNumber: Option[String],
  email: Option[String],
  customerNumber: Option[String]) extends TopicTrait

case class InBoundUpdateCustomerScaData(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerCommons) extends InBoundTrait[CustomerCommons]

case class OutBoundCheckCustomerNumberAvailable(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  customerNumber: String) extends TopicTrait

case class InBoundCheckCustomerNumberAvailable(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundUpdateBankAccount(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  accountType: String,
  accountLabel: String,
  branchId: String,
  accountRoutings: List[AccountRouting]
) extends TopicTrait

case class InBoundUpdateBankAccount(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]

case class OutBoundUpdatePhysicalCard(outboundAdapterCallContext: OutboundAdapterCallContext,
  cardId: String,
  bankCardNumber: String,
  nameOnCard: String,
  cardType: String,
  issueNumber: String,
  serialNumber: String,
  validFrom: Date,
  expires: Date,
  enabled: Boolean,
  cancelled: Boolean,
  onHotList: Boolean,
  technology: String,
  networks: List[String],
  allows: List[String],
  accountId: String,
  bankId: String,
  replacement: Option[CardReplacementInfo],
  pinResets: List[PinResetInfo],
  collected: Option[CardCollectionInfo],
  posted: Option[CardPostedInfo],
  customerId: String
) extends TopicTrait

case class InBoundUpdatePhysicalCard(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: PhysicalCard) extends InBoundTrait[PhysicalCard]

case class OutBoundDeletePhysicalCardForBank(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  cardId: String) extends TopicTrait

case class InBoundDeletePhysicalCardForBank(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundGetPhysicalCardForBank(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  cardId: String) extends TopicTrait

case class InBoundGetPhysicalCardForBank(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: PhysicalCard) extends InBoundTrait[PhysicalCard]

case class OutBoundGetPhysicalCardsForBank(outboundAdapterCallContext: OutboundAdapterCallContext,
  bank: Bank,
  user: User,
  limit: Int,
  offset: Int,
  fromDate: String,
  toDate: String) extends TopicTrait

case class InBoundGetPhysicalCardsForBank(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[PhysicalCard]) extends InBoundTrait[List[PhysicalCard]]

//bankCardNumber, nameOnCard, cardType, issueNumber, serialNumber, validFrom, expires, enabled, cancelled, onHotList, technology, networks, allows, accountId, bankId, replacement, pinResets, collected, posted, customerId
case class OutBoundCreatePhysicalCard(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankCardNumber: String,
  nameOnCard: String,
  cardType: String,
  issueNumber: String,
  serialNumber: String,
  validFrom: Date,
  expires: Date,
  enabled: Boolean,
  cancelled: Boolean,
  onHotList: Boolean,
  technology: String,
  networks: List[String],
  allows: List[String],
  accountId: String,
  bankId: String,
  replacement: Option[CardReplacementInfo],
  pinResets: List[PinResetInfo],
  collected: Option[CardCollectionInfo],
  posted: Option[CardPostedInfo],
  customerId: String,
  cvv: String,
  brand: String
) extends TopicTrait

case class InBoundCreatePhysicalCard(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: PhysicalCard) extends InBoundTrait[PhysicalCard]


case class OutBoundGetTransactionRequestImpl(outboundAdapterCallContext: OutboundAdapterCallContext,
  transactionRequestId: TransactionRequestId) extends TopicTrait

case class InBoundGetTransactionRequestImpl(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundGetCustomerByCustomerId(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerId: String) extends TopicTrait

case class InBoundGetCustomerByCustomerId(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerCommons) extends InBoundTrait[CustomerCommons]

case class OutBoundCreateOrUpdateKycCheck(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: String,
  customerId: String,
  id: String,
  customerNumber: String,
  date: Date,
  how: String,
  staffUserId: String,
  mStaffName: String,
  mSatisfied: Boolean,
  comments: String) extends TopicTrait

case class InBoundCreateOrUpdateKycCheck(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: KycCheckCommons) extends InBoundTrait[KycCheckCommons]

case class OutBoundCreateOrUpdateKycDocument(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: String,
  customerId: String,
  id: String,
  customerNumber: String,
  `type`: String,
  number: String,
  issueDate: Date,
  issuePlace: String,
  expiryDate: Date) extends TopicTrait

case class InBoundCreateOrUpdateKycDocument(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: KycDocument) extends InBoundTrait[KycDocument]

case class OutBoundCreateOrUpdateKycMedia(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: String,
  customerId: String,
  id: String,
  customerNumber: String,
  `type`: String,
  url: String,
  date: Date,
  relatesToKycDocumentId: String,
  relatesToKycCheckId: String) extends TopicTrait

case class InBoundCreateOrUpdateKycMedia(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: KycMediaCommons) extends InBoundTrait[KycMediaCommons]

case class OutBoundCreateOrUpdateKycStatus(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: String,
  customerId: String,
  customerNumber: String,
  ok: Boolean,
  date: Date) extends TopicTrait

case class InBoundCreateOrUpdateKycStatus(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: KycStatusCommons) extends InBoundTrait[KycStatusCommons]

case class OutBoundGetKycChecks(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerId: String) extends TopicTrait

case class InBoundGetKycChecks(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[KycCheckCommons]) extends InBoundTrait[List[KycCheckCommons]]

case class OutBoundGetKycDocuments(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerId: String) extends TopicTrait

case class InBoundGetKycDocuments(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[KycDocumentCommons]) extends InBoundTrait[List[KycDocumentCommons]]

case class OutBoundGetKycMedias(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerId: String) extends TopicTrait

case class InBoundGetKycMedias(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[KycMediaCommons]) extends InBoundTrait[List[KycMediaCommons]]

case class OutBoundGetKycStatuses(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerId: String) extends TopicTrait

case class InBoundGetKycStatuses(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[KycStatusCommons]) extends InBoundTrait[List[KycStatusCommons]]


case class OutBoundCreateBankAccount(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  accountType: String,
  accountLabel: String,
  currency: String,
  initialBalance: BigDecimal,
  accountHolderName: String,
  branchId: String,
  accountRoutings: List[AccountRouting]) extends TopicTrait

case class InBoundCreateBankAccount(inboundAdapterCallContext: InboundAdapterCallContext,
  status: Status,
  data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]

case class OutBoundCreateMessage(outboundAdapterCallContext: OutboundAdapterCallContext,
  user: User,
  bankId: BankId,
  message: String,
  fromDepartment: String,
  fromPerson: String) extends TopicTrait

case class InBoundCreateMessage(inboundAdapterCallContext: InboundAdapterCallContext,
  status: Status,
  data: CustomerMessageCommons) extends InBoundTrait[CustomerMessageCommons]

case class OutBoundValidateChallengeAnswer(outboundAdapterCallContext: OutboundAdapterCallContext,
  challengeId: String,
  hashOfSuppliedAnswer: String) extends TopicTrait

case class InBoundValidateChallengeAnswer(inboundAdapterCallContext: InboundAdapterCallContext,
  status: Status,
  data: Boolean) extends InBoundTrait[Boolean]


case class OutBoundValidateChallengeAnswerV2(outboundAdapterCallContext: OutboundAdapterCallContext,
  challengeId: String,
  suppliedAnswer: String,
  suppliedAnswerType: SuppliedAnswerType) extends TopicTrait

case class InBoundValidateChallengeAnswerV2(inboundAdapterCallContext: InboundAdapterCallContext,
  status: Status,
  data: Boolean) extends InBoundTrait[Boolean]

//---------------------


case class OutBoundGetBankLegacy(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId) extends TopicTrait

case class InBoundGetBankLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankCommons) extends InBoundTrait[BankCommons]


case class OutBoundGetBanksLegacy(outboundAdapterCallContext: OutboundAdapterCallContext) extends TopicTrait

case class InBoundGetBanksLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[BankCommons]) extends InBoundTrait[List[BankCommons]]


case class OutBoundGetBankAccountsForUserLegacy(outboundAdapterCallContext: OutboundAdapterCallContext,
  provider: String, username: String) extends TopicTrait

case class InBoundGetBankAccountsForUserLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[InboundAccountCommons]) extends InBoundTrait[List[InboundAccountCommons]]


case class OutBoundGetBankAccountLegacy(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId) extends TopicTrait

case class InBoundGetBankAccountLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetBankAccountByRouting(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: Option[BankId],
  scheme: String,
  address: String) extends TopicTrait

case class InBoundGetBankAccountByRouting(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetCoreBankAccountsLegacy(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankIdAccountIds: List[BankIdAccountId]) extends TopicTrait

case class InBoundGetCoreBankAccountsLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CoreAccount]) extends InBoundTrait[List[CoreAccount]]


case class OutBoundGetBankAccountsHeldLegacy(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankIdAccountIds: List[BankIdAccountId]) extends TopicTrait

case class InBoundGetBankAccountsHeldLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[AccountHeld]) extends InBoundTrait[List[AccountHeld]]


case class OutBoundCheckBankAccountExistsLegacy(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId) extends TopicTrait

case class InBoundCheckBankAccountExistsLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetCounterpartyByCounterpartyIdLegacy(outboundAdapterCallContext: OutboundAdapterCallContext,
  counterpartyId: CounterpartyId) extends TopicTrait

case class InBoundGetCounterpartyByCounterpartyIdLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyTraitCommons) extends InBoundTrait[CounterpartyTraitCommons]


case class OutBoundGetCounterpartiesLegacy(outboundAdapterCallContext: OutboundAdapterCallContext,
  thisBankId: BankId,
  thisAccountId: AccountId,
  viewId: ViewId) extends TopicTrait

case class InBoundGetCounterpartiesLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CounterpartyTraitCommons]) extends InBoundTrait[List[CounterpartyTraitCommons]]


case class OutBoundGetTransactionsLegacy(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  limit: Int,
  offset: Int,
  fromDate: String,
  toDate: String) extends TopicTrait

case class InBoundGetTransactionsLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[Transaction]) extends InBoundTrait[List[Transaction]]


case class OutBoundGetTransactionLegacy(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  transactionId: TransactionId) extends TopicTrait

case class InBoundGetTransactionLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Transaction) extends InBoundTrait[Transaction]


case class OutBoundCreatePhysicalCardLegacy(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankCardNumber: String,
  nameOnCard: String,
  cardType: String,
  issueNumber: String,
  serialNumber: String,
  validFrom: Date,
  expires: Date,
  enabled: Boolean,
  cancelled: Boolean,
  onHotList: Boolean,
  technology: String,
  networks: List[String],
  allows: List[String],
  accountId: String,
  bankId: String,
  replacement: Option[CardReplacementInfo],
  pinResets: List[PinResetInfo],
  collected: Option[CardCollectionInfo],
  posted: Option[CardPostedInfo],
  customerId: String,
  cvv: String = "",
  brand: String = "") extends TopicTrait

case class InBoundCreatePhysicalCardLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: PhysicalCard) extends InBoundTrait[PhysicalCard]


case class OutBoundCreateBankAccountLegacy(bankId: BankId,
  accountId: AccountId,
  accountType: String,
  accountLabel: String,
  currency: String,
  initialBalance: BigDecimal,
  accountHolderName: String,
  branchId: String,
  accountRoutings: List[AccountRouting]) extends TopicTrait

case class InBoundCreateBankAccountLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetCustomerByCustomerIdLegacy(outboundAdapterCallContext: OutboundAdapterCallContext,
  customerId: String) extends TopicTrait

case class InBoundGetCustomerByCustomerIdLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerCommons) extends InBoundTrait[CustomerCommons]

case class OutBoundGetBankAccountByIban(outboundAdapterCallContext: OutboundAdapterCallContext,
  iban: String) extends TopicTrait

case class InBoundGetBankAccountByIban(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]

case class OutBoundGetBankAccounts(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankIdAccountIds: List[BankIdAccountId]) extends TopicTrait

case class InBoundGetBankAccounts(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[BankAccountCommons]) extends InBoundTrait[List[BankAccountCommons]]

case class OutBoundDynamicEntityProcess(outboundAdapterCallContext: OutboundAdapterCallContext,
  operation: DynamicEntityOperation,
  entityName: String,
  requestBody: Option[JObject],
  entityId: Option[String],
  bankId: Option[String],
  queryParameters: Option[Map[String, List[String]]],
  userId: Option[String],
  isPersonalEntity: Boolean) extends TopicTrait

case class InBoundDynamicEntityProcess(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: JValue) extends InBoundTrait[JValue]

// because swagger generate not support JValue type, so here supply too xxxDoc TO generate correct request and response body example
case class FooBar(name: String, number: Int, fooBarId: Option[String] = None)

case class OutBoundDynamicEntityProcessDoc(outboundAdapterCallContext: OutboundAdapterCallContext,
  operation: DynamicEntityOperation,
  entityName: String,
  requestBody: Option[FooBar],
  entityId: Option[String]) extends TopicTrait

case class InBoundDynamicEntityProcessDoc(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: FooBar) extends InBoundTrait[FooBar]


// --------------------- some special connector methods corresponding InBound and OutBound
case class OutBoundCreateChallenges(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, accountId: AccountId, userIds: List[String], transactionRequestType: TransactionRequestType, transactionRequestId: String, scaMethod: Option[StrongCustomerAuthentication.SCA]) extends TopicTrait

case class InBoundCreateChallenges(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[String]) extends InBoundTrait[List[String]]


case class OutBoundGetCounterpartyFromTransaction(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, accountId: AccountId, counterpartyId: String) extends TopicTrait

case class InBoundGetCounterpartyFromTransaction(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Counterparty) extends InBoundTrait[Counterparty]

case class OutBoundGetCounterpartiesFromTransaction(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, accountId: AccountId) extends TopicTrait

case class InBoundGetCounterpartiesFromTransaction(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[Counterparty]) extends InBoundTrait[List[Counterparty]]

case class OutBoundGetCounterparty(outboundAdapterCallContext: OutboundAdapterCallContext, thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String) extends TopicTrait

case class InBoundGetCounterparty(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Counterparty) extends InBoundTrait[Counterparty]

case class OutBoundGetPhysicalCardsForUser(outboundAdapterCallContext: OutboundAdapterCallContext, user: User) extends TopicTrait

case class InBoundGetPhysicalCardsForUser(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[PhysicalCard]) extends InBoundTrait[List[PhysicalCard]]


case class OutBoundCreateTransactionRequest(outboundAdapterCallContext: OutboundAdapterCallContext, initiator: User, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody) extends TopicTrait

case class InBoundCreateTransactionRequest(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]

case class OutBoundGetStatus(outboundAdapterCallContext: OutboundAdapterCallContext, challengeThresholdAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal, transactionRequestType: TransactionRequestType) extends TopicTrait

case class InBoundGetStatus(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, statusValue: String, data: TransactionRequestStatus.Value) extends InBoundTrait[TransactionRequestStatus.Value]


case class OutBoundGetChargeValue(outboundAdapterCallContext: OutboundAdapterCallContext, chargeLevelAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal) extends TopicTrait

case class InBoundGetChargeValue(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: String) extends InBoundTrait[String]

case class OutBoundCreateTransactionRequestv400(outboundAdapterCallContext: OutboundAdapterCallContext, initiator: User, viewId: ViewId, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType,
  transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
  detailsPlain: String,
  chargePolicy: String,
  challengeType: Option[String],
  scaMethod: Option[StrongCustomerAuthentication.SCA],
  reasons: Option[List[TransactionRequestReason]],
) extends TopicTrait

case class InBoundCreateTransactionRequestv400(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]

case class OutBoundCreateTransactionRequestSepaCreditTransfersBGV1(
  outboundAdapterCallContext: OutboundAdapterCallContext, 
  initiator: Option[User],
  paymentServiceType: PaymentServiceTypes.Value,
  transactionRequestType: TransactionRequestTypes.Value,
  transactionRequestBody: SepaCreditTransfersBerlinGroupV13,
) extends TopicTrait

case class InBoundCreateTransactionRequestSepaCreditTransfersBGV1(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequestBGV1) extends InBoundTrait[TransactionRequestBGV1]

case class OutBoundCreateTransactionRequestPeriodicSepaCreditTransfersBGV1(
  outboundAdapterCallContext: OutboundAdapterCallContext, initiator: Option[User],
  paymentServiceType: PaymentServiceTypes.Value,
  transactionRequestType: TransactionRequestTypes.Value,
  transactionRequestBody: PeriodicSepaCreditTransfersBerlinGroupV13,
) extends TopicTrait

case class InBoundCreateTransactionRequestPeriodicSepaCreditTransfersBGV1(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequestBGV1) extends InBoundTrait[TransactionRequestBGV1]

case class OutBoundCreateTransactionRequestImpl(outboundAdapterCallContext: OutboundAdapterCallContext, transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType, fromAccount: BankAccount, counterparty: BankAccount, body: TransactionRequestBody, status: String, charge: TransactionRequestCharge) extends TopicTrait

case class InBoundCreateTransactionRequestImpl(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]

case class OutBoundSaveTransactionRequestTransaction(outboundAdapterCallContext: OutboundAdapterCallContext, transactionRequestId: TransactionRequestId, transactionId: TransactionId) extends TopicTrait

case class InBoundSaveTransactionRequestTransaction(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundSaveTransactionRequestTransactionImpl(outboundAdapterCallContext: OutboundAdapterCallContext, transactionRequestId: TransactionRequestId, transactionId: TransactionId) extends TopicTrait

case class InBoundSaveTransactionRequestTransactionImpl(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundSaveTransactionRequestChallenge(outboundAdapterCallContext: OutboundAdapterCallContext, transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge) extends TopicTrait

case class InBoundSaveTransactionRequestChallenge(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundSaveTransactionRequestChallengeImpl(outboundAdapterCallContext: OutboundAdapterCallContext, transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge) extends TopicTrait

case class InBoundSaveTransactionRequestChallengeImpl(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundSaveTransactionRequestStatusImpl(outboundAdapterCallContext: OutboundAdapterCallContext, transactionRequestId: TransactionRequestId, status: String) extends TopicTrait

case class InBoundSaveTransactionRequestStatusImpl(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundGetTransactionRequests(outboundAdapterCallContext: OutboundAdapterCallContext, initiator: User, fromAccount: BankAccount) extends TopicTrait

case class InBoundGetTransactionRequests(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequest]) extends InBoundTrait[List[TransactionRequest]]

case class OutBoundGetTransactionRequestTypes(outboundAdapterCallContext: OutboundAdapterCallContext, initiator: User, fromAccount: BankAccount) extends TopicTrait

case class InBoundGetTransactionRequestTypes(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequestType]) extends InBoundTrait[List[TransactionRequestType]]

case class OutBoundCreateTransactionAfterChallenge(outboundAdapterCallContext: OutboundAdapterCallContext, initiator: User, transReqId: TransactionRequestId) extends TopicTrait

case class InBoundCreateTransactionAfterChallenge(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundCreateSandboxBankAccount(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, accountId: AccountId, accountNumber: String, accountType: String, accountLabel: String, currency: String, initialBalance: BigDecimal, accountHolderName: String, branchId: String, accountRoutings: List[AccountRouting]) extends TopicTrait

case class InBoundCreateSandboxBankAccount(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundUpdateAccountLabel(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, accountId: AccountId, label: String) extends TopicTrait

case class InBoundUpdateAccountLabel(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class GetProductsParam(name: String, value: List[String])

case class OutBoundGetProducts(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, params: List[GetProductsParam]) extends TopicTrait

case class InBoundGetProducts(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ProductCommons]) extends InBoundTrait[List[ProductCommons]]

case class OutBoundGetProduct(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, productCode: ProductCode) extends TopicTrait

case class InBoundGetProduct(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: ProductCommons) extends InBoundTrait[ProductCommons]

case class OutBoundGetCurrentFxRate(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String) extends TopicTrait

case class InBoundGetCurrentFxRate(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: FXRateCommons) extends InBoundTrait[FXRateCommons]

case class OutBoundCreateOrUpdateFXRate(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: String, fromCurrencyCode: String, toCurrencyCode: String, conversionValue: Double, inverseConversionValue: Double, effectiveDate: Date) extends TopicTrait

case class InBoundCreateOrUpdateFXRate(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: FXRateCommons) extends InBoundTrait[FXRateCommons]


case class OutBoundGetBranchLegacy(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, branchId: BranchId) extends TopicTrait

case class InBoundGetBranchLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BranchTCommons) extends InBoundTrait[BranchTCommons]

case class OutBoundGetAtmLegacy(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, atmId: AtmId) extends TopicTrait

case class InBoundGetAtmLegacy(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AtmTCommons) extends InBoundTrait[AtmTCommons]

case class OutBoundGetTransactionRequestTypeCharges(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestTypes: List[TransactionRequestType]) extends TopicTrait

case class InBoundGetTransactionRequestTypeCharges(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequestTypeChargeCommons]) extends InBoundTrait[List[TransactionRequestTypeChargeCommons]]

case class OutBoundGetCustomersByCustomerPhoneNumber(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, phoneNumber: String) extends TopicTrait

case class InBoundGetCustomersByCustomerPhoneNumber(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CustomerCommons]) extends InBoundTrait[List[CustomerCommons]]

case class OutBoundGetTransactionAttributeById(outboundAdapterCallContext: OutboundAdapterCallContext, transactionAttributeId: String) extends TopicTrait

case class InBoundGetTransactionAttributeById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionAttributeCommons) extends InBoundTrait[TransactionAttributeCommons]

case class OutBoundCreateOrUpdateCustomerAttribute(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, customerId: CustomerId, customerAttributeId: Option[String], name: String, attributeType: CustomerAttributeType.Value, value: String) extends TopicTrait

case class InBoundCreateOrUpdateCustomerAttribute(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerAttributeCommons) extends InBoundTrait[CustomerAttributeCommons]


case class OutBoundCreateOrUpdateTransactionAttribute(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, transactionId: TransactionId, transactionAttributeId: Option[String], name: String, attributeType: TransactionAttributeType.Value, value: String) extends TopicTrait

case class InBoundCreateOrUpdateTransactionAttribute(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionAttributeCommons) extends InBoundTrait[TransactionAttributeCommons]

case class OutBoundGetCustomerAttributes(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, customerId: CustomerId) extends TopicTrait

case class InBoundGetCustomerAttributes(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CustomerAttributeCommons]) extends InBoundTrait[List[CustomerAttributeCommons]]

case class OutBoundGetCustomerIdsByAttributeNameValues(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, nameValues: Map[String, List[String]]) extends TopicTrait

case class InBoundGetCustomerIdsByAttributeNameValues(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[String]) extends InBoundTrait[List[String]]

case class CustomerAndAttribute(customer: Customer, attributes: List[CustomerAttribute])

case class OutBoundGetCustomerAttributesForCustomers(outboundAdapterCallContext: OutboundAdapterCallContext, customers: List[Customer]) extends TopicTrait

case class InBoundGetCustomerAttributesForCustomers(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CustomerAndAttribute]) extends InBoundTrait[List[CustomerAndAttribute]]

case class OutBoundGetTransactionIdsByAttributeNameValues(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, nameValues: Map[String, List[String]]) extends TopicTrait

case class InBoundGetTransactionIdsByAttributeNameValues(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[String]) extends InBoundTrait[List[String]]

case class OutBoundGetTransactionAttributes(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, transactionId: TransactionId) extends TopicTrait

case class InBoundGetTransactionAttributes(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionAttributeCommons]) extends InBoundTrait[List[TransactionAttributeCommons]]

case class OutBoundGetBankAttributesByBank(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId) extends TopicTrait

case class InBoundGetBankAttributesByBank(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[BankAttributeTraitCommons]) extends InBoundTrait[List[BankAttributeTraitCommons]]

case class OutBoundGetCustomerAttributeById(outboundAdapterCallContext: OutboundAdapterCallContext, customerAttributeId: String) extends TopicTrait

case class InBoundGetCustomerAttributeById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerAttributeCommons) extends InBoundTrait[CustomerAttributeCommons]

case class OutBoundCreateDirectDebit(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: String, accountId: String, customerId: String, userId: String, counterpartyId: String, dateSigned: Date, dateStarts: Date, dateExpires: Option[Date]) extends TopicTrait

case class InBoundCreateDirectDebit(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: DirectDebitTraitCommons) extends InBoundTrait[DirectDebitTraitCommons]

case class OutBoundDeleteCustomerAttribute(outboundAdapterCallContext: OutboundAdapterCallContext, customerAttributeId: String) extends TopicTrait

case class InBoundDeleteCustomerAttribute(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundCheckExternalUserCredentials(outboundAdapterCallContext: OutboundAdapterCallContext, username: String, password: String) extends TopicTrait

case class InBoundCheckExternalUserCredentials(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: InboundExternalUser) extends InBoundTrait[InboundExternalUser]

case class OutBoundCheckExternalUserExists(outboundAdapterCallContext: OutboundAdapterCallContext, username: String) extends TopicTrait

case class InBoundCheckExternalUserExists(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: InboundExternalUser) extends InBoundTrait[InboundExternalUser]

case class OutBoundCreateChallengesC2(
  outboundAdapterCallContext: OutboundAdapterCallContext,
  userIds: List[String],
  challengeType: ChallengeType.Value,
  transactionRequestId: Option[String],
  scaMethod: Option[SCA],
  scaStatus: Option[SCAStatus],
  consentId: Option[String],
  authenticationMethodId: Option[String]) extends TopicTrait

case class OutBoundCreateChallengesC3(
  outboundAdapterCallContext: OutboundAdapterCallContext,
  userIds: List[String],
  challengeType: ChallengeType.Value,
  transactionRequestId: Option[String],
  scaMethod: Option[SCA],
  scaStatus: Option[SCAStatus],
  consentId: Option[String],
  basketId: Option[String],
  authenticationMethodId: Option[String]) extends TopicTrait

case class InBoundCreateChallengesC2(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ChallengeCommons]) extends InBoundTrait[List[ChallengeCommons]]

case class InBoundCreateChallengesC3(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ChallengeCommons]) extends InBoundTrait[List[ChallengeCommons]]

case class OutBoundValidateChallengeAnswerC2(
  outboundAdapterCallContext: OutboundAdapterCallContext,
  transactionRequestId: Option[String],
  consentId: Option[String],
  challengeId: String,
  hashOfSuppliedAnswer: String
) extends TopicTrait

case class OutBoundValidateChallengeAnswerC3(
  outboundAdapterCallContext: OutboundAdapterCallContext,
  transactionRequestId: Option[String],
  consentId: Option[String],
  basketId: Option[String],
  challengeId: String,
  hashOfSuppliedAnswer: String
) extends TopicTrait

case class OutBoundValidateChallengeAnswerC4(
  outboundAdapterCallContext: OutboundAdapterCallContext,
  transactionRequestId: Option[String],
  consentId: Option[String],
  challengeId: String,
  suppliedAnswer: String,
  suppliedAnswerType: SuppliedAnswerType
) extends TopicTrait

case class OutBoundValidateChallengeAnswerC5(
  outboundAdapterCallContext: OutboundAdapterCallContext,
  transactionRequestId: Option[String],
  consentId: Option[String],
  basketId: Option[String],
  challengeId: String,
  suppliedAnswer: String,
  suppliedAnswerType: SuppliedAnswerType,
) extends TopicTrait

case class InBoundValidateChallengeAnswerC4(
  inboundAdapterCallContext: InboundAdapterCallContext,
  status: Status,
  data: ChallengeCommons
) extends InBoundTrait[ChallengeCommons]

case class InBoundValidateChallengeAnswerC5(
  inboundAdapterCallContext: InboundAdapterCallContext,
  status: Status,
  data: ChallengeCommons
) extends InBoundTrait[ChallengeCommons]

case class InBoundValidateChallengeAnswerC2(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: ChallengeCommons) extends InBoundTrait[ChallengeCommons]

case class InBoundValidateChallengeAnswerC3(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: ChallengeCommons) extends InBoundTrait[ChallengeCommons]

case class OutBoundValidateAndCheckIbanNumber(
  outboundAdapterCallContext: OutboundAdapterCallContext,
  iban: String
) extends TopicTrait

case class InBoundValidateAndCheckIbanNumber(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: IbanChecker) extends InBoundTrait[IbanChecker]


case class OutBoundGetChallenge(outboundAdapterCallContext: OutboundAdapterCallContext, challengeId: String) extends TopicTrait

case class InBoundGetChallenge(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: ChallengeCommons) extends InBoundTrait[ChallengeCommons]

case class OutBoundGetChallengesByTransactionRequestId(outboundAdapterCallContext: OutboundAdapterCallContext, transactionRequestId: String) extends TopicTrait

case class InBoundGetChallengesByTransactionRequestId(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ChallengeCommons]) extends InBoundTrait[List[ChallengeCommons]]

case class OutBoundGetChallengesByConsentId(outboundAdapterCallContext: OutboundAdapterCallContext, consentId: String) extends TopicTrait

case class InBoundGetChallengesByConsentId(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ChallengeCommons]) extends InBoundTrait[List[ChallengeCommons]]

case class OutBoundGetChallengesByBasketId(outboundAdapterCallContext: OutboundAdapterCallContext, basketId: String) extends TopicTrait

case class InBoundGetChallengesByBasketId(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ChallengeCommons]) extends InBoundTrait[List[ChallengeCommons]]

case class OutBoundGetCounterpartyByIbanAndBankAccountId(outboundAdapterCallContext: OutboundAdapterCallContext, iban: String, bankId: BankId, accountId: AccountId) extends TopicTrait

case class InBoundGetCounterpartyByIbanAndBankAccountId(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyTraitCommons) extends InBoundTrait[CounterpartyTraitCommons]
  
case class OutBoundGetPaymentLimit(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: String,
  accountId: String,
  viewId: String,
  transactionRequestType: String,
  currency: String,
  userId: String,
  username: String) extends TopicTrait

case class InBoundGetPaymentLimit(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AmountOfMoney) extends InBoundTrait[AmountOfMoney]


case class OutBoundAllChallengesSuccessfullyAnswered(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  transReqId: TransactionRequestId) extends TopicTrait

case class InBoundAllChallengesSuccessfullyAnswered(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundGetAccountRoutingsByScheme(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: Option[BankId],
  scheme: String) extends TopicTrait

case class InBoundGetBankSettlementAccounts(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[BankAccountCommons]) extends InBoundTrait[List[BankAccountCommons]]


case class OutBoundGetAccountsHeld(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  user: User) extends TopicTrait

case class InBoundGetAccountsHeld(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[BankIdAccountId]) extends InBoundTrait[List[BankIdAccountId]]


case class OutBoundGetAccountsHeldByUser(outboundAdapterCallContext: OutboundAdapterCallContext,
  user: User) extends TopicTrait

case class InBoundGetAccountsHeldByUser(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[BankIdAccountId]) extends InBoundTrait[List[BankIdAccountId]]

case class OutBoundGetBankAccountFromCounterparty(outboundAdapterCallContext: OutboundAdapterCallContext,
  counterparty: CounterpartyTrait,
  isOutgoingAccount: Boolean) extends TopicTrait

case class InBoundGetBankAccountFromCounterparty(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetBankAccountByNumber(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: Option[BankId],
  accountNumber: String) extends TopicTrait

case class InBoundGetBankAccountByNumber(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetBankAccountByRoutings(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankAccountRoutings: BankAccountRoutings) extends TopicTrait

case class InBoundGetBankAccountByRoutings(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundDeleteCounterpartyByCounterpartyId(outboundAdapterCallContext: OutboundAdapterCallContext,
  counterpartyId: CounterpartyId) extends TopicTrait

case class InBoundDeleteCounterpartyByCounterpartyId(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]


case class OutBoundGetOrCreateCounterparty(outboundAdapterCallContext: OutboundAdapterCallContext,
  name: String,
  description: String,
  currency: String,
  createdByUserId: String,
  thisBankId: String,
  thisAccountId: String,
  thisViewId: String,
  other_bank_routing_scheme: String,
  other_bank_routing_address: String,
  other_branch_routing_scheme: String,
  other_branch_routing_address: String,
  other_account_routing_scheme: String,
  other_account_routing_address: String,
  other_account_secondary_routing_scheme: String,
  other_account_secondary_routing_address: String) extends TopicTrait

case class InBoundGetOrCreateCounterparty(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyTraitCommons) extends InBoundTrait[CounterpartyTraitCommons]


case class OutBoundGetCounterpartyByRoutings(outboundAdapterCallContext: OutboundAdapterCallContext,
  otherBankRoutingScheme: String,
  otherBankRoutingAddress: String,
  otherBranchRoutingScheme: String,
  otherBranchRoutingAddress: String,
  otherAccountRoutingScheme: String,
  otherAccountRoutingAddress: String,
  otherAccountSecondaryRoutingScheme: String,
  otherAccountSecondaryRoutingAddress: String) extends TopicTrait

case class InBoundGetCounterpartyByRoutings(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyTraitCommons) extends InBoundTrait[CounterpartyTraitCommons]


case class OutBoundGetSumOfTransactionsFromAccountToCounterparty(outboundAdapterCallContext: OutboundAdapterCallContext,
  fromBankId: BankId,
  fromAccountId: AccountId,
  counterpartyId: CounterpartyId,
  fromDate: Date,
  toDate: Date) extends TopicTrait

case class InBoundGetSumOfTransactionsFromAccountToCounterparty(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AmountOfMoney) extends InBoundTrait[AmountOfMoney]

case class OutBoundGetPhysicalCardByCardNumber(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankCardNumber: String) extends TopicTrait

case class InBoundGetPhysicalCardByCardNumber(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: PhysicalCardTraitCommons) extends InBoundTrait[PhysicalCardTraitCommons]


case class OutBoundSaveDoubleEntryBookTransaction(outboundAdapterCallContext: OutboundAdapterCallContext,
  doubleEntryTransaction: DoubleEntryTransaction) extends TopicTrait

case class InBoundSaveDoubleEntryBookTransaction(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: DoubleEntryTransaction) extends InBoundTrait[DoubleEntryTransaction]


case class OutBoundGetDoubleEntryBookTransaction(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  accountId: AccountId,
  transactionId: TransactionId) extends TopicTrait

case class InBoundGetDoubleEntryBookTransaction(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: DoubleEntryTransaction) extends InBoundTrait[DoubleEntryTransaction]


case class OutBoundGetBalancingTransaction(outboundAdapterCallContext: OutboundAdapterCallContext,
  transactionId: TransactionId) extends TopicTrait

case class InBoundGetBalancingTransaction(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: DoubleEntryTransaction) extends InBoundTrait[DoubleEntryTransaction]


case class OutBoundGetProductTree(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  productCode: ProductCode) extends TopicTrait

case class InBoundGetProductTree(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ProductCommons]) extends InBoundTrait[List[ProductCommons]]


case class OutBoundCreateOrUpdateBranch(outboundAdapterCallContext: OutboundAdapterCallContext,
  branch: BranchT) extends TopicTrait

case class InBoundCreateOrUpdateBranch(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BranchTCommons) extends InBoundTrait[BranchTCommons]


case class OutBoundCreateOrUpdateAtm(outboundAdapterCallContext: OutboundAdapterCallContext,
  atm: AtmT) extends TopicTrait

case class InBoundCreateOrUpdateAtm(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AtmTCommons) extends InBoundTrait[AtmTCommons]


case class OutBoundDeleteAtm(outboundAdapterCallContext: OutboundAdapterCallContext,
  atm: AtmT) extends TopicTrait

case class InBoundDeleteAtm(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]


case class OutBoundCreateSystemLevelEndpointTag(outboundAdapterCallContext: OutboundAdapterCallContext,
  operationId: String,
  tagName: String) extends TopicTrait

case class InBoundCreateSystemLevelEndpointTag(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: EndpointTagTCommons) extends InBoundTrait[EndpointTagTCommons]


case class OutBoundUpdateSystemLevelEndpointTag(outboundAdapterCallContext: OutboundAdapterCallContext,
  endpointTagId: String,
  operationId: String,
  tagName: String) extends TopicTrait

case class InBoundUpdateSystemLevelEndpointTag(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: EndpointTagTCommons) extends InBoundTrait[EndpointTagTCommons]


case class OutBoundCreateBankLevelEndpointTag(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: String,
  operationId: String,
  tagName: String) extends TopicTrait

case class InBoundCreateBankLevelEndpointTag(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: EndpointTagTCommons) extends InBoundTrait[EndpointTagTCommons]


case class OutBoundUpdateBankLevelEndpointTag(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: String,
  endpointTagId: String,
  operationId: String,
  tagName: String) extends TopicTrait

case class InBoundUpdateBankLevelEndpointTag(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: EndpointTagTCommons) extends InBoundTrait[EndpointTagTCommons]


case class OutBoundGetSystemLevelEndpointTag(outboundAdapterCallContext: OutboundAdapterCallContext,
  operationId: String,
  tagName: String) extends TopicTrait

case class InBoundGetSystemLevelEndpointTag(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: EndpointTagTCommons) extends InBoundTrait[EndpointTagTCommons]


case class OutBoundGetBankLevelEndpointTag(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: String,
  operationId: String,
  tagName: String) extends TopicTrait

case class InBoundGetBankLevelEndpointTag(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: EndpointTagTCommons) extends InBoundTrait[EndpointTagTCommons]


case class OutBoundGetEndpointTagById(outboundAdapterCallContext: OutboundAdapterCallContext,
  endpointTagId: String) extends TopicTrait

case class InBoundGetEndpointTagById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: EndpointTagTCommons) extends InBoundTrait[EndpointTagTCommons]


case class OutBoundDeleteEndpointTag(outboundAdapterCallContext: OutboundAdapterCallContext,
  endpointTagId: String) extends TopicTrait

case class InBoundDeleteEndpointTag(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]


case class OutBoundGetSystemLevelEndpointTags(outboundAdapterCallContext: OutboundAdapterCallContext,
  operationId: String) extends TopicTrait

case class InBoundGetSystemLevelEndpointTags(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[EndpointTagTCommons]) extends InBoundTrait[List[EndpointTagTCommons]]


case class OutBoundGetBankLevelEndpointTags(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: String,
  operationId: String) extends TopicTrait

case class InBoundGetBankLevelEndpointTags(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[EndpointTagTCommons]) extends InBoundTrait[List[EndpointTagTCommons]]


case class OutBoundCreateOrUpdateProductFee(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  productCode: ProductCode,
  productFeeId: Option[String],
  name: String,
  isActive: Boolean,
  moreInfo: String,
  currency: String,
  amount: BigDecimal,
  frequency: String,
  `type`: String) extends TopicTrait

case class InBoundCreateOrUpdateProductFee(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: ProductFeeTraitCommons) extends InBoundTrait[ProductFeeTraitCommons]


case class OutBoundGetProductFeesFromProvider(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  productCode: ProductCode) extends TopicTrait

case class InBoundGetProductFeesFromProvider(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ProductFeeTraitCommons]) extends InBoundTrait[List[ProductFeeTraitCommons]]


case class OutBoundGetProductFeeById(outboundAdapterCallContext: OutboundAdapterCallContext,
  productFeeId: String) extends TopicTrait

case class InBoundGetProductFeeById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: ProductFeeTraitCommons) extends InBoundTrait[ProductFeeTraitCommons]


case class OutBoundDeleteProductFee(outboundAdapterCallContext: OutboundAdapterCallContext,
  productFeeId: String) extends TopicTrait

case class InBoundDeleteProductFee(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]


case class OutBoundUpdateAtmSupportedLanguages(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  atmId: AtmId,
  supportedLanguages: List[String]) extends TopicTrait

case class InBoundUpdateAtmSupportedLanguages(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AtmTCommons) extends InBoundTrait[AtmTCommons]


case class OutBoundUpdateAtmSupportedCurrencies(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  atmId: AtmId,
  supportedCurrencies: List[String]) extends TopicTrait

case class InBoundUpdateAtmSupportedCurrencies(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AtmTCommons) extends InBoundTrait[AtmTCommons]


case class OutBoundUpdateAtmAccessibilityFeatures(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  atmId: AtmId,
  accessibilityFeatures: List[String]) extends TopicTrait

case class InBoundUpdateAtmAccessibilityFeatures(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AtmTCommons) extends InBoundTrait[AtmTCommons]


case class OutBoundUpdateAtmServices(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  atmId: AtmId,
  supportedCurrencies: List[String]) extends TopicTrait

case class InBoundUpdateAtmServices(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AtmTCommons) extends InBoundTrait[AtmTCommons]


case class OutBoundUpdateAtmNotes(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  atmId: AtmId,
  notes: List[String]) extends TopicTrait

case class InBoundUpdateAtmNotes(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AtmTCommons) extends InBoundTrait[AtmTCommons]


case class OutBoundUpdateAtmLocationCategories(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  atmId: AtmId,
  locationCategories: List[String]) extends TopicTrait

case class InBoundUpdateAtmLocationCategories(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AtmTCommons) extends InBoundTrait[AtmTCommons]



case class OutBoundCheckCounterpartyExists(outboundAdapterCallContext: OutboundAdapterCallContext,
  name: String,
  thisBankId: String,
  thisAccountId: String,
  thisViewId: String) extends TopicTrait

case class InBoundCheckCounterpartyExists(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyTraitCommons) extends InBoundTrait[CounterpartyTraitCommons]


case class OutBoundCheckAgentNumberAvailable(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  agentNumber: String) extends TopicTrait

case class InBoundCheckAgentNumberAvailable(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]


case class OutBoundCreateCustomerC2(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  legalName: String,
  customerNumber: String,
  mobileNumber: String,
  email: String,
  faceImage: CustomerFaceImageTrait,
  dateOfBirth: Date,
  relationshipStatus: String,
  dependents: Int,
  dobOfDependents: List[Date],
  highestEducationAttained: String,
  employmentStatus: String,
  kycStatus: Boolean,
  lastOkDate: Date,
  creditRating: Option[CreditRatingTrait],
  creditLimit: Option[AmountOfMoneyTrait],
  title: String,
  branchId: String,
  nameSuffix: String) extends TopicTrait

case class InBoundCreateCustomerC2(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerCommons) extends InBoundTrait[CustomerCommons]


case class OutBoundGetAgentByAgentId(outboundAdapterCallContext: OutboundAdapterCallContext,
  agentId: String) extends TopicTrait

case class InBoundGetAgentByAgentId(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AgentCommons) extends InBoundTrait[AgentCommons]


case class OutBoundGetAgentByAgentNumber(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  agentNumber: String) extends TopicTrait

case class InBoundGetAgentByAgentNumber(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AgentCommons) extends InBoundTrait[AgentCommons]


case class OutBoundUpdateAgentStatus(outboundAdapterCallContext: OutboundAdapterCallContext,
  agentId: String,
  isPendingAgent: Boolean,
  isConfirmedAgent: Boolean) extends TopicTrait

case class InBoundUpdateAgentStatus(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AgentCommons) extends InBoundTrait[AgentCommons]


case class OutBoundCreateAgent(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: String,
  legalName: String,
  mobileNumber: String,
  agentNumber: String) extends TopicTrait

case class InBoundCreateAgent(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AgentCommons) extends InBoundTrait[AgentCommons]

case class OutBoundGetCustomersByCustomerLegalName(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  legalName: String) extends TopicTrait

case class InBoundGetCustomersByCustomerLegalName(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CustomerCommons]) extends InBoundTrait[List[CustomerCommons]]

case class OutBoundDeleteBankAttribute(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankAttributeId: String) extends TopicTrait

case class InBoundDeleteBankAttribute(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]


case class OutBoundDeleteAtmAttribute(outboundAdapterCallContext: OutboundAdapterCallContext,
  atmAttributeId: String) extends TopicTrait

case class InBoundDeleteAtmAttribute(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundDeleteAtmAttributesByAtmId(outboundAdapterCallContext: OutboundAdapterCallContext,
  atmId: AtmId) extends TopicTrait

case class InBoundDeleteAtmAttributesByAtmId(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]


case class OutBoundCreateOrUpdateAttributeDefinition(outboundAdapterCallContext: OutboundAdapterCallContext,
  bankId: BankId,
  name: String,
  category: AttributeCategory.Value,
  `type`: AttributeType.Value,
  description: String,
  alias: String,
  canBeSeenOnViews: List[String],
  isActive: Boolean
  ) extends TopicTrait


  case class InBoundDeleteUserAttribute(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]


  case class OutBoundGetAccountAttributesByAccountCanBeSeenOnView(outboundAdapterCallContext: OutboundAdapterCallContext,
    bankId: BankId,
    accountId: AccountId,
    viewId: ViewId) extends TopicTrait

  case class InBoundGetAccountAttributesByAccountCanBeSeenOnView(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[AccountAttributeCommons]) extends InBoundTrait[List[AccountAttributeCommons]]


  case class OutBoundGetAccountAttributesByAccountsCanBeSeenOnView(outboundAdapterCallContext: OutboundAdapterCallContext,
    accounts: List[BankIdAccountId],
    viewId: ViewId) extends TopicTrait

  case class InBoundGetAccountAttributesByAccountsCanBeSeenOnView(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[AccountAttributeCommons]) extends InBoundTrait[List[AccountAttributeCommons]]


  case class OutBoundGetTransactionAttributesByTransactionsCanBeSeenOnView(outboundAdapterCallContext: OutboundAdapterCallContext,
    bankId: BankId,
    transactionIds: List[TransactionId],
    viewId: ViewId) extends TopicTrait

  case class InBoundGetTransactionAttributesByTransactionsCanBeSeenOnView(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionAttributeCommons]) extends InBoundTrait[List[TransactionAttributeCommons]]


  case class OutBoundGetTransactionAttributesCanBeSeenOnView(outboundAdapterCallContext: OutboundAdapterCallContext,
    bankId: BankId,
    transactionId: TransactionId,
    viewId: ViewId) extends TopicTrait

  case class InBoundGetTransactionAttributesCanBeSeenOnView(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionAttributeCommons]) extends InBoundTrait[List[TransactionAttributeCommons]]


  case class OutBoundGetTransactionRequestAttributesFromProvider(outboundAdapterCallContext: OutboundAdapterCallContext,
    transactionRequestId: TransactionRequestId) extends TopicTrait

  case class InBoundGetTransactionRequestAttributesFromProvider(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequestAttributeTraitCommons]) extends InBoundTrait[List[TransactionRequestAttributeTraitCommons]]


  case class OutBoundGetTransactionRequestAttributes(outboundAdapterCallContext: OutboundAdapterCallContext,
    bankId: BankId,
    transactionRequestId: TransactionRequestId) extends TopicTrait

  case class InBoundGetTransactionRequestAttributes(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequestAttributeTraitCommons]) extends InBoundTrait[List[TransactionRequestAttributeTraitCommons]]


  case class OutBoundGetTransactionRequestAttributesCanBeSeenOnView(outboundAdapterCallContext: OutboundAdapterCallContext,
    bankId: BankId,
    transactionRequestId: TransactionRequestId,
    viewId: ViewId) extends TopicTrait

  case class InBoundGetTransactionRequestAttributesCanBeSeenOnView(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequestAttributeTraitCommons]) extends InBoundTrait[List[TransactionRequestAttributeTraitCommons]]


  case class OutBoundGetTransactionRequestAttributeById(outboundAdapterCallContext: OutboundAdapterCallContext,
    transactionRequestAttributeId: String) extends TopicTrait

  case class InBoundGetTransactionRequestAttributeById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequestAttributeTraitCommons) extends InBoundTrait[TransactionRequestAttributeTraitCommons]


  case class OutBoundGetByAttributeNameValues(outboundAdapterCallContext: OutboundAdapterCallContext,
    bankId: BankId,
    params: Map[String,
      List[String]],
    isPersonal: Boolean) extends TopicTrait

  case class InBoundGetByAttributeNameValues(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequestAttributeTraitCommons]) extends InBoundTrait[List[TransactionRequestAttributeTraitCommons]]

  case class OutBoundCreateOrUpdateTransactionRequestAttribute(outboundAdapterCallContext: OutboundAdapterCallContext,
    bankId: BankId,
    transactionRequestId: TransactionRequestId,
    transactionRequestAttributeId: Option[String],
    name: String,
    attributeType: TransactionRequestAttributeType.Value,
    value: String) extends TopicTrait

  case class InBoundCreateOrUpdateTransactionRequestAttribute(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequestAttributeTraitCommons) extends InBoundTrait[TransactionRequestAttributeTraitCommons]


  case class OutBoundCreateTransactionRequestAttributes(outboundAdapterCallContext: OutboundAdapterCallContext,
    bankId: BankId,
    transactionRequestId: TransactionRequestId,
    transactionRequestAttributes: List[TransactionRequestAttributeJsonV400],
    isPersonal: Boolean) extends TopicTrait

  case class InBoundCreateTransactionRequestAttributes(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequestAttributeTraitCommons]) extends InBoundTrait[List[TransactionRequestAttributeTraitCommons]]


  case class OutBoundDeleteTransactionRequestAttribute(outboundAdapterCallContext: OutboundAdapterCallContext,
    transactionRequestAttributeId: String) extends TopicTrait

  case class InBoundDeleteTransactionRequestAttribute(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]


  case class OutBoundCreateCustomerMessage(outboundAdapterCallContext: OutboundAdapterCallContext,
    customer: Customer,
    bankId: BankId,
    transport: String,
    message: String,
    fromDepartment: String,
    fromPerson: String) extends TopicTrait

  case class InBoundCreateCustomerMessage(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerMessageCommons) extends InBoundTrait[CustomerMessageCommons]


  case class OutBoundGetCustomerMessages(outboundAdapterCallContext: OutboundAdapterCallContext,
    customer: Customer,
    bankId: BankId) extends TopicTrait

  case class InBoundGetCustomerMessages(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CustomerMessageCommons]) extends InBoundTrait[List[CustomerMessageCommons]]


  case class OutBoundCreateStandingOrder(outboundAdapterCallContext: OutboundAdapterCallContext,
    bankId: String,
    accountId: String,
    customerId: String,
    userId: String,
    counterpartyId: String,
    amountValue: BigDecimal,
    amountCurrency: String,
    whenFrequency: String,
    whenDetail: String,
    dateSigned: Date,
    dateStarts: Date,
    dateExpires: Option[Date]) extends TopicTrait

  case class InBoundCreateStandingOrder(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: StandingOrderTraitCommons) extends InBoundTrait[StandingOrderTraitCommons]


  case class OutBoundValidateUserAuthContextUpdateRequest(outboundAdapterCallContext: OutboundAdapterCallContext,
    bankId: String,
    userId: String,
    key: String,
    value: String,
    scaMethod: String) extends TopicTrait

  case class InBoundValidateUserAuthContextUpdateRequest(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: UserAuthContextUpdateCommons) extends InBoundTrait[UserAuthContextUpdateCommons]


  case class OutBoundCheckAnswer(outboundAdapterCallContext: OutboundAdapterCallContext,
    authContextUpdateId: String,
    challenge: String) extends TopicTrait

  case class InBoundCheckAnswer(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: UserAuthContextUpdateCommons) extends InBoundTrait[UserAuthContextUpdateCommons]


  case class OutBoundGetCustomerAccountLink(outboundAdapterCallContext: OutboundAdapterCallContext,
    customerId: String,
    accountId: String) extends TopicTrait

  case class InBoundGetCustomerAccountLink(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerAccountLinkTraitCommons) extends InBoundTrait[CustomerAccountLinkTraitCommons]


  case class OutBoundGetCustomerAccountLinksByCustomerId(outboundAdapterCallContext: OutboundAdapterCallContext,
    customerId: String) extends TopicTrait

  case class InBoundGetCustomerAccountLinksByCustomerId(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CustomerAccountLinkTraitCommons]) extends InBoundTrait[List[CustomerAccountLinkTraitCommons]]


  case class OutBoundGetAgentAccountLinksByAgentId(outboundAdapterCallContext: OutboundAdapterCallContext,
    agnetId: String) extends TopicTrait

  case class InBoundGetAgentAccountLinksByAgentId(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CustomerAccountLinkTraitCommons]) extends InBoundTrait[List[CustomerAccountLinkTraitCommons]]


  case class OutBoundGetCustomerAccountLinksByBankIdAccountId(outboundAdapterCallContext: OutboundAdapterCallContext,
    bankId: String,
    accountId: String) extends TopicTrait

  case class InBoundGetCustomerAccountLinksByBankIdAccountId(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CustomerAccountLinkTraitCommons]) extends InBoundTrait[List[CustomerAccountLinkTraitCommons]]


  case class OutBoundGetCustomerAccountLinkById(outboundAdapterCallContext: OutboundAdapterCallContext,
    customerAccountLinkId: String) extends TopicTrait

  case class InBoundGetCustomerAccountLinkById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerAccountLinkTraitCommons) extends InBoundTrait[CustomerAccountLinkTraitCommons]


  case class OutBoundDeleteCustomerAccountLinkById(outboundAdapterCallContext: OutboundAdapterCallContext,
    customerAccountLinkId: String) extends TopicTrait

  case class InBoundDeleteCustomerAccountLinkById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]


  case class OutBoundCreateCustomerAccountLink(outboundAdapterCallContext: OutboundAdapterCallContext,
    customerId: String,
    bankId: String,
    accountId: String,
    relationshipType: String) extends TopicTrait

  case class InBoundCreateCustomerAccountLink(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerAccountLinkTraitCommons) extends InBoundTrait[CustomerAccountLinkTraitCommons]


  case class OutBoundCreateAgentAccountLink(outboundAdapterCallContext: OutboundAdapterCallContext,
    agentId: String,
    bankId: String,
    accountId: String) extends TopicTrait

  case class InBoundCreateAgentAccountLink(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AgentAccountLinkTraitCommons) extends InBoundTrait[AgentAccountLinkTraitCommons]


  case class OutBoundUpdateCustomerAccountLinkById(outboundAdapterCallContext: OutboundAdapterCallContext,
    customerAccountLinkId: String,
    relationshipType: String) extends TopicTrait

  case class InBoundUpdateCustomerAccountLinkById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerAccountLinkTraitCommons) extends InBoundTrait[CustomerAccountLinkTraitCommons]


  case class OutBoundGetConsentImplicitSCA(outboundAdapterCallContext: OutboundAdapterCallContext,
    user: User) extends TopicTrait

  case class InBoundGetConsentImplicitSCA(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: ConsentImplicitSCATCommons) extends InBoundTrait[ConsentImplicitSCATCommons]


  case class OutBoundCreateOrUpdateCounterpartyLimit(outboundAdapterCallContext: OutboundAdapterCallContext,
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
    maxNumberOfTransactions: Int) extends TopicTrait

  case class InBoundCreateOrUpdateCounterpartyLimit(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyLimitTraitCommons) extends InBoundTrait[CounterpartyLimitTraitCommons]


  case class OutBoundGetCounterpartyLimit(outboundAdapterCallContext: OutboundAdapterCallContext,
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String) extends TopicTrait

  case class InBoundGetCounterpartyLimit(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyLimitTraitCommons) extends InBoundTrait[CounterpartyLimitTraitCommons]


  case class OutBoundDeleteCounterpartyLimit(outboundAdapterCallContext: OutboundAdapterCallContext,
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String) extends TopicTrait

  case class InBoundDeleteCounterpartyLimit(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]




// --------------------- some special connector methods corresponding InBound and OutBound -- end --

//---------------- dynamic start -------------------please don't modify this line

//---------------- dynamic end ---------------------please don't modify this line
