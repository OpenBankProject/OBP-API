package code.remotedata

import java.util.Date

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.counterparties.{MapperCounterparties, RemotedataCounterpartiesCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._

import scala.collection.immutable.List


class RemotedataCounterpartiesActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MapperCounterparties
  val cc = RemotedataCounterpartiesCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)=>
      logger.debug("checkCounterpartyAvailable(" + name +", "+ thisBankId +", "+ thisAccountId +", "+ thisViewId +")")
      sender ! (mapper.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String))

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
      sender ! (mapper.createCounterparty(
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
      sender ! (mapper.getOrCreateMetadata(bankId: BankId, accountId : AccountId, counterpartyId:String, counterpartyName:String) )

    case cc.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId) =>
      logger.debug("getOrCreateMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")
      sender ! (mapper.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId))

    case cc.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String) =>
      logger.debug("getMetadata(" + originalPartyBankId +", "+originalPartyAccountId+")")
      sender ! (mapper.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String))

    case cc.getCounterparty(counterpartyId: String) =>
      logger.debug("getCounterparty(" + counterpartyId +")")
      sender ! (mapper.getCounterparty(counterpartyId: String))

    case cc.getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId) =>
      logger.debug("getCounterparties(" + thisBankId +")")
      sender ! (mapper.getCounterparties(thisBankId, thisAccountId, viewId))

    case cc.getCounterpartyByIban(iban: String) =>
      logger.debug("getOrCreateMetadata(" + iban +")")
      sender ! (mapper.getCounterpartyByIban(iban: String))

    case cc.getPublicAlias(counterpartyId: String) =>
      logger.debug("getPublicAlias(" + counterpartyId + ")")
      sender ! (mapper.getPublicAlias(counterpartyId))

    case cc.getPrivateAlias(counterpartyId: String) =>
      logger.debug("getPrivateAlias(" + counterpartyId + ")")
      sender ! (mapper.getPrivateAlias(counterpartyId))

    case cc.getCorporateLocation(counterpartyId: String) =>
      logger.debug("getCorporateLocation(" + counterpartyId + ")")
      sender ! (mapper.getCorporateLocation(counterpartyId))

    case cc.getPhysicalLocation(counterpartyId: String) =>
      logger.debug("getPhysicalLocation(" + counterpartyId + ")")
      sender ! (mapper.getPhysicalLocation(counterpartyId))

    case cc.getOpenCorporatesURL(counterpartyId: String) =>
      logger.debug("getOpenCorporatesURL(" + counterpartyId + ")")
      sender ! (mapper.getOpenCorporatesURL(counterpartyId))

    case cc.getImageURL(counterpartyId: String) =>
      logger.debug("getImageURL(" + counterpartyId + ")")
      sender ! (mapper.getImageURL(counterpartyId))

    case cc.getUrl(counterpartyId: String) =>
      logger.debug("getUrl(" + counterpartyId + ")")
      sender ! (mapper.getUrl(counterpartyId))

    case cc.getMoreInfo(counterpartyId: String) =>
      logger.debug("getMoreInfo(" + counterpartyId + ")")
      sender ! (mapper.getMoreInfo(counterpartyId))

    case cc.addPrivateAlias(counterpartyId: String, alias: String) =>
      logger.debug("addPrivateAlias(" + counterpartyId + ", " + alias +")")
      sender ! (mapper.addPrivateAlias(counterpartyId, alias))

    case cc.addPublicAlias(counterpartyId: String, alias: String) =>
      logger.debug("addPublicAlias(" + counterpartyId + ", " + alias +")")
      sender ! (mapper.addPublicAlias(counterpartyId, alias))

    case cc.addURL(counterpartyId: String, url: String) =>
      logger.debug("addURL(" + counterpartyId + ", " + url +")")
      sender ! (mapper.addURL(counterpartyId, url))

    case cc.addImageURL(counterpartyId: String, url: String) =>
      logger.debug("addImageURL(" + counterpartyId + ", " + url +")")
      sender ! (mapper.addImageURL(counterpartyId, url))

    case cc.addOpenCorporatesURL(counterpartyId: String, url: String) =>
      logger.debug("addOpenCorporatesURL(" + counterpartyId + ", " + url +")")
      sender ! (mapper.addOpenCorporatesURL(counterpartyId, url))

    case cc.addMoreInfo(counterpartyId : String, moreInfo: String) =>
      logger.debug("addMoreInfo(" + counterpartyId + ", " + moreInfo +")")
      sender ! (mapper.addMoreInfo(counterpartyId, moreInfo))

    case cc.addPhysicalLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double) =>
      logger.debug("addPhysicalLocation(" + counterpartyId + ", " + userId + ", " + datePosted + ", " + longitude + ", " + latitude +")")
      sender ! (mapper.addPhysicalLocation(counterpartyId, userId, datePosted, longitude, latitude))

    case cc.addCorporateLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double) =>
      logger.debug("addCorporateLocation(" + counterpartyId + ", " + userId + ", " + datePosted + ", " + longitude + ", " + latitude +")")
      sender ! (mapper.addCorporateLocation(counterpartyId, userId, datePosted, longitude, latitude))

    case cc.deleteCorporateLocation(counterpartyId: String) =>
      logger.debug("deleteCorporateLocation(" + counterpartyId + ")")
      sender ! (mapper.deleteCorporateLocation(counterpartyId))

    case cc.deletePhysicalLocation(counterpartyId: String) =>
      logger.debug("deletePhysicalLocation(" + counterpartyId + ")")
      sender ! (mapper.deletePhysicalLocation(counterpartyId))

    case cc.bulkDeleteAllCounterparties() =>
      logger.debug("bulkDeleteAllCounterparties()")
      sender ! (mapper.bulkDeleteAllCounterparties())
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}
