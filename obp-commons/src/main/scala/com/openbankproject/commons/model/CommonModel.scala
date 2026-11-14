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
package com.openbankproject.commons.model

import org.json4s._
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import com.openbankproject.commons.model.enums._
import com.openbankproject.commons.util.{ReflectUtils, json, optional}
import org.json4s.JsonAST.{JObject, JValue}
import org.json4s.JsonDSL._
import org.json4s.{Formats, JInt, JString}

import net.liftweb.common.Box

import java.lang
import java.util.Date
import scala.reflect.runtime.universe._


// `D <% T` was view-bound syntax; it desugars to exactly the implicit constructor parameter
// written out here, so subclasses need the same implicit D => T they already needed. Everything
// else Converter needs - toCommons and the four implicit collection-shaped conversions built on
// it - is identical to ConverterWithType once dType is fixed to typeTag[D].tpe, so it inherits
// them rather than redeclaring them (same pattern as OBPEnumeration/OBPEnumerationBase below).
abstract class Converter[T, D: TypeTag](implicit ev: D => T) extends ConverterWithType[T, D](typeTag[D].tpe)

// Same as Converter, but for the handful of subclasses declared in obp-api rather than here: D's
// Type is a constructor parameter instead of a TypeTag context bound, because typeTag[D] needs the
// Scala 2 compiler's TypeTag synthesis at the `extends` clause itself, and obp-api compiles under
// Scala 3. Subclasses pass ReflectUtils.forType("fully.qualified.D") instead - a pure string-based
// class lookup needing no compiler synthesis.
abstract class ConverterWithType[T, D](dType: Type)(implicit ev: D => T){
  implicit def toCommons(t: T): D = ReflectUtils.toOther[D](t, dType)

  implicit val toCommonsList: List[T] => List[D] = (items: List[T]) => items.map(toCommons)

  implicit val toCommonsBox: Box[T] => Box[D] = (box: Box[T]) => box.map(toCommons)

  implicit val toCommonsBoxList: Box[List[T]] => Box[List[D]] = (boxItems: Box[List[T]]) => boxItems.map(toCommonsList)

  implicit val toCommonsOption: Option[T] => Option[D] = (option: Option[T]) => option.map(toCommons)

  implicit val toCommonsOptionList: Option[List[T]] => Option[List[D]] = (optionItems: Option[List[T]]) => optionItems.map(toCommonsList)
}

case class ProductAttributeCommons(
                                    bankId :BankId,
                                    productCode :ProductCode,
                                    productAttributeId :String,
                                    name :String,
                                    attributeType : ProductAttributeType.Value,
                                    value :String, 
                                    isActive: Option[Boolean] = None) extends ProductAttribute
object ProductAttributeCommons extends Converter[ProductAttribute, ProductAttributeCommons]


case class ProductCollectionCommons(
                                     collectionCode :String,
                                     productCode :String) extends ProductCollection

object ProductCollectionCommons extends Converter[ProductCollection, ProductCollectionCommons]


case class AccountAttributeCommons(
                                    bankId :BankId,
                                    accountId :AccountId,
                                    productCode :ProductCode,
                                    accountAttributeId :String,
                                    name :String,
                                    attributeType : AccountAttributeType.Value,
                                    value :String,
                                    productInstanceCode: Option[String] = None) extends AccountAttribute

object AccountAttributeCommons extends Converter[AccountAttribute, AccountAttributeCommons]


case class AccountApplicationCommons(
                                      accountApplicationId :String,
                                      productCode :ProductCode,
                                      userId :String,
                                      customerId :String,
                                      dateOfApplication :Date,
                                      status :String) extends AccountApplication

object AccountApplicationCommons extends Converter[AccountApplication, AccountApplicationCommons]


case class UserAuthContextCommons(
                                   userAuthContextId :String,
                                   userId :String,
                                   key :String,
                                   value :String,
                                   timeStamp :Date,
                                   consumerId :String) extends UserAuthContext

object UserAuthContextCommons extends Converter[UserAuthContext, UserAuthContextCommons]


case class BankAccountCommons(
                               accountId :AccountId,
                               accountType :String,
                               balance :BigDecimal,
                               currency :String,
                               name :String,
                               @optional
                               label :String,
                               number :String,
                               bankId :BankId,
                               lastUpdate :Date,
                               branchId :String,
                               accountRoutings :List[AccountRouting],
                               @optional
                               accountRules :List[AccountRule],
                               @optional
                               accountHolder :String,
                               override val attributes : Option[List[Attribute]] = None) extends BankAccount

object BankAccountCommons extends Converter[BankAccount, BankAccountCommons]

case class BankAccountBalanceTraitCommons(
  bankId :BankId,
  accountId :AccountId,
  balanceId :BalanceId,
  balanceType :String,
  balanceAmount :BigDecimal,
  lastChangeDateTime: Option[Date],
  referenceDate: Option[String],
) extends BankAccountBalanceTrait

object BankAccountBalanceTraitCommons extends Converter[BankAccountBalanceTrait, BankAccountBalanceTraitCommons]

case class ProductCollectionItemCommons(
                                         collectionCode :String,
                                         memberProductCode :String) extends ProductCollectionItem

object ProductCollectionItemCommons extends Converter[ProductCollectionItem, ProductCollectionItemCommons]


case class CustomerCommons(
                            customerId :String,
                            bankId :String,
                            number :String,
                            legalName :String,
                            mobileNumber :String,
                            email :String,
                            faceImage :CustomerFaceImage,
                            dateOfBirth :Date,
                            relationshipStatus :String,
                            dependents :Integer,
                            dobOfDependents :List[Date],
                            highestEducationAttained :String,
                            employmentStatus :String,
                            creditRating :CreditRating,
                            creditLimit :CreditLimit,
                            kycStatus : java.lang.Boolean,
                            lastOkDate :Date,
                            title :String,
                            branchId :String,
                            nameSuffix :String,
                            customerType :Option[String] = Some("INDIVIDUAL"),
                            parentCustomerId :Option[String] = Some("")) extends Customer

