package code.api.v3_0_0

import code.setup.APIResponse
import code.api.util.APIUtil.OAuth._
import code.api.util.APIUtil.canUseFirehose
import code.api.util.ErrorMessages.{FirehoseViewsNotAllowedOnThisInstance, UserHasMissingRoles}
import code.api.util.ApiRole.CanUseFirehoseAtAnyBank
import net.liftweb.json.JsonAST.compactRender

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
  feature("Assuring that entitlement requirements are checked for account(s) related endpoints") {

    scenario("We try to get firehose accounts without required role " + CanUseFirehoseAtAnyBank){

      When("We have to find it by endpoint getFirehoseAccountsAtOneBank")
      val requestGet = (v3_0Request / "banks" / "BANK_ID" / "firehose" / "accounts" / "views" / "VIEW_ID").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      And("We should get a 403")
      responseGet.code should equal(403)
      compactRender(responseGet.body \ "error").replaceAll("\"", "") should equal(FirehoseViewsNotAllowedOnThisInstance +" or " + UserHasMissingRoles + CanUseFirehoseAtAnyBank  )
    }}


  }
