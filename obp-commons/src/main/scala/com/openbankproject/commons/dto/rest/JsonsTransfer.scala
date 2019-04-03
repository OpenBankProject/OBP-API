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

package com.openbankproject.commons.dto.rest

import java.util.Date

import com.openbankproject.commons.model._

import scala.collection.immutable.List


case class OutBoundBankRest (authInfo: AuthInfoBasic, bankId: BankId)
case class InBoundBankRest (authInfo: AuthInfoBasic, data: BankCommons)

//--------generated


case class OutBoundGetObpApiLoopback (authInfo: AuthInfoBasic)
case class InBoundGetObpApiLoopback (authInfo: AuthInfoBasic, data: ObpApiLoopback)


case class OutBoundGetAdapterInfoFuture (authInfo: AuthInfoBasic)
case class InBoundGetAdapterInfoFuture (authInfo: AuthInfoBasic, data: InboundAdapterInfoInternal)


case class OutBoundGetChallengeThreshold (authInfo: AuthInfoBasic,
                                          bankId: String,
                                          accountId: String,
                                          viewId: String,
                                          transactionRequestType: String,
                                          currency: String,
                                          userId: String,
                                          userName: String)
case class InBoundGetChallengeThreshold (authInfo: AuthInfoBasic, data: AmountOfMoney)


case class OutBoundGetChargeLevel (authInfo: AuthInfoBasic,
                                   bankId: BankId,
                                   accountId: AccountId,
                                   viewId: ViewId,
                                   userId: String,
                                   userName: String,
                                   transactionRequestType: String,
                                   currency: String)
case class InBoundGetChargeLevel (authInfo: AuthInfoBasic, data: AmountOfMoney)


case class OutBoundGetBankFuture (authInfo: AuthInfoBasic,
                                  bankId: BankId)
case class InBoundGetBankFuture (authInfo: AuthInfoBasic, data: BankCommons)


case class OutBoundGetBanksFuture (authInfo: AuthInfoBasic)
case class InBoundGetBanksFuture (authInfo: AuthInfoBasic, data: List[BankCommons])

case class OutBoundGetBankAccountsByUsernameFuture (authInfo: AuthInfoBasic,
                                          username: String)
case class InBoundGetBankAccountsByUsernameFuture (authInfo: AuthInfoBasic, data: List[InboundAccountCommonCommons])


case class OutBoundGetBankAccountFuture (authInfo: AuthInfoBasic,
                                         bankId: BankId,
                                         accountId: AccountId)
case class InBoundGetBankAccountFuture (authInfo: AuthInfoBasic, data: BankAccountCommons)


