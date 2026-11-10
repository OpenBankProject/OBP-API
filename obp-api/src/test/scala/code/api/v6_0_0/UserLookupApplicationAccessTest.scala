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
import code.api.util.ApiRole.CanGetAnyUser
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.scope.Scope
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * The user lookups an identity provider needs (by provider and username, by user id, and the list) admit an
 * application acting as itself on its Consumer's Scope CanGetAnyUser, as well as a person with the Entitlement.
 * The application's Consumer must be a Platform App. A person calling through that Consumer does not get its Scope.
 */
class UserLookupApplicationAccessTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag("getUserByProviderAndUsername")
  object ApiEndpoint2 extends Tag("getUserByUserId")
  object ApiEndpoint3 extends Tag("getUsers")

  private def byUsername = (v6_0_0_Request / "users" / "provider" / resourceUser1.provider / "username" / resourceUser1.name).GET
  private def byUserId = (v6_0_0_Request / "users" / "user-id" / resourceUser1.userId).GET
  private def list = (v6_0_0_Request / "users").GET
  private val lookups = List("by provider and username" -> (() => byUsername), "by user id" -> (() => byUserId), "list" -> (() => list))

  /** Runs `f` while testConsumer2 (application2's Consumer) holds the Scope CanGetAnyUser. */
  private def withScope(f: => Unit): Unit = {
    val scope = Scope.scope.vend.addScope("", testConsumer2.id.get.toString, CanGetAnyUser.toString)
    try f finally Scope.scope.vend.deleteScope(scope)
  }

  feature("User lookups admit an application acting as itself") {

    scenario("an application with the Scope CanGetAnyUser looks users up", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      withScope {
        lookups.foreach { case (name, request) =>
          withClue(name) { makeGetRequest(request() <@ (application2)).code should equal(200) }
        }
      }
    }

    scenario("an application without the Scope is refused", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      lookups.foreach { case (name, request) =>
        withClue(name) {
          val response = makeGetRequest(request() <@ (application2))
          response.code should equal(403)
          response.body.extract[ErrorMessage].message should include(UserHasMissingRoles)
        }
      }
    }

    scenario("an application whose Consumer is not a Platform App does not get its Scope", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      withScope {
        makeGetRequest(byUserId <@ (application2)).code should equal(200) // fixtures mark it as a Platform App
        val consumerId = testConsumer2.consumerId.get
        code.platformapp.PlatformApps.platformAppProvider.vend.deletePlatformApp(consumerId)
        try {
          // The token itself, without application2, which would mark the Consumer again
          val notMarked = Some((consumer2, tokenApplication2))
          lookups.foreach { case (name, request) =>
            withClue(name) {
              val response = makeGetRequest(request() <@ (notMarked))
              response.code should equal(403)
              response.body.extract[ErrorMessage].message should include(UserHasMissingRoles)
            }
          }
        } finally ensurePlatformApp(testConsumer2)
      }
    }

    scenario("a person calling through the Consumer does not get its Scope", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      withScope {
        lookups.foreach { case (name, request) =>
          withClue(name) {
            val response = makeGetRequest(request() <@ (user2))
            response.code should equal(403)
            response.body.extract[ErrorMessage].message should include(UserHasMissingRoles)
          }
        }
      }
    }
  }
}
