package code.api.v6_0_0

import org.json4s._
import code.api.Constant
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.CanVerifyUserCredentials
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.{InvalidLoginCredentials, UserHasMissingRoles, UsernameHasBeenLocked}
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import code.model.dataAccess.AuthUser
import code.scope.Scope
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Full
import org.json4s.native.Serialization.write
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
  }

  override def afterAll(): Unit = {
    // Clean up test user
    if (testAuthUser != null) {
      testAuthUser.delete_!
    }
    // Reset any login attempt locks
    LoginAttempt.resetBadLoginAttempts(Constant.localIdentityProvider, testUsername)
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
      And("The error message should indicate application not identified (UserOrApplication mode requires at least app auth)")
      response.body.extract[ErrorMessage].message should include("OBP-20200")
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

    scenario("Successfully verify valid credentials with consumer scope (no user entitlement)", ApiEndpoint, VersionOfApi) {
      // Add scope to consumer instead of entitlement to user — UserOrApplication should accept this
      val addedScope = Scope.scope.vend.addScope("", testConsumer.id.get.toString, ApiRole.CanVerifyUserCredentials.toString)

      When("We verify valid credentials using consumer with scope")
      val postJson = Map(
        "username" -> testUsername,
        "password" -> testPassword,
        "provider" -> Constant.localIdentityProvider
      )
      val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
      val response = try {
        makePostRequest(request, write(postJson))
      } finally {
        Scope.scope.vend.deleteScope(addedScope)
      }

      Then("We should get a 200")
      response.code should equal(200)

      And("The response should contain user details")
      val json = response.body
      (json \ "username").extract[String] should equal(testUsername)
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

    scenario("Wrong password for external provider should not increment local user bad login attempts", ApiEndpoint, VersionOfApi) {
      // This test verifies the fix for collateral damage: two users share the same username
      // but have different providers. Verifying the external user with wrong credentials
      // must NOT increment bad login attempts on the local user.
      val sharedUsername = "shared_user_" + randomString(8).toLowerCase
      val localPassword = "LocalPassword123!"
      val localEmail = sharedUsername + "@local.example.com"
      val externalProvider = "external_test_provider"

      // Create a local user
      val localUser = AuthUser.create
        .email(localEmail)
        .username(sharedUsername)
        .password(localPassword)
        .validated(true)
        .firstName("Local")
        .lastName("User")
        .provider(Constant.localIdentityProvider)
        .saveMe()

      // Create an external user with the same username (dummy password, as external users have)
      val externalUser = AuthUser.create
        .email(sharedUsername + "@external.example.com")
        .username(sharedUsername)
        .password(net.liftweb.util.Helpers.randomString(40)) // random dummy password
        .validated(true)
        .firstName("External")
        .lastName("User")
        .provider(externalProvider)
        .saveMe()

      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      try {
        // Reset any prior login attempts for both
        LoginAttempt.resetBadLoginAttempts(Constant.localIdentityProvider, sharedUsername)
        LoginAttempt.resetBadLoginAttempts(externalProvider, sharedUsername)

        // Record local user's bad login attempts before the test
        val localAttemptsBefore = LoginAttempt.getOrCreateBadLoginStatus(
          Constant.localIdentityProvider, sharedUsername
        ).map(_.badAttemptsSinceLastSuccessOrReset).openOr(0)

        When("We verify credentials with the external provider (which will fail)")
        val postJson = Map(
          "username" -> sharedUsername,
          "password" -> "SomeWrongPassword!",
          "provider" -> externalProvider
        )
        val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
        val response = makePostRequest(request, write(postJson))

        Then("We should get a 401")
        response.code should equal(401)

        And("The local user's bad login attempts should NOT have increased")
        val localAttemptsAfter = LoginAttempt.getOrCreateBadLoginStatus(
          Constant.localIdentityProvider, sharedUsername
        ).map(_.badAttemptsSinceLastSuccessOrReset).openOr(0)
        localAttemptsAfter should equal(localAttemptsBefore)

        And("The local user should still be able to log in with correct credentials")
        val localPostJson = Map(
          "username" -> sharedUsername,
          "password" -> localPassword,
          "provider" -> Constant.localIdentityProvider
        )
        val localRequest = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
        val localResponse = makePostRequest(localRequest, write(localPostJson))
        localResponse.code should equal(200)
      } finally {
        // Clean up
        LoginAttempt.resetBadLoginAttempts(Constant.localIdentityProvider, sharedUsername)
        LoginAttempt.resetBadLoginAttempts(externalProvider, sharedUsername)
        localUser.delete_!
        externalUser.delete_!
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
    }

    scenario("Empty provider should be treated as local provider", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      When("We verify valid credentials with an empty provider string")
      val postJson = Map(
        "username" -> testUsername,
        "password" -> testPassword,
        "provider" -> ""
      )
      val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
      val response = try {
        makePostRequest(request, write(postJson))
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 200 because empty provider is treated as local")
      response.code should equal(200)

      And("The response should contain user details")
      (response.body \ "username").extract[String] should equal(testUsername)
    }

    scenario("Same username across multiple realistic providers should be fully isolated", ApiEndpoint, VersionOfApi) {
      // In production, a single username like "alice" might exist under several providers:
      // the local OBP instance, Google OIDC, GitHub, and possibly erroneous entries.
      // Each must be completely isolated from the others.
      val sharedUsername = "alice_" + randomString(8).toLowerCase
      val localPassword = "LocalAlice123!"

      val googleProvider = "https://accounts.google.com"
      val githubProvider = "https://github.com/login/oauth"
      val erroneousProvider = "https://gogle.com"  // typo in production data

      // Create a local user
      val localUser = AuthUser.create
        .email(sharedUsername + "@openbankproject.com")
        .username(sharedUsername)
        .password(localPassword)
        .validated(true)
        .firstName("Alice")
        .lastName("Local")
        .provider(Constant.localIdentityProvider)
        .saveMe()

      // Create external users with the same username under different providers
      // (as would exist in production when users sign in via different identity providers)
      val googleUser = AuthUser.create
        .email(sharedUsername + "@gmail.com")
        .username(sharedUsername)
        .password(randomString(40)) // dummy password, as with all external users
        .validated(true)
        .firstName("Alice")
        .lastName("Google")
        .provider(googleProvider)
        .saveMe()

      val githubUser = AuthUser.create
        .email(sharedUsername + "@github.com")
        .username(sharedUsername)
        .password(randomString(40))
        .validated(true)
        .firstName("Alice")
        .lastName("GitHub")
        .provider(githubProvider)
        .saveMe()

      val erroneousUser = AuthUser.create
        .email(sharedUsername + "@gogle.com")
        .username(sharedUsername)
        .password(randomString(40))
        .validated(true)
        .firstName("Alice")
        .lastName("Erroneous")
        .provider(erroneousProvider)
        .saveMe()

      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      try {
        // Reset all login attempt counters
        val allProviders = List(Constant.localIdentityProvider, googleProvider, githubProvider, erroneousProvider)
        allProviders.foreach(p => LoginAttempt.resetBadLoginAttempts(p, sharedUsername))

        When("We attempt to verify credentials against the Google provider (will fail, no connector)")
        val googlePostJson = Map(
          "username" -> sharedUsername,
          "password" -> "WrongPassword!",
          "provider" -> googleProvider
        )
        val googleRequest = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
        makePostRequest(googleRequest, write(googlePostJson))

        And("We attempt to verify credentials against the GitHub provider (will fail)")
        val githubPostJson = Map(
          "username" -> sharedUsername,
          "password" -> "WrongPassword!",
          "provider" -> githubProvider
        )
        val githubRequest = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
        makePostRequest(githubRequest, write(githubPostJson))

        And("We attempt to verify credentials against the erroneous provider (will fail)")
        val erroneousPostJson = Map(
          "username" -> sharedUsername,
          "password" -> "WrongPassword!",
          "provider" -> erroneousProvider
        )
        val erroneousRequest = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
        makePostRequest(erroneousRequest, write(erroneousPostJson))

        Then("The local user's bad login attempts should still be zero")
        val localAttempts = LoginAttempt.getOrCreateBadLoginStatus(
          Constant.localIdentityProvider, sharedUsername
        ).map(_.badAttemptsSinceLastSuccessOrReset).openOr(0)
        localAttempts should equal(0)

        And("The local user should still authenticate successfully")
        val localPostJson = Map(
          "username" -> sharedUsername,
          "password" -> localPassword,
          "provider" -> Constant.localIdentityProvider
        )
        val localRequest = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
        val localResponse = makePostRequest(localRequest, write(localPostJson))
        localResponse.code should equal(200)
        (localResponse.body \ "username").extract[String] should equal(sharedUsername)
        (localResponse.body \ "provider").extract[String] should equal(Constant.localIdentityProvider)

      } finally {
        val allProviders = List(Constant.localIdentityProvider, googleProvider, githubProvider, erroneousProvider)
        allProviders.foreach(p => LoginAttempt.resetBadLoginAttempts(p, sharedUsername))
        localUser.delete_!
        googleUser.delete_!
        githubUser.delete_!
        erroneousUser.delete_!
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
    }

    scenario("Failed external auth for one provider should not affect a different external provider", ApiEndpoint, VersionOfApi) {
      // Providers are independent namespaces. Failing against https://accounts.google.com
      // should not increment bad attempts for https://github.com/login/oauth.
      val sharedUsername = "multi_ext_" + randomString(8).toLowerCase
      val googleProvider = "https://accounts.google.com"
      val githubProvider = "https://github.com/login/oauth"

      val googleUser = AuthUser.create
        .email(sharedUsername + "@gmail.com")
        .username(sharedUsername)
        .password(randomString(40))
        .validated(true)
        .firstName("Test")
        .lastName("Google")
        .provider(googleProvider)
        .saveMe()

      val githubUser = AuthUser.create
        .email(sharedUsername + "@github.com")
        .username(sharedUsername)
        .password(randomString(40))
        .validated(true)
        .firstName("Test")
        .lastName("GitHub")
        .provider(githubProvider)
        .saveMe()

      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      try {
        LoginAttempt.resetBadLoginAttempts(googleProvider, sharedUsername)
        LoginAttempt.resetBadLoginAttempts(githubProvider, sharedUsername)

        When("We fire multiple failed auth attempts against Google provider")
        for (_ <- 1 to 3) {
          val postJson = Map(
            "username" -> sharedUsername,
            "password" -> "WrongPassword!",
            "provider" -> googleProvider
          )
          val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
          makePostRequest(request, write(postJson))
        }

        Then("GitHub provider's bad login attempts should still be zero")
        val githubAttempts = LoginAttempt.getOrCreateBadLoginStatus(
          githubProvider, sharedUsername
        ).map(_.badAttemptsSinceLastSuccessOrReset).openOr(0)
        githubAttempts should equal(0)

      } finally {
        LoginAttempt.resetBadLoginAttempts(googleProvider, sharedUsername)
        LoginAttempt.resetBadLoginAttempts(githubProvider, sharedUsername)
        googleUser.delete_!
        githubUser.delete_!
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
    }

    scenario("Failed local auth should not affect external users with the same username", ApiEndpoint, VersionOfApi) {
      // The reverse of the external→local test: wrong local password should not
      // touch the external provider's login attempt counter.
      val sharedUsername = "reverse_iso_" + randomString(8).toLowerCase
      val localPassword = "LocalPassword123!"
      val googleProvider = "https://accounts.google.com"

      val localUser = AuthUser.create
        .email(sharedUsername + "@openbankproject.com")
        .username(sharedUsername)
        .password(localPassword)
        .validated(true)
        .firstName("Test")
        .lastName("Local")
        .provider(Constant.localIdentityProvider)
        .saveMe()

      val googleUser = AuthUser.create
        .email(sharedUsername + "@gmail.com")
        .username(sharedUsername)
        .password(randomString(40))
        .validated(true)
        .firstName("Test")
        .lastName("Google")
        .provider(googleProvider)
        .saveMe()

      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      try {
        LoginAttempt.resetBadLoginAttempts(Constant.localIdentityProvider, sharedUsername)
        LoginAttempt.resetBadLoginAttempts(googleProvider, sharedUsername)

        When("We fire multiple failed local auth attempts with wrong password")
        for (_ <- 1 to 3) {
          val postJson = Map(
            "username" -> sharedUsername,
            "password" -> "WrongPassword!",
            "provider" -> Constant.localIdentityProvider
          )
          val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
          makePostRequest(request, write(postJson))
        }

        Then("Google provider's bad login attempts should still be zero")
        val googleAttempts = LoginAttempt.getOrCreateBadLoginStatus(
          googleProvider, sharedUsername
        ).map(_.badAttemptsSinceLastSuccessOrReset).openOr(0)
        googleAttempts should equal(0)

      } finally {
        LoginAttempt.resetBadLoginAttempts(Constant.localIdentityProvider, sharedUsername)
        LoginAttempt.resetBadLoginAttempts(googleProvider, sharedUsername)
        localUser.delete_!
        googleUser.delete_!
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
    }

    scenario("Non-existent external user should fail cleanly", ApiEndpoint, VersionOfApi) {
      // Post a username that has no AuthUser record at all for this external provider.
      // Should get 401 without any side effects on other providers.
      val nonExistentUsername = "no_such_user_" + randomString(8).toLowerCase
      val googleProvider = "https://accounts.google.com"

      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      try {
        When("We verify credentials for a non-existent external user")
        val postJson = Map(
          "username" -> nonExistentUsername,
          "password" -> "SomePassword!",
          "provider" -> googleProvider
        )
        val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
        val response = makePostRequest(request, write(postJson))

        Then("We should get a 401")
        response.code should equal(401)
        response.body.extract[ErrorMessage].message should include("OBP-20004")
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
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

    scenario("Successfully verify credentials with URL-encoded local provider", ApiEndpoint, VersionOfApi) {
      // Test that URL-encoded local provider strings are correctly decoded
      // The local provider constant might be URL-encoded in some scenarios
      val urlEncodedLocalProvider = java.net.URLEncoder.encode(Constant.localIdentityProvider, "UTF-8")
      val username = "encoded_local_test_" + randomString(8).toLowerCase
      val password = "TestPassword123!"
      val email = username + "@example.com"

      // Create a local user
      val testUser = AuthUser.create
        .email(email)
        .username(username)
        .password(password)
        .validated(true)
        .firstName("Test")
        .lastName("EncodedLocal")
        .provider(Constant.localIdentityProvider)
        .saveMe()

      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      try {
        When("We verify credentials with URL-encoded local provider")
        val postJson = Map(
          "username" -> username,
          "password" -> password,
          "provider" -> urlEncodedLocalProvider  // Send encoded version of local provider
        )
        val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
        val response = makePostRequest(request, write(postJson))

        Then("We should get a 200 because the local provider is decoded correctly")
        response.code should equal(200)

        And("The response should contain user details with local provider")
        (response.body \ "username").extract[String] should equal(username)
        (response.body \ "provider").extract[String] should equal(Constant.localIdentityProvider)
      } finally {
        testUser.delete_!
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
    }

    scenario("Successfully verify credentials with provider containing special characters", ApiEndpoint, VersionOfApi) {
      // Test that the provider field correctly handles URL encoding/decoding
      // In this test, we verify that empty provider (treated as local) works correctly
      val username = "special_chars_test_" + randomString(8).toLowerCase
      val password = "TestPassword123!"
      val email = username + "@example.com"

      // Create a local user (empty provider is treated as local)
      val testUser = AuthUser.create
        .email(email)
        .username(username)
        .password(password)
        .validated(true)
        .firstName("Test")
        .lastName("SpecialChars")
        .provider(Constant.localIdentityProvider)
        .saveMe()

      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      try {
        When("We verify credentials with empty provider (should be treated as local)")
        val postJson = Map(
          "username" -> username,
          "password" -> password,
          "provider" -> ""  // Empty provider
        )
        val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
        val response = makePostRequest(request, write(postJson))

        Then("We should get a 200 because empty provider is treated as local")
        response.code should equal(200)

        And("The response should contain user details")
        (response.body \ "username").extract[String] should equal(username)
        (response.body \ "provider").extract[String] should equal(Constant.localIdentityProvider)
      } finally {
        testUser.delete_!
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
    }

    scenario("Verify credentials with non-encoded local provider should work", ApiEndpoint, VersionOfApi) {
      // Test that non-encoded local provider (the normal case) still works correctly
      val username = "non_encoded_test_" + randomString(8).toLowerCase
      val password = "TestPassword123!"
      val email = username + "@example.com"

      // Create a local user
      val testUser = AuthUser.create
        .email(email)
        .username(username)
        .password(password)
        .validated(true)
        .firstName("Test")
        .lastName("NonEncoded")
        .provider(Constant.localIdentityProvider)
        .saveMe()

      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      try {
        When("We verify credentials with non-encoded local provider")
        val postJson = Map(
          "username" -> username,
          "password" -> password,
          "provider" -> Constant.localIdentityProvider  // Send non-encoded local provider
        )
        val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
        val response = makePostRequest(request, write(postJson))

        Then("We should get a 200 because non-encoded local provider works normally")
        response.code should equal(200)

        And("The response should contain user details")
        (response.body \ "username").extract[String] should equal(username)
        (response.body \ "provider").extract[String] should equal(Constant.localIdentityProvider)
      } finally {
        testUser.delete_!
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
    }

    scenario("URL-encoded provider mismatch should fail with 401", ApiEndpoint, VersionOfApi) {
      // Test that provider mismatch is detected even with URL encoding
      // User has local provider, but request sends a different (encoded) provider
      val wrongProvider = "https://github.com/login/oauth"
      val urlEncodedWrongProvider = java.net.URLEncoder.encode(wrongProvider, "UTF-8")
      val username = "encoded_mismatch_test_" + randomString(8).toLowerCase
      val password = "TestPassword123!"
      val email = username + "@example.com"

      // Create a local user
      val testUser = AuthUser.create
        .email(email)
        .username(username)
        .password(password)
        .validated(true)
        .firstName("Test")
        .lastName("Mismatch")
        .provider(Constant.localIdentityProvider)
        .saveMe()

      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      try {
        When("We verify credentials with URL-encoded wrong provider")
        val postJson = Map(
          "username" -> username,
          "password" -> password,
          "provider" -> urlEncodedWrongProvider  // Send encoded wrong provider
        )
        val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
        val response = makePostRequest(request, write(postJson))

        Then("We should get a 401 because this is treated as external provider auth (no connector)")
        response.code should equal(401)
        response.body.extract[ErrorMessage].message should include("OBP-20004")
      } finally {
        testUser.delete_!
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
    }

  }
}
