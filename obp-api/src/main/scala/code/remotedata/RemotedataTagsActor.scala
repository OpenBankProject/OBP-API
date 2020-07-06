package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.tags.{MappedTags, RemotedataTagsCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId, TransactionId}

class RemotedataTagsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedTags
  val cc = RemotedataTagsCaseClasses

  def receive = {

    case cc.getTags(bankId, accountId, transactionId, viewId) =>
      logger.debug(s"getTags($bankId, $accountId, $transactionId, $viewId)")
      sender ! (mapper.getTags(bankId, accountId, transactionId)(viewId))

    case cc.getTagsOnAccount(bankId, accountId, viewId) =>
      logger.debug(s"getTags($bankId, $accountId, $viewId)")
      sender ! (mapper.getTagsOnAccount(bankId, accountId)(viewId))
      
    case cc.addTag(bankId, accountId, transactionId, userId, viewId, text, datePosted) =>
      logger.debug(s"addTag($bankId, $accountId, $transactionId, $text, $text, $datePosted)")
      sender ! (mapper.addTag(bankId, accountId, transactionId)(userId, viewId, text, datePosted))
      
    case cc.addTagOnAccount(bankId, accountId, userId, viewId, text, datePosted) =>
      logger.debug(s"addTag($bankId, $accountId, $text, $text, $datePosted)")
      sender ! (mapper.addTagOnAccount(bankId, accountId)(userId, viewId, text, datePosted))

    case cc.deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, tagId : String) =>
      logger.debug(s"deleteTag($bankId, $accountId, $transactionId, $tagId)")
      sender ! (mapper.deleteTag(bankId, accountId, transactionId)(tagId))

    case cc.deleteTagOnAccount(bankId : BankId, accountId : AccountId, tagId : String) =>
      logger.debug(s"deleteTag($bankId, $accountId, $tagId)")
      sender ! (mapper.deleteTagOnAccount(bankId, accountId)(tagId))

    case cc.bulkDeleteTags(bankId: BankId, accountId: AccountId) =>
      logger.debug(s"bulkDeleteTags($bankId, $accountId)")
      sender ! (mapper.bulkDeleteTags(bankId, accountId))
      
    case cc.bulkDeleteTagsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId) =>
      logger.debug(s"bulkDeleteTagsOnTransaction($bankId, $accountId, $transactionId)")
      sender ! (mapper.bulkDeleteTagsOnTransaction(bankId, accountId, transactionId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


