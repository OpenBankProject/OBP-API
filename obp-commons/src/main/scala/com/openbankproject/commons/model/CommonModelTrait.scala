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

import java.util.Date

import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import com.openbankproject.commons.model.enums._
import com.openbankproject.commons.util.ReflectUtils

import scala.collection.immutable.List
import scala.reflect.runtime.universe

/**
  * a mark trait, any type that extends this trait will rename field from Camel-Case to snakify naming
  */
trait JsonFieldReName

/**
*
* This is the base class for all kafka outbound case class
* action and messageFormat are mandatory
* The optionalFields can be any other new fields .
*/
abstract class OutboundMessageBase(
  optionalFields: String*
) {
  def action: String
  def messageFormat: String
}

abstract class InboundMessageBase(
  optionalFields: String*
) {
  def errorCode: String
}

case class InboundStatusMessage(
  source: String,
  status: String,
  errorCode: String,
  text: String
)

case class Status(
                   errorCode: String,
                   backendMessages: List[InboundStatusMessage]
                 ) {
  def hasError = this.errorCode != null && errorCode.trim != ""
  def hasNoError = !hasError
}

case class InboundAdapterInfoInternal(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  name: String,
  version: String,
  git_commit: String,
  date: String
) extends InboundMessageBase


trait AccountApplication {
  def accountApplicationId: String
  def productCode: ProductCode
  def userId: String
  def customerId: String
  def dateOfApplication: Date
  def status: String
}

trait AccountAttribute {
  def bankId: BankId
  def accountId: AccountId
  def productCode: ProductCode
  def accountAttributeId: String
  def name: String
  def attributeType: AccountAttributeType.Value
  def value: String
}

trait CardAttribute {
  def bankId: Option[BankId]
  def cardId: Option[String]
  def cardAttributeId: Option[String]
  def name: String
  def attributeType: CardAttributeType.Value
  def value: String
}
case class CardAttributeCommons(
  bankId: Option[BankId],
  cardId: Option[String],
  cardAttributeId: Option[String],
  name: String,
  attributeType: CardAttributeType.Value,
  value: String
) extends CardAttribute with JsonFieldReName

object CardAttributeCommons extends Converter[CardAttribute, CardAttributeCommons]

trait AtmT {
  def atmId: AtmId
  def bankId: BankId
  def name: String
  def address: AddressT
  def location: LocationT
  def meta: MetaT
  def OpeningTimeOnMonday: Option[String]
  def ClosingTimeOnMonday: Option[String]
  def OpeningTimeOnTuesday: Option[String]
  def ClosingTimeOnTuesday: Option[String]
  def OpeningTimeOnWednesday: Option[String]
  def ClosingTimeOnWednesday: Option[String]
  def OpeningTimeOnThursday: Option[String]
  def ClosingTimeOnThursday: Option[String]
  def OpeningTimeOnFriday: Option[String]
  def ClosingTimeOnFriday: Option[String]
  def OpeningTimeOnSaturday: Option[String]
  def ClosingTimeOnSaturday: Option[String]
  def OpeningTimeOnSunday: Option[String]
  def ClosingTimeOnSunday: Option[String]
  def isAccessible: Option[Boolean]
  def locatedAt: Option[String]
  def moreInfo: Option[String]
  def hasDepositCapability: Option[Boolean]
}

// MappedBranch will implement this.
// The trait defines the fields the API will interact with.

trait BranchT {
  def branchId: BranchId
  def bankId: BankId
  def name: String
  def address: Address
  def location: Location
  def lobbyString: Option[LobbyStringT]
  def driveUpString: Option[DriveUpStringT]
  def meta: Meta
  def branchRouting: Option[RoutingT]
  def lobby: Option[Lobby]
  def driveUp: Option[DriveUp]
  // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
  def isAccessible : Option[Boolean]
  def accessibleFeatures: Option[String]
  def branchType : Option[String]
  def moreInfo : Option[String]
  def phoneNumber : Option[String]
  // marks whether this branch is deleted
  def isDeleted : Option[Boolean]
}

trait CustomerAddress {
  def customerId: String
  def customerAddressId: String
  def line1: String
  def line2: String
  def line3: String
  def city: String
  def county: String
  def state: String
  def postcode: String
  def countryCode: String
  def status: String
  def tags: String
  def insertDate: Date
}

// This is the common InboundAccount from all Kafka/remote, not finished yet.
trait InboundAccount{
  def bankId: String
  def branchId: String
  def accountId: String
  def accountNumber: String
  def accountType: String
  def balanceAmount: String
  def balanceCurrency: String
  def owners: List[String]
  def viewsToGenerate: List[String]
  def bankRoutingScheme:String
  def bankRoutingAddress:String
  def branchRoutingScheme:String
  def branchRoutingAddress:String
  def accountRoutingScheme:String
  def accountRoutingAddress:String
}

