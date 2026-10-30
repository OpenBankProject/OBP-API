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
package code.organisation

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil
import code.api.util.migration.MigrationOfOrganisationSpace
import code.entitlement.MappedEntitlement
import code.group.Group
import code.setup.ServerSetup
import net.liftweb.db.DB
import net.liftweb.mapper.By
import net.liftweb.util.DefaultConnectionIdentifier
import org.scalatest.Tag

/**
 * The migration that puts stored Organisations in the system space and moves their Roles there.
 *
 * Each scenario checks one decision: an Organisation written before the bank id column existed moves to
 * SYS while one already in a space stays; an Organisation Role at the empty bank id moves to SYS, and
 * CanGetAnyOrganisation is renamed on the way; and a Group holding only Organisation Roles moves with
 * them, while one holding a mix stays where its other Roles are read.
 */
class OrganisationSpaceMigrationTest extends ServerSetup {

  object OrganisationSpaceMigration extends Tag("OrganisationSpaceMigration")

  private val suffix = APIUtil.generateUUID().take(8)
  private val userId = s"org-space-user-$suffix"

  private def bankIdStoredFor(orgId: String): String =
    Organisation.find(By(Organisation.OrganisationId, orgId)).map(_.BankId.get)
      .openOrThrowException(s"organisation $orgId should exist")

  /** An Organisation as stored before the column existed: its bank id is NULL. */
  private def legacyOrganisation(orgId: String): Unit = {
    Organisation.create.OrganisationId(orgId).Name(orgId).Status("active").Visibility("public")
      .CreatedByUserId(userId).saveMe()
    DB.use(DefaultConnectionIdentifier) { connection =>
      val statement = connection.prepareStatement(
        s"UPDATE ${Organisation._dbTableNameLC} SET ${Organisation.BankId.dbColumnName} = NULL " +
          s"WHERE ${Organisation.OrganisationId.dbColumnName} = ?")
      try {
        statement.setString(1, orgId)
        statement.executeUpdate()
      } finally statement.close()
    }
  }

  private def giveEntitlement(bankId: String, roleName: String): Unit =
    MappedEntitlement.create.mBankId(bankId).mUserId(userId).mRoleName(roleName)
      .mEntitlementId(APIUtil.generateUUID()).saveMe()

  private def entitlementsOf(roleName: String): List[String] =
    MappedEntitlement.findAll(By(MappedEntitlement.mRoleName, roleName), By(MappedEntitlement.mUserId, userId))
      .map(_.mBankId.get)

  private def makeGroup(name: String, roles: List[String]): Unit =
    Group.create.GroupId(APIUtil.generateUUID()).GroupName(name).GroupDescription("organisation space test")
      .BankId("").ListOfRoles(roles.mkString(",")).IsEnabled(true).saveMe()

  private def reloadGroup(name: String): Group =
    Group.find(By(Group.GroupName, name)).openOrThrowException(s"group $name should exist")

  feature("Moving Organisations into spaces") {

    scenario("A stored Organisation moves to SYS, one already in a bank stays", OrganisationSpaceMigration) {
      val legacy = s"legacy-org-$suffix"
      val atBank = s"bank-org-$suffix"
      legacyOrganisation(legacy)
      Organisations.organisation.vend.createOrganisation("some-bank", atBank, atBank, None, None, None,
        "active", "public", userId)

      MigrationOfOrganisationSpace.moveEverything(s"testMoveOrganisations_$suffix") shouldBe true

      bankIdStoredFor(legacy) shouldBe DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
      bankIdStoredFor(atBank) shouldBe "some-bank"
    }

    scenario("Organisation Roles move from the empty bank id to SYS, and CanGetAnyOrganisation is renamed", OrganisationSpaceMigration) {
      giveEntitlement("", "CanGetAnyOrganisation")
      giveEntitlement("", "CanCreateOrganisation")
      giveEntitlement("", "CanGetAnyUser")

      MigrationOfOrganisationSpace.moveEverything(s"testMoveOrganisationRoles_$suffix") shouldBe true

      entitlementsOf("CanGetAnyOrganisation") shouldBe Nil
      entitlementsOf("CanGetOrganisation") shouldBe List(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
      entitlementsOf("CanCreateOrganisation") shouldBe List(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
      And("a Role that is not about Organisations is left alone")
      entitlementsOf("CanGetAnyUser") shouldBe List("")
    }

    scenario("A Group holding only Organisation Roles moves to SYS; a mixed one stays", OrganisationSpaceMigration) {
      val onlyOrgs = s"org-admins-$suffix"
      val mixed = s"mixed-admins-$suffix"
      makeGroup(onlyOrgs, List("CanGetAnyOrganisation", "CanUpdateOrganisation"))
      makeGroup(mixed, List("CanGetAnyOrganisation", "CanGetAnyUser"))

      MigrationOfOrganisationSpace.moveEverything(s"testMoveOrganisationGroups_$suffix") shouldBe true

      val movedGroup = reloadGroup(onlyOrgs)
      movedGroup.BankId.get shouldBe DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
      movedGroup.ListOfRoles.get shouldBe "CanGetOrganisation,CanUpdateOrganisation"
      val mixedGroup = reloadGroup(mixed)
      mixedGroup.BankId.get shouldBe ""
      mixedGroup.ListOfRoles.get shouldBe "CanGetOrganisation,CanGetAnyUser"
    }
  }
}
