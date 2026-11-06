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

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

/**
 * The four stores that hold Role names, for the migrations that rename Roles: Entitlements, Entitlement
 * Requests and Consumer Scopes, one Role per row, and Groups, which hold a comma-joined list.
 *
 * Upstream's migrations read these through their Lift Mapper entities; this branch has none, so they read
 * and write the rows here, by primary key. A NULL bank id is read as the empty string, which is how the
 * system level was written before the SYS sentinel.
 */
object RoleRows {

  /** One Role held in one row of a one-Role-per-row store. */
  case class RoleRow(table: String, id: Long, roleName: String, bankId: String)

  /** The one-Role-per-row stores, with the label each migration log uses for it. */
  val roleTables: List[(String, String)] = List(
    "mappedentitlement" -> "Entitlements",
    "mappedentitlementrequest" -> "Entitlement Requests",
    "mappedscope" -> "Consumer Scopes")

  def rows(table: String): List[RoleRow] =
    DoobieUtil.runQuery(
      (Fragment.const(s"SELECT id, mrolename, mbankid FROM $table") ++ fr"ORDER BY id ASC")
        .query[(Long, Option[String], Option[String])].to[List])
      .map { case (id, role, bank) => RoleRow(table, id, role.getOrElse(""), bank.getOrElse("")) }

  def save(row: RoleRow, roleName: String, bankId: String): Unit = {
    DoobieUtil.runUpdate(
      (Fragment.const(s"UPDATE ${row.table} SET") ++ fr"mrolename = $roleName, mbankid = $bankId" ++
        fr"WHERE id = ${row.id}").update.run)
    ()
  }

  /** A Group: the Roles it grants, as stored, and the bank it grants them at. */
  case class GroupRow(id: Long, groupName: String, bankId: String, listOfRoles: String)

  def groups(): List[GroupRow] =
    DoobieUtil.runQuery(
      sql"SELECT id, groupname, bankid, listofroles FROM groupofroles ORDER BY id ASC"
        .query[(Long, Option[String], Option[String], Option[String])].to[List])
      .map { case (id, name, bank, roles) => GroupRow(id, name.getOrElse(""), bank.getOrElse(""), roles.getOrElse("")) }

  def saveGroup(group: GroupRow): Unit = {
    DoobieUtil.runUpdate(
      sql"""UPDATE groupofroles SET listofroles = ${group.listOfRoles}, bankid = ${group.bankId},
              updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}
            WHERE id = ${group.id}""".update.run)
    ()
  }
}
