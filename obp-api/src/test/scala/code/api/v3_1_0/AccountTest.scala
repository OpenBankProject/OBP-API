package code.api.v3_1_0

import code.api.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.updateAccountRequestJsonV310
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.UserNotLoggedIn
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

  lazy val testBankId = randomBankId
  lazy val putCreateAccountJSONV310 = SwaggerDefinitionsJSON.createAccountJSONV220.copy(user_id = resourceUser1.userId)
  
  
  feature("test Update Account") {
    scenario("We will test Update Account Api", ApiEndpoint1, VersionOfApi) {
      Given("The test bank and test account")
      val testBank = testBankId1
      val testAccount = testAccountId1
      val testPutJson = updateAccountRequestJsonV310
      
      Then(s"We call the update api without proper role:  ${ApiRole.canUpdateAccount}")
      val requestPut = (v3_1_0_Request / "management" / "banks" / testBank.value / "accounts" / testAccount.value).PUT <@ (user1)
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


      val requestGet = (v3_1_0_Request /"my"/ "banks" / testBank.value / "accounts" / testAccount.value/"account").PUT <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get 200 and updated account data")
      responseGet.code should equal(200)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].`type` should be (testPutJson.`type`)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].label should be (testPutJson.label)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].account_routings.head.scheme should be (testPutJson.account_routing.scheme)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].account_routings.head.address should be (testPutJson.account_routing.address)
      
    }
  }


  feature("Create Account v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / testBankId / "accounts" / "ACCOUNT_ID" ).PUT
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
      val request310 = (v3_1_0_Request / "banks" / testBankId / "accounts" / "TEST_ACCOUNT_ID" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putCreateAccountJSONV310))
      Then("We should get a 200")
      response310.code should equal(200)
      val customer = response310.body.extract[CreateAccountJSONV220]
      customer.`type` should be (putCreateAccountJSONV310.`type`)
      customer.`label` should be (putCreateAccountJSONV310.`label`)
      customer.balance.amount.toDouble should be (putCreateAccountJSONV310.balance.amount.toDouble)
      customer.balance.currency should be (putCreateAccountJSONV310.balance.currency)
      customer.branch_id should be (putCreateAccountJSONV310.branch_id)
      customer.user_id should be (putCreateAccountJSONV310.user_id)
      customer.label should be (putCreateAccountJSONV310.label)
      customer.account_routing should be (putCreateAccountJSONV310.account_routing)
    }
  }

} 
