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

package com.openbankproject.commons.dto

import java.util.Date

import com.openbankproject.commons.model._

import scala.collection.immutable.List

trait InBoundTrait[T] {
  val inboundAdapterCallContext: InboundAdapterCallContext
  val status: Status
  val data: T
}

//--------generated

case class OutBoundGetObpApiLoopback(outboundAdapterCallContext: OutboundAdapterCallContext) extends TopicTrait
case class InBoundGetObpApiLoopback(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: ObpApiLoopback) extends InBoundTrait[ObpApiLoopback]

case class OutBoundGetChallengeThreshold(outboundAdapterCallContext: OutboundAdapterCallContext,
                                         bankId: String,
                                         accountId: String,
                                         viewId: String,
                                         transactionRequestType: String,
                                         currency: String,
                                         userId: String,
                                         userName: String) extends TopicTrait
case class InBoundGetChallengeThreshold(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AmountOfMoney) extends InBoundTrait[AmountOfMoney]


case class OutBoundGetChargeLevel(outboundAdapterCallContext: OutboundAdapterCallContext,
                                  bankId: BankId,
                                  accountId: AccountId,
                                  viewId: ViewId,
                                  userId: String,
                                  userName: String,
                                  transactionRequestType: String,
                                  currency: String) extends TopicTrait
case class InBoundGetChargeLevel(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: AmountOfMoney) extends InBoundTrait[AmountOfMoney]


case class OutBoundGetBank(outboundAdapterCallContext: OutboundAdapterCallContext,
                           bankId: BankId) extends TopicTrait
case class InBoundGetBank(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankCommons) extends InBoundTrait[BankCommons]

case class OutBoundGetBankAccountsForUser(outboundAdapterCallContext: OutboundAdapterCallContext,
                                          username: String) extends TopicTrait
case class InBoundGetBankAccountsForUser(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[InboundAccountCommons]) extends InBoundTrait[List[InboundAccountCommons]]


case class OutBoundGetBankAccount(outboundAdapterCallContext: OutboundAdapterCallContext,
                                  bankId: BankId,
                                  accountId: AccountId) extends TopicTrait
case class InBoundGetBankAccount(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetCoreBankAccounts(outboundAdapterCallContext: OutboundAdapterCallContext,
                                       bankIdAccountIds: List[BankIdAccountId]) extends TopicTrait
case class InBoundGetCoreBankAccounts(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CoreAccount]) extends InBoundTrait[List[CoreAccount]]


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
case class InBoundGetTransactions(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionCommons]) extends InBoundTrait[List[TransactionCommons]]


case class OutBoundGetTransaction(outboundAdapterCallContext: OutboundAdapterCallContext,
                                  bankId: BankId,
                                  accountId: AccountId,
                                  transactionId: TransactionId) extends TopicTrait
case class InBoundGetTransaction(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionCommons) extends InBoundTrait[TransactionCommons]

case class OutBoundMakePaymentv210(outboundAdapterCallContext: OutboundAdapterCallContext,
                                   fromAccount: BankAccountCommons,
                                   toAccount: BankAccountCommons,
                                   transactionRequestCommonBody: TransactionRequestCommonBodyJSONCommons,
                                   amount: BigDecimal,
                                   description: String,
                                   transactionRequestType: TransactionRequestType,
                                   chargePolicy: String) extends TopicTrait

case class InBoundMakePaymentv210(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionId) extends InBoundTrait[TransactionId]


case class OutBoundCreateTransactionRequestv210(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                initiator: User, //TODO FIXME
                                                viewId: ViewId,
                                                fromAccount: BankAccountCommons,
                                                toAccount: BankAccountCommons,
                                                transactionRequestType: TransactionRequestType,
                                                transactionRequestCommonBody: TransactionRequestCommonBodyJSONCommons,
                                                detailsPlain: String,
                                                chargePolicy: String) extends TopicTrait
