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


//--------generated

case class OutBoundGetObpApiLoopback  (outboundAdapterCallContext: OutboundAdapterCallContext)
case class InBoundGetObpApiLoopback  (inboundAdapterCallContext: InboundAdapterCallContext, data: ObpApiLoopback)


case class OutBoundGetAdapterInfoFuture  (outboundAdapterCallContext: OutboundAdapterCallContext)
case class InBoundGetAdapterInfoFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: InboundAdapterInfoInternal)


case class OutBoundGetChallengeThreshold  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                          bankId: String,
                                          accountId: String,
                                          viewId: String,
                                          transactionRequestType: String,
                                          currency: String,
                                          userId: String,
                                          userName: String)
case class InBoundGetChallengeThreshold  (inboundAdapterCallContext: InboundAdapterCallContext, data: AmountOfMoney)


case class OutBoundGetChargeLevel  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                   bankId: BankId,
                                   accountId: AccountId,
                                   viewId: ViewId,
                                   userId: String,
                                   userName: String,
                                   transactionRequestType: String,
                                   currency: String)
case class InBoundGetChargeLevel  (inboundAdapterCallContext: InboundAdapterCallContext, data: AmountOfMoney)


case class OutBoundGetBankFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                  bankId: BankId)
case class InBoundGetBankFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: BankCommons)


case class OutBoundGetBanksFuture  (outboundAdapterCallContext: OutboundAdapterCallContext)
case class InBoundGetBanksFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[BankCommons])

case class OutBoundGetBankAccountsForUserFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                          username: String)
case class InBoundGetBankAccountsForUserFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[InboundAccountCommons])


case class OutBoundGetBankAccountFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                         bankId: BankId,
                                         accountId: AccountId)
case class InBoundGetBankAccountFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: BankAccountCommons)


case class OutBoundGetBankAccountsFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                          bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetBankAccountsFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[BankAccountCommons])


case class OutBoundGetCoreBankAccountsFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                              bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetCoreBankAccountsFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[CoreAccount])


case class OutBoundGetCoreBankAccountsHeldFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                  bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetCoreBankAccountsHeldFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[AccountHeld])


case class OutBoundCheckBankAccountExistsFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                 bankId: BankId,
                                                 accountId: AccountId)
case class InBoundCheckBankAccountExistsFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: BankAccountCommons)


case class OutBoundGetCounterpartyTrait  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                         bankId: BankId,
                                         accountId: AccountId,
                                         couterpartyId: String)
case class InBoundGetCounterpartyTrait  (inboundAdapterCallContext: InboundAdapterCallContext, data: CounterpartyTraitCommons)


case class OutBoundGetCounterpartyByCounterpartyIdFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                          counterpartyId: CounterpartyId)
case class InBoundGetCounterpartyByCounterpartyIdFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: CounterpartyTraitCommons)


case class OutBoundGetCounterpartyByIban  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                          iban: String)
case class InBoundGetCounterpartyByIban  (inboundAdapterCallContext: InboundAdapterCallContext, data: CounterpartyTraitCommons)


case class OutBoundGetCounterpartiesFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                            thisBankId: BankId,
                                            thisAccountId: AccountId,
                                            viewId: ViewId)
case class InBoundGetCounterpartiesFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[CounterpartyTraitCommons])


case class OutBoundGetTransactionsFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                          bankId: BankId,
                                          accountId: AccountId,
                                          limit: Int,
                                          fromDate: String,
                                          toDate: String)
case class InBoundGetTransactionsFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[TransactionCommons])


case class OutBoundGetTransactionFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                         bankId: BankId,
                                         accountId: AccountId,
                                         transactionId: TransactionId)
case class InBoundGetTransactionFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionCommons)

case class OutBoundMakePaymentv210  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                    fromAccount: BankAccountCommons,
                                    toAccount: BankAccountCommons,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSONCommons,
                                    amount: BigDecimal,
                                    description: String,
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)

case class InBoundMakePaymentv210  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionId)


case class OutBoundCreateTransactionRequestv210  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                 initiator: User, //TODO FIXME
                                                 viewId: ViewId,
                                                 fromAccount: BankAccountCommons,
                                                 toAccount: BankAccountCommons,
                                                 transactionRequestType: TransactionRequestType,
                                                 transactionRequestCommonBody: TransactionRequestCommonBodyJSONCommons,
                                                 detailsPlain: String,
                                                 chargePolicy: String)
