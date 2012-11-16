package code.model.traits

trait User {
  
  def emailAddress : String
  def theFistName : String
  def theLastName : String
  def permittedViews(bankAccount: BankAccount) : Set[View]
  def hasMangementAccess(bankAccount: BankAccount)  : Boolean
  def accountsWithMoreThanAnonAccess : Set[BankAccount]
  
}