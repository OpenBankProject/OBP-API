package code.remotedata

import java.util.Date

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.wheretags.{MapperWhereTags, RemotedataWhereTagsCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._


class RemotedataWhereTagsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MapperWhereTags  
  val cc = RemotedataWhereTagsCaseClasses

  def receive = {

    case cc.getWhereTagForTransaction(bankId, accountId, transactionId, viewId) =>
      logger.debug(s"getWhereTagForTransaction($bankId, $accountId, $transactionId, $viewId)")
      sender ! (mapper.getWhereTagForTransaction(bankId, accountId, transactionId)(viewId))

    case cc.bulkDeleteWhereTags(bankId: BankId, accountId: AccountId) =>
      logger.debug(s"bulkDeleteWhereTags($bankId, $accountId)")
      sender ! (mapper.bulkDeleteWhereTags(bankId, accountId))
      
    case cc.bulkDeleteWhereTagsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId) =>
      logger.debug(s"bulkDeleteWhereTagsOnTransaction($bankId, $accountId, $transactionId)")
      sender ! (mapper.bulkDeleteWhereTagsOnTransaction(bankId, accountId, transactionId))

    case cc.deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId) =>
      logger.debug(s"deleteWhereTag($bankId, $accountId, $transactionId, $viewId)")
      sender ! (mapper.deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId))

    case cc.addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserPrimaryKey, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) =>
      logger.debug(s"addWhereTag($bankId, $accountId, $transactionId, $userId, $viewId, $datePosted, $longitude, $latitude)")
      sender ! (mapper.addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


