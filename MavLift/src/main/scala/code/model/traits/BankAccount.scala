package code.model.traits
import scala.math.BigDecimal
import java.util.Date

trait BankAccount {

  def id : String
  
  var owners : Set[AccountOwner]
  
  //e.g. chequing, savings
  def accountType : String
  
  //TODO: Check if BigDecimal is an appropriate data type
  def balance : BigDecimal
  
  //ISO 4217, e.g. EUR, GBP, USD, etc.
  def currency: String
  
  //Name to display, e.g. TESOBE Postbank Account
  def label : String
  
  def nationalIdentifier : String
  
  def swift_bic : Option[String]
  
  def iban : Option[String]
  
  def transactions : Set[Transaction]
  
  def transactions(from: Date, to: Date) : Set[Transaction]
  
  def transaction(id: String) : Option[Transaction]
  
  //Is an anonymous view available for this bank account
  def allowAnnoymousAccess : Boolean
  
}