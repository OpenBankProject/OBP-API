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

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetConfigProps, CanGetConnectorTrace}
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s._
import org.scalatest.Tag

/**
 * This suite checks that the Connector Traces and Config Props endpoints require their Roles.
 *
 * Both Roles were lost when the endpoints moved from Lift to http4s, which let any logged-in
 * user read every connector message (with customer and account data) and every configuration
 * value. Each endpoint gets the standard three scenarios: no login, login without the Role,
 * and login with the Role.
 */
class ConnectorTraceAndConfigPropsRoleTest extends V600ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object GetConnectorTracesEndpoint extends Tag(nameOf(Implementations6_0_0.getConnectorTraces))
  object GetConfigPropsEndpoint extends Tag(nameOf(Implementations6_0_0.getConfigProps))

  private def connectorTracesRequest = v6_0_0_Request / "management" / "connector" / "traces"
  private def configPropsRequest = v6_0_0_Request / "management" / "config-props"

  feature(s"Get Connector Traces - GET /obp/v6.0.0/management/connector/traces - $VersionOfApi") {

    scenario("Anonymous access fails with 401", GetConnectorTracesEndpoint, VersionOfApi) {
      val response = makeGetRequest(connectorTracesRequest.GET)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("A logged-in user without CanGetConnectorTrace gets 403", GetConnectorTracesEndpoint, VersionOfApi) {
      val response = makeGetRequest(connectorTracesRequest.GET <@ (user1))
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetConnectorTrace)
    }

    scenario("A user with CanGetConnectorTrace gets 200", GetConnectorTracesEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetConnectorTrace.toString)
      val response = try makeGetRequest(connectorTracesRequest.GET <@ (user1))
      finally Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)

      response.code should equal(200)
      response.body \ "connector_traces" shouldBe a[JArray]
    }
  }

  feature(s"Get Config Props - GET /obp/v6.0.0/management/config-props - $VersionOfApi") {

    scenario("Anonymous access fails with 401", GetConfigPropsEndpoint, VersionOfApi) {
      val response = makeGetRequest(configPropsRequest.GET)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("A logged-in user without CanGetConfigProps gets 403", GetConfigPropsEndpoint, VersionOfApi) {
      val response = makeGetRequest(configPropsRequest.GET <@ (user1))
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetConfigProps)
    }

    scenario("A user with CanGetConfigProps gets 200", GetConfigPropsEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetConfigProps.toString)
      val response = try makeGetRequest(configPropsRequest.GET <@ (user1))
      finally Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)

      response.code should equal(200)
      response.body \ "config_props" shouldBe a[JArray]
    }
  }
}
