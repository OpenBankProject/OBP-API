package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetCorrelatedUsersInfo, CanGetCorrelatedUsersInfoAtAnyBank}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class CorrelatedUserInfoTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getCorrelatedUsersInfoByCustomerId))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getMyCorrelatedEntities))

  lazy val bankId = randomBankId

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    lazy val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "correlated-users").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access without roles") {
    lazy val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "correlated-users").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We should get a 403")
      response400.code should equal(403)
      val errorMessage = response400.body.extract[ErrorMessage].message
      errorMessage contains (UserHasMissingRoles) should be (true)
      errorMessage contains (CanGetCorrelatedUsersInfoAtAnyBank.toString()) should be (true)
    }
  }
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access with roles") {
    scenario("We will call the endpoint without user credentials-bank level role", ApiEndpoint1, VersionOfApi) {
      lazy val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val link = createUserCustomerLink(bankId, resourceUser1.userId, customerId)
      
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCorrelatedUsersInfo.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "correlated-users").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We should get a 200")
      response400.code should equal(200)
      val customerAndUsersWithAttributesResponseJson = response400.body.extract[CustomerAndUsersWithAttributesResponseJson]
      customerAndUsersWithAttributesResponseJson.customer.bank_id should be (bankId)
      customerAndUsersWithAttributesResponseJson.users.length should be (1)
    }

    scenario("We will call the endpoint without user credentials - system level role", ApiEndpoint1, VersionOfApi) {
      lazy val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val link = createUserCustomerLink(bankId, resourceUser1.userId, customerId)
      
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCorrelatedUsersInfoAtAnyBank.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "correlated-users").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We should get a 200")
      response400.code should equal(200)
      val customerAndUsersWithAttributesResponseJson = response400.body.extract[CustomerAndUsersWithAttributesResponseJson]
      customerAndUsersWithAttributesResponseJson.customer.bank_id should be (bankId)
      customerAndUsersWithAttributesResponseJson.users.length should be (1)
    }
  }


  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    lazy val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "my" / "correlated-entities").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials-bank level role", ApiEndpoint1, VersionOfApi) {
      lazy val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val link = createUserCustomerLink(bankId, resourceUser1.userId, customerId)
      
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCorrelatedUsersInfo.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "my" / "correlated-entities").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We should get a 200")
      response400.code should equal(200)
      val correlatedEntities = response400.body.extract[CorrelatedEntities]
      correlatedEntities.correlated_entities.head.customer.bank_id should be (bankId)
      correlatedEntities.correlated_entities.head.users.length should be (1)
    }
  }

}
