package code.api.v5_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.v5_0_0.OBPAPI5_0_0.Implementations5_0_0
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.{canCreateCustomerAccountLink, canDeleteCustomerAccountLink, canGetCustomerAccountLink, canGetCustomerAccountLinks, canUpdateCustomerAccountLink}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import net.liftweb.json.Serialization.write

class CustomerAccountLinkTest extends V500ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_0_0.createCustomerAccountLink))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_0_0.getCustomerAccountLinkById))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_0_0.updateCustomerAccountLinkById))
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_0_0.getCustomerAccountLinksByCustomerId))
  object ApiEndpoint5 extends Tag(nameOf(Implementations5_0_0.getCustomerAccountLinksByBankIdAccountId))
  object ApiEndpoint6 extends Tag(nameOf(Implementations5_0_0.deleteCustomerAccountLinkById))




  feature(s"customer account link $VersionOfApi - Error cases ") {

    lazy val testBankId = randomBankId
    lazy val testAccountId = testAccountId1
    lazy val createCustomerAccountLinkJson = SwaggerDefinitionsJSON.createCustomerAccountLinkJson
    lazy val updateCustomerAccountLinkJson = SwaggerDefinitionsJSON.updateCustomerAccountLinkJson
    lazy val customerAccountLinkId1 = "wrongId"
    lazy val customerId1 = "wrongId"
    
    scenario("We will call the endpoints without user credentials", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, ApiEndpoint6,  VersionOfApi) {
      val requestApiEndpoint1 =  (v5_0_0_Request / "banks" / testBankId / "customer-account-links" ).POST 
      val responseApiEndpoint1 = makePostRequest(requestApiEndpoint1, write(createCustomerAccountLinkJson))
      Then("We should get a 401")
      responseApiEndpoint1.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      responseApiEndpoint1.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)


      Then(s"We make a request $VersionOfApi $ApiEndpoint2")
      val requestApiEndpoint2 =  (v5_0_0_Request / "banks" / testBankId / "customer-account-links"/customerAccountLinkId1 ).GET 
      val responseApiEndpoint2 = makeGetRequest(requestApiEndpoint2)
      Then("We should get a 401")
      responseApiEndpoint2.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      responseApiEndpoint2.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)

      Then(s"We make a request $VersionOfApi $ApiEndpoint3")
      val requestApiEndpoint3 =  (v5_0_0_Request / "banks" / testBankId / "customer-account-links"/customerAccountLinkId1 ).PUT
      val responseApiEndpoint3 = makePutRequest(requestApiEndpoint3, write(updateCustomerAccountLinkJson))
      Then("We should get a 401")
      responseApiEndpoint2.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      responseApiEndpoint2.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)

      Then(s"We make a request $VersionOfApi $ApiEndpoint4")
      val requestApiEndpoint4 =  (v5_0_0_Request / "banks" / testBankId /"customers"/customerId1 / "customer-account-links" )
      val responseApiEndpoint4 = makeGetRequest(requestApiEndpoint4)
      Then("We should get a 401")
      responseApiEndpoint4.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      responseApiEndpoint4.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)


      Then(s"We make a request $VersionOfApi $ApiEndpoint5")
      val requestApiEndpoint5 =  (v5_0_0_Request / "banks" / testBankId /"accounts"/testAccountId.value / "customer-account-links")
      val responseApiEndpoint5 = makeGetRequest(requestApiEndpoint5)
      Then("We should get a 401")
      responseApiEndpoint5.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      responseApiEndpoint5.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)



      Then(s"We make a request $VersionOfApi $ApiEndpoint6")
      val requestApiEndpoint6 =  (v5_0_0_Request / "banks" / testBankId / "customer-account-links"/customerAccountLinkId1)
      val responseApiEndpoint6 = makeDeleteRequest(requestApiEndpoint6)
      Then("We should get a 401")
      responseApiEndpoint2.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      responseApiEndpoint2.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }    

    scenario("We will call the endpoint without roles", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, ApiEndpoint6, VersionOfApi) {
      val requestApiEndpoint1 =  (v5_0_0_Request / "banks" / testBankId / "customer-account-links" ).POST <@(user1)
      val responseApiEndpoint1 = makePostRequest(requestApiEndpoint1, write(createCustomerAccountLinkJson))
      Then("We should get a 403")
      responseApiEndpoint1.code should equal(403)
      And("error should be " + UserHasMissingRoles)
      responseApiEndpoint1.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
      responseApiEndpoint1.body.extract[ErrorMessage].message contains (canCreateCustomerAccountLink.toString()) should be (true)


      Then(s"We make a request $VersionOfApi $ApiEndpoint2")
      val requestApiEndpoint2 =  (v5_0_0_Request / "banks" / testBankId / "customer-account-links"/customerAccountLinkId1 ).GET <@(user1)
      val responseApiEndpoint2 = makeGetRequest(requestApiEndpoint2)
      Then("We should get a 403")
      responseApiEndpoint2.code should equal(403)
      And("error should be " + UserHasMissingRoles)
      responseApiEndpoint2.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
      responseApiEndpoint2.body.extract[ErrorMessage].message contains (canGetCustomerAccountLink.toString()) should be (true)

      Then(s"We make a request $VersionOfApi $ApiEndpoint3")
      val requestApiEndpoint3 =  (v5_0_0_Request / "banks" / testBankId / "customer-account-links"/customerAccountLinkId1 ).PUT<@(user1)
      val responseApiEndpoint3 = makePutRequest(requestApiEndpoint3, write(updateCustomerAccountLinkJson))
      Then("We should get a 403")
      responseApiEndpoint3.code should equal(403)
      And("error should be " + UserHasMissingRoles)
      responseApiEndpoint3.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
      responseApiEndpoint3.body.extract[ErrorMessage].message contains (canUpdateCustomerAccountLink.toString()) should be (true)

      Then(s"We make a request $VersionOfApi $ApiEndpoint4")
      val requestApiEndpoint4 =  (v5_0_0_Request / "banks" / testBankId /"customers"/customerId1 / "customer-account-links" ).GET <@(user1)
      val responseApiEndpoint4 = makeGetRequest(requestApiEndpoint4)
      Then("We should get a 403")
      responseApiEndpoint4.code should equal(403)
      And("error should be " + UserHasMissingRoles)
      responseApiEndpoint4.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
      responseApiEndpoint4.body.extract[ErrorMessage].message contains (canGetCustomerAccountLinks.toString()) should be (true)


      Then(s"We make a request $VersionOfApi $ApiEndpoint5")
      val requestApiEndpoint5 =  (v5_0_0_Request / "banks" / testBankId /"accounts"/testAccountId.value / "customer-account-links").GET<@(user1)
      val responseApiEndpoint5 = makeGetRequest(requestApiEndpoint5)
      Then("We should get a 403")
      responseApiEndpoint5.code should equal(403)
      And("error should be " + UserHasMissingRoles)
      responseApiEndpoint5.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
      responseApiEndpoint5.body.extract[ErrorMessage].message contains (canGetCustomerAccountLink.toString()) should be (true)


      Then(s"We make a request $VersionOfApi $ApiEndpoint6")
      val requestApiEndpoint6 =  (v5_0_0_Request / "banks" / testBankId / "customer-account-links"/customerAccountLinkId1).DELETE <@(user1)
      val responseApiEndpoint6 = makeDeleteRequest(requestApiEndpoint6)
      Then("We should get a 403")
      responseApiEndpoint6.code should equal(403)
      And("error should be " + UserHasMissingRoles)
      responseApiEndpoint6.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
      responseApiEndpoint6.body.extract[ErrorMessage].message contains (canDeleteCustomerAccountLink.toString()) should be (true)
    }    

  }
  

  feature(s"Create Account $VersionOfApi - Success access") {
    
    
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, ApiEndpoint6, VersionOfApi) {
      When(s"We make a request $VersionOfApi $ApiEndpoint1")
      
      lazy val testBankId = randomBankId
      lazy val testAccountId = testAccountId1
      val customerId = createAndGetCustomerIdViaEndpoint(testBankId, user1)
      lazy val createCustomerAccountLinkJson = SwaggerDefinitionsJSON.createCustomerAccountLinkJson.copy(customer_id = customerId, bank_id = testBankId, account_id= testAccountId.value)
      lazy val updateCustomerAccountLinkJson = SwaggerDefinitionsJSON.updateCustomerAccountLinkJson.copy(relationship_type ="test")
      
      
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canCreateCustomerAccountLink.toString())
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canUpdateCustomerAccountLink.toString())
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canGetCustomerAccountLink.toString())
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canGetCustomerAccountLinks.toString())
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canDeleteCustomerAccountLink.toString())
      
      val requestApiEndpoint1 =  (v5_0_0_Request / "banks" / testBankId / "customer-account-links" ).POST <@(user1)
      val responseApiEndpoint1 = makePostRequest(requestApiEndpoint1, write(createCustomerAccountLinkJson))
      Then("We should get a 201")
      responseApiEndpoint1.code should equal(201)

      val customerAccountLinkJson1 = responseApiEndpoint1.body.extract[CustomerAccountLinkJson]
      val customerAccountLinkId1 = customerAccountLinkJson1.customer_account_link_id
      customerAccountLinkId1.nonEmpty should be (true)

      Then(s"We make a request $VersionOfApi $ApiEndpoint2")
      val requestApiEndpoint2 =  (v5_0_0_Request / "banks" / testBankId / "customer-account-links"/customerAccountLinkId1 ).GET <@(user1)
      val responseApiEndpoint2 = makeGetRequest(requestApiEndpoint2)
      Then("We should get a 200")
      responseApiEndpoint2.code should equal(200)
      val customerAccountLinkJson2 = responseApiEndpoint2.body.extract[CustomerAccountLinkJson]
      val customerAccountLinkId2 = customerAccountLinkJson2.customer_account_link_id
      customerAccountLinkId2 should be (customerAccountLinkId1)
      
      Then(s"We make a request $VersionOfApi $ApiEndpoint3")
      val requestApiEndpoint3 =  (v5_0_0_Request / "banks" / testBankId / "customer-account-links"/customerAccountLinkId1 ).PUT <@(user1)
      val responseApiEndpoint3 = makePutRequest(requestApiEndpoint3, write(updateCustomerAccountLinkJson))
      Then("We should get a 200")
      responseApiEndpoint3.code should equal(200)
      val customerAccountLinkJson3 = responseApiEndpoint3.body.extract[CustomerAccountLinkJson]
      val customerAccountLinkId3 = customerAccountLinkJson3.customer_account_link_id
      customerAccountLinkId3 should be (customerAccountLinkId1)
      
      Then(s"We make a request $VersionOfApi $ApiEndpoint4")
      val requestApiEndpoint4 =  (v5_0_0_Request / "banks" / testBankId /"customers"/customerId / "customer-account-links" ).GET <@(user1)
      val responseApiEndpoint4 = makeGetRequest(requestApiEndpoint4)
      Then("We should get a 200")
      responseApiEndpoint4.code should equal(200)
      val customerAccountLinkJson4 = responseApiEndpoint4.body.extract[CustomerAccountLinksJson]
      val customerAccountLinkId4 = customerAccountLinkJson4.links.head.customer_account_link_id
      customerAccountLinkId4 should be (customerAccountLinkId1)
      
      
      Then(s"We make a request $VersionOfApi $ApiEndpoint5")
      val requestApiEndpoint5 =  (v5_0_0_Request / "banks" / testBankId /"accounts"/testAccountId.value / "customer-account-links").GET <@(user1)
      val responseApiEndpoint5 = makeGetRequest(requestApiEndpoint5)
      Then("We should get a 200")
      responseApiEndpoint5.code should equal(200)
      val customerAccountLinkJson5 = responseApiEndpoint5.body.extract[CustomerAccountLinksJson]
      val customerAccountLinkId5 = customerAccountLinkJson5.links.head.customer_account_link_id
      customerAccountLinkId5 should be (customerAccountLinkId1)



      Then(s"We make a request $VersionOfApi $ApiEndpoint6")
      val requestApiEndpoint6 =  (v5_0_0_Request / "banks" / testBankId / "customer-account-links"/customerAccountLinkId1).DELETE <@(user1)
      val responseApiEndpoint6 = makeDeleteRequest(requestApiEndpoint6)
      Then("We should get a 204")
      responseApiEndpoint6.code should equal(204)
      Then(s"We call $ApiEndpoint5 should return empty list")
      val responseApiEndpoint5AfterDelete = makeGetRequest(requestApiEndpoint5)
      Then("We should get a 200")
      responseApiEndpoint5AfterDelete.code should equal(200)
      val customerAccountLinkJson5AfterDelete = responseApiEndpoint5AfterDelete.body.extract[CustomerAccountLinksJson]
      customerAccountLinkJson5AfterDelete.links.length should be (0)

    }

  }

} 
