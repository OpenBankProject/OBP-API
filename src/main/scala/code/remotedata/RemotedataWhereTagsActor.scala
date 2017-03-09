package code.remotedata

import java.util.concurrent.TimeUnit
import java.util.Date

import akka.actor.Actor
import akka.event.Logging
import akka.util.Timeout
import code.metadata.wheretags.{MapperWhereTags, RemotedataWhereTagsCaseClasses}
import code.model._
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo

import scala.concurrent.duration._


class RemotedataWhereTagsActor extends Actor with ActorHelper {

  val logger = Logging(context.system, this)

  val mapper = MapperWhereTags  
  val cc = RemotedataWhereTagsCaseClasses

  def receive = {

    case cc.getWhereTagForTransaction(bankId, accountId, transactionId, viewId) =>
      logger.info("getWhereTagForTransaction(" + bankId +", "+ accountId +", "+ transactionId +", "+ viewId +")")
      sender ! extractResult(mapper.getWhereTagForTransaction(bankId, accountId, transactionId)(viewId))

    case cc.bulkDeleteWhereTags(bankId: BankId, accountId: AccountId) =>
      logger.info("bulkDeleteWhereTags(" + bankId +", "+ accountId + ")")
      sender ! extractResult(mapper.bulkDeleteWhereTags(bankId, accountId))

    case cc.deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId) =>
      logger.info("deleteWhereTag(" + bankId +", "+ accountId + ", "+ transactionId + ", "+ viewId + ")")
      sender ! extractResult(mapper.deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId))

    case cc.addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserId, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) =>
      logger.info("addWhereTag(" + bankId +", "+ accountId + ", "+ transactionId + ", "+ userId + ", " + viewId + ", "+ datePosted +  ", "+ longitude +  ", "+ latitude + ")")
      sender ! extractResult(mapper.addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserId, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double))

    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


