package code.model.traits

trait AccountOwner {

  def id : String
  
  def name : String
  
  def bankAccounts : Set[BankAccount]
  
}