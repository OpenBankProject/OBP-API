package code.api.v5_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.v5_0_0.OBPAPI5_0_0.Implementations5_0_0
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag
import code.api.util.APIUtil.OAuth._
import code.api.util.{ApiRole}
import code.api.util.ErrorMessages.UserNotLoggedIn
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import net.liftweb.json.Serialization.write

class CustomerAccountLinkTest extends V500ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_0_0.createCustomerAccountLink))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_0_0.getCustomerAccountLinkById))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_0_0.updateCustomerAccountLinkById))
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_0_0.getCustomerAccountLinksByCustomerId))
  object ApiEndpoint5 extends Tag(nameOf(Implementations5_0_0.getCustomerAccountLinksByAccountId))
  object ApiEndpoint6 extends Tag(nameOf(Implementations5_0_0.deleteCustomerAccountLinkById))




  feature(s"customer account link $VersionOfApi - Error cases ") {

    lazy val testBankId = randomBankId
    lazy val testAccountId = testAccountId1
    lazy val createCustomerAccountLinkJson = SwaggerDefinitionsJSON.createCustomerAccountLinkJson
    lazy val updateCustomerAccountLinkJson = SwaggerDefinitionsJSON.updateCustomerAccountLinkJson
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      
      When(s"We make a request $VersionOfApi")
      val request500 = (v5_0_0_Request / "banks" / testBankId / "customer_account_links" ).POST
      val response500 = makePostRequest(request500, write(createCustomerAccountLinkJson))
      Then("We should get a 401")
      response500.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response500.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }    
//    scenario("We will call the endpoint without user credentials", ApiEndpoint1,ApiEndpoint2,ApiEndpoint3,ApiEndpoint4,ApiEndpoint5,ApiEndpoint6, VersionOfApi) {
//      When(s"We make a request $VersionOfApi")
//      val request500 = (v5_0_0_Request / "banks" / testBankId / "accounts" / "ACCOUNT_ID" ).PUT
//      val response500 = makePutRequest(request500, write(putCreateAccountJSONV500))
//      Then("We should get a 401")
//      response500.code should equal(401)
//      And("error should be " + UserNotLoggedIn)
//      response500.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
//    }
  }
  

  feature(s"Create Account $VersionOfApi - Success access") {
    
    
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, ApiEndpoint6, VersionOfApi) {
      When(s"We make a request $VersionOfApi $ApiEndpoint1")
      
      lazy val testBankId = randomBankId
      lazy val testAccountId = testAccountId1
      val customerId = createAndGetCustomerIdViaEndpoint(testBankId, user1)
      lazy val createCustomerAccountLinkJson = SwaggerDefinitionsJSON.createCustomerAccountLinkJson.copy(customer_id = customerId, account_id= testAccountId.value)
      lazy val updateCustomerAccountLinkJson = SwaggerDefinitionsJSON.updateCustomerAccountLinkJson.copy(relationship_type ="test")
      
      
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canCreateCustomerAccountLink.toString())
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canUpdateCustomerAccountLink.toString())
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canGetCustomerAccountLink.toString())
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canGetCustomerAccountLinks.toString())
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canDeleteCustomerAccountLink.toString())
      
      val requestApiEndpoint1 =  (v5_0_0_Request / "banks" / testBankId / "customer_account_links" ).POST <@(user1)
      val responseApiEndpoint1 = makePostRequest(requestApiEndpoint1, write(createCustomerAccountLinkJson))
      Then("We should get a 201")
      responseApiEndpoint1.code should equal(201)

      val customerAccountLinkJson1 = responseApiEndpoint1.body.extract[CustomerAccountLinkJson]
      val customerAccountLinkId1 = customerAccountLinkJson1.customer_account_link_id
      customerAccountLinkId1.nonEmpty should be (true)

      Then(s"We make a request $VersionOfApi $ApiEndpoint2")
      val requestApiEndpoint2 =  (v5_0_0_Request / "banks" / testBankId / "customer_account_links"/customerAccountLinkId1 ).GET <@(user1)
      val responseApiEndpoint2 = makeGetRequest(requestApiEndpoint2)
      Then("We should get a 200")
      responseApiEndpoint2.code should equal(200)
      val customerAccountLinkJson2 = responseApiEndpoint2.body.extract[CustomerAccountLinkJson]
      val customerAccountLinkId2 = customerAccountLinkJson2.customer_account_link_id
      customerAccountLinkId2 should be (customerAccountLinkId1)
      
      Then(s"We make a request $VersionOfApi $ApiEndpoint3")
      val requestApiEndpoint3 =  (v5_0_0_Request / "banks" / testBankId / "customer_account_links"/customerAccountLinkId1 ).PUT <@(user1)
      val responseApiEndpoint3 = makePutRequest(requestApiEndpoint3, write(updateCustomerAccountLinkJson))
      Then("We should get a 200")
      responseApiEndpoint3.code should equal(200)
      val customerAccountLinkJson3 = responseApiEndpoint3.body.extract[CustomerAccountLinkJson]
      val customerAccountLinkId3 = customerAccountLinkJson3.customer_account_link_id
      customerAccountLinkId3 should be (customerAccountLinkId1)
      
      Then(s"We make a request $VersionOfApi $ApiEndpoint4")
      val requestApiEndpoint4 =  (v5_0_0_Request / "banks" / testBankId /"customers"/customerId / "customer_account_links" ).GET <@(user1)
      val responseApiEndpoint4 = makeGetRequest(requestApiEndpoint4)
      Then("We should get a 200")
      responseApiEndpoint4.code should equal(200)
      val customerAccountLinkJson4 = responseApiEndpoint4.body.extract[CustomerAccountLinksJson]
      val customerAccountLinkId4 = customerAccountLinkJson4.links.head.customer_account_link_id
      customerAccountLinkId4 should be (customerAccountLinkId1)
      
      
      Then(s"We make a request $VersionOfApi $ApiEndpoint5")
      val requestApiEndpoint5 =  (v5_0_0_Request / "banks" / testBankId /"accounts"/testAccountId.value / "customer_account_links").GET <@(user1)
      val responseApiEndpoint5 = makeGetRequest(requestApiEndpoint5)
      Then("We should get a 200")
      responseApiEndpoint5.code should equal(200)
      val customerAccountLinkJson5 = responseApiEndpoint5.body.extract[CustomerAccountLinksJson]
      val customerAccountLinkId5 = customerAccountLinkJson5.links.head.customer_account_link_id
      customerAccountLinkId5 should be (customerAccountLinkId1)



      Then(s"We make a request $VersionOfApi $ApiEndpoint6")
      val requestApiEndpoint6 =  (v5_0_0_Request / "banks" / testBankId / "customer_account_links"/customerAccountLinkId1).DELETE <@(user1)
      val responseApiEndpoint6 = makeDeleteRequest(requestApiEndpoint6)
      Then("We should get a 204")
      responseApiEndpoint6.code should equal(204)
      Then(s"We call $ApiEndpoint5 should return empty list")
      val responseApiEndpoint5AfterDelete = makeGetRequest(requestApiEndpoint5)
      Then("We should get a 200")
      responseApiEndpoint5AfterDelete.code should equal(200)
      val customerAccountLinkJson5AfterDelete = responseApiEndpoint5AfterDelete.body.extract[CustomerAccountLinksJson]
      customerAccountLinkJson5AfterDelete.links.length should be (0)
      
