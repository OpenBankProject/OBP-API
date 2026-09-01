/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v6_0_0

import org.json4s._
import java.util.UUID
import code.api.util.ExampleValue
import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.CertificateUtil
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v6_0_0.Http4s600

import code.entitlement.Entitlement
import code.model.dataAccess.{AuthUser, ResourceUser}
import code.users.Users
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Full}
import org.json4s.native.Serialization.write
import net.liftweb.mapper.By
import org.scalatest.Tag

/**
 * Test suite for v6.0.0 Password Reset flow:
 * - Authenticated: POST /obp/v6.0.0/management/user/reset-password-url
 * - Anonymous request: POST /obp/v6.0.0/users/password-reset-url
 * - Anonymous complete: POST /obp/v6.0.0/users/password
 */
class PasswordResetTest extends V600ServerSetup with code.setup.EnvVarOverride {

  override def beforeEach() = {
    wipeTestData()
    super.beforeEach()
    setPropsValues(
      "portal_external_url" -> "https://test-portal.example.com",
      "mail.test.mode" -> "true"
    )
    AuthUser.bulkDelete_!!(By(AuthUser.username, postJson.username))
    ResourceUser.bulkDelete_!!(By(ResourceUser.providerId, postJson.username))
  }

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Http4s600.Implementations6_0_0.resetPasswordUrl))
  object ApiEndpoint2 extends Tag(nameOf(Http4s600.Implementations6_0_0.resetPasswordUrlAnonymous))
  object ApiEndpoint3 extends Tag(nameOf(Http4s600.Implementations6_0_0.resetPasswordComplete))
  lazy val postUserId = UUID.randomUUID.toString
  lazy val postJson = JSONFactory600.PostResetPasswordUrlJsonV600("marko", "marko@tesobe.com", postUserId)

  val strongPassword = ExampleValue.passwordExample.value

  /** Helper to create a JWT token for a given uniqueId with configurable expiry */
  def createJwtToken(uniqueId: String, expiryMinutes: Int = 120): String = {
    val claimsSet = new com.nimbusds.jwt.JWTClaimsSet.Builder()
      .subject(uniqueId)
      .expirationTime(new java.util.Date(System.currentTimeMillis() + expiryMinutes * 60L * 1000L))
      .issueTime(new java.util.Date())
      .build()
    CertificateUtil.jwtWithHmacProtection(claimsSet)
  }

  /** Helper to create an expired JWT token */
  def createExpiredJwtToken(uniqueId: String): String = {
    val claimsSet = new com.nimbusds.jwt.JWTClaimsSet.Builder()
      .subject(uniqueId)
      .expirationTime(new java.util.Date(System.currentTimeMillis() - 1000L)) // 1 second in the past
      .issueTime(new java.util.Date(System.currentTimeMillis() - 60000L))
      .build()
    CertificateUtil.jwtWithHmacProtection(claimsSet)
  }

  // ==========================================
  // Authenticated endpoint: POST /management/user/reset-password-url
  // ==========================================

  feature("Reset password url v6.0.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0")
      val request600 = (v6_0_0_Request / "management" / "user" / "reset-password-url").POST
      val response600 = makePostRequest(request600, write(postJson))
      Then("We should get a 401")
      response600.code should equal(401)
      And("error should be " + AuthenticatedUserIsRequired)
      response600.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }

  feature("Reset password url v6.0.0 - Authorized access") {
    scenario("We will call the endpoint without the proper Role " + canCreateResetPasswordUrl, ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0 without a Role " + canCreateResetPasswordUrl)
      val request600 = (v6_0_0_Request / "management" / "user" / "reset-password-url").POST <@(user1)
      val response600 = makePostRequest(request600, write(postJson))
      Then("We should get a 403")
      response600.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateResetPasswordUrl)
      response600.body.extract[ErrorMessage].message should equal((UserHasMissingRoles + CanCreateResetPasswordUrl))
    }

    scenario("We will call the endpoint with the proper Role " + canCreateResetPasswordUrl, ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateResetPasswordUrl.toString)
      val authUser: AuthUser = AuthUser.create.email(postJson.email).username(postJson.username).validated(true).saveMe()
      val resourceUser: Box[User] = Users.users.vend.getUserByResourceUserId(authUser.user.get)
      When("We make a request v6.0.0")
      val request600 = (v6_0_0_Request / "management" / "user" / "reset-password-url").POST <@(user1)
      val response600 = makePostRequest(request600, write(postJson.copy(user_id = resourceUser.map(_.userId).getOrElse(""))))
      Then("We should get a 201")
      withClue(s"Response body: ${response600.body} ") {
        response600.code should equal(201)
      }
      response600.body.extractOpt[JSONFactory600.ResetPasswordEmailSentJsonV600].isDefined should equal(true)
      And("The response should acknowledge delivery without leaking the reset URL")
      val ack = response600.body.extract[JSONFactory600.ResetPasswordEmailSentJsonV600]
      ack.status should equal("sent")
      ack.to should equal(postJson.email)
      And("The response body must NOT contain a reset_password_url field")
      (response600.body \ "reset_password_url") should equal(org.json4s.JNothing)
    }

    scenario("SMTP failure must surface as a 500, not a fake 'sent'", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateResetPasswordUrl.toString)
      val authUser: AuthUser = AuthUser.create.email(postJson.email).username(postJson.username).validated(true).saveMe()
      val resourceUser: Box[User] = Users.users.vend.getUserByResourceUserId(authUser.user.get)
      And("SMTP is misconfigured (closed port) with test mode off, so the send must fail")
      setPropsValues(
        "mail.test.mode" -> "false",
        "mail.smtp.host" -> "localhost",
        "mail.smtp.port" -> "1" // reserved port, connection refused immediately
      )
      // run_tests_parallel.sh (local runner) exports OBP_MAIL_TEST_MODE=true for
      // every shard so other tests don't open a real SMTP socket. APIUtil.getPropsValue
      // checks that env var before the setPropsValues override above, so without this,
      // "mail.test.mode" -> "false" is silently ignored locally (CI has no such env var,
      // only a props-file default, so it isn't affected either way).
      withEnvOverride("OBP_MAIL_TEST_MODE" -> "false") {
        When("We make a request v6.0.0")
        val request600 = (v6_0_0_Request / "management" / "user" / "reset-password-url").POST <@(user1)
        val response600 = makePostRequest(request600, write(postJson.copy(user_id = resourceUser.map(_.userId).getOrElse(""))))
        Then("We should get a 500 that says the email could not be sent")
        withClue(s"Response body: ${response600.body} ") {
          response600.code should equal(500)
        }
        response600.body.extract[ErrorMessage].message should include("Failed to send password reset email")
      }
      // beforeEach restores mail.test.mode=true for the remaining scenarios
    }

    scenario("We will call the endpoint with unvalidated user", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateResetPasswordUrl.toString)
      val testUsername = "unvalidated@tesobe.com"
      val testEmail = "unvalidated@tesobe.com"
      val authUser: AuthUser = AuthUser.create.email(testEmail).username(testUsername).validated(false).saveMe()
      val resourceUser: Box[User] = Users.users.vend.getUserByResourceUserId(authUser.user.get)
      When("We make a request v6.0.0 with unvalidated user")
      val request600 = (v6_0_0_Request / "management" / "user" / "reset-password-url").POST <@(user1)
      val testJson = JSONFactory600.PostResetPasswordUrlJsonV600(testUsername, testEmail, resourceUser.map(_.userId).getOrElse(""))
      val response600 = makePostRequest(request600, write(testJson))
      Then("We should get a 400")
      response600.code should equal(400)
      And("error should indicate user validation issue")
      response600.body.extract[ErrorMessage].message should include("not validated")
      // Clean up
      authUser.delete_!
    }

    scenario("We will call the endpoint with mismatched email", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateResetPasswordUrl.toString)
      val testUsername = "mismatch@tesobe.com"
      val testEmail = "correct@tesobe.com"
      val wrongEmail = "wrong@tesobe.com"
      val authUser: AuthUser = AuthUser.create.email(testEmail).username(testUsername).validated(true).saveMe()
      val resourceUser: Box[User] = Users.users.vend.getUserByResourceUserId(authUser.user.get)
      When("We make a request v6.0.0 with mismatched email")
      val request600 = (v6_0_0_Request / "management" / "user" / "reset-password-url").POST <@(user1)
      val testJson = JSONFactory600.PostResetPasswordUrlJsonV600(testUsername, wrongEmail, resourceUser.map(_.userId).getOrElse(""))
      val response600 = makePostRequest(request600, write(testJson))
      Then("We should get a 400")
      response600.code should equal(400)
      And("error should indicate email mismatch")
      response600.body.extract[ErrorMessage].message should include("email mismatch")
      // Clean up
      authUser.delete_!
    }

    scenario("We will call the endpoint with non-existent user", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateResetPasswordUrl.toString)
      When("We make a request v6.0.0 with non-existent user")
      val request600 = (v6_0_0_Request / "management" / "user" / "reset-password-url").POST <@(user1)
      val nonExistentJson = JSONFactory600.PostResetPasswordUrlJsonV600("nonexistent@tesobe.com", "nonexistent@tesobe.com", UUID.randomUUID.toString)
      val response600 = makePostRequest(request600, write(nonExistentJson))
      Then("We should get a 400")
      response600.code should equal(400)
      And("error should indicate user not found")
      response600.body.extract[ErrorMessage].message should include("User not found")
    }
  }

  // ==========================================
  // Anonymous request endpoint: POST /users/password-reset-url
  // ==========================================

  feature("Anonymous password reset url request v6.0.0") {
    scenario("We will request a password reset for a valid user without authentication", ApiEndpoint2, VersionOfApi) {
      val testUsername = "anonreset@tesobe.com"
      val testEmail = "anonreset@tesobe.com"
      val authUser: AuthUser = AuthUser.create.email(testEmail).username(testUsername).validated(true).saveMe()
      When("We make an anonymous request to reset password")
      val request600 = (v6_0_0_Request / "users" / "password-reset-url").POST
      val anonJson = JSONFactory600.PostResetPasswordUrlAnonymousJsonV600(testUsername, testEmail)
      val response600 = makePostRequest(request600, write(anonJson))
      Then("We should get a 201")
      response600.code should equal(201)
      And("The response should contain a generic message")
      val message = (response600.body \ "message").extract[String]
      message should include("If the account exists")
      // Clean up
      authUser.delete_!
    }

    scenario("We will request a password reset for a non-existent user - should still return 201", ApiEndpoint2, VersionOfApi) {
      When("We make an anonymous request for non-existent user")
      val request600 = (v6_0_0_Request / "users" / "password-reset-url").POST
      val anonJson = JSONFactory600.PostResetPasswordUrlAnonymousJsonV600("nonexistent@tesobe.com", "nonexistent@tesobe.com")
      val response600 = makePostRequest(request600, write(anonJson))
      Then("We should get a 201 to prevent user enumeration")
      response600.code should equal(201)
      And("The response should contain the same generic message")
      val message = (response600.body \ "message").extract[String]
      message should include("If the account exists")
    }

    scenario("We will request a password reset with mismatched email - should still return 201", ApiEndpoint2, VersionOfApi) {
      val testUsername = "anonmismatch@tesobe.com"
      val testEmail = "anonmismatch@tesobe.com"
      val authUser: AuthUser = AuthUser.create.email(testEmail).username(testUsername).validated(true).saveMe()
      When("We make an anonymous request with wrong email")
      val request600 = (v6_0_0_Request / "users" / "password-reset-url").POST
      val anonJson = JSONFactory600.PostResetPasswordUrlAnonymousJsonV600(testUsername, "wrong@tesobe.com")
      val response600 = makePostRequest(request600, write(anonJson))
      Then("We should get a 201 to prevent user enumeration")
      response600.code should equal(201)
      val message = (response600.body \ "message").extract[String]
      message should include("If the account exists")
      // Clean up
      authUser.delete_!
    }

    scenario("We will request a password reset with invalid JSON", ApiEndpoint2, VersionOfApi) {
      When("We make an anonymous request with invalid JSON")
      val request600 = (v6_0_0_Request / "users" / "password-reset-url").POST
      val response600 = makePostRequest(request600, "{ invalid json }")
      Then("We should get a 400")
      response600.code should equal(400)
    }
  }

  // ==========================================
  // Complete password reset: POST /users/password
  // ==========================================

  feature("Complete password reset v6.0.0") {
    scenario("Successfully reset password with valid JWT token and strong password", ApiEndpoint3, VersionOfApi) {
      val testUsername = "complete@tesobe.com"
      val testEmail = "complete@tesobe.com"
      val authUser: AuthUser = AuthUser.create
        .email(testEmail)
        .username(testUsername)
        .password(strongPassword)
        .validated(true)
        .saveMe()
      // Set a known uniqueId and create a JWT containing it
      val resetUniqueId = UUID.randomUUID().toString.replace("-", "")
      authUser.uniqueId.set(resetUniqueId)
      authUser.save
      val jwtToken = createJwtToken(resetUniqueId)

      When("We complete the password reset with the JWT token")
      val request600 = (v6_0_0_Request / "users" / "password").POST
      val completeJson = JSONFactory600.PostResetPasswordCompleteJsonV600(jwtToken, "NewStr0ng!Pass123")
      val response600 = makePostRequest(request600, write(completeJson))
      Then("We should get a 201")
      response600.code should equal(201)
      And("The response should confirm the reset")
      val message = (response600.body \ "message").extract[String]
      message should include("Password has been reset successfully")

      And("The token should be invalidated (using the same token again should fail)")
      val response600Again = makePostRequest(request600, write(completeJson))
      response600Again.code should equal(400)

      // Clean up
      AuthUser.find(By(AuthUser.username, testUsername)).map(_.delete_!)
    }

    scenario("Fail to reset password with expired JWT token", ApiEndpoint3, VersionOfApi) {
      val testUsername = "expired@tesobe.com"
      val testEmail = "expired@tesobe.com"
      val authUser: AuthUser = AuthUser.create
        .email(testEmail)
        .username(testUsername)
        .password(strongPassword)
        .validated(true)
        .saveMe()
      val resetUniqueId = UUID.randomUUID().toString.replace("-", "")
      authUser.uniqueId.set(resetUniqueId)
      authUser.save
      val expiredToken = createExpiredJwtToken(resetUniqueId)

      When("We try to complete a password reset with an expired JWT token")
      val request600 = (v6_0_0_Request / "users" / "password").POST
      val completeJson = JSONFactory600.PostResetPasswordCompleteJsonV600(expiredToken, strongPassword)
      val response600 = makePostRequest(request600, write(completeJson))
      Then("We should get a 400")
      response600.code should equal(400)

      // Clean up
      AuthUser.find(By(AuthUser.username, testUsername)).map(_.delete_!)
    }

    scenario("Fail to reset password with invalid token", ApiEndpoint3, VersionOfApi) {
      When("We try to complete a password reset with a bogus token")
      val request600 = (v6_0_0_Request / "users" / "password").POST
      val completeJson = JSONFactory600.PostResetPasswordCompleteJsonV600("bogus_token_12345", strongPassword)
      val response600 = makePostRequest(request600, write(completeJson))
      Then("We should get a 400")
      response600.code should equal(400)
    }

    scenario("Fail to reset password with empty token", ApiEndpoint3, VersionOfApi) {
      When("We try to complete a password reset with an empty token")
      val request600 = (v6_0_0_Request / "users" / "password").POST
      val completeJson = JSONFactory600.PostResetPasswordCompleteJsonV600("", strongPassword)
      val response600 = makePostRequest(request600, write(completeJson))
      Then("We should get a 400")
      response600.code should equal(400)
    }

    scenario("Fail to reset password with weak password", ApiEndpoint3, VersionOfApi) {
      val testUsername = "weakpw@tesobe.com"
      val testEmail = "weakpw@tesobe.com"
      val authUser: AuthUser = AuthUser.create
        .email(testEmail)
        .username(testUsername)
        .password(strongPassword)
        .validated(true)
        .saveMe()
      val resetUniqueId = UUID.randomUUID().toString.replace("-", "")
      authUser.uniqueId.set(resetUniqueId)
      authUser.save
      val jwtToken = createJwtToken(resetUniqueId)

      When("We try to complete a password reset with a weak password")
      val request600 = (v6_0_0_Request / "users" / "password").POST
      val completeJson = JSONFactory600.PostResetPasswordCompleteJsonV600(jwtToken, "weak")
      val response600 = makePostRequest(request600, write(completeJson))
      Then("We should get a 400")
      response600.code should equal(400)
      And("The error should indicate invalid password format")
      response600.body.extract[ErrorMessage].message should include(InvalidStrongPasswordFormat)

      // Clean up
      AuthUser.find(By(AuthUser.username, testUsername)).map(_.delete_!)
    }

    scenario("Fail to reset password with invalid JSON", ApiEndpoint3, VersionOfApi) {
      When("We send invalid JSON")
      val request600 = (v6_0_0_Request / "users" / "password").POST
      val response600 = makePostRequest(request600, "{ invalid json }")
      Then("We should get a 400")
      response600.code should equal(400)
    }
  }

  // ==========================================
  // Full flow: request reset URL then complete reset
  // ==========================================

  feature("Full password reset flow v6.0.0") {
    scenario("Request reset URL (authenticated) then complete password reset", ApiEndpoint1, ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateResetPasswordUrl.toString)
      val testUsername = "fullflow@tesobe.com"
      val testEmail = "fullflow@tesobe.com"
      val authUser: AuthUser = AuthUser.create
        .email(testEmail)
        .username(testUsername)
        .password(strongPassword)
        .validated(true)
        .saveMe()
      val resourceUser: Box[User] = Users.users.vend.getUserByResourceUserId(authUser.user.get)

      When("We request a password reset email via the authenticated endpoint")
      val resetUrlRequest = (v6_0_0_Request / "management" / "user" / "reset-password-url").POST <@(user1)
      val resetUrlJson = JSONFactory600.PostResetPasswordUrlJsonV600(testUsername, testEmail, resourceUser.map(_.userId).getOrElse(""))
      val resetUrlResponse = makePostRequest(resetUrlRequest, write(resetUrlJson))
      Then("We should get a 201 acknowledgement (no URL leaked in the body)")
      withClue(s"Response body: ${resetUrlResponse.body} ") {
        resetUrlResponse.code should equal(201)
      }
      val ack = resetUrlResponse.body.extract[JSONFactory600.ResetPasswordEmailSentJsonV600]
      ack.status should equal("sent")
      ack.to should equal(testEmail)

      And("The endpoint rotated the user's uniqueId; we mint a matching JWT to drive the complete step")
      val rotatedAuthUser = AuthUser.find(By(AuthUser.username, testUsername)).openOrThrowException("user gone after reset request")
      val expiryMinutes = code.api.util.APIUtil.getPropsAsIntValue("password_reset_token_expiry_minutes", 120)
      val claimsSet = new com.nimbusds.jwt.JWTClaimsSet.Builder()
        .subject(rotatedAuthUser.uniqueId.get)
        .expirationTime(new java.util.Date(System.currentTimeMillis() + expiryMinutes * 60L * 1000L))
        .issueTime(new java.util.Date())
        .build()
      val token = CertificateUtil.jwtWithHmacProtection(claimsSet)
      token.length should be > 0

      When("We complete the password reset with the JWT token")
      val completeRequest = (v6_0_0_Request / "users" / "password").POST
      val newPassword = s"${ExampleValue.passwordExample.value}New"
      val completeJson = JSONFactory600.PostResetPasswordCompleteJsonV600(token, newPassword)
      val completeResponse = makePostRequest(completeRequest, write(completeJson))
      Then("We should get a 201")
      completeResponse.code should equal(201)
      val message = (completeResponse.body \ "message").extract[String]
      message should include("Password has been reset successfully")

      And("Using the same token again should fail")
      val completeResponseAgain = makePostRequest(completeRequest, write(completeJson))
      completeResponseAgain.code should equal(400)

      // Clean up
      AuthUser.find(By(AuthUser.username, testUsername)).map(_.delete_!)
    }
  }
}
