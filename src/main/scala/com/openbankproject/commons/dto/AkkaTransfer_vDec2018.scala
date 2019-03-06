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
case class OutboundGetAdapterInfo(date: String,
                                  callContext: Option[CallContextAkka])

case class OutboundGetBanks(callContext: Option[CallContextAkka])

case class OutboundGetBank(bankId: String,
                           callContext: Option[CallContextAkka])

case class OutboundCheckBankAccountExists(bankId: String,
                                          ccountId: String,
                                          callContext: Option[CallContextAkka])

case class OutboundGetAccount(bankId: String,
                              accountId: String,
                              callContext: Option[CallContextAkka])

case class OutboundGetCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId],
                                       callContext: Option[CallContextAkka])

case class OutboundGetCustomersByUserId(userId: String, callContext: Option[CallContextAkka])

case class OutboundGetCounterparties(thisBankId: String,
                                     thisAccountId: String,
                                     viewId: String,
                                     callContext: Option[CallContextAkka])

case class OutboundGetTransactions(bankId: String,
                                   accountId: String,
                                   limit: Int,
                                   fromDate: String,
                                   toDate: String,
                                   callContext: Option[CallContextAkka])

case class OutboundGetTransaction(bankId: String,
                                  accountId: String,
                                  transactionId: String,
                                  callContext: Option[CallContextAkka])

/**
  *
  * case classes used to define inbound Akka messages
  *
  */
case class InboundAdapterInfo(
                               name: String,
                               version: String,
                               git_commit: String,
                               date: String,
                               callContext: Option[CallContextAkka]
                             )

case class InboundGetBanks(payload: Option[List[InboundBank]],
                           callContext: Option[CallContextAkka])

case class InboundGetBank(payload: Option[InboundBank],
                          callContext: Option[CallContextAkka])

case class InboundCheckBankAccountExists(payload: Option[InboundAccount],
                                         callContext: Option[CallContextAkka])

case class InboundGetAccount(payload: Option[InboundAccount],
                             callContext: Option[CallContextAkka])

case class InboundGetCoreBankAccounts(payload: List[InboundCoreAccount],
                                      callContext: Option[CallContextAkka])

case class InboundGetCustomersByUserId(payload: List[InboundCustomer],
                                       callContext: Option[CallContextAkka])

case class InboundGetCounterparties(payload: List[InboundCounterparty],
                                    callContext: Option[CallContextAkka])

case class InboundGetTransactions(payload: List[InboundTransaction],
                                  callContext: Option[CallContextAkka])

case class InboundGetTransaction(payload: Option[InboundTransaction],
                                 callContext: Option[CallContextAkka])


case class InboundBank(
                        bankId: String,
                        shortName: String,
                        fullName: String,
                        logoUrl: String,
                        websiteUrl: String,
                        bankRoutingScheme: String,
                        bankRoutingAddress: String
                      )

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
                                bespoke: List[CounterpartyBespoke]) extends CounterpartyTrait