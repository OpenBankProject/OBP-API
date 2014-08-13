package code.model.operations

import java.util.Date

sealed trait Operation {
  val id : String
  val action : OperationAction
  val status : OperationStatus
  val startDate : Date
  val endDate : Option[Date]
  val challenges : List[Challenge]
}

sealed trait PaymentOperation extends Operation {
  override val action : OperationAction = PAYMENT
}

class CompletedPayment(
  val operationId : String, val transactionId : String,
  val startDate : Date, val finishDate : Date) extends PaymentOperation {

  override val id = operationId
  override val status = COMPLETED
  override val endDate = Some(finishDate)
  override val challenges = Nil
}

class FailedPayment(
  val operationId : String, val failureMessage : String,
  val startDate : Date, val finishDate : Date) extends PaymentOperation {

  override val id = operationId
  override val status = FAILED
  override val endDate = Some(finishDate)
  override val challenges = Nil
}

sealed trait OperationAction
object PAYMENT extends OperationAction

sealed trait OperationStatus
object INITIATED extends OperationStatus
object CHALLENGE_PENDING extends OperationStatus
object FAILED extends OperationStatus
object COMPLETED extends OperationStatus