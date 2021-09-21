package code.api.v4_0_0

import java.util.UUID

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetAnyUser
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn, attemptedToOpenAnEmptyBox}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import code.model.UserX
import code.users.Users
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class UserTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getCurrentUserId))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getUserByUserId))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getUsers))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getUserByUsername))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getUsersByEmail))
  

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "current" / "user_id").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "current" / "user_id").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(200)
      response400.body.extract[UserIdJsonV400].user_id should equal(resourceUser1.userId) 
    }
  }


  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / "user_id").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("error should be " + UserHasMissingRoles + CanGetAnyUser)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetAnyUser)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(200)
      response400.body.extract[UserJsonV400].user_id should equal(resourceUser3.userId)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint3 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("error should be " + UserHasMissingRoles + CanGetAnyUser)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetAnyUser)
    }
  }
  feature(s"test $ApiEndpoint3 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(200)
      response400.body.extract[UsersJsonV400]
    }
  }
  
  feature(s"test $ApiEndpoint4 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "username" / "USERNAME").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint4 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "username" / "USERNAME").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("error should be " + UserHasMissingRoles + CanGetAnyUser)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetAnyUser)
    }
  }
  feature(s"test $ApiEndpoint4 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint4, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      val user = UserX.createResourceUser(defaultProvider, Some("user.name.1"), None, Some("user.name.1"), None, Some(UUID.randomUUID.toString), None).openOrThrowException(attemptedToOpenAnEmptyBox)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "username" / user.idGivenByProvider).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(200)
      response400.body.extract[UserJsonV400]
      Users.users.vend.deleteResourceUser(user.id.get)
    }
  }
  
  feature(s"test $ApiEndpoint5 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint5, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "email" / "EMAIL" / "terminator").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint5 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint5, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "email" / "EMAIL" / "terminator").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("error should be " + UserHasMissingRoles + CanGetAnyUser)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetAnyUser)
    }
  }
  feature(s"test $ApiEndpoint5 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint5, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      val user = UserX.createResourceUser(defaultProvider, Some("user.name.1"), None, Some("user.name.1"), Some("test@tesobe.com"), Some(UUID.randomUUID.toString), None).openOrThrowException(attemptedToOpenAnEmptyBox)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "email" / user.emailAddress / "terminator").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(200)
      response400.body.extract[UsersJsonV400]
      Users.users.vend.deleteResourceUser(user.id.get)
    }
  }
  
  
}
