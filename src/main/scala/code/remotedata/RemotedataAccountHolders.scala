package code.remotedata

import code.accountholder.{AccountHolders, RemoteAccountHoldersCaseClasses}
import code.model.{AccountId, BankId, User}
import net.liftweb.common.{Full, _}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataAccountHolders extends AccountHolders {

  implicit val timeout = Timeout(10000 milliseconds)
  val TIMEOUT = 10 seconds
  val rAccountHolders = RemoteAccountHoldersCaseClasses
  var accountHoldersActor = RemotedataActorSystem.getActor("accountHolders")

  override def createAccountHolder(userId: Long, bankId: String, accountId: String, source: String = "MappedAccountHolder"): Boolean = {
    Await.result(
      (accountHoldersActor ? rAccountHolders.createAccountHolder(userId, bankId, accountId, source)).mapTo[Boolean],
      TIMEOUT
    )
  }

  override def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] = {
    Await.result(
      (accountHoldersActor ? rAccountHolders.getAccountHolders(bankId, accountId)).mapTo[Set[User]],
      TIMEOUT
    )
  }


  def bulkDeleteAllAccountHolders(): Box[Boolean] = {
    Full(
      Await.result(
        (accountHoldersActor ? rAccountHolders.bulkDeleteAllAccountHolders()).mapTo[Boolean],
        TIMEOUT
      )
    )
  }

}