case class InBoundCreateTransactionRequestv210  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionAfterChallengeV210  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                        fromAccount: BankAccountCommons,
                                                        transactionRequest: TransactionRequest)
case class InBoundCreateTransactionAfterChallengeV210  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionRequest)


case class OutBoundGetBranchFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                    bankId: BankId,
                                    branchId: BranchId)
case class InBoundGetBranchFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: BranchTCommons)


case class OutBoundGetBranchesFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                      bankId: BankId, limit: Int, offset: Int, fromDate: String, toDate: String)
case class InBoundGetBranchesFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[BranchTCommons])



case class OutBoundGetAtmFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                 bankId: BankId,
                                 atmId: AtmId)
case class InBoundGetAtmFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: AtmTCommons)


case class OutBoundGetAtmsFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                  bankId: BankId, limit: Int, offset: Int, fromDate: String, toDate: String)
case class InBoundGetAtmsFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[AtmTCommons])


case class OutBoundCreateTransactionAfterChallengev300  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                        initiator: User,       //TODO fixme
                                                        fromAccount: BankAccountCommons,
                                                        transReqId: TransactionRequestId,
                                                        transactionRequestType: TransactionRequestType)
case class InBoundCreateTransactionAfterChallengev300  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionRequest)


case class OutBoundMakePaymentv300  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                    initiator: User,      //TODO fixme
                                    fromAccount: BankAccountCommons,
                                    toAccount: BankAccountCommons,
                                    toCounterparty: CounterpartyTraitCommons,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)
case class InBoundMakePaymentv300  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionId)


case class OutBoundCreateTransactionRequestv300  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                 initiator: User,      //TODO fixme
                                                 viewId: ViewId,
                                                 fromAccount: BankAccountCommons,
                                                 toAccount: BankAccountCommons,
                                                 toCounterparty: CounterpartyTraitCommons,
                                                 transactionRequestType: TransactionRequestType,
                                                 transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
                                                 detailsPlain: String,
                                                 chargePolicy: String)
case class InBoundCreateTransactionRequestv300  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateCustomerFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
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
                                         creditLimit: Option[AmountOfMoney])
case class InBoundCreateCustomerFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: CustomerCommons)


case class OutBoundGetCustomersByUserIdFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                               userId: String)
case class InBoundGetCustomersByUserIdFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[CustomerCommons])


case class OutBoundGetCustomerByCustomerIdFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                  customerId: String)
case class InBoundGetCustomerByCustomerIdFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: CustomerCommons)


case class OutBoundGetCustomerByCustomerNumberFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                      customerNumber: String,
                                                      bankId: BankId)
case class InBoundGetCustomerByCustomerNumberFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: CustomerCommons)


case class OutBoundGetCustomerAddress  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                       customerId: String)
case class InBoundGetCustomerAddress  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[CustomerAddressCommons])


case class OutBoundCreateCustomerAddress  (outboundAdapterCallContext: OutboundAdapterCallContext,
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
                                          status: String)
case class InBoundCreateCustomerAddress  (inboundAdapterCallContext: InboundAdapterCallContext, data: CustomerAddressCommons)


case class OutBoundUpdateCustomerAddress  (outboundAdapterCallContext: OutboundAdapterCallContext,
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
                                          status: String)
case class InBoundUpdateCustomerAddress  (inboundAdapterCallContext: InboundAdapterCallContext, data: CustomerAddressCommons)


case class OutBoundCreateTaxResidence  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                       customerId: String,
                                       domain: String,
                                       taxNumber: String)
case class InBoundCreateTaxResidence  (inboundAdapterCallContext: InboundAdapterCallContext, data: TaxResidenceCommons)


case class OutBoundGetTaxResidence  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                    customerId: String)
case class InBoundGetTaxResidence  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[TaxResidenceCommons])


case class OutBoundGetCustomersFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                       bankId: BankId)
case class InBoundGetCustomersFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[CustomerCommons])


case class OutBoundGetCheckbookOrdersFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                             bankId: String,
                                             accountId: String)
case class InBoundGetCheckbookOrdersFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: CheckbookOrdersJson)


