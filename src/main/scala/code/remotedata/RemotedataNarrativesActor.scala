package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.narrative.{MappedNarratives, RemoteNarrativesCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId, TransactionId}

class RemotedataNarrativesActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedNarratives
  val cc = RemoteNarrativesCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId) =>
      logger.debug("getNarrative(" + bankId + ", " + accountId + ", " + transactionId + ")")
      sender ! (mapper.getNarrative(bankId, accountId, transactionId)())

    case cc.setNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId, narrative: String) =>
      logger.debug("setNarrative(" + bankId + ", " + accountId + ", " + transactionId + ", " + narrative + ")")
      sender ! (mapper.setNarrative(bankId, accountId, transactionId)(narrative))

    case cc.bulkDeleteNarratives(bankId: BankId, accountId: AccountId) =>
      logger.debug("bulkDeleteComments(" + bankId +", "+ accountId + ")")
      sender ! (mapper.bulkDeleteNarratives(bankId, accountId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


