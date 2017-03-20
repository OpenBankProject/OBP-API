package code.metadata.counterparties

import java.util.Date

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector
import code.model._
import code.remotedata.RemotedataCounterparties

object Counterparties extends SimpleInjector {

  val counterparties = new Inject(buildOne _) {}

//  def buildOne: Counterparties = MapperCounterparties
  def buildOne: Counterparties = RemotedataCounterparties

}

trait Counterparties {

  def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, otherParty : Counterparty) : Box[CounterpartyMetadata]

  //get all counterparty metadatas for a single OBP account
  def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId : AccountId) : List[CounterpartyMetadata]

  def getMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, counterpartyMetadataId : String) : Box[CounterpartyMetadata]

  def getCounterparty(counterPartyId : String): Box[CounterpartyTrait]

  def getCounterpartyByIban(iban : String): Box[CounterpartyTrait]

  def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId): Box[List[CounterpartyTrait]]

  def createCounterparty(
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
                          isBeneficiary:Boolean
                        ): Box[CounterpartyTrait]

  def checkCounterpartyAvailable(
                                  name: String,
                                  thisBankId: String,
                                  thisAccountId: String,
                                  thisViewId: String
                                ): Boolean

  def addPublicAlias(counterPartyId: String, alias: String): Box[Boolean]
  def addPrivateAlias(counterPartyId: String, alias: String): Box[Boolean]
  def addURL(counterPartyId: String, url: String): Box[Boolean]
  def addImageURL(counterPartyId : String, imageUrl: String): Box[Boolean]
  def addOpenCorporatesURL(counterPartyId : String, imageUrl: String): Box[Boolean]
  def addMoreInfo(counterPartyId : String, moreInfo: String): Box[Boolean]
  def addPhysicalLocation(counterPartyId : String, userId: UserId, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean]
  def addCorporateLocation(counterPartyId : String, userId: UserId, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean]
  def deletePhysicalLocation(counterPartyId : String): Box[Boolean]
  def deleteCorporateLocation(counterPartyId : String): Box[Boolean]
  def getCorporateLocation(counterPartyId : String): Box[GeoTag]
  def getPhysicalLocation(counterPartyId : String): Box[GeoTag]
  def getOpenCorporatesURL(counterPartyId : String): Box[String]
  def getImageURL(counterPartyId : String): Box[String]
  def getUrl(counterPartyId : String): Box[String]
  def getMoreInfo(counterPartyId : String): Box[String]
  def getPublicAlias(counterPartyId : String): Box[String]
  def getPrivateAlias(counterPartyId : String): Box[String]
}

trait CounterpartyTrait {
  def createdByUserId: String
  def name: String
  def thisBankId: String
  def thisAccountId: String
  def thisViewId: String
  def counterpartyId: String
  def otherAccountRoutingScheme: String
  def otherAccountRoutingAddress: String
  def otherBankRoutingScheme: String
  def otherBankRoutingAddress: String
  def otherBranchRoutingScheme: String
  def otherBranchRoutingAddress: String
  def isBeneficiary : Boolean

}

class RemotedataCounterpartiesCaseClasses {
  case class getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty)

  case class getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId)

  case class getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String)

  case class getCounterparty(counterPartyId: String)

  case class getCounterpartyByIban(iban: String)

  case class getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId)

  case class createCounterparty(
                                 createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String,
                                 name: String,
                                 otherAccountRoutingScheme: String,
                                 otherAccountRoutingAddress: String, otherBankRoutingScheme: String,
                                 otherBranchRoutingScheme: String, otherBranchRoutingAddress: String,
                                 otherBankRoutingAddress: String, isBeneficiary: Boolean)

  case class checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)

  case class addPublicAlias(counterPartyId: String, alias: String)

  case class addPrivateAlias(counterPartyId: String, alias: String)

  case class addURL(counterPartyId: String, url: String)

  case class addImageURL(counterPartyId : String, imageUrl: String)

  case class addOpenCorporatesURL(counterPartyId : String, imageUrl: String)

  case class addMoreInfo(counterPartyId : String, moreInfo: String)

  case class addPhysicalLocation(counterPartyId : String, userId: UserId, datePosted : Date, longitude : Double, latitude : Double)

  case class addCorporateLocation(counterPartyId : String, userId: UserId, datePosted : Date, longitude : Double, latitude : Double)

  case class deletePhysicalLocation(counterPartyId : String)

  case class deleteCorporateLocation(counterPartyId : String)

  case class getCorporateLocation(counterPartyId : String)

  case class getPhysicalLocation(counterPartyId : String)

  case class getOpenCorporatesURL(counterPartyId : String)

  case class getImageURL(counterPartyId : String)

  case class getUrl(counterPartyId : String)

  case class getMoreInfo(counterPartyId : String)

  case class getPublicAlias(counterPartyId : String)

  case class getPrivateAlias(counterPartyId : String)
}

object RemotedataCounterpartiesCaseClasses extends RemotedataCounterpartiesCaseClasses
