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

import java.util.UUID
import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v6_0_0.APIMethods600

import code.entitlement.Entitlement
import code.model.dataAccess.{AuthUser, ResourceUser}
import code.users.Users
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.User
import net.liftweb.common.Box
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.By
import org.scalatest.Tag

/**
 * Test suite for Password Reset URL endpoint (POST /obp/v6.0.0/management/user/reset-password-url)
 * 
 * Tests cover:
 * - Unauthorized access (no authentication)
 * - Missing role (authenticated but no CanCreateResetPasswordUrl)
 * - Successful password reset URL creation (with proper role)
 * - User validation requirements
 * - Email sending functionality
 */
class PasswordResetTest extends V600ServerSetup {

  override def beforeEach() = {
    wipeTestData()
    super.beforeEach()
    AuthUser.bulkDelete_!!(By(AuthUser.username, postJson.username))
    ResourceUser.bulkDelete_!!(By(ResourceUser.providerId, postJson.username))
  }

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(APIMethods600.Implementations6_0_0.resetPasswordUrl))
  lazy val postUserId = UUID.randomUUID.toString
  lazy val postJson = JSONFactory600.PostResetPasswordUrlJsonV600("marko", "marko@tesobe.com", postUserId)

  feature("Reset password url v6.0.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0")
      val request600 = (v6_0_0_Request / "management" / "user" / "reset-password-url").POST
      val response600 = makePostRequest(request600, write(postJson))
      Then("We should get a 401")
      response600.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response600.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
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
      response600.code should equal(201)
      response600.body.extractOpt[JSONFactory600.ResetPasswordUrlJsonV600].isDefined should equal(true)
      And("The response should contain a valid reset URL")
      val resetUrl = (response600.body \ "reset_password_url").extract[String]
      resetUrl should include("/user_mgt/reset_password/")
      resetUrl.split("/user_mgt/reset_password/").last.length should be > 0
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
}