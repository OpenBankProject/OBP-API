package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiVersion
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class AccountTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getCoreAccountById))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getPrivateAccountByIdFull))

  feature(s"test $ApiEndpoint1") {
    scenario("prepare all the need parameters", VersionOfApi, ApiEndpoint1) {
      Given("We prepare the accounts in V300ServerSetup, just check the response")

      When("We send the request")
      val request = (v4_0_0_Request /"my" / "banks" / testBankId1.value/ "accounts" / testAccountId1.value / "account").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val moderatedCoreAccountJsonV400 = response.body.extract[ModeratedCoreAccountJsonV400]
      moderatedCoreAccountJsonV400.account_attributes.length == 0 should be (true)
      moderatedCoreAccountJsonV400.views_basic.length >= 1 should be (true)

    }
  }
  feature(s"test $ApiEndpoint2") {
    scenario("prepare all the need parameters", VersionOfApi, ApiEndpoint2) {
      Given("We prepare the accounts in V300ServerSetup, just check the response")

      lazy val bankId = randomBankId
      lazy val bankAccount = randomPrivateAccount(bankId)
      lazy val view = randomOwnerViewPermalink(bankId, bankAccount)

      When("We send the request")
      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "account").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val moderatedAccountJSON400 = response.body.extract[ModeratedAccountJSON400]
      moderatedAccountJSON400.account_attributes.length == 0 should be (true)
      moderatedAccountJSON400.views_available.length >= 1 should be (true)
    }
  }
  
}
