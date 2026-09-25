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
package code.api.util.migration

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.entitlement.MappedEntitlement
import code.entitlementrequest.MappedEntitlementRequest
import code.group.Group
import code.scope.MappedScope

/**
 * Rename the Dynamic Entity Roles wherever a Role name is stored, and move the ones that were system
 * level onto the system space.
 *
 * This happens in two passes, each registered as its own runOnce migration, because the two Role
 * families moved at different times.
 *
 * The **Record** pass ([[renameEverywhere]]). The Roles that gate an entity's records gained the word
 * Record and lost their System twin, so `CanCreateDynamicEntity_SystemCountry` and
 * `CanCreateDynamicEntity_Country` are both now `CanCreateDynamicEntityRecord_Country`. The name no
 * longer says which space it applies to; the Entitlement's bank id does, and the system space is
 * DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID rather than the empty string it used to be.
 *
 * The **Definition** pass ([[renameDefinitionRolesEverywhere]]). The Roles that gate a definition
 * lost their System and BankLevel split: `CanCreateSystemLevelDynamicEntity` and
 * `CanCreateBankLevelDynamicEntity` are both now `CanCreateDynamicEntityDefinition`, and so on for
 * update, delete, get, backup and cascade delete. It waited for the v7.0.0 management endpoints,
 * which name the space in their URL, because until then a system level Definition Role could only be
 * checked at the empty bank id. See DYNAMIC_ENTITY_SPACE_MODEL_PLAN.md, phase 6.
 *
 * Unlike a Role that merely narrows, a renamed Role leaves an existing grant meaningless rather than
 * weaker: the old name no longer exists, so nothing reads the row. Each rename is also exactly
 * one-to-one, with no fan-out per bank, which is what makes this worth migrating rather than asking
 * every operator to re-grant by hand.
 *
 * Which variant a stored name was is read from the row rather than from the name. An empty bank id
 * means the system variant, anything else means the bank variant. That matters for the generated
 * Roles, where the entity name is glued to the word System: a row named
 * `CanCreateDynamicEntity_SystemFoo` is the system level Role for entity `Foo` when its bank id is
 * empty, and the bank level Role for an entity actually called `SystemFoo` when it is not.
 *
 * Four stores hold Role names: Entitlements, Entitlement Requests, Consumer Scopes, and the
 * comma-joined list on a Group. Groups need one extra step — a system level Group grants its Roles at
 * its own bank id, which is empty, so a Group holding only Dynamic Entity Roles is moved to the
 * system space as well; one holding a mix is left where it is and named in the log, because moving it
 * would put its other Roles at a space nothing reads.
 *
 * Two Roles are deliberately not migrated. `CanCreateAnyBankLevelDynamicEntity` and
 * `CanGetAnyBankLevelDynamicEntities` authorised every bank at once and have no single successor, so
 * their holders are named in the log for an operator to re-grant deliberately, per bank. That is the
 * narrowing this work exists for, and it cannot be done by a migration.
 */
object MigrationOfDynamicEntityRoleNames {

  /** Roles that authorised every bank at once, and so have no single successor. */
  private val rolesWithNoSuccessor: Set[String] =
    Set("CanCreateAnyBankLevelDynamicEntity", "CanGetAnyBankLevelDynamicEntities")

  /** The generated per-entity Roles: old prefix -> new prefix. */
  private val generatedRolePrefixRenames: List[(String, String)] = List(
    "CanCreateDynamicEntity_"        -> "CanCreateDynamicEntityRecord_",
    "CanUpdateDynamicEntity_"        -> "CanUpdateDynamicEntityRecord_",
    "CanGetDynamicEntity_"           -> "CanGetDynamicEntityRecord_",
    "CanDeleteDynamicEntity_"        -> "CanDeleteDynamicEntityRecord_",
    "CanGrantDynamicEntityRowAccess_" -> "CanGrantDynamicEntityRowAccess_",
    "CanWriteDynamicEntityField_"    -> "CanWriteDynamicEntityField_",
    "CanGetDynamicEntityField_"      -> "CanGetDynamicEntityField_"
  )

