package code.remotedata

import akka.pattern.ask
import code.metadata.narrative.{Narrative, RemoteNarrativesCaseClasses}
import code.model._
import scala.concurrent.Await



object RemotedataNarratives extends ActorInit with  Narrative {

  val cc = RemoteNarrativesCaseClasses

  def getNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)() : String = {
    Await.result(
      (actor ? cc.getNarrative(bankId, accountId, transactionId)).mapTo[String],
      TIMEOUT
    )
  }

  def setNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(narrative: String): Boolean = {
    Await.result(
      (actor ? cc.setNarrative(bankId, accountId, transactionId, narrative)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def bulkDeleteNarratives(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (actor ? cc.bulkDeleteNarratives(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }

}
