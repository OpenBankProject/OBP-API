package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.metadata.counterparties.{Counterparties, RemotedataCounterpartiesCaseClasses}
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box

import scala.collection.immutable.List


object RemotedataCounterparties extends ObpActorInit with Counterparties {

  val cc = RemotedataCounterpartiesCaseClasses

  override def getOrCreateMetadata(bankId: BankId, accountId : AccountId, counterpartyId:String, counterpartyName:String) : Box[CounterpartyMetadata] = getValueFromFuture(
    (actor ? cc.getOrCreateMetadata(bankId: BankId, accountId : AccountId, counterpartyId:String, counterpartyName:String)).mapTo[Box[CounterpartyMetadata]]
  )
  
  override def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId): List[CounterpartyMetadata] = getValueFromFuture(
    (actor ? cc.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId)).mapTo[List[CounterpartyMetadata]]
  )

  override def getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String): Box[CounterpartyMetadata] = getValueFromFuture(
    (actor ? cc.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String)).mapTo[Box[CounterpartyMetadata]]
  )

  override def getCounterparty(counterpartyId: String): Box[CounterpartyTrait] = getValueFromFuture(
    (actor ? cc.getCounterparty(counterpartyId: String)).mapTo[Box[CounterpartyTrait]]
  )

  override def getCounterpartyByIban(iban: String): Box[CounterpartyTrait] = getValueFromFuture(
    (actor ? cc.getCounterpartyByIban(iban: String)).mapTo[Box[CounterpartyTrait]]
  )

  override def getCounterpartyByIbanAndBankAccountId(iban: String, bankId: BankId, accountId: AccountId): Box[CounterpartyTrait] = getValueFromFuture(
    (actor ? cc.getCounterpartyByIbanAndBankAccountId(iban: String, bankId: BankId, accountId: AccountId)).mapTo[Box[CounterpartyTrait]]
  )

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId): Box[List[CounterpartyTrait]] = getValueFromFuture(
    (actor ? cc.getCounterparties(thisBankId, thisAccountId, viewId)).mapTo[Box[List[CounterpartyTrait]]]
  )
  
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
                                  currency: String,
                                  bespoke: List[CounterpartyBespoke]): Box[CounterpartyTrait] = getValueFromFuture(
    (actor ? cc.createCounterparty(createdByUserId, 
                                  thisBankId,
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
                                  currency,
                                  bespoke)).mapTo[Box[CounterpartyTrait]]
  )

  override def checkCounterpartyExists(name: String, thisBankId: String, thisAccountId: String, thisViewId: String): Box[CounterpartyTrait] = getValueFromFuture(
    (actor ? cc.checkCounterpartyExists(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)).mapTo[Box[CounterpartyTrait]]
  )

  override def getCorporateLocation(counterpartyId: String): Box[GeoTag] = getValueFromFuture(
    (actor ? cc.getCorporateLocation(counterpartyId)).mapTo[Box[GeoTag]]
  )

  override def getPublicAlias(counterpartyId: String): Box[String] = getValueFromFuture(
    (actor ? cc.getPublicAlias(counterpartyId)).mapTo[Box[String]]
  )

  override def getPrivateAlias(counterpartyId: String): Box[String] = getValueFromFuture(
    (actor ? cc.getPrivateAlias(counterpartyId)).mapTo[Box[String]]
  )

  override def getPhysicalLocation(counterpartyId: String): Box[GeoTag] = getValueFromFuture(
    (actor ? cc.getPhysicalLocation(counterpartyId)).mapTo[Box[GeoTag]]
  )

  override def getOpenCorporatesURL(counterpartyId: String): Box[String] = getValueFromFuture(
    (actor ? cc.getOpenCorporatesURL(counterpartyId)).mapTo[Box[String]]
  )

  override def getImageURL(counterpartyId: String): Box[String] = getValueFromFuture(
    (actor ? cc.getImageURL(counterpartyId)).mapTo[Box[String]]
  )

  override def getUrl(counterpartyId: String): Box[String] = getValueFromFuture(
    (actor ? cc.getUrl(counterpartyId)).mapTo[Box[String]]
  )

  override def getMoreInfo(counterpartyId: String): Box[String] = getValueFromFuture(
    (actor ? cc.getMoreInfo(counterpartyId)).mapTo[Box[String]]
  )

  override def addPublicAlias(counterpartyId: String, alias: String): Box[Boolean] = getValueFromFuture(
    (actor ? cc.addPublicAlias(counterpartyId, alias)).mapTo[Box[Boolean]]
  )

  override def addPrivateAlias(counterpartyId: String, alias: String): Box[Boolean] = getValueFromFuture(
    (actor ? cc.addPrivateAlias(counterpartyId, alias)).mapTo[Box[Boolean]]
  )

  override def addURL(counterpartyId: String, url: String): Box[Boolean] = getValueFromFuture(
    (actor ? cc.addURL(counterpartyId, url)).mapTo[Box[Boolean]]
  )

  override def addImageURL(counterpartyId: String, url: String): Box[Boolean] = getValueFromFuture(
    (actor ? cc.addImageURL(counterpartyId, url)).mapTo[Box[Boolean]]
  )

  override def addOpenCorporatesURL(counterpartyId: String, url: String): Box[Boolean] = getValueFromFuture(
    (actor ? cc.addOpenCorporatesURL(counterpartyId, url)).mapTo[Box[Boolean]]
  )

  override def addMoreInfo(counterpartyId : String, moreInfo: String): Box[Boolean] = getValueFromFuture(
    (actor ? cc.addMoreInfo(counterpartyId, moreInfo)).mapTo[Box[Boolean]]
  )

  override def addPhysicalLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean] = getValueFromFuture(
    (actor ? cc.addPhysicalLocation(counterpartyId, userId, datePosted, longitude, latitude)).mapTo[Box[Boolean]]
  )

  override def addCorporateLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean] = getValueFromFuture(
    (actor ? cc.addCorporateLocation(counterpartyId, userId, datePosted, longitude, latitude)).mapTo[Box[Boolean]]
  )

  override def deletePhysicalLocation(counterpartyId: String): Box[Boolean] = getValueFromFuture(
    (actor ? cc.deletePhysicalLocation(counterpartyId)).mapTo[Box[Boolean]]
  )

  override def deleteCorporateLocation(counterpartyId: String): Box[Boolean] = getValueFromFuture(
    (actor ? cc.deleteCorporateLocation(counterpartyId)).mapTo[Box[Boolean]]
  )
  
  override def bulkDeleteAllCounterparties(): Box[Boolean] = getValueFromFuture(
    (actor ? cc.bulkDeleteAllCounterparties()).mapTo[Box[Boolean]]
  )

}
