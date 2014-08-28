package code.model.operations

trait Challenge {

  val id : String
  val question : String
  val label : String

}

sealed trait ChallengeResponse

case class PaymentOperationResolved(resolvedOperation : CompletedPayment) extends ChallengeResponse
case object TryChallengeAgain extends ChallengeResponse
case class AnotherChallengeRequired(nextChallengeId : String) extends ChallengeResponse
case class ChallengeFailedOperationFailed(operationId : String) extends ChallengeResponse
