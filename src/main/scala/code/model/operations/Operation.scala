package code.model.operations

import java.util.Date

import code.model.Transaction

sealed trait Operation {
  val id : String
  val action : OperationAction
  val status : OperationStatus
  val startDate : Date
  val endDate : Option[Date]
  val challenges : List[Challenge]
}

sealed trait PaymentOperation extends Operation {
  override val action : OperationAction = OperationAction_PAYMENT
}

class CompletedPayment(
  val operationId : String, val transaction : Transaction,
  val startDate : Date, val finishDate : Date) extends PaymentOperation {

  override val id = operationId
  override val status = OperationStatus_COMPLETED
  override val endDate = Some(finishDate)
  override val challenges = Nil
}

class FailedPayment(
  val operationId : String, val failureMessage : String,
  val startDate : Date, val finishDate : Date) extends PaymentOperation {

  override val id = operationId
  override val status = OperationStatus_FAILED
  override val endDate = Some(finishDate)
  override val challenges = Nil
}

class ChallengePendingPayment(
  val operationId : String,
  val startDate : Date,
  val challenges : List[Challenge]) extends PaymentOperation {

  override val id = operationId
  override val status = OperationStatus_CHALLENGE_PENDING
  override val endDate = None

}

sealed trait OperationAction
object OperationAction_PAYMENT extends OperationAction

sealed trait OperationStatus
object OperationStatus_INITIATED extends OperationStatus
object OperationStatus_CHALLENGE_PENDING extends OperationStatus
object OperationStatus_FAILED extends OperationStatus
object OperationStatus_COMPLETED extends OperationStatus