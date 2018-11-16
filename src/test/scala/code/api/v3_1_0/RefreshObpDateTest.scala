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
import code.api.util.ApiRole.CanRefreshUser
import code.api.util.ApiVersion
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag
import code.api.util.APIUtil.OAuth._

class RefreshUserTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.refreshUser))
  feature(nameOf(Implementations3_1_0.refreshUser))
  {
    scenario(s"The user missing the $CanRefreshUser role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val userId = resourceUser1.userId
      val request310 = (v3_1_0_Request / "users" / userId /"refresh").POST <@(user1)
      val response310 = makePostRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanRefreshUser)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanRefreshUser)
    }
    
    
    scenario(s"Test the success case ", ApiEndpoint1, VersionOfApi) {
      When("")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanRefreshUser.toString)
      When("We make a request v3.1.0")
      val userId = resourceUser1.userId
      val request310 = (v3_1_0_Request / "users" / userId /"refresh").POST <@(user1)
      val response310 = makePostRequest(request310)
      Then("We should get a 201")
      response310.code should equal(201)
      response310.body.extract[RefreshUserJson]
    }
    
  }
}
