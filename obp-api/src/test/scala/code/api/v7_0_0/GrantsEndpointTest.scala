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

package code.api.v7_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetAllScopes, CanGetReachableRoles, CanGetTelemetry}
import code.api.util.ErrorMessages.{ApplicationNotIdentified, UserHasMissingRoles}
import code.api.v6_0_0.V600ServerSetup
import code.api.v7_0_0.JSONFactory700.{ReachableRolesJsonV700, ScopeJsonV700, ScopesJsonV700}
import code.entitlement.Entitlement
import code.scope.Scope
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * This suite checks GET /obp/v7.0.0/scopes and GET /obp/v7.0.0/reachable-roles: the Roles they need,
 * and that reachable-roles lists each Role name held as an Entitlement or a Scope once, and nothing else.
 */
class GrantsEndpointTest extends V600ServerSetup {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object GetAllScopes extends Tag("getAllScopes")
  object GetReachableRoles extends Tag("getReachableRoles")

  private def scopesRequest = v7_0_0_Request / "scopes"
  private def reachableRequest = v7_0_0_Request / "reachable-roles"

  private def withEntitlement[T](role: String)(body: => T): T = {
    val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, role)
    try body finally Entitlement.entitlement.vend.deleteEntitlement(entitlement)
  }

  private def withScope[T](role: String)(body: => T): T = {
    val scope = Scope.scope.vend.addScope("", testConsumer2.id.get.toString, role)
    try body finally Scope.scope.vend.deleteScope(scope)
  }

  feature(s"Get all Scopes - GET /obp/v7.0.0/scopes - $VersionOfApi") {

    scenario("Anonymous access fails with 401", GetAllScopes, VersionOfApi) {
      val response = makeGetRequest(scopesRequest.GET)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ApplicationNotIdentified)
    }

    scenario("A logged-in user without CanGetAllScopes gets 403", GetAllScopes, VersionOfApi) {
      val response = makeGetRequest(scopesRequest.GET <@ (user1))
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetAllScopes)
    }

    scenario("A user with CanGetAllScopes sees every Consumer's Scopes", GetAllScopes, VersionOfApi) {
      val response = withScope(CanGetTelemetry.toString) {
        withEntitlement(CanGetAllScopes.toString) { makeGetRequest(scopesRequest.GET <@ (user1)) }
      }
      response.code should equal(200)
      response.body.extract[ScopesJsonV700].scopes should contain(
        ScopeJsonV700(bank_id = "", role_name = CanGetTelemetry.toString, consumer_id = testConsumer2.id.get.toString))
    }
  }

  feature(s"Get Reachable Roles - GET /obp/v7.0.0/reachable-roles - $VersionOfApi") {

    scenario("Anonymous access fails with 401", GetReachableRoles, VersionOfApi) {
      val response = makeGetRequest(reachableRequest.GET)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ApplicationNotIdentified)
    }

    scenario("A logged-in user without CanGetReachableRoles gets 403", GetReachableRoles, VersionOfApi) {
      val response = makeGetRequest(reachableRequest.GET <@ (user1))
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetReachableRoles)
    }

    scenario("A Consumer holding it as a Scope gets the Role names held as Entitlements and Scopes, each once", GetReachableRoles, VersionOfApi) {
      Given("user1 holds CanGetTelemetry as an Entitlement, and testConsumer2 holds it and CanGetReachableRoles as Scopes")
      val response = withEntitlement(CanGetTelemetry.toString) {
        withScope(CanGetTelemetry.toString) {
          withScope(CanGetReachableRoles.toString) { makeGetRequest(reachableRequest.GET <@ (user2)) }
        }
      }

      Then("both Role names are listed, once each, and nothing about who holds them")
      response.code should equal(200)
      val roleNames = response.body.extract[ReachableRolesJsonV700].role_names
      roleNames should contain(CanGetTelemetry.toString)
      roleNames should contain(CanGetReachableRoles.toString)
      roleNames.count(_ == CanGetTelemetry.toString) should equal(1)
      roleNames should equal(roleNames.sorted)
      response.body.values.asInstanceOf[Map[String, Any]].keySet should equal(Set("role_names"))
    }
  }
}
