package code.model
import scala.math.BigDecimal
import java.util.Date

trait Transaction {

  def id : String
 
  def account : BankAccount
  
  def otherParty : NonObpAccount
  
  //E.g. cash withdrawal, electronic payment, etc.
  def transactionType : String
  
  //TODO: Check if BigDecimal is an appropriate data type
  def amount : BigDecimal
  
  //ISO 4217, e.g. EUR, GBP, USD, etc.
  def currency : String
  
  // Bank/ provided comment
  def label : Option[String]
  
  // Owner provided comment, done in OBP
  def ownerComment : Option[String]
  
  def comments : List[Comment]
  
  // The date the transaction was initiated
  def startDate : Date
  
  // The date when the money finished changing hands
  def finishDate : Date
  
  def addComment(comment: Comment)
  
  //the new balance for the bank account
  //TODO : Rethink this
  def balance : BigDecimal
  
}