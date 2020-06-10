package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.comments.{MappedComments, RemotedataCommentsCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId, TransactionId}


class RemotedataCommentsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedComments
  val cc = RemotedataCommentsCaseClasses

  def receive = {

    case cc.getComments(bankId, accountId, transactionId, viewId) =>
      logger.debug(s"getComments($bankId, $accountId, $transactionId, $viewId)")
      sender ! (mapper.getComments(bankId, accountId, transactionId)(viewId))

    case cc.addComment(bankId, accountId, transactionId, userId, viewId, text, datePosted) =>
      logger.debug(s"addComment($bankId, $accountId, $transactionId, $text, $text, $datePosted)")
      sender ! (mapper.addComment(bankId, accountId, transactionId)(userId, viewId, text, datePosted))

    case cc.deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId, commentId : String) =>
      logger.debug(s"deleteComment($bankId, $accountId, $transactionId, $commentId)")
      sender ! (mapper.deleteComment(bankId, accountId, transactionId)(commentId))

    case cc.bulkDeleteComments(bankId: BankId, accountId: AccountId) =>
      logger.debug(s"bulkDeleteComments($bankId, $accountId)")
      sender ! (mapper.bulkDeleteComments(bankId, accountId))
      
    case cc.bulkDeleteCommentsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId) =>
      logger.debug(s"bulkDeleteComments($bankId, $accountId, $transactionId)")
      sender ! (mapper.bulkDeleteCommentsOnTransaction(bankId, accountId, transactionId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

