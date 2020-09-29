package code.metadata.counterparties

import java.util.Date

import code.api.util.APIUtil
import code.model._
import code.remotedata.RemotedataCounterparties
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.collection.immutable.List

object Counterparties extends SimpleInjector {

  val counterparties = new Inject(buildOne _) {}

  def buildOne: Counterparties =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MapperCounterparties
      case true => RemotedataCounterparties     // We will use Akka as a middleware
    }

}

trait Counterparties {

  def getOrCreateMetadata(bankId: BankId, accountId : AccountId, counterpartyId:String, counterpartyName:String)  : Box[CounterpartyMetadata]

  //get all counterparty metadatas for a single OBP account
  def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId : AccountId) : List[CounterpartyMetadata]

  def getMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, counterpartyMetadataId : String) : Box[CounterpartyMetadata]

  def getCounterparty(counterpartyId : String): Box[CounterpartyTrait]

  def getCounterpartyByIban(iban : String): Box[CounterpartyTrait]

  def getCounterpartyByIbanAndBankAccountId(iban: String, bankId: BankId, accountId: AccountId): Box[CounterpartyTrait]

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

class RemotedataCounterpartiesCaseClasses {
  case class getOrCreateMetadata(bankId: BankId, accountId : AccountId, counterpartyId:String, counterpartyName:String)
  
  case class getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId)

  case class getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String)

  case class getCounterparty(counterpartyId: String)

  case class getCounterpartyByIban(iban: String)

  case class getCounterpartyByIbanAndBankAccountId(iban: String, bankId: BankId, accountId: AccountId)

  case class getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId)

  case class createCounterparty(
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
    bespoke: List[CounterpartyBespoke]
  )

  case class checkCounterpartyExists(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)

  case class addPublicAlias(counterpartyId: String, alias: String)

  case class addPrivateAlias(counterpartyId: String, alias: String)

  case class addURL(counterpartyId: String, url: String)

  case class addImageURL(counterpartyId : String, imageUrl: String)

  case class addOpenCorporatesURL(counterpartyId : String, imageUrl: String)

  case class addMoreInfo(counterpartyId : String, moreInfo: String)

  case class addPhysicalLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double)

  case class addCorporateLocation(counterpartyId : String, userId: UserPrimaryKey, datePosted : Date, longitude : Double, latitude : Double)

  case class deletePhysicalLocation(counterpartyId : String)

  case class deleteCorporateLocation(counterpartyId : String)

  case class getCorporateLocation(counterpartyId : String)

  case class getPhysicalLocation(counterpartyId : String)

  case class getOpenCorporatesURL(counterpartyId : String)

  case class getImageURL(counterpartyId : String)

  case class getUrl(counterpartyId : String)

  case class getMoreInfo(counterpartyId : String)

  case class getPublicAlias(counterpartyId : String)

  case class getPrivateAlias(counterpartyId : String)
  
  case class bulkDeleteAllCounterparties()
}

object RemotedataCounterpartiesCaseClasses extends RemotedataCounterpartiesCaseClasses
