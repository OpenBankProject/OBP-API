package code.remotedata

import java.util.Date

import akka.actor.Actor
import akka.event.Logging
import code.metadata.narrative.{MappedNarratives, RemoteNarrativesCaseClasses}
import code.model._
import net.liftweb.util.ControlHelpers.tryo


class RemotedataNarrativesActor extends Actor {

  val logger = Logging(context.system, this)

  val mNarratives = MappedNarratives
  val rNarratives = RemoteNarrativesCaseClasses

  def receive = {


    case rNarratives.getNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId) =>

      logger.info("getNarrative(" + bankId + ", " + accountId + ", " + transactionId + ")")

      {
        for {
          res <- tryo{mNarratives.getNarrative(bankId, accountId, transactionId)()}
        } yield {
          sender ! res.asInstanceOf[String]
        }
      }.getOrElse( context.stop(sender) )

    case rNarratives.setNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId, narrative: String) =>

      logger.info("setNarrative(" + bankId + ", " + accountId + ", " + transactionId + ", " + narrative + ")")

      {
        for {
          res <- tryo{mNarratives.setNarrative(bankId, accountId, transactionId)(narrative)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )

    case rNarratives.bulkDeleteNarratives(bankId: BankId, accountId: AccountId) =>

      logger.info("bulkDeleteComments(" + bankId +", "+ accountId + ")")

      {
        for {
          res <- tryo{mNarratives.bulkDeleteNarratives(bankId, accountId)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )



    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


