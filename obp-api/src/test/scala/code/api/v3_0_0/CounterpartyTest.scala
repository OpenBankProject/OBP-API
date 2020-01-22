package code.api.v3_0_0

import code.api.Constant._
import com.openbankproject.commons.util.ApiVersion
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class CounterpartyTest extends V300ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_0_0.getOtherAccountsForBankAccount))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_0_0.getOtherAccountByIdForBankAccount))
  
  feature("Get Other Accounts of one Account.and Get Other Account by Id. - V300") {
    scenario("prepare all the need parameters", VersionOfApi, ApiEndpoint1, ApiEndpoint2) {
      Given("We prepare all the parameters, just check the response")
      val bankId = randomBankId
      val accountId = randomPrivateAccountId(bankId)
      val viewId = CUSTOM_OWNER_VIEW_ID
      val loginedUser = user1
      
      When("we call the `Get Other Accounts of one Account.`")
      val httpResponseAccounts = getOtherAccountsForBankAccount(bankId,accountId,viewId,user1)

      Then("We should get a 200 and check the response body")
      httpResponseAccounts.code should equal(200)
      val otherAccountsJson = httpResponseAccounts.body.extract[OtherAccountsJsonV300]
      
      
      Then("We random get a otherAccountId ")
      val otherAccountId=otherAccountsJson.other_accounts.head.id
      
      Then("we call the `Get Other Account by Id.`")
      val httpResponseAccount = getOtherAccountByIdForBankAccount(bankId,accountId,viewId,otherAccountId,user1)
      
      
      Then("We should get a 200 and check the response body")
      httpResponseAccount.code should equal(200)
      httpResponseAccount.body.extract[OtherAccountJsonV300]
    }
  }

}