object CustomerCommons extends Converter[Customer, CustomerCommons]

case class AgentCommons(
  agentId: String,
  bankId: String,
  number: String,
  legalName: String,
  mobileNumber: String,
  isConfirmedAgent: Boolean,
  isPendingAgent: Boolean,
) extends Agent
object AgentCommons extends Converter[Agent, AgentCommons]

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

object CustomerAddressCommons extends Converter[CustomerAddress, CustomerAddressCommons]

//This class is only used for connector.getBankAccountsForUser method. Not the used for accounts relevant endpoints.
//It will get the bankId, accountId and viewsToGenerate to create the OBP side data, such as views, accountHolder.
case class InboundAccountCommons(
                                        bankId :String,
                                        @optional
                                        branchId :String,
                                        accountId :String,
                                        @optional
                                        accountNumber :String,
                                        @optional
                                        accountType :String,
                                        @optional
                                        balanceAmount :String,
                                        @optional
                                        balanceCurrency :String,
                                        @optional
                                        owners :List[String],
                                        viewsToGenerate :List[String],
                                        @optional
                                        bankRoutingScheme :String,
                                        @optional
                                        bankRoutingAddress :String,
                                        @optional
                                        branchRoutingScheme :String,
                                        @optional
                                        branchRoutingAddress :String,
                                        @optional
                                        accountRoutingScheme :String,
                                        @optional
                                        accountRoutingAddress :String) extends InboundAccount

object InboundAccountCommons extends Converter[InboundAccount, InboundAccountCommons]


case class AtmTCommons(
                        atmId :AtmId,
                        bankId :BankId,
                        name :String,
                        address :Address,
                        location :Location,
                        meta :Meta,
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
                        hasDepositCapability : Option[Boolean] = None,
                        supportedLanguages : Option[List[String]] = None,
                        services: Option[List[String]] = None,
                        accessibilityFeatures: Option[List[String]] = None,
                        supportedCurrencies: Option[List[String]] = None,
                        notes: Option[List[String]] = None,
                        locationCategories: Option[List[String]] = None,
                        minimumWithdrawal: Option[String] = None,
                        branchIdentification: Option[String] = None,
                        siteIdentification: Option[String] = None,
                        siteName: Option[String] = None,
                        cashWithdrawalNationalFee: Option[String] = None,
                        cashWithdrawalInternationalFee: Option[String] = None,
                        balanceInquiryFee: Option[String] = None,
                        atmType: Option[String] = None,
                        phone: Option[String] = None,
) extends AtmT

object AtmTCommons extends Converter[AtmT, AtmTCommons]


case class BankCommons(
                        bankId :BankId,
                        shortName :String,
                        fullName :String,
                        logoUrl :String,
                        websiteUrl :String,
                        bankRoutingScheme :String,
                        bankRoutingAddress :String,
                        @optional
                        swiftBic :String,
                        @optional
                        nationalIdentifier :String) extends Bank {
  def this(bankId :BankId,
    shortName :String,
    fullName :String,
    logoUrl :String,
    websiteUrl :String,
    bankRoutingScheme :String,
    bankRoutingAddress :String) = this(bankId, shortName, fullName, logoUrl, websiteUrl, bankRoutingScheme, bankRoutingAddress, null, null)
}


object BankCommons extends Converter[Bank, BankCommons]

case class CounterpartyTraitCommons(
                                     createdByUserId :String,
                                     name :String,
                                     description :String,
                                     currency: String,
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
                                     bespoke :List[CounterpartyBespoke]) extends CounterpartyTrait

object CounterpartyTraitCommons extends Converter[CounterpartyTrait, CounterpartyTraitCommons]


case class TaxResidenceCommons(
                                customerId :String,
                                taxResidenceId :String,
                                domain :String,
                                taxNumber :String) extends TaxResidence

object TaxResidenceCommons extends Converter[TaxResidence, TaxResidenceCommons]


case class BranchTCommons(
                           branchId: BranchId,
                           bankId: BankId,
                           name: String,
                           address: Address,
                           location: Location,
                           lobbyString: Option[LobbyString],
                           driveUpString: Option[DriveUpString],
                           meta: Meta,
                           branchRouting: Option[Routing],
                           lobby: Option[Lobby],
                           driveUp: Option[DriveUp],
                           isAccessible : Option[Boolean],
                           accessibleFeatures: Option[String],
                           branchType : Option[String],
                           moreInfo : Option[String],
                           phoneNumber : Option[String],
                           isDeleted : Option[Boolean]) extends BranchT

object BranchTCommons extends Converter[BranchT, BranchTCommons]


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

object MeetingCommons extends Converter[Meeting, MeetingCommons]

case class ProductCommons(bankId: BankId,
                       code : ProductCode,
                       parentProductCode : ProductCode,
                       name : String,
                       category: String,
                       family : String,
                       superFamily : String,
                       moreInfoUrl: String,
                       termsAndConditionsUrl: String,
                       details: String,
                       description: String,
                       meta: Meta) extends Product

object ProductCommons extends Converter[Product, ProductCommons]

case class TransactionRequestCommonBodyJSONCommons(
  value: AmountOfMoneyJsonV121,
  description: String,
) extends TransactionRequestCommonBodyJSON

object TransactionRequestCommonBodyJSONCommons extends Converter[TransactionRequestCommonBodyJSON, TransactionRequestCommonBodyJSONCommons]

