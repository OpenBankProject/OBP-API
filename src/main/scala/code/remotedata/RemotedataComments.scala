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


object RemotedataComments extends ActorInit with Comments {

   val cc = RemotedataCommentsCaseClasses

  def getComments(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[Comment] = {
    Await.result(
      (ac ? cc.getComments(bankId, accountId, transactionId, viewId)).mapTo[List[Comment]],
      TIMEOUT
    )
  }

  def addComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserId, viewId : ViewId, text : String, datePosted : Date) : Box[Comment] = {
    Full(
      Await.result(
        (ac ? cc.addComment(bankId, accountId, transactionId, userId, viewId, text, datePosted)).mapTo[Comment],
        TIMEOUT
      )
    )
  }

  def deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(commentId : String) : Box[Boolean] = {
    val res = try {
      Full(
        Await.result(
          (ac ? cc.deleteComment(bankId, accountId, transactionId, commentId)).mapTo[Boolean],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Cannot delete the comment", 404)
      case e: Throwable => throw e
    }
    res
  }

  def bulkDeleteComments(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (ac ? cc.bulkDeleteComments(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }


}
