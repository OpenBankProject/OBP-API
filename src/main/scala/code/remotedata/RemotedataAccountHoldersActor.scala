package code.remotedata

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.event.Logging
import akka.util.Timeout
import code.accountholder.{MapperAccountHolders, RemotedataAccountHoldersCaseClasses}
import code.model._
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo

import scala.concurrent.duration._


class RemotedataAccountHoldersActor extends Actor {

  val logger = Logging(context.system, this)

  val mapper = MapperAccountHolders
  val cc = RemotedataAccountHoldersCaseClasses

  def receive = {

    case cc.createAccountHolder(userId: Long, bankId: String, accountId: String, source: String) =>

      logger.info("createAccountHolder(" + userId +", "+ bankId +", "+ accountId +", "+ source +")")

        {
        for {
          res <- tryo{mapper.createAccountHolder(userId, bankId, accountId, source)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case cc.getAccountHolders(bankId: BankId, accountId: AccountId) =>

      logger.info("getAccountHolders(" + bankId +", "+ accountId +")")

        {
        for {
          res <- tryo{mapper.getAccountHolders(bankId, accountId)}
        } yield {
          sender ! res.asInstanceOf[Set[User]]
        }
      }.getOrElse( context.stop(sender) )


    case cc.bulkDeleteAllAccountHolders() =>

      logger.info("bulkDeleteAllAccountHolders()")

        {
        for {
          res <- mapper.bulkDeleteAllAccountHolders()
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }
}

