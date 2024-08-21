package code.metadata.counterparties

import java.util.Date

import code.api.util.APIUtil
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.collection.immutable.List

object Counterparties extends SimpleInjector {

  val counterparties = new Inject(buildOne _) {}

  def buildOne: Counterparties = MapperCounterparties

}

trait Counterparties {

  def getOrCreateMetadata(bankId: BankId, accountId : AccountId, counterpartyId:String, counterpartyName:String)  : Box[CounterpartyMetadata]

  //get all counterparty metadatas for a single OBP account
  def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId : AccountId) : List[CounterpartyMetadata]

  def getMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, counterpartyMetadataId : String) : Box[CounterpartyMetadata]

  def deleteMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String): Box[Boolean]
  
  def getCounterparty(counterpartyId : String): Box[CounterpartyTrait]
  
  def deleteCounterparty(counterpartyId : String): Box[Boolean]

  def getCounterpartyByIban(iban : String): Box[CounterpartyTrait]

  def getCounterpartyByIbanAndBankAccountId(iban: String, bankId: BankId, accountId: AccountId): Box[CounterpartyTrait]
  
  def getCounterpartyByRoutings(
    otherBankRoutingScheme: String,
    otherBankRoutingAddress: String,
    otherBranchRoutingScheme: String,
    otherBranchRoutingAddress: String,
    otherAccountRoutingScheme: String,
    otherAccountRoutingAddress: String
  ): Box[CounterpartyTrait]
  
  def getCounterpartyBySecondaryRouting(    
    otherAccountSecondaryRoutingScheme: String,
    otherAccountSecondaryRoutingAddress: String
  ): Box[CounterpartyTrait]

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
                          isBeneficiary:Boolean,
                          otherAccountSecondaryRoutingScheme: String,
                          otherAccountSecondaryRoutingAddress: String,
                          description: String,
                          currency: String,
                          bespoke: List[CounterpartyBespoke]
                        ): Box[CounterpartyTrait]

  def checkCounterpartyExists(
                              name: String,
                              thisBankId: String,
                              thisAccountId: String,
                              thisViewId: String
                            ): Box[CounterpartyTrait]

  def addPublicAlias(counterpartyId: String, alias: String): Box[Boolean]
  def addPrivateAlias(counterpartyId: String, alias: String): Box[Boolean]
  def addURL(counterpartyId: String, url: String): Box[Boolean]
  def addImageURL(counterpartyId : String, imageUrl: String): Box[Boolean]
  def addOpenCorporatesURL(counterpartyId : String, imageUrl: String): Box[Boolean]
  def addMoreInfo(counterpartyId : String, moreInfo: String): Box[Boolean]
  def addPhysicalLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean]
  def addCorporateLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean]
  def deletePhysicalLocation(counterpartyId : String): Box[Boolean]
  def deleteCorporateLocation(counterpartyId : String): Box[Boolean]
  def getCorporateLocation(counterpartyId : String): Box[GeoTag]
  def getPhysicalLocation(counterpartyId : String): Box[GeoTag]
  def getOpenCorporatesURL(counterpartyId : String): Box[String]
  def getImageURL(counterpartyId : String): Box[String]
  def getUrl(counterpartyId : String): Box[String]
  def getMoreInfo(counterpartyId : String): Box[String]
  def getPublicAlias(counterpartyId : String): Box[String]
  def getPrivateAlias(counterpartyId : String): Box[String]
  def bulkDeleteAllCounterparties(): Box[Boolean]
}