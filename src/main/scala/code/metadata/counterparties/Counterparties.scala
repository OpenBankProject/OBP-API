package code.metadata.counterparties

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector
import code.model.{AccountId, BankId, CounterpartyMetadata, Counterparty}

object Counterparties extends SimpleInjector {

  val counterparties = new Inject(buildOne _) {}

  def buildOne: Counterparties = MapperCounterparties

}

trait Counterparties {

  def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, otherParty : Counterparty) : CounterpartyMetadata

  //get all counterparty metadatas for a single OBP account
  def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId : AccountId) : List[CounterpartyMetadata]

  def getMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, counterpartyMetadataId : String) : Box[CounterpartyMetadata]

  def getCounterparty(counterPartyId : String): Box[CounterpartiesFields]

  def getCounterpartyByIban(iBan : String): Box[CounterpartiesFields]

  def addCounterparty(createdByUserId: String, bankId: String, accountId : String, name: String, counterPartyBankId : String, primaryRoutingScheme : String, primaryRoutingAddress : String): Box[CounterpartiesFields]
}

trait CounterpartiesFields {
  def createdByUserId: String
  def thisBankId: String
  def thisAccountId: String
  def otherBankId: String
  def otherAccountId: String
  def otherAccountProvider: String
  def counterPartyId: String
  def accountRoutingScheme: String
  def accountRoutingAddress: String
  def bankRoutingScheme: String
  def bankRoutingAddress: String
  def isBeneficiary : Boolean

}