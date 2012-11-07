package code.model.traits

trait User {
  
  def emailAddress : String
  def userName : String
  def permittedViews(bankAccount: BankAccount) : Set[View]
  def hasMangementAccess(bankAccount: BankAccount)  : Boolean
  
}