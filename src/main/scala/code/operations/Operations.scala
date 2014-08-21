package code.operations

import code.model.{ModeratedTransaction, Transaction, User}
import code.model.operations.{ChallengeResponse, CompletedPayment, Operation}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object Operations extends SimpleInjector {

  val operations = new Inject(buildOne _) {}

  def buildOne: Operations = MappedOperations

}

trait Operations {
  def getOperation(operationId : String, user : Box[User]) : Box[Operation]

  def saveNewCompletedPayment(transaction : Transaction) : CompletedPayment

  def answerChallenge(challengeId : String, answer : String) : Box[ChallengeResponse]
}