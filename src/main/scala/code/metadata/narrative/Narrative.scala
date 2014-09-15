package code.metadata.narrative

import code.model.{AccountId, BankId}
import net.liftweb.util.SimpleInjector

object Narrative extends SimpleInjector {

  val narrative = new Inject(buildOne _) {}

  def buildOne: Narrative = MongoTransactionNarrative

}

/**
 * A narrative is the note the owner of the bank account attaches to a transaction
 */
trait Narrative {

  //TODO: should return an Option
  // Currently: return empty string if there is no narrative
  def getNarrative(bankId: BankId, accountId: AccountId, transactionId: String)() : String

  //TODO: should return something that lets us know if it saved or failed
  def setNarrative(bankId: BankId, accountId: AccountId, transactionId: String)(narrative: String) : Unit

}