case class OutBoundGetStatusOfCreditCardOrderFuture  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                     bankId: String,
                                                     accountId: String)
case class InBoundGetStatusOfCreditCardOrderFuture  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[CardObjectJson])


case class OutBoundCreateUserAuthContext  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                          userId: String,
                                          key: String,
                                          value: String)
case class InBoundCreateUserAuthContext  (inboundAdapterCallContext: InboundAdapterCallContext, data: UserAuthContextCommons)


case class OutBoundGetUserAuthContexts  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                        userId: String)
case class InBoundGetUserAuthContexts  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[UserAuthContextCommons])


case class OutBoundCreateOrUpdateProductAttribute  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                   bankId: BankId,
                                                   productCode: ProductCode,
                                                   productAttributeId: Option[String],
                                                   name: String,
                                                   attributType: ProductAttributeType.Value,
                                                   value: String)
case class InBoundCreateOrUpdateProductAttribute  (inboundAdapterCallContext: InboundAdapterCallContext, data: ProductAttributeCommons)


case class OutBoundGetProductAttributeById  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                            productAttributeId: String)
case class InBoundGetProductAttributeById  (inboundAdapterCallContext: InboundAdapterCallContext, data: ProductAttributeCommons)


case class OutBoundGetProductAttributesByBankAndCode  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                      bank: BankId,
                                                      productCode: ProductCode)
case class InBoundGetProductAttributesByBankAndCode  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[ProductAttributeCommons])


case class OutBoundCreateOrUpdateAccountAttribute  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                   bankId: BankId,
                                                   accountId: AccountId,
                                                   productCode: ProductCode,
                                                   productAttributeId: Option[String],
                                                   name: String,
                                                   attributType: AccountAttributeType.Value,
                                                   value: String)
case class InBoundCreateOrUpdateAccountAttribute  (inboundAdapterCallContext: InboundAdapterCallContext, data: AccountAttributeCommons)


case class OutBoundCreateAccountApplication  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                             productCode: ProductCode,
                                             userId: Option[String],
                                             customerId: Option[String])
case class InBoundCreateAccountApplication  (inboundAdapterCallContext: InboundAdapterCallContext, data: AccountApplicationCommons)


case class OutBoundGetAllAccountApplication  (outboundAdapterCallContext: OutboundAdapterCallContext)
case class InBoundGetAllAccountApplication  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[AccountApplicationCommons])


case class OutBoundGetAccountApplicationById  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                              accountApplicationId: String)
case class InBoundGetAccountApplicationById  (inboundAdapterCallContext: InboundAdapterCallContext, data: AccountApplicationCommons)


case class OutBoundUpdateAccountApplicationStatus  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                   accountApplicationId: String,
                                                   status: String)
case class InBoundUpdateAccountApplicationStatus  (inboundAdapterCallContext: InboundAdapterCallContext, data: AccountApplicationCommons)


case class OutBoundGetOrCreateProductCollection  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                 collectionCode: String,
                                                 productCodes: List[String])
case class InBoundGetOrCreateProductCollection  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[ProductCollectionCommons])


case class OutBoundGetProductCollection  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                         collectionCode: String)
case class InBoundGetProductCollection  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[ProductCollectionCommons])


case class OutBoundGetOrCreateProductCollectionItem  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                     collectionCode: String,
                                                     memberProductCodes: List[String])
case class InBoundGetOrCreateProductCollectionItem  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[ProductCollectionItemCommons])


case class OutBoundGetProductCollectionItem  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                             collectionCode: String)
case class InBoundGetProductCollectionItem  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[ProductCollectionItemCommons])


case class OutBoundGetProductCollectionItemsTree  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                  collectionCode: String,
                                                  bankId: String)
case class InBoundGetProductCollectionItemsTree  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[(ProductCollectionItemCommons, ProductCommons, List[ProductAttributeCommons])])


case class OutBoundCreateMeeting  (outboundAdapterCallContext: OutboundAdapterCallContext,
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
                                  invitees: List[Invitee])
case class InBoundCreateMeeting  (inboundAdapterCallContext: InboundAdapterCallContext, data: MeetingCommons)


case class OutBoundGetMeetings  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                bankId: BankId,
                                user: User) //TODO fixme
case class InBoundGetMeetings  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[MeetingCommons])


