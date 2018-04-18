package code.api.UKOpenBanking.v2_0_0

import code.api.util.APIUtil.OAuth._
import code.setup.{APIResponse, DefaultUsers}
import org.scalatest.Tag

//TODO just a quick tests, when the endpoints are clear, we need more tests here.
class UKOpenBankingV200Tests extends UKOpenBankingV200ServerSetup with DefaultUsers {
  
  object UKOpenBankingV200 extends Tag("UKOpenBankingV200")
  
  feature("test the UKOpenBankingV200 Read Account List") 
  {
    scenario("Successful Case", UKOpenBankingV200) 
    {
      val requestGetAll = (UKOpenBankingV200Request / "accounts" ).GET <@(user1)
      val response: APIResponse = makeGetRequest(requestGetAll)
  
      Then("We should get a 200 ")
      response.code should equal(200)
      println(response)
    }
  }
  
//  feature("test the UKOpenBankingV200 Read Balance") 
//  {
//    scenario("Successful Case", UKOpenBankingV200) 
//    {
//      val requestGetAll = (UKOpenBankingV200Request / "accounts"/ testAccountId1.value /"balances" ).GET <@(user1)
//      val response = makeGetRequest(requestGetAll)
//
//      Then("We should get a 200 ")
//      response.code should equal(200)
//      println(response)
//    }
//  }
  
  feature("test the UKOpenBankingV200 Read Account Transactions") 
  {
    scenario("Successful Case", UKOpenBankingV200)
    {
      val requestGetAll = (UKOpenBankingV200Request / "accounts"/ testAccountId1.value /"transactions" ).GET <@(user1)
      val response = makeGetRequest(requestGetAll)
      
      Then("We should get a 200 ")
      response.code should equal(200)
      println(response)
    }
  }
    
}