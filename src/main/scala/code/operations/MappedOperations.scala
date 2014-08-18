package code.operations

import code.bankconnectors.Connector
import code.model.User
import code.model.operations._
import code.util.Helper
import code.views.Views
import net.liftweb.common.{Full, Failure, Box}
import net.liftweb.mapper._

object MappedOperations extends Operations {

  def getOperation(operationId : String, user : Box[User]) : Box[Operation] = {

    //currently the only kind of operations are payment operations, and we allow
    //users with owner access to the account making the payment to see operations

    //when more operations are supported this will have to be reworked
    for {
      op <- MappedPaymentOperation.find(By(MappedPaymentOperation.permalink, operationId)) ?~! s"Operation $operationId not found"
      ownerView <- Views.views.vend.view("owner", op.accountPermalink.get, op.bankPermalink.get)
      u <- user ?~! "Currently supported operations require a logged in user"
      userHasOwnerAccess <- Helper.booleanToBox(ownerView.users.contains(u), "Insufficient privileges")
      status <- Box(op.getStatus) ?~! "server error: unknown operation state"
      operation <- status match {
        case OperationStatus_INITIATED => {
          Connector.connector.vend.getModeratedTransaction(op.transactionId.get, op.bankPermalink.get, op.accountPermalink.get)(ownerView.moderate _) match {
            case Full(transaction) => Full(new InitiatedPayment(operationId, transaction, op.startDate.get))
            case _ => Failure(s"server error: transaction not found for operation $operationId")
          }
        }
        case OperationStatus_CHALLENGE_PENDING => Full(new ChallengePendingPayment(operationId, op.startDate.get, toChallenges(op.challenges.toList)))
        case OperationStatus_FAILED => Full(new FailedPayment(operationId, op.failMsg, op.startDate.get, op.endDate.get))
        case OperationStatus_COMPLETED => {
          Connector.connector.vend.getModeratedTransaction(op.transactionId.get, op.bankPermalink.get, op.accountPermalink.get)(ownerView.moderate _) match {
            case Full(transaction) => Full(new CompletedPayment(operationId, transaction, op.startDate.get, op.endDate.get))
            case _ => Failure(s"server error: transaction not found for operation $operationId")
          }
        }
      }
    } yield operation
  }

  private def toChallenges(mappedChallenges : List[MappedChallenge]) : List[Challenge] = {
    mappedChallenges.map(c => {
      new Challenge {
        override val question: String = c.question.get
        override val label: String = c.label.get
        override val id: String = c.permalink.get
      }
    })
  }

}


class MappedPaymentOperation extends LongKeyedMapper[MappedPaymentOperation] with IdPK with OneToMany[Long, MappedPaymentOperation] {

  def getSingleton = MappedPaymentOperation

  //links this payment to the account from which it originated
  object bankPermalink extends MappedString(this, 100)
  object accountPermalink extends MappedString(this, 100)

  //the id of the transaction associated with this payment
  object transactionId extends MappedString(this, 100)

  //the id/permalink of this operation
  object permalink extends MappedString(this, 255)

  object startDate extends MappedDate(this)
  object endDate extends MappedDate(this)

  //the failure message, which should be set if the payment operation failed
  object failMsg extends MappedString(this, 255) {
    override def defaultValue = "payment failed"
  }

  object challenges extends MappedOneToMany(MappedChallenge, MappedChallenge.operation)

  //these get retrieved and set via get/set methods with non-string results/arguments
  private object status extends MappedString(this, 50)

  private val OpStatInitiated = "I"
  private val OpStatChallengePending = "P"
  private val OpStatFailed = "F"
  private val OpStatCompleted = "C"


  def getStatus : Option[OperationStatus] = {
    status.get match {
      case OpStatInitiated => Some(OperationStatus_INITIATED)
      case OpStatChallengePending => Some(OperationStatus_CHALLENGE_PENDING)
      case OpStatFailed => Some(OperationStatus_FAILED)
      case OpStatCompleted => Some(OperationStatus_COMPLETED)
      case _ => None
    }
  }

  //note: doesn't save
  def setStatus(operationStatus : OperationStatus) = {
    val statusString  = operationStatus match {
      case OperationStatus_INITIATED => OpStatInitiated
      case OperationStatus_CHALLENGE_PENDING => OpStatChallengePending
      case OperationStatus_FAILED => OpStatFailed
      case OperationStatus_COMPLETED => OpStatCompleted
    }
    status(statusString)
  }

}

object MappedPaymentOperation extends MappedPaymentOperation with LongKeyedMetaMapper[MappedPaymentOperation] {
  override def dbIndexes = UniqueIndex(permalink) :: super.dbIndexes
}

class MappedChallenge extends LongKeyedMapper[MappedChallenge] with IdPK {

  def getSingleton = MappedChallenge

  object operation extends MappedLongForeignKey(this, MappedPaymentOperation)

  object permalink extends MappedString(this, 100)
  object question extends MappedString(this, 100)
  object label extends MappedString(this, 100)

}

object MappedChallenge extends MappedChallenge with LongKeyedMetaMapper[MappedChallenge] {
  override def dbIndexes = UniqueIndex(permalink) :: super.dbIndexes
}
