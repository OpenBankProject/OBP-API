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
 * The Roles that gate an entity's records gained the word Record and lost their System twin, so
 * `CanCreateDynamicEntity_SystemCountry` and `CanCreateDynamicEntity_Country` are both now
 * `CanCreateDynamicEntityRecord_Country`. The name no longer says which space it applies to; the
 * Entitlement's bank id does, and the system space is DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID rather than
 * the empty string it used to be.
 *
 * The Roles that gate a **definition** are deliberately untouched here. Merging their System and
 * BankLevel variants into one name means choosing one `requiresBankId`, and choosing `false` — which
 * the system management endpoints need, because their URLs carry no space for the middleware to read
 * — would widen the bank level Role into one grant that authorises every bank. They are renamed and
 * re-scoped in the same change that gives those endpoints a space in their URL; see
 * DYNAMIC_ENTITY_SPACE_MODEL_PLAN.md, phase 6.
 *
 * Unlike a Role that merely narrows, a renamed Role leaves an existing grant meaningless rather than
 * weaker: the old name no longer exists, so nothing reads the row. The rename is also exactly
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

  /**
   * The new name for a stored Role, given the bank id the row holds, or None when the Role is not one
   * of ours or has no successor. `wasSystemLevel` is true when the stored bank id is empty.
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

  /**
   * Does a Role of this name move onto the system space when its row sat at the empty bank id?
   *
   * The Record family does: its checks resolve the space in the handler, so a system level grant now
   * belongs at DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID. The Definition family does not, yet: those
   * endpoints are served at URLs with no space segment, so the middleware still resolves them at the
   * empty bank id and moving their rows would strand them. They move in the same change that gives
   * those endpoints a space in their URL.
   */
  private def movesToSystemSpace(oldName: String): Boolean = renameOf(oldName, wasSystemLevel = true).isDefined

  /** The space a renamed Role belongs at. */
  private def newBankId(oldName: String, oldBankId: String): String =
    if (oldBankId.isEmpty && movesToSystemSpace(oldName)) DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID else oldBankId

  def renameEverywhere(name: String): Boolean = {
    val startDate = System.currentTimeMillis()
    val report = scala.collection.mutable.ListBuffer[String]()

    def renameRows(
      label: String,
      rows: List[(String, String, String => Unit, String => Unit)]
    ): Unit = {
      var renamed = 0
      val orphaned = scala.collection.mutable.ListBuffer[String]()
      rows.foreach { case (roleName, bankId, setRoleName, setBankId) =>
        if (rolesWithNoSuccessor.contains(roleName)) orphaned += roleName
        else renameOf(roleName, bankId.isEmpty).foreach { newName =>
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
        val renamedRoles = storedRoles.map(r => renameOf(r, wasSystemLevel).getOrElse(r))
        if (renamedRoles != storedRoles) {
          group.ListOfRoles(renamedRoles.mkString(","))
          renamedGroups += 1
        }
        // A system level Group grants at its own bank id, which is empty, so a Group whose Roles are
        // all ours moves to the system space and keeps working. One holding a mix cannot move: its
        // other Roles belong at the empty bank id and would land where nothing reads them.
        // Only a Group whose Roles all move to the system space may move with them. One holding a
        // Definition Role, which still resolves at the empty bank id, has to stay where it is.
        val everyRoleMoves = storedRoles.nonEmpty &&
          storedRoles.forall(r => renameOf(r, wasSystemLevel).isDefined && movesToSystemSpace(r))
        if (wasSystemLevel && everyRoleMoves) {
          group.BankId(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
          movedGroups += 1
        } else if (wasSystemLevel && storedRoles.exists(r => renameOf(r, wasSystemLevel).isDefined && movesToSystemSpace(r))) {
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
