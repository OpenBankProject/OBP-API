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

package com.openbankproject.commons.dto

import java.util.Date

import com.openbankproject.commons.model.enums.{CardAttributeType, CustomerAttributeType, DynamicEntityOperation, StrongCustomerAuthentication, TransactionAttributeType, TransactionRequestStatus}
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.{enums, _}
import net.liftweb.json.{JObject, JValue}

import scala.collection.immutable.List

trait InBoundTrait[T] {
  val inboundAdapterCallContext: InboundAdapterCallContext
  val status: Status
  val data: T
}

//--------generated

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


case class OutBoundGetBank(outboundAdapterCallContext: OutboundAdapterCallContext,
                           bankId: BankId) extends TopicTrait
case class InBoundGetBank(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankCommons) extends InBoundTrait[BankCommons]

case class OutBoundGetBankAccountsForUser(outboundAdapterCallContext: OutboundAdapterCallContext,
                                          username: String) extends TopicTrait
case class InBoundGetBankAccountsForUser(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[InboundAccountCommons]) extends InBoundTrait[List[InboundAccountCommons]]


case class OutBoundGetBankAccountOld(
                                  bankId: BankId,
                                  accountId: AccountId) extends TopicTrait
case class InBoundGetBankAccountOld(status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

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
                                   transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                   amount: BigDecimal,
                                   description: String,
                                   transactionRequestType: TransactionRequestType,
                                   chargePolicy: String) extends TopicTrait

case class InBoundMakePaymentv210(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionId) extends InBoundTrait[TransactionId]


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
                                                       initiator: User,       //TODO fixme
                                                       fromAccount: BankAccount,
                                                       transReqId: TransactionRequestId,
                                                       transactionRequestType: TransactionRequestType) extends TopicTrait
case class InBoundCreateTransactionAfterChallengev300(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundMakePaymentv300(outboundAdapterCallContext: OutboundAdapterCallContext,
                                   initiator: User,      //TODO fixme
                                   fromAccount: BankAccount,
                                   toAccount: BankAccount,
                                   toCounterparty: CounterpartyTrait,
                                   transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
                                   transactionRequestType: TransactionRequestType,
                                   chargePolicy: String) extends TopicTrait
case class InBoundMakePaymentv300(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionId) extends InBoundTrait[TransactionId]


case class OutBoundCreateTransactionRequestv300(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                initiator: User,      //TODO fixme
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
                                                  value: String) extends TopicTrait
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
                                                  value: String) extends TopicTrait
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
                                 staffUser: User,      //TODO fixme
                                 customerUser: User,      //TODO fixme
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
                              user: User,      //TODO fixme
                              meetingId: String) extends TopicTrait
case class InBoundGetMeeting(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: MeetingCommons) extends InBoundTrait[MeetingCommons]

case class OutBoundGetUser(name: String, password: String) extends TopicTrait

case class InBoundGetUser(status: Status, data: InboundUser) extends InBoundTrait[InboundUser] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}
case class OutBoundGetExternalUser(name: String, password: String) extends TopicTrait

case class InBoundGetExternalUser(status: Status, data: InboundExternalUser) extends InBoundTrait[InboundExternalUser] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}


//create bound case classes
case class OutBoundCreateChallenge(outboundAdapterCallContext: OutboundAdapterCallContext,
                                   bankId: BankId,
                                   accountId: AccountId,
                                   userId: String,
                                   transactionRequestType: TransactionRequestType,
                                   transactionRequestId: String,
                                   scaMethod: Option[SCA]) extends TopicTrait

case class InBoundCreateChallenge(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: String) extends InBoundTrait[String]

case class OutBoundCreateCounterparty(outboundAdapterCallContext: OutboundAdapterCallContext, name: String, description: String, createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String, otherAccountRoutingScheme: String, otherAccountRoutingAddress: String, otherAccountSecondaryRoutingScheme: String, otherAccountSecondaryRoutingAddress: String, otherBankRoutingScheme: String, otherBankRoutingAddress: String, otherBranchRoutingScheme: String, otherBranchRoutingAddress: String, isBeneficiary: Boolean, bespoke: List[CounterpartyBespoke]) extends TopicTrait

