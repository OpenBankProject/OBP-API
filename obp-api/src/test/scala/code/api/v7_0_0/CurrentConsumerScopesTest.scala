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

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.canGetDynamicEntityDefinitions
import code.api.util.ErrorMessages.ApplicationNotIdentified
import code.api.v6_0_0.V600ServerSetup
import code.api.v7_0_0.JSONFactory700.{CurrentConsumerScopeJsonV700, CurrentConsumerScopesJsonV700}
import code.scope.Scope
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/** GET /obp/v7.0.0/consumers/current/scopes: the caller's own Consumer's Scopes, no Role. */
class CurrentConsumerScopesTest extends V600ServerSetup {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("getCurrentConsumerScopes")

  private def scopesPath = v7_0_0_Request / "consumers" / "current" / "scopes"

  feature(s"test $ApiEndpoint1 version $VersionOfApi") {

    scenario("Without any credentials the application cannot be identified", ApiEndpoint1, VersionOfApi) {
      val response = makeGetRequest(scopesPath.GET)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ApplicationNotIdentified)
    }

    scenario("A caller sees the Scopes of the Consumer they called with, and only those", ApiEndpoint1, VersionOfApi) {
      Given("testConsumer2 holds a Scope at SYS")
      val granted = Scope.scope.vend.addScope(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, testConsumer2.id.get.toString,
        canGetDynamicEntityDefinitions.toString)
      try {
        When("user2, who signs with testConsumer2, asks")
        val response = makeGetRequest(scopesPath.GET <@ (user2))
        Then("the Scope is listed, with its bank id")
        response.code should equal(200)
        val body = response.body.extract[CurrentConsumerScopesJsonV700]
        body.consumer_id should equal(testConsumer2.consumerId.get)
        body.scopes should contain(CurrentConsumerScopeJsonV700(canGetDynamicEntityDefinitions.toString, DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID))

        When("user1, who signs with testConsumer, asks")
        val other = makeGetRequest(scopesPath.GET <@ (user1))
        Then("testConsumer2's Scope is not among testConsumer's")
        other.code should equal(200)
        val otherBody = other.body.extract[CurrentConsumerScopesJsonV700]
        otherBody.consumer_id should equal(testConsumer.consumerId.get)
        otherBody.scopes should not contain CurrentConsumerScopeJsonV700(canGetDynamicEntityDefinitions.toString, DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
      } finally Scope.scope.vend.deleteScope(granted)
    }
  }
}
