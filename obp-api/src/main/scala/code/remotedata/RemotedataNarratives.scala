package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.metadata.narrative.{Narrative, RemoteNarrativesCaseClasses}
import code.model._
import com.openbankproject.commons.model.{AccountId, BankId, TransactionId}

object RemotedataNarratives extends ObpActorInit with Narrative {

  val cc = RemoteNarrativesCaseClasses

  def getNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)() : String = getValueFromFuture(
    (actor ? cc.getNarrative(bankId, accountId, transactionId)).mapTo[String]
  )

  def setNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(narrative: String): Boolean = getValueFromFuture(
    (actor ? cc.setNarrative(bankId, accountId, transactionId, narrative)).mapTo[Boolean]
  )

  def bulkDeleteNarrativeOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteNarrativeOnTransaction(bankId, accountId, transactionId)).mapTo[Boolean]
  )
  
  def bulkDeleteNarratives(bankId: BankId, accountId: AccountId): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteNarratives(bankId, accountId)).mapTo[Boolean]
  )

}
