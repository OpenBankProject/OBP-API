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

package code.api.v6_0_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{
  CanCreateGroupAtAllBanks,
  CanGetEntitlementsForAnyBank
}
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class GroupEntitlementsTest extends V600ServerSetup with DefaultUsers {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  /** Test tags Example: To run tests with tag "getGroupEntitlements": mvn test
    * -D tagsToInclude
    *
    * This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1
      extends Tag(nameOf(Implementations6_0_0.getGroupEntitlements))

  feature(
    s"Assuring that endpoint getGroupEntitlements works as expected - $VersionOfApi"
  ) {

    scenario(
      "We try to consume endpoint getGroupEntitlements - Anonymous access",
      ApiEndpoint1,
      VersionOfApi
    ) {
      When("We make the request")
      val request =
        (v6_0_0_Request / "management" / "groups" / "test-group-id" / "entitlements").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.AuthenticatedUserIsRequired)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(
        ErrorMessages.AuthenticatedUserIsRequired
      )
    }

    scenario(
      "We try to consume endpoint getGroupEntitlements without proper role - Authorized access",
      ApiEndpoint1,
      VersionOfApi
    ) {
      When("We make the request")
      val request =
        (v6_0_0_Request / "management" / "groups" / "test-group-id" / "entitlements").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403")
      And(
        "We should get a message: " + s"$CanGetEntitlementsForAnyBank entitlement required"
      )
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(
        UserHasMissingRoles + CanGetEntitlementsForAnyBank
      )
    }

    scenario(
      "We try to consume endpoint getGroupEntitlements with proper role - Authorized access",
      ApiEndpoint1,
      VersionOfApi
    ) {
      When("We add the required entitlement")
      Entitlement.entitlement.vend.addEntitlement(
        "",
        resourceUser1.userId,
        CanGetEntitlementsForAnyBank.toString
      )
      And("We make the request")
      val request =
        (v6_0_0_Request / "management" / "groups" / "test-group-id" / "entitlements").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 404 because the group doesn't exist")
      response.code should equal(404)
    }
  }

}
