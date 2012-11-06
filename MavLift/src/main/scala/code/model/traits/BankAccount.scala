package code.model.traits
import scala.math.BigDecimal
import java.util.Date
import net.liftweb.common.Box
import code.model.dataAccess.LocalStorage
import net.liftweb.common.{Full, Empty}
import code.model.dataAccess.Account
import code.model.dataAccess.OBPEnvelope.OBPQueryParam

trait BankAccount {

  def id : String
  
  var owners : Set[AccountOwner]
  
  //e.g. chequing, savings
  def accountType : String
  
  //TODO: Check if BigDecimal is an appropriate data type
  def balance : Option[BigDecimal]
  
  //ISO 4217, e.g. EUR, GBP, USD, etc.
  def currency: String
  
  //Name to display, e.g. TESOBE Postbank Account
  def label : String
  
  def bankName : String
  
  def number: String
  
  def nationalIdentifier : String
  
  def swift_bic : Option[String]
  
  def iban : Option[String]
  
  def transaction(id: String) : Option[Transaction]
  
  //Is an anonymous view available for this bank account
  def allowAnnoymousAccess : Boolean
  
  def getModeratedTransactions(moderate: Transaction => ModeratedTransaction): List[ModeratedTransaction]
  
  def getModeratedTransactions(queryParams: OBPQueryParam*)(moderate: Transaction => ModeratedTransaction): List[ModeratedTransaction]
  
}

object BankAccount {
  def apply(bankpermalink: String, bankAccountPermalink: String) : Box[BankAccount] = {
    LocalStorage.getAccount(bankpermalink, bankAccountPermalink) match {
      case Full(account) => Full(Account.toBankAccount(account))
      case _ => Empty
    }
  }
}