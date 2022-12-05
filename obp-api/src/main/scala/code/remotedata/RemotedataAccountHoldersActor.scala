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

    case cc.getOrCreateAccountHolder(user: User, account :BankIdAccountId, source: Option[String]) =>
      logger.debug(s"getOrCreateAccountHolder($user, $account, $source)")
      sender ! (mapper.getOrCreateAccountHolder(user: User, account :BankIdAccountId, source))
      
    case cc.getAccountHolders(bankId: BankId, accountId: AccountId) =>
      logger.debug(s"getAccountHolders($bankId, $accountId)")
      sender ! (mapper.getAccountHolders(bankId, accountId))

    case cc.getAccountsHeld(bankId: BankId, user: User) =>
      logger.debug(s"getAccountsHeld($bankId, $user)")
      sender ! (mapper.getAccountsHeld(bankId: BankId, user: User))

    case cc.getAccountsHeldByUser(user: User, source: Option[String]) =>
      logger.debug(s"getAccountsHeldByUser($user, $source)")
      sender ! (mapper.getAccountsHeldByUser(user: User, source: Option[String]))
      
    case cc.bulkDeleteAllAccountHolders() =>
      logger.debug(s"bulkDeleteAllAccountHolders()")
      sender ! (mapper.bulkDeleteAllAccountHolders())  
      
    case cc.deleteAccountHolder(user: User, bankAccountUID :BankIdAccountId) =>
      logger.debug(s"deleteAccountHolder($user,$bankAccountUID)")
      sender ! (mapper.deleteAccountHolder(user, bankAccountUID))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }
}

