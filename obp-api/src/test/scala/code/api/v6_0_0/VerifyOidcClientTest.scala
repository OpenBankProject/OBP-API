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
import code.api.util.ApiRole.CanVerifyOidcClient
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

class VerifyOidcClientTest extends V600ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations6_0_0.verifyOidcClient))

  feature(s"Verify OIDC Client - POST /obp/v6.0.0/oidc/clients/verify - $VersionOfApi") {

    scenario("Anonymous access should fail with 401", ApiEndpoint, VersionOfApi) {
      When("We make the request without authentication")
      val postJson = Map(
        "client_id" -> "nonexistent_client_id",
        "client_secret" -> "some_secret"
      )
      val request = (v6_0_0_Request / "oidc" / "clients" / "verify").POST
      val response = makePostRequest(request, write(postJson))

      Then("We should get a 401")
      response.code should equal(401)
      And("The error message should indicate authentication is required")
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.ApplicationNotIdentified)
    }

    scenario("Authenticated user without role should fail with 403", ApiEndpoint, VersionOfApi) {
      When("We make the request as an authenticated user without the required role")
      val postJson = Map(
        "client_id" -> "nonexistent_client_id",
        "client_secret" -> "some_secret"
      )
      val request = (v6_0_0_Request / "oidc" / "clients" / "verify").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))

      Then("We should get a 403")
      response.code should equal(403)
      And("The error message should indicate missing role")
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanVerifyOidcClient)
    }

    scenario("Authenticated user with CanVerifyOidcClient role but invalid client should fail with 404", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyOidcClient.toString)

      When("We verify a non-existent client")
      val postJson = Map(
        "client_id" -> "nonexistent_client_id",
        "client_secret" -> "some_secret"
      )
      val request = (v6_0_0_Request / "oidc" / "clients" / "verify").POST <@ (user1)
      val response = try {
        makePostRequest(request, write(postJson))
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should not get a 401 or 403 (role check passed)")
      response.code should not equal(401)
      response.code should not equal(403)
    }

  }
}
