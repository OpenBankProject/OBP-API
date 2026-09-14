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
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import code.setup.DefaultUsers
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class V6EntitlementCascadeTest extends V600ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)

  feature(s"POST /users/USER_ID/entitlements must reach a handler at $VersionOfApi via the bridge cascade") {

    scenario("Unauthenticated POST /obp/v6.0.0/users/USER_ID/entitlements must NOT 404", VersionOfApi) {
      When("We POST without credentials to the v6.0.0 path")
      val requestPost =
        (v6_0_0_Request / "users" / resourceUser1.userId / "entitlements").POST
      val body = write(SwaggerDefinitionsJSON.createEntitlementJSON)
      val response = makePostRequest(requestPost, body)

      Then("We should NOT get 404 — addEntitlement (v2.0.0) must be reachable via cascade")
      info(s"Status: ${response.code}; body: ${response.body}")
      response.code should not equal 404
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("Unauthenticated GET /obp/v6.0.0/users/USER_ID/entitlements must NOT 404", VersionOfApi) {
      When("We GET without credentials")
      val requestGet =
        (v6_0_0_Request / "users" / resourceUser1.userId / "entitlements").GET
      val response = makeGetRequest(requestGet)

      Then("We should NOT get 404 — getEntitlements (v4.0.0 override / v2.0.0) must be reachable via cascade")
      info(s"Status: ${response.code}; body: ${response.body}")
      response.code should not equal 404
      response.code should equal(401)
    }
  }
}