  /** The Definition Roles: every old name -> the one Role that replaces it. */
  private val definitionRoleRenames: Map[String, String] = Map(
    "CanCreateSystemLevelDynamicEntity"   -> "CanCreateDynamicEntityDefinition",
    "CanCreateBankLevelDynamicEntity"     -> "CanCreateDynamicEntityDefinition",
    "CanUpdateSystemLevelDynamicEntity"   -> "CanUpdateDynamicEntityDefinition",
    "CanUpdateBankLevelDynamicEntity"     -> "CanUpdateDynamicEntityDefinition",
    "CanDeleteSystemLevelDynamicEntity"   -> "CanDeleteDynamicEntityDefinition",
    "CanDeleteBankLevelDynamicEntity"     -> "CanDeleteDynamicEntityDefinition",
    "CanGetSystemLevelDynamicEntities"    -> "CanGetDynamicEntityDefinitions",
    "CanGetBankLevelDynamicEntities"      -> "CanGetDynamicEntityDefinitions",
    "CanDeleteCascadeSystemDynamicEntity" -> "CanDeleteCascadeDynamicEntityDefinition",
    "CanBackupSystemDynamicEntity"        -> "CanBackupDynamicEntityDefinition",
    "CanBackupBankLevelDynamicEntity"     -> "CanBackupDynamicEntityDefinition"
  )

  /**
   * The new name for a stored Record Role, given the bank id the row holds, or None when the Role is
   * not one of ours or has no successor. `wasSystemLevel` is true when the stored bank id is empty.
   */
  def renameOf(oldName: String, wasSystemLevel: Boolean): Option[String] = {
    if (rolesWithNoSuccessor.contains(oldName)) None
    else {
      generatedRolePrefixRenames.collectFirst {
        case (oldPrefix, newPrefix) if oldName.startsWith(oldPrefix) =>
          val remainder = oldName.drop(oldPrefix.length)
          // The System twin exists only for a system level row; on a bank row the same letters are
          // the beginning of the entity's own name.
          val entityAndField = if (wasSystemLevel && remainder.startsWith("System")) remainder.drop("System".length) else remainder
          newPrefix + entityAndField
      }
    }
  }

  /** The new name for a stored Definition Role, or None when the Role is not an old Definition Role. */
  def definitionRenameOf(oldName: String): Option[String] = definitionRoleRenames.get(oldName)

  /**
   * Is this a Dynamic Entity Role under its current name — one that names a space, so a grant of it
   * at the empty bank id belongs at the system space?
   */
  private def isCurrentDynamicEntityRoleName(roleName: String): Boolean =
    definitionRoleRenames.values.toSet.contains(roleName) ||
      generatedRolePrefixRenames.map(_._2).exists(roleName.startsWith)

  /**
   * One rename pass. `renameOf` gives a stored name's successor (the Boolean says the row sat at the
   * empty bank id); `belongsInSpace` says whether a Role stored at the empty bank id belongs at the
   * system space once renamed, and is also what decides whether a system level Group can move.
   */
  private case class RenamePass(
    renameOf: (String, Boolean) => Option[String],
    belongsInSpace: String => Boolean
  )

  /**
   * The Record pass moves only the Record Roles. The Definition Roles still resolved at the empty
   * bank id then, so a Group holding one of them had to stay where it was.
   */
  private val recordPass = RenamePass(
    renameOf = renameOf,
    belongsInSpace = oldName => renameOf(oldName, wasSystemLevel = true).isDefined
  )

  /**
   * The Definition pass moves the Definition Roles, and counts every Dynamic Entity Role already
   * under its new name as belonging to a space too. That second part is what lets a system level
   * Group the Record pass had to leave behind, because it also held a Definition Role, move now.
   */
  private val definitionPass = RenamePass(
    renameOf = (oldName, _) => definitionRenameOf(oldName),
    belongsInSpace = roleName => definitionRoleRenames.contains(roleName) || isCurrentDynamicEntityRoleName(roleName)
  )

  /** Rename the Record Roles in every store. */
  def renameEverywhere(name: String): Boolean = run(name, recordPass)

  /** Rename the Definition Roles in every store. */
  def renameDefinitionRolesEverywhere(name: String): Boolean = run(name, definitionPass)

