package code.model

trait OBPUser {
  
  def emailAddress : String
  def userName : String
  def permittedViews(bankAccount : BankAccount) : Set[View]
  
}