case class BerlinGroupTransactionRequestCommonBodyJsonCommons(
  endToEndIdentification:  Option[String],
  instructionIdentification:  Option[String],
  debtorName:  Option[String],
  debtorAccount: PaymentAccount,
  debtorId: Option[String],
  ultimateDebtor: Option[String],
  instructedAmount: AmountOfMoneyJsonV121,
  currencyOfTransfer: Option[String],
  exchangeRateInformation: Option[String],
  creditorAccount: PaymentAccount,
  creditorAgent: Option[String],
  creditorAgentName: Option[String],
  creditorName: String,
  creditorId: Option[String],
  creditorAddress: Option[String],
  creditorNameAndAddress: Option[String],
  ultimateCreditor: Option[String],
  purposeCode: Option[String],
  chargeBearer: Option[String],
  serviceLevel: Option[String],
  remittanceInformationUnstructured: Option[String],
  remittanceInformationUnstructuredArray: Option[String],
  remittanceInformationStructured: Option[String],
  remittanceInformationStructuredArray: Option[String],
  requestedExecutionDate: Option[String],
  requestedExecutionTime: Option[String],
) extends BerlinGroupTransactionRequestCommonBodyJson

object BerlinGroupTransactionRequestCommonBodyJsonCommons extends Converter[BerlinGroupTransactionRequestCommonBodyJson, BerlinGroupTransactionRequestCommonBodyJsonCommons]

case class TransactionRequestStatusCommons(
                                            transactionRequestId: String,
                                            bulkTransactionsStatus: List[TransactionStatus]
                                          ) extends TransactionRequestStatus

object TransactionRequestStatusCommons extends Converter[TransactionRequestStatus, TransactionRequestStatusCommons]



case class KycCheckCommons(
                            override val bankId: String,
                            override val customerId: String,
                            override val idKycCheck : String,
                            override val customerNumber : String,
                            override val date : Date,
                            override val how : String,
                            override val staffUserId : String,
                            override val staffName : String,
                            override val satisfied: Boolean,
                            override val comments : String
                          ) extends KycCheck

object KycCheckCommons extends Converter[KycCheck, KycCheckCommons]

case class KycDocumentCommons(
  override val bankId: String,
  override val customerId: String,
  override val idKycDocument : String,
  override val customerNumber : String,
  override val `type` : String,
  override val number : String,
  override val issueDate : Date,
  override val issuePlace : String,
  override val expiryDate : Date
) extends KycDocument

object KycDocumentCommons extends Converter[KycDocument, KycDocumentCommons]

case class KycMediaCommons (
  override val bankId: String,
  override val customerId: String,
  override val idKycMedia : String,
  override val customerNumber : String,
  override val `type` : String,
  override val url : String,
  override val date : Date,
  override val relatesToKycDocumentId : String,
  override val relatesToKycCheckId : String
) extends KycMedia

object KycMediaCommons extends Converter[KycMedia, KycMediaCommons]

case class KycStatusCommons (
  override val bankId: String,
  override val customerId: String,
  override val customerNumber : String,
  override val ok : Boolean,
  override val date : Date
) extends KycStatus

object KycStatusCommons extends Converter[KycStatus, KycStatusCommons]

case class CustomerMessageCommons(
  override val messageId: String,
  override val date: Date,
  override val message: String,
  override val fromDepartment: String,
  override val fromPerson: String,
  override val transport: Option[String] = None,
) extends CustomerMessage
object CustomerMessageCommons extends Converter[CustomerMessage, CustomerMessageCommons]

case class CustomerAttributeCommons (
  override val bankId: BankId,
  override val customerId: CustomerId,
  override val customerAttributeId: String,
  override val attributeType: CustomerAttributeType.Value,
  override val name: String,
  override val value: String,
) extends CustomerAttribute
object CustomerAttributeCommons extends Converter[CustomerAttribute, CustomerAttributeCommons]

case class TransactionAttributeCommons (
    override val  bankId: BankId,
    override val  transactionId: TransactionId,
    override val  transactionAttributeId: String,
    override val  attributeType: TransactionAttributeType.Value,
    override val  name: String,
    override val  value: String,
) extends TransactionAttribute
object TransactionAttributeCommons extends Converter[TransactionAttribute, TransactionAttributeCommons]

case class BankAttributeTraitCommons (
    override val bankId: BankId,
    override val bankAttributeId: String,
    override val attributeType: BankAttributeType.Value,
    override val name: String,
    override val value: String,
    override val isActive: Option[Boolean]
) extends BankAttributeTrait
object BankAttributeTraitCommons extends Converter[BankAttributeTrait, BankAttributeTraitCommons]

case class FXRateCommons (
  override val bankId : BankId,
  override val fromCurrencyCode: String,
  override val toCurrencyCode: String,
  override val conversionValue: Double,
  override val inverseConversionValue: Double,
  override val effectiveDate: Date
) extends FXRate
object FXRateCommons extends Converter[FXRate, FXRateCommons]


case class TransactionRequestTypeChargeCommons (
   override val transactionRequestTypeId: String,
   override val bankId: String,
   override val chargeCurrency: String,
   override val chargeAmount: String,
   override val chargeSummary: String
) extends TransactionRequestTypeCharge
object TransactionRequestTypeChargeCommons extends Converter[TransactionRequestTypeCharge, TransactionRequestTypeChargeCommons]

case class DirectDebitTraitCommons (
    override val directDebitId: String,
    override val bankId: String,
    override val accountId: String,
    override val customerId: String,
    override val userId: String,
    override val counterpartyId: String,
    override val dateSigned: Date,
    override val dateCancelled: Date,
    override val dateStarts: Date,
    override val dateExpires: Date,
    override val active: Boolean
) extends DirectDebitTrait
object DirectDebitTraitCommons extends Converter[DirectDebitTrait, DirectDebitTraitCommons]

case class TransactionStatusCommons(
   override val transactionId : String,
   override val transactionStatus: String,
   override val transactionTimestamp: String
) extends TransactionStatus
object TransactionStatusCommons extends Converter[TransactionStatus, TransactionStatusCommons]

