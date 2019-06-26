package code.api.v3_1_0

import code.api.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.updateAccountRequestJsonV310
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.{ApiRole, ApiVersion}
import code.api.v2_2_0.CreateAccountJSONV220
import code.api.v3_0_0.ModeratedCoreAccountJsonV300
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class AccountTest extends V310ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.updateAccount))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.createAccount))

  lazy val testBankId = testBankId1
  lazy val putCreateAccountJSONV310 = SwaggerDefinitionsJSON.createAccountJSONV220.copy(user_id = resourceUser1.userId)
  lazy val putCreateAccountOtherUserJsonV310 = SwaggerDefinitionsJSON.createAccountJSONV220.copy(user_id = resourceUser2.userId)
  
  
  feature("test Update Account") {
    scenario("We will test Update Account Api", ApiEndpoint1, VersionOfApi) {
      Given("The test bank and test account")
      val testAccount = testAccountId1
      val testPutJson = updateAccountRequestJsonV310
      
      Then(s"We call the update api without proper role:  ${ApiRole.canUpdateAccount}")
      val requestPut = (v3_1_0_Request / "management" / "banks" / testBankId.value / "accounts" / testAccount.value).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(testPutJson))
      And("We should get  403 and the error message missing can CanUpdateAccount role")
      responsePut.code should equal(403)
      responsePut.body.toString contains("OBP-20006: User is missing one or more roles: CanUpdateAccount")


      Then(s"We grant the user ${ApiRole.canUpdateAccount} role")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canUpdateAccount.toString)
      val responsePut2 = makePutRequest(requestPut, write(testPutJson))
      And("We should get 200 and updated account data")
      responsePut2.code should equal(200)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].`type` should be (testPutJson.`type`)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].branch_id should be (testPutJson.branch_id)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].label should be (testPutJson.label)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].account_routing.scheme should be (testPutJson.account_routing.scheme)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].account_routing.address should be (testPutJson.account_routing.address)


      val requestGet = (v3_1_0_Request /"my"/ "banks" / testBankId.value / "accounts" / testAccount.value/"account").PUT <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get 200 and updated account data")
      responseGet.code should equal(200)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].`type` should be (testPutJson.`type`)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].label should be (testPutJson.label)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].account_routings.toString() contains (testPutJson.account_routing.scheme) should be (true)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].account_routings.toString() contains (testPutJson.account_routing.address) should be (true)
      
    }
  }


  feature("Create Account v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / testBankId.value / "accounts" / "ACCOUNT_ID" ).PUT
      val response310 = makePutRequest(request310, write(putCreateAccountJSONV310))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Create Account v3.1.0 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putCreateAccountJSONV310))
      Then("We should get a 201")
      response310.code should equal(201)
      val account = response310.body.extract[CreateAccountJSONV220]
      account.`type` should be (putCreateAccountJSONV310.`type`)
      account.`label` should be (putCreateAccountJSONV310.`label`)
      account.balance.amount.toDouble should be (putCreateAccountJSONV310.balance.amount.toDouble)
      account.balance.currency should be (putCreateAccountJSONV310.balance.currency)
      account.branch_id should be (putCreateAccountJSONV310.branch_id)
      account.user_id should be (putCreateAccountJSONV310.user_id)
      account.label should be (putCreateAccountJSONV310.label)
      account.account_routing should be (putCreateAccountJSONV310.account_routing)

      Then("We make a request v3.1.0 but with other user")
      val request310WithNewAccountId = (v3_1_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID2" ).PUT <@(user1)
      val responseWithNoRole = makePutRequest(request310WithNewAccountId, write(putCreateAccountOtherUserJsonV310))
      Then("We should get a 403 and some error message")
      responseWithNoRole.code should equal(403)
      responseWithNoRole.body.toString contains(s"$UserHasMissingRoles") should be (true)


      Then("We grant the roles and test it again")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      val responseWithOtherUesrV310 = makePutRequest(request310WithNewAccountId, write(putCreateAccountOtherUserJsonV310))
      
      val account2 = responseWithOtherUesrV310.body.extract[CreateAccountJsonV310]
      account2.`type` should be (putCreateAccountOtherUserJsonV310.`type`)
      account2.`label` should be (putCreateAccountOtherUserJsonV310.`label`)
      account2.balance.amount.toDouble should be (putCreateAccountOtherUserJsonV310.balance.amount.toDouble)
      account2.balance.currency should be (putCreateAccountOtherUserJsonV310.balance.currency)
      account2.branch_id should be (putCreateAccountOtherUserJsonV310.branch_id)
      account2.user_id should be (putCreateAccountOtherUserJsonV310.user_id)
      account2.label should be (putCreateAccountOtherUserJsonV310.label)
      account2.account_routing should be (putCreateAccountOtherUserJsonV310.account_routing)

    }
    
  }

} 
