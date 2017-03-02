package code.remotedata

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.event.Logging
import akka.util.Timeout
import code.metadata.counterparties.{CounterpartyTrait, MapperCounterparties, RemoteCounterpartiesCaseClasses}
import code.model._
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo

import scala.concurrent.duration._


class RemotedataCounterpartiesActor extends Actor {

  val logger = Logging(context.system, this)

  val mCounterparties = MapperCounterparties
  val rCounterparties = RemoteCounterpartiesCaseClasses

  def receive = {

    case rCounterparties.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)=>

      logger.info("checkCounterpartyAvailable(" + name +", "+ thisBankId +", "+ thisAccountId +", "+ thisViewId +")")

      sender ! mCounterparties.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)

    case rCounterparties.createCounterparty(createdByUserId, thisBankId, thisAccountId, thisViewId,
                                            name, otherBankId, otherAccountId, otherAccountRoutingScheme,
                                            otherAccountRoutingAddress, otherBankRoutingScheme, 
                                            otherBankRoutingAddress, isBeneficiary) =>

      logger.info("createCounterparty(" + createdByUserId +", "+ thisBankId +", "+ thisAccountId +", "+ thisViewId +", "+ name +", "+ otherBankId + otherAccountId +", "
                    + otherAccountRoutingScheme +", "+ otherAccountRoutingAddress +", "+ otherBankRoutingScheme +", "+ otherBankRoutingAddress +", "+ isBeneficiary+ ")")

      {
        for {
          res <- mCounterparties.createCounterparty(createdByUserId, thisBankId, thisAccountId, thisViewId, name, otherBankId, otherAccountId,
                                                    otherAccountRoutingScheme, otherAccountRoutingAddress, otherBankRoutingScheme, otherBankRoutingAddress,
                                                    isBeneficiary)
        } yield {
          sender ! res.asInstanceOf[CounterpartyTrait]
        }
      }.getOrElse( context.stop(sender) )



    case rCounterparties.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty) =>

      logger.info("getOrCreateMetadata(" + originalPartyBankId +", " +originalPartyAccountId+otherParty+")")

      {
        for {
          res <- mCounterparties.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty)
        } yield {
          sender ! res.asInstanceOf[CounterpartyMetadata]
        }
      }.getOrElse( context.stop(sender) )

    case rCounterparties.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId) =>
      logger.info("getOrCreateMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")

      Full({
             for {
               res <- Full(mCounterparties.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId))
             } yield {
               sender ! res.asInstanceOf[List[CounterpartyMetadata]]
             }
           }).getOrElse(context.stop(sender))


    case rCounterparties.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String) =>

        logger.info("getMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")

      {
        for {
          res <- mCounterparties.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String)
        } yield {
          sender ! res.asInstanceOf[CounterpartyMetadata]
        }
      }.getOrElse( context.stop(sender) )



    case rCounterparties.getCounterparty(counterPartyId: String) =>

      logger.info("getCounterparty(" + counterPartyId +")")

      {
        for {
          res <- mCounterparties.getCounterparty(counterPartyId: String)
        } yield {
          sender ! res.asInstanceOf[CounterpartyTrait]
        }
      }.getOrElse( context.stop(sender) )


    case rCounterparties.getCounterpartyByIban(iban: String) =>

      logger.info("getOrCreateMetadata(" + iban +")")

      {
        for {
          res <- mCounterparties.getCounterpartyByIban(iban: String)
        } yield {
          sender ! res.asInstanceOf[CounterpartyTrait]
        }
      }.getOrElse( context.stop(sender) )


    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}
