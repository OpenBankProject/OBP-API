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
      logger.debug(s"getNarrative($bankId, $accountId, $transactionId)")
      sender ! (mapper.getNarrative(bankId, accountId, transactionId)())

    case cc.setNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId, narrative: String) =>
      logger.debug(s"setNarrative($bankId, $accountId, $transactionId, $narrative)")
      sender ! (mapper.setNarrative(bankId, accountId, transactionId)(narrative))

    case cc.bulkDeleteNarrativeOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId) =>
      logger.debug(s"bulkDeleteNarrativeOnTransaction($bankId, $accountId, $transactionId)")
      sender ! (mapper.bulkDeleteNarrativeOnTransaction(bankId, accountId, transactionId))
      
    case cc.bulkDeleteNarratives(bankId: BankId, accountId: AccountId) =>
      logger.debug(s"bulkDeleteComments($bankId, $accountId)")
      sender ! (mapper.bulkDeleteNarratives(bankId, accountId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


