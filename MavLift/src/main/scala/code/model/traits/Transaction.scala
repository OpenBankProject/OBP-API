package code.model.traits
import scala.math.BigDecimal
import java.util.Date

trait Transaction {

  def id : String
 
  var thisAccount : BankAccount

  def otherAccount : OtherBankAccount
  
  def metadata : TransactionMetadata
  
  //E.g. cash withdrawal, electronic payment, etc.
  def transactionType : String
  
  //TODO: Check if BigDecimal is an appropriate data type
  def amount : BigDecimal
  
  //ISO 4217, e.g. EUR, GBP, USD, etc.
  def currency : String
  
  // Bank provided comment
  def label : Option[String]
  
  // The date the transaction was initiated
  def startDate : Date
  
  // The date when the money finished changing hands
  def finishDate : Date
  
  //the new balance for the bank account
  //TODO : Rethink this
  def balance : BigDecimal
  
}