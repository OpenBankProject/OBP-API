package code.remotedata

import java.util.Date

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.metadata.comments.{Comments, RemotedataCommentsCaseClasses}
import code.model._
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataComments extends RemotedataActorInit with Comments {

   val cc = RemotedataCommentsCaseClasses

  def getComments(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[Comment] =
    extractFuture(actor ? cc.getComments(bankId, accountId, transactionId, viewId))

  def addComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserId, viewId : ViewId, text : String, datePosted : Date) : Box[Comment] =
    extractFutureToBox(actor ? cc.addComment(bankId, accountId, transactionId, userId, viewId, text, datePosted))

  def deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(commentId : String) : Box[Boolean] =
    extractFutureToBox(actor ? cc.deleteComment(bankId, accountId, transactionId, commentId))

  def bulkDeleteComments(bankId: BankId, accountId: AccountId): Boolean =
    extractFuture(actor ? cc.bulkDeleteComments(bankId, accountId))


}
