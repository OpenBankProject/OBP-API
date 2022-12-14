package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.{CanDeleteCustomerCascade, CanDeleteTransactionCascade}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class DeleteCustomerCascadeTest extends V400ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * mvn test -D tagsToInclude
    *
    * This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)

  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.deleteCustomerCascade))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId / 
        "customers" / "CUSTOMER_ID" ).DELETE
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId /
        "customers" / "CUSTOMER_ID" ).DELETE <@(user1)
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 403")
      response400.code should equal(403)
      val errorMessage = response400.body.extract[ErrorMessage].message
      errorMessage contains (UserHasMissingRoles) should be (true) 
      errorMessage contains (CanDeleteCustomerCascade.toString()) should be (true) 
    }
  }
  feature(s"test $ApiEndpoint1 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)

      Then("we create the Customer Attribute")
      createAndGetCustomerAttributeIdViaEndpoint(bankId:String, customerId:String, user1)

      When("We make a request v4.0.0")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteCustomerCascade.toString)
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId /
        "customers" / customerId ).DELETE <@(user1)
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 200")
      response400.code should equal(200)

      When("We try to delete one more time we should get 404")
      makeDeleteRequest(request400).code should equal(404)
    }
  }  

}
