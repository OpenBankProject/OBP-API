package code.remotedata

import akka.actor.Actor
import akka.event.Logging
import code.metadata.counterparties.{MapperCounterparties, RemotedataCounterpartiesCaseClasses}
import code.model._

import scala.concurrent.duration._


class RemotedataCounterpartiesActor extends Actor with ActorHelper {

  val logger = Logging(context.system, this)

  val mapper = MapperCounterparties
  val cc = RemotedataCounterpartiesCaseClasses

  def receive = {

    case cc.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)=>
      logger.info("checkCounterpartyAvailable(" + name +", "+ thisBankId +", "+ thisAccountId +", "+ thisViewId +")")
      sender ! extractResult(mapper.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String))

    case cc.createCounterparty(createdByUserId, thisBankId, thisAccountId, thisViewId,
                               name, otherAccountRoutingScheme,
                               otherAccountRoutingAddress, otherBankRoutingScheme,
                               otherBankRoutingAddress, isBeneficiary) =>
      logger.info("createCounterparty(" + createdByUserId + ", " + thisBankId + ", " + thisAccountId + ", " + thisViewId + ", " + name + ", "
                    + otherAccountRoutingScheme +", "+ otherAccountRoutingAddress +", "+ otherBankRoutingScheme +", "+ otherBankRoutingAddress +", "+ isBeneficiary+ ")")
      sender ! extractResult(mapper.createCounterparty(createdByUserId, thisBankId, thisAccountId, thisViewId, name,
                                           otherAccountRoutingScheme, otherAccountRoutingAddress, otherBankRoutingScheme, otherBankRoutingAddress,
                                           isBeneficiary))

    case cc.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty) =>
      logger.info("getOrCreateMetadata(" + originalPartyBankId +", " +originalPartyAccountId+otherParty+")")
      sender ! extractResult(mapper.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty))

    case cc.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId) =>
      logger.info("getOrCreateMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")
      sender ! extractResult(mapper.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId))

    case cc.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String) =>
      logger.info("getMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")
      sender ! extractResult(mapper.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String))

    case cc.getCounterparty(counterPartyId: String) =>
      logger.info("getCounterparty(" + counterPartyId +")")
      sender ! extractResult(mapper.getCounterparty(counterPartyId: String))

    case cc.getCounterpartyByIban(iban: String) =>
      logger.info("getOrCreateMetadata(" + iban +")")
      sender ! extractResult(mapper.getCounterpartyByIban(iban: String))

    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}