case class ChallengeCommons(
  override val challengeId : String,
  override val transactionRequestId : String,
  override val expectedAnswer : String  ,
  override val expectedUserId : String   ,
  override val salt: String ,
  override val successful: Boolean,

  override val challengeType: String,
  override val consentId: Option[String],
  override val basketId: Option[String] = None,
  override val scaMethod: Option[SCA],
  override val scaStatus: Option[SCAStatus],
  override val authenticationMethodId: Option[String] ,
  override val attemptCounter: Int  = 0, //NOTE: set the default value here, so do not break current connectors
  override val challengePurpose: Option[String] = None,
  override val challengeContextHash: Option[String] = None,
  override val challengeContextStructure: Option[String] = None
) extends ChallengeTrait
object ChallengeCommons extends Converter[ChallengeTrait, ChallengeCommons]

case class ProductFeeTraitCommons(
  bankId: BankId,
  productCode: ProductCode,
  productFeeId: String,
  name: String,
  isActive: Boolean,
  moreInfo: String,
  currency: String,
  amount: BigDecimal,
  frequency: String,
  `type`: String) extends ProductFeeTrait

object ProductFeeTraitCommons extends Converter[ProductFeeTrait, ProductFeeTraitCommons]


case class CustomerAccountLinkTraitCommons(
  customerAccountLinkId: String,
  customerId: String,
  bankId: String,
  accountId: String,
  relationshipType: String) extends CustomerAccountLinkTrait

object CustomerAccountLinkTraitCommons extends Converter[CustomerAccountLinkTrait, CustomerAccountLinkTraitCommons]

case class AgentAccountLinkTraitCommons(
  agentAccountLinkId: String,
  agentId: String,
  bankId: String,
  accountId: String
) extends AgentAccountLinkTrait

object AgentAccountLinkTraitCommons extends Converter[AgentAccountLinkTrait, AgentAccountLinkTraitCommons]


case class CounterpartyLimitTraitCommons(
  counterpartyLimitId: String,
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
  maxNumberOfTransactions: Int,
) extends CounterpartyLimitTrait {
  // Signature uses the json.* aliases, not org.json4s directly - see ApiVersion.scala's toJValue
  // override for why (a ScalaSig-pickled signature naming org.json4s.JsonAST.JValue directly
  // becomes unreadable once json4s-native_2.13 is off obp-api's classpath).
  override def toJValue(implicit format: json.Formats): json.JValue = {
    ("counterparty_limit_id", counterpartyLimitId) ~
      ("bank_id", bankId) ~
      ("account_id",accountId) ~
      ("view_id",viewId) ~
      ("counterparty_id",counterpartyId) ~
      ("currency",currency) ~
      ("max_single_amount", maxSingleAmount) ~
      ("max_monthly_amount", maxMonthlyAmount) ~
      ("max_number_of_monthly_transactions", maxNumberOfMonthlyTransactions) ~
      ("max_yearly_amount", maxYearlyAmount) ~
      ("max_number_of_yearly_transactions", maxNumberOfYearlyTransactions) ~
      ("max_total_amount", maxTotalAmount) ~
      ("max_number_of_transactions", maxNumberOfTransactions)
  }
}

object CounterpartyLimitTraitCommons extends Converter[CounterpartyLimitTrait, CounterpartyLimitTraitCommons]

case class ChallengeTraitCommons(
  challengeId: String,
  transactionRequestId: String,
  expectedAnswer: String,
  expectedUserId: String,
  salt: String,
  successful: Boolean,
  challengeType: String,
  consentId: Option[String],
  basketId: Option[String],
  scaMethod: Option[SCA],
  scaStatus: Option[SCAStatus],
  authenticationMethodId: Option[String],
  attemptCounter: Int,
  challengePurpose: Option[String] = None,
  challengeContextHash: Option[String] = None,
  challengeContextStructure: Option[String] = None) extends ChallengeTrait with JsonFieldReName

object ChallengeTraitCommons extends Converter[ChallengeTrait, ChallengeTraitCommons]


case class PhysicalCardTraitCommons(
  cardId: String,
  bankId: String,
  bankCardNumber: String,
  cardType: String,
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
  allows: List[CardAction],
  account: BankAccount,
  replacement: Option[CardReplacementInfo],
  pinResets: List[PinResetInfo],
  collected: Option[CardCollectionInfo],
  posted: Option[CardPostedInfo],
  customerId: String,
  cvv: Option[String],
  brand: Option[String]
) extends PhysicalCardTrait

object PhysicalCardTraitCommons extends Converter[PhysicalCardTrait, PhysicalCardTraitCommons]

case class TransactionRequestAttributeTraitCommons(
  bankId: BankId,
  transactionRequestId: TransactionRequestId,
  transactionRequestAttributeId: String,
  attributeType: TransactionRequestAttributeType,
  name: String,
  value: String,
  isPersonal: Boolean
) extends TransactionRequestAttributeTrait

object TransactionRequestAttributeTraitCommons extends Converter[TransactionRequestAttributeTrait, TransactionRequestAttributeTraitCommons]


case class EndpointTagTCommons(
  endpointTagId: Option[String],
  operationId: String,
  tagName: String,
  bankId: Option[String]) extends EndpointTagT

object EndpointTagTCommons extends Converter[EndpointTagT, EndpointTagTCommons]

case class StandingOrderTraitCommons(
  standingOrderId: String,
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
  dateCancelled: Date,
  dateStarts: Date,
  dateExpires: Date,
  active: Boolean) extends StandingOrderTrait

object StandingOrderTraitCommons extends Converter[StandingOrderTrait, StandingOrderTraitCommons]

case class UserAuthContextUpdateCommons(
  userAuthContextUpdateId: String,
  userId: String,
  key: String,
  value: String,
  challenge: String,
  status: String,
  consumerId: String) extends UserAuthContextUpdate

