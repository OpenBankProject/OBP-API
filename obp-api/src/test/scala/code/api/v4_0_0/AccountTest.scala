package code.api.v4_0_0

import code.api.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.{ApiRole, ApiVersion}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.AmountOfMoneyJsonV121
import net.liftweb.json.Serialization.write
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
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.addAccount))

  lazy val testBankId = testBankId1
  lazy val addAccountJson = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"))
  lazy val addAccountJsonOtherUser = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser2.userId, balance = AmountOfMoneyJsonV121("EUR","0"))
  
  
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

  feature(s"test $ApiEndpoint3 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId.value / "accounts"  ).POST
      val response400 = makePostRequest(request400, write(addAccountJson))
      Then("We should get a 400")
      response400.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint3 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId.value / "accounts" ).POST <@(user1)
      val response400 = makePostRequest(request400, write(addAccountJson))
      Then("We should get a 201")
      response400.code should equal(201)
      val account = response400.body.extract[CreateAccountResponseJsonV310]
      account.account_id should not be empty
      account.product_code should be (addAccountJson.product_code)
      account.`label` should be (addAccountJson.`label`)
      account.balance.amount.toDouble should be (addAccountJson.balance.amount.toDouble)
      account.balance.currency should be (addAccountJson.balance.currency)
      account.branch_id should be (addAccountJson.branch_id)
      account.user_id should be (addAccountJson.user_id)
      account.label should be (addAccountJson.label)
      account.account_routing should be (addAccountJson.account_routing)

      
      Then(s"We call $ApiEndpoint1 to get the account back")
      val request = (v4_0_0_Request /"my" / "banks" / testBankId1.value/ "accounts" / account.account_id / "account").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val moderatedCoreAccountJsonV400 = response.body.extract[ModeratedCoreAccountJsonV400]
      moderatedCoreAccountJsonV400.account_attributes.length == 0 should be (true)
      moderatedCoreAccountJsonV400.views_basic.length >= 1 should be (true)
      
      
      
      
      Then("We make a request v4.0.0 but with other user")
      val requestWithNewAccountId = (v4_0_0_Request / "banks" / testBankId.value / "accounts" ).POST <@(user1)
      val responseWithNoRole = makePostRequest(requestWithNewAccountId, write(addAccountJsonOtherUser))
      Then("We should get a 403 and some error message")
      responseWithNoRole.code should equal(403)
      responseWithNoRole.body.toString contains(s"$UserHasMissingRoles") should be (true)


      Then("We grant the roles and test it again")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      val responseWithOtherUesr = makePostRequest(requestWithNewAccountId, write(addAccountJsonOtherUser))

      val account2 = responseWithOtherUesr.body.extract[CreateAccountResponseJsonV310]
      account2.account_id should not be empty
      account2.product_code should be (addAccountJson.product_code)
      account2.`label` should be (addAccountJson.`label`)
      account2.balance.amount.toDouble should be (addAccountJson.balance.amount.toDouble)
      account2.balance.currency should be (addAccountJson.balance.currency)
      account2.branch_id should be (addAccountJson.branch_id)
      account2.user_id should be (addAccountJsonOtherUser.user_id)
      account2.label should be (addAccountJson.label)
      account2.account_routing should be (addAccountJson.account_routing)
    }
  }
  
}