case class InBoundCreateTransactionRequestv210(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundCreateTransactionAfterChallengeV210(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                       fromAccount: BankAccountCommons,
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
                                                       fromAccount: BankAccountCommons,
                                                       transReqId: TransactionRequestId,
                                                       transactionRequestType: TransactionRequestType) extends TopicTrait
case class InBoundCreateTransactionAfterChallengev300(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundMakePaymentv300(outboundAdapterCallContext: OutboundAdapterCallContext,
                                   initiator: User,      //TODO fixme
                                   fromAccount: BankAccountCommons,
                                   toAccount: BankAccountCommons,
                                   toCounterparty: CounterpartyTraitCommons,
                                   transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
                                   transactionRequestType: TransactionRequestType,
                                   chargePolicy: String) extends TopicTrait
case class InBoundMakePaymentv300(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionId) extends InBoundTrait[TransactionId]


case class OutBoundCreateTransactionRequestv300(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                initiator: User,      //TODO fixme
                                                viewId: ViewId,
                                                fromAccount: BankAccountCommons,
                                                toAccount: BankAccountCommons,
                                                toCounterparty: CounterpartyTraitCommons,
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
                                   faceImage: CustomerFaceImage,
                                   dateOfBirth: Date,
                                   relationshipStatus: String,
                                   dependents: Int,
                                   dobOfDependents: List[Date],
                                   highestEducationAttained: String,
                                   employmentStatus: String,
                                   kycStatus: Boolean,
                                   lastOkDate: Date,
                                   creditRating: Option[CreditRating],
                                   creditLimit: Option[AmountOfMoney],
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
                                bankId: BankId) extends TopicTrait
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
                                                  attributType: ProductAttributeType.Value,
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
                                                  attributType: AccountAttributeType.Value,
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
case class InBoundGetProductCollectionItemsTree(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[(ProductCollectionItemCommons, ProductCommons, List[ProductAttributeCommons])])


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

case class OutBoundGetUser(outboundAdapterCallContext: OutboundAdapterCallContext, name: String, password: String) extends TopicTrait

case class InBoundGetUser(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: InboundUser) extends InBoundTrait[InboundUser]


//create bound case classes
case class OutBoundCreateChallenge(outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String) extends TopicTrait

case class InBoundCreateChallenge(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: String) extends InBoundTrait[String]

case class OutBoundCreateCounterparty(outboundAdapterCallContext: OutboundAdapterCallContext, name: String, description: String, createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String, otherAccountRoutingScheme: String, otherAccountRoutingAddress: String, otherAccountSecondaryRoutingScheme: String, otherAccountSecondaryRoutingAddress: String, otherBankRoutingScheme: String, otherBankRoutingAddress: String, otherBranchRoutingScheme: String, otherBranchRoutingAddress: String, isBeneficiary: Boolean, bespoke: List[CounterpartyBespoke]) extends TopicTrait

case class InBoundCreateCounterparty(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: CounterpartyTraitCommons) extends InBoundTrait[CounterpartyTraitCommons]

case class OutBoundGetTransactionRequests210(outboundAdapterCallContext: OutboundAdapterCallContext, initiator : User, fromAccount : BankAccount) extends TopicTrait

case class InBoundGetTransactionRequests210(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequest]) extends InBoundTrait[List[TransactionRequest]]

case class OutBoundGetTransactionsCore(bankId: BankId, accountID: AccountId, limit: Int, offset: Int, fromDate: String, toDate: String) extends TopicTrait
case class InBoundGetTransactionsCore(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionCore]) extends InBoundTrait[List[TransactionCore]]

//-------- return type are not Future--------------------------------------------------------------------------------------------------

case class OutBoundGetAdapterInfo(outboundAdapterCallContext: OutboundAdapterCallContext) extends TopicTrait
case class InBoundGetAdapterInfo(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: InboundAdapterInfoInternal) extends InBoundTrait[InboundAdapterInfoInternal]


case class OutBoundGetBanks(outboundAdapterCallContext: OutboundAdapterCallContext) extends TopicTrait
case class InBoundGetBanks(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[BankCommons]) extends InBoundTrait[List[BankCommons]]


case class OutBoundGetBankAccountsHeld(outboundAdapterCallContext: OutboundAdapterCallContext,
                                       bankIdAccountIds: List[BankIdAccountId]) extends TopicTrait
case class InBoundGetBankAccountsHeld(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[AccountHeld]) extends InBoundTrait[List[AccountHeld]]


case class OutBoundGetEmptyBankAccount(outboundAdapterCallContext: OutboundAdapterCallContext) extends TopicTrait
case class InBoundGetEmptyBankAccount(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankAccountCommons) extends InBoundTrait[BankAccountCommons]


case class OutBoundGetCounterpartyFromTransaction(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                  bankId: BankId,
                                                  accountId: AccountId,
                                                  counterpartyId: String) extends TopicTrait
case class InBoundGetCounterpartyFromTransaction(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Counterparty) extends InBoundTrait[Counterparty]


case class OutBoundGetCounterpartiesFromTransaction(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                    bankId: BankId,
                                                    accountId: AccountId) extends TopicTrait
case class InBoundGetCounterpartiesFromTransaction(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[Counterparty]) extends InBoundTrait[List[Counterparty]]


case class OutBoundGetCounterparty(outboundAdapterCallContext: OutboundAdapterCallContext,
                                   thisBankId: BankId,
                                   thisAccountId: AccountId,
                                   couterpartyId: String) extends TopicTrait
