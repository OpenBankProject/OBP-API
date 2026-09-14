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

import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * Unit tests for EndpointAuthMode sealed trait and its integration with ResourceDoc.
 * Tests that the auth mode values are correctly defined and accessible.
 */
class EndpointAuthModeTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)

  feature("EndpointAuthMode sealed trait") {

    scenario("All four auth modes should be defined", VersionOfApi) {
      val userOnly: EndpointAuthMode = UserOnly
      val appOnly: EndpointAuthMode = ApplicationOnly
      val userOrApp: EndpointAuthMode = UserOrApplication
      val userAndApp: EndpointAuthMode = UserAndApplication

      userOnly shouldBe a[EndpointAuthMode]
      appOnly shouldBe a[EndpointAuthMode]
      userOrApp shouldBe a[EndpointAuthMode]
      userAndApp shouldBe a[EndpointAuthMode]
    }

    scenario("verifyUserCredentials ResourceDoc should have UserOrApplication authMode", VersionOfApi) {
      val operationId = buildOperationId(ApiVersion.v6_0_0, "verifyUserCredentials")
      val docs = ResourceDoc.getResourceDocs(List(operationId))

      docs should not be empty
      docs.foreach { doc =>
        doc.authMode should equal(UserOrApplication)
        doc.errorResponseBodies should contain(ApplicationNotIdentified)
      }
    }

    scenario("Default authMode should be UserOnly for existing endpoints", VersionOfApi) {
      val operationId = buildOperationId(ApiVersion.v6_0_0, "root")
      val docs = ResourceDoc.getResourceDocs(List(operationId))

      docs should not be empty
      docs.foreach { doc =>
        doc.authMode should equal(UserOnly)
      }
    }

    scenario("handleAccessControlWithAuthMode should pass for empty roles", VersionOfApi) {
      val result = handleAccessControlWithAuthMode("", "", "", Nil, UserOnly)
      result should equal(true)
    }
  }
}
