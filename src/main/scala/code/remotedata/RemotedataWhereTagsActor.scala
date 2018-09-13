package code.remotedata

import java.util.Date

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.wheretags.{MapperWhereTags, RemotedataWhereTagsCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable


class RemotedataWhereTagsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MapperWhereTags  
  val cc = RemotedataWhereTagsCaseClasses

  def receive = {

    case cc.getWhereTagForTransaction(bankId, accountId, transactionId, viewId) =>
      logger.debug("getWhereTagForTransaction(" + bankId +", "+ accountId +", "+ transactionId +", "+ viewId +")")
      sender ! extractResult(mapper.getWhereTagForTransaction(bankId, accountId, transactionId)(viewId))

    case cc.bulkDeleteWhereTags(bankId: BankId, accountId: AccountId) =>
      logger.debug("bulkDeleteWhereTags(" + bankId +", "+ accountId + ")")
      sender ! extractResult(mapper.bulkDeleteWhereTags(bankId, accountId))

    case cc.deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId) =>
      logger.debug("deleteWhereTag(" + bankId +", "+ accountId + ", "+ transactionId + ", "+ viewId + ")")
      sender ! extractResult(mapper.deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId))

    case cc.addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserPrimaryKey, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) =>
      logger.debug("addWhereTag(" + bankId +", "+ accountId + ", "+ transactionId + ", "+ userId + ", " + viewId + ", "+ datePosted +  ", "+ longitude +  ", "+ latitude + ")")
      sender ! extractResult(mapper.addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


