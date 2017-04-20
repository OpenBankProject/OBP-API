package code.remotedata

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.event.Logging
import akka.util.Timeout
import code.accountholder.{MapperAccountHolders, RemotedataAccountHoldersCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo

import scala.concurrent.duration._


class RemotedataAccountHoldersActor extends Actor with ActorHelper with MdcLoggable {


  val mapper = MapperAccountHolders
  val cc = RemotedataAccountHoldersCaseClasses

  def receive = {

    case cc.createAccountHolder(userId: Long, bankId: String, accountId: String, source: String) =>
      logger.debug("createAccountHolder(" + userId +", "+ bankId +", "+ accountId +", "+ source +")")
      sender ! extractResult(mapper.createAccountHolder(userId, bankId, accountId, source))

    case cc.getAccountHolders(bankId: BankId, accountId: AccountId) =>
      logger.debug("getAccountHolders(" + bankId +", "+ accountId +")")
      sender ! extractResult(mapper.getAccountHolders(bankId, accountId))

    case cc.bulkDeleteAllAccountHolders() =>
      logger.debug("bulkDeleteAllAccountHolders()")
      sender ! extractResult(mapper.bulkDeleteAllAccountHolders())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }
}

