package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.comments.{MappedComments, RemotedataCommentsCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable


class RemotedataCommentsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedComments
  val cc = RemotedataCommentsCaseClasses

  def receive = {

    case cc.getComments(bankId, accountId, transactionId, viewId) =>
      logger.debug("getComments(" + bankId +", "+ accountId +", "+ transactionId +", "+ viewId +")")
      sender ! extractResult(mapper.getComments(bankId, accountId, transactionId)(viewId))

    case cc.addComment(bankId, accountId, transactionId, userId, viewId, text, datePosted) =>
      logger.debug("addComment(" + bankId +", "+ accountId +", "+ transactionId +", "+ text +", "+ text +", "+ datePosted +")")
      sender ! extractResult(mapper.addComment(bankId, accountId, transactionId)(userId, viewId, text, datePosted))

    case cc.deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId, commentId : String) =>
      logger.debug("deleteComment(" + bankId +", "+ accountId +", "+ transactionId + commentId +")")
      sender ! extractResult(mapper.deleteComment(bankId, accountId, transactionId)(commentId))

    case cc.bulkDeleteComments(bankId: BankId, accountId: AccountId) =>
      logger.debug("bulkDeleteComments(" + bankId +", "+ accountId + ")")
      sender ! extractResult(mapper.bulkDeleteComments(bankId, accountId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

