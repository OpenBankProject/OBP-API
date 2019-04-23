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


case class OutBoundGetObpApiLoopback (adapterCallContext: OutboundAdapterCallContext)
case class InBoundGetObpApiLoopback (adapterCallContext: InboundAdapterCallContext, data: ObpApiLoopback)


case class OutBoundGetAdapterInfoFuture (adapterCallContext: OutboundAdapterCallContext)
case class InBoundGetAdapterInfoFuture (adapterCallContext: InboundAdapterCallContext, data: InboundAdapterInfoInternal)


case class OutBoundGetChallengeThreshold (adapterCallContext: OutboundAdapterCallContext,
                                          bankId: String,
                                          accountId: String,
                                          viewId: String,
                                          transactionRequestType: String,
                                          currency: String,
                                          userId: String,
                                          userName: String)
case class InBoundGetChallengeThreshold (adapterCallContext: InboundAdapterCallContext, data: AmountOfMoney)


case class OutBoundGetChargeLevel (adapterCallContext: OutboundAdapterCallContext,
                                   bankId: BankId,
                                   accountId: AccountId,
                                   viewId: ViewId,
                                   userId: String,
                                   userName: String,
                                   transactionRequestType: String,
                                   currency: String)
case class InBoundGetChargeLevel (adapterCallContext: InboundAdapterCallContext, data: AmountOfMoney)


case class OutBoundGetBankFuture (adapterCallContext: OutboundAdapterCallContext,
                                  bankId: BankId)
case class InBoundGetBankFuture (adapterCallContext: InboundAdapterCallContext, data: BankCommons)


case class OutBoundGetBanksFuture (outboundAdapterCallContext: OutboundAdapterCallContext)
case class InBoundGetBanksFuture (inboundAdapterCallContext: InboundAdapterCallContext, data: List[BankCommons])

case class OutBoundGetBankAccountsByUsernameFuture (adapterCallContext: OutboundAdapterCallContext,
                                          username: String)
case class InBoundGetBankAccountsByUsernameFuture (adapterCallContext: InboundAdapterCallContext, data: List[InboundAccountCommonCommons])


case class OutBoundGetBankAccountFuture (adapterCallContext: OutboundAdapterCallContext,
                                         bankId: BankId,
                                         accountId: AccountId)
case class InBoundGetBankAccountFuture (adapterCallContext: InboundAdapterCallContext, data: BankAccountCommons)


