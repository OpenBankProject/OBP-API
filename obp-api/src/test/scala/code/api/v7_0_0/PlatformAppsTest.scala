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
import code.api.util.ApiRole.{canCreatePlatformApp, canDeletePlatformApp, canGetDynamicEntityDefinitions, canGetPlatformApps}
import code.api.util.ErrorMessages.{InvalidPlatformAppDeclaration, PlatformAppAlreadyExists, PlatformAppNotFound, UserHasMissingRoles}
import code.api.v6_0_0.V600ServerSetup
import code.entitlement.Entitlement
import code.platformapp.PlatformApps
import code.scope.Scope
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Full
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.{compact, render}
import org.scalatest.Tag

/** Platform Apps: an administrator marks a Consumer, the app declares the Scopes it needs as itself. */
class PlatformAppsTest extends V600ServerSetup {

  def v7 = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("createPlatformApp")
  object ApiEndpoint2 extends Tag("getPlatformApps")
  object ApiEndpoint3 extends Tag("deletePlatformApp")
  object ApiEndpoint4 extends Tag("updateCurrentConsumerPlatformApp")

  private def platformApps = v7 / "management" / "platform-apps"
  private def declaration = v7 / "consumers" / "current" / "platform-app"
  private def mark(consumerId: String, label: String) =
    compact(render(("consumer_id" -> consumerId) ~ ("label" -> label)))
  private def declare(scopes: (String, String, String, Boolean)*) =
    compact(render(("version" -> "1.2.3") ~ ("required_scopes" -> scopes.toList.map { case (role, bank, neededFor, optional) =>
      ("role_name" -> role) ~ ("bank_id" -> bank) ~ ("needed_for" -> neededFor) ~ ("optional" -> optional)
    })))
  private def grant(role: code.api.util.ApiRole) = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, role.toString)
  private def message(r: code.setup.APIResponse) = r.body.extract[ErrorMessage].message

  private val SYS = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID

  feature("Platform Apps") {

    scenario("mark a Consumer, let it declare its Scopes, see which it holds, and unmark it",
      ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      val consumerId = testConsumer2.consumerId.get
      PlatformApps.platformAppProvider.vend.deletePlatformApp(consumerId)
      val entitlements = List(canCreatePlatformApp, canGetPlatformApps, canDeletePlatformApp)
      var scope: net.liftweb.common.Box[Scope] = net.liftweb.common.Empty
      try {
        When("testConsumer2 declares before anyone has marked it")
        val early = makePutRequest(declaration.PUT <@ (user2), declare((canGetDynamicEntityDefinitions.toString, SYS, "Finding entities.", false)))
        Then("it is refused: only marked Consumers may declare")
        early.code should equal(404)
        message(early) should include(PlatformAppNotFound)

        When("user1 marks it without the Role")
        val refused = makePostRequest(platformApps.POST <@ (user1), mark(consumerId, "Portal"))
        refused.code should equal(403)
        message(refused) should include(UserHasMissingRoles)

        val granted = entitlements.map(grant)
        try {
          When("user1 marks it with CanCreatePlatformApp")
          val created = makePostRequest(platformApps.POST <@ (user1), mark(consumerId, "Portal"))
          created.code should equal(201)
          (created.body \ "state").extract[String] should equal("not_declared")

          And("marking it again is refused")
          makePostRequest(platformApps.POST <@ (user1), mark(consumerId, "Portal")).code should equal(409)

          When("it declares a Role that does not exist")
          val invalid = makePutRequest(declaration.PUT <@ (user2), declare(("CanDoNothingAtAll", SYS, "Nothing.", false)))
          invalid.code should equal(400)
          message(invalid) should include(InvalidPlatformAppDeclaration)

          When("it declares one required and one optional Scope")
          val declared = makePutRequest(declaration.PUT <@ (user2), declare(
            (canGetDynamicEntityDefinitions.toString, SYS, "Finding entities.", false),
            (canCreatePlatformApp.toString, "", "Nothing that matters.", true)))
          Then("the required one is missing")
          declared.code should equal(200)
          (declared.body \ "state").extract[String] should equal("missing")
          (declared.body \ "version").extract[String] should equal("1.2.3")

          When("its Consumer is granted the required Scope")
          scope = Scope.scope.vend.addScope(SYS, testConsumer2.id.get.toString, canGetDynamicEntityDefinitions.toString)
          val listed = makeGetRequest(platformApps.GET <@ (user1))
          Then("the list shows it held, and the app ok although the optional Scope is not held")
          listed.code should equal(200)
          val app = (listed.body \ "platform_apps").children.find(a => (a \ "consumer_id").extract[String] == consumerId)
            .getOrElse(fail("the app should be listed"))
          (app \ "state").extract[String] should equal("ok")
          (app \ "label").extract[String] should equal("Portal")
          val held = (app \ "required_scopes").children.map(s => ((s \ "role_name").extract[String], (s \ "held").extract[Boolean]))
          held should contain((canGetDynamicEntityDefinitions.toString, true))
          held should contain((canCreatePlatformApp.toString, false))

          When("it is unmarked")
          makeDeleteRequest((platformApps / consumerId).DELETE <@ (user1)).code should equal(204)
          Then("it can no longer declare")
          makePutRequest(declaration.PUT <@ (user2), declare((canGetDynamicEntityDefinitions.toString, SYS, "Finding entities.", false)))
            .code should equal(404)
        } finally granted.foreach(e => Entitlement.entitlement.vend.deleteEntitlement(e))
      } finally {
        scope.foreach(s => Scope.scope.vend.deleteScope(Full(s)))
        PlatformApps.platformAppProvider.vend.deletePlatformApp(consumerId)
      }
    }

    scenario("marking an unknown Consumer is a 404", ApiEndpoint1, VersionOfApi) {
      val granted = grant(canCreatePlatformApp)
      try {
        makePostRequest(platformApps.POST <@ (user1), mark("no-such-consumer", "Nothing")).code should equal(404)
      } finally Entitlement.entitlement.vend.deleteEntitlement(granted)
    }
  }
}