case class OutBoundGetMeeting  (outboundAdapterCallContext: OutboundAdapterCallContext,
                               bankId: BankId,
                               user: User,      //TODO fixme
                               meetingId: String)
case class InBoundGetMeeting  (inboundAdapterCallContext: InboundAdapterCallContext, data: MeetingCommons)

case class OutBoundGetUser (outboundAdapterCallContext: OutboundAdapterCallContext, name: String, password: String)

case class InBoundGetUser (inboundAdapterCallContext: InboundAdapterCallContext, data: InboundUser)


//create bound case classes
case class OutBoundCreateChallenge (outboundAdapterCallContext: OutboundAdapterCallContext, bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String)

case class InBoundCreateChallenge (inboundAdapterCallContext: InboundAdapterCallContext, data: String)

case class OutBoundCreateCounterparty (outboundAdapterCallContext: OutboundAdapterCallContext, name: String, description: String, createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String, otherAccountRoutingScheme: String, otherAccountRoutingAddress: String, otherAccountSecondaryRoutingScheme: String, otherAccountSecondaryRoutingAddress: String, otherBankRoutingScheme: String, otherBankRoutingAddress: String, otherBranchRoutingScheme: String, otherBranchRoutingAddress: String, isBeneficiary: Boolean, bespoke: List[CounterpartyBespoke])

case class InBoundCreateCounterparty (inboundAdapterCallContext: InboundAdapterCallContext, data: CounterpartyTraitCommons)

case class OutBoundGetTransactionRequests210 (outboundAdapterCallContext: OutboundAdapterCallContext, initiator : User, fromAccount : BankAccount)

case class InBoundGetTransactionRequests210 (inboundAdapterCallContext: InboundAdapterCallContext, data: List[TransactionRequest])

case class OutBoundGetTransactionsCore(bankId: BankId, accountID: AccountId, limit: Int, offset: Int, fromDate: String, toDate: String)
case class InBoundGetTransactionsCore (inboundAdapterCallContext: InboundAdapterCallContext, data: List[TransactionCore])

case class OutBoundGetTransactions(bankId: BankId, accountID: AccountId, limit: Int, offset: Int, fromDate: String, toDate: String)
case class InBoundGetTransactions (inboundAdapterCallContext: InboundAdapterCallContext, data: List[Transaction])

//-------- return type are not Future--------------------------------------------------------------------------------------------------

case class OutBoundGetAdapterInfo  (outboundAdapterCallContext: OutboundAdapterCallContext)
case class InBoundGetAdapterInfo  (inboundAdapterCallContext: InboundAdapterCallContext, data: InboundAdapterInfoInternal)


case class OutBoundGetBank  (outboundAdapterCallContext: OutboundAdapterCallContext,
                            bankId: BankId)
case class InBoundGetBank  (inboundAdapterCallContext: InboundAdapterCallContext, data: BankCommons)


case class OutBoundGetBanks  (outboundAdapterCallContext: OutboundAdapterCallContext)
case class InBoundGetBanks  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[BankCommons])


case class OutBoundGetBankAccounts  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                    accounts: List[(BankId, AccountId)])
case class InBoundGetBankAccounts  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[BankAccountCommons])


case class OutBoundGetBankAccountsForUser  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                              username: String)
case class InBoundGetBankAccountsForUser  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[InboundAccountCommons])


case class OutBoundGetCoreBankAccounts  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                        bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetCoreBankAccounts  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[CoreAccount])


case class OutBoundGetBankAccountsHeld  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                        bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetBankAccountsHeld  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[AccountHeld])


case class OutBoundCheckBankAccountExists  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                           bankId: BankId,
                                           accountId: AccountId)
case class InBoundCheckBankAccountExists  (inboundAdapterCallContext: InboundAdapterCallContext, data: BankAccountCommons)


case class OutBoundGetEmptyBankAccount  (outboundAdapterCallContext: OutboundAdapterCallContext)
case class InBoundGetEmptyBankAccount  (inboundAdapterCallContext: InboundAdapterCallContext, data: BankAccountCommons)


case class OutBoundGetCounterpartyFromTransaction  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                   bankId: BankId,
                                                   accountId: AccountId,
                                                   counterpartyId: String)
case class InBoundGetCounterpartyFromTransaction  (inboundAdapterCallContext: InboundAdapterCallContext, data: Counterparty)


