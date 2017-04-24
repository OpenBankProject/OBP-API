package code.remotedata

import code.accountholder.{AccountHolders, RemotedataAccountHoldersCaseClasses}
import code.model.{AccountId, BankId, User}
import net.liftweb.common.{Full, _}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataAccountHolders extends RemotedataActorInit with AccountHolders {

  val cc = RemotedataAccountHoldersCaseClasses

  override def createAccountHolder(userId: Long, bankId: String, accountId: String, source: String = "MappedAccountHolder"): Boolean =
    extractFuture(actor ? cc.createAccountHolder(userId, bankId, accountId, source))

  override def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] =
    extractFuture(actor ? cc.getAccountHolders(bankId, accountId))

  def bulkDeleteAllAccountHolders(): Box[Boolean] =
    extractFutureToBox(actor ? cc.bulkDeleteAllAccountHolders())

}
