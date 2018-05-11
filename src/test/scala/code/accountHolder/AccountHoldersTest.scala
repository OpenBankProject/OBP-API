package code.accountHolder

import code.accountholder.AccountHolders
import code.model._
import code.setup.{DefaultUsers, ServerSetup}

class AccountHoldersTest extends ServerSetup with DefaultUsers{
  
  override def beforeAll() = {
    super.beforeAll()
    AccountHolders.accountHolders.vend.bulkDeleteAllAccountHolders()
  }
  
  override def afterEach() = {
    super.afterEach()
    AccountHolders.accountHolders.vend.bulkDeleteAllAccountHolders()
  }
  
  val bankIdAccountId = BankIdAccountId(BankId("1"),AccountId("2"))
  
  feature("test some important methods in MappedViews ") {
    
    scenario("test - getOrCreateAccountView") {
      
      Given("3 users and 1 bankAccount, and call the method")
      var mapperAccountHolder = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(resourceUser1, bankIdAccountId)
      var mapperAccountHolder2 = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(resourceUser2, bankIdAccountId)
      var mapperAccountHolder3 = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(resourceUser3, bankIdAccountId)
      
      Then("Check the result.")
      var accountholders = AccountHolders.accountHolders.vend.getAccountHolders(bankIdAccountId.bankId, bankIdAccountId.accountId)
  
      val numberOfAccountholders = accountholders.toList.length
      numberOfAccountholders should equal(3)
      
      Then("We call this method again")
      AccountHolders.accountHolders.vend.getOrCreateAccountHolder(resourceUser1, bankIdAccountId)
      
      Then("Check the result, the number should be the same as before ")
      accountholders = AccountHolders.accountHolders.vend.getAccountHolders(bankIdAccountId.bankId, bankIdAccountId.accountId)
      numberOfAccountholders  should equal(3)
      
    }
  
  }
  
  
}
