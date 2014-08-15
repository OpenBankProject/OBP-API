package code.api.v1_3_0

import java.util.Date
import code.model._
import code.model.CardReplacementInfo
import code.model.PhysicalCard
import code.model.PinResetInfo
import net.liftweb.json.{Extraction, JValue}
import code.model.operations._

case class PhysicalCardsJSON(
  cards : List[PhysicalCardJSON])

case class PhysicalCardJSON(
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

case class ReplacementJSON(
  requested_date : Date,
  reason_requested : String)

case class PinResetJSON(
   requested_date : Date,
   reason_requested : String)

case class TransactionsJSON1_3_0(
  transactions: List[TransactionJSON1_3_0])

case class TransactionJSON1_3_0(
  id : String,
  this_account : code.api.v1_2_1.ThisAccountJSON,
  other_account : code.api.v1_2_1.OtherAccountJSON,
  details : TransactionDetailsJSON1_3_0,
  metadata : code.api.v1_2_1.TransactionMetadataJSON
)

case class TransactionDetailsJSON1_3_0(
  status : String,
  `type` : String,
  description : String,
  posted : Date,
  completed : Date,
  new_balance : code.api.v1_2_1.AmountOfMoneyJSON,
  value : code.api.v1_2_1.AmountOfMoneyJSON)

case class OperationJSON1_3_0(
  id : String,
  action : String,
  status : String,
  start_date : Date,
  end_date : Date,
  challenges : List[ChallengeJSON1_3_0])

//TODO: add start_date, expiration_date, allowed_attempts?
case class ChallengeJSON1_3_0(
  id : String,
  question : String,
  label : String)

object JSONFactory1_3_0 {

  implicit val dateFormats = net.liftweb.json.DefaultFormats

  def stringOrNull = code.api.v1_2_1.JSONFactory.stringOrNull _
  def stringOptionOrNull = code.api.v1_2_1.JSONFactory.stringOptionOrNull _

  def optionOrNull[T >: Null](option : Option[T]) : T = {
    option match {
      case Some(t) => t
      case None => null
    }
  }

  def createPinResetJson(resetInfo: PinResetInfo) : PinResetJSON = {
    PinResetJSON(
      requested_date = resetInfo.requestedDate,
      reason_requested = resetInfo.reasonRequested match {
        case FORGOT => "forgot"
        case GOOD_SECURITY_PRACTICE => "routine_security"
      }
    )
  }

  def createReplacementJson(replacementInfo: CardReplacementInfo) : ReplacementJSON = {
    ReplacementJSON(
      requested_date = replacementInfo.requestedDate,
      reason_requested = replacementInfo.reasonRequested match {
        case LOST => "lost"
        case STOLEN => "stolen"
        case RENEW => "renewal"
      }
    )
  }

  def cardActionsToString(action : CardAction) : String = {
    action match {
      case CREDIT => "credit"
      case DEBIT => "debit"
      case CASH_WITHDRAWAL => "cash_withdrawal"
    }
  }

  def createAccountJson(bankAccount : BankAccount, user : User) : code.api.v1_2_1.AccountJSON = {
    val views = bankAccount.views(user).getOrElse(Nil)
    val viewsJson = views.map(code.api.v1_2_1.JSONFactory.createViewJSON)
    code.api.v1_2_1.JSONFactory.createAccountJSON(bankAccount, viewsJson)
  }

  def createPhysicalCardsJSON(cards : Set[PhysicalCard], user : User) : PhysicalCardsJSON = {

    val cardJsons = cards.map(card => {

      PhysicalCardJSON(
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
        networks = card.networks.toList,
        allows = card.allows.map(cardActionsToString).toList,
        account = card.account.map(createAccountJson(_, user)).getOrElse(null),
        replacement = card.replacement.map(createReplacementJson).getOrElse(null),
        pin_reset = card.pinResets.map(createPinResetJson),
        collected = card.collected.map(_.date).getOrElse(null),
        posted = card.posted.map(_.date).getOrElse(null)
      )

    })

    PhysicalCardsJSON(cardJsons.toList)
  }

  def createTransactionsJSON(transactions : List[ModeratedTransaction]) : JValue = {
    val transactionsJson = new TransactionsJSON1_3_0(transactions.map(transactionToJson))
    Extraction.decompose(transactionsJson)
  }

  private def transactionToJson(transaction: ModeratedTransaction) : TransactionJSON1_3_0 = {
    new TransactionJSON1_3_0(
      id = transaction.id,
      this_account = transaction.bankAccount.map(code.api.v1_2_1.JSONFactory.createThisAccountJSON).getOrElse(null),
      other_account = transaction.otherBankAccount.map(code.api.v1_2_1.JSONFactory.createOtherBankAccount).getOrElse(null),
      details = createTransactionDetailsJSON1_3_0(transaction),
      metadata = transaction.metadata.map(code.api.v1_2_1.JSONFactory.createTransactionMetadataJSON).getOrElse(null)
    )
  }

  def createTransactionJSON(transaction : ModeratedTransaction) : JValue = {
    Extraction.decompose(transactionToJson(transaction))
  }

  def createTransactionDetailsJSON1_3_0(transaction : ModeratedTransaction) : TransactionDetailsJSON1_3_0 = {
    new TransactionDetailsJSON1_3_0(
      status = stringOptionOrNull(transaction.status.map(transactionStatusToString)),
      `type` = stringOptionOrNull(transaction.transactionType),
      description = stringOptionOrNull(transaction.description),
      posted = transaction.startDate.getOrElse(null),
      completed = transaction.finishDate.getOrElse(null),
      new_balance = code.api.v1_2_1.JSONFactory.createAmountOfMoneyJSON(transaction.currency, transaction.balance),
      value= code.api.v1_2_1.JSONFactory.createAmountOfMoneyJSON(transaction.currency, transaction.amount.map(_.toString))
    )
  }

  def transactionStatusToString(status : TransactionStatus): String = {
    //we avoid using the standard toString to avoid accidentally changing API behaviour if a status object is renamed
    status match {
      case TransactionStatus_DRAFT => "DRAFT"
      case TransactionStatus_CHALLENGE_PENDING => "CHALLENGE_PENDING"
      case TransactionStatus_APPROVED => "APPROVED"
      case TransactionStatus_PAUSED => "PAUSED"
      case TransactionStatus_CANCELLED => "CANCELLED"
      case TransactionStatus_COMPLETED => "COMPLETED"
    }

  }

  def operationActionToString(action : OperationAction) : String = {
    //we avoid using the standard toString to avoid accidentally changing API behaviour if an action object is renamed
    action match {
      case OperationAction_PAYMENT => "PAYMENT"
    }
  }

  def operationStatusToString(status : OperationStatus) : String = {
    //we avoid using the standard toString to avoid accidentally changing API behaviour if a status object is renamed
    status match {
      case OperationStatus_INITIATED => "INITIATED"
      case OperationStatus_CHALLENGE_PENDING => "CHALLENGE_PENDING"
      case OperationStatus_FAILED => "FAILED"
      case OperationStatus_COMPLETED => "COMPLETED"
    }
  }

  def createOperationJson(operation : Operation) : JValue = {
    val operationJson = OperationJSON1_3_0(
      id = operation.id,
      action = operationActionToString(operation.action),
      status = operationStatusToString(operation.status),
      start_date = operation.startDate,
      end_date = optionOrNull(operation.endDate),
      challenges = operation.challenges.map(challengeJson)
    )
    Extraction.decompose(operationJson)
  }

  def createChallengeJson(challenge : Challenge) : JValue = {
    val jsonObj = challengeJson(challenge)
    Extraction.decompose(jsonObj)
  }

  private def challengeJson(challenge : Challenge) : ChallengeJSON1_3_0 = {
    ChallengeJSON1_3_0(
      id = challenge.id,
      question = challenge.question,
      label = challenge.label
    )
  }

}
