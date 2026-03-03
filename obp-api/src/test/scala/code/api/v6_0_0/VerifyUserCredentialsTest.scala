package code.api.v6_0_0

import code.api.Constant
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanVerifyUserCredentials
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.{InvalidLoginCredentials, UserHasMissingRoles, UsernameHasBeenLocked}
import code.api.v6_0_0.APIMethods600.Implementations6_0_0
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import code.model.dataAccess.AuthUser
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Full
import net.liftweb.json.Serialization.write
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag

/**
 * Test suite for Verify User Credentials endpoint (POST /obp/v6.0.0/users/verify-credentials)
 *
 * Tests cover:
 * - Anonymous access (should fail with 401)
 * - Missing role (should fail with 403)
 * - Successful credential verification
 * - Invalid password (should fail with 401)
 * - Invalid username (should fail with 401)
 * - Provider mismatch
 * - Invalid JSON format
 */
class VerifyUserCredentialsTest extends V600ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations6_0_0.verifyUserCredentials))

  // Test data
  val testUsername = "verify_creds_test_" + randomString(8).toLowerCase
  val testPassword = "TestPassword123!"
  val testEmail = testUsername + "@example.com"
  var testAuthUser: AuthUser = null
  
  // Test data for URL-encoded provider test
  val testUsernameUrlEncoded = "verify_url_encoded_" + randomString(8).toLowerCase
  val testPasswordUrlEncoded = "TestPassword456!"
  val testEmailUrlEncoded = testUsernameUrlEncoded + "@example.com"
  val testProviderWithSpecialChars = "http://auth.example.com:8080/oauth"
  var testAuthUserUrlEncoded: AuthUser = null

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Create a test user for credential verification
    testAuthUser = AuthUser.create
      .email(testEmail)
      .username(testUsername)
      .password(testPassword)
      .validated(true)
      .firstName("Test")
      .lastName("User")
      .provider(Constant.localIdentityProvider)
      .saveMe()
    
    // Create a test user with URL-encoded provider
    testAuthUserUrlEncoded = AuthUser.create
      .email(testEmailUrlEncoded)
      .username(testUsernameUrlEncoded)
      .password(testPasswordUrlEncoded)
      .validated(true)
      .firstName("TestUrl")
      .lastName("Encoded")
      .provider(testProviderWithSpecialChars)
      .saveMe()
  }

  override def afterAll(): Unit = {
    // Clean up test user
    if (testAuthUser != null) {
      testAuthUser.delete_!
    }
    if (testAuthUserUrlEncoded != null) {
      testAuthUserUrlEncoded.delete_!
    }
    // Reset any login attempt locks
    LoginAttempt.resetBadLoginAttempts(Constant.localIdentityProvider, testUsername)
    LoginAttempt.resetBadLoginAttempts(testProviderWithSpecialChars, testUsernameUrlEncoded)
    super.afterAll()
  }

  feature(s"Verify User Credentials - POST /obp/v6.0.0/users/verify-credentials - $VersionOfApi") {

    scenario("Anonymous access should fail with 401", ApiEndpoint, VersionOfApi) {
      When("We make the request without authentication")
      val postJson = Map(
        "username" -> testUsername,
        "password" -> testPassword,
        "provider" -> Constant.localIdentityProvider
      )
      val request = (v6_0_0_Request / "users" / "verify-credentials").POST
      val response = makePostRequest(request, write(postJson))

      Then("We should get a 401")
      response.code should equal(401)
      And("The error message should indicate authentication is required")
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("Authenticated user without role should fail with 403", ApiEndpoint, VersionOfApi) {
      When("We make the request as an authenticated user without the required role")
      val postJson = Map(
        "username" -> testUsername,
        "password" -> testPassword,
        "provider" -> Constant.localIdentityProvider
      )
      val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))

      Then("We should get a 403")
      response.code should equal(403)
      And("The error message should indicate missing role")
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanVerifyUserCredentials)
    }

    scenario("Successfully verify valid credentials", ApiEndpoint, VersionOfApi) {
      // Add the required entitlement
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      When("We verify valid credentials")
      val postJson = Map(
        "username" -> testUsername,
        "password" -> testPassword,
        "provider" -> Constant.localIdentityProvider
      )
      val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
      val response = try {
        makePostRequest(request, write(postJson))
      } finally {
        // Clean up entitlement
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 200")
      response.code should equal(200)

      And("The response should contain user details")
      val json = response.body
      (json \ "username").extract[String] should equal(testUsername)
      (json \ "email").extract[String] should equal(testEmail)
      (json \ "provider").extract[String] should equal(Constant.localIdentityProvider)
      (json \ "user_id").extract[String] should not be empty
    }

    scenario("Fail to verify with wrong password", ApiEndpoint, VersionOfApi) {
      // Add the required entitlement
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      When("We verify credentials with wrong password")
      val postJson = Map(
        "username" -> testUsername,
        "password" -> "WrongPassword123!",
        "provider" -> Constant.localIdentityProvider
      )
      val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
      val response = try {
        makePostRequest(request, write(postJson))
      } finally {
        // Reset bad login attempts for this user
        LoginAttempt.resetBadLoginAttempts(Constant.localIdentityProvider, testUsername)
        // Clean up entitlement
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 401")
      response.code should equal(401)
      And("The error message should indicate invalid credentials")
      response.body.extract[ErrorMessage].message should include("OBP-20004")
    }

    scenario("Fail to verify with non-existent username", ApiEndpoint, VersionOfApi) {
      // Add the required entitlement
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      When("We verify credentials with non-existent username")
      val postJson = Map(
        "username" -> ("nonexistent_user_" + randomString(8)),
        "password" -> testPassword,
        "provider" -> Constant.localIdentityProvider
      )
      val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
      val response = try {
        makePostRequest(request, write(postJson))
      } finally {
        // Clean up entitlement
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 401")
      response.code should equal(401)
      And("The error message should indicate invalid credentials")
      response.body.extract[ErrorMessage].message should include("OBP-20004")
    }

    scenario("Fail to verify with mismatched provider", ApiEndpoint, VersionOfApi) {
      // Add the required entitlement
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      When("We verify credentials with wrong provider")
      val postJson = Map(
        "username" -> testUsername,
        "password" -> testPassword,
        "provider" -> "some_other_provider"
      )
      val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
      val response = try {
        makePostRequest(request, write(postJson))
      } finally {
        // Clean up entitlement
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 401")
      response.code should equal(401)
      And("The error message should indicate invalid credentials")
      response.body.extract[ErrorMessage].message should include("OBP-20004")
    }

    scenario("Fail with invalid JSON format", ApiEndpoint, VersionOfApi) {
      // Add the required entitlement
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      When("We send invalid JSON")
      val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
      val response = try {
        makePostRequest(request, "{ invalid json }")
      } finally {
        // Clean up entitlement
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 400")
      response.code should equal(400)
      And("The error message should indicate invalid JSON format")
      response.body.extract[ErrorMessage].message should include("OBP-10001")
    }

    scenario("Successfully verify credentials with URL-encoded provider", ApiEndpoint, VersionOfApi) {
      // Add the required entitlement
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      When("We verify credentials with URL-encoded provider containing special characters")
      // URL encode the provider: "http://auth.example.com:8080/oauth" -> "http%3A%2F%2Fauth.example.com%3A8080%2Foauth"
      val urlEncodedProvider = java.net.URLEncoder.encode(testProviderWithSpecialChars, "UTF-8")
      val postJson = Map(
        "username" -> testUsernameUrlEncoded,
        "password" -> testPasswordUrlEncoded,
        "provider" -> urlEncodedProvider
      )
      val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
      val response = try {
        makePostRequest(request, write(postJson))
      } finally {
        // Clean up entitlement
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 200")
      response.code should equal(200)

      And("The response should contain user details with decoded provider")
      val json = response.body
      (json \ "username").extract[String] should equal(testUsernameUrlEncoded)
      (json \ "email").extract[String] should equal(testEmailUrlEncoded)
      (json \ "provider").extract[String] should equal(testProviderWithSpecialChars)
      (json \ "user_id").extract[String] should not be empty
    }

    scenario("Successfully verify credentials with non-encoded provider containing special characters", ApiEndpoint, VersionOfApi) {
      // Add the required entitlement
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      When("We verify credentials with non-encoded provider containing special characters")
      val postJson = Map(
        "username" -> testUsernameUrlEncoded,
        "password" -> testPasswordUrlEncoded,
        "provider" -> testProviderWithSpecialChars
      )
      val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
      val response = try {
        makePostRequest(request, write(postJson))
      } finally {
        // Clean up entitlement
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 200")
      response.code should equal(200)

      And("The response should contain user details")
      val json = response.body
      (json \ "username").extract[String] should equal(testUsernameUrlEncoded)
      (json \ "email").extract[String] should equal(testEmailUrlEncoded)
      (json \ "provider").extract[String] should equal(testProviderWithSpecialChars)
      (json \ "user_id").extract[String] should not be empty
    }

  }
}
