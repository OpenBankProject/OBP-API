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

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil
import code.api.util.migration.MigrationOfDynamicEntityRoleNames
import code.entitlement.MappedEntitlement
import code.group.Group
import code.setup.ServerSetup
import net.liftweb.mapper.By
import org.scalatest.Tag

/**
 * The migration that moves stored Dynamic Entity Roles onto their new names and spaces.
 *
 * A Role rename is not like a Role that merely narrows: the old name stops existing, so an Entitlement
 * carrying it authorises nothing at all rather than authorising less. The mapping is one-to-one, which
 * is why this is migrated rather than left to every operator to re-grant, and these scenarios are what
 * say the mapping is the one intended.
 *
 * Four things are checked, because each is a different decision rather than a different example:
 * a Record Role is renamed and moved to the system space; a Definition Role is left alone, because its
 * endpoints still resolve at the empty bank id until their URLs carry a space; a Role that authorised
 * every bank has no successor and must be left for a human; and a Group holding only Record Roles moves
 * to the system space with them, while one holding a mix stays put so that its other Roles keep working.
 */
class DynamicEntityRoleRenameMigrationTest extends ServerSetup {

  object RoleRenameMigration extends Tag("DynamicEntityRoleRenameMigration")

  private val suffix = APIUtil.generateUUID().take(8)
  private val userId = s"role-rename-user-$suffix"

  private def entitlementRowsFor(roleName: String): List[MappedEntitlement] =
    MappedEntitlement.findAll(By(MappedEntitlement.mRoleName, roleName), By(MappedEntitlement.mUserId, userId))

  private def giveEntitlement(bankId: String, roleName: String): Unit =
    MappedEntitlement.create.mBankId(bankId).mUserId(userId).mRoleName(roleName)
      .mEntitlementId(APIUtil.generateUUID()).saveMe()

  private def makeGroup(name: String, bankId: String, roles: List[String]): Group =
    Group.create.GroupId(APIUtil.generateUUID()).GroupName(name).GroupDescription("role rename test")
      .BankId(bankId).ListOfRoles(roles.mkString(",")).IsEnabled(true).saveMe()

  private def reloadGroup(groupName: String): Group =
    Group.find(By(Group.GroupName, groupName)).openOrThrowException(s"group $groupName should exist")

  feature("The Dynamic Entity Role rename migration") {

    scenario("it renames a Record Role and moves a system level one onto the system space", RoleRenameMigration) {
      val entity = s"country_$suffix"
      Given("a system level grant under the old name, and a bank level one")
      giveEntitlement("", s"CanCreateDynamicEntity_System$entity")
      giveEntitlement("bank_one", s"CanGetDynamicEntity_$entity")

      When("the migration runs")
      MigrationOfDynamicEntityRoleNames.renameEverywhere(s"roleRenameTest_$suffix") should equal(true)

      Then("the system level grant carries the new name at the system space")
      val systemRows = entitlementRowsFor(s"CanCreateDynamicEntityRecord_$entity")
      systemRows.size should equal(1)
      systemRows.head.mBankId.get should equal(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)

      And("the bank level grant carries the new name and stays at its bank")
      val bankRows = entitlementRowsFor(s"CanGetDynamicEntityRecord_$entity")
      bankRows.size should equal(1)
      bankRows.head.mBankId.get should equal("bank_one")

      And("nothing is left under the old names")
      entitlementRowsFor(s"CanCreateDynamicEntity_System$entity") shouldBe empty
      entitlementRowsFor(s"CanGetDynamicEntity_$entity") shouldBe empty
    }

    scenario("it leaves a Definition Role and an any-bank Role alone", RoleRenameMigration) {
      Given("a Definition Role, which still resolves at the empty bank id")
      giveEntitlement("", "CanCreateSystemLevelDynamicEntity")
      And("an any-bank Role, which has no single successor")
      giveEntitlement("", "CanCreateAnyBankLevelDynamicEntity")

      When("the migration runs")
      MigrationOfDynamicEntityRoleNames.renameEverywhere(s"roleRenameTest2_$suffix")

      Then("both are untouched, name and bank id alike")
      val definitionRows = entitlementRowsFor("CanCreateSystemLevelDynamicEntity")
      definitionRows.size should equal(1)
      definitionRows.head.mBankId.get should equal("")

      val anyBankRows = entitlementRowsFor("CanCreateAnyBankLevelDynamicEntity")
      anyBankRows.size should equal(1)
      anyBankRows.head.mBankId.get should equal("")
    }

    scenario("a Group of Record Roles moves to the system space; a mixed Group stays put", RoleRenameMigration) {
      val entity = s"parcel_$suffix"
      val pureName = s"pure_group_$suffix"
      val mixedName = s"mixed_group_$suffix"

      Given("a system level Group holding only Record Roles")
      makeGroup(pureName, "", List(s"CanCreateDynamicEntity_System$entity", s"CanGetDynamicEntity_System$entity"))
      And("another holding a Record Role and a Definition Role")
      makeGroup(mixedName, "", List(s"CanCreateDynamicEntity_System$entity", "CanCreateSystemLevelDynamicEntity"))

      When("the migration runs")
      MigrationOfDynamicEntityRoleNames.renameEverywhere(s"roleRenameTest3_$suffix")

      Then("the pure Group carries the new names and has moved to the system space")
      val pure = reloadGroup(pureName)
      pure.ListOfRoles.get should equal(s"CanCreateDynamicEntityRecord_$entity,CanGetDynamicEntityRecord_$entity")
      pure.BankId.get should equal(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)

      And("the mixed Group carries the new name for its Record Role but stays at the empty bank id")
      val mixed = reloadGroup(mixedName)
      mixed.ListOfRoles.get should equal(s"CanCreateDynamicEntityRecord_$entity,CanCreateSystemLevelDynamicEntity")
      mixed.BankId.get should equal("")
    }
  }
}
