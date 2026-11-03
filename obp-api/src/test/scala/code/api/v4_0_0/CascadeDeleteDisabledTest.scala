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

import code.api.util.ErrorMessages.InvalidUri
import code.api.util.APIUtil.OAuth._
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JString
import org.scalatest.Tag

// Boots its own JVM with allow_cascade_delete false, via OBP_ALLOW_CASCADE_DELETE=false (see
// run_tests_parallel.sh and CI's shard 10). Whether the cascade-delete endpoints exist is decided
// when the API objects initialise, so a JVM sees only one value of the prop; every other shard
// runs with it true, and CascadeDeleteEndpointsTest covers that case.
class CascadeDeleteDisabledTest extends V400ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object PropGatedPublicEndpoint extends Tag("PropGatedPublicEndpoint")

  private def v6_0_0_Request = baseRequest / "obp" / "v6.0.0"
  private def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"

  feature("allow_cascade_delete=false") {
    scenario("The prop is off in this JVM", PropGatedPublicEndpoint, VersionOfApi) {
      CascadeDeleteEndpoints.enabled should be(false)
    }

    scenario("No cascade-delete endpoint is routed or listed in the Resource Docs", PropGatedPublicEndpoint, VersionOfApi) {
      CascadeDeleteEndpoints.found.foreach { case (where, names) =>
        withClue(where) { names should be(empty) }
      }
    }

    scenario("A request to any cascade-delete path gets 404", PropGatedPublicEndpoint, VersionOfApi) {
      val bankId = testBankId1.value
      val requests = List(
        v4_0_0_Request / "management" / "cascading" / "banks" / bankId / "accounts" / "ACCOUNT_ID" / "transactions" / "TRANSACTION_ID",
        v4_0_0_Request / "management" / "cascading" / "banks" / bankId / "accounts" / "ACCOUNT_ID",
        v4_0_0_Request / "management" / "cascading" / "banks" / bankId,
        v4_0_0_Request / "management" / "cascading" / "banks" / bankId / "products" / "PRODUCT_CODE",
        v4_0_0_Request / "management" / "cascading" / "banks" / bankId / "customers" / "CUSTOMER_ID",
        v6_0_0_Request / "management" / "system-dynamic-entities" / "cascade" / "DYNAMIC_ENTITY_ID",
        v7_0_0_Request / "management" / "banks" / bankId / "dynamic-entities" / "cascade" / "DYNAMIC_ENTITY_ID",
        // v7.0.0 hands paths it has no route for down to the older versions.
        v7_0_0_Request / "management" / "cascading" / "banks" / bankId
      )
      requests.foreach { request =>
        val response = makeDeleteRequest(request <@ (user1))
        withClue(request.baseUrl) {
          response.code should equal(404)
          (response.body \ "message") match {
            case JString(message) => message should startWith(InvalidUri)
            case other => fail(s"no message in $other")
          }
        }
      }
    }
  }
}
