/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.v5_1_0

import code.api.Constant.localIdentityProvider
import code.api.util.APIUtil.OAuth
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanLockUser, CanReadUserLockedStatus, CanUnlockUser}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotFoundByProviderAndUsername, AuthenticatedUserIsRequired, UsernameHasBeenLocked}
import code.api.v3_0_0.UserJsonV300
import code.api.v3_1_0.BadLoginStatusJson
import code.api.v5_1_0.Http4s510.Implementations5_1_0
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class LockUserTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.lockUserByProviderAndUsername))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.getUserLockStatus))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_1_0.unlockUserByProviderAndUsername))
  

  feature(s"test $ApiEndpoint1,$ApiEndpoint2, $ApiEndpoint3, version $VersionOfApi - Unauthorized access") {
    scenario(s"We will call the $ApiEndpoint1 without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "users"/"PROVIDER" / "USERNAME" / "locks").POST
      val response = makePostRequest(request, "")
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
    scenario(s"We will call the $ApiEndpoint2 without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "users" / "PROVIDER" / "USERNAME" / "lock-status").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
    scenario(s"We will call the $ApiEndpoint3 without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "users" / "PROVIDER" / "USERNAME" / "lock-status").PUT
      val response = makePutRequest(request, "")
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }

  feature(s"test $ApiEndpoint1,$ApiEndpoint2, $ApiEndpoint3, version $VersionOfApi - Missing roles") {
    scenario(s"We will call the $ApiEndpoint1 without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request /"users" /"PROVIDER" / "USERNAME" / "locks").POST <@(user1)
      val response = makePostRequest(request, "")
      Then("error should be " + UserHasMissingRoles + CanLockUser)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanLockUser)
    }
    scenario(s"We will call the $ApiEndpoint2 without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "users" /"PROVIDER" / "USERNAME" / "lock-status").GET <@(user1)
      val response = makeGetRequest(request)
      Then("error should be " + UserHasMissingRoles + CanReadUserLockedStatus)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanReadUserLockedStatus)
    }
    scenario(s"We will call the $ApiEndpoint3 without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "users" /"PROVIDER" / "USERNAME" / "lock-status").PUT <@(user1)
      val response = makePutRequest(request, "")
      Then("error should be " + UserHasMissingRoles + CanUnlockUser)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanUnlockUser)
    }
  }

  feature(s"test $ApiEndpoint1,$ApiEndpoint2, $ApiEndpoint3,  version $VersionOfApi - Wrong username") {
    scenario(s"We will call the $ApiEndpoint1 without user credentials", ApiEndpoint1, VersionOfApi) {
      val username = "USERNAME"
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanLockUser.toString)
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request /"users" / localIdentityProvider / username / "locks").POST <@(user1)
      val response = makePostRequest(request, "")
      Then("We should get a 404")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message contains s"$UserNotFoundByProviderAndUsername" shouldBe(true)
    }
    scenario(s"We will call the $ApiEndpoint2 without user credentials", ApiEndpoint2, VersionOfApi) {
      val username = "USERNAME"
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadUserLockedStatus.toString)
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request /"users" / localIdentityProvider / username / "lock-status").GET <@(user1)
      val response = makeGetRequest(request)
      Then("We should get a 404")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message contains s"$UserNotFoundByProviderAndUsername" shouldBe(true)
    }
    scenario(s"We will call the $ApiEndpoint3 without user credentials", ApiEndpoint3, VersionOfApi) {
      val username = "USERNAME"
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUnlockUser.toString)
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request /"users" / localIdentityProvider / username / "lock-status").PUT <@(user1)
      val response = makePutRequest(request, "")
      Then("We should get a 404")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message contains s"$UserNotFoundByProviderAndUsername" shouldBe(true)
    }
  }
  
  feature(s"test $ApiEndpoint1,$ApiEndpoint2, $ApiEndpoint3,  version $VersionOfApi - Proper values") {

    val resource2Username = resourceUser2.name 
    val resource2Provider = resourceUser2.provider 
    scenario(s"We will call the $ApiEndpoint1 without user credentials", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanLockUser.toString)
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request /"users" / resource2Provider / resource2Username / "locks").POST <@(user1)
      val response = makePostRequest(request, "")
      Then("We should get a 200")
      response.code should equal(200)

      {
        Then("We try endpoint with user2")
        val request = (v5_1_0_Request / "users" / "current").GET <@ (user2)
        val response = makeGetRequest(request)
        Then("We should get a 401")
        response.body.extract[ErrorMessage].message contains s"$UsernameHasBeenLocked" shouldBe (true)
        response.code should equal (401)
        response
      }
    }
    scenario(s"we fake failed login 10 times, cause lock the user, and check login status and unlock it ", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadUserLockedStatus.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUnlockUser.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanLockUser.toString)

      // Lock the user in order to test functionality
      for (i <- 1 to 10)  LoginAttempt.incrementBadLoginAttempts(resource2Provider, resource2Username)
      
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "users" / "current").GET <@ (user2)
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message contains s"$UsernameHasBeenLocked" shouldBe(true)
      
      {
        Then("We make check the lock status")
        val request = (v5_1_0_Request / "users" / resource2Provider / resource2Username / "lock-status").GET <@ (user1)
        val response = makeGetRequest(request)
        Then("We should get a 200")
        response.code should equal(200)
        response.body.extract[BadLoginStatusJson].bad_attempts_since_last_success_or_reset shouldBe (10)
      }

      {
        Then("We unlock the user")
        val request = (v5_1_0_Request / "users" / resource2Provider / resource2Username / "lock-status").PUT <@ (user1)
        val response = makePutRequest(request, "")
        Then("We should get a 200")
        response.code should equal(200)
      }

      {
        Then("We try endpoint with user2")
        val request = (v5_1_0_Request / "users" / "current").GET <@ (user2)
        val response = makeGetRequest(request)
        Then("We should get a 200")
        response.code should equal(200)
        response.body.extract[UserJsonV300].username shouldBe(resource2Username)
      }
      
    }
    
  }
  
}