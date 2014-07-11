package code.metadata.counterparties

import net.liftweb.util.SimpleInjector
import code.model.{OtherBankAccountMetadata, OtherBankAccount}

object Counterparties extends SimpleInjector {

  val counterparties = new Inject(buildOne _) {}

  def buildOne: Counterparties = MongoCounterparties

}

trait Counterparties {

  def getOrCreateMetadata(originalPartyBankId: String, originalPartyAccountId : String, otherParty : OtherBankAccount) : OtherBankAccountMetadata

}