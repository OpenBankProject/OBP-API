package code.remotedata

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.event.Logging
import akka.util.Timeout
import code.metadata.counterparties.{CounterpartyTrait, MapperCounterparties, RemotedataCounterpartiesCaseClasses}
import code.model._
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo

import scala.concurrent.duration._


class RemotedataCounterpartiesActor extends Actor {

  val logger = Logging(context.system, this)

  val mapper = MapperCounterparties
  val cc = RemotedataCounterpartiesCaseClasses

  def receive = {

    case cc.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)=>

      logger.info("checkCounterpartyAvailable(" + name +", "+ thisBankId +", "+ thisAccountId +", "+ thisViewId +")")

      sender ! mapper.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)

    case cc.createCounterparty(createdByUserId, thisBankId, thisAccountId, thisViewId,
                               name, otherAccountRoutingScheme,
                               otherAccountRoutingAddress, otherBankRoutingScheme,
                               otherBankRoutingAddress, isBeneficiary) =>
  
      logger.info("createCounterparty(" + createdByUserId + ", " + thisBankId + ", " + thisAccountId + ", " + thisViewId + ", " + name + ", "
                    + otherAccountRoutingScheme +", "+ otherAccountRoutingAddress +", "+ otherBankRoutingScheme +", "+ otherBankRoutingAddress +", "+ isBeneficiary+ ")")

      {
        for {
          res <- mapper.createCounterparty(createdByUserId, thisBankId, thisAccountId, thisViewId, name,
                                           otherAccountRoutingScheme, otherAccountRoutingAddress, otherBankRoutingScheme, otherBankRoutingAddress,
                                           isBeneficiary)
        } yield {
          sender ! res.asInstanceOf[CounterpartyTrait]
        }
      }.getOrElse( context.stop(sender) )



    case cc.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty) =>

      logger.info("getOrCreateMetadata(" + originalPartyBankId +", " +originalPartyAccountId+otherParty+")")

      {
        for {
          res <- mapper.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty)
        } yield {
          sender ! res.asInstanceOf[CounterpartyMetadata]
        }
      }.getOrElse( context.stop(sender) )

    case cc.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId) =>
      logger.info("getOrCreateMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")

      Full({
             for {
               res <- Full(mapper.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId))
             } yield {
               sender ! res.asInstanceOf[List[CounterpartyMetadata]]
             }
           }).getOrElse(context.stop(sender))


    case cc.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String) =>

        logger.info("getMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")

      {
        for {
          res <- mapper.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String)
        } yield {
          sender ! res.asInstanceOf[CounterpartyMetadata]
        }
      }.getOrElse( context.stop(sender) )



    case cc.getCounterparty(counterPartyId: String) =>

      logger.info("getCounterparty(" + counterPartyId +")")

      {
        for {
          res <- mapper.getCounterparty(counterPartyId: String)
        } yield {
          sender ! res.asInstanceOf[CounterpartyTrait]
        }
      }.getOrElse( context.stop(sender) )


    case cc.getCounterpartyByIban(iban: String) =>

      logger.info("getOrCreateMetadata(" + iban +")")

      {
        for {
          res <- mapper.getCounterpartyByIban(iban: String)
        } yield {
          sender ! res.asInstanceOf[CounterpartyTrait]
        }
      }.getOrElse( context.stop(sender) )


    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}
