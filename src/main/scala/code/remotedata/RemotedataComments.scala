package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.metadata.comments.{Comments, RemotedataCommentsCaseClasses}
import code.model._
import net.liftweb.common.Box

import scala.collection.immutable.List


object RemotedataComments extends ObpActorInit with Comments {

   val cc = RemotedataCommentsCaseClasses

  def getComments(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[Comment] =
    extractFuture(actor ? cc.getComments(bankId, accountId, transactionId, viewId))

  def addComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, text : String, datePosted : Date) : Box[Comment] =
    extractFutureToBox(actor ? cc.addComment(bankId, accountId, transactionId, userId, viewId, text, datePosted))

  def deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(commentId : String) : Box[Boolean] =
    extractFutureToBox(actor ? cc.deleteComment(bankId, accountId, transactionId, commentId))

  def bulkDeleteComments(bankId: BankId, accountId: AccountId): Boolean =
    extractFuture(actor ? cc.bulkDeleteComments(bankId, accountId))


}
