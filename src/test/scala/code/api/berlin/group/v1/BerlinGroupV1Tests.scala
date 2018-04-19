package code.api.berlin.group.v1

import code.api.berlin.group.v1.JSONFactory_BERLIN_GROUP_1.{AccountBalances, CoreAccountsJsonV1, TransactionsJsonV1}
import code.api.util.APIUtil.OAuth._
import code.setup.{APIResponse, DefaultUsers}
import org.scalatest.Tag

class BerlinGroupV1Tests extends BerlinGroupV1ServerSetup with DefaultUsers {
  
  object BerlinGroup extends Tag("berlinGroup")
  
  feature("test the BG Read Account List") 
  {
    scenario("Successful Case", BerlinGroup) 
    {
      val requestGetAll = (BerlinGroup_V1Request / "accounts" ).GET <@(user1)
      val response: APIResponse = makeGetRequest(requestGetAll)
  
      Then("We should get a 200 ")
      response.code should equal(200)
//      TODO because of the links is a trait, we can not extract automatically here.
//      logger.info(response.body)
//      val coreAccountsJsonV1 = response.body.extract[CoreAccountsJsonV1]
    }
  }
  
  feature("test the BG Read Balance") 
  {
    scenario("Successful Case", BerlinGroup) 
    {
      val requestGetAll = (BerlinGroup_V1Request / "accounts"/ testAccountId1.value /"balances" ).GET <@(user1)
      val response = makeGetRequest(requestGetAll)
      
      Then("We should get a 200 ")
      response.code should equal(200)
      val accountBalances = response.body.extract[AccountBalances]
    }
  }
  
  feature("test the BG Read Account Transactions") 
  {
    scenario("Successful Case", BerlinGroup)
    {
      val requestGetAll = (BerlinGroup_V1Request / "accounts"/ testAccountId1.value /"transactions" ).GET <@(user1)
      val response = makeGetRequest(requestGetAll)
      
      Then("We should get a 200 ")
      response.code should equal(200)
      val transactionsJsonV1 = response.body.extract[TransactionsJsonV1]
    }
  }
    
}