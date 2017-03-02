package code.remotedata

import java.util.concurrent.TimeUnit
import java.util.Date

import akka.actor.Actor
import akka.event.Logging
import akka.util.Timeout
import code.metadata.wheretags.{MapperWhereTags, RemoteWhereTagsCaseClasses}
import code.model._
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo

import scala.concurrent.duration._


class RemotedataWhereTagsActor extends Actor {

  val logger = Logging(context.system, this)

  val mWhereTags = MapperWhereTags
  val rWhereTags = RemoteWhereTagsCaseClasses

  def receive = {

    case rWhereTags.getWhereTagForTransaction(bankId, accountId, transactionId, viewId) =>
      logger.info("getWhereTagForTransaction(" + bankId +", "+ accountId +", "+ transactionId +", "+ viewId +")")
      sender ! mWhereTags.getWhereTagForTransaction(bankId, accountId, transactionId)(viewId)

    case rWhereTags.bulkDeleteWhereTags(bankId: BankId, accountId: AccountId) =>

      logger.info("bulkDeleteWhereTags(" + bankId +", "+ accountId + ")")

      {
        for {
          res <- tryo{mWhereTags.bulkDeleteWhereTags(bankId, accountId)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    case rWhereTags.deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId) =>

      logger.info("deleteWhereTag(" + bankId +", "+ accountId + ", "+ transactionId + ", "+ viewId + ")")

      {
        for {
          res <- tryo{mWhereTags.deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    case rWhereTags.addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserId, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) =>

      logger.info("addWhereTag(" + bankId +", "+ accountId + ", "+ transactionId + ", "+ userId + ", " + viewId + ", "+ datePosted +  ", "+ longitude +  ", "+ latitude + ")")

      {
        for {
          res <- tryo{mWhereTags.addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserId, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


