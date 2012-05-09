package code.model

class TesobeAccountOwner(ownedBankAccounts: Set[BankAccount]) extends AccountOwner{

  def id = ""
    
  def name = "TESOBE / Music Pictures Ltd."
    
  def bankAccounts = ownedBankAccounts
  
}

object TesobeBankAccountOwner{
  val account = new TesobeAccountOwner(Set(TesobeBankAccount.bankAccount))
} 