package code.api.berlin.group.v1

import code.api.util.APIUtil.OAuth._
import code.setup.{APIResponse, DefaultUsers}
import org.scalatest.Tag

//TODO just a quick tests, when the endpoints are clear, we need more tests here.
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
    }
  }
    
}