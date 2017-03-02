package code.remotedata

import java.util.Date

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.metadata.comments.{Comments, RemoteCommentsCaseClasses}
import code.model._
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataComments extends Comments {

  implicit val timeout = Timeout(10000 milliseconds)
  val TIMEOUT = 10 seconds
  val rComments = RemoteCommentsCaseClasses
  var commentsActor = RemotedataActorSystem.getActor("comments")

  def getComments(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[Comment] = {
    Await.result(
      (commentsActor ? rComments.getComments(bankId, accountId, transactionId, viewId)).mapTo[List[Comment]],
      TIMEOUT
    )
  }

  def addComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserId, viewId : ViewId, text : String, datePosted : Date) : Box[Comment] = {
    Full(
      Await.result(
        (commentsActor ? rComments.addComment(bankId, accountId, transactionId, userId, viewId, text, datePosted)).mapTo[Comment],
        TIMEOUT
      )
    )
  }

  def deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(commentId : String) : Box[Boolean] = {
    val res = try {
      Full(
        Await.result(
          (commentsActor ? rComments.deleteComment(bankId, accountId, transactionId, commentId)).mapTo[Boolean],
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
      (commentsActor ? rComments.bulkDeleteComments(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }


}
