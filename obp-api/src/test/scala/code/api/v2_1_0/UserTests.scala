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

package code.api.v2_1_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetAnyUser
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.util.{ApiRole, ErrorMessages}
import code.api.v2_0_0.JSONFactory200.UsersJsonV200
import code.entitlement.Entitlement

class UserTests extends V210ServerSetup {

  feature("Assuring that endpoint Get all Users works as expected - v2.1.0") 
  {

    scenario("We try to get all roles without credentials - Get all Users") {
      When("We make the request")
      val requestGet = (v2_1Request / "users").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 401")
      responseGet.code should equal(401)
      And("We should get a message: " + ErrorMessages.AuthenticatedUserIsRequired)
      responseGet.body.extract[ErrorMessage].message should equal (ErrorMessages.AuthenticatedUserIsRequired)

    }

    scenario("We try to get all roles with credentials but no roles- Get all Users") 
    {
      When("We make the request")
      val requestGet = (v2_1Request / "users").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(403)
      And("We should get a message: " + ErrorMessages.UserHasMissingRoles)
      responseGet.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetAnyUser)
    }
  
  
    scenario(s"We try to get all roles with credentials with ${ApiRole.canGetAnyUser} roles- Get all Users")
    {
      When(s"We first grant the ${ApiRole.canGetAnyUser} to the User1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAnyUser.toString())
      
      val requestGet = (v2_1Request / "users").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
      responseGet.body.extract[UsersJsonV200]
    }
    
  }
  
}