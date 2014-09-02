package code.operations

import java.util.{UUID, Date}
import code.bankconnectors.{LocalConnector, Connector}
import code.model.{BankAccount, Transaction, User}
import code.model.operations._
import code.payments.SandboxPaymentProcessor
import code.util.Helper
import net.liftweb.util.Helpers._
import code.views.Views
import net.liftweb.common.{Loggable, Full, Failure, Box, Empty}
import net.liftweb.mapper._

object MappedOperations extends Operations with Loggable {

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
          Connector.connector.vend.getTransaction(op.bankPermalink.get, op.accountPermalink.get, op.transactionPermalink.get) match {
            case Full(transaction) => Full(new InitiatedPayment(operationId, transaction, op.startDate.get))
            case _ => Failure(s"server error: transaction not found for operation $operationId")
          }
        }
        case OperationStatus_CHALLENGE_PENDING => Full(new ChallengePendingPayment(operationId, op.startDate.get, activeChallenges(op.challenges.toList)))
        case OperationStatus_FAILED => Full(new FailedPayment(operationId, op.failMsg, op.startDate.get, op.endDate.get))
        case OperationStatus_COMPLETED => {
          Connector.connector.vend.getTransaction(op.bankPermalink.get, op.accountPermalink.get, op.transactionPermalink.get) match {
            case Full(transaction) => Full(new CompletedPayment(operationId, transaction, op.startDate.get, op.endDate.get))
            case _ => Failure(s"server error: transaction not found for operation $operationId")
          }
        }
      }
    } yield operation
  }

  def saveNewCompletedPayment(transaction : Transaction) : CompletedPayment = {
    val mappedOp = MappedPaymentOperation.create
      .bankPermalink(transaction.bankPermalink)
      .accountPermalink(transaction.accountPermalink)
      .transactionPermalink(transaction.id)
      .permalink(UUID.randomUUID.toString)
      .setStatus(OperationStatus_COMPLETED)
      .startDate(transaction.startDate)
      .endDate(transaction.finishDate)
      .saveMe()

    completedPayment(mappedOp, transaction)
  }

  def saveNewFailedPayment(bankPermalink : String, accountPermalink : String, failureMessage : String) = {
    val currentTime = new Date()
    val mappedOp = MappedPaymentOperation.create
      .bankPermalink(bankPermalink)
      .accountPermalink(accountPermalink)
      .permalink(UUID.randomUUID.toString)
      .setStatus(OperationStatus_FAILED)
      .startDate(currentTime)
      .endDate(currentTime)
      .failMsg(failureMessage)
      .saveMe()

    new FailedPayment(mappedOp.permalink.get, failureMessage, currentTime, currentTime)
  }

  def saveNewChallengePendingPayment(fromAccountBankPermalink : String, fromAccountPermalink : String, toAccountBankPermalink : String,
                                     toAccountPermalink : String, amount : BigDecimal, challenges : List[Challenge]) : ChallengePendingPayment = {
    val currentTime = new Date()

    val mappedChallenges : List[MappedChallenge] = {
      challenges.flatMap(c => MappedChallenge.find(By(MappedChallenge.permalink, c.id)))
    }

    val mappedOp = MappedPaymentOperation.create
      .bankPermalink(fromAccountBankPermalink) //TODO: duplicate of fromAccountBankPermalink?
      .accountPermalink(fromAccountPermalink) //TODO: duplicate of fromAccountPermalink?
      .fromAccountBankId(fromAccountBankPermalink) //TODO: duplicate of bankPermalink?
      .fromAccountId(fromAccountPermalink) //TODO: duplicate of accountPermalink?
      .toAccountBankId(toAccountBankPermalink)
      .toAccountId(toAccountPermalink)
      .transactionAmount(amount.toString)
      .permalink(UUID.randomUUID.toString)
      .setStatus(OperationStatus_CHALLENGE_PENDING)
      .startDate(currentTime)

    mappedOp.challenges.insertAll(0, mappedChallenges)
    mappedOp.save

    new ChallengePendingPayment(mappedOp.permalink.get, mappedOp.startDate.get, challenges)
  }

  def completedPayment(payOp : MappedPaymentOperation, transaction : Transaction) : CompletedPayment = {
    new CompletedPayment(payOp.permalink.get, transaction, payOp.startDate.get, payOp.endDate.get)
  }

  def completedPayment(payOp : MappedPaymentOperation) : Box[CompletedPayment] = {
    for {
      transaction <- Connector.connector.vend.getTransaction(payOp.bankPermalink.get, payOp.accountPermalink.get, payOp.transactionPermalink.get)
    } yield completedPayment(payOp, transaction)
  }

  def answerChallenge(challengeId : String, answer : String) : Box[ChallengeResponse] = {
    MappedChallenge.find(By(MappedChallenge.permalink, challengeId)) match {
      case Full(challenge) => {
        //dummy implementation (challenges have no expiration)
        if(!challenge.active) {
          Failure(s"challenge $challengeId is no longer active and cannot be answered.")
        } else if(answer.toLowerCase == "berlin") {
          challenge.operation.obj match {
            case Full(op) => {

              //challenge has been answered and is no longer active
              challenge.active(false).save

              val nextChallenge = op.challenges.find(c => c.active)
              (nextChallenge match {
                case Some(ch) => Full(AnotherChallengeRequired(ch.permalink.get))
                case None => {
                  op.setStatus(OperationStatus_COMPLETED).endDate(new Date()).save
                  for {
                    fromBankAccount <- BankAccount(op.fromAccountBankId.get, op.fromAccountId.get)
                    toBankAccount <- BankAccount(op.toAccountBankId.get, op.toAccountId.get)
                    amt <- tryo {BigDecimal(op.transactionAmount.get)}
                    //saves transactions on both ends, returns the one for the "from" bank account
                    transaction <- SandboxPaymentProcessor.saveMongoDBPaymentTransaction(fromBankAccount, toBankAccount, amt)
                  } yield {
                    op.transactionPermalink(transaction.id).save
                    PaymentOperationResolved(completedPayment(op, transaction))
                  }
                }
              }) ?~! "server error"
            }
            case _ => {
              logger.warn(s"operation not found for challenge $challengeId")
              Failure("server error")
            }
          }
        } else {
          //dummy implementation: if there has already been one failed attempt, fail the transaction
          if(challenge.failedAnswerCount.get >= 1) {
            challenge.active(false).save
            challenge.operation.obj match {
              case Full(op) => {
                op.failMsg("Too many incorrect answers to challenge").endDate(new Date()).setStatus(OperationStatus_FAILED).save
                Full(ChallengeFailedOperationFailed(op.permalink.get))
              }
              case _ => {
                logger.warn(s"operation not found for challenge $challengeId")
                Failure("server error")
              }
            }

          } else {
            Full(TryChallengeAgain)
          }
        }
      }
      case _ => Failure(s"challenge $challengeId not found")
    }

  }

  def createDummyChallenge() : Challenge = {
    //TODO: question vs label?
    val mappedChallenge = MappedChallenge.create.active(true).startDate(new Date()).
      label("What is the capital of Germany?").
      question("").saveMe

    new Challenge {
      val id = mappedChallenge.permalink.get
      val question = mappedChallenge.question.get
      val label = mappedChallenge.label.get
    }
  }

  private def activeChallenges(mappedChallenges : List[MappedChallenge]) : List[Challenge] = {
    mappedChallenges.filter(c => c.active).map(c => {
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

  //the permalink of the transaction associated with this payment
  object transactionPermalink extends MappedString(this, 100)

  //the id/permalink of this operation
  object permalink extends MappedString(this, 255)

  object startDate extends MappedDate(this)
  object endDate extends MappedDate(this)

  //the failure message, which should be set if the payment operation failed
  object failMsg extends MappedString(this, 255)

  object challenges extends MappedOneToMany(MappedChallenge, MappedChallenge.operation)

  //these get retrieved and set via get/set methods with non-string results/arguments
  private object status extends MappedString(this, 50)

  //these fields are for when we're in the ChallengePending state
  object fromAccountBankId extends MappedString(this, 100) //TODO: same as bankPermalink?
  object fromAccountId extends MappedString(this, 100) //TODO: same as accountPermalink?
  object toAccountBankId extends MappedString(this, 100)
  object toAccountId extends MappedString(this, 100)
  object transactionAmount extends MappedString(this, 100)//storing the amount as a a string for now


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

  object startDate extends MappedDate(this)

  object permalink extends MappedString(this, 100) {
    override def defaultValue = UUID.randomUUID().toString
  }
  object question extends MappedString(this, 100)
  object label extends MappedString(this, 100)

  object active extends MappedBoolean(this)
  object failedAnswerCount extends MappedInt(this)

}

object MappedChallenge extends MappedChallenge with LongKeyedMetaMapper[MappedChallenge] {
  override def dbIndexes = UniqueIndex(permalink) :: super.dbIndexes
}
