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
import code.organisation.Organisation
import code.scope.MappedScope
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier

/**
 * Move Organisations, and the Roles that manage them, into spaces.
 *
 * An Organisation used to belong to no bank, and its four Roles were system Roles checked at the empty
 * bank id. It now lives in a space (a bank, or SYS for the system space), and its Roles are granted at
 * that space. Every Organisation stored before that change goes to SYS, which is what makes the Role part
 * exact rather than a widening or narrowing: a grant at the empty bank id covered exactly the
 * Organisations that now sit at SYS, so the same grant at SYS covers them again. One Role is also renamed:
 * `CanGetAnyOrganisation` is now `CanGetOrganisation`.
 *
 * Four stores hold Role names: Entitlements, Entitlement Requests, Consumer Scopes, and the comma-joined
 * list on a Group. As in [[MigrationOfDynamicEntityRoleNames]], a Group at the empty bank id grants its
 * Roles there, so one holding only Organisation Roles moves to SYS with them; one holding a mix stays, and
 * is named in the log, because moving it would put its other Roles where nothing reads them.
 */
object MigrationOfOrganisationSpace {

  private val roleRenames: Map[String, String] = Map("CanGetAnyOrganisation" -> "CanGetOrganisation")

  private val organisationRoles: Set[String] =
    Set("CanCreateOrganisation", "CanGetOrganisation", "CanUpdateOrganisation", "CanDeleteOrganisation")

  /** The current name of a stored Role name. */
  def renameOf(roleName: String): String = roleRenames.getOrElse(roleName, roleName)

  /** Is this one of the Organisation Roles, under its old or its current name? */
  def isOrganisationRole(roleName: String): Boolean = organisationRoles.contains(renameOf(roleName))

  def moveEverything(name: String): Boolean = {
    val startDate = System.currentTimeMillis()
    val report = scala.collection.mutable.ListBuffer[String]()

    if (DbFunction.tableExists(Organisation)) {
      // SQL rather than Mapper: a row written before the column existed holds NULL, and Mapper would read
      // it back as the field's default, SYS, so it would look as if there were nothing to move.
      val moved = DB.use(DefaultConnectionIdentifier) { connection =>
        val column = Organisation.BankId.dbColumnName
        val statement = connection.prepareStatement(
          s"UPDATE ${Organisation._dbTableNameLC} SET $column = ? WHERE $column IS NULL OR $column = ''")
        try {
          statement.setString(1, DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
          statement.executeUpdate()
        } finally statement.close()
      }
      report += s"Organisations: moved $moved to $DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID"
    }

    /** Rename an Organisation Role and move it from the empty bank id to SYS. */
    def moveRows(label: String, rows: List[(String, String, (String, String) => Unit)]): Unit = {
      var changed = 0
      var failed = 0
      rows.foreach { case (roleName, bankId, save) =>
        if (isOrganisationRole(roleName)) {
          val newName = renameOf(roleName)
          val newBankId = if (bankId == null || bankId.isEmpty) DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID else bankId
          if (newName != roleName || newBankId != bankId) {
            // A row the same holder already has at SYS would break a unique index; it is counted, not fatal.
            if (scala.util.Try(save(newName, newBankId)).isSuccess) changed += 1 else failed += 1
          }
        }
      }
      val failedNote = if (failed == 0) "" else s"; $failed could not be saved, most likely already held at $DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID"
      report += s"$label: changed $changed row(s)$failedNote"
    }

    if (DbFunction.tableExists(MappedEntitlement)) {
      moveRows("Entitlements", MappedEntitlement.findAll().map(row =>
        (row.mRoleName.get, row.mBankId.get,
          (n: String, b: String) => { row.mRoleName(n).mBankId(b).save; () })))
    }
    if (DbFunction.tableExists(MappedEntitlementRequest)) {
      moveRows("Entitlement Requests", MappedEntitlementRequest.findAll().map(row =>
        (row.mRoleName.get, row.mBankId.get,
          (n: String, b: String) => { row.mRoleName(n).mBankId(b).save; () })))
    }
    if (DbFunction.tableExists(MappedScope)) {
      moveRows("Consumer Scopes", MappedScope.findAll().map(row =>
        (row.mRoleName.get, row.mBankId.get,
          (n: String, b: String) => { row.mRoleName(n).mBankId(b).save; () })))
    }

    if (DbFunction.tableExists(Group)) {
      var renamedGroups = 0
      var movedGroups = 0
      val mixedGroups = scala.collection.mutable.ListBuffer[String]()
      Group.findAll().foreach { group =>
        val storedRoles = group.ListOfRoles.get.split(",").toList.map(_.trim).filter(_.nonEmpty)
        if (storedRoles.exists(isOrganisationRole)) {
          val renamedRoles = storedRoles.map(renameOf)
          if (renamedRoles != storedRoles) {
            group.ListOfRoles(renamedRoles.mkString(","))
            renamedGroups += 1
          }
          if (group.BankId.get.isEmpty) {
            if (storedRoles.forall(isOrganisationRole)) {
              group.BankId(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
              movedGroups += 1
            } else mixedGroups += group.GroupName.get
          }
          group.save
        }
      }
      val mixedNote =
        if (mixedGroups.isEmpty) ""
        else s"; left at the empty bank id because they also hold Roles that belong there, so their " +
          s"Organisation Roles need granting at $DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID another way: ${mixedGroups.mkString(", ")}"
      report += s"Groups: renamed Roles on $renamedGroups group(s), moved $movedGroups to $DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID$mixedNote"
    }

    val endDate = System.currentTimeMillis()
    saveLog(name, APIUtil.gitCommit, isSuccessful = true, startDate, endDate, report.mkString("; "))
    true
  }
}
