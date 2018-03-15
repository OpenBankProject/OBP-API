package code.model

import java.util.Date

/**
 * Represents a physical card (credit, debit, etc.)
 *
 * TODO: id...?
 *
 */

trait PhysicalCardTrait {
  def bankId: String
  def bankCardNumber: String
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
}

case class PhysicalCard  (
  val bankId: String,
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
  val networks : List[String],
  val allows : List[CardAction],
  val account : BankAccount,
  val replacement : Option[CardReplacementInfo],
  val pinResets : List[PinResetInfo],
  val collected : Option[CardCollectionInfo],
  val posted : Option[CardPostedInfo]) extends PhysicalCardTrait


sealed trait CardAction

case object CardAction {
  //TODO: are these good, or should they be changed (also, are there more actions to add?)
  case object CREDIT extends CardAction
  case object DEBIT extends CardAction
  case object CASH_WITHDRAWAL extends CardAction

  def valueOf(value: String) = value match {
    case "credit" => CREDIT
    case "debit" => DEBIT
    case "cash_withdrawal" => CASH_WITHDRAWAL
    case _ => throw new IllegalArgumentException ("Incorrect CardAction value: " + value)
  }
  val availableValues = "credit" :: "debit" :: "cash_withdrawal" :: Nil
}



sealed trait Network
//TODO: what kind of networks are there?


case class CardReplacementInfo(requestedDate : Date, reasonRequested: CardReplacementReason)

sealed trait CardReplacementReason

case object CardReplacementReason {
  case object LOST extends CardReplacementReason
  case object STOLEN extends CardReplacementReason
  case object RENEW extends CardReplacementReason

  def valueOf(value: String) = value match {
    case "LOST" => LOST
    case "STOLEN" => STOLEN
    case "RENEW" => RENEW
    case _ => throw new IllegalArgumentException ("Incorrect CardReplacementReason value: " + value)
  }
  val availableValues = "LOST" :: "STOLEN" :: "RENEW" :: Nil
}


case class PinResetInfo(requestedDate: Date, reasonRequested: PinResetReason)

sealed trait PinResetReason

case object PinResetReason {
  case object FORGOT extends PinResetReason
  case object GOOD_SECURITY_PRACTICE extends PinResetReason

  def valueOf(value: String) = value match {
    case "FORGOT" => FORGOT
    case "GOOD_SECURITY_PRACTICE" => GOOD_SECURITY_PRACTICE
    case _ => throw new IllegalArgumentException ("Incorrect PinResetReason value: " + value)
  }
  val availableValues = "FORGOT" :: "GOOD_SECURITY_PRACTICE" :: Nil
}


case class CardCollectionInfo(date : Date)

case class CardPostedInfo(date: Date)