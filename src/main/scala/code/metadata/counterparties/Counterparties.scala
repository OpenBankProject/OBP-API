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
}