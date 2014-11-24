package code.metadata.counterparties

import net.liftweb.util.SimpleInjector
import code.model.{AccountId, BankId, OtherBankAccountMetadata, OtherBankAccount}

object Counterparties extends SimpleInjector {

  val counterparties = new Inject(buildOne _) {}

  def buildOne: Counterparties = MapperCounterparties

}

trait Counterparties {

  def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, otherParty : OtherBankAccount) : OtherBankAccountMetadata

  //get all counterparty metadatas for a single OBP account
  def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId : AccountId) : List[OtherBankAccountMetadata]
}