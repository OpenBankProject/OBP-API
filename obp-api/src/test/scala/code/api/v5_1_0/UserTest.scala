package code.api.v5_1_0

import java.util.UUID

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetAnyUser, CanGetEntitlementsForAnyUserAtAnyBank}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn, attemptedToOpenAnEmptyBox}
import code.api.v3_0_0.UserJsonV300
import code.api.v4_0_0.UserJsonV400
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.entitlement.Entitlement
import code.model.UserX
import code.users.Users
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class UserTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.getUserByProviderAndUsername))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.getEntitlementsAndPermissions))
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request400 = (v5_1_0_Request / "users" / "provider"/"x" / "username" / "USERNAME").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request400 = (v5_1_0_Request / "users" / "provider"/defaultProvider / "username" / "USERNAME").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("error should be " + UserHasMissingRoles + CanGetAnyUser)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetAnyUser)
    }
  }
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      val user = UserX.createResourceUser(defaultProvider, Some("user.name.1"), None, Some("user.name.1"), None, Some(UUID.randomUUID.toString), None).openOrThrowException(attemptedToOpenAnEmptyBox)
      When("We make a request v5.1.0")
      val request400 = (v5_1_0_Request / "users" / "provider"/user.provider / "username" / user.name ).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(200)
      response400.body.extract[UserJsonV400]
      Users.users.vend.deleteResourceUser(user.id.get)
    }
  }



  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "users" / "USER_ID" / "entitlements-and-permissions").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      val user = UserX.createResourceUser(defaultProvider, Some("user.name.1"), None, Some("user.name.1"), None, Some(UUID.randomUUID.toString), None).openOrThrowException(attemptedToOpenAnEmptyBox)
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "users" / user.userId / "entitlements-and-permissions").GET <@(user1)
      val response = makeGetRequest(request)
      Then("error should be " + UserHasMissingRoles + CanGetEntitlementsForAnyUserAtAnyBank)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetEntitlementsForAnyUserAtAnyBank)
      // Clean up
      Users.users.vend.deleteResourceUser(user.id.get)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetEntitlementsForAnyUserAtAnyBank.toString)
      val user = UserX.createResourceUser(defaultProvider, Some("user.name.1"), None, Some("user.name.1"), None, Some(UUID.randomUUID.toString), None).openOrThrowException(attemptedToOpenAnEmptyBox)
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "users" / user.userId / "entitlements-and-permissions").GET <@(user1)
      val response = makeGetRequest(request)
      Then("We get successful response")
      response.code should equal(200)
      response.body.extract[UserJsonV300]
      // Clean up
      Users.users.vend.deleteResourceUser(user.id.get)
    }
  }
  
  
}
