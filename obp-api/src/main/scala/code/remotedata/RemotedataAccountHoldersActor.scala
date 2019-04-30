package code.remotedata

import akka.actor.Actor
import code.accountholders.{MapperAccountHolders, RemotedataAccountHoldersCaseClasses}
import code.accountholders.{MapperAccountHolders, RemotedataAccountHoldersCaseClasses}
import code.actorsystem.ObpActorHelper
import code.model._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, User}


class RemotedataAccountHoldersActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MapperAccountHolders
  val cc = RemotedataAccountHoldersCaseClasses

  def receive = {

    case cc.getOrCreateAccountHolder(user: User, account :BankIdAccountId) =>
      logger.debug("getOrCreateAccountHolder(" + user +", "+ account +", " +")")
      sender ! (mapper.getOrCreateAccountHolder(user: User, account :BankIdAccountId))
      
    case cc.getAccountHolders(bankId: BankId, accountId: AccountId) =>
      logger.debug("getAccountHolders(" + bankId +", "+ accountId +")")
      sender ! (mapper.getAccountHolders(bankId, accountId))

    case cc.getAccountsHeld(bankId: BankId, user: User) =>
      logger.debug("getAccountsHeld(" + bankId +", "+ user+")")
      sender ! (mapper.getAccountsHeld(bankId: BankId, user: User))

    case cc.getAccountsHeldByUser(user: User) =>
      logger.debug("getAccountsHeldByUser(" + user+")")
      sender ! (mapper.getAccountsHeldByUser(user: User))
      
    case cc.bulkDeleteAllAccountHolders() =>
      logger.debug("bulkDeleteAllAccountHolders()")
      sender ! (mapper.bulkDeleteAllAccountHolders())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }
}

