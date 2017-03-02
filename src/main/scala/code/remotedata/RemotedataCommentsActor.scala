package code.remotedata

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.event.Logging
import akka.util.Timeout
import bootstrap.liftweb.ToSchemify
import code.metadata.comments.{MappedComment, MappedComments, RemoteCommentsCaseClasses}
import code.model._
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo

import scala.concurrent.duration._


class RemotedataCommentsActor extends Actor {

  val logger = Logging(context.system, this)

  val mComments = MappedComments
  val rComments = RemoteCommentsCaseClasses

  def receive = {

    case rComments.getComments(bankId, accountId, transactionId, viewId) =>
      logger.info("getComments(" + bankId +", "+ accountId +", "+ transactionId +", "+ viewId +")")
      sender ! mComments.getComments(bankId, accountId, transactionId)(viewId)

    case rComments.addComment(bankId, accountId, transactionId, userId, viewId, text, datePosted) =>
      logger.info("addComment(" + bankId +", "+ accountId +", "+ transactionId +", "+ text +", "+ text +", "+ datePosted +")")

      {
        for {
          res <- mComments.addComment(bankId, accountId, transactionId)(userId, viewId, text, datePosted)
        } yield {
          sender ! res.asInstanceOf[Comment]
        }
      }.getOrElse( context.stop(sender) )

    case rComments.deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId, commentId : String) =>
      logger.info("deleteComment(" + bankId +", "+ accountId +", "+ transactionId + commentId +")")

      {
        for {
          res <- mComments.deleteComment(bankId, accountId, transactionId)(commentId)
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    case rComments.bulkDeleteComments(bankId: BankId, accountId: AccountId) =>

      logger.info("bulkDeleteComments(" + bankId +", "+ accountId + ")")

      {
        for {
          res <- tryo{mComments.bulkDeleteComments(bankId, accountId)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

