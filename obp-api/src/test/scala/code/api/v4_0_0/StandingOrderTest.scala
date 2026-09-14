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
import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.extractErrorMessageCode
import code.api.util.ApiRole.CanCreateStandingOrderAtOneBank
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages.{NoViewPermission, UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import com.github.dwickern.macros.NameOf.nameOf
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class StandingOrderTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createStandingOrder))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.createStandingOrderManagement))

  lazy val postStandingOrderJsonV400 = SwaggerDefinitionsJSON.postStandingOrderJsonV400
  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
  lazy val view = randomOwnerViewPermalinkViaEndpoint(bankId, bankAccount)

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "standing-order").POST
      val response400 = makePostRequest(request400, write(postStandingOrderJsonV400))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "standing-order").POST <@(user1)
      val response400 = makePostRequest(request400, write(postStandingOrderJsonV400))
      Then("We should get a 400")
      response400.code should equal(400)
      response400.body.extract[ErrorMessage].message  contains extractErrorMessageCode(NoViewPermission) should be (true)
    }
  }
  

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "banks" / bankId / "accounts" / bankAccount.id / "standing-order").POST
      val response400 = makePostRequest(request400, "")
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "banks" / bankId / "accounts" / bankAccount.id / "standing-order").POST <@(user1)
      val response400 = makePostRequest(request400, write(postStandingOrderJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      val errorMessage = response400.body.extract[ErrorMessage].message
      errorMessage contains (UserHasMissingRoles) should be (true)
      errorMessage contains (CanCreateStandingOrderAtOneBank.toString()) should be (true)
    }
  }
  
}
