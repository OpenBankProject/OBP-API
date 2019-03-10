package code.api.UKOpenBanking.v2_0_0

import code.api.UKOpenBanking.v2_0_0.JSONFactory_UKOpenBanking_200.{AccountBalancesUKV200, Accounts, TransactionsJsonUKV200}
import code.api.util.APIUtil.OAuth._
import code.setup.{APIResponse, DefaultUsers}
import org.scalatest.Tag

class UKOpenBankingV200Tests extends UKOpenBankingV200ServerSetup with DefaultUsers {
  
  object UKOpenBankingV200 extends Tag("UKOpenBankingV200")
  
  feature("test the UKOpenBankingV200 GET Account List") 
  {
    scenario("Successful Case", UKOpenBankingV200) 
    {
      val requestGetAll = (UKOpenBankingV200Request / "accounts" ).GET <@(user1)
      val response: APIResponse = makeGetRequest(requestGetAll)
  
      Then("We should get a 200 ")
      response.code should equal(200)
      val accounts = response.body.extract[Accounts]
      accounts.Links.Self contains ("open-banking/v2.0/accounts") should be (true)
    }
  }
  
  feature("test the UKOpenBankingV200 Get Account Balances") 
  {
    scenario("Successful Case", UKOpenBankingV200) 
    {
      val requestGetAll = (UKOpenBankingV200Request / "accounts"/ testAccountId1.value /"balances" ).GET <@(user1)
      val response = makeGetRequest(requestGetAll)

      Then("We should get a 200 ")
      response.code should equal(200)
      val accountBalancesUKV200 = response.body.extract[AccountBalancesUKV200]
      accountBalancesUKV200.Links.Self contains("balances")
      
    }
  }
  
  feature("test the UKOpenBankingV200 Get Balances")
  {
    scenario("Successful Case", UKOpenBankingV200)
    {
      val requestGetAll = (UKOpenBankingV200Request / "balances" ).GET <@(user1)
      val response = makeGetRequest(requestGetAll)
      
      Then("We should get a 200 ")
      response.code should equal(200)
      val accountBalancesUKV200 = response.body.extract[AccountBalancesUKV200]
      accountBalancesUKV200.Links.Self contains("balances")
      
    }
  }
  
  feature("test the UKOpenBankingV200 GET Account Transactions") 
  {
    scenario("Successful Case", UKOpenBankingV200)
    {
      val requestGetAll = (UKOpenBankingV200Request / "accounts"/ testAccountId1.value /"transactions" ).GET <@(user1)
      val response = makeGetRequest(requestGetAll)
      
      Then("We should get a 200 ")
      response.code should equal(200)
  
      val transactionsJsonUKV200 = response.body.extract[TransactionsJsonUKV200]
      transactionsJsonUKV200.Links.Self contains("Transactions")
    }
  }
    
}