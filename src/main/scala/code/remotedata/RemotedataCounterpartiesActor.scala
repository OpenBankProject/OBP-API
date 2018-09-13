package code.remotedata

import java.util.Date

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.counterparties.{MapperCounterparties, RemotedataCounterpartiesCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable

import scala.collection.immutable.List


class RemotedataCounterpartiesActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MapperCounterparties
  val cc = RemotedataCounterpartiesCaseClasses

  def receive = {

    case cc.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)=>
      logger.debug("checkCounterpartyAvailable(" + name +", "+ thisBankId +", "+ thisAccountId +", "+ thisViewId +")")
      sender ! extractResult(mapper.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String))

    case cc.createCounterparty(
      createdByUserId: String,
      thisBankId: String,
      thisAccountId: String,
      thisViewId: String,
      name: String,
      otherAccountRoutingScheme: String,
      otherAccountRoutingAddress: String,
      otherBankRoutingScheme: String,
      otherBankRoutingAddress: String,
      otherBranchRoutingScheme: String,
      otherBranchRoutingAddress: String,
      isBeneficiary: Boolean,
      otherAccountSecondaryRoutingScheme: String,
      otherAccountSecondaryRoutingAddress: String,
      description: String,
      bespoke: List[CounterpartyBespoke]
    ) =>
      logger.debug("createCounterparty(" +createdByUserId + ", " + thisBankId + ", " + thisAccountId + ", " + thisViewId + ", " + name + ", "
                    + otherAccountRoutingScheme +", "+ otherAccountRoutingAddress +", "+ otherBankRoutingScheme +", "+ otherBankRoutingAddress +", "+ otherBranchRoutingScheme+
                    ", "+ otherBranchRoutingAddress+ ", "+ isBeneficiary+", "+ otherAccountSecondaryRoutingScheme+", "+ otherAccountSecondaryRoutingAddress+", "+ description+", "+ bespoke+")")
      sender ! extractResult(mapper.createCounterparty(
        createdByUserId: String,
        thisBankId: String,
        thisAccountId: String,
        thisViewId: String,
        name: String,
        otherAccountRoutingScheme: String,
        otherAccountRoutingAddress: String,
        otherBankRoutingScheme: String,
        otherBankRoutingAddress: String,
        otherBranchRoutingScheme: String,
        otherBranchRoutingAddress: String,
        isBeneficiary: Boolean,
        otherAccountSecondaryRoutingScheme: String,
        otherAccountSecondaryRoutingAddress: String,
        description: String,
        bespoke: List[CounterpartyBespoke]
      ))

    case cc.getOrCreateMetadata(bankId: BankId, accountId : AccountId, counterpartyId:String, counterpartyName:String)  =>
      logger.debug("getOrCreateMetadata(" + bankId +", " +accountId+counterpartyId+")")
      sender ! extractResult(mapper.getOrCreateMetadata(bankId: BankId, accountId : AccountId, counterpartyId:String, counterpartyName:String) )

    case cc.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId) =>
      logger.debug("getOrCreateMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")
      sender ! extractResult(mapper.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId))

    case cc.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String) =>
      logger.debug("getMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")
      sender ! extractResult(mapper.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String))

    case cc.getCounterparty(counterpartyId: String) =>
      logger.debug("getCounterparty(" + counterpartyId +")")
      sender ! extractResult(mapper.getCounterparty(counterpartyId: String))

    case cc.getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId) =>
      logger.debug("getCounterparties(" + thisBankId +")")
      sender ! extractResult(mapper.getCounterparties(thisBankId, thisAccountId, viewId))

    case cc.getCounterpartyByIban(iban: String) =>
      logger.debug("getOrCreateMetadata(" + iban +")")
      sender ! extractResult(mapper.getCounterpartyByIban(iban: String))

    case cc.getPublicAlias(counterpartyId: String) =>
      logger.debug("getPublicAlias(" + counterpartyId + ")")
      sender ! extractResult(mapper.getPublicAlias(counterpartyId))

    case cc.getPrivateAlias(counterpartyId: String) =>
      logger.debug("getPrivateAlias(" + counterpartyId + ")")
      sender ! extractResult(mapper.getPrivateAlias(counterpartyId))

    case cc.getCorporateLocation(counterpartyId: String) =>
      logger.debug("getCorporateLocation(" + counterpartyId + ")")
      sender ! extractResult(mapper.getCorporateLocation(counterpartyId))

    case cc.getPhysicalLocation(counterpartyId: String) =>
      logger.debug("getPhysicalLocation(" + counterpartyId + ")")
      sender ! extractResult(mapper.getPhysicalLocation(counterpartyId))

    case cc.getOpenCorporatesURL(counterpartyId: String) =>
      logger.debug("getOpenCorporatesURL(" + counterpartyId + ")")
      sender ! extractResult(mapper.getOpenCorporatesURL(counterpartyId))

    case cc.getImageURL(counterpartyId: String) =>
      logger.debug("getImageURL(" + counterpartyId + ")")
      sender ! extractResult(mapper.getImageURL(counterpartyId))

    case cc.getUrl(counterpartyId: String) =>
      logger.debug("getUrl(" + counterpartyId + ")")
      sender ! extractResult(mapper.getUrl(counterpartyId))

    case cc.getMoreInfo(counterpartyId: String) =>
      logger.debug("getMoreInfo(" + counterpartyId + ")")
      sender ! extractResult(mapper.getMoreInfo(counterpartyId))

    case cc.addPrivateAlias(counterpartyId: String, alias: String) =>
      logger.debug("addPrivateAlias(" + counterpartyId + ", " + alias +")")
      sender ! extractResult(mapper.addPrivateAlias(counterpartyId, alias))

    case cc.addPublicAlias(counterpartyId: String, alias: String) =>
      logger.debug("addPublicAlias(" + counterpartyId + ", " + alias +")")
      sender ! extractResult(mapper.addPublicAlias(counterpartyId, alias))

    case cc.addURL(counterpartyId: String, url: String) =>
      logger.debug("addURL(" + counterpartyId + ", " + url +")")
      sender ! extractResult(mapper.addURL(counterpartyId, url))

    case cc.addImageURL(counterpartyId: String, url: String) =>
      logger.debug("addImageURL(" + counterpartyId + ", " + url +")")
      sender ! extractResult(mapper.addImageURL(counterpartyId, url))

    case cc.addOpenCorporatesURL(counterpartyId: String, url: String) =>
      logger.debug("addOpenCorporatesURL(" + counterpartyId + ", " + url +")")
      sender ! extractResult(mapper.addOpenCorporatesURL(counterpartyId, url))

    case cc.addMoreInfo(counterpartyId : String, moreInfo: String) =>
      logger.debug("addMoreInfo(" + counterpartyId + ", " + moreInfo +")")
      sender ! extractResult(mapper.addMoreInfo(counterpartyId, moreInfo))

    case cc.addPhysicalLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double) =>
      logger.debug("addPhysicalLocation(" + counterpartyId + ", " + userId + ", " + datePosted + ", " + longitude + ", " + latitude +")")
      sender ! extractResult(mapper.addPhysicalLocation(counterpartyId, userId, datePosted, longitude, latitude))

    case cc.addCorporateLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double) =>
      logger.debug("addCorporateLocation(" + counterpartyId + ", " + userId + ", " + datePosted + ", " + longitude + ", " + latitude +")")
      sender ! extractResult(mapper.addCorporateLocation(counterpartyId, userId, datePosted, longitude, latitude))

    case cc.deleteCorporateLocation(counterpartyId: String) =>
      logger.debug("deleteCorporateLocation(" + counterpartyId + ")")
      sender ! extractResult(mapper.deleteCorporateLocation(counterpartyId))

    case cc.deletePhysicalLocation(counterpartyId: String) =>
      logger.debug("deletePhysicalLocation(" + counterpartyId + ")")
      sender ! extractResult(mapper.deletePhysicalLocation(counterpartyId))

    case cc.bulkDeleteAllCounterparties() =>
      logger.debug("bulkDeleteAllCounterparties()")
      sender ! extractResult(mapper.bulkDeleteAllCounterparties())
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}
