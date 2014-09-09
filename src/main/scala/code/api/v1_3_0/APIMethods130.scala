package code.api.v1_3_0

import code.model.operations._
import code.operations.Operations
import code.views.Views
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.common.{Full, Failure, Box}
import code.model.{PhysicalCard, User}
import code.bankconnectors.Connector
import net.liftweb.json.Extraction
import code.util.APIUtil._
import net.liftweb.util.Props
import code.model.{BankAccount, View}
import net.liftweb.json.JValue
import code.payments.{TransferMethods, Payments, PaymentsInjector}
import code.util.Helper._
import net.liftweb.util.Helpers._

case class AnswerChallengeJson(answer : String)

trait APIMethods130 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations1_3_0 = new Object(){

    lazy val getCards : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "cards" :: Nil JsonGet _ => {
        user => {
          val cardsJson = user match {
            case Full(u) => {
              val cards = Connector.connector.vend.getPhysicalCards(u)
              JSONFactory1_3_0.createPhysicalCardsJSON(cards, u)
            }
            case _ => PhysicalCardsJSON(Nil)
          }

          Full(successJsonResponse(Extraction.decompose(cardsJson)))
        }
      }
    }

    def getCardsForBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: bankId :: "cards" :: Nil JsonGet _ => {
        user => {
          val cardsJson = user match {
            case Full(u) => {
              val cards = Connector.connector.vend.getPhysicalCardsForBank(bankId, u)
              JSONFactory1_3_0.createPhysicalCardsJSON(cards, u)
            }
            case _ => PhysicalCardsJSON(Nil)
          }

          Full(successJsonResponse(Extraction.decompose(cardsJson)))
        }
      }
    }

    lazy val getTransferMethods : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: bankId :: "accounts" :: accountId :: "transfer-methods" :: Nil JsonGet _ => {
        user => {

          for {
            u <- user ?~ "Only authenticated users may make this call"
            bankAccount <- BankAccount(bankId, accountId)
            transferMethods <- TransferMethods(bankAccount, u)
          } yield {
            val json = JSONFactory1_3_0.createTransferMethodsJson(transferMethods, bankAccount)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    case class TransactionId(transaction_id : String)

    lazy val makePayment : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: bankId :: "accounts" :: accountId :: viewId :: "transactions" :: Nil JsonPost json -> _ => {
        user =>
          if (Props.getBool("payments_enabled", false)) {
            for {
              u <- user ?~ "User not found"
              fromAccount <- BankAccount(bankId, accountId) ?~ s"account $accountId not found at bank $bankId"
              owner <- booleanToBox(u.ownerAccess(fromAccount), "user does not have access to owner view")
              view <- View.fromUrl(viewId, fromAccount) ?~ s"view $viewId not found"//TODO: need to check if this view has permission to make payments
              makeTransJson <- tryo{json.extract[code.api.v1_2_1.MakePaymentJson]} ?~ {"wrong json format"}
              toAccount <- {
                BankAccount(makeTransJson.bank_id, makeTransJson.account_id) ?~! {"Intended recipient with " +
                  s" account id ${makeTransJson.account_id} at bank ${makeTransJson.bank_id}" +
                  " not found"}
              }
              sameCurrency <- booleanToBox(fromAccount.currency == toAccount.currency, {
                s"Cannot send payment to account with different currency (From ${fromAccount.currency} to ${toAccount.currency}"
              })
              rawAmt <- tryo {BigDecimal(makeTransJson.amount)} ?~! s"amount ${makeTransJson.amount} not convertible to number"
              isPositiveAmtToSend <- booleanToBox(rawAmt > BigDecimal("0"), s"Can't send a payment with a value of 0 or less. (${makeTransJson.amount})")
              paymentOperation <- Payments.payment(view, fromAccount, toAccount, rawAmt)
            } yield {
              import code.model.operations._

              //TODO: how can we make sure this stays the correct url?
              val operationLocation = s"/obp/v1.3.0/operations/${paymentOperation.id}"
              val operationHeaders = List(("location", operationLocation))

              paymentOperation match {
                case completed : CompletedPayment => successJsonResponse(JSONFactory1_3_0.createTransactionJSON(view.moderate(completed.transaction)), 201, operationHeaders)
                case initiated : InitiatedPayment => successJsonResponse(JSONFactory1_3_0.createTransactionJSON(view.moderate(initiated.transaction)), 201, operationHeaders)
                case failed : FailedPayment => errorJsonResponse(failed.failureMessage)
                case challengePending : ChallengePendingPayment => acceptedJsonResponse(operationHeaders)
              }
            }
          }
          else{
            Failure("Sorry, payments are not enabled in this API instance.")
          }
      }
    }

    lazy val getOperationById : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "operations" :: operationId :: Nil JsonGet _ => {
        user =>
          for {
            operation <- Operations.operations.vend.getOperation(operationId, user)
          } yield {
            successJsonResponse(JSONFactory1_3_0.createOperationJson(operation))
          }
      }
    }

    lazy val answerChallenge : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "challenges" :: challengeId :: "answers" :: Nil JsonPost json -> _ => {
        //TODO: check user is allowed to answer challenge?
        user =>
          for {
            answerJson <- tryo{json.extract[AnswerChallengeJson]}
            response <- Operations.operations.vend.answerChallenge(challengeId, answerJson.answer)
          } yield {
            response match {
              case PaymentOperationResolved(resolvedOp) => {
                val t = resolvedOp.transaction
                //TODO: make sure this link is the right one for this api version
                val transactionLocation = s"/obp/v1.3.0/banks/${t.bankPermalink}/accounts/${t.accountPermalink}/owner/transactions/${t.id}/transaction"
                noContentJsonResponse(List("location" -> transactionLocation))
              }
              case TryChallengeAgain => {
                //TODO: make sure this link is the right one for this api version
                val headers = List("location" -> s"challenges/$challengeId/}")
                errorJsonResponse("incorrect answer", 400, headers)
              }
              case AnotherChallengeRequired(nextChallengeId) => {
                //TODO: make sure this link is the right one for this api version
                val headers = List("location" -> s"challenges/$nextChallengeId/}")
                noContentJsonResponse(headers)
              }
              case ChallengeFailedOperationFailed(failedOpId) => {
                //TODO: make sure this link is the right one for this api version
                val headers = List("location" -> s"operations/$failedOpId/}")
                errorJsonResponse("incorrect answer. operation failed.", 400, headers)
              }
            }
          }
      }
    }

  }

}