case class InBoundGetCounterparty(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: Counterparty) extends InBoundTrait[Counterparty]


case class OutBoundGetCounterparties(outboundAdapterCallContext: OutboundAdapterCallContext,
                                     thisBankId: BankId,
                                     thisAccountId: AccountId,
                                     viewId: ViewId) extends TopicTrait
case class InBoundGetCounterparties(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[CounterpartyTraitCommons]) extends InBoundTrait[List[CounterpartyTraitCommons]]



case class OutBoundGetPhysicalCards(outboundAdapterCallContext: OutboundAdapterCallContext,
                                    user: User) extends TopicTrait
case class InBoundGetPhysicalCards(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[PhysicalCard]) extends InBoundTrait[List[PhysicalCard]]


case class OutBoundGetPhysicalCardsForBank(outboundAdapterCallContext: OutboundAdapterCallContext,
                                           bank: Bank,
                                           user: User) extends TopicTrait
case class InBoundGetPhysicalCardsForBank(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[PhysicalCard]) extends InBoundTrait[List[PhysicalCard]]


case class OutBoundCreatePhysicalCard(outboundAdapterCallContext: OutboundAdapterCallContext,
                                              bankCardNumber: String,
                                              nameOnCard: String,
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
                                              posted: Option[CardPostedInfo]) extends TopicTrait
case class InBoundCreatePhysicalCard(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: PhysicalCard) extends InBoundTrait[PhysicalCard]


case class OutBoundMakePayment(outboundAdapterCallContext: OutboundAdapterCallContext,
                               initiator: User,
                               fromAccountUID: BankIdAccountId,
                               toAccountUID: BankIdAccountId,
                               amt: BigDecimal,
                               description: String,
                               transactionRequestType: TransactionRequestType) extends TopicTrait
case class InBoundMakePayment(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionId) extends InBoundTrait[TransactionId]


case class OutBoundMakePaymentv200(outboundAdapterCallContext: OutboundAdapterCallContext,
                                   fromAccount: BankAccount,
                                   toAccount: BankAccount,
                                   transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                   amount: BigDecimal,
                                   description: String,
                                   transactionRequestType: TransactionRequestType,
                                   chargePolicy: String) extends TopicTrait
case class InBoundMakePaymentv200(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionId) extends InBoundTrait[TransactionId]


case class OutBoundMakePaymentImpl(outboundAdapterCallContext: OutboundAdapterCallContext,
                                   fromAccount: BankAccount,
                                   toAccount: BankAccount,
                                   transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                   amt: BigDecimal,
                                   description: String,
                                   transactionRequestType: TransactionRequestType,
                                   chargePolicy: String) extends TopicTrait
case class InBoundMakePaymentImpl(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionId) extends InBoundTrait[TransactionId]


case class OutBoundCreateTransactionRequest(outboundAdapterCallContext: OutboundAdapterCallContext,
                                            initiator: User,
                                            fromAccount: BankAccount,
                                            toAccount: BankAccount,
                                            transactionRequestType: TransactionRequestType,
                                            body: TransactionRequestBody) extends TopicTrait
