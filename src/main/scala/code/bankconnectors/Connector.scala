package code.bankconnectors

import net.liftweb.common.Box
import code.model._
import net.liftweb.util.SimpleInjector
import code.model.User
import code.model.ModeratedOtherBankAccount
import code.model.OtherBankAccount
import code.model.ModeratedTransaction
import code.model.Transaction
import java.util.Date
import scala.Some

object Connector  extends SimpleInjector {

  val connector = new Inject(buildOne _) {}

  def buildOne: Connector = LocalConnector

}

class OBPQueryParam
trait OBPOrder { def orderValue : Int }
object OBPOrder {
  def apply(s: Option[String]): OBPOrder = s match {
    case Some("asc") => OBPAscending
    case Some("ASC")=> OBPAscending
    case _ => OBPDescending
  }
}
object OBPAscending extends OBPOrder { def orderValue = 1 }
object OBPDescending extends OBPOrder { def orderValue = -1}
case class OBPLimit(value: Int) extends OBPQueryParam
case class OBPOffset(value: Int) extends OBPQueryParam
case class OBPFromDate(value: Date) extends OBPQueryParam
case class OBPToDate(value: Date) extends OBPQueryParam
case class OBPOrdering(field: Option[String], order: OBPOrder) extends OBPQueryParam

trait Connector {

  //gets a particular bank handled by this connector
  def getBank(permalink : String) : Box[Bank]

  //gets banks handled by this connector
  def getBanks : List[Bank]

  def getBankAccount(bankPermalink : String, accountId : String) : Box[BankAccount]

  def getModeratedOtherBankAccount(bankID: String, accountID : String, otherAccountID : String)
  	(moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]) : Box[ModeratedOtherBankAccount]

  def getModeratedOtherBankAccounts(bankID: String, accountID : String)
  	(moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]): Box[List[ModeratedOtherBankAccount]]

  def getModeratedTransactions(bankID: String, accountID: String, queryParams: OBPQueryParam*)
    (moderate: Transaction => ModeratedTransaction): Box[List[ModeratedTransaction]]

  def getModeratedTransaction(id : String, bankID : String, accountID : String)
    (moderate: Transaction => ModeratedTransaction) : Box[ModeratedTransaction]

  def getPhysicalCards(user : User) : Set[PhysicalCard]

  def getPhysicalCardsForBank(bankID : String, user : User) : Set[PhysicalCard]
  
  //gets the users who are the legal owners/holders of the account
  def getAccountHolders(bankID: String, accountID: String) : Set[User]
  //...
}