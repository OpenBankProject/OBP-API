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

package code.api.v4_0_0

import org.json4s._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.{CanCreateUserInvitation, CanGetUserInvitation}
import code.api.util.ErrorMessages.{CannotGetUserInvitation, UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.entitlement.Entitlement
import code.users.UserInvitationProvider
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class UserInvitationApiTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createUserInvitation))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getUserInvitationAnonymous))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getUserInvitation))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getUserInvitations))


  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitation").POST
      val postJson = SwaggerDefinitionsJSON.userInvitationPostJsonV400
      val response400 = makePostRequest(request400, write(postJson))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitation").POST <@(user1)
      val postJson = SwaggerDefinitionsJSON.userInvitationPostJsonV400
      val response400 = makePostRequest(request400, write(postJson))
      Then("error should be " + UserHasMissingRoles + CanCreateUserInvitation)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanCreateUserInvitation)
    }
  }
  feature(s"test $ApiEndpoint1 and $ApiEndpoint4 version $VersionOfApi - Successful response") {
    scenario("We will call the endpoint with required entitlements", ApiEndpoint1, ApiEndpoint4, VersionOfApi) {
      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanCreateUserInvitation.toString)
      Then("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitation").POST <@(user1)
      val postJson = SwaggerDefinitionsJSON.userInvitationPostJsonV400
      val response400 = makePostRequest(request400, write(postJson))
      Then("We get successful response")
      response400.code should equal(201)
      val userInvitation = response400.body.extract[UserInvitationJsonV400]

      When("We add required entitlement for getting invitations")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanGetUserInvitation.toString)
      Then(s"We make a request $ApiEndpoint4")
      val request = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We get successful response")
      response.code should equal(200)
      val userInvitations = response.body.extract[UserInvitationsJsonV400]
      userInvitations.user_invitations.exists(i => i.email == userInvitation.email) should equal(true)
    }
  }


  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations").POST <@(user1)
      val postJson = PostUserInvitationAnonymousJsonV400(secret_key = 0L)
      val response400 = makePostRequest(request400, write(postJson))
      Then("error should be " + CannotGetUserInvitation)
      response400.code should equal(404)
      response400.body.extract[ErrorMessage].message should be(CannotGetUserInvitation)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations" / "secret-link").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }
  feature(s"test $ApiEndpoint3 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations" / "secret-link").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("error should be " + UserHasMissingRoles + CanGetUserInvitation)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanGetUserInvitation)
    }
  }

  feature(s"test $ApiEndpoint4 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }
  feature(s"test $ApiEndpoint4 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("error should be " + UserHasMissingRoles + CanGetUserInvitation)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanGetUserInvitation)
    }
  }
  
  
}
