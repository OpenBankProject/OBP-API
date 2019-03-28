package com.openbankproject.commons.model

import java.util.Date
import code.customeraddress.CustomerAddress
import code.bankconnectors.InboundAccountCommon
import code.branches.Branches.BranchT
import com.openbankproject.commons.model.Customer
import code.context.UserAuthContext
import code.meetings.Meeting
import code.taxresidence.TaxResidence
import code.productcollectionitem.ProductCollectionItem
import com.openbankproject.commons.model.Bank
import code.productcollection.ProductCollection
import com.openbankproject.commons.model.CounterpartyTrait
import code.atms.Atms.AtmT
import code.productattribute.ProductAttribute.ProductAttribute
import code.accountattribute.AccountAttribute.AccountAttribute
import com.openbankproject.commons.model.BankAccount
import code.accountapplication.AccountApplication

case class ProductAttributeCommons(
                                    bankId :BankId,
                                    productCode :ProductCode,
                                    productAttributeId :String,
                                    name :String,
                                    attributeType : ProductAttributeType.Value,
                                    value :String) extends ProductAttribute


case class ProductCollectionCommons(
                                     collectionCode :String,
                                     productCode :String) extends ProductCollection


case class AccountAttributeCommons(
                                    bankId :BankId,
                                    accountId :AccountId,
                                    productCode :ProductCode,
                                    accountAttributeId :String,
                                    name :String,
                                    attributeType :Value,
                                    value :String) extends AccountAttribute


case class AccountApplicationCommons(
                                      accountApplicationId :String,
                                      productCode :ProductCode,
                                      userId :String,
                                      customerId :String,
                                      dateOfApplication :Date,
                                      status :String) extends AccountApplication


case class UserAuthContextCommons(
                                   userAuthContextId :String,
                                   userId :String,
                                   key :String,
                                   value :String) extends UserAuthContext


case class BankAccountCommons(
                               accountId :AccountId,
                               accountType :String,
                               balance :BigDecimal,
                               currency :String,
                               name :String,
                               label :String,
                               swift_bic :Option[String],
                               iban :Option[String],
                               number :String,
                               bankId :BankId,
                               lastUpdate :Date,
                               branchId :String,
                               accountRoutingScheme :String,
                               accountRoutingAddress :String,
                               accountRoutings :List[AccountRouting],
                               accountRules :List[AccountRule],
                               accountHolder :String) extends BankAccount


case class ProductCollectionItemCommons(
                                         collectionCode :String,
                                         memberProductCode :String) extends ProductCollectionItem


case class CustomerCommons(
                            customerId :String,
                            bankId :String,
                            number :String,
                            legalName :String,
                            mobileNumber :String,
                            email :String,
                            faceImage :CustomerFaceImageTrait,
                            dateOfBirth :Date,
                            relationshipStatus :String,
                            dependents :Integer,
                            dobOfDependents :List[Date],
                            highestEducationAttained :String,
                            employmentStatus :String,
                            creditRating :CreditRatingTrait,
                            creditLimit :AmountOfMoneyTrait,
                            kycStatus : java.lang.Boolean,
                            lastOkDate :Date,
                            title :String,
                            branchId :String,
                            nameSuffix :String) extends Customer


case class CustomerAddressCommons(
                                   customerId :String,
                                   customerAddressId :String,
                                   line1 :String,
                                   line2 :String,
                                   line3 :String,
                                   city :String,
                                   county :String,
                                   state :String,
                                   postcode :String,
                                   countryCode :String,
                                   status :String,
                                   tags :String,
                                   insertDate :Date) extends CustomerAddress


case class InboundAccountCommonCommons(
                                        errorCode :String,
                                        bankId :String,
                                        branchId :String,
                                        accountId :String,
                                        accountNumber :String,
                                        accountType :String,
                                        balanceAmount :String,
                                        balanceCurrency :String,
                                        owners :List[String],
                                        viewsToGenerate :List[String],
                                        bankRoutingScheme :String,
                                        bankRoutingAddress :String,
                                        branchRoutingScheme :String,
                                        branchRoutingAddress :String,
                                        accountRoutingScheme :String,
                                        accountRoutingAddress :String) extends InboundAccountCommon


case class AtmTCommons(
                        atmId :AtmId,
                        bankId :BankId,
                        name :String,
                        address :AddressT,
                        location :LocationT,
                        meta :MetaT,
                        OpeningTimeOnMonday : Option[String],
                        ClosingTimeOnMonday : Option[String],

                        OpeningTimeOnTuesday : Option[String],
                        ClosingTimeOnTuesday : Option[String],

                        OpeningTimeOnWednesday : Option[String],
                        ClosingTimeOnWednesday : Option[String],

                        OpeningTimeOnThursday : Option[String],
                        ClosingTimeOnThursday: Option[String],

                        OpeningTimeOnFriday : Option[String],
                        ClosingTimeOnFriday : Option[String],

                        OpeningTimeOnSaturday : Option[String],
                        ClosingTimeOnSaturday : Option[String],

                        OpeningTimeOnSunday: Option[String],
                        ClosingTimeOnSunday : Option[String],

                        isAccessible : Option[Boolean],

                        locatedAt : Option[String],
                        moreInfo : Option[String],
                        hasDepositCapability : Option[Boolean]) extends AtmT


case class BankCommons(
                        bankId :BankId,
                        shortName :String,
                        fullName :String,
                        logoUrl :String,
                        websiteUrl :String,
                        bankRoutingScheme :String,
                        bankRoutingAddress :String,
                        swiftBic :String,
                        nationalIdentifier :String) extends Bank


case class CounterpartyTraitCommons(
                                     createdByUserId :String,
                                     name :String,
                                     description :String,
                                     thisBankId :String,
                                     thisAccountId :String,
                                     thisViewId :String,
                                     counterpartyId :String,
                                     otherAccountRoutingScheme :String,
                                     otherAccountRoutingAddress :String,
                                     otherAccountSecondaryRoutingScheme :String,
                                     otherAccountSecondaryRoutingAddress :String,
                                     otherBankRoutingScheme :String,
                                     otherBankRoutingAddress :String,
                                     otherBranchRoutingScheme :String,
                                     otherBranchRoutingAddress :String,
                                     isBeneficiary :Boolean,
                                     bespoke :List) extends CounterpartyTrait


case class TaxResidenceCommons(
                                customerId :Long,
                                taxResidenceId :String,
                                domain :String,
                                taxNumber :String) extends TaxResidence


case class BranchTCommons(
                           branchId: BranchId,
                           bankId: BankId,
                           name: String,
                           address: Address,
                           location: Location,
                           lobbyString: Option[LobbyStringT],
                           driveUpString: Option[DriveUpStringT],
                           meta: Meta,
                           branchRouting: Option[RoutingT],
                           lobby: Option[Lobby],
                           driveUp: Option[DriveUp],
                           isAccessible : Option[Boolean],
                           accessibleFeatures: Option[String],
                           branchType : Option[String],
                           moreInfo : Option[String],
                           phoneNumber : Option[String],
                           isDeleted : Option[Boolean]) extends BranchT


case class MeetingCommons(
                           meetingId :String,
                           providerId :String,
                           purposeId :String,
                           bankId :String,
                           present :MeetingPresent,
                           keys :MeetingKeys,
                           when :Date,
                           creator :ContactDetails,
                           invitees :List[Invitee]) extends Meeting
