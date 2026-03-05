package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetAnyUser
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v6_0_0.APIMethods600.Implementations6_0_0
import code.entitlement.Entitlement
import code.model.dataAccess.AuthUser
import code.model.UserX
import code.setup.DefaultUsers
import code.users.Users
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag

import java.util.UUID

class GetUserByProviderAndUsernameTest extends V600ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations6_0_0.getUserByProviderAndUsername))

  feature(s"Get User by USERNAME - GET /obp/v6.0.0/users/provider/PROVIDER/username/USERNAME - $VersionOfApi") {

    scenario("Anonymous access should fail with 401", ApiEndpoint, VersionOfApi) {
      When("We make the request without authentication")
      val request = (v6_0_0_Request / "users" / "provider" / "x" / "username" / "USERNAME").GET
      val response = makeGetRequest(request)

      Then("We should get a 401")
      response.code should equal(401)
      And("The error message should indicate authentication is required")
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("Authenticated user without CanGetAnyUser role should fail with 403", ApiEndpoint, VersionOfApi) {
      When("We make the request without the required role")
      val request = (v6_0_0_Request / "users" / "provider" / defaultProvider / "username" / "USERNAME").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 403")
      response.code should equal(403)
      And("The error message should indicate missing role")
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetAnyUser)
    }

    scenario("Authenticated user with CanGetAnyUser role should succeed", ApiEndpoint, VersionOfApi) {
      val user = UserX.createResourceUser(defaultProvider, Some("user.v600.1"), None, Some("user.v600.1"), None, Some(UUID.randomUUID.toString), None)
        .openOrThrowException("Failed to create test user")
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)

      When("We make the request with the required role")
      val request = (v6_0_0_Request / "users" / "provider" / user.provider / "username" / user.name).GET <@ (user1)
      val response = try {
        makeGetRequest(request)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
        Users.users.vend.deleteResourceUser(user.id.get)
      }

      Then("We should get a 200")
      response.code should equal(200)
      And("The response should contain the correct user_id and username")
      (response.body \ "user_id").extract[String] should equal(user.userId)
      (response.body \ "username").extract[String] should equal(user.name)
      And("The response should include first_name and last_name fields")
      (response.body \ "first_name").extract[String] should equal("")
      (response.body \ "last_name").extract[String] should equal("")
    }

    scenario("Response should include firstname and lastname from AuthUser when present", ApiEndpoint, VersionOfApi) {
      val username = "user.v600.names." + randomString(6).toLowerCase
      val email = username + "@example.com"

      val authUser = AuthUser.create
        .email(email)
        .username(username)
        .password(randomString(12))
        .validated(true)
        .firstName("Alice")
        .lastName("Smith")
        .provider(defaultProvider)
        .saveMe()

      val resourceUser = AuthUser.find(By(AuthUser.username, username))
        .flatMap(au => Users.users.vend.getUserByProviderId(defaultProvider, username))
        .openOrThrowException("Resource user must exist after AuthUser creation")

      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)

      When("We request a user that has an AuthUser with first/last name")
      val request = (v6_0_0_Request / "users" / "provider" / defaultProvider / "username" / username).GET <@ (user1)
      val response = try {
        makeGetRequest(request)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
        authUser.delete_!
      }

      Then("We should get a 200")
      response.code should equal(200)
      And("The first_name and last_name should be populated from AuthUser")
      (response.body \ "first_name").extract[String] should equal("Alice")
      (response.body \ "last_name").extract[String] should equal("Smith")
    }

    scenario("Request for non-existent user should fail with 404", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)

      When("We request a user that does not exist")
      val request = (v6_0_0_Request / "users" / "provider" / defaultProvider / "username" / ("nonexistent_" + randomString(8))).GET <@ (user1)
      val response = try {
        makeGetRequest(request)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 404")
      response.code should equal(404)
    }

    scenario("Provider with URL-encoded special characters should be decoded correctly", ApiEndpoint, VersionOfApi) {
      val provider = "http://127.0.0.1:8080"
      val user = UserX.createResourceUser(provider, Some("user.v600.url"), None, Some("user.v600.url"), None, Some(UUID.randomUUID.toString), None)
        .openOrThrowException("Failed to create test user")
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)

      // dispatch encodes '/' as '%2F', so "http://127.0.0.1:8080" becomes "http:%2F%2F127.0.0.1:8080" on the wire.
      // The endpoint applies URLDecoder.decode to recover the original value before the user lookup.
      When("We make the request with a provider containing slashes and colons")
      val request = (v6_0_0_Request / "users" / "provider" / provider / "username" / user.name).GET <@ (user1)
      val response = try {
        makeGetRequest(request)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
        Users.users.vend.deleteResourceUser(user.id.get)
      }

      Then("We should get a 200 - provider was correctly URL-decoded")
      response.code should equal(200)
      (response.body \ "user_id").extract[String] should equal(user.userId)
    }

  }
}
