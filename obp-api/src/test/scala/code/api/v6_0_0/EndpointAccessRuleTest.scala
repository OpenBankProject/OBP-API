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
import code.api.util.ApiRole
import code.api.util.ErrorMessages._
import code.entitlement.Entitlement
import code.platformapp.PlatformApps
import code.scope.Scope
import code.users.Users
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * Who may call an endpoint, and where its Roles come from.
 *
 * - The error list says who may call: "User not logged in" or Roles means a user is required. "The application
 *   cannot be identified" without those means an identified application is required, and a user is optional.
 * - A person needs a Role as an Entitlement. An application acting as itself (its Consumer is a Platform App, and
 *   it calls as its own client-credentials user) needs it as a Scope.
 */
class EndpointAccessRuleTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)

  private val role = ApiRole.canGetAnyUser
  private def ownUserId(c: code.model.Consumer): String = {
    tokenApplication2 // creates the Consumer's own client-credentials user
    Users.users.vend.getUserByProviderId(code.api.OAuth2Login.OBPOIDC.clientCredentialsProvider, c.key.get)
      .map(_.userId).openOrThrowException("the application's own user should exist")
  }
  private def consumerPk(c: code.model.Consumer): String = c.id.get.toString
  private def allowed(userId: String, c: code.model.Consumer): Boolean =
    handleAccessControlRegardingEntitlementsAndScopes("", userId, consumerPk(c), List(role))

  feature("The error list says who may call") {

    scenario("a consent endpoint needs an identified application but no user", VersionOfApi) {
      val docs = ResourceDoc.getResourceDocs(List(buildOperationId(code.api.berlin.group.ConstantsBG.berlinGroupVersion1, "createConsent")))
      docs should not be empty
      docs.foreach { doc =>
        doc.requiresApplicationButNotUser shouldBe true
        doc.errorResponseBodies should contain(ApplicationNotIdentified)
        doc.errorResponseBodies should not contain AuthenticatedUserIsRequired
      }
    }

    scenario("an endpoint with Roles needs a user", VersionOfApi) {
      val docs = ResourceDoc.getResourceDocs(List(buildOperationId(ApiVersion.v6_0_0, "verifyUserCredentials")))
      docs should not be empty
      docs.foreach { doc =>
        doc.requiresApplicationButNotUser shouldBe false
        doc.errorResponseBodies should contain(AuthenticatedUserIsRequired)
      }
    }

    scenario("a public endpoint needs neither", VersionOfApi) {
      val docs = ResourceDoc.getResourceDocs(List(buildOperationId(ApiVersion.v6_0_0, "root")))
      docs should not be empty
      docs.foreach(_.requiresApplicationButNotUser shouldBe false)
    }
  }

  feature("Where a Role comes from") {

    scenario("no Role needed: always allowed", VersionOfApi) {
      handleAccessControlRegardingEntitlementsAndScopes("", "", "", Nil) shouldBe true
    }

    scenario("a Platform App acting as itself is allowed on its Scope, and only on its Scope", VersionOfApi) {
      ensurePlatformApp(testConsumer2)
      val appUser = ownUserId(testConsumer2)
      allowed(appUser, testConsumer2) shouldBe false
      Scope.scope.vend.addScope("", consumerPk(testConsumer2), role.toString)
      allowed(appUser, testConsumer2) shouldBe true
    }

    scenario("a Consumer that is not a Platform App does not get its Scope", VersionOfApi) {
      val appUser = ownUserId(testConsumer2)
      PlatformApps.platformAppProvider.vend.deletePlatformApp(testConsumer2.consumerId.get)
      Scope.scope.vend.addScope("", consumerPk(testConsumer2), role.toString)
      allowed(appUser, testConsumer2) shouldBe false
      ensurePlatformApp(testConsumer2)
    }

    scenario("a person needs their own Entitlement, not the Scope of the Consumer they call through", VersionOfApi) {
      ensurePlatformApp(testConsumer2)
      Scope.scope.vend.addScope("", consumerPk(testConsumer2), role.toString)
      allowed(resourceUser2.userId, testConsumer2) shouldBe false
      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, role.toString)
      allowed(resourceUser2.userId, testConsumer2) shouldBe true
    }
  }
}
