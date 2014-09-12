package code.metadata.counterparties

import net.liftweb.util.SimpleInjector
import code.model.{BankId, OtherBankAccountMetadata, OtherBankAccount}

object Counterparties extends SimpleInjector {

  val counterparties = new Inject(buildOne _) {}

  def buildOne: Counterparties = MongoCounterparties

}

trait Counterparties {

  def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId : String, otherParty : OtherBankAccount) : OtherBankAccountMetadata

}