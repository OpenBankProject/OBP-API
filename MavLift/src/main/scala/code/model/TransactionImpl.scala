package code.model

import scala.math.BigDecimal
import java.util.Date
import scala.collection.immutable.List

class TransactionImpl(env : OBPEnvelope) extends Transaction {

  def id(): String = { null }

  def account(): BankAccount = { null }

  def otherParty(): NonObpAccount = { null }

  def transactionType(): String = { null }

  def amount(): BigDecimal = { null }

  def currency(): String = { null }

  def label(): String = { null }

  def comments(): List[Comment] = { null }

  def startDate(): Date = { null }

  def finishDate(): Date = { null }

  def addComment(comment: Comment) = {}

}