trait Meeting {
  def meetingId: String
  def providerId: String
  def purposeId: String
  def bankId: String
  def present: MeetingPresent
  def keys: MeetingKeys
  def when: Date
  def creator: ContactDetails
  def invitees: List[Invitee]
}

trait ProductAttribute {
  def bankId: BankId

  def productCode: ProductCode

  def productAttributeId: String

  def name: String

  def attributeType: ProductAttributeType.Value

  def value: String
}


trait ProductCollection {
  def collectionCode: String
  def productCode: String
}


trait ProductCollectionItem {
  def collectionCode: String
  def memberProductCode: String
}

trait TaxResidence {
  def customerId: String
  def taxResidenceId: String
  def domain: String
  def taxNumber: String
}

trait UserAuthContext {
  def userAuthContextId : String
  def userId : String
  def key : String
  def value : String
}


trait AddressT {
  def line1 : String
  def line2 : String
  def line3 : String
  def city : String
  def county : Option[String]
  def state : String
  def postCode : String
  //ISO_3166-1_alpha-2
  def countryCode : String
}

trait LocationT {
  def latitude: Double
  def longitude: Double
}

trait MetaT {
  def license : LicenseT
}


trait LicenseT {
  def id : String
  def name : String
}

@deprecated("Use Lobby instead which contains detailed fields, not this string","24 July 2017")
trait LobbyStringT {
  def hours : String
}


@deprecated("Use DriveUp instead which contains detailed fields now, not this string","24 July 2017")
trait DriveUpStringT {
  def hours : String
}


trait RoutingT {
  def scheme: String
  def address: String
}

// @see 'case request: TopicTrait' in  code/bankconnectors/kafkaStreamsHelper.scala
// This is for Kafka topics for both North and South sides.
// In OBP-API, these topics will be created automatically.
trait TopicTrait {

  // return constructor parameter value list, the order is just the same as constructor
  lazy val nameToValue: List[(String, Any)] = {
    val tp = ReflectUtils.getType(this)
    val instanceMirror = ReflectUtils.getInstanceMirror(this)
    tp.decls.collectFirst {
      case x if x.isConstructor =>
        val constructorArgs: List[universe.Symbol] = x.asMethod.paramLists.head
        val nameToMethodSymbol: Map[universe.Name, universe.MethodSymbol] = tp.decls.filter(it => it.isMethod && it.asMethod.paramLists.isEmpty)
          .map(it => it.name -> it.asMethod).toMap
        val argList: List[(String, Any)] = constructorArgs.map { arg =>
          val method = nameToMethodSymbol(arg.name)
          val argValue = instanceMirror.reflectMethod(method).apply()
          arg.name.decodedName.toString -> argValue
        }
        argList
    }.get
  }
}

//high level of four different kinds of transaction request types: FREE_FROM, SANDBOXTAN, COUNTERPATY and SEPA.
//They share the same AmountOfMoney and description fields
//Note : in scala case-to-case inheritance is prohibited, so used trait instead
trait TransactionRequestCommonBodyJSON {
  val value : AmountOfMoneyJsonV121
  val description: String
}

trait Product {
  def code : ProductCode
  def parentProductCode : ProductCode
  def bankId : BankId
  def name : String
  def category: String
  def family : String
  def superFamily : String
  def moreInfoUrl: String
  def details :String
  def description: String
  def meta : Meta
}

trait KycCheck {
  def bankId: String
  def customerId: String
  def idKycCheck : String
  def customerNumber : String
  def date : Date
  def how : String
  def staffUserId : String
  def staffName : String
  def satisfied: Boolean
  def comments : String
}


trait KycDocument {
  def bankId: String
  def customerId: String
  def idKycDocument : String
  def customerNumber : String
  def `type` : String
  def number : String
  def issueDate : Date
  def issuePlace : String
  def expiryDate : Date
}

trait KycMedia {
  def bankId: String
  def customerId: String
  def idKycMedia : String
  def customerNumber : String
  def `type` : String
  def url : String
  def date : Date
  def relatesToKycDocumentId : String
  def relatesToKycCheckId : String
}

trait KycStatus {
  def bankId: String
  def customerId: String
  def customerNumber : String
  def ok : Boolean
  def date : Date
}

trait CustomerMessage {
  //TODO: message language?
  def messageId : String
  def date : Date
  def message : String
  def fromDepartment : String
  def fromPerson : String
}