object UserAuthContextUpdateCommons extends Converter[UserAuthContextUpdate, UserAuthContextUpdateCommons]

case class ConsentImplicitSCATCommons(
  scaMethod: StrongCustomerAuthentication,
  recipient: String) extends ConsentImplicitSCAT

object ConsentImplicitSCATCommons extends Converter[ConsentImplicitSCAT, ConsentImplicitSCATCommons]


case class CardAttributeCommons(
  bankId: Option[BankId],
  cardId: Option[String],
  cardAttributeId: Option[String],
  name: String,
  attributeType: CardAttributeType.Value,
  value: String
) extends CardAttribute with JsonFieldReName

object CardAttributeCommons extends Converter[CardAttribute, CardAttributeCommons]

  //----------------obp-api moved to here case classes

case class BankRoutingJson(
  scheme: String,
  address: String
)
case class BranchRoutingJsonV141(
                                  scheme: String,
                                  address: String
                                )

case class AccountRoutingJsonV121(
                                   scheme: String,
                                   address: String
                                 )

case class BankAccountRoutings(
  bank:BankRoutingJson,
  account:BranchRoutingJsonV141,
  branch:AccountRoutingJsonV121
)

case class AccountV310Json(
                            bank_id: String ,
                            account_id: String ,
                            account_type : String,
                            account_routings: List[AccountRoutingJsonV121],
                            branch_routings: List[BranchRoutingJsonV141]
                          )

case class CheckbookOrdersJson(
                                account: AccountV310Json ,
                                orders: List[OrderJson]
                              )

case class OrderJson(order: OrderObjectJson)

case class OrderObjectJson(
                            order_id: String,
                            order_date: String,
                            number_of_checkbooks: String,
                            distribution_channel: String,
                            status: String,
                            first_check_number: String,
                            shipping_code: String
                          )

case class ObpApiLoopback(
                          connectorVersion: String,
                          gitCommit: String,
                          durationTime: String
                         ) extends TopicTrait

case class CardObjectJson(
                           card_type: String,
                           card_description: String,
                           use_type: String
                         )

case class TransactionRequestAccount (
                                       bank_id: String,
                                       account_id : String
                                     )

//For SEPA, it needs the iban to find the toCounterpaty--> toBankAccount
case class TransactionRequestIban (iban : String)

case class AmountOfMoneyJsonV121(
                                  currency : String,
                                  amount : String
                                )

case class ToAccountTransferToAccountAccount(
                                              number: String,
                                              iban: String
                                            )

case class FromAccountTransfer(
                                mobile_phone_number: String,
                                nickname: String
                              )

case class TransactionRequestAttributeJsonV400(
  name: String,
  attribute_type: String,
  value: String,
) 

case class ToAccountTransferToAtmKycDocument(
                                              `type`: String,
                                              number: String
                                            )

case class ToAccountTransferToAccount(
                                       name: String,
                                       bank_code: String,
                                       branch_number: String,
                                       account: ToAccountTransferToAccountAccount
                                     )

case class ToAccountTransferToPhone(
                                     mobile_phone_number: String
                                   )

case class TransactionRequestTransferToPhone(
                                              value: AmountOfMoneyJsonV121,
                                              description: String,
                                              message: String,
                                              from: FromAccountTransfer,
                                              to: ToAccountTransferToPhone
                                            ) extends TransactionRequestCommonBodyJSON

case class ToAccountTransferToAtm(
                                   legal_name: String,
                                   date_of_birth: String,
                                   mobile_phone_number: String,
                                   kyc_document: ToAccountTransferToAtmKycDocument
                                 )

case class TransactionRequestTransferToAtm(
                                            value: AmountOfMoneyJsonV121,
                                            description: String,
                                            message: String,
                                            from: FromAccountTransfer,
                                            to: ToAccountTransferToAtm
                                          ) extends TransactionRequestCommonBodyJSON

case class RegulatedEntityTraitCommons(
  entityId :String,
  certificateAuthorityCaOwnerId :String,
  entityName :String,
  entityCode :String,
  entityCertificatePublicKey :String,
  entityType :String,
  entityAddress :String,
  entityTownCity :String,
  entityPostCode :String,
  entityCountry :String,
  entityWebSite :String,
  services :String,
  attributes :Option[List[RegulatedEntityAttributeSimple]]
) extends RegulatedEntityTrait

object RegulatedEntityTraitCommons extends Converter[RegulatedEntityTrait, RegulatedEntityTraitCommons]

//For COUNTERPARTY, it needs the counterparty_id to find the toCounterparty--> toBankAccount
case class TransactionRequestCounterpartyId (counterparty_id : String)

//For AGENT_CASH_WITHDRAWAL, it needs the agent_number to find the toAgent--> toBankAccount
case class TransactionRequestAgentCashWithdrawal (bank_id: String , agent_number : String)

case class TransactionRequestSimple (
  otherBankRoutingScheme: String,
  otherBankRoutingAddress: String,
  otherBranchRoutingScheme: String,
  otherBranchRoutingAddress: String,
  otherAccountRoutingScheme: String,
  otherAccountRoutingAddress: String,
  otherAccountSecondaryRoutingScheme: String,
  otherAccountSecondaryRoutingAddress: String
)

case class TransactionRequestTransferToAccount(
                                                value: AmountOfMoneyJsonV121,
                                                description: String,
                                                transfer_type: String,
                                                future_date: String,
                                                to: ToAccountTransferToAccount
                                              ) extends TransactionRequestCommonBodyJSON
case class PaymentAccount( //This is from berlinGroup
  iban: String
)

case class CancelPayment(canBeCancelled: Boolean, startSca: Option[Boolean])

case class SepaCreditTransfers( //This is from berlinGroup
  debtorAccount: PaymentAccount,
  instructedAmount: AmountOfMoneyJsonV121,
  creditorAccount: PaymentAccount,
  creditorName: String
)

