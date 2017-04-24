package code.remotedata

import java.util.Date

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.metadata.counterparties.{Counterparties, CounterpartyTrait, RemotedataCounterpartiesCaseClasses}
import code.model._
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataCounterparties extends RemotedataActorInit with Counterparties {

  val cc = RemotedataCounterpartiesCaseClasses

  override def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty): Box[CounterpartyMetadata] =
    extractFutureToBox(actor ? cc.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty))

  override def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId): List[CounterpartyMetadata] =
    extractFuture(actor ? cc.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId))

  override def getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String): Box[CounterpartyMetadata] =
    extractFutureToBox(actor ? cc.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String))

  override def getCounterparty(counterPartyId: String): Box[CounterpartyTrait] =
    extractFutureToBox(actor ? cc.getCounterparty(counterPartyId: String))

  override def getCounterpartyByIban(iban: String): Box[CounterpartyTrait] =
    extractFutureToBox(actor ? cc.getCounterpartyByIban(iban: String))

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId): Box[List[CounterpartyTrait]] =
    extractFutureToBox(actor ? cc.getCounterparties(thisBankId, thisAccountId, viewId))
  
  override def createCounterparty(createdByUserId: String,
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
                                  isBeneficiary: Boolean): Box[CounterpartyTrait] =
    extractFutureToBox(actor ? cc.createCounterparty( createdByUserId, thisBankId,
                                                      thisAccountId, thisViewId, name,
                                                      otherAccountRoutingScheme,
                                                      otherAccountRoutingAddress,
                                                      otherBankRoutingScheme,
                                                      otherBankRoutingAddress,
                                                      otherBranchRoutingScheme,
                                                      otherBranchRoutingAddress,
                                                      isBeneficiary))

  override def checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String): Boolean =
    extractFuture(actor ? cc.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String))

  override def getCorporateLocation(counterPartyId: String): Box[GeoTag] =
    extractFutureToBox(actor ? cc.getCorporateLocation(counterPartyId))

  override def getPublicAlias(counterPartyId: String): Box[String] =
    extractFutureToBox(actor ? cc.getPublicAlias(counterPartyId))

  override def getPrivateAlias(counterPartyId: String): Box[String] =
    extractFutureToBox(actor ? cc.getPrivateAlias(counterPartyId))

  override def getPhysicalLocation(counterPartyId: String): Box[GeoTag] =
    extractFutureToBox(actor ? cc.getPhysicalLocation(counterPartyId))

  override def getOpenCorporatesURL(counterPartyId: String): Box[String] =
    extractFutureToBox(actor ? cc.getOpenCorporatesURL(counterPartyId))

  override def getImageURL(counterPartyId: String): Box[String] =
  extractFutureToBox(actor ? cc.getImageURL(counterPartyId))

  override def getUrl(counterPartyId: String): Box[String] =
  extractFutureToBox(actor ? cc.getUrl(counterPartyId))

  override def getMoreInfo(counterPartyId: String): Box[String] =
  extractFutureToBox(actor ? cc.getMoreInfo(counterPartyId))

  override def addPublicAlias(counterPartyId: String, alias: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.addPublicAlias(counterPartyId, alias))

  override def addPrivateAlias(counterPartyId: String, alias: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.addPrivateAlias(counterPartyId, alias))

  override def addURL(counterPartyId: String, url: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.addURL(counterPartyId, url))

  override def addImageURL(counterPartyId: String, url: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.addImageURL(counterPartyId, url))

  override def addOpenCorporatesURL(counterPartyId: String, url: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.addOpenCorporatesURL(counterPartyId, url))

  override def addMoreInfo(counterPartyId : String, moreInfo: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.addMoreInfo(counterPartyId, moreInfo))

  override def addPhysicalLocation(counterPartyId : String, userId: UserId, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean] =
    extractFutureToBox(actor ? cc.addPhysicalLocation(counterPartyId, userId, datePosted, longitude, latitude))

  override def addCorporateLocation(counterPartyId : String, userId: UserId, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean] =
    extractFutureToBox(actor ? cc.addCorporateLocation(counterPartyId, userId, datePosted, longitude, latitude))

  override def deletePhysicalLocation(counterPartyId: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.deletePhysicalLocation(counterPartyId))

  override def deleteCorporateLocation(counterPartyId: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.deleteCorporateLocation(counterPartyId))

}