case class InBoundCreateCounterparty(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyTraitCommons) extends InBoundTrait[CounterpartyTraitCommons]

case class OutBoundGetTransactionRequests210(outboundAdapterCallContext: OutboundAdapterCallContext, initiator : User, fromAccount : BankAccount) extends TopicTrait

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
                                           accountAttributes: List[ProductAttribute]
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
                                          customerAddressId : String) extends TopicTrait
case class InBoundDeleteCustomerAddress(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundCreateUserAuthContextUpdate(outboundAdapterCallContext: OutboundAdapterCallContext,
                                               userId: String,
                                               key: String,
                                               value: String) extends TopicTrait
case class InBoundCreateUserAuthContextUpdate(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: UserAuthContextUpdateCommons) extends InBoundTrait[UserAuthContextUpdateCommons]

case class OutBoundDeleteTaxResidence(outboundAdapterCallContext: OutboundAdapterCallContext,
                                      taxResourceId : String) extends TopicTrait
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
                              accountRoutingScheme: String,
                              accountRoutingAddress: String
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
                                      customerId: String) extends TopicTrait
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
                                      customerId: String
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
                                     accountRoutingScheme: String,
                                     accountRoutingAddress: String) extends TopicTrait

case class InBoundCreateBankAccount(inboundAdapterCallContext: InboundAdapterCallContext,
                                    status: Status,
                                    data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]

case class OutBoundCreateMessage(outboundAdapterCallContext: OutboundAdapterCallContext,
                                 user: User,
                                 bankId: BankId,
                                 message: String,
                                 fromDepartment: String,
                                 fromPerson : String) extends TopicTrait

case class InBoundCreateMessage(inboundAdapterCallContext: InboundAdapterCallContext,
                                status: Status,
                                data: CustomerMessageCommons) extends InBoundTrait[CustomerMessageCommons]

case class OutBoundValidateChallengeAnswer(outboundAdapterCallContext: OutboundAdapterCallContext,
                                           challengeId: String,
                                           hashOfSuppliedAnswer: String) extends TopicTrait

case class InBoundValidateChallengeAnswer(inboundAdapterCallContext: InboundAdapterCallContext,
                                status: Status,
                                data: Boolean) extends InBoundTrait[Boolean]

//---------------------


case class OutBoundGetBankLegacy (outboundAdapterCallContext: OutboundAdapterCallContext,
                                  bankId: BankId) extends TopicTrait
case class InBoundGetBankLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankCommons) extends InBoundTrait[BankCommons]


case class OutBoundGetBanksLegacy (outboundAdapterCallContext: OutboundAdapterCallContext) extends TopicTrait
case class InBoundGetBanksLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[BankCommons]) extends InBoundTrait[List[BankCommons]]


case class OutBoundGetBankAccountsForUserLegacy (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                 username: String) extends TopicTrait
case class InBoundGetBankAccountsForUserLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[InboundAccountCommons]) extends InBoundTrait[List[InboundAccountCommons]]


case class OutBoundGetBankAccountLegacy (outboundAdapterCallContext: OutboundAdapterCallContext,
                                         bankId: BankId,
                                         accountId: AccountId) extends TopicTrait
case class InBoundGetBankAccountLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetBankAccountByRouting (outboundAdapterCallContext: OutboundAdapterCallContext,
                                            scheme: String,
                                            address: String) extends TopicTrait
case class InBoundGetBankAccountByRouting (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetCoreBankAccountsLegacy (outboundAdapterCallContext: OutboundAdapterCallContext,
                                              bankIdAccountIds: List[BankIdAccountId]) extends TopicTrait
case class InBoundGetCoreBankAccountsLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CoreAccount]) extends InBoundTrait[List[CoreAccount]]


case class OutBoundGetBankAccountsHeldLegacy (outboundAdapterCallContext: OutboundAdapterCallContext,
                                              bankIdAccountIds: List[BankIdAccountId]) extends TopicTrait
case class InBoundGetBankAccountsHeldLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[AccountHeld]) extends InBoundTrait[List[AccountHeld]]


case class OutBoundCheckBankAccountExistsLegacy (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                 bankId: BankId,
                                                 accountId: AccountId) extends TopicTrait
case class InBoundCheckBankAccountExistsLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetCounterpartyByCounterpartyIdLegacy (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                          counterpartyId: CounterpartyId) extends TopicTrait
case class InBoundGetCounterpartyByCounterpartyIdLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyTraitCommons) extends InBoundTrait[CounterpartyTraitCommons]


case class OutBoundGetCounterpartiesLegacy (outboundAdapterCallContext: OutboundAdapterCallContext,
                                            thisBankId: BankId,
                                            thisAccountId: AccountId,
                                            viewId: ViewId) extends TopicTrait
case class InBoundGetCounterpartiesLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CounterpartyTraitCommons]) extends InBoundTrait[List[CounterpartyTraitCommons]]


case class OutBoundGetTransactionsLegacy (outboundAdapterCallContext: OutboundAdapterCallContext,
                                          bankId: BankId,
                                          accountId: AccountId,
                                          limit: Int,
                                          offset: Int,
                                          fromDate: String,
                                          toDate: String) extends TopicTrait
case class InBoundGetTransactionsLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[Transaction]) extends InBoundTrait[List[Transaction]]


case class OutBoundGetTransactionLegacy (outboundAdapterCallContext: OutboundAdapterCallContext,
                                         bankId: BankId,
                                         accountId: AccountId,
                                         transactionId: TransactionId) extends TopicTrait
case class InBoundGetTransactionLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Transaction) extends InBoundTrait[Transaction]


case class OutBoundCreatePhysicalCardLegacy (outboundAdapterCallContext: OutboundAdapterCallContext,
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
                                             customerId: String) extends TopicTrait
case class InBoundCreatePhysicalCardLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: PhysicalCard) extends InBoundTrait[PhysicalCard]


case class OutBoundCreateBankAccountLegacy (bankId: BankId,
                                            accountId: AccountId,
                                            accountType: String,
                                            accountLabel: String,
                                            currency: String,
                                            initialBalance: BigDecimal,
                                            accountHolderName: String,
                                            branchId: String,
                                            accountRoutingScheme: String,
                                            accountRoutingAddress: String) extends TopicTrait
case class InBoundCreateBankAccountLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetCustomerByCustomerIdLegacy (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                  customerId: String) extends TopicTrait
case class InBoundGetCustomerByCustomerIdLegacy (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerCommons) extends InBoundTrait[CustomerCommons]

case class OutBoundGetBankAccountByIban (outboundAdapterCallContext: OutboundAdapterCallContext,
                                         iban : String) extends TopicTrait
case class InBoundGetBankAccountByIban (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]

case class OutBoundGetBankAccounts (outboundAdapterCallContext: OutboundAdapterCallContext,
                                    bankIdAccountIds: List[BankIdAccountId]) extends TopicTrait
case class InBoundGetBankAccounts (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[BankAccountCommons]) extends InBoundTrait[List[BankAccountCommons]]

case class OutBoundDynamicEntityProcess (outboundAdapterCallContext: OutboundAdapterCallContext,
                                         operation: DynamicEntityOperation,
                                         entityName: String,
                                         requestBody: Option[JObject],
                                         entityId: Option[String]) extends TopicTrait
case class InBoundDynamicEntityProcess (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: JValue) extends InBoundTrait[JValue]

// because swagger generate not support JValue type, so here supply too xxxDoc TO generate correct request and response body example
case class FooBar(name: String, number: Int, fooBarId: Option[String] = None)
case class OutBoundDynamicEntityProcessDoc (outboundAdapterCallContext: OutboundAdapterCallContext,
                                            operation: DynamicEntityOperation,
                                            entityName: String,
                                            requestBody: Option[FooBar],
                                            entityId: Option[String]) extends TopicTrait
