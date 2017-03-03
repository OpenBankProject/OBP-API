package code.remotedata

import code.accountholder.{AccountHolders, RemotedataAccountHoldersCaseClasses}
import code.model.{AccountId, BankId, User}
import net.liftweb.common.{Full, _}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataAccountHolders extends ActorInit with AccountHolders {

  val cc = RemotedataAccountHoldersCaseClasses

  override def createAccountHolder(userId: Long, bankId: String, accountId: String, source: String = "MappedAccountHolder"): Boolean = {
    Await.result(
      (ac ? cc.createAccountHolder(userId, bankId, accountId, source)).mapTo[Boolean],
      TIMEOUT
    )
  }

  override def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] = {
    Await.result(
      (ac ? cc.getAccountHolders(bankId, accountId)).mapTo[Set[User]],
      TIMEOUT
    )
  }


  def bulkDeleteAllAccountHolders(): Box[Boolean] = {
    Full(
      Await.result(
        (ac ? cc.bulkDeleteAllAccountHolders()).mapTo[Boolean],
        TIMEOUT
      )
    )
  }

}