case class InBoundCreateTransactionRequest(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundCreateTransactionRequestv200(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                initiator: User,
                                                fromAccount: BankAccount,
                                                toAccount: BankAccount,
                                                transactionRequestType: TransactionRequestType,
                                                body: TransactionRequestBody) extends TopicTrait
case class InBoundCreateTransactionRequestv200(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundCreateTransactionRequestImpl(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                transactionRequestId: TransactionRequestId,
                                                transactionRequestType: TransactionRequestType,
                                                fromAccount: BankAccount,
                                                counterparty: BankAccount,
                                                body: TransactionRequestBody,
                                                status: String,
                                                charge: TransactionRequestCharge) extends TopicTrait
case class InBoundCreateTransactionRequestImpl(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundCreateTransactionRequestImpl210(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                   transactionRequestId: TransactionRequestId,
                                                   transactionRequestType: TransactionRequestType,
                                                   fromAccount: BankAccount,
                                                   toAccount: BankAccount,
                                                   transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                                   details: String,
                                                   status: String,
                                                   charge: TransactionRequestCharge,
                                                   chargePolicy: String) extends TopicTrait
case class InBoundCreateTransactionRequestImpl210(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundGetTransactionRequests(outboundAdapterCallContext: OutboundAdapterCallContext,
                                          initiator: User,
                                          fromAccount: BankAccount) extends TopicTrait
case class InBoundGetTransactionRequests(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequest]) extends InBoundTrait[List[TransactionRequest]]


case class OutBoundGetTransactionRequestStatuses(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                ) extends TopicTrait
case class InBoundGetTransactionRequestStatuses(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequestStatusCommons) extends InBoundTrait[TransactionRequestStatusCommons]


case class OutBoundGetTransactionRequestStatusesImpl(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                    ) extends TopicTrait
case class InBoundGetTransactionRequestStatusesImpl(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequestStatusCommons) extends InBoundTrait[TransactionRequestStatusCommons]


case class OutBoundGetTransactionRequestsImpl(outboundAdapterCallContext: OutboundAdapterCallContext,
                                              fromAccount: BankAccount) extends TopicTrait
case class InBoundGetTransactionRequestsImpl(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequest]) extends InBoundTrait[List[TransactionRequest]]


case class OutBoundGetTransactionRequestsImpl210(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                 fromAccount: BankAccount) extends TopicTrait
case class InBoundGetTransactionRequestsImpl210(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequest]) extends InBoundTrait[List[TransactionRequest]]


case class OutBoundGetTransactionRequestImpl(outboundAdapterCallContext: OutboundAdapterCallContext,
                                             transactionRequestId: TransactionRequestId) extends TopicTrait
case class InBoundGetTransactionRequestImpl(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundGetTransactionRequestTypes(outboundAdapterCallContext: OutboundAdapterCallContext,
                                              initiator: User,
                                              fromAccount: BankAccount) extends TopicTrait
case class InBoundGetTransactionRequestTypes(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequestType]) extends InBoundTrait[List[TransactionRequestType]]


case class OutBoundGetTransactionRequestTypesImpl(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                  fromAccount: BankAccount) extends TopicTrait
case class InBoundGetTransactionRequestTypesImpl(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[TransactionRequestType]) extends InBoundTrait[List[TransactionRequestType]]


case class OutBoundCreateTransactionAfterChallenge(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                   initiator: User,
                                                   transReqId: TransactionRequestId) extends TopicTrait
case class InBoundCreateTransactionAfterChallenge(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundCreateTransactionAfterChallengev200(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                       fromAccount: BankAccount,
                                                       toAccount: BankAccount,
                                                       transactionRequest: TransactionRequest) extends TopicTrait
case class InBoundCreateTransactionAfterChallengev200(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: TransactionRequest) extends InBoundTrait[TransactionRequest]


case class OutBoundCreateBankAndAccount(outboundAdapterCallContext: OutboundAdapterCallContext,
                                        bankName: String,
                                        bankNationalIdentifier: String,
                                        accountNumber: String,
                                        accountType: String,
                                        accountLabel: String,
                                        currency: String,
                                        accountHolderName: String,
                                        branchId: String,
                                        accountRoutingScheme: String,
                                        accountRoutingAddress: String) extends TopicTrait
case class InBoundCreateBankAndAccount(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: (BankCommons, BankAccountCommons))


case class OutBoundGetProducts(outboundAdapterCallContext: OutboundAdapterCallContext,
                               bankId: BankId) extends TopicTrait
case class InBoundGetProducts(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: List[ProductCommons]) extends InBoundTrait[List[ProductCommons]]


case class OutBoundGetProduct(outboundAdapterCallContext: OutboundAdapterCallContext,
                              bankId: BankId,
                              productCode: ProductCode) extends TopicTrait
case class InBoundGetProduct(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: ProductCommons) extends InBoundTrait[ProductCommons]


case class OutBoundCreateOrUpdateBank(outboundAdapterCallContext: OutboundAdapterCallContext,
                                      bankId: String,
                                      fullBankName: String,
                                      shortBankName: String,
                                      logoURL: String,
                                      websiteURL: String,
                                      swiftBIC: String,
                                      national_identifier: String,
                                      bankRoutingScheme: String,
                                      bankRoutingAddress: String) extends TopicTrait
case class InBoundCreateOrUpdateBank(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: BankCommons) extends InBoundTrait[BankCommons]


case class OutBoundCreateOrUpdateProduct(outboundAdapterCallContext: OutboundAdapterCallContext,
                                         bankId: String,
                                         code: String,
                                         parentProductCode: Option[String],
                                         name: String,
                                         category: String,
                                         family: String,
                                         superFamily: String,
                                         moreInfoUrl: String,
                                         details: String,
                                         description: String,
                                         metaLicenceId: String,
                                         metaLicenceName: String) extends TopicTrait
case class InBoundCreateOrUpdateProduct(inboundAdapterCallContext: InboundAdapterCallContext, status: Status, data: ProductCommons) extends InBoundTrait[ProductCommons]


case class OutBoundGetTransactionRequestTypeCharge(outboundAdapterCallContext: OutboundAdapterCallContext,
                                                   bankId: BankId,
                                                   accountId: AccountId,
                                                   viewId: ViewId,
                                                   transactionRequestType: TransactionRequestType) extends TopicTrait


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