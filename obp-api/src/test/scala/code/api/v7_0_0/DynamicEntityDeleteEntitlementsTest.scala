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
import code.api.util.ApiRole
import code.api.util.ApiRole._
import code.entitlement.Entitlement
import code.setup.ServerSetupWithTestData
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.util.UUID

/**
 * Deleting a Dynamic Entity deletes the grants of its record Roles, but only in its own space.
 *
 * A record Role is named after the entity alone (CanGetDynamicEntityRecord_<entity>), with no bank, so
 * an entity of the same name at another bank, or at SYS, uses the same Role names. Deleting one of
 * them used to delete every grant of those names at every bank, and to unregister the Roles while the
 * other entity still used them.
 */
class DynamicEntityDeleteEntitlementsTest extends ServerSetupWithTestData {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Http4s700DynamicEntityDefinitions.deleteDynamicEntityDefinitionCascade))

  private val SYS = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID

  def v7 = baseRequest / "obp" / "v7.0.0"

  private def definitionsAt(bankId: String) = v7 / "management" / "banks" / bankId / "dynamic-entities"

  private def newEntityName(): String = "test_delete_grants_" + UUID.randomUUID().toString.take(8).replace("-", "")

  private def definition(entityName: String): JValue =
    ("entity_name" -> entityName) ~
    ("has_personal_entity" -> false) ~
    ("schema" -> parse(
      """{"description": "Entity for the delete-grants test.", "required": ["name"],
        | "properties": {"name": {"type": "string", "maxLength": 40, "minLength": 1, "example": "Test"}}}""".stripMargin))

  private def grant(bankId: String, userId: String, roleName: String): Unit =
    Entitlement.entitlement.vend.addEntitlement(bankId, userId, roleName)

  private def createdAt(bankId: String, entityName: String): String = {
    grant(bankId, resourceUser1.userId, canCreateDynamicEntityDefinition.toString)
    val response = makePostRequest(definitionsAt(bankId).POST <@ (user1), write(definition(entityName)))
    response.code should equal(201)
    (response.body \ "dynamic_entity_id").extract[String]
  }

  private def cascadeDelete(bankId: String, dynamicEntityId: String): Unit = {
    grant(bankId, resourceUser1.userId, canDeleteCascadeDynamicEntityDefinition.toString)
    makeDeleteRequest((definitionsAt(bankId) / "cascade" / dynamicEntityId).DELETE <@ (user1)).code should equal(204)
  }

  /** The bank ids at which user2 holds `roleName`. */
  private def user2BanksFor(roleName: String): Set[String] =
    Entitlement.entitlement.vend.getEntitlementsByUserId(resourceUser2.userId).openOr(Nil)
      .filter(_.roleName == roleName).map(_.bankId).toSet

  feature("Deleting a Dynamic Entity leaves the grants of another space's entity of the same name") {

    scenario("an entity named the same at a bank and at SYS: deleting one leaves the other's grants", ApiEndpoint1, VersionOfApi) {
      val entityName = newEntityName()
      val readRole = s"CanGetDynamicEntityRecord_$entityName"
      val bank = testBankId1.value

      Given(s"$entityName at ${bank} and at SYS, and user2 granted its read Role at both, and at the empty bank id")
      val atBank = createdAt(bank, entityName)
      val atSys = createdAt(SYS, entityName)
      grant(bank, resourceUser2.userId, readRole)
      grant(SYS, resourceUser2.userId, readRole)
      grant("", resourceUser2.userId, readRole)
      user2BanksFor(readRole) should equal(Set(bank, SYS, ""))

      When("the system level entity is deleted")
      cascadeDelete(SYS, atSys)

      Then("only the system space's grants are gone, SYS and the empty bank id")
      user2BanksFor(readRole) should equal(Set(bank))
      And("the Role is still registered, because the bank's entity still uses it")
      ApiRole.availableRoles should contain(readRole)

      When("the bank's entity is deleted too")
      cascadeDelete(bank, atBank)

      Then("its grant goes, and with no entity of that name left the Role is no longer registered")
      user2BanksFor(readRole) shouldBe empty
      ApiRole.availableRoles should not contain readRole
    }
  }
}
