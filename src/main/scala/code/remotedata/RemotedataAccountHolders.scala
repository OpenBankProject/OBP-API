package code.remotedata

import code.accountholder.{AccountHolders, MapperAccountHolders, RemotedataAccountHoldersCaseClasses}
import code.model._
import net.liftweb.common.Box
import akka.pattern.ask
import code.actorsystem.ObpActorInit


object RemotedataAccountHolders extends ObpActorInit with AccountHolders {

  val cc = RemotedataAccountHoldersCaseClasses

  override def getOrCreateAccountHolder(user: User, bankAccountUID :BankIdAccountId): Box[MapperAccountHolders] =
    extractFutureToBox(actor ? cc.getOrCreateAccountHolder(user: User, bankAccountUID :BankIdAccountId))

  override def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] =
    extractFuture(actor ? cc.getAccountHolders(bankId, accountId))
  
  override def getAccountsHeld(bankId: BankId, user: User): Set[BankIdAccountId] =
    extractFuture(actor ? cc.getAccountsHeld(bankId: BankId, user: User))

  def bulkDeleteAllAccountHolders(): Box[Boolean] =
    extractFutureToBox(actor ? cc.bulkDeleteAllAccountHolders())

}
