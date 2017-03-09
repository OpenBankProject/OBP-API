package code.remotedata

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.event.Logging

import code.metadata.comments.{MappedComments, RemotedataCommentsCaseClasses}
import code.model._

import scala.concurrent.duration._


class RemotedataCommentsActor extends Actor with ActorHelper {

  val logger = Logging(context.system, this)

  val mapper = MappedComments
  val cc = RemotedataCommentsCaseClasses

  def receive = {

    case cc.getComments(bankId, accountId, transactionId, viewId) =>
      logger.info("getComments(" + bankId +", "+ accountId +", "+ transactionId +", "+ viewId +")")
      sender ! extractResult(mapper.getComments(bankId, accountId, transactionId)(viewId))

    case cc.addComment(bankId, accountId, transactionId, userId, viewId, text, datePosted) =>
      logger.info("addComment(" + bankId +", "+ accountId +", "+ transactionId +", "+ text +", "+ text +", "+ datePosted +")")
      sender ! extractResult(mapper.addComment(bankId, accountId, transactionId)(userId, viewId, text, datePosted))

    case cc.deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId, commentId : String) =>
      logger.info("deleteComment(" + bankId +", "+ accountId +", "+ transactionId + commentId +")")
      sender ! extractResult(mapper.deleteComment(bankId, accountId, transactionId)(commentId))

    case cc.bulkDeleteComments(bankId: BankId, accountId: AccountId) =>
      logger.info("bulkDeleteComments(" + bankId +", "+ accountId + ")")
      sender ! extractResult(mapper.bulkDeleteComments(bankId, accountId))

    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