//      //We need to waite some time for the account creation, because we introduce `AuthUser.refreshUser(user, callContext)`
//      //It may not finished when we call the get accounts directly.
//      TimeUnit.SECONDS.sleep(2)
//
//      Then(s"we call $ApiEndpoint4 to get the account back")
//      val requestApiEndpoint4 = (v5_0_0_Request / "my" / "accounts" ).PUT <@(user1)
//      val responseApiEndpoint4 = makeGetRequest(requestApiEndpoint4)
//
//
//
//      responseApiEndpoint4.code should equal(200)
//      val accounts = responseApiEndpoint4.body.extract[CoreAccountsJsonV300].accounts
//      accounts.map(_.id).toList.toString() contains(account.account_id) should be (true)
//
//      Then(s"we call $ApiEndpoint5 to get the account back")
//      val requestApiEndpoint5 = (v5_0_0_Request /"banks" / testBankId / "accounts").GET <@ (user1)
//      val responseApiEndpoint5 = makeGetRequest(requestApiEndpoint5)
//
//      Then("We should get a 200")
//      responseApiEndpoint5.code should equal(200)
//      responseApiEndpoint5.body.extract[List[BasicAccountJSON]].toList.toString() contains(account.account_id) should be (true)
//
//
//      val requestGetApiEndpoint3 = (v5_0_0_Request / "banks" / testBankId / "balances").GET <@ (user1)
//      val responseGetApiEndpoint3 = makeGetRequest(requestGetApiEndpoint3)
//      responseGetApiEndpoint3.code should equal(200)
//      responseGetApiEndpoint3.body.extract[AccountsBalancesJsonV400].accounts.map(_.account_id) contains(account.account_id) should be (true)
//
//
//      Then(s"We make a request $VersionOfApi but with other user")
//      val request500WithNewAccountId = (v5_0_0_Request / "banks" / testBankId / "accounts" / "TEST_ACCOUNT_ID2" ).PUT <@(user2)
//      val responseWithNoRole = makePutRequest(request500WithNewAccountId, write(putCreateAccountOtherUserJsonV500))
//      Then("We should get a 403 and some error message")
//      responseWithNoRole.code should equal(403)
//      responseWithNoRole.body.toString contains(extractErrorMessageCode(UserHasMissingRoles)) should be (true)
// 
//
//      Then("We grant the roles and test it again")
//      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser2.userId, ApiRole.canCreateAccount.toString)
//      val responseWithOtherUserV500 = makePutRequest(request500WithNewAccountId, write(putCreateAccountOtherUserJsonV500))
//
//      val account2 = responseWithOtherUserV500.body.extract[CreateCustomerAccountLinkJson]
//      account2.product_code should be (putCreateAccountOtherUserJsonV500.product_code)
//      account2.`label` should be (putCreateAccountOtherUserJsonV500.`label`)
//      account2.balance.amount.toDouble should be (putCreateAccountOtherUserJsonV500.balance.amount.toDouble)
//      account2.balance.currency should be (putCreateAccountOtherUserJsonV500.balance.currency)
//      account2.branch_id should be (putCreateAccountOtherUserJsonV500.branch_id)
//      account2.user_id should be (putCreateAccountOtherUserJsonV500.user_id)
//      account2.label should be (putCreateAccountOtherUserJsonV500.label)
//      account2.account_routings should be (putCreateAccountOtherUserJsonV500.account_routings)

    }

  }

} 
