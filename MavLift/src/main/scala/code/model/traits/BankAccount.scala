package code.model.traits
import scala.math.BigDecimal
import java.util.Date
import net.liftweb.common.Box
import code.model.dataAccess.LocalStorage
import net.liftweb.common.{Full, Empty}
import code.model.dataAccess.Account
import code.model.dataAccess.OBPEnvelope.OBPQueryParam
import code.model.dataAccess.OBPUser
import net.liftweb.json.JObject
import net.liftweb.json.JsonDSL._
import net.liftweb.http.LiftResponse
import net.liftweb.http.JsonResponse

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
  
  def bankPermalink : String
  
  def permalink : String
  
  def number: String
  
  def nationalIdentifier : String
  
  def swift_bic : Option[String]
  
  def iban : Option[String]
  
  def transaction(id: String) : Box[Transaction]
  
  //Is an anonymous view available for this bank account
  def allowAnnoymousAccess : Boolean
  
  def permittedViews(user: Box[User]) : Set[View]
  
  def getModeratedTransactions(moderate: Transaction => ModeratedTransaction): List[ModeratedTransaction]
  
  def getModeratedTransactions(queryParams: OBPQueryParam*)(moderate: Transaction => ModeratedTransaction): List[ModeratedTransaction]
  
  def authorisedAccess(view: View, user: Option[OBPUser]) : Boolean
}

object BankAccount {
  def apply(bankpermalink: String, bankAccountPermalink: String) : Box[BankAccount] = {
    LocalStorage.getAccount(bankpermalink, bankAccountPermalink) match {
      case Full(account) => Full(Account.toBankAccount(account))
      case _ => Empty
    }
  }
  
  def all : List[BankAccount] = {
    LocalStorage.getAllAccounts() map Account.toBankAccount
  }
  
  def publicAccounts : List[BankAccount] = {
    LocalStorage.getAllPublicAccounts() map Account.toBankAccount
  }
}