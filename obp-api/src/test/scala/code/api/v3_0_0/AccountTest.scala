package code.api.v3_0_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanUseAccountFirehoseAtAnyBank
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages.{AccountFirehoseNotAllowedOnThisInstance, UserHasMissingRoles}
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.setup.APIResponse
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class AccountTest extends V300ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_0_0.corePrivateAccountsAllBanks))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_0_0.getFirehoseAccountsAtOneBank))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_0_0.getCoreAccountById))
  
  // Need test endpoints -- /my/accounts - corePrivateAccountsAllBanks - V300
  def getCorePrivateAccountsAllBanksV300(consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = v3_0Request / "my" / "accounts"  <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  feature("/my/accounts - corePrivateAccountsAllBanks -V300") {
    scenario("prepare all the need parameters", VersionOfApi, ApiEndpoint1) {
      Given("We prepare the accounts in V300ServerSetup, just check the response")
      
      When("We send the request")
      val httpResponse = getCorePrivateAccountsAllBanksV300(user1)

      Then("We should get a 200 and check the response body")
      httpResponse.code should equal(200)
      httpResponse.body.extract[CoreAccountsJsonV300]

    }
  
  }
  feature("Assuring that entitlement requirements are checked for account(s) related endpoints") {

    scenario("We try to get firehose accounts without required role " + CanUseAccountFirehoseAtAnyBank, VersionOfApi, ApiEndpoint2){

      When("We have to find it by endpoint getFirehoseAccountsAtOneBank")
      val requestGet = (v3_0Request / "banks" / "BANK_ID" / "firehose" / "accounts" / "views" / "VIEW_ID").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      And("We should get a 403")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should equal(AccountFirehoseNotAllowedOnThisInstance +" or " + UserHasMissingRoles + CanUseAccountFirehoseAtAnyBank  )
    }}


  feature(s"test $ApiEndpoint3") {
    scenario("prepare all the need parameters", VersionOfApi, ApiEndpoint1) {
      Given("We prepare the accounts in V300ServerSetup, just check the response")

      When("We send the request")
      val requestGet = (v3_0Request /"my" / "banks" / testBankId1.value/ "accounts" / testAccountId1.value / "account").GET <@ (user1)
      val httpResponse = makeGetRequest(requestGet)

      Then("We should get a 200 and check the response body")
      httpResponse.code should equal(200)
      val newModeratedCoreAccountJsonV300 = httpResponse.body.extract[NewModeratedCoreAccountJsonV300]
      newModeratedCoreAccountJsonV300.views_basic.length >= 1 should be (true)

    }
  }
  
  }
