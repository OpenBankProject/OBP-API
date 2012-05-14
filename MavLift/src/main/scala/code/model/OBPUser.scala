package code.model

trait OBPUser {
  
  def emailAddress : String
  
  def permittedViews(bankAccount : BankAccount) : Set[View]
  
}