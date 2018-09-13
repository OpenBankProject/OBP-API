package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.metadata.counterparties.{Counterparties, CounterpartyTrait, RemotedataCounterpartiesCaseClasses}
import code.model._
import net.liftweb.common.Box

import scala.collection.immutable.List


object RemotedataCounterparties extends ObpActorInit with Counterparties {

  val cc = RemotedataCounterpartiesCaseClasses

  override def getOrCreateMetadata(bankId: BankId, accountId : AccountId, counterpartyId:String, counterpartyName:String) : Box[CounterpartyMetadata] =
    extractFutureToBox(actor ? cc.getOrCreateMetadata(bankId: BankId, accountId : AccountId, counterpartyId:String, counterpartyName:String))
  
  override def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId): List[CounterpartyMetadata] =
    extractFuture(actor ? cc.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId))

  override def getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String): Box[CounterpartyMetadata] =
    extractFutureToBox(actor ? cc.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String))

  override def getCounterparty(counterpartyId: String): Box[CounterpartyTrait] =
    extractFutureToBox(actor ? cc.getCounterparty(counterpartyId: String))

  override def getCounterpartyByIban(iban: String): Box[CounterpartyTrait] =
    extractFutureToBox(actor ? cc.getCounterpartyByIban(iban: String))

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId): Box[List[CounterpartyTrait]] =
    extractFutureToBox(actor ? cc.getCounterparties(thisBankId, thisAccountId, viewId))
  
  override def createCounterparty(
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
                                  bespoke: List[CounterpartyBespoke]): Box[CounterpartyTrait] =
    extractFutureToBox(actor ? cc.createCounterparty(createdByUserId, thisBankId,
                                                      thisAccountId, thisViewId, name,
                                                      otherAccountRoutingScheme,
                                                      otherAccountRoutingAddress,
                                                      otherBankRoutingScheme,
                                                      otherBankRoutingAddress,
                                                      otherBranchRoutingScheme,
                                                      otherBranchRoutingAddress,
                                                      isBeneficiary,
                                                      otherAccountSecondaryRoutingScheme,
                                                      otherAccountSecondaryRoutingAddress,
                                                      description,
                                                      bespoke))

  override def checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String): Boolean =
    extractFuture(actor ? cc.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String))

  override def getCorporateLocation(counterpartyId: String): Box[GeoTag] =
    extractFutureToBox(actor ? cc.getCorporateLocation(counterpartyId))

  override def getPublicAlias(counterpartyId: String): Box[String] =
    extractFutureToBox(actor ? cc.getPublicAlias(counterpartyId))

  override def getPrivateAlias(counterpartyId: String): Box[String] =
    extractFutureToBox(actor ? cc.getPrivateAlias(counterpartyId))

  override def getPhysicalLocation(counterpartyId: String): Box[GeoTag] =
    extractFutureToBox(actor ? cc.getPhysicalLocation(counterpartyId))

  override def getOpenCorporatesURL(counterpartyId: String): Box[String] =
    extractFutureToBox(actor ? cc.getOpenCorporatesURL(counterpartyId))

  override def getImageURL(counterpartyId: String): Box[String] =
  extractFutureToBox(actor ? cc.getImageURL(counterpartyId))

  override def getUrl(counterpartyId: String): Box[String] =
  extractFutureToBox(actor ? cc.getUrl(counterpartyId))

  override def getMoreInfo(counterpartyId: String): Box[String] =
  extractFutureToBox(actor ? cc.getMoreInfo(counterpartyId))

  override def addPublicAlias(counterpartyId: String, alias: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.addPublicAlias(counterpartyId, alias))

  override def addPrivateAlias(counterpartyId: String, alias: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.addPrivateAlias(counterpartyId, alias))

  override def addURL(counterpartyId: String, url: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.addURL(counterpartyId, url))

  override def addImageURL(counterpartyId: String, url: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.addImageURL(counterpartyId, url))

  override def addOpenCorporatesURL(counterpartyId: String, url: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.addOpenCorporatesURL(counterpartyId, url))

  override def addMoreInfo(counterpartyId : String, moreInfo: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.addMoreInfo(counterpartyId, moreInfo))

  override def addPhysicalLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean] =
    extractFutureToBox(actor ? cc.addPhysicalLocation(counterpartyId, userId, datePosted, longitude, latitude))

  override def addCorporateLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean] =
    extractFutureToBox(actor ? cc.addCorporateLocation(counterpartyId, userId, datePosted, longitude, latitude))

  override def deletePhysicalLocation(counterpartyId: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.deletePhysicalLocation(counterpartyId))

  override def deleteCorporateLocation(counterpartyId: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.deleteCorporateLocation(counterpartyId))
  
  override def bulkDeleteAllCounterparties(): Box[Boolean] =
    extractFutureToBox(actor ? cc.bulkDeleteAllCounterparties())

}
