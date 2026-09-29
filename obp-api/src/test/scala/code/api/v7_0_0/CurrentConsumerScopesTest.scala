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
import code.api.util.ApiRole.{canCreateScopeAtAnyBank, canGetDynamicEntityDefinitions}
import code.api.util.ErrorMessages.{ApplicationNotIdentified, EntitlementAlreadyExists, UserHasMissingRoles}
import code.entitlement.Entitlement
import code.api.v6_0_0.V600ServerSetup
import code.api.v7_0_0.JSONFactory700.{CurrentConsumerScopeJsonV700, CurrentConsumerScopesJsonV700}
import code.scope.Scope
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.{compact, render}
import org.scalatest.Tag

/** GET /obp/v7.0.0/consumers/current/scopes: the caller's own Consumer's Scopes, no Role. */
class CurrentConsumerScopesTest extends V600ServerSetup {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("getCurrentConsumerScopes")
  object ApiEndpoint2 extends Tag("addScope")

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

  feature(s"test $ApiEndpoint2 version $VersionOfApi") {

    scenario("A Scope can be granted at SYS, once, by a caller holding CanCreateScopeAtAnyBank", ApiEndpoint2, VersionOfApi) {
      val path = v7_0_0_Request / "consumers" / testConsumer2.consumerId.get / "scopes"
      val body = compact(render(("bank_id" -> DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID) ~ ("role_name" -> canGetDynamicEntityDefinitions.toString)))
      def existing = Scope.scope.vend.getScope(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, testConsumer2.id.get.toString,
        canGetDynamicEntityDefinitions.toString)
      existing.foreach(s => Scope.scope.vend.deleteScope(net.liftweb.common.Full(s)))

      When("user1 has no granting Role")
      val refused = makePostRequest(path.POST <@ (user1), body)
      Then("the call is refused")
      refused.code should equal(403)
      refused.body.extract[ErrorMessage].message should include(UserHasMissingRoles)

      Given("user1 holds CanCreateScopeAtAnyBank")
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateScopeAtAnyBank.toString)
      try {
        When("user1 grants the Scope at SYS")
        val created = makePostRequest(path.POST <@ (user1), body)
        Then("it is created at SYS, which v4.0.0 refuses as an unknown bank")
        created.code should equal(201)
        (created.body \ "bank_id").extract[String] should equal(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
        existing.isDefined should equal(true)

        When("the same Scope is granted again")
        val again = makePostRequest(path.POST <@ (user1), body)
        Then("it is refused as a duplicate")
        again.code should equal(409)
        again.body.extract[ErrorMessage].message should include(EntitlementAlreadyExists)
      } finally {
        existing.foreach(s => Scope.scope.vend.deleteScope(net.liftweb.common.Full(s)))
        entitlement.foreach(e => Entitlement.entitlement.vend.deleteEntitlement(net.liftweb.common.Full(e)))
      }
    }
  }
}
