package code.api.v1_3_0

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
import code.payments.PaymentsInjector
import code.util.Helper._
import net.liftweb.util.Helpers._

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
            } yield {

              val paymentOperation = PaymentsInjector.processor.vend.makePayment(fromAccount, toAccount, rawAmt)

              import code.model.operations._

              paymentOperation match {
                case completed : CompletedPayment => {
                  //payments (and operations) are currently only visible to the account owner, so we moderate it
                  // with the owner view
                  val ownerView = Views.views.vend.view("owner", accountId, bankId)
                  ownerView match {
                    case Full(v) => {
                      //TODO add location header for the operation
                      successJsonResponse(JSONFactory1_3_0.createTransactionJSON(v.moderate(completed.transaction)))
                    }
                    case _ =>  errorJsonResponse("server error")
                  }

                }
                case failed : FailedPayment => {
                  errorJsonResponse(failed.failureMessage)
                }
                case challengePending : ChallengePendingPayment => {
                  errorJsonResponse("TODO")
                }
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
            operation <- Operations.operations.vend.getOperation(operationId, user) ?~! s"Operation with id $operationId not found"
          } yield {
            successJsonResponse(JSONFactory1_3_0.createOperationJson(operation))
          }
      }
    }

  }

}
