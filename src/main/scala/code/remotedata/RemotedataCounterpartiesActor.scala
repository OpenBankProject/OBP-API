package code.remotedata

import java.util.Date

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.counterparties.{MapperCounterparties, RemotedataCounterpartiesCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable


class RemotedataCounterpartiesActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MapperCounterparties
  val cc = RemotedataCounterpartiesCaseClasses

  def receive = {

    case cc.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)=>
      logger.debug("checkCounterpartyAvailable(" + name +", "+ thisBankId +", "+ thisAccountId +", "+ thisViewId +")")
      sender ! extractResult(mapper.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String))

    case cc.createCounterparty(createdByUserId, thisBankId, thisAccountId, thisViewId,
                               name, otherAccountRoutingScheme,
                               otherAccountRoutingAddress, otherBankRoutingScheme,
                               otherBranchRoutingScheme, otherBranchRoutingAddress,
                               otherBankRoutingAddress, isBeneficiary) =>
      logger.debug("createCounterparty(" + createdByUserId + ", " + thisBankId + ", " + thisAccountId + ", " + thisViewId + ", " + name + ", "
                    + otherAccountRoutingScheme +", "+ otherAccountRoutingAddress +", "+ otherBankRoutingScheme +", "+ otherBankRoutingAddress +", "+ otherBranchRoutingScheme+
                    ", "+ otherBranchRoutingAddress+ ", "+ isBeneficiary+")")
      sender ! extractResult(mapper.createCounterparty(createdByUserId, thisBankId, thisAccountId, thisViewId,
                                                       name, otherAccountRoutingScheme,
                                                       otherAccountRoutingAddress, otherBankRoutingScheme,
                                                       otherBranchRoutingScheme, otherBranchRoutingAddress,
                                                       otherBankRoutingAddress, isBeneficiary))

    case cc.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty) =>
      logger.debug("getOrCreateMetadata(" + originalPartyBankId +", " +originalPartyAccountId+otherParty+")")
      sender ! extractResult(mapper.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty))

    case cc.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId) =>
      logger.debug("getOrCreateMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")
      sender ! extractResult(mapper.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId))

    case cc.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String) =>
      logger.debug("getMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")
      sender ! extractResult(mapper.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String))

    case cc.getCounterparty(counterPartyId: String) =>
      logger.debug("getCounterparty(" + counterPartyId +")")
      sender ! extractResult(mapper.getCounterparty(counterPartyId: String))

    case cc.getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId) =>
      logger.debug("getCounterparties(" + thisBankId +")")
      sender ! extractResult(mapper.getCounterparties(thisBankId, thisAccountId, viewId))

    case cc.getCounterpartyByIban(iban: String) =>
      logger.debug("getOrCreateMetadata(" + iban +")")
      sender ! extractResult(mapper.getCounterpartyByIban(iban: String))

    case cc.getPublicAlias(counterPartyId: String) =>
      logger.debug("getPublicAlias(" + counterPartyId + ")")
      sender ! extractResult(mapper.getPublicAlias(counterPartyId))

    case cc.getPrivateAlias(counterPartyId: String) =>
      logger.debug("getPrivateAlias(" + counterPartyId + ")")
      sender ! extractResult(mapper.getPrivateAlias(counterPartyId))

    case cc.getCorporateLocation(counterPartyId: String) =>
      logger.debug("getCorporateLocation(" + counterPartyId + ")")
      sender ! extractResult(mapper.getCorporateLocation(counterPartyId))

    case cc.getPhysicalLocation(counterPartyId: String) =>
      logger.debug("getPhysicalLocation(" + counterPartyId + ")")
      sender ! extractResult(mapper.getPhysicalLocation(counterPartyId))

    case cc.getOpenCorporatesURL(counterPartyId: String) =>
      logger.debug("getOpenCorporatesURL(" + counterPartyId + ")")
      sender ! extractResult(mapper.getOpenCorporatesURL(counterPartyId))

    case cc.getImageURL(counterPartyId: String) =>
      logger.debug("getImageURL(" + counterPartyId + ")")
      sender ! extractResult(mapper.getImageURL(counterPartyId))

    case cc.getUrl(counterPartyId: String) =>
      logger.debug("getUrl(" + counterPartyId + ")")
      sender ! extractResult(mapper.getUrl(counterPartyId))

    case cc.getMoreInfo(counterPartyId: String) =>
      logger.debug("getMoreInfo(" + counterPartyId + ")")
      sender ! extractResult(mapper.getMoreInfo(counterPartyId))

    case cc.addPrivateAlias(counterPartyId: String, alias: String) =>
      logger.debug("addPrivateAlias(" + counterPartyId + ", " + alias +")")
      sender ! extractResult(mapper.addPrivateAlias(counterPartyId, alias))

    case cc.addPublicAlias(counterPartyId: String, alias: String) =>
      logger.debug("addPublicAlias(" + counterPartyId + ", " + alias +")")
      sender ! extractResult(mapper.addPublicAlias(counterPartyId, alias))

    case cc.addURL(counterPartyId: String, url: String) =>
      logger.debug("addURL(" + counterPartyId + ", " + url +")")
      sender ! extractResult(mapper.addURL(counterPartyId, url))

    case cc.addImageURL(counterPartyId: String, url: String) =>
      logger.debug("addImageURL(" + counterPartyId + ", " + url +")")
      sender ! extractResult(mapper.addImageURL(counterPartyId, url))

    case cc.addOpenCorporatesURL(counterPartyId: String, url: String) =>
      logger.debug("addOpenCorporatesURL(" + counterPartyId + ", " + url +")")
      sender ! extractResult(mapper.addOpenCorporatesURL(counterPartyId, url))

    case cc.addMoreInfo(counterPartyId : String, moreInfo: String) =>
      logger.debug("addMoreInfo(" + counterPartyId + ", " + moreInfo +")")
      sender ! extractResult(mapper.addMoreInfo(counterPartyId, moreInfo))

    case cc.addPhysicalLocation(counterPartyId : String, userId: UserId, datePosted : Date, longitude : Double, latitude : Double) =>
      logger.debug("addPhysicalLocation(" + counterPartyId + ", " + userId + ", " + datePosted + ", " + longitude + ", " + latitude +")")
      sender ! extractResult(mapper.addPhysicalLocation(counterPartyId, userId, datePosted, longitude, latitude))

    case cc.addCorporateLocation(counterPartyId : String, userId: UserId, datePosted : Date, longitude : Double, latitude : Double) =>
      logger.debug("addCorporateLocation(" + counterPartyId + ", " + userId + ", " + datePosted + ", " + longitude + ", " + latitude +")")
      sender ! extractResult(mapper.addCorporateLocation(counterPartyId, userId, datePosted, longitude, latitude))

    case cc.deleteCorporateLocation(counterPartyId: String) =>
      logger.debug("deleteCorporateLocation(" + counterPartyId + ")")
      sender ! extractResult(mapper.deleteCorporateLocation(counterPartyId))

    case cc.deletePhysicalLocation(counterPartyId: String) =>
      logger.debug("deletePhysicalLocation(" + counterPartyId + ")")
      sender ! extractResult(mapper.deletePhysicalLocation(counterPartyId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}
