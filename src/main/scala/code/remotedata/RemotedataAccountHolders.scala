package code.remotedata

import code.accountholder.{AccountHolders, MapperAccountHolders, RemotedataAccountHoldersCaseClasses}
import code.model._
import net.liftweb.common.Box
import akka.pattern.ask
import code.actorsystem.ObpActorInit


object RemotedataAccountHolders extends ObpActorInit with AccountHolders {

  val cc = RemotedataAccountHoldersCaseClasses

  override def createAccountHolder(userId: Long, bankId: String, accountId: String): Boolean =
    extractFuture(actor ? cc.createAccountHolder(userId, bankId, accountId))
  
  override def getOrCreateAccountHolder(user: User, bankAccountUID :BankAccountUID): Box[MapperAccountHolders] =
    extractFuture(actor ? cc.getOrCreateAccountHolder(user: User, bankAccountUID :BankAccountUID))

  override def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] =
    extractFuture(actor ? cc.getAccountHolders(bankId, accountId))

  def bulkDeleteAllAccountHolders(): Box[Boolean] =
    extractFutureToBox(actor ? cc.bulkDeleteAllAccountHolders())

}