case class SepaCreditTransfersBerlinGroupV13(
  endToEndIdentification: Option[String] = None,
  instructionIdentification: Option[String] = None,
  debtorName: Option[String] = None,
  debtorAccount: PaymentAccount,
  debtorId: Option[String] = None,
  ultimateDebtor: Option[String] = None,
  instructedAmount: AmountOfMoneyJsonV121,
  currencyOfTransfer: Option[String] = None,
  exchangeRateInformation: Option[String] = None,
  creditorAccount: PaymentAccount,
  creditorAgent: Option[String] = None,
  creditorAgentName: Option[String] = None,
  creditorName: String,
  creditorId: Option[String] = None,
  creditorAddress: Option[String] = None,
  creditorNameAndAddress: Option[String] = None,
  ultimateCreditor: Option[String] = None,
  purposeCode: Option[String] = None,
  chargeBearer: Option[String] = None,
  serviceLevel: Option[String] = None,
  remittanceInformationUnstructured: Option[String] = None,
  remittanceInformationUnstructuredArray: Option[String] = None,
  remittanceInformationStructured: Option[String] = None,
  remittanceInformationStructuredArray: Option[String] = None,
  requestedExecutionDate: Option[String] = None,
  requestedExecutionTime: Option[String] = None
) extends  BerlinGroupTransactionRequestCommonBodyJson

case class PeriodicSepaCreditTransfersBerlinGroupV13( 
  endToEndIdentification: Option[String] = None,
  instructionIdentification: Option[String] = None,
  debtorName: Option[String] = None,
  debtorAccount: PaymentAccount,
  debtorId: Option[String] = None,
  ultimateDebtor: Option[String] = None,
  instructedAmount: AmountOfMoneyJsonV121,
  currencyOfTransfer: Option[String] = None,
  exchangeRateInformation: Option[String] = None,
  creditorAccount: PaymentAccount,
  creditorAgent: Option[String] = None,
  creditorAgentName: Option[String] = None,
  creditorName: String,
  creditorId: Option[String] = None,
  creditorAddress: Option[String] = None,
  creditorNameAndAddress: Option[String] = None,
  ultimateCreditor: Option[String] = None,
  purposeCode: Option[String] = None,
  chargeBearer: Option[String] = None,
  serviceLevel: Option[String] = None,
  remittanceInformationUnstructured: Option[String] = None,
  remittanceInformationUnstructuredArray: Option[String] = None,
  remittanceInformationStructured: Option[String] = None,
  remittanceInformationStructuredArray: Option[String] = None,
  requestedExecutionDate: Option[String] = None,
  requestedExecutionTime: Option[String] = None,
  startDate: String,
  executionRule: Option[String] = None,
  endDate: Option[String] = None,
  frequency: String,
  dayOfExecution: Option[String] = None,
) extends BerlinGroupTransactionRequestCommonBodyJson

case class TransactionRequestBodyAllTypes (
                                            @optional
                                            to_sandbox_tan: Option[TransactionRequestAccount],
                                            @optional
                                            to_sepa: Option[TransactionRequestIban],
                                            @optional
                                            to_counterparty: Option[TransactionRequestCounterpartyId],
                                            @optional
                                            to_simple: Option[TransactionRequestSimple] = None,
                                            @optional
                                            to_transfer_to_phone: Option[TransactionRequestTransferToPhone] = None, //TODO not stable
                                            @optional
                                            to_transfer_to_atm: Option[TransactionRequestTransferToAtm]= None,//TODO not stable
                                            @optional
                                            to_transfer_to_account: Option[TransactionRequestTransferToAccount]= None,//TODO not stable
                                            @optional
                                            to_sepa_credit_transfers: Option[SepaCreditTransfers]= None,//TODO not stable, from berlin Group
                                            @optional
                                            to_agent: Option[TransactionRequestAgentCashWithdrawal]= None,
  
                                            value: AmountOfMoney,
                                            description: String
                                          )

case class TransactionRequestCharge(
                                     summary: String,
                                     value : AmountOfMoney
                                   )

case class TransactionRequestChallenge (
                                         id: String,
                                         allowed_attempts : Int,
                                         challenge_type: String
                                       )
case class TransactionRequest (
                                id: TransactionRequestId,
                                `type` : String,
                                from: TransactionRequestAccount,
                                body: TransactionRequestBodyAllTypes,
                                transaction_ids: String,
                                status: String,
                                start_date: Date,
                                end_date: Date,
                                challenge: TransactionRequestChallenge,
                                charge: TransactionRequestCharge,
                                @optional
                                charge_policy: String,
                                @optional
                                counterparty_id :CounterpartyId,
                                @optional
                                name :String,
                                @optional
                                this_bank_id : BankId,
                                @optional
                                this_account_id : AccountId,
                                @optional
                                this_view_id :ViewId,
                                @optional
                                other_account_routing_scheme : String,
                                @optional
                                other_account_routing_address : String,
                                @optional
                                other_bank_routing_scheme : String,
                                @optional
                                other_bank_routing_address : String,
                                @optional
                                is_beneficiary :Boolean,
                                @optional
                                future_date :Option[String] = None,
                                @optional
                                payment_start_date :Option[Date] = None,
                                @optional
                                payment_end_date :Option[Date] = None,
                                @optional
                                payment_execution_Rule :Option[String] = None,
                                @optional
                                payment_frequency :Option[String] = None,
                                @optional
                                payment_day_of_execution :Option[String] = None,
                                @optional
                                user_id :Option[String] = None,
                                @optional
                                on_behalf_of_user_id :Option[String] = None,
                                @optional
                                originator :Option[TransactionRequestOriginator] = None,
                              )