case class OutBoundGetBankAccountsFuture (adapterCallContext: OutboundAdapterCallContext,
                                          bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetBankAccountsFuture (adapterCallContext: InboundAdapterCallContext, data: List[BankAccountCommons])


case class OutBoundGetCoreBankAccountsFuture (adapterCallContext: OutboundAdapterCallContext,
                                              bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetCoreBankAccountsFuture (adapterCallContext: InboundAdapterCallContext, data: List[CoreAccount])


case class OutBoundGetCoreBankAccountsHeldFuture (adapterCallContext: OutboundAdapterCallContext,
                                                  bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetCoreBankAccountsHeldFuture (adapterCallContext: InboundAdapterCallContext, data: List[AccountHeld])


case class OutBoundCheckBankAccountExistsFuture (adapterCallContext: OutboundAdapterCallContext,
                                                 bankId: BankId,
                                                 accountId: AccountId)
case class InBoundCheckBankAccountExistsFuture (adapterCallContext: InboundAdapterCallContext, data: BankAccountCommons)


case class OutBoundGetCounterpartyTrait (adapterCallContext: OutboundAdapterCallContext,
                                         bankId: BankId,
                                         accountId: AccountId,
                                         couterpartyId: String)
case class InBoundGetCounterpartyTrait (adapterCallContext: InboundAdapterCallContext, data: CounterpartyTraitCommons)


case class OutBoundGetCounterpartyByCounterpartyIdFuture (adapterCallContext: OutboundAdapterCallContext,
                                                          counterpartyId: CounterpartyId)
case class InBoundGetCounterpartyByCounterpartyIdFuture (adapterCallContext: InboundAdapterCallContext, data: CounterpartyTraitCommons)


case class OutBoundGetCounterpartyByIban (adapterCallContext: OutboundAdapterCallContext,
                                          iban: String)
case class InBoundGetCounterpartyByIban (adapterCallContext: InboundAdapterCallContext, data: CounterpartyTraitCommons)


case class OutBoundGetCounterpartiesFuture (adapterCallContext: OutboundAdapterCallContext,
                                            thisBankId: BankId,
                                            thisAccountId: AccountId,
                                            viewId: ViewId)
case class InBoundGetCounterpartiesFuture (adapterCallContext: InboundAdapterCallContext, data: List[CounterpartyTraitCommons])


case class OutBoundGetTransactionsFuture (adapterCallContext: OutboundAdapterCallContext,
                                          bankId: BankId,
                                          accountId: AccountId,
                                          limit: Int,
                                          fromDate: String,
                                          toDate: String)
case class InBoundGetTransactionsFuture (adapterCallContext: InboundAdapterCallContext, data: List[TransactionCommons])


case class OutBoundGetTransactionFuture (adapterCallContext: OutboundAdapterCallContext,
                                         bankId: BankId,
                                         accountId: AccountId,
                                         transactionId: TransactionId)
case class InBoundGetTransactionFuture (adapterCallContext: OutboundAdapterCallContext, data: TransactionCommons)


case class OutBoundMakePaymentv210 (adapterCallContext: OutboundAdapterCallContext,
                                    fromAccount: BankAccount,
                                    toAccount: BankAccount,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                    amount: BigDecimal,
                                    description: String,
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)
case class OutBoundMakePaymentv210 (adapterCallContext: OutboundAdapterCallContext,
                                    fromAccount: BankAccountCommons,
                                    toAccount: BankAccountCommons,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSONCommons,
                                    amount: BigDecimal,
                                    description: String,
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String) {

  def this(adapterCallContext: OutboundAdapterCallContext,
           fromAccount: BankAccount,
           toAccount: BankAccount,
           transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
           amount: BigDecimal,
           description: String,
           transactionRequestType: TransactionRequestType,
           chargePolicy: String) = this(
    adapterCallContext,
    BankAccountCommons(fromAccount),
    BankAccountCommons(toAccount),
    TransactionRequestCommonBodyJSONCommons(transactionRequestCommonBody),
    amount: BigDecimal,
    description: String,
    transactionRequestType: TransactionRequestType,
    chargePolicy)
}

case class InBoundMakePaymentv210 (adapterCallContext: OutboundAdapterCallContext, data: TransactionId)


case class OutBoundCreateTransactionRequestv210 (adapterCallContext: OutboundAdapterCallContext,
                                                 initiator: User, //TODO FIXME
                                                 viewId: ViewId,
                                                 fromAccount: BankAccountCommons,
                                                 toAccount: BankAccountCommons,
                                                 transactionRequestType: TransactionRequestType,
                                                 transactionRequestCommonBody: TransactionRequestCommonBodyJSONCommons,
                                                 detailsPlain: String,
                                                 chargePolicy: String)
case class InBoundCreateTransactionRequestv210 (adapterCallContext: OutboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionAfterChallengeV210 (adapterCallContext: OutboundAdapterCallContext,
                                                        fromAccount: BankAccountCommons,
                                                        transactionRequest: TransactionRequest)
case class InBoundCreateTransactionAfterChallengeV210 (adapterCallContext: OutboundAdapterCallContext, data: TransactionRequest)


case class OutBoundGetBranchFuture (adapterCallContext: OutboundAdapterCallContext,
                                    bankId: BankId,
                                    branchId: BranchId)
case class InBoundGetBranchFuture (adapterCallContext: OutboundAdapterCallContext, data: BranchTCommons)


case class OutBoundGetBranchesFuture (adapterCallContext: OutboundAdapterCallContext,
                                      bankId: BankId, limit: Int, offset: Int, fromDate: String, toDate: String)
case class InBoundGetBranchesFuture (adapterCallContext: OutboundAdapterCallContext, data: List[BranchTCommons])
case class OutBoundGetBranchesFuture (adapterCallContext: OutboundAdapterCallContext,
                                      bankId: BankId)
case class InBoundGetBranchesFuture (adapterCallContext: OutboundAdapterCallContext, data: List[BranchTCommons])


case class OutBoundGetAtmFuture (adapterCallContext: OutboundAdapterCallContext,
                                 bankId: BankId,
                                 atmId: AtmId)
case class InBoundGetAtmFuture (adapterCallContext: OutboundAdapterCallContext, data: AtmTCommons)


case class OutBoundGetAtmsFuture (adapterCallContext: OutboundAdapterCallContext,
                                  bankId: BankId, limit: Int, offset: Int, fromDate: String, toDate: String)
case class InBoundGetAtmsFuture (adapterCallContext: OutboundAdapterCallContext, data: List[AtmTCommons])
case class OutBoundGetAtmsFuture (adapterCallContext: OutboundAdapterCallContext,
                                  bankId: BankId)
case class InBoundGetAtmsFuture (adapterCallContext: OutboundAdapterCallContext, data: List[AtmTCommons])


case class OutBoundCreateTransactionAfterChallengev300 (adapterCallContext: OutboundAdapterCallContext,
                                                        initiator: User,       //TODO fixme
                                                        fromAccount: BankAccountCommons,
                                                        transReqId: TransactionRequestId,
                                                        transactionRequestType: TransactionRequestType)
case class InBoundCreateTransactionAfterChallengev300 (adapterCallContext: OutboundAdapterCallContext, data: TransactionRequest)


case class OutBoundMakePaymentv300 (adapterCallContext: OutboundAdapterCallContext,
                                    initiator: User,      //TODO fixme
                                    fromAccount: BankAccountCommons,
                                    toAccount: BankAccountCommons,
                                    toCounterparty: CounterpartyTraitCommons,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)
case class InBoundMakePaymentv300 (adapterCallContext: OutboundAdapterCallContext, data: TransactionId)


case class OutBoundCreateTransactionRequestv300 (adapterCallContext: OutboundAdapterCallContext,
                                                 initiator: User,      //TODO fixme
                                                 viewId: ViewId,
                                                 fromAccount: BankAccountCommons,
                                                 toAccount: BankAccountCommons,
                                                 toCounterparty: CounterpartyTraitCommons,
                                                 transactionRequestType: TransactionRequestType,
                                                 transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
                                                 detailsPlain: String,
                                                 chargePolicy: String)
case class InBoundCreateTransactionRequestv300 (adapterCallContext: OutboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateCustomerFuture (adapterCallContext: OutboundAdapterCallContext,
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
case class InBoundCreateCustomerFuture (adapterCallContext: OutboundAdapterCallContext, data: CustomerCommons)


case class OutBoundGetCustomersByUserIdFuture (adapterCallContext: OutboundAdapterCallContext,
                                               userId: String)
case class InBoundGetCustomersByUserIdFuture (adapterCallContext: OutboundAdapterCallContext, data: List[CustomerCommons])


case class OutBoundGetCustomerByCustomerIdFuture (adapterCallContext: OutboundAdapterCallContext,
                                                  customerId: String)
case class InBoundGetCustomerByCustomerIdFuture (adapterCallContext: OutboundAdapterCallContext, data: CustomerCommons)


case class OutBoundGetCustomerByCustomerNumberFuture (adapterCallContext: OutboundAdapterCallContext,
                                                      customerNumber: String,
                                                      bankId: BankId)
case class InBoundGetCustomerByCustomerNumberFuture (adapterCallContext: OutboundAdapterCallContext, data: CustomerCommons)


case class OutBoundGetCustomerAddress (adapterCallContext: OutboundAdapterCallContext,
                                       customerId: String)
case class InBoundGetCustomerAddress (adapterCallContext: OutboundAdapterCallContext, data: List[CustomerAddressCommons])


case class OutBoundCreateCustomerAddress (adapterCallContext: OutboundAdapterCallContext,
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
case class InBoundCreateCustomerAddress (adapterCallContext: OutboundAdapterCallContext, data: CustomerAddressCommons)


case class OutBoundUpdateCustomerAddress (adapterCallContext: OutboundAdapterCallContext,
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
case class InBoundUpdateCustomerAddress (adapterCallContext: OutboundAdapterCallContext, data: CustomerAddressCommons)


case class OutBoundCreateTaxResidence (adapterCallContext: OutboundAdapterCallContext,
                                       customerId: String,
                                       domain: String,
                                       taxNumber: String)
case class InBoundCreateTaxResidence (adapterCallContext: OutboundAdapterCallContext, data: TaxResidenceCommons)


case class OutBoundGetTaxResidence (adapterCallContext: OutboundAdapterCallContext,
                                    customerId: String)
case class InBoundGetTaxResidence (adapterCallContext: OutboundAdapterCallContext, data: List[TaxResidenceCommons])


case class OutBoundGetCustomersFuture (adapterCallContext: OutboundAdapterCallContext,
                                       bankId: BankId)
case class InBoundGetCustomersFuture (adapterCallContext: OutboundAdapterCallContext, data: List[CustomerCommons])


case class OutBoundGetCheckbookOrdersFuture (adapterCallContext: OutboundAdapterCallContext,
                                             bankId: String,
                                             accountId: String)
case class InBoundGetCheckbookOrdersFuture (adapterCallContext: OutboundAdapterCallContext, data: CheckbookOrdersJson)


case class OutBoundGetStatusOfCreditCardOrderFuture (adapterCallContext: OutboundAdapterCallContext,
                                                     bankId: String,
                                                     accountId: String)
case class InBoundGetStatusOfCreditCardOrderFuture (adapterCallContext: OutboundAdapterCallContext, data: List[CardObjectJson])


case class OutBoundCreateUserAuthContext (adapterCallContext: OutboundAdapterCallContext,
                                          userId: String,
                                          key: String,
                                          value: String)
case class InBoundCreateUserAuthContext (adapterCallContext: OutboundAdapterCallContext, data: UserAuthContextCommons)


case class OutBoundGetUserAuthContexts (adapterCallContext: OutboundAdapterCallContext,
                                        userId: String)
case class InBoundGetUserAuthContexts (adapterCallContext: OutboundAdapterCallContext, data: List[UserAuthContextCommons])


case class OutBoundCreateOrUpdateProductAttribute (adapterCallContext: OutboundAdapterCallContext,
                                                   bankId: BankId,
                                                   productCode: ProductCode,
                                                   productAttributeId: Option[String],
                                                   name: String,
                                                   attributType: ProductAttributeType.Value,
                                                   value: String)
case class InBoundCreateOrUpdateProductAttribute (adapterCallContext: OutboundAdapterCallContext, data: ProductAttributeCommons)


case class OutBoundGetProductAttributeById (adapterCallContext: OutboundAdapterCallContext,
                                            productAttributeId: String)
case class InBoundGetProductAttributeById (adapterCallContext: OutboundAdapterCallContext, data: ProductAttributeCommons)


case class OutBoundGetProductAttributesByBankAndCode (adapterCallContext: OutboundAdapterCallContext,
                                                      bank: BankId,
                                                      productCode: ProductCode)
case class InBoundGetProductAttributesByBankAndCode (adapterCallContext: OutboundAdapterCallContext, data: List[ProductAttributeCommons])


case class OutBoundCreateOrUpdateAccountAttribute (adapterCallContext: OutboundAdapterCallContext,
                                                   bankId: BankId,
                                                   accountId: AccountId,
                                                   productCode: ProductCode,
                                                   productAttributeId: Option[String],
                                                   name: String,
                                                   attributType: AccountAttributeType.Value,
                                                   value: String)
case class InBoundCreateOrUpdateAccountAttribute (adapterCallContext: OutboundAdapterCallContext, data: AccountAttributeCommons)


case class OutBoundCreateAccountApplication (adapterCallContext: OutboundAdapterCallContext,
                                             productCode: ProductCode,
                                             userId: Option[String],
                                             customerId: Option[String])
case class InBoundCreateAccountApplication (adapterCallContext: OutboundAdapterCallContext, data: AccountApplicationCommons)


case class OutBoundGetAllAccountApplication (adapterCallContext: OutboundAdapterCallContext)
case class InBoundGetAllAccountApplication (adapterCallContext: OutboundAdapterCallContext, data: List[AccountApplicationCommons])


case class OutBoundGetAccountApplicationById (adapterCallContext: OutboundAdapterCallContext,
                                              accountApplicationId: String)
case class InBoundGetAccountApplicationById (adapterCallContext: OutboundAdapterCallContext, data: AccountApplicationCommons)


case class OutBoundUpdateAccountApplicationStatus (adapterCallContext: OutboundAdapterCallContext,
                                                   accountApplicationId: String,
                                                   status: String)
case class InBoundUpdateAccountApplicationStatus (adapterCallContext: OutboundAdapterCallContext, data: AccountApplicationCommons)


case class OutBoundGetOrCreateProductCollection (adapterCallContext: OutboundAdapterCallContext,
                                                 collectionCode: String,
                                                 productCodes: List[String])
case class InBoundGetOrCreateProductCollection (adapterCallContext: OutboundAdapterCallContext, data: List[ProductCollectionCommons])


case class OutBoundGetProductCollection (adapterCallContext: OutboundAdapterCallContext,
                                         collectionCode: String)
case class InBoundGetProductCollection (adapterCallContext: OutboundAdapterCallContext, data: List[ProductCollectionCommons])


case class OutBoundGetOrCreateProductCollectionItem (adapterCallContext: OutboundAdapterCallContext,
                                                     collectionCode: String,
                                                     memberProductCodes: List[String])
case class InBoundGetOrCreateProductCollectionItem (adapterCallContext: OutboundAdapterCallContext, data: List[ProductCollectionItemCommons])


case class OutBoundGetProductCollectionItem (adapterCallContext: OutboundAdapterCallContext,
                                             collectionCode: String)
case class InBoundGetProductCollectionItem (adapterCallContext: OutboundAdapterCallContext, data: List[ProductCollectionItemCommons])


case class OutBoundGetProductCollectionItemsTree (adapterCallContext: OutboundAdapterCallContext,
                                                  collectionCode: String,
                                                  bankId: String)
case class InBoundGetProductCollectionItemsTree (adapterCallContext: OutboundAdapterCallContext, data: List[(ProductCollectionItemCommons, ProductCommons, List[ProductAttributeCommons])])


case class OutBoundCreateMeeting (adapterCallContext: OutboundAdapterCallContext,
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
case class InBoundCreateMeeting (adapterCallContext: OutboundAdapterCallContext, data: MeetingCommons)


case class OutBoundGetMeetings (adapterCallContext: OutboundAdapterCallContext,
                                bankId: BankId,
                                user: User) //TODO fixme
case class InBoundGetMeetings (adapterCallContext: OutboundAdapterCallContext, data: List[MeetingCommons])


case class OutBoundGetMeeting (adapterCallContext: OutboundAdapterCallContext,
                               bankId: BankId,
                               user: User,      //TODO fixme
                               meetingId: String)
case class InBoundGetMeeting (adapterCallContext: OutboundAdapterCallContext, data: MeetingCommons)

case class OutBoundGetUser(adapterCallContext: OutboundAdapterCallContext, name: String, password: String)

case class InBoundGetUser(adapterCallContext: OutboundAdapterCallContext, data: InboundUser)


//create bound case classes
case class OutBoundCreateChallenge(adapterCallContext: OutboundAdapterCallContext, bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String)

case class InBoundCreateChallenge(adapterCallContext: OutboundAdapterCallContext, data: String)

case class OutBoundCreateCounterparty(adapterCallContext: OutboundAdapterCallContext, name: String, description: String, createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String, otherAccountRoutingScheme: String, otherAccountRoutingAddress: String, otherAccountSecondaryRoutingScheme: String, otherAccountSecondaryRoutingAddress: String, otherBankRoutingScheme: String, otherBankRoutingAddress: String, otherBranchRoutingScheme: String, otherBranchRoutingAddress: String, isBeneficiary: Boolean, bespoke: List[CounterpartyBespoke])

case class InBoundCreateCounterparty(adapterCallContext: OutboundAdapterCallContext, data: CounterpartyTraitCommons)

case class OutBoundGetTransactionRequests210(adapterCallContext: OutboundAdapterCallContext, initiator : User, fromAccount : BankAccount)

case class InBoundGetTransactionRequests210(adapterCallContext: OutboundAdapterCallContext, data: List[TransactionRequest])

case class OutBoundGetTransactionsCore(bankId: BankId, accountID: AccountId, limit: Int, offset: Int, fromDate: String, toDate: String)
case class InBoundGetTransactionsCore(adapterCallContext: OutboundAdapterCallContext, data: List[TransactionCore])

case class OutBoundGetTransactions(bankId: BankId, accountID: AccountId, limit: Int, offset: Int, fromDate: String, toDate: String)
case class InBoundGetTransactions(adapterCallContext: OutboundAdapterCallContext, data: List[Transaction])

//-------- return type are not Future--------------------------------------------------------------------------------------------------

case class OutBoundGetAdapterInfo (adapterCallContext: OutboundAdapterCallContext)
case class InBoundGetAdapterInfo (adapterCallContext: OutboundAdapterCallContext, data: InboundAdapterInfoInternal)


case class OutBoundGetBank (adapterCallContext: OutboundAdapterCallContext,
                            bankId: BankId)
case class InBoundGetBank (adapterCallContext: OutboundAdapterCallContext, data: BankCommons)


case class OutBoundGetBanks (adapterCallContext: OutboundAdapterCallContext)
case class InBoundGetBanks (adapterCallContext: OutboundAdapterCallContext, data: List[BankCommons])


case class OutBoundGetBankAccounts (adapterCallContext: OutboundAdapterCallContext,
                                    accounts: List[(BankId, AccountId)])
case class InBoundGetBankAccounts (adapterCallContext: OutboundAdapterCallContext, data: List[BankAccountCommons])


case class OutBoundGetBankAccountsByUsername (adapterCallContext: OutboundAdapterCallContext,
                                              username: String)
case class InBoundGetBankAccountsByUsername (adapterCallContext: OutboundAdapterCallContext, data: List[InboundAccountCommonCommons])


case class OutBoundGetCoreBankAccounts (adapterCallContext: OutboundAdapterCallContext,
                                        bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetCoreBankAccounts (adapterCallContext: OutboundAdapterCallContext, data: List[CoreAccount])


case class OutBoundGetBankAccountsHeld (adapterCallContext: OutboundAdapterCallContext,
                                        bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetBankAccountsHeld (adapterCallContext: OutboundAdapterCallContext, data: List[AccountHeld])


case class OutBoundCheckBankAccountExists (adapterCallContext: OutboundAdapterCallContext,
                                           bankId: BankId,
                                           accountId: AccountId)
case class InBoundCheckBankAccountExists (adapterCallContext: OutboundAdapterCallContext, data: BankAccountCommons)


case class OutBoundGetEmptyBankAccount (adapterCallContext: OutboundAdapterCallContext)
case class InBoundGetEmptyBankAccount (adapterCallContext: OutboundAdapterCallContext, data: BankAccountCommons)


case class OutBoundGetCounterpartyFromTransaction (adapterCallContext: OutboundAdapterCallContext,
                                                   bankId: BankId,
                                                   accountId: AccountId,
                                                   counterpartyId: String)
case class InBoundGetCounterpartyFromTransaction (adapterCallContext: OutboundAdapterCallContext, data: Counterparty)


case class OutBoundGetCounterpartiesFromTransaction (adapterCallContext: OutboundAdapterCallContext,
                                                     bankId: BankId,
                                                     accountId: AccountId)
case class InBoundGetCounterpartiesFromTransaction (adapterCallContext: OutboundAdapterCallContext, data: List[Counterparty])


case class OutBoundGetCounterparty (adapterCallContext: OutboundAdapterCallContext,
                                    thisBankId: BankId,
                                    thisAccountId: AccountId,
                                    couterpartyId: String)
case class InBoundGetCounterparty (adapterCallContext: OutboundAdapterCallContext, data: Counterparty)


case class OutBoundGetCounterpartyByCounterpartyId (adapterCallContext: OutboundAdapterCallContext,
                                                    counterpartyId: CounterpartyId)
case class InBoundGetCounterpartyByCounterpartyId (adapterCallContext: OutboundAdapterCallContext, data: CounterpartyTraitCommons)


case class OutBoundGetCounterparties (adapterCallContext: OutboundAdapterCallContext,
                                      thisBankId: BankId,
                                      thisAccountId: AccountId,
                                      viewId: ViewId)
case class InBoundGetCounterparties (adapterCallContext: OutboundAdapterCallContext, data: List[CounterpartyTraitCommons])


case class OutBoundGetTransaction (adapterCallContext: OutboundAdapterCallContext,
                                   bankId: BankId,
                                   accountID: AccountId,
                                   transactionId: TransactionId)
case class InBoundGetTransaction (adapterCallContext: OutboundAdapterCallContext, data: TransactionCommons)


case class OutBoundGetPhysicalCards (adapterCallContext: OutboundAdapterCallContext,
                                     user: User)
case class InBoundGetPhysicalCards (adapterCallContext: OutboundAdapterCallContext, data: List[PhysicalCard])


case class OutBoundGetPhysicalCardsForBank (adapterCallContext: OutboundAdapterCallContext,
                                            bank: Bank,
                                            user: User)
case class InBoundGetPhysicalCardsForBank (adapterCallContext: OutboundAdapterCallContext, data: List[PhysicalCard])


case class OutBoundCreateOrUpdatePhysicalCard (adapterCallContext: OutboundAdapterCallContext,
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
case class InBoundCreateOrUpdatePhysicalCard (adapterCallContext: OutboundAdapterCallContext, data: PhysicalCard)


case class OutBoundMakePayment (adapterCallContext: OutboundAdapterCallContext,
                                initiator: User,
                                fromAccountUID: BankIdAccountId,
                                toAccountUID: BankIdAccountId,
                                amt: BigDecimal,
                                description: String,
                                transactionRequestType: TransactionRequestType)
case class InBoundMakePayment (adapterCallContext: OutboundAdapterCallContext, data: TransactionId)


case class OutBoundMakePaymentv200 (adapterCallContext: OutboundAdapterCallContext,
                                    fromAccount: BankAccount,
                                    toAccount: BankAccount,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                    amount: BigDecimal,
                                    description: String,
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)
case class InBoundMakePaymentv200 (adapterCallContext: OutboundAdapterCallContext, data: TransactionId)


case class OutBoundMakePaymentImpl (adapterCallContext: OutboundAdapterCallContext,
                                    fromAccount: BankAccount,
                                    toAccount: BankAccount,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                    amt: BigDecimal,
                                    description: String,
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)
case class InBoundMakePaymentImpl (adapterCallContext: OutboundAdapterCallContext, data: TransactionId)


case class OutBoundCreateTransactionRequest (adapterCallContext: OutboundAdapterCallContext,
                                             initiator: User,
                                             fromAccount: BankAccount,
                                             toAccount: BankAccount,
                                             transactionRequestType: TransactionRequestType,
                                             body: TransactionRequestBody)
case class InBoundCreateTransactionRequest (adapterCallContext: OutboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionRequestv200 (adapterCallContext: OutboundAdapterCallContext,
                                                 initiator: User,
                                                 fromAccount: BankAccount,
                                                 toAccount: BankAccount,
                                                 transactionRequestType: TransactionRequestType,
                                                 body: TransactionRequestBody)
case class InBoundCreateTransactionRequestv200 (adapterCallContext: OutboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionRequestImpl (adapterCallContext: OutboundAdapterCallContext,
                                                 transactionRequestId: TransactionRequestId,
                                                 transactionRequestType: TransactionRequestType,
                                                 fromAccount: BankAccount,
                                                 counterparty: BankAccount,
                                                 body: TransactionRequestBody,
                                                 status: String,
                                                 charge: TransactionRequestCharge)
case class InBoundCreateTransactionRequestImpl (adapterCallContext: OutboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionRequestImpl210 (adapterCallContext: OutboundAdapterCallContext,
                                                    transactionRequestId: TransactionRequestId,
                                                    transactionRequestType: TransactionRequestType,
                                                    fromAccount: BankAccount,
                                                    toAccount: BankAccount,
                                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                                    details: String,
                                                    status: String,
                                                    charge: TransactionRequestCharge,
                                                    chargePolicy: String)
case class InBoundCreateTransactionRequestImpl210 (adapterCallContext: OutboundAdapterCallContext, data: TransactionRequest)


case class OutBoundGetTransactionRequests (adapterCallContext: OutboundAdapterCallContext,
                                           initiator: User,
                                           fromAccount: BankAccount)
case class InBoundGetTransactionRequests (adapterCallContext: OutboundAdapterCallContext, data: List[TransactionRequest])


case class OutBoundGetTransactionRequestStatuses (adapterCallContext: OutboundAdapterCallContext,
                                                 )
case class InBoundGetTransactionRequestStatuses (adapterCallContext: OutboundAdapterCallContext, data: TransactionRequestStatusCommons)


case class OutBoundGetTransactionRequestStatusesImpl (adapterCallContext: OutboundAdapterCallContext,
                                                     )
case class InBoundGetTransactionRequestStatusesImpl (adapterCallContext: OutboundAdapterCallContext, data: TransactionRequestStatusCommons)


case class OutBoundGetTransactionRequestsImpl (adapterCallContext: OutboundAdapterCallContext,
                                               fromAccount: BankAccount)
case class InBoundGetTransactionRequestsImpl (adapterCallContext: OutboundAdapterCallContext, data: List[TransactionRequest])


case class OutBoundGetTransactionRequestsImpl210 (adapterCallContext: OutboundAdapterCallContext,
                                                  fromAccount: BankAccount)
case class InBoundGetTransactionRequestsImpl210 (adapterCallContext: OutboundAdapterCallContext, data: List[TransactionRequest])


case class OutBoundGetTransactionRequestImpl (adapterCallContext: OutboundAdapterCallContext,
                                              transactionRequestId: TransactionRequestId)
case class InBoundGetTransactionRequestImpl (adapterCallContext: OutboundAdapterCallContext, data: TransactionRequest)


case class OutBoundGetTransactionRequestTypes (adapterCallContext: OutboundAdapterCallContext,
                                               initiator: User,
                                               fromAccount: BankAccount)
case class InBoundGetTransactionRequestTypes (adapterCallContext: OutboundAdapterCallContext, data: List[TransactionRequestType])


case class OutBoundGetTransactionRequestTypesImpl (adapterCallContext: OutboundAdapterCallContext,
                                                   fromAccount: BankAccount)
case class InBoundGetTransactionRequestTypesImpl (adapterCallContext: OutboundAdapterCallContext, data: List[TransactionRequestType])


case class OutBoundCreateTransactionAfterChallenge (adapterCallContext: OutboundAdapterCallContext,
                                                    initiator: User,
                                                    transReqId: TransactionRequestId)
case class InBoundCreateTransactionAfterChallenge (adapterCallContext: OutboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionAfterChallengev200 (adapterCallContext: OutboundAdapterCallContext,
                                                        fromAccount: BankAccount,
                                                        toAccount: BankAccount,
                                                        transactionRequest: TransactionRequest)
case class InBoundCreateTransactionAfterChallengev200 (adapterCallContext: OutboundAdapterCallContext, data: TransactionRequest)


case class OutBoundCreateBankAndAccount (adapterCallContext: OutboundAdapterCallContext,
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
case class InBoundCreateBankAndAccount (adapterCallContext: OutboundAdapterCallContext, data: (BankCommons, BankAccountCommons))


case class OutBoundGetProducts (adapterCallContext: OutboundAdapterCallContext,
                                bankId: BankId)
case class InBoundGetProducts (adapterCallContext: OutboundAdapterCallContext, data: List[ProductCommons])


case class OutBoundGetProduct (adapterCallContext: OutboundAdapterCallContext,
                               bankId: BankId,
                               productCode: ProductCode)
case class InBoundGetProduct (adapterCallContext: OutboundAdapterCallContext, data: ProductCommons)


case class OutBoundCreateOrUpdateBank (adapterCallContext: OutboundAdapterCallContext,
                                       bankId: String,
                                       fullBankName: String,
                                       shortBankName: String,
                                       logoURL: String,
                                       websiteURL: String,
                                       swiftBIC: String,
                                       national_identifier: String,
                                       bankRoutingScheme: String,
                                       bankRoutingAddress: String)
case class InBoundCreateOrUpdateBank (adapterCallContext: OutboundAdapterCallContext, data: BankCommons)


case class OutBoundCreateOrUpdateProduct (adapterCallContext: OutboundAdapterCallContext,
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
case class InBoundCreateOrUpdateProduct (adapterCallContext: OutboundAdapterCallContext, data: ProductCommons)


case class OutBoundGetBranch (adapterCallContext: OutboundAdapterCallContext,
                              bankId: BankId,
                              branchId: BranchId)
case class InBoundGetBranch (adapterCallContext: OutboundAdapterCallContext, data: BranchTCommons)


case class OutBoundGetAtm (adapterCallContext: OutboundAdapterCallContext,
                           bankId: BankId,
                           atmId: AtmId)
case class InBoundGetAtm (adapterCallContext: OutboundAdapterCallContext, data: AtmTCommons)


case class OutBoundGetTransactionRequestTypeCharge (adapterCallContext: OutboundAdapterCallContext,
                                                    bankId: BankId,
                                                    accountId: AccountId,
                                                    viewId: ViewId,
                                                    transactionRequestType: TransactionRequestType)


case class OutBoundGetCustomerByCustomerId (adapterCallContext: OutboundAdapterCallContext,
                                            customerId: String)
case class InBoundGetCustomerByCustomerId (adapterCallContext: OutboundAdapterCallContext, data: CustomerCommons)