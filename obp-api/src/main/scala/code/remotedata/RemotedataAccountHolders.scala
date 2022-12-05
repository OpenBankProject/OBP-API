package code.remotedata

import akka.pattern.ask
import code.accountholders.{AccountHolders, MapperAccountHolders, RemotedataAccountHoldersCaseClasses}
import code.actorsystem.ObpActorInit
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, User}
import net.liftweb.common.Box


object RemotedataAccountHolders extends ObpActorInit with AccountHolders {

  val cc = RemotedataAccountHoldersCaseClasses

  override def getOrCreateAccountHolder(user: User, bankAccountUID :BankIdAccountId, source: Option[String] = None): Box[MapperAccountHolders] = getValueFromFuture(
    (actor ? cc.getOrCreateAccountHolder(user: User, bankAccountUID :BankIdAccountId)).mapTo[Box[MapperAccountHolders]]
  )

  override def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] = getValueFromFuture(
    (actor ? cc.getAccountHolders(bankId, accountId)).mapTo[Set[User]]
  )
  
  override def getAccountsHeld(bankId: BankId, user: User): Set[BankIdAccountId] = getValueFromFuture(
    (actor ? cc.getAccountsHeld(bankId: BankId, user: User)).mapTo[Set[BankIdAccountId]]
  )

  override def getAccountsHeldByUser(user: User, source: Option[String] = None): Set[BankIdAccountId] = getValueFromFuture(
    (actor ? cc.getAccountsHeldByUser(user: User, source: Option[String])).mapTo[Set[BankIdAccountId]]
  )

  def bulkDeleteAllAccountHolders(): Box[Boolean] = getValueFromFuture(
    (actor ? cc.bulkDeleteAllAccountHolders()).mapTo[Box[Boolean]]
  )

  override def deleteAccountHolder(user: User, bankAccountUID :BankIdAccountId): Box[Boolean]  = getValueFromFuture(
    (actor ? cc.deleteAccountHolder(user: User, bankAccountUID :BankIdAccountId)).mapTo[Box[Boolean]]
  )
}
