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
package code.api.v4_0_0

import java.util.UUID

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import code.entitlement.Entitlement
import code.model.dataAccess.AuthUser
import code.users.Users
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.User
import net.liftweb.common.Box
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class PasswordRecoverTest extends V400ServerSetupAsync {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.resetPasswordUrl))
  lazy val postUserId = UUID.randomUUID.toString
  lazy val postJson = PostResetPasswordUrlJsonV400("marko", "marko@tesobe.com", postUserId)

  feature("Reset password url v4.0.4- Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "user" / "reset-password-url").POST
      val response400 = makePostRequestAsync(request400, write(postJson))
      Then("We should get a 401")
      response400 map { r => r.code should equal(401) }
      And("error should be " + UserNotLoggedIn)
      response400 map { r =>
          r.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
      }
    }
  }

  feature("Reset password url v4.0.0 - Authorized access") {
    scenario("We will call the endpoint without the proper Role " + canCreateResetPasswordUrl, ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0 without a Role " + canCreateResetPasswordUrl)
      val request400 = (v4_0_0_Request / "management" / "user" / "reset-password-url").POST <@(user1)
      val response400 = makePostRequestAsync(request400, write(postJson))
      Then("We should get a 403")
      response400 map { r => r.code should equal(400) }
      And("error should be " + UserHasMissingRoles + CanCreateResetPasswordUrl)
      response400 map { r =>
        r.body.extract[ErrorMessage].message should equal((UserHasMissingRoles + CanCreateResetPasswordUrl))
      }
    }

    scenario("We will call the endpoint with the proper Role " + canCreateResetPasswordUrl , ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateResetPasswordUrl.toString)
      val authUser: AuthUser = AuthUser.create.email(postJson.email).username(postJson.username).validated(true).saveMe()
      val resourceUser: Box[User] = Users.users.vend.getUserByResourceUserId(authUser.user.get)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "user" / "reset-password-url").POST <@(user1)
      val response400 = makePostRequestAsync(request400, write(postJson.copy(user_id = resourceUser.map(_.userId).getOrElse(""))))
      Then("We should get a 201")
      response400 map { r =>
        r.code should equal(201)
        r.body.extractOpt[ResetPasswordUrlJsonV400].isDefined should equal(true)
      }
    }
    
  }
}
