package code.remotedata

import akka.pattern.ask
import code.accountholder.{AccountHolders, MapperAccountHolders, RemotedataAccountHoldersCaseClasses}
import code.actorsystem.ObpActorInit
import code.model._
import net.liftweb.common.Box


object RemotedataAccountHolders extends ObpActorInit with AccountHolders {

  val cc = RemotedataAccountHoldersCaseClasses

  override def getOrCreateAccountHolder(user: User, bankAccountUID :BankIdAccountId): Box[MapperAccountHolders] = getValueFromFuture(
    (actor ? cc.getOrCreateAccountHolder(user: User, bankAccountUID :BankIdAccountId)).mapTo[Box[MapperAccountHolders]]
  )

  override def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] = getValueFromFuture(
    (actor ? cc.getAccountHolders(bankId, accountId)).mapTo[Set[User]]
  )
  
  override def getAccountsHeld(bankId: BankId, user: User): Set[BankIdAccountId] = getValueFromFuture(
    (actor ? cc.getAccountsHeld(bankId: BankId, user: User)).mapTo[Set[BankIdAccountId]]
  )

  def bulkDeleteAllAccountHolders(): Box[Boolean] = getValueFromFuture(
    (actor ? cc.bulkDeleteAllAccountHolders()).mapTo[Box[Boolean]]
  )

}
