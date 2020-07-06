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

import com.openbankproject.commons.model.enums.{SimpleEnum, SimpleEnumCollection}

import scala.collection.immutable.ListMap

/**
 * Represents a physical card (credit, debit, etc.)
 *
 * TODO: id...?
 *
 */

trait PhysicalCardTrait {
  def cardId: String // This is the UUID for the card.
  def bankId: String
  def bankCardNumber: String
  def cardType: String
  def nameOnCard: String
  def issueNumber: String
  def serialNumber: String
  def validFrom: Date
  def expires: Date
  def enabled: Boolean
  def cancelled: Boolean
  def onHotList: Boolean
  def technology: String
  def networks: List[String]
  def allows: List[CardAction]
  def account: BankAccount
  def replacement: Option[CardReplacementInfo]
  def pinResets: List[PinResetInfo]
  def collected: Option[CardCollectionInfo]
  def posted: Option[CardPostedInfo]
  def customerId: String
}

case class PhysicalCard  (
  val cardId : String,
  val bankId: String,
  val bankCardNumber : String,
  val cardType : String,
  val nameOnCard : String,
  val issueNumber : String,
  val serialNumber : String,
  val validFrom : Date,
  val expires : Date,
  val enabled : Boolean,
  val cancelled: Boolean,
  val onHotList : Boolean,
  val technology: String,
  val networks : List[String],
  val allows : List[CardAction],
  val account : BankAccount,
  val replacement : Option[CardReplacementInfo],
  val pinResets : List[PinResetInfo],
  val collected : Option[CardCollectionInfo],
  val posted : Option[CardPostedInfo],
  val customerId: String
) extends PhysicalCardTrait


sealed trait CardAction extends SimpleEnum

case object CardAction extends SimpleEnumCollection[CardAction] {
  //TODO: are these good, or should they be changed (also, are there more actions to add?)
  case object CREDIT extends CardAction
  case object DEBIT extends CardAction
  case object CASH_WITHDRAWAL extends CardAction

  override def nameToValue: Map[String, CardAction] = ListMap(
    "credit"-> CREDIT,
    "debit" -> DEBIT,
    "cash_withdrawal" -> CASH_WITHDRAWAL
  )
}

sealed trait Network extends SimpleEnum
//TODO: what kind of networks are there?
object Network extends SimpleEnumCollection[Network] {
  override def nameToValue: Map[String, Network] = ListMap()
}


case class CardReplacementInfo(requestedDate : Date, reasonRequested: CardReplacementReason)

sealed trait CardReplacementReason extends SimpleEnum

case object CardReplacementReason  extends SimpleEnumCollection[CardReplacementReason] {
  case object LOST extends CardReplacementReason
  case object STOLEN extends CardReplacementReason
  case object RENEW extends CardReplacementReason
  case object FIRST extends CardReplacementReason

  override def nameToValue: Map[String, CardReplacementReason] = ListMap(
     "LOST" -> LOST,
     "STOLEN" -> STOLEN,
     "RENEW" -> RENEW,
     "FIRST" -> FIRST
  )
}


case class PinResetInfo(requestedDate: Date, reasonRequested: PinResetReason)

sealed trait PinResetReason extends SimpleEnum

case object PinResetReason  extends SimpleEnumCollection[PinResetReason]{
  case object FORGOT extends PinResetReason
  case object GOOD_SECURITY_PRACTICE extends PinResetReason

  override def nameToValue: Map[String, PinResetReason] = ListMap(
    "FORGOT" -> FORGOT,
    "GOOD_SECURITY_PRACTICE" -> GOOD_SECURITY_PRACTICE
  )
}


case class CardCollectionInfo(date : Date)

case class CardPostedInfo(date: Date)