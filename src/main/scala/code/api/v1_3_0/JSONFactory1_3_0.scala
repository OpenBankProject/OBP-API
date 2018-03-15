package code.api.v1_3_0

import java.util.Date
import code.model._
import code.model.CardReplacementInfo
import code.model.PhysicalCard
import code.model.PinResetInfo
import code.views.Views

case class PhysicalCardsJSON(
  cards : List[PhysicalCardJSON])

case class PhysicalCardJSON(
   bank_id : String,
   bank_card_number : String,
   name_on_card : String,
   issue_number : String,
   serial_number : String,
   valid_from_date : Date,
   expires_date : Date,
   enabled : Boolean,
   cancelled : Boolean,
   on_hot_list : Boolean,
   technology : String,
   networks : List[String],
   allows : List[String],
   account : code.api.v1_2_1.AccountJSON,
   replacement : ReplacementJSON,
   pin_reset : List[PinResetJSON],
   collected : Date,
   posted : Date)

case class PostPhysicalCardJSON(
                             bank_card_number : String,
                             name_on_card : String,
                             issue_number : String,
                             serial_number : String,
                             valid_from_date : Date,
                             expires_date : Date,
                             enabled : Boolean,
                             technology : String,
                             networks : List[String],
                             allows : List[String],
                             account_id : String,
                             replacement : ReplacementJSON,
                             pin_reset : List[PinResetJSON],
                             collected : Date,
                             posted : Date)

case class ReplacementJSON(
  requested_date : Date,
  reason_requested : String)

case class PinResetJSON(
   requested_date : Date,
   reason_requested : String)

object JSONFactory1_3_0 {

  def stringOrNull(text : String) =
    if(text == null || text.isEmpty)
      null
    else
      text

  def createPinResetJson(resetInfo: PinResetInfo) : PinResetJSON = {
    PinResetJSON(
      requested_date = resetInfo.requestedDate,
      reason_requested = resetInfo.reasonRequested match {
        case PinResetReason.FORGOT => PinResetReason.FORGOT.toString
        case PinResetReason.GOOD_SECURITY_PRACTICE => PinResetReason.GOOD_SECURITY_PRACTICE.toString
      }
    )
  }

  def createReplacementJson(replacementInfo: CardReplacementInfo) : ReplacementJSON = {
    ReplacementJSON(
      requested_date = replacementInfo.requestedDate,
      reason_requested = replacementInfo.reasonRequested match {
        case CardReplacementReason.LOST => CardReplacementReason.LOST.toString
        case CardReplacementReason.STOLEN => CardReplacementReason.STOLEN.toString
        case CardReplacementReason.RENEW => CardReplacementReason.RENEW.toString
        case _ => ""
      }
    )
  }

  def cardActionsToString(action : CardAction) : String = {
    action match {
      case CardAction.CREDIT => "credit"
      case CardAction.DEBIT => "debit"
      case CardAction.CASH_WITHDRAWAL => "cash_withdrawal"
    }
  }

  def createAccountJson(bankAccount : BankAccount, user : User) : code.api.v1_2_1.AccountJSON = {
    val views = Views.views.vend.viewsForAccount(BankIdAccountId(bankAccount.bankId, bankAccount.accountId))
    val viewsJson = views.map(code.api.v1_2_1.JSONFactory.createViewJSON)
    code.api.v1_2_1.JSONFactory.createAccountJSON(bankAccount, viewsJson)
  }

  def createPhysicalCardJSON(card: PhysicalCard, user : User): PhysicalCardJSON = {
    PhysicalCardJSON(
      bank_id = stringOrNull(card.bankId),
      bank_card_number = stringOrNull(card.bankCardNumber),
      name_on_card = stringOrNull(card.nameOnCard),
      issue_number = stringOrNull(card.issueNumber),
      serial_number = stringOrNull(card.serialNumber),
      valid_from_date = card.validFrom,
      expires_date = card.expires,
      enabled = card.enabled,
      cancelled = card.cancelled,
      on_hot_list = card.onHotList,
      technology = stringOrNull(card.technology),
      networks = card.networks,
      allows = card.allows.map(cardActionsToString).toList,
      account = createAccountJson(card.account, user),
      replacement = card.replacement.map(createReplacementJson).getOrElse(null),
      pin_reset = card.pinResets.map(createPinResetJson),
      collected = card.collected.map(_.date).getOrElse(null),
      posted = card.posted.map(_.date).getOrElse(null)
    )
  }

  def createPhysicalCardsJSON(cards : List[PhysicalCard], user : User) : PhysicalCardsJSON = {

    val cardJsons = cards.map(card => createPhysicalCardJSON(card, user))

    PhysicalCardsJSON(cardJsons)
  }

}
