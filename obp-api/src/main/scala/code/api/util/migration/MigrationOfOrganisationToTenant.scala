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
import code.tenant.Tenant
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier

/**
 * Organisation is now Tenant: a customer of the operator of this instance, which belongs to no bank.
 *
 * 1. Rows of the old table Organisation are copied into Tenant, without their bank id. A row whose id is already in
 *    Tenant is not copied again. Tenants have no members, so OrganisationMember is not copied. The old tables are
 *    left as they are; nothing reads them any more.
 * 2. The four Roles are renamed (CanCreateOrganisation to CanCreateTenant, CanGetOrganisation and its older name
 *    CanGetAnyOrganisation to CanGetTenant, and so on) and become system Roles: a grant at SYS or at the empty bank
 *    id moves to the empty bank id. A grant at another bank is left as it was, under its old name, which nothing
 *    checks: a system Role is never widened from one bank to all of them. Such grants are counted in the log. The
 *    four Organisation member Roles have no successor and are left as they are.
 *
 * Four stores hold Role names: Entitlements, Entitlement Requests, Consumer Scopes, and the comma-joined list on a
 * Group. A Group at SYS holding only these Roles moves to the empty bank id with them; a mixed one stays, renamed,
 * and is named in the log.
 */
object MigrationOfOrganisationToTenant {

  val roleRenames: Map[String, String] = Map(
    "CanCreateOrganisation" -> "CanCreateTenant",
    "CanGetOrganisation" -> "CanGetTenant",
    "CanGetAnyOrganisation" -> "CanGetTenant",
    "CanUpdateOrganisation" -> "CanUpdateTenant",
    "CanDeleteOrganisation" -> "CanDeleteTenant"
  )

  private def systemLevel(bankId: String): Boolean =
    bankId == null || bankId.isEmpty || bankId == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID

  def migrate(name: String): Boolean = {
    val startDate = System.currentTimeMillis()
    val report = scala.collection.mutable.ListBuffer[String]()

    // SQL for the old tables: their Mapper classes are gone.
    def copyRows(oldTable: String, label: String)(copy: java.sql.ResultSet => Boolean): Unit =
      if (DbFunction.tableExistsByName(oldTable)) {
        var copied = 0
        DB.use(DefaultConnectionIdentifier) { connection =>
          val statement = connection.prepareStatement(s"SELECT * FROM $oldTable")
          try {
            val rows = statement.executeQuery()
            while (rows.next()) if (copy(rows)) copied += 1
          } finally statement.close()
        }
        report += s"$label: copied $copied row(s)"
      }

    copyRows("organisation", "Tenants") { row =>
      val tenantId = row.getString("organisationid")
      Tenant.findByTenantId(tenantId).isEmpty && scala.util.Try {
        val created = Tenant.insert(tenantId, row.getString("name"), row.getString("website"),
          row.getString("logourl"), row.getString("status"), row.getString("visibility"),
          row.getString("createdbyuserid"))
        // The copy keeps the dates the Organisation had rather than the moment it was copied.
        Option(row.getTimestamp("lastupdate")).foreach(d => Tenant.update(created.copy(updatedAt = new java.util.Date(d.getTime))))
        Option(row.getTimestamp("creationdate")).foreach(Tenant.setCreationDate(tenantId, _))
      }.isSuccess
    }

    /** Rename each of these Roles held at SYS or the empty bank id, and make it a system Role. */
    def moveRows(label: String, rows: List[(String, String, (String, String) => Unit)]): Unit = {
      var changed = 0
      var failed = 0
      var leftAtBanks = 0
      rows.foreach { case (roleName, bankId, save) =>
        roleRenames.get(roleName).foreach { newName =>
          if (!systemLevel(bankId)) leftAtBanks += 1
          // A row the same holder already has would break a unique index; it is counted, not fatal.
          else if (scala.util.Try(save(newName, "")).isSuccess) changed += 1 else failed += 1
        }
      }
      val notes = List(
        if (failed == 0) "" else s"; $failed could not be saved, most likely already held",
        if (leftAtBanks == 0) "" else s"; $leftAtBanks granted at a bank left unchanged, as nothing checks them"
      ).mkString
      report += s"$label: changed $changed row(s)$notes"
    }

    RoleRows.roleTables.foreach { case (table, label) =>
      if (DbFunction.tableExistsByName(table)) {
        moveRows(label, RoleRows.rows(table).map(row =>
          (row.roleName, row.bankId, (n: String, b: String) => RoleRows.save(row, n, b))))
      }
    }
    if (DbFunction.tableExistsByName("groupofroles")) {
      var renamedGroups = 0
      var movedGroups = 0
      val mixedGroups = scala.collection.mutable.ListBuffer[String]()
      RoleRows.groups().foreach { stored =>
        val storedRoles = stored.listOfRoles.split(",").toList.map(_.trim).filter(_.nonEmpty)
        if (storedRoles.exists(roleRenames.contains) && systemLevel(stored.bankId)) {
          var group = stored.copy(listOfRoles = storedRoles.map(r => roleRenames.getOrElse(r, r)).mkString(","))
          renamedGroups += 1
          if (stored.bankId == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID) {
            if (storedRoles.forall(roleRenames.contains)) {
              group = group.copy(bankId = "")
              movedGroups += 1
            } else mixedGroups += stored.groupName
          }
          RoleRows.saveGroup(group)
        }
      }
      val mixedNote =
        if (mixedGroups.isEmpty) ""
        else s"; left at $DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID because they also hold Roles that belong there, so their " +
          s"Tenant Roles need granting as system Roles another way: ${mixedGroups.mkString(", ")}"
      report += s"Groups: renamed Roles on $renamedGroups group(s), moved $movedGroups to the system level$mixedNote"
    }

    saveLog(name, APIUtil.gitCommit, isSuccessful = true, startDate, System.currentTimeMillis(), report.mkString("; "))
    true
  }
}
