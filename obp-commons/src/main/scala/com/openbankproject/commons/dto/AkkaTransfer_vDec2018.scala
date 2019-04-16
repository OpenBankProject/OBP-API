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

import java.lang
import java.util.Date

import com.openbankproject.commons.model.{CounterpartyTrait, CreditLimit, _}

import scala.collection.immutable.List
import scala.math.BigDecimal

/**
  *
  * case classes used to define outbound Akka messages
  *
  */

case class OutboundGetAccount(bankId: String,
                              accountId: String,
                              adapterCallContext: Option[AdapterCallContext])

case class OutboundGetCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId],
                                       adapterCallContext: Option[AdapterCallContext])

case class OutboundGetCustomersByUserId(userId: String, adapterCallContext: Option[AdapterCallContext])

case class OutboundGetCounterparties(thisBankId: String,
                                     thisAccountId: String,
                                     viewId: String,
                                     adapterCallContext: Option[AdapterCallContext])

case class OutboundGetTransactions(bankId: String,
                                   accountId: String,
                                   limit: Int,
                                   fromDate: String,
                                   toDate: String,
                                   adapterCallContext: Option[AdapterCallContext])

case class OutboundGetTransaction(bankId: String,
                                  accountId: String,
                                  transactionId: String,
                                  adapterCallContext: Option[AdapterCallContext])

/**
  *
  * case classes used to define inbound Akka messages
  *
  */

case class InboundGetAccount(payload: Option[InboundAccount],
                             adapterCallContext: Option[AdapterCallContext])

case class InboundGetCoreBankAccounts(payload: List[InboundCoreAccount],
                                      adapterCallContext: Option[AdapterCallContext])

case class InboundGetCustomersByUserId(payload: List[InboundCustomer],
                                       adapterCallContext: Option[AdapterCallContext])

case class InboundGetCounterparties(payload: List[InboundCounterparty],
                                    adapterCallContext: Option[AdapterCallContext])

case class InboundGetTransactions(payload: List[InboundTransaction],
                                  adapterCallContext: Option[AdapterCallContext])

case class InboundGetTransaction(payload: Option[InboundTransaction],
                                 adapterCallContext: Option[AdapterCallContext])

case class InboundAccount(
                           bankId: String,
                           branchId: String,
                           accountId: String,
                           accountNumber: String,
                           accountType: String,
                           balanceAmount: String,
                           balanceCurrency: String,
                           owners: List[String],
                           viewsToGenerate: List[String],
                           bankRoutingScheme: String,
                           bankRoutingAddress: String,
                           branchRoutingScheme: String,
                           branchRoutingAddress: String,
                           accountRoutingScheme: String,
                           accountRoutingAddress: String,
                           accountRouting: List[AccountRouting],
                           accountRules: List[AccountRule]
                         )


case class InboundCoreAccount(
                               id: String,
                               label: String,
                               bankId: String,
                               accountType: String,
                               accountRoutings: List[AccountRouting]
                             )

case class InboundCustomer(
                            customerId: String,
                            bankId: String,
                            number: String,
                            legalName: String,
                            mobileNumber: String,
                            email: String,
                            faceImage: CustomerFaceImage,
                            dateOfBirth: Date,
                            relationshipStatus: String,
                            dependents: Integer,
                            dobOfDependents: List[Date],
                            highestEducationAttained: String,
                            employmentStatus: String,
                            creditRating: CreditRating,
                            creditLimit: CreditLimit,
                            kycStatus: lang.Boolean,
                            lastOkDate: Date
                          )

case class InboundTransaction(
                               uuid: String,
                               id: TransactionId,
                               thisAccount: BankAccount,
                               otherAccount: Counterparty,
                               transactionType: String,
                               amount: BigDecimal,
                               currency: String,
                               description: Option[String],
                               startDate: Date,
                               finishDate: Date,
                               balance: BigDecimal
                             )

case class InboundCounterparty(
                                createdByUserId: String,
                                name: String,
                                thisBankId: String,
                                thisAccountId: String,
                                thisViewId: String,
                                counterpartyId: String,
                                otherAccountRoutingScheme: String,
                                otherAccountRoutingAddress: String,
                                otherBankRoutingScheme: String,
                                otherBankRoutingAddress: String,
                                otherBranchRoutingScheme: String,
                                otherBranchRoutingAddress: String,
                                isBeneficiary: Boolean,
                                description: String,
                                otherAccountSecondaryRoutingScheme: String,
                                otherAccountSecondaryRoutingAddress: String,
                                bespoke: List[CounterpartyBespoke]
) extends CounterpartyTrait