case class OutBoundGetCounterpartiesFromTransaction  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                     bankId: BankId,
                                                     accountId: AccountId)
case class InBoundGetCounterpartiesFromTransaction  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[Counterparty])


case class OutBoundGetCounterparty  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                    thisBankId: BankId,
                                    thisAccountId: AccountId,
                                    couterpartyId: String)
case class InBoundGetCounterparty  (inboundAdapterCallContext: InboundAdapterCallContext, data: Counterparty)


case class OutBoundGetCounterpartyByCounterpartyId  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                    counterpartyId: CounterpartyId)
case class InBoundGetCounterpartyByCounterpartyId  (inboundAdapterCallContext: InboundAdapterCallContext, data: CounterpartyTraitCommons)


case class OutBoundGetCounterparties  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                      thisBankId: BankId,
                                      thisAccountId: AccountId,
                                      viewId: ViewId)
case class InBoundGetCounterparties  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[CounterpartyTraitCommons])


case class OutBoundGetTransaction  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                   bankId: BankId,
                                   accountID: AccountId,
                                   transactionId: TransactionId)
case class InBoundGetTransaction  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionCommons)


case class OutBoundGetPhysicalCards  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                     user: User)
case class InBoundGetPhysicalCards  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[PhysicalCard])


case class OutBoundGetPhysicalCardsForBank  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                            bank: Bank,
                                            user: User)
case class InBoundGetPhysicalCardsForBank  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[PhysicalCard])


case class OutBoundCreateOrUpdatePhysicalCard  (outboundAdapterCallContext: OutboundAdapterCallContext,
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
                                               posted: Option[CardPostedInfo])
case class InBoundCreateOrUpdatePhysicalCard  (inboundAdapterCallContext: InboundAdapterCallContext, data: PhysicalCard)


case class OutBoundMakePayment  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                initiator: User,
                                fromAccountUID: BankIdAccountId,
                                toAccountUID: BankIdAccountId,
                                amt: BigDecimal,
                                description: String,
                                transactionRequestType: TransactionRequestType)
case class InBoundMakePayment  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionId)


case class OutBoundMakePaymentv200  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                    fromAccount: BankAccount,
                                    toAccount: BankAccount,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                    amount: BigDecimal,
                                    description: String,
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)
case class InBoundMakePaymentv200  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionId)


case class OutBoundMakePaymentImpl  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                    fromAccount: BankAccount,
                                    toAccount: BankAccount,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                    amt: BigDecimal,
                                    description: String,
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)
case class InBoundMakePaymentImpl  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionId)


case class OutBoundCreateTransactionRequest  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                             initiator: User,
                                             fromAccount: BankAccount,
                                             toAccount: BankAccount,
                                             transactionRequestType: TransactionRequestType,
                                             body: TransactionRequestBody)
case class InBoundCreateTransactionRequest  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionRequestv200  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                 initiator: User,
                                                 fromAccount: BankAccount,
                                                 toAccount: BankAccount,
                                                 transactionRequestType: TransactionRequestType,
                                                 body: TransactionRequestBody)
case class InBoundCreateTransactionRequestv200  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionRequestImpl  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                 transactionRequestId: TransactionRequestId,
                                                 transactionRequestType: TransactionRequestType,
                                                 fromAccount: BankAccount,
                                                 counterparty: BankAccount,
                                                 body: TransactionRequestBody,
                                                 status: String,
                                                 charge: TransactionRequestCharge)
case class InBoundCreateTransactionRequestImpl  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionRequestImpl210  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                    transactionRequestId: TransactionRequestId,
                                                    transactionRequestType: TransactionRequestType,
                                                    fromAccount: BankAccount,
                                                    toAccount: BankAccount,
                                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                                    details: String,
                                                    status: String,
                                                    charge: TransactionRequestCharge,
                                                    chargePolicy: String)
case class InBoundCreateTransactionRequestImpl210  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionRequest)


case class OutBoundGetTransactionRequests  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                           initiator: User,
                                           fromAccount: BankAccount)
case class InBoundGetTransactionRequests  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[TransactionRequest])


case class OutBoundGetTransactionRequestStatuses  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                 )
case class InBoundGetTransactionRequestStatuses  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionRequestStatusCommons)


