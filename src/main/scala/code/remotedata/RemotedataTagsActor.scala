package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.tags.{MappedTags, RemotedataTagsCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable

class RemotedataTagsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedTags
  val cc = RemotedataTagsCaseClasses

  def receive = {

    case cc.getTags(bankId, accountId, transactionId, viewId) =>
      logger.debug("getTags(" + bankId +", "+ accountId +", "+ transactionId +", "+ viewId +")")
      sender ! extractResult(mapper.getTags(bankId, accountId, transactionId)(viewId))

    case cc.addTag(bankId, accountId, transactionId, userId, viewId, text, datePosted) =>
      logger.debug("addTag(" + bankId +", "+ accountId +", "+ transactionId +", "+ text +", "+ text +", "+ datePosted +")")
      sender ! extractResult(mapper.addTag(bankId, accountId, transactionId)(userId, viewId, text, datePosted))

    case cc.deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, tagId : String) =>
      logger.debug("deleteTag(" + bankId +", "+ accountId +", "+ transactionId + tagId +")")
      sender ! extractResult(mapper.deleteTag(bankId, accountId, transactionId)(tagId))

    case cc.bulkDeleteTags(bankId: BankId, accountId: AccountId) =>
      logger.debug("bulkDeleteTags(" + bankId +", "+ accountId + ")")
      sender ! extractResult(mapper.bulkDeleteTags(bankId, accountId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


