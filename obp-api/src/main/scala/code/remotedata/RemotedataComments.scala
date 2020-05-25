package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.metadata.comments.{Comments, RemotedataCommentsCaseClasses}
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box

import scala.collection.immutable.List


object RemotedataComments extends ObpActorInit with Comments {

   val cc = RemotedataCommentsCaseClasses

  def getComments(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[Comment] = getValueFromFuture(
    (actor ? cc.getComments(bankId, accountId, transactionId, viewId)).mapTo[List[Comment]]
  )

  def addComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, text : String, datePosted : Date) : Box[Comment] = getValueFromFuture(
    (actor ? cc.addComment(bankId, accountId, transactionId, userId, viewId, text, datePosted)).mapTo[Box[Comment]]
  )

  def deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(commentId : String) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.deleteComment(bankId, accountId, transactionId, commentId)).mapTo[Box[Boolean]]
  )

  def bulkDeleteComments(bankId: BankId, accountId: AccountId): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteComments(bankId, accountId)).mapTo[Boolean]
  )
  def bulkDeleteCommentsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteCommentsOnTransaction(bankId, accountId, transactionId)).mapTo[Boolean]
  )


}
