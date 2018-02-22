package code.metadata.narrative

import code.api.util.APIUtil
import code.model.{AccountId, BankId, TransactionId}
import code.remotedata.RemotedataNarratives
import net.liftweb.util.{Props, SimpleInjector}

object Narrative extends SimpleInjector {

  val narrative = new Inject(buildOne _) {}

  def buildOne: Narrative =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedNarratives
      case true => RemotedataNarratives     // We will use Akka as a middleware
    }

}

/**
 * A narrative is the note the owner of the bank account attaches to a transaction
 */
trait Narrative {

  //TODO: should return an Option
  // Currently: return empty string if there is no narrative
  def getNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)() : String

  //TODO: should return something that lets us know if it saved or failed
  def setNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(narrative: String) : Boolean

  def bulkDeleteNarratives(bankId: BankId, accountId: AccountId): Boolean

}

class RemoteNarrativesCaseClasses {
  case class getNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId)
  case class setNarrative(bankId: BankId, accountId: AccountId, transactionId: TransactionId, narrative: String)
  case class bulkDeleteNarratives(bankId: BankId, accountId: AccountId)
}

object RemoteNarrativesCaseClasses extends RemoteNarrativesCaseClasses