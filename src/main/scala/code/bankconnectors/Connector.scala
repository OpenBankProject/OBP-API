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
  def getBank(bankId : BankId) : Box[Bank]

  //gets banks handled by this connector
  def getBanks : List[Bank]

  def getBankAccount(bankId : BankId, accountId : String) : Box[BankAccount]

  def getModeratedOtherBankAccount(bankId: BankId, accountID : String, otherAccountID : String)
  	(moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]) : Box[ModeratedOtherBankAccount]

  def getModeratedOtherBankAccounts(bankId: BankId, accountID : String)
  	(moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]): Box[List[ModeratedOtherBankAccount]]

  def getTransactions(bankId: BankId, accountID: String, queryParams: OBPQueryParam*): Box[List[Transaction]]

  def getTransaction(bankId: BankId, accountID : String, transactionID : String): Box[Transaction]

  def getPhysicalCards(user : User) : Set[PhysicalCard]

  def getPhysicalCardsForBank(bankId: BankId, user : User) : Set[PhysicalCard]
  
  //gets the users who are the legal owners/holders of the account
  def getAccountHolders(bankId: BankId, accountID: String) : Set[User]
  //...
}