package code.remotedata

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.metadata.narrative.{Narrative, RemoteNarrativesCaseClasses}
import code.model._
import net.liftweb.common.{Empty, Full, Failure}

import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataNarratives extends Narrative {

  implicit val timeout = Timeout(10000 milliseconds)
  val TIMEOUT = 10 seconds
  val rNarratives = RemoteNarrativesCaseClasses
  var actor = RemotedataActorSystem.getActor("narratives")


  def getNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)() : String = {
    Await.result(
      (actor ? rNarratives.getNarrative(bankId, accountId, transactionId)).mapTo[String],
      TIMEOUT
    )
  }

  def setNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(narrative: String): Boolean = {
    Await.result(
      (actor ? rNarratives.setNarrative(bankId, accountId, transactionId, narrative)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def bulkDeleteNarratives(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (actor ? rNarratives.bulkDeleteNarratives(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }

}
