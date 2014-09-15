package code.metadata.counterparties

import net.liftweb.util.SimpleInjector
import code.model.{AccountId, BankId, OtherBankAccountMetadata, OtherBankAccount}

object Counterparties extends SimpleInjector {

  val counterparties = new Inject(buildOne _) {}

  def buildOne: Counterparties = MongoCounterparties

}

trait Counterparties {

  def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId : AccountId, otherParty : OtherBankAccount) : OtherBankAccountMetadata

}