// FATF Recommendation 16 "Travel Rule" originator block (who the payment is from).
// Optional on the TR model; required by the OPEN_CORRIDOR_PROMISE Transaction Request type.
// When not explicitly stored, the v7 JSON response layer virtually fills it from
// the from-account's linked Customer via customer_account_link.
case class TransactionRequestOriginator(
                                         name: String,
                                         address: String,
                                         account_routing: TransactionRequestOriginatorAccountRouting
                                       )

case class TransactionRequestOriginatorAccountRouting(
                                                       scheme: String,
                                                       address: String
                                                     )

case class TransactionRequestBGV1(
  id: TransactionRequestId,
  status: String,
)

case class TransactionRequestBody (
                                    val to: TransactionRequestAccount,
                                    val value : AmountOfMoney,
                                    val description : String
                                  )

case class TransactionRequestReason(
                                     code: String,
                                     documentNumber: Option[String],
                                     amount: Option[String],
                                     currency: Option[String],
                                     description: Option[String]
                                   )

case class Transaction(
                   //A universally unique id
                   @optional
                   uuid: String,
                   //id is unique for transactions of @thisAccount
                   id : TransactionId,
                   thisAccount : BankAccount,
                   otherAccount : Counterparty,
                   //E.g. cash withdrawal, electronic payment, etc.
                   transactionType : String,
                   amount : BigDecimal,
                   //ISO 4217, e.g. EUR, GBP, USD, etc.
                   currency : String,
                   // Bank provided label
                   description : Option[String],
                   // The date the transaction was initiated
                   startDate : Date,
                   // The date when the money finished changing hands
                   finishDate : Option[Date],
                   //the new balance for the bank account
                   balance :  BigDecimal,
                   status : Option[String]
                 ) {

  val bankId = thisAccount.bankId
  val accountId = thisAccount.accountId
}

case class UserCommons(userPrimaryKey : UserPrimaryKey, 
                       userId: String,
                       idGivenByProvider: String, 
                       provider : String,
                       emailAddress : String,
                       name : String,
                       createdByConsentId: Option[String] = None,
                       createdByUserInvitationId: Option[String] = None,
                       isDeleted: Option[Boolean] = None,
                       lastMarketingAgreementSignedDate: Option[Date] = None) extends User

case class InternalBasicUser(
  userId:String,
  emailAddress: String,
  name: String
)


case class BasicUserAuthContext(
  key: String,
  value: String
)

case class ViewBasic(
  id: String,
  name: String,
  description: String,
)
case class BasicLinkedCustomer(
  customerId: String,
  customerNumber: String,
  legalName: String,
)
case class InternalBasicCustomer(
  bankId:String,
  customerId: String,
  customerNumber: String,
  legalName: String,
  dateOfBirth: Date
)
case class InternalBasicCustomers(customers: List[InternalBasicCustomer])

case class InternalBasicUsers(users: List[InternalBasicUser])

case class AccountBasic(
  id: String,
  accountRoutings: List[AccountRouting],
  customerOwners: List[InternalBasicCustomer],
  userOwners: List[InternalBasicUser]
)

case class AuthView(
  view: ViewBasic,
  account:AccountBasic,
)

case class OutboundAdapterCallContext(
  correlationId: String = "",
  sessionId: Option[String] = None, //Only this value must be used for cache key !!!
  consumerId: Option[String] = None,
  generalContext: Option[List[BasicGeneralContext]]= None,
  outboundAdapterAuthInfo: Option[OutboundAdapterAuthInfo] = None,
  outboundAdapterConsenterInfo: Option[OutboundAdapterAuthInfo] = None, //Here consentInfo object structure is the same as AuthInfo. so share the same class
)

case class BasicGeneralContext(
  key: String,
  value: String
)

case class OutboundAdapterAuthInfo(
  userId: Option[String]= None, 
  username: Option[String]= None, 
  linkedCustomers: Option[List[BasicLinkedCustomer]] = None,
  userAuthContext: Option[List[BasicUserAuthContext]]= None,//be set by obp from some endpoints. 
  authViews: Option[List[AuthView]] = None,
)

case class InboundAdapterCallContext(
  correlationId: String = "",
  sessionId: Option[String] = None,
  generalContext: Option[List[BasicGeneralContext]]= None,  //be set by backend, send it back to the header? not finish yet.
)

case class BasicCustomer(
  customerId: String,
  customerNumber: String,
  legalName: String,
)

case class AuthInfo(
  userId: String = "",
  username: String = "",
  cbsToken: String = "",
  isFirst: Boolean = true,
  correlationId: String = "",
  sessionId: String = "",
  linkedCustomers: List[BasicCustomer] = Nil,
  userAuthContexts: List[BasicUserAuthContext]= Nil,
  authViews: List[AuthView] = Nil,
)