trait CustomerAttribute {
  def bankId: BankId
  def customerId: CustomerId
  def customerAttributeId: String
  def attributeType: CustomerAttributeType.Value
  def name: String
  def value: String
}

trait TransactionAttribute {
  def bankId: BankId
  def transactionId: TransactionId
  def transactionAttributeId: String
  def attributeType: TransactionAttributeType.Value
  def name: String
  def value: String
}

trait FXRate {
  def bankId : BankId
  def fromCurrencyCode: String
  def toCurrencyCode: String
  def conversionValue: Double
  def inverseConversionValue: Double
  def effectiveDate: Date
}

trait TransactionRequestTypeCharge {
  def transactionRequestTypeId: String
  def bankId: String
  def chargeCurrency: String
  def chargeAmount: String
  def chargeSummary: String
}

trait TransactionRequestReasonsTrait {
  def transactionRequestReasonId: String
  def transactionRequestId: String
  def code: String
  def documentNumber: String
  def amount: String
  def currency: String
  def description: String
}

trait DirectDebitTrait {
  def directDebitId: String
  def bankId: String
  def accountId: String
  def customerId: String
  def userId: String
  def counterpartyId: String
  def dateSigned: Date
  def dateCancelled: Date
  def dateStarts: Date
  def dateExpires: Date
  def active: Boolean
}

trait ChallengeTrait {
  def challengeId : String
  def transactionRequestId : String
  def expectedAnswer : String
  def expectedUserId : String
  def salt : String
  def successful : Boolean

  //OBP will support many different challenge types:
  //OBP_Payment, OBP_Consent, OBP_General, BerlinGroup_Payment, BerlinGroup_Consent,
  def challengeType: String

  //NOTE: following are from BerlinGroup, we try to share the same challenges for different standard.
  //for OBP standard, all the following can be Optional: 
  def consentId: Option[String] // Note: consentId and transactionRequestId are exclusive here.
  def scaMethod: Option[SCA]
  def scaStatus: Option[SCAStatus]
  def authenticationMethodId: Option[String]
}


//---------------------------------------- trait dependents of case class

@deprecated("Use Lobby instead which contains detailed fields, not this string","24 July 2017")
case class LobbyString (hours : String) extends LobbyStringT


@deprecated("Use DriveUp instead which contains detailed fields now, not this string","24 July 2017")
case class  DriveUpString (hours : String ) extends DriveUpStringT


case class  Routing (
   scheme: String,
   address: String
) extends RoutingT

case class BranchId(value : String) {
  override def toString = value
}

object BranchId {
  def unapply(id : String) = Some(BranchId(id))
}

case class Address(
                    line1 : String,
                    line2 : String,
                    line3 : String,
                    city : String,
                    county : Option[String],
                    state : String,
                    postCode : String,
                    //ISO_3166-1_alpha-2
                    countryCode : String) extends AddressT


case class MeetingPresent(
                           staffUserId: String,
                           customerUserId: String
                         )


case class Location(
                     latitude: Double,
                     longitude: Double,
                     date : Option[Date],
                     user: Option[BasicResourceUser]
                   ) extends LocationT

/*
Basic User data
 */
case class BasicResourceUser(
                              userId: String, // Should come from Resource User Id
                              provider: String,
                              username: String
                            )


case class Meta (
                  license : License
                ) extends MetaT



case class License (
                     id : String,
                     name : String
                   ) extends LicenseT

case class Lobby(
                  monday: List[OpeningTimes],
                  tuesday: List[OpeningTimes],
                  wednesday: List[OpeningTimes],
                  thursday: List[OpeningTimes],
                  friday: List[OpeningTimes],
                  saturday: List[OpeningTimes],
                  sunday: List[OpeningTimes]
                )

case class OpeningTimes(
                         openingTime: String,
                         closingTime: String
                       )

case class DriveUp(
                    monday: OpeningTimes,
                    tuesday: OpeningTimes,
                    wednesday: OpeningTimes,
                    thursday: OpeningTimes,
                    friday: OpeningTimes,
                    saturday: OpeningTimes,
                    sunday: OpeningTimes
                  )

case class MeetingKeys (
                         sessionId: String,
                         customerToken: String,
                         staffToken: String
                       )

case class ContactDetails(
                           name: String,
                           phone: String,
                           email: String
                         )

case class Invitee(
                    contactDetails: ContactDetails,
                    status: String
                  )
// Good to have this as a class because when passing as argument, we get compiler error if passing the wrong type.

case class ProductCode(value : String)

object ProductCode {
  def unapply(code : String) = Some(ProductCode(code))
}

case class AtmId(value : String){
  override def toString() = value
}

object AtmId {
  def unapply(id : String) = Some(AtmId(id))
}