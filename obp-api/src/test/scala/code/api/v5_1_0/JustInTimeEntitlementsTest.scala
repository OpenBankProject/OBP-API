package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateEntitlementAtAnyBank, CanGetAnyUser}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.api.v4_0_0.UserJsonV400
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class JustInTimeEntitlementsTest extends V510ServerSetup with DefaultUsers {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getUserByUserId))

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    setPropsValues("create_just_in_time_entitlements" -> "false")
  }

  feature(s"Assuring Just In Time Entitlements works as expected - $VersionOfApi") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v5_1_0_Request / "users" / "user_id" / "user_id").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v5_1_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response = makeGetRequest(request)
      Then("error should be " + UserHasMissingRoles + CanGetAnyUser)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetAnyUser)
    }
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateEntitlementAtAnyBank.toString)
      When("We make a request v4.0.0")
      val request = (v5_1_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response = makeGetRequest(request)
      Then("error should be " + UserHasMissingRoles + CanGetAnyUser)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetAnyUser)
    }
    scenario("Call the endpoint with user credentials and a proper entitlement and Just In Time Entitlements enabled", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateEntitlementAtAnyBank.toString)
      When("We make a request v4.0.0")
      val request = (v5_1_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      setPropsValues("create_just_in_time_entitlements" -> "true")
      val response = makeGetRequest(request)
      Then("We get successful response")
      response.code should equal(200)
      response.body.extract[UserJsonV400].user_id should equal(resourceUser3.userId)
    }
  }

}
