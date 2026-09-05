package code.api.v5_1_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetAnyUser, CanGetEntitlementsForAnyUserAtAnyBank, CanValidateUser}
import code.api.util.ErrorMessages.{UserHasMissingRoles, AuthenticatedUserIsRequired, attemptedToOpenAnEmptyBox}
import code.api.v3_0_0.UserJsonV300
import code.api.v5_1_0.Http4s510.Implementations5_1_0
import code.entitlement.Entitlement
import code.model.UserX
import code.model.dataAccess.AuthUser
import code.users.Users
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag

import java.util.UUID

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
  object ValidateUserByUserId extends Tag(nameOf(Implementations5_1_0.validateUserByUserId))

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request400 = (v5_1_0_Request / "users" / "provider"/"x" / "username" / "USERNAME").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
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
      Then("We get successful response with first_name and last_name fields")
      response400.code should equal(200)
      val json = response400.body.extract[UserWithNamesJsonV510]
      json.first_name should equal("")
      json.last_name should equal("")
      Users.users.vend.deleteResourceUser(user.id.get)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - first_name and last_name populated from AuthUser") {
    scenario("We will call the endpoint with an AuthUser that has first and last name set", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      val username = "user.withnames." + UUID.randomUUID.toString.take(8)
      val email = s"$username@example.com"
      val user = UserX.createResourceUser(defaultProvider, Some(username), None, Some(username), None, Some(UUID.randomUUID.toString), None).openOrThrowException(attemptedToOpenAnEmptyBox)
      val authUser = AuthUser.create
        .email(email).username(username).password(randomString(12))
        .validated(true).firstName("Alice").lastName("Smith")
        .provider(defaultProvider).user(user.userPrimaryKey.value).saveMe()
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "users" / "provider" / user.provider / "username" / user.name).GET <@(user1)
      val response = makeGetRequest(request)
      Then("We get first_name and last_name from AuthUser")
      response.code should equal(200)
      val json = response.body.extract[UserWithNamesJsonV510]
      json.first_name should equal("Alice")
      json.last_name should equal("Smith")
      authUser.delete_!
      Users.users.vend.deleteResourceUser(user.id.get)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access with URL-encoded provider") {
    scenario("We will call the endpoint with a provider containing special URL characters (colon, slash)", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      // Provider contains special URL characters - dispatch encodes '/' as '%2F' but keeps ':' as-is,
      // so "http://127.0.0.1:8080" becomes "http:%2F%2F127.0.0.1:8080" in the request path.
      // The endpoint applies URLDecoder.decode to recover the original provider value before the user lookup.
      val provider = "http://127.0.0.1:8080"
      val user = UserX.createResourceUser(provider, Some("user.url.encoded"), None, Some("user.url.encoded"), None, Some(UUID.randomUUID.toString), None).openOrThrowException(attemptedToOpenAnEmptyBox)
      When("We make a request v5.1.0 with provider containing special URL characters")
      val request = (v5_1_0_Request / "users" / "provider" / provider / "username" / user.name).GET <@(user1)
      val response = makeGetRequest(request)
      Then("We get successful response - endpoint correctly URL-decodes the provider")
      response.code should equal(200)
      response.body.extract[UserWithNamesJsonV510]
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
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
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


  feature(s"test $ValidateUserByUserId version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ValidateUserByUserId, VersionOfApi) {
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "management" / "users" / resourceUser1.userId ).PUT
      val response = makePutRequest(request, write(UserValidatedJson(true)))
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }

  feature(s"test $ValidateUserByUserId version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ValidateUserByUserId, VersionOfApi) {
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "management" / "users" / resourceUser1.userId ).PUT <@ (user1)
      val response = makePutRequest(request, write(UserValidatedJson(true)))
      Then("error should be " + UserHasMissingRoles + CanValidateUser)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be(UserHasMissingRoles + CanValidateUser)
    }
  }
  
  
}