case class OutBoundGetTransactionRequestStatusesImpl  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                     )
case class InBoundGetTransactionRequestStatusesImpl  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionRequestStatusCommons)


case class OutBoundGetTransactionRequestsImpl  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                               fromAccount: BankAccount)
case class InBoundGetTransactionRequestsImpl  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[TransactionRequest])


case class OutBoundGetTransactionRequestsImpl210  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                  fromAccount: BankAccount)
case class InBoundGetTransactionRequestsImpl210  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[TransactionRequest])


case class OutBoundGetTransactionRequestImpl  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                              transactionRequestId: TransactionRequestId)
case class InBoundGetTransactionRequestImpl  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionRequest)


case class OutBoundGetTransactionRequestTypes  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                               initiator: User,
                                               fromAccount: BankAccount)
case class InBoundGetTransactionRequestTypes  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[TransactionRequestType])


case class OutBoundGetTransactionRequestTypesImpl  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                   fromAccount: BankAccount)
case class InBoundGetTransactionRequestTypesImpl  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[TransactionRequestType])


case class OutBoundCreateTransactionAfterChallenge  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                    initiator: User,
                                                    transReqId: TransactionRequestId)
case class InBoundCreateTransactionAfterChallenge  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionAfterChallengev200  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                        fromAccount: BankAccount,
                                                        toAccount: BankAccount,
                                                        transactionRequest: TransactionRequest)
case class InBoundCreateTransactionAfterChallengev200  (inboundAdapterCallContext: InboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateBankAndAccount  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                         bankName: String,
                                         bankNationalIdentifier: String,
                                         accountNumber: String,
                                         accountType: String,
                                         accountLabel: String,
                                         currency: String,
                                         accountHolderName: String,
                                         branchId: String,
                                         accountRoutingScheme: String,
                                         accountRoutingAddress: String)
case class InBoundCreateBankAndAccount  (inboundAdapterCallContext: InboundAdapterCallContext, data: (BankCommons, BankAccountCommons))


case class OutBoundGetProducts  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                bankId: BankId)
case class InBoundGetProducts  (inboundAdapterCallContext: InboundAdapterCallContext, data: List[ProductCommons])


case class OutBoundGetProduct  (outboundAdapterCallContext: OutboundAdapterCallContext,
                               bankId: BankId,
                               productCode: ProductCode)
case class InBoundGetProduct  (inboundAdapterCallContext: InboundAdapterCallContext, data: ProductCommons)


case class OutBoundCreateOrUpdateBank  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                       bankId: String,
                                       fullBankName: String,
                                       shortBankName: String,
                                       logoURL: String,
                                       websiteURL: String,
                                       swiftBIC: String,
                                       national_identifier: String,
                                       bankRoutingScheme: String,
                                       bankRoutingAddress: String)
case class InBoundCreateOrUpdateBank  (inboundAdapterCallContext: InboundAdapterCallContext, data: BankCommons)


case class OutBoundCreateOrUpdateProduct  (outboundAdapterCallContext: OutboundAdapterCallContext,
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
                                          metaLicenceName: String)
case class InBoundCreateOrUpdateProduct  (inboundAdapterCallContext: InboundAdapterCallContext, data: ProductCommons)


case class OutBoundGetBranch  (outboundAdapterCallContext: OutboundAdapterCallContext,
                              bankId: BankId,
                              branchId: BranchId)
case class InBoundGetBranch  (inboundAdapterCallContext: InboundAdapterCallContext, data: BranchTCommons)


case class OutBoundGetAtm  (outboundAdapterCallContext: OutboundAdapterCallContext,
                           bankId: BankId,
                           atmId: AtmId)
case class InBoundGetAtm  (inboundAdapterCallContext: InboundAdapterCallContext, data: AtmTCommons)


case class OutBoundGetTransactionRequestTypeCharge  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                                    bankId: BankId,
                                                    accountId: AccountId,
                                                    viewId: ViewId,
                                                    transactionRequestType: TransactionRequestType)


case class OutBoundGetCustomerByCustomerId  (outboundAdapterCallContext: OutboundAdapterCallContext,
                                            customerId: String)
case class InBoundGetCustomerByCustomerId  (inboundAdapterCallContext: InboundAdapterCallContext, data: CustomerCommons)