package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateUserCustomerLink, CanDeleteUserCustomerLink, CanGetUserCustomerLink}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v2_0_0.OBPAPI2_0_0.Implementations2_0_0
import code.api.v2_0_0.UserCustomerLinksJson
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class UserCustomerLinkTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getUserCustomerLinksByUserId))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.deleteUserCustomerLink))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getUserCustomerLinksByCustomerId))
  
  object VersionOfApi2 extends Tag(ApiVersion.v2_0_0.toString)
  object ApiEndpoint4 extends Tag(nameOf(Implementations2_0_0.createUserCustomerLinks))

  lazy val bankId = randomBankId
  lazy val firstUserId = resourceUser1.userId
  
  

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "user_customer_links" / "users" / firstUserId ).GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "user_customer_links" / "users" / firstUserId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetUserCustomerLink)
    }
  }
  
  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    lazy val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "user_customer_links" / "customers" / customerId ).GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint3 version $VersionOfApi - Authorized access") {
    lazy val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "user_customer_links" / "customers" / customerId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetUserCustomerLink)
    }
  }
  

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "user_customer_links" / "USER_CUSTOMER_LINK_ID").DELETE
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "user_customer_links" / "USER_CUSTOMER_LINK_ID").DELETE <@(user1)
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanDeleteUserCustomerLink)
    }
  }

  feature(s"test $ApiEndpoint1, $ApiEndpoint2, $ApiEndpoint4 version $VersionOfApi - All good") {
    lazy val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)
    lazy val postJson = SwaggerDefinitionsJSON.createUserCustomerLinkJson
      .copy(user_id = firstUserId, customer_id = customerId)
    
    scenario("We will call the endpoints", ApiEndpoint1, ApiEndpoint2, ApiEndpoint4, VersionOfApi) {

      // 1st Get User Customer Link
      Entitlement.entitlement.vend.addEntitlement(bankId, firstUserId, CanGetUserCustomerLink.toString())
      val getRequest = (v4_0_0_Request / "banks" / bankId / "user_customer_links" / "users" / firstUserId).GET <@(user1)
      val getResponse = makeGetRequest(getRequest)
      Then("We should get a 200")
      getResponse.code should equal(200)
      val initialSize = getResponse.body.extract[UserCustomerLinksJson]
        .user_customer_links.size
      
      // Create User Customer Link
      Entitlement.entitlement.vend.addEntitlement(bankId, firstUserId, CanCreateUserCustomerLink.toString())
      val createRequest = (v4_0_0_Request / "banks" / bankId / "user_customer_links" ).POST <@(user1)
      val createResponse = makePostRequest(createRequest, write(postJson))
      Then("We should get a 201")
      createResponse.code should equal(201)

      // 2nd Get User Customer Link
      val user_customer_link_id = makeGetRequest(getRequest).body.extract[UserCustomerLinksJson]
        .user_customer_links.headOption.map(_.user_customer_link_id).getOrElse("")

      // Delete User Customer Link
      Entitlement.entitlement.vend.addEntitlement(bankId, firstUserId, CanDeleteUserCustomerLink.toString())
      val deleteRequest = (v4_0_0_Request / "banks" / bankId / "user_customer_links" / user_customer_link_id).DELETE <@(user1)
      val deleteResponse = makeDeleteRequest(deleteRequest)
      Then("We should get a 200")
      deleteResponse.code should equal(200)
      
      // 2nd Delete User Customer Link call should fail due to already deleted row in a table
      makeDeleteRequest(deleteRequest).code should equal(400)

      // 3rd Get User Customer Link
      makeGetRequest(getRequest).body.extract[UserCustomerLinksJson]
        .user_customer_links.size should equal(initialSize)
      
    }
  }
  
}
