package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{ ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class CustomerAttributesTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createCustomerAttribute))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateCustomerAttribute))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getCustomerAttributes))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getCustomerAttributeById))

  
  lazy val bankId = randomBankId
  lazy val postCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400
  lazy val customerId = createAndGetCustomerId(bankId, user1)
  

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attribute").POST
      val response400 = makePostRequest(request400, write(postCustomerAttributeJsonV400))
      Then("We should get a 400")
      response400.code should equal(400)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access- missing role") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attribute").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postCustomerAttributeJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
    }
  }
//
//  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access- with role- success") {
//    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
//      When("We make a request v4.0.0")
//      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attribute").POST <@ (user1)
//      val response400 = makePostRequest(request400, write(postCustomerAttributeJsonV400))
//      Then("We should get a 403")
//      response400.code should equal(403)
//      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
//    }
//  }
//  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
//    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
//      When("We make a request v4.0.0")
//      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / "account-access" / "revoke").POST
//      val response400 = makePostRequest(request400, write(postAccountAccessJson))
//      Then("We should get a 400")
//      response400.code should equal(400)
//      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
//    }
//  }
//  feature(s"test $ApiEndpoint1 and $ApiEndpoint2 version $VersionOfApi - Authorized access") {
//    scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint1, ApiEndpoint2) {
//      
//      val account = createAnAccount(bankId, user1)
//      val view = createViewForAnAccount(bankId, account.account_id)
//      val postJson = PostAccountAccessJsonV400(resourceUser2.userId, PostViewJsonV400(view.id, view.is_system))
//      When("We send the request")
//      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / account.account_id / "account-access" / "grant").POST <@ (user1)
//      val response = makePostRequest(request, write(postJson))
//      Then("We should get a 201 and check the response body")
//      response.code should equal(201)
//      response.body.extract[ViewJsonV300]
//      
//      When("We send the request")
//      val requestRevoke = (v4_0_0_Request / "banks" / bankId / "accounts" / account.account_id / "account-access" / "revoke").POST <@ (user1)
//      val responseRevoke = makePostRequest(requestRevoke, write(postJson))
//      Then("We should get a 201 and check the response body")
//      responseRevoke.code should equal(201)
//      responseRevoke.body.extract[RevokedJsonV400]
//    }
//  }

  
  
}