  private def run(name: String, pass: RenamePass): Boolean = {
    val startDate = System.currentTimeMillis()
    val report = scala.collection.mutable.ListBuffer[String]()

    /** The space a renamed Role belongs at. */
    def newBankId(oldName: String, oldBankId: String): String =
      if (oldBankId.isEmpty && pass.belongsInSpace(oldName)) DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID else oldBankId

    def renameRows(
      label: String,
      rows: List[(String, String, String => Unit, String => Unit)]
    ): Unit = {
      var renamed = 0
      val orphaned = scala.collection.mutable.ListBuffer[String]()
      rows.foreach { case (roleName, bankId, setRoleName, setBankId) =>
        if (rolesWithNoSuccessor.contains(roleName)) orphaned += roleName
        else pass.renameOf(roleName, bankId.isEmpty).foreach { newName =>
          setRoleName(newName)
          setBankId(newBankId(roleName, bankId))
          renamed += 1
        }
      }
      val orphanNote =
        if (orphaned.isEmpty) ""
        else s"; left alone because they authorised every bank and have no single successor: " +
          orphaned.groupBy(identity).map { case (n, all) => s"$n x${all.size}" }.mkString(", ")
      report += s"$label: renamed $renamed row(s)$orphanNote"
    }

    if (DbFunction.tableExists(MappedEntitlement)) {
      val rows = MappedEntitlement.findAll()
      renameRows("Entitlements", rows.map(row =>
        (row.mRoleName.get, row.mBankId.get,
          (n: String) => { row.mRoleName(n); () },
          (b: String) => { row.mBankId(b).save; () })))
    }

    if (DbFunction.tableExists(MappedEntitlementRequest)) {
      val rows = MappedEntitlementRequest.findAll()
      renameRows("Entitlement Requests", rows.map(row =>
        (row.mRoleName.get, row.mBankId.get,
          (n: String) => { row.mRoleName(n); () },
          (b: String) => { row.mBankId(b).save; () })))
    }

    if (DbFunction.tableExists(MappedScope)) {
      val rows = MappedScope.findAll()
      renameRows("Consumer Scopes", rows.map(row =>
        (row.mRoleName.get, row.mBankId.get,
          (n: String) => { row.mRoleName(n); () },
          (b: String) => { row.mBankId(b).save; () })))
    }

    if (DbFunction.tableExists(Group)) {
      var renamedGroups = 0
      var movedGroups = 0
      val mixedGroups = scala.collection.mutable.ListBuffer[String]()
      Group.findAll().foreach { group =>
        val wasSystemLevel = group.BankId.get.isEmpty
        val storedRoles = group.ListOfRoles.get.split(",").toList.map(_.trim).filter(_.nonEmpty)
        val renamedRoles = storedRoles.map(r => pass.renameOf(r, wasSystemLevel).getOrElse(r))
        if (renamedRoles != storedRoles) {
          group.ListOfRoles(renamedRoles.mkString(","))
          renamedGroups += 1
        }
        // A system level Group grants at its own bank id, which is empty, so a Group whose Roles all
        // belong in a space moves to the system space and keeps working. One holding a mix cannot
        // move: its other Roles belong at the empty bank id and would land where nothing reads them.
        val everyRoleMoves = storedRoles.nonEmpty && storedRoles.forall(pass.belongsInSpace)
        if (wasSystemLevel && everyRoleMoves) {
          group.BankId(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
          movedGroups += 1
        } else if (wasSystemLevel && storedRoles.exists(r => pass.renameOf(r, wasSystemLevel).isDefined)) {
          mixedGroups += group.GroupName.get
        }
        group.save
      }
      val mixedNote =
        if (mixedGroups.isEmpty) ""
        else s"; left at the empty bank id because they also hold Roles that belong there, so their " +
          s"Dynamic Entity Roles need granting another way: ${mixedGroups.mkString(", ")}"
      report += s"Groups: renamed Roles on $renamedGroups group(s), moved $movedGroups to the system space$mixedNote"
    }

    val endDate = System.currentTimeMillis()
    saveLog(name, APIUtil.gitCommit, isSuccessful = true, startDate, endDate, report.mkString("; "))
    true
  }
}