case class InternalCustomer(
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

case class InboundBank(
  bankId: String,
  name: String,
  logo: String,
  url: String
)

case class InboundValidatedUser(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  email: String,
  displayName: String
) extends InboundMessageBase

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

//Note: this is used for connector method: 'def getUser(name: String, password: String): Box[InboundUser]'
case class InboundUser(
                        email: String,
                        password: String,
                        displayName: String
                      )

case class InboundExternalUser(
                                aud: String,
                                exp: String,
                                iat: String,
                                iss: String,
                                sub: String,
                                azp: Option[String],
                                email: Option[String],
                                emailVerified: Option[String],
                                name: Option[String],
                                userAuthContext: Option[List[BasicUserAuthContext]] = None
                              )


case class ErrorMessage(code: Int, message: String)

object ErrorMessage {

  def isErrorMessage(jValue: JValue) = jValue match {
    case jObj @JObject(fields) =>
      fields.size == 2 &&
        (jObj \ "code").isInstanceOf[JInt] &&
        (jObj \ "message").isInstanceOf[JString]
    case _ => false
  }
}

/**
 * this case class is a generic list items container for serialized to json string
 * it will serialize to key value way as follow:
 * ListResult("someName", List("value"))
 * --> {"somename": ["value"]}
 *
 * note: the type can be defined as:
 * > case class ListResult[T](name: String, results: List[T])
 * because lift json not support type parameter is another field type parameter when do deserialize
 *
 * when do deserialize to type ListResult, should supply exactly type parameter, should not give wildcard like this:
 * > jValue.extract[ListResult[List[_]]]
 *
 * @param name convert to json single field name
 * @param results convert json single field value
 * @tparam T List type
 */
case class ListResult[+T <: List[_]](name: String, results: T) {

  // T's TypeTag can no longer be synthesised at each of this class's ~50 obp-api call sites once
  // they compile under Scala 3 - TypeTag synthesis is a Scala 2 compiler feature, and it would be
  // needed at every construction site, not just here. itemType's only caller
  // (SwaggerJSONFactory.translateEntity) uses it purely to render a Swagger example's item schema,
  // and every value it is called on there is a curated, non-empty ResourceDoc example - so
  // reflecting the concrete list type off the runtime data (an ordinary value-level operation,
  // not TypeTag synthesis) recovers the same information for that case without touching any
  // call site's construction.
  def itemType: Type = results.headOption
    .map(head => appliedType(typeOf[List[_]].typeConstructor, ReflectUtils.getType(head)))
    .getOrElse(typeOf[List[Any]])

}

// Trading Offer Models
case class TradingOffer(
  offerId: String,
  offerType: String,  // "BUY" | "SELL"
  status: String,     // "active" | "cancelled" | "filled" | "expired"
  offerDetails: TradingOfferDetails,
  accountInfo: TradingAccountInfo,
  executions: List[OfferExecution],
  userId: String,     // Audit: User who created the offer
  consentId: Option[String],  // Audit: Consent ID if applicable
  createdAt: Date,
  updatedAt: Date
)

case class TradingOfferDetails(
  assetCode: String,
  assetAmount: BigDecimal,
  priceCurrency: String,
  priceAmount: BigDecimal,
  settlementAccountId: String,
  expiryDatetime: Option[Date],
  minimumFill: Option[BigDecimal]
)

case class TradingAccountInfo(
  bankId: String,
  accountId: String,
  viewId: String
)

case class OfferExecution(
  executionId: String,
  executedAmount: BigDecimal,
  executedPrice: BigDecimal,
  executedAt: Date,
  counterpartOfferId: String
)

// Market Trading Models
case class MarketOrder(
  orderId: String,
  side: String,              // "BUY" | "SELL"
  price: BigDecimal,
  quantity: BigDecimal,
  accountId: String,
  status: String,            // "active" | "cancelled" | "filled"
  userId: String,            // Audit: User who created the order
  consentId: Option[String], // Audit: Consent ID if applicable
  createdAt: Date,
  updatedAt: Date
)

case class MarketMatch(
  matchId: String,
  orderId: String,
  counterOrderId: String,
  amount: BigDecimal,
  price: BigDecimal,
  userId: String,            // Audit: User who created the match
  consentId: Option[String], // Audit: Consent ID if applicable
  createdAt: Date
)

case class MarketTrade(
  tradeId: String,
  buyOrderId: String,
  sellOrderId: String,
  amount: BigDecimal,
  price: BigDecimal,
  status: String,            // "pending" | "settled"
  userId: String,            // Audit: User who initiated the trade
  consentId: Option[String], // Audit: Consent ID if applicable
  createdAt: Date
)

case class Settlement(
  settlementId: String,
  tradeId: String,
  step: Option[String],
  status: String,            // "pending" | "completed" | "failed"
  userId: String,            // Audit: User who requested settlement
  consentId: Option[String], // Audit: Consent ID if applicable
  createdAt: Date,
  completedAt: Option[Date]
)

case class Deposit(
  depositId: String,
  txHash: String,
  from: String,
  to: String,
  amount: BigDecimal,
  confirmations: Int,
  requiredConfirmations: Int,  // Number of confirmations required (e.g., 12 for Ethereum mainnet)
  status: String,              // "confirmed" | "pending"
  nonce: Option[Long],         // Transaction nonce from blockchain
  gasUsed: Option[Long],       // Gas consumed by the transaction
  errorMessage: Option[String], // Error details if transaction failed
  userId: String,              // Audit: User who notified the deposit
  consentId: Option[String],   // Audit: Consent ID if applicable
  createdAt: Date
)

// TCC (Try-Confirm-Cancel) Payment Authorization for atomic settlement
case class PaymentAuth(
  authId: String,
  tradeId: String,                    // Link to the trade being settled
  buyerAccountId: String,             // Buyer's fiat account (EUR)
  sellerAccountId: String,            // Seller's fiat account (EUR)
  amountFiat: BigDecimal,             // Amount to authorize in fiat currency
  currency: String,                   // Currency code (e.g., "EUR")
  state: String,                      // "PREAUTH" | "CAPTURED" | "RELEASED" | "FAILED"
  holdId: Option[String],             // Link to OBP Account Hold (P5 integration)
  errorMessage: Option[String],       // Error details if state is FAILED
  userId: String,                     // Audit: User who created the authorization
  consentId: Option[String],          // Audit: Consent ID if applicable
  createdAt: Date,
  updatedAt: Date
)

case class Withdrawal(
  withdrawalId: String,
  accountId: String,
  amount: BigDecimal,
  address: String,
  status: String,              // "pending" | "completed" | "failed"
  txHash: Option[String],
  confirmations: Option[Int],  // Current number of confirmations (if tx submitted)
  requiredConfirmations: Int,  // Number of confirmations required
  nonce: Option[Long],         // Transaction nonce from blockchain
  gasUsed: Option[Long],       // Gas consumed by the transaction
  errorMessage: Option[String], // Error details if transaction failed
  userId: String,              // Audit: User who requested withdrawal
  consentId: Option[String],   // Audit: Consent ID if applicable
  createdAt: Date
)