case class InBoundDynamicEntityProcessDoc (inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: FooBar) extends InBoundTrait[FooBar]


// --------------------- some special connector methods corresponding InBound and OutBound
case class OutBoundCreateChallenges(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, accountId: AccountId, userIds: List[String], transactionRequestType: TransactionRequestType, transactionRequestId: String, scaMethod: Option[StrongCustomerAuthentication.SCA]) extends TopicTrait
case class InBoundCreateChallenges(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[String]) extends InBoundTrait[List[String]]

case class OutBoundGetEmptyBankAccount() extends TopicTrait
case class InBoundGetEmptyBankAccount(status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetCounterpartyFromTransaction(bankId: BankId, accountId: AccountId, counterpartyId: String) extends TopicTrait
case class InBoundGetCounterpartyFromTransaction(status: Status, data: Counterparty) extends InBoundTrait[Counterparty] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetCounterpartiesFromTransaction(bankId: BankId, accountId: AccountId) extends TopicTrait
case class InBoundGetCounterpartiesFromTransaction(status: Status, data: List[Counterparty]) extends InBoundTrait[List[Counterparty]] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetCounterparty(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String) extends TopicTrait
case class InBoundGetCounterparty(status: Status, data: Counterparty) extends InBoundTrait[Counterparty] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetPhysicalCards(user: User) extends TopicTrait
case class InBoundGetPhysicalCards(status: Status, data: List[PhysicalCard]) extends InBoundTrait[List[PhysicalCard]] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetPhysicalCardsForBankLegacy(bank: Bank, user: User,
                                                 limit: Int,
                                                 offset: Int,
                                                 fromDate: String,
                                                 toDate: String) extends TopicTrait
case class InBoundGetPhysicalCardsForBankLegacy(status: Status, data: List[PhysicalCard]) extends InBoundTrait[List[PhysicalCard]] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundMakePayment(initiator: User, fromAccountUID: BankIdAccountId, toAccountUID: BankIdAccountId, amt: BigDecimal, description: String, transactionRequestType: TransactionRequestType) extends TopicTrait
case class InBoundMakePayment(status: Status, data: TransactionId) extends InBoundTrait[TransactionId] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundMakePaymentv200(fromAccount: BankAccount, toAccount: BankAccount, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, amount: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String) extends TopicTrait
case class InBoundMakePaymentv200(status: Status, data: TransactionId) extends InBoundTrait[TransactionId] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundMakePaymentImpl(fromAccount: BankAccount, toAccount: BankAccount, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, amt: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String) extends TopicTrait
case class InBoundMakePaymentImpl(status: Status, data: TransactionId) extends InBoundTrait[TransactionId] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundCreateTransactionRequest(initiator: User, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody) extends TopicTrait
case class InBoundCreateTransactionRequest(status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundCreateTransactionRequestv200(initiator: User, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody) extends TopicTrait
case class InBoundCreateTransactionRequestv200(status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetStatus(challengeThresholdAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal, transactionRequestType: TransactionRequestType) extends TopicTrait
case class InBoundGetStatus(status: Status, statusValue: String) extends InBoundTrait[TransactionRequestStatus.Value] {

  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
  override val data: TransactionRequestStatus.Value = TransactionRequestStatus.withName(statusValue)
}

case class OutBoundGetChargeValue(chargeLevelAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal) extends TopicTrait
case class InBoundGetChargeValue(status: Status, data: String) extends InBoundTrait[String] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundCreateTransactionRequestv400(outboundAdapterCallContext: OutboundAdapterCallContext, initiator: User, viewId: ViewId, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, detailsPlain: String, chargePolicy: String, challengeType: Option[String], scaMethod: Option[StrongCustomerAuthentication.SCA]) extends TopicTrait
case class InBoundCreateTransactionRequestv400(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]

case class OutBoundCreateTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType, fromAccount: BankAccount, counterparty: BankAccount, body: TransactionRequestBody, status: String, charge: TransactionRequestCharge) extends TopicTrait
case class InBoundCreateTransactionRequestImpl(status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundCreateTransactionRequestImpl210(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, details: String, status: String, charge: TransactionRequestCharge, chargePolicy: String) extends TopicTrait
case class InBoundCreateTransactionRequestImpl210(status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundSaveTransactionRequestTransaction(transactionRequestId: TransactionRequestId, transactionId: TransactionId) extends TopicTrait
case class InBoundSaveTransactionRequestTransaction(status: Status, data: Boolean) extends InBoundTrait[Boolean] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundSaveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId) extends TopicTrait
case class InBoundSaveTransactionRequestTransactionImpl(status: Status, data: Boolean) extends InBoundTrait[Boolean] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundSaveTransactionRequestChallenge(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge) extends TopicTrait
case class InBoundSaveTransactionRequestChallenge(status: Status, data: Boolean) extends InBoundTrait[Boolean] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundSaveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge) extends TopicTrait
case class InBoundSaveTransactionRequestChallengeImpl(status: Status, data: Boolean) extends InBoundTrait[Boolean] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundSaveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String) extends TopicTrait
case class InBoundSaveTransactionRequestStatusImpl(status: Status, data: Boolean) extends InBoundTrait[Boolean] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetTransactionRequests(initiator: User, fromAccount: BankAccount) extends TopicTrait
case class InBoundGetTransactionRequests(status: Status, data: List[TransactionRequest]) extends InBoundTrait[List[TransactionRequest]] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetTransactionRequestStatuses() extends TopicTrait
case class InBoundGetTransactionRequestStatuses(status: Status, data: TransactionRequestStatusCommons) extends InBoundTrait[TransactionRequestStatusCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetTransactionRequestStatusesImpl() extends TopicTrait
case class InBoundGetTransactionRequestStatusesImpl(status: Status, data: TransactionRequestStatusCommons) extends InBoundTrait[TransactionRequestStatusCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetTransactionRequestsImpl(fromAccount: BankAccount) extends TopicTrait
case class InBoundGetTransactionRequestsImpl(status: Status, data: List[TransactionRequest]) extends InBoundTrait[List[TransactionRequest]] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetTransactionRequestsImpl210(fromAccount: BankAccount) extends TopicTrait
case class InBoundGetTransactionRequestsImpl210(status: Status, data: List[TransactionRequest]) extends InBoundTrait[List[TransactionRequest]] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetTransactionRequestTypes(initiator: User, fromAccount: BankAccount) extends TopicTrait
case class InBoundGetTransactionRequestTypes(status: Status, data: List[TransactionRequestType]) extends InBoundTrait[List[TransactionRequestType]] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetTransactionRequestTypesImpl(fromAccount: BankAccount) extends TopicTrait
case class InBoundGetTransactionRequestTypesImpl(status: Status, data: List[TransactionRequestType]) extends InBoundTrait[List[TransactionRequestType]] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundCreateTransactionAfterChallenge(initiator: User, transReqId: TransactionRequestId) extends TopicTrait
case class InBoundCreateTransactionAfterChallenge(status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundCreateTransactionAfterChallengev200(fromAccount: BankAccount, toAccount: BankAccount, transactionRequest: TransactionRequest) extends TopicTrait
case class InBoundCreateTransactionAfterChallengev200(status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundAddBankAccount(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, accountType: String, accountLabel: String, currency: String, initialBalance: BigDecimal, accountHolderName: String, branchId: String, accountRoutingScheme: String, accountRoutingAddress: String) extends TopicTrait
case class InBoundAddBankAccount(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]

case class BankAndBankAccount(bank: BankCommons, account: BankAccountCommons)
case class OutBoundCreateBankAndAccount(bankName: String, bankNationalIdentifier: String, accountNumber: String, accountType: String, accountLabel: String, currency: String, accountHolderName: String, branchId: String, accountRoutingScheme: String, accountRoutingAddress: String) extends TopicTrait
case class InBoundCreateBankAndAccount(status: Status, value: BankAndBankAccount) extends InBoundTrait[(Bank, BankAccount)] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
  override val data: (Bank, BankAccount) = (value.bank, value.account)
}

case class OutBoundCreateSandboxBankAccount(bankId: BankId, accountId: AccountId, accountNumber: String, accountType: String, accountLabel: String, currency: String, initialBalance: BigDecimal, accountHolderName: String, branchId: String, accountRoutingScheme: String, accountRoutingAddress: String) extends TopicTrait
case class InBoundCreateSandboxBankAccount(status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundAccountExists(bankId: BankId, accountNumber: String) extends TopicTrait
case class InBoundAccountExists(status: Status, data: Boolean) extends InBoundTrait[Boolean] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundRemoveAccount(bankId: BankId, accountId: AccountId) extends TopicTrait
case class InBoundRemoveAccount(status: Status, data: Boolean) extends InBoundTrait[Boolean] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetMatchingTransactionCount(bankNationalIdentifier: String, accountNumber: String, amount: String, completed: Date, otherAccountHolder: String) extends TopicTrait
case class InBoundGetMatchingTransactionCount(status: Status, data: Int) extends InBoundTrait[Int] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundUpdateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: BigDecimal) extends TopicTrait
case class InBoundUpdateAccountBalance(status: Status, data: Boolean) extends InBoundTrait[Boolean] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundSetBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber: String, updateDate: Date) extends TopicTrait
case class InBoundSetBankAccountLastUpdated(status: Status, data: Boolean) extends InBoundTrait[Boolean] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundUpdateAccountLabel(bankId: BankId, accountId: AccountId, label: String) extends TopicTrait
case class InBoundUpdateAccountLabel(status: Status, data: Boolean) extends InBoundTrait[Boolean] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundUpdateAccount(bankId: BankId, accountId: AccountId, label: String) extends TopicTrait
case class InBoundUpdateAccount(status: Status, data: Boolean) extends InBoundTrait[Boolean] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class GetProductsParam(name: String, value: List[String])
case class OutBoundGetProducts(bankId: BankId, params: List[GetProductsParam]) extends TopicTrait

case class InBoundGetProducts(status: Status, data: List[ProductCommons]) extends InBoundTrait[List[ProductCommons]] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetProduct(bankId: BankId, productCode: ProductCode) extends TopicTrait
case class InBoundGetProduct(status: Status, data: ProductCommons) extends InBoundTrait[ProductCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}


case class OutBoundCreateOrUpdateBranch(branch: BranchT) extends TopicTrait
case class InBoundCreateOrUpdateBranch(status: Status, data: BranchTCommons) extends InBoundTrait[BranchTCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundCreateOrUpdateBank(bankId: String, fullBankName: String, shortBankName: String, logoURL: String, websiteURL: String, swiftBIC: String, national_identifier: String, bankRoutingScheme: String, bankRoutingAddress: String) extends TopicTrait
case class InBoundCreateOrUpdateBank(status: Status, data: BankCommons) extends InBoundTrait[BankCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundCreateOrUpdateAtm(atm: AtmT) extends TopicTrait
case class InBoundCreateOrUpdateAtm(status: Status, data: AtmTCommons) extends InBoundTrait[AtmTCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundCreateOrUpdateProduct(bankId: String, code: String, parentProductCode: Option[String], name: String, category: String, family: String, superFamily: String, moreInfoUrl: String, details: String, description: String, metaLicenceId: String, metaLicenceName: String) extends TopicTrait
case class InBoundCreateOrUpdateProduct(status: Status, data: ProductCommons) extends InBoundTrait[ProductCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundCreateOrUpdateFXRate(bankId: String, fromCurrencyCode: String, toCurrencyCode: String, conversionValue: Double, inverseConversionValue: Double, effectiveDate: Date) extends TopicTrait
case class InBoundCreateOrUpdateFXRate(status: Status, data: FXRateCommons) extends InBoundTrait[FXRateCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetBranchLegacy(bankId: BankId, branchId: BranchId) extends TopicTrait
case class InBoundGetBranchLegacy(status: Status, data: BranchTCommons) extends InBoundTrait[BranchTCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetAtmLegacy(bankId: BankId, atmId: AtmId) extends TopicTrait
case class InBoundGetAtmLegacy(status: Status, data: AtmTCommons) extends InBoundTrait[AtmTCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundAccountOwnerExists(user: User, bankId: BankId, accountId: AccountId) extends TopicTrait
case class InBoundAccountOwnerExists(status: Status, data: Boolean) extends InBoundTrait[Boolean] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String) extends TopicTrait
case class InBoundGetCurrentFxRate(status: Status, data: FXRateCommons) extends InBoundTrait[FXRateCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetCurrentFxRateCached(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String) extends TopicTrait
case class InBoundGetCurrentFxRateCached(status: Status, data: FXRateCommons) extends InBoundTrait[FXRateCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetTransactionRequestTypeCharge(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestType: TransactionRequestType) extends TopicTrait
case class InBoundGetTransactionRequestTypeCharge(status: Status, data: TransactionRequestTypeChargeCommons) extends InBoundTrait[TransactionRequestTypeChargeCommons] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

case class OutBoundGetTransactionRequestTypeCharges(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestTypes: List[TransactionRequestType]) extends TopicTrait
case class InBoundGetTransactionRequestTypeCharges(status: Status, data: List[TransactionRequestTypeChargeCommons]) extends InBoundTrait[List[TransactionRequestTypeChargeCommons]] {
  override val inboundAdapterCallContext: InboundAdapterCallContext = InboundAdapterCallContext()
}

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

case class OutBoundGetCustomerIdsByAttributeNameValues(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, nameValues: Map[String,List[String]]) extends TopicTrait
case class InBoundGetCustomerIdsByAttributeNameValues(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[String]) extends InBoundTrait[List[String]]

case class CustomerAndAttribute(customer: Customer, attributes: List[CustomerAttribute])
case class OutBoundGetCustomerAttributesForCustomers(outboundAdapterCallContext: OutboundAdapterCallContext, customers: List[Customer]) extends TopicTrait
case class InBoundGetCustomerAttributesForCustomers(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CustomerAndAttribute]) extends InBoundTrait[List[CustomerAndAttribute]]

case class OutBoundGetTransactionIdsByAttributeNameValues(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, nameValues: Map[String,List[String]]) extends TopicTrait
case class InBoundGetTransactionIdsByAttributeNameValues(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[String]) extends InBoundTrait[List[String]]

case class OutBoundGetTransactionAttributes(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, transactionId: TransactionId) extends TopicTrait
case class InBoundGetTransactionAttributes(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionAttributeCommons]) extends InBoundTrait[List[TransactionAttributeCommons]]

case class OutBoundGetCustomerAttributeById(outboundAdapterCallContext: OutboundAdapterCallContext, customerAttributeId: String) extends TopicTrait
case class InBoundGetCustomerAttributeById(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CustomerAttributeCommons) extends InBoundTrait[CustomerAttributeCommons]

case class OutBoundCreateDirectDebit(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: String, accountId: String, customerId: String, userId: String, counterpartyId: String, dateSigned: Date, dateStarts: Date, dateExpires: Option[Date]) extends TopicTrait
case class InBoundCreateDirectDebit(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: DirectDebitTraitCommons) extends InBoundTrait[DirectDebitTraitCommons]

case class OutBoundDeleteCustomerAttribute(outboundAdapterCallContext: OutboundAdapterCallContext, customerAttributeId: String) extends TopicTrait
case class InBoundDeleteCustomerAttribute(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Boolean) extends InBoundTrait[Boolean]

case class OutBoundCheckExternalUserCredentials(outboundAdapterCallContext: OutboundAdapterCallContext, username: String, password: String) extends TopicTrait
case class InBoundCheckExternalUserCredentials(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: InboundExternalUser) extends InBoundTrait[InboundExternalUser]
// --------------------- some special connector methods corresponding InBound and OutBound -- end --