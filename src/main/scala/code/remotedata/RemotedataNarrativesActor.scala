package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.narrative.{MappedNarratives, RemoteNarrativesCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable

class RemotedataNarrativesActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedNarratives
  val cc = RemoteNarrativesCaseClasses

  def receive = {

    case cc.getNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId) =>
      logger.debug("getNarrative(" + bankId + ", " + accountId + ", " + transactionId + ")")
      sender ! extractResult(mapper.getNarrative(bankId, accountId, transactionId)())

    case cc.setNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId, narrative: String) =>
      logger.debug("setNarrative(" + bankId + ", " + accountId + ", " + transactionId + ", " + narrative + ")")
      sender ! extractResult(mapper.setNarrative(bankId, accountId, transactionId)(narrative))

    case cc.bulkDeleteNarratives(bankId: BankId, accountId: AccountId) =>
      logger.debug("bulkDeleteComments(" + bankId +", "+ accountId + ")")
      sender ! extractResult(mapper.bulkDeleteNarratives(bankId, accountId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


