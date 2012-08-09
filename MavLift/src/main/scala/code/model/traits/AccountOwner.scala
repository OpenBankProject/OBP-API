package code.model.traits

trait BankAccountOwner {

  def id : String
  
  def name : String
  
  def bankAccounts : Set[BankAccount]
  
}