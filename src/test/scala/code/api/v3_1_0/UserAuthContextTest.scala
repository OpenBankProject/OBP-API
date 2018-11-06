/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v3_1_0

import code.api.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class UserAuthContextTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createUserAuthContext))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getUserAuthContexts))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.deleteUserAuthContexts))
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_1_0.deleteUserAuthContextById))

  val postUserAuthContextJson = SwaggerDefinitionsJSON.postUserAuthContextJson
  val postUserAuthContextJson2 = SwaggerDefinitionsJSON.postUserAuthContextJson.copy(key="TOKEN")

  feature("Add/Get/Delete User Auth Context v3.1.0") {
    scenario("We will call the Add endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "users" / userId.value / "auth-context").POST
      val response310 = makePostRequest(request310, write(postUserAuthContextJson))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
    scenario("We will call the Add endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "users" / userId.value / "auth-context").POST <@(user1)
      val response310 = makePostRequest(request310, write(postUserAuthContextJson))
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateUserAuthContext)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanCreateUserAuthContext)
    }

    scenario("We will call the Get endpoint without a user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "users" / userId.value / "auth-context").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
    scenario("We will call the Get endpoint without a proper role", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "users" / userId.value / "auth-context").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetUserAuthContext)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanGetUserAuthContext)
    }

    scenario("We will call the deleteUserAuthContexts endpoint without a user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "users" / userId.value / "auth-context").DELETE
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
    scenario("We will call the deleteUserAuthContexts endpoint without a proper role", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "users" / userId.value / "auth-context").DELETE <@(user1)
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanDeleteUserAuthContext)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanDeleteUserAuthContext)
    }

    scenario("We will call the deleteUserAuthContextById endpoint without a user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "users" / userId.value / "auth-context"/ "userAuthContextId").DELETE
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
    scenario("We will call the deleteUserAuthContextById endpoint without a proper role", ApiEndpoint4, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "users" / userId.value / "auth-context" / "userAuthContextId").DELETE <@(user1)
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanDeleteUserAuthContext)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanDeleteUserAuthContext)
    }

    scenario("We will call the Add, Get and Delete endpoints with user credentials and role", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      When("We try to create the UserAuthContext v3.1.0")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateUserAuthContext.toString)
      val requestUserAuthContext310 = (v3_1_0_Request / "users" / userId.value / "auth-context").POST <@(user1)
      val responseUserAuthContext310 = makePostRequest(requestUserAuthContext310, write(postUserAuthContextJson))
      Then("We should get a 200")
      responseUserAuthContext310.code should equal(200)
      val customerJson = responseUserAuthContext310.body.extract[UserAuthContextJson]

      When("We try to create the UserAuthContext v3.1.0")
      val successReq = (v3_1_0_Request / "users" / userId.value / "auth-context").POST <@(user1)
      val successRes = makePostRequest(successReq, write(postUserAuthContextJson2))
      Then("We should get a 200")
      successRes.code should equal(200)
      successRes.body.extract[UserAuthContextJson]

      When("We try to make the GET request v3.1.0")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetUserAuthContext.toString)
      val successGetReq = (v3_1_0_Request / "users" / userId.value / "auth-context").GET <@(user1)
      val successGetRes = makeGetRequest(successGetReq)
      Then("We should get a 200")
      successGetRes.code should equal(200)
      val userAuthContexts = successGetRes.body.extract[UserAuthContextsJson]
      userAuthContexts.user_auth_contexts.map(_.user_id).forall(userId.value ==) shouldBe (true)

      When("We try to make the DELETE request v3.1.0")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteUserAuthContext.toString)
      val existsUserAuthContextId = userAuthContexts.user_auth_contexts.head.user_auth_context_id
      val successDeleteReq = (v3_1_0_Request / "users" / userId.value / "auth-context" / existsUserAuthContextId).DELETE <@(user1)
      val successDeleteRes = makeDeleteRequest(successDeleteReq)
      Then("We should get a 200")
      successDeleteRes.code should equal(200)

      When("We try to make the DELETE request v3.1.0")
      makePostRequest(requestUserAuthContext310, write(postUserAuthContextJson)) // add a new UserAuthContext to do delete
      val successDeleteUserAuthContextsReq = (v3_1_0_Request / "users" / userId.value / "auth-context").DELETE <@(user1)
      val successDeleteUserAuthContextsRes = makeDeleteRequest(successDeleteUserAuthContextsReq)
      Then("We should get a 200")
      successDeleteUserAuthContextsRes.code should equal(200)
    }
  }
}