case class OutBoundGetBankAccountsFuture (authInfo: AuthInfoBasic,
                                          bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetBankAccountsFuture (authInfo: AuthInfoBasic, data: List[BankAccountCommons])


case class OutBoundGetCoreBankAccountsFuture (authInfo: AuthInfoBasic,
                                              bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetCoreBankAccountsFuture (authInfo: AuthInfoBasic, data: List[CoreAccount])


case class OutBoundGetCoreBankAccountsHeldFuture (authInfo: AuthInfoBasic,
                                                  bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetCoreBankAccountsHeldFuture (authInfo: AuthInfoBasic, data: List[AccountHeld])


case class OutBoundCheckBankAccountExistsFuture (authInfo: AuthInfoBasic,
                                                 bankId: BankId,
                                                 accountId: AccountId)
case class InBoundCheckBankAccountExistsFuture (authInfo: AuthInfoBasic, data: BankAccountCommons)


case class OutBoundGetCounterpartyTrait (authInfo: AuthInfoBasic,
                                         bankId: BankId,
                                         accountId: AccountId,
                                         couterpartyId: String)
case class InBoundGetCounterpartyTrait (authInfo: AuthInfoBasic, data: CounterpartyTraitCommons)


case class OutBoundGetCounterpartyByCounterpartyIdFuture (authInfo: AuthInfoBasic,
                                                          counterpartyId: CounterpartyId)
case class InBoundGetCounterpartyByCounterpartyIdFuture (authInfo: AuthInfoBasic, data: CounterpartyTraitCommons)


case class OutBoundGetCounterpartyByIban (authInfo: AuthInfoBasic,
                                          iban: String)
case class InBoundGetCounterpartyByIban (authInfo: AuthInfoBasic, data: CounterpartyTraitCommons)


case class OutBoundGetCounterpartiesFuture (authInfo: AuthInfoBasic,
                                            thisBankId: BankId,
                                            thisAccountId: AccountId,
                                            viewId: ViewId)
case class InBoundGetCounterpartiesFuture (authInfo: AuthInfoBasic, data: List[CounterpartyTraitCommons])


case class OutBoundGetTransactionsFuture (authInfo: AuthInfoBasic,
                                          bankId: BankId,
                                          accountID: AccountId)
case class InBoundGetTransactionsFuture (authInfo: AuthInfoBasic, data: List[TransactionCommons])


case class OutBoundGetTransactionFuture (authInfo: AuthInfoBasic,
                                         bankId: BankId,
                                         accountID: AccountId,
                                         transactionId: TransactionId)
case class InBoundGetTransactionFuture (authInfo: AuthInfoBasic, data: TransactionCommons)


case class OutBoundMakePaymentv210 (authInfo: AuthInfoBasic,
                                    fromAccount: BankAccountCommons,
                                    toAccount: BankAccountCommons,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
                                    amount: BigDecimal,
                                    description: String,
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)
case class InBoundMakePaymentv210 (authInfo: AuthInfoBasic, data: TransactionId)


case class OutBoundCreateTransactionRequestv210 (authInfo: AuthInfoBasic,
                                                 initiator: User, //TODO FIXME
                                                 viewId: ViewId,
                                                 fromAccount: BankAccountCommons,
                                                 toAccount: BankAccountCommons,
                                                 transactionRequestType: TransactionRequestType,
                                                 transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
                                                 detailsPlain: String,
                                                 chargePolicy: String)
case class InBoundCreateTransactionRequestv210 (authInfo: AuthInfoBasic, data: TransactionRequest)


case class OutBoundCreateTransactionAfterChallengeV210 (authInfo: AuthInfoBasic,
                                                        fromAccount: BankAccountCommons,
                                                        transactionRequest: TransactionRequest)
case class InBoundCreateTransactionAfterChallengeV210 (authInfo: AuthInfoBasic, data: TransactionRequest)


case class OutBoundGetBranchFuture (authInfo: AuthInfoBasic,
                                    bankId: BankId,
                                    branchId: BranchId)
case class InBoundGetBranchFuture (authInfo: AuthInfoBasic, data: BranchTCommons)


case class OutBoundGetBranchesFuture (authInfo: AuthInfoBasic,
                                      bankId: BankId)
case class InBoundGetBranchesFuture (authInfo: AuthInfoBasic, data: List[BranchTCommons])


case class OutBoundGetAtmFuture (authInfo: AuthInfoBasic,
                                 bankId: BankId,
                                 atmId: AtmId)
case class InBoundGetAtmFuture (authInfo: AuthInfoBasic, data: AtmTCommons)


case class OutBoundGetAtmsFuture (authInfo: AuthInfoBasic,
                                  bankId: BankId)
case class InBoundGetAtmsFuture (authInfo: AuthInfoBasic, data: List[AtmTCommons])


case class OutBoundCreateTransactionAfterChallengev300 (authInfo: AuthInfoBasic,
                                                        initiator: User,       //TODO fixme
                                                        fromAccount: BankAccountCommons,
                                                        transReqId: TransactionRequestId,
                                                        transactionRequestType: TransactionRequestType)
case class InBoundCreateTransactionAfterChallengev300 (authInfo: AuthInfoBasic, data: TransactionRequest)


case class OutBoundMakePaymentv300 (authInfo: AuthInfoBasic,
                                    initiator: User,      //TODO fixme
                                    fromAccount: BankAccountCommons,
                                    toAccount: BankAccountCommons,
                                    toCounterparty: CounterpartyTraitCommons,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)
case class InBoundMakePaymentv300 (authInfo: AuthInfoBasic, data: TransactionId)


case class OutBoundCreateTransactionRequestv300 (authInfo: AuthInfoBasic,
                                                 initiator: User,      //TODO fixme
                                                 viewId: ViewId,
                                                 fromAccount: BankAccountCommons,
                                                 toAccount: BankAccountCommons,
                                                 toCounterparty: CounterpartyTraitCommons,
                                                 transactionRequestType: TransactionRequestType,
                                                 transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
                                                 detailsPlain: String,
                                                 chargePolicy: String)
case class InBoundCreateTransactionRequestv300 (authInfo: AuthInfoBasic, data: TransactionRequest)


case class OutBoundCreateCustomerFuture (authInfo: AuthInfoBasic,
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
case class InBoundCreateCustomerFuture (authInfo: AuthInfoBasic, data: CustomerCommons)


case class OutBoundGetCustomersByUserIdFuture (authInfo: AuthInfoBasic,
                                               userId: String)
case class InBoundGetCustomersByUserIdFuture (authInfo: AuthInfoBasic, data: List[CustomerCommons])


case class OutBoundGetCustomerByCustomerIdFuture (authInfo: AuthInfoBasic,
                                                  customerId: String)
case class InBoundGetCustomerByCustomerIdFuture (authInfo: AuthInfoBasic, data: CustomerCommons)


case class OutBoundGetCustomerByCustomerNumberFuture (authInfo: AuthInfoBasic,
                                                      customerNumber: String,
                                                      bankId: BankId)
case class InBoundGetCustomerByCustomerNumberFuture (authInfo: AuthInfoBasic, data: CustomerCommons)


case class OutBoundGetCustomerAddress (authInfo: AuthInfoBasic,
                                       customerId: String)
case class InBoundGetCustomerAddress (authInfo: AuthInfoBasic, data: List[CustomerAddressCommons])


case class OutBoundCreateCustomerAddress (authInfo: AuthInfoBasic,
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
case class InBoundCreateCustomerAddress (authInfo: AuthInfoBasic, data: CustomerAddressCommons)


case class OutBoundUpdateCustomerAddress (authInfo: AuthInfoBasic,
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
case class InBoundUpdateCustomerAddress (authInfo: AuthInfoBasic, data: CustomerAddressCommons)


case class OutBoundCreateTaxResidence (authInfo: AuthInfoBasic,
                                       customerId: String,
                                       domain: String,
                                       taxNumber: String)
case class InBoundCreateTaxResidence (authInfo: AuthInfoBasic, data: TaxResidenceCommons)


case class OutBoundGetTaxResidence (authInfo: AuthInfoBasic,
                                    customerId: String)
case class InBoundGetTaxResidence (authInfo: AuthInfoBasic, data: List[TaxResidenceCommons])


case class OutBoundGetCustomersFuture (authInfo: AuthInfoBasic,
                                       bankId: BankId)
case class InBoundGetCustomersFuture (authInfo: AuthInfoBasic, data: List[CustomerCommons])


case class OutBoundGetCheckbookOrdersFuture (authInfo: AuthInfoBasic,
                                             bankId: String,
                                             accountId: String)
case class InBoundGetCheckbookOrdersFuture (authInfo: AuthInfoBasic, data: CheckbookOrdersJson)


case class OutBoundGetStatusOfCreditCardOrderFuture (authInfo: AuthInfoBasic,
                                                     bankId: String,
                                                     accountId: String)
case class InBoundGetStatusOfCreditCardOrderFuture (authInfo: AuthInfoBasic, data: List[CardObjectJson])


case class OutBoundCreateUserAuthContext (authInfo: AuthInfoBasic,
                                          userId: String,
                                          key: String,
                                          value: String)
case class InBoundCreateUserAuthContext (authInfo: AuthInfoBasic, data: UserAuthContextCommons)


case class OutBoundGetUserAuthContexts (authInfo: AuthInfoBasic,
                                        userId: String)
case class InBoundGetUserAuthContexts (authInfo: AuthInfoBasic, data: List[UserAuthContextCommons])


case class OutBoundCreateOrUpdateProductAttribute (authInfo: AuthInfoBasic,
                                                   bankId: BankId,
                                                   productCode: ProductCode,
                                                   productAttributeId: Option[String],
                                                   name: String,
                                                   attributType: ProductAttributeType.Value,
                                                   value: String)
case class InBoundCreateOrUpdateProductAttribute (authInfo: AuthInfoBasic, data: ProductAttributeCommons)


case class OutBoundGetProductAttributeById (authInfo: AuthInfoBasic,
                                            productAttributeId: String)
case class InBoundGetProductAttributeById (authInfo: AuthInfoBasic, data: ProductAttributeCommons)


case class OutBoundGetProductAttributesByBankAndCode (authInfo: AuthInfoBasic,
                                                      bank: BankId,
                                                      productCode: ProductCode)
case class InBoundGetProductAttributesByBankAndCode (authInfo: AuthInfoBasic, data: List[ProductAttributeCommons])


case class OutBoundCreateOrUpdateAccountAttribute (authInfo: AuthInfoBasic,
                                                   bankId: BankId,
                                                   accountId: AccountId,
                                                   productCode: ProductCode,
                                                   productAttributeId: Option[String],
                                                   name: String,
                                                   attributType: AccountAttributeType.Value,
                                                   value: String)
case class InBoundCreateOrUpdateAccountAttribute (authInfo: AuthInfoBasic, data: AccountAttributeCommons)


case class OutBoundCreateAccountApplication (authInfo: AuthInfoBasic,
                                             productCode: ProductCode,
                                             userId: Option[String],
                                             customerId: Option[String])
case class InBoundCreateAccountApplication (authInfo: AuthInfoBasic, data: AccountApplicationCommons)


case class OutBoundGetAllAccountApplication (authInfo: AuthInfoBasic)
case class InBoundGetAllAccountApplication (authInfo: AuthInfoBasic, data: List[AccountApplicationCommons])


case class OutBoundGetAccountApplicationById (authInfo: AuthInfoBasic,
                                              accountApplicationId: String)
case class InBoundGetAccountApplicationById (authInfo: AuthInfoBasic, data: AccountApplicationCommons)


case class OutBoundUpdateAccountApplicationStatus (authInfo: AuthInfoBasic,
                                                   accountApplicationId: String,
                                                   status: String)
case class InBoundUpdateAccountApplicationStatus (authInfo: AuthInfoBasic, data: AccountApplicationCommons)


case class OutBoundGetOrCreateProductCollection (authInfo: AuthInfoBasic,
                                                 collectionCode: String,
                                                 productCodes: List[String])
case class InBoundGetOrCreateProductCollection (authInfo: AuthInfoBasic, data: List[ProductCollectionCommons])


case class OutBoundGetProductCollection (authInfo: AuthInfoBasic,
                                         collectionCode: String)
case class InBoundGetProductCollection (authInfo: AuthInfoBasic, data: List[ProductCollectionCommons])


case class OutBoundGetOrCreateProductCollectionItem (authInfo: AuthInfoBasic,
                                                     collectionCode: String,
                                                     memberProductCodes: List[String])
case class InBoundGetOrCreateProductCollectionItem (authInfo: AuthInfoBasic, data: List[ProductCollectionItemCommons])


case class OutBoundGetProductCollectionItem (authInfo: AuthInfoBasic,
                                             collectionCode: String)
case class InBoundGetProductCollectionItem (authInfo: AuthInfoBasic, data: List[ProductCollectionItemCommons])


case class OutBoundGetProductCollectionItemsTree (authInfo: AuthInfoBasic,
                                                  collectionCode: String,
                                                  bankId: String)
case class InBoundGetProductCollectionItemsTree (authInfo: AuthInfoBasic, data: List[(ProductCollectionItemCommons, Product, List[ProductAttribute])])


case class OutBoundCreateMeeting (authInfo: AuthInfoBasic,
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
case class InBoundCreateMeeting (authInfo: AuthInfoBasic, data: MeetingCommons)


case class OutBoundGetMeetings (authInfo: AuthInfoBasic,
                                bankId: BankId,
                                userId: User) //TODO fixme
case class InBoundGetMeetings (authInfo: AuthInfoBasic, data: List[MeetingCommons])


case class OutBoundGetMeeting (authInfo: AuthInfoBasic,
                               bankId: BankId,
                               userId: User,      //TODO fixme
                               meetingId: String)
case class InBoundGetMeeting (authInfo: AuthInfoBasic, data: MeetingCommons)
