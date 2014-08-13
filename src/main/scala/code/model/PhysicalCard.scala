package code.model

import java.util.Date

/**
 * Represents a physical card (credit, debit, etc.)
 *
 * TODO: id...?
 *
 */
case class PhysicalCard(
  val bankCardNumber : String,
  val nameOnCard : String,
  val issueNumber : String,
  val serialNumber : String,
  val validFrom : Date,
  val expires : Date,
  val enabled : Boolean,
  val cancelled: Boolean,
  val onHotList : Boolean,
  val technology: String,
  val networks : Set[String],
  val allows : Set[CardAction],
  val account : Option[BankAccount],
  val replacement : Option[CardReplacementInfo],
  val pinResets : List[PinResetInfo],
  val collected : Option[CardCollectionInfo],
  val posted : Option[CardPostedInfo])

sealed trait CardAction

//TODO: are these good, or should they be changed (also, are there more actions to add?)
object CREDIT extends CardAction
object DEBIT extends CardAction
object CASH_WITHDRAWAL extends CardAction

sealed trait Network
//TODO: what kind of networks are there?


case class CardReplacementInfo(requestedDate : Date, reasonRequested: CardReplacementReason)

sealed trait CardReplacementReason

object LOST extends CardReplacementReason
object STOLEN extends CardReplacementReason
object RENEW extends CardReplacementReason

case class PinResetInfo(requestedDate: Date, reasonRequested: PinResetReason)

sealed trait PinResetReason

object FORGOT extends PinResetReason
object GOOD_SECURITY_PRACTICE extends PinResetReason

case class CardCollectionInfo(date : Date)

case class CardPostedInfo(date: Date)