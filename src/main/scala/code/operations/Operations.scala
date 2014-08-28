package code.operations

import code.model.{ModeratedTransaction, Transaction, User}
import code.model.operations._
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object Operations extends SimpleInjector {

  val operations = new Inject(buildOne _) {}

  def buildOne: Operations = MappedOperations

}

trait Operations {
  def getOperation(operationId : String, user : Box[User]) : Box[Operation]

  def saveNewCompletedPayment(transaction : Transaction) : CompletedPayment

  def saveNewFailedPayment(bankPermalink : String, accountPermalink : String, failureMessage : String) : FailedPayment

  def saveNewChallengePendingPayment(fromAccountBankPermalink : String, fromAccountPermalink : String, toAccountBankPermalink : String,
                                     toAccountPermalink : String, amount : BigDecimal, challenges : List[Challenge]) : ChallengePendingPayment

  def answerChallenge(challengeId : String, answer : String) : Box[ChallengeResponse]
}