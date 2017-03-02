package code.metadata.counterparties

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector
import code.model.{AccountId, BankId, Counterparty, CounterpartyMetadata}
import code.remotedata.Remotedata

object Counterparties extends SimpleInjector {

  val counterparties = new Inject(buildOne _) {}

  def buildOne: Counterparties = MapperCounterparties
//  def buildOne: Counterparties = AkkaMapperViews

}

trait Counterparties {

  def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, otherParty : Counterparty) : Box[CounterpartyMetadata]

  //get all counterparty metadatas for a single OBP account
  def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId : AccountId) : List[CounterpartyMetadata]

  def getMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, counterpartyMetadataId : String) : Box[CounterpartyMetadata]

  def getCounterparty(counterPartyId : String): Box[CounterpartyTrait]

  def getCounterpartyByIban(iban : String): Box[CounterpartyTrait]

  def createCounterparty(
                          createdByUserId: String,
                          thisBankId: String,
                          thisAccountId: String,
                          thisViewId: String,
                          name: String,
                          otherBankId: String,
                          otherAccountId: String,
                          otherAccountRoutingScheme: String,
                          otherAccountRoutingAddress: String,
                          otherBankRoutingScheme: String,
                          otherBankRoutingAddress: String,
                          isBeneficiary:Boolean
                        ): Box[CounterpartyTrait]

  def checkCounterpartyAvailable(
                                  name: String,
                                  thisBankId: String,
                                  thisAccountId: String,
                                  thisViewId: String
                                ): Boolean
}

trait CounterpartyTrait {
  def createdByUserId: String
  def name: String
  def thisBankId: String
  def thisAccountId: String
  def thisViewId: String
  def counterpartyId: String
  def otherAccountRoutingScheme: String
  def otherAccountRoutingAddress: Option[String]
  def otherBankRoutingScheme: String
  def otherBankRoutingAddress: Option[String]
  def isBeneficiary : Boolean

}

class RemoteCounterpartiesCaseClasses {
  case class getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty)

  case class getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId)

  case class getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String)

  case class getCounterparty(counterPartyId: String)

  case class getCounterpartyByIban(iban: String)

  case class createCounterparty(
                                 createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String,
                                 name: String, otherBankId: String, otherAccountId: String, otherAccountRoutingScheme: String,
                                 otherAccountRoutingAddress: String, otherBankRoutingScheme: String,
                                 otherBankRoutingAddress: String, isBeneficiary: Boolean)

  case class checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)
}

object RemoteCounterpartiesCaseClasses extends RemoteCounterpartiesCaseClasses