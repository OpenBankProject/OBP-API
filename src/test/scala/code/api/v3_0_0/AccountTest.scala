package code.api.v3_0_0

import code.setup.APIResponse
import code.api.util.APIUtil.OAuth._

class AccountTest extends V300ServerSetup {
  
  // Need test endpoints -- /my/accounts - corePrivateAccountsAllBanks - V300
  def getCorePrivateAccountsAllBanksV300(consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = v3_0Request / "my" / "accounts"  <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  feature("/my/accounts - corePrivateAccountsAllBanks -V300") {
    scenario("prepare all the need parameters") {
      Given("We prepare the accounts in V300ServerSetup, just check the response")
      
      When("We send the request")
      val httpResponse = getCorePrivateAccountsAllBanksV300(user1)

      Then("We should get a 200 and check the response body")
      httpResponse.code should equal(200)
      httpResponse.body.extract[CoreAccountsJsonV300]

    }
  
  }

}
