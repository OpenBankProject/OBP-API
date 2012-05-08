package code.model

trait AccountOwner {

  def id : String
  
  def name : String
  
  def bankAccounts : Set[BankAccount]
  
}