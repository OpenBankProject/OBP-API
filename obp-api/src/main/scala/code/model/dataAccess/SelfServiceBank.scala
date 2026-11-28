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
package code.model.dataAccess

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.util.Helpers.tryo

/**
 * The banks made with Create My Bank (POST /my/banks): a space of their own for developers, without CanCreateBank.
 * Anyone may make one and grant themselves its Roles, so those Roles vouch for nothing: files stored there start
 * pending, for example. The Create My Bank quota counts these rows.
 *
 * Create My Bank refuses unless OBP stores banks itself (the mapped connector), so a self-service bank is never on a
 * backend system. A bank from a backend is never in this record, and counts as a real bank.
 *
 * Upstream declares this as a Lift Mapper entity; here it is a Doobie store over the table the changelog creates
 * (db.changelog-develop-merge-3.yaml), unique on bankid.
 */
object SelfServiceBank {

  /** Whether the bank was made with Create My Bank. SYS and banks from a backend are not. */
  def isSelfService(bankId: String): Boolean =
    DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM selfservicebank WHERE bankid = ${Option(bankId)}".query[Long].unique) > 0

  def record(bankId: String, createdByUserId: String): Unit =
    if (!isSelfService(bankId)) tryo {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO selfservicebank (bankid, createdbyuserid, createdat)
              VALUES (${Option(bankId)}, ${Option(createdByUserId)},
               ${new java.sql.Timestamp(System.currentTimeMillis())})""".update.run)
    }

  /** Every self-service bank's id. */
  def allBankIds(): List[String] =
    DoobieUtil.runQuery(sql"SELECT bankid FROM selfservicebank ORDER BY id ASC".query[Option[String]].to[List]).flatten

  /** How many self-service banks these users made, which is what the Create My Bank quota counts. */
  def countByCreatedByUserIds(userIds: List[String]): Long =
    if (userIds.isEmpty) 0L
    else DoobieUtil.runQuery(
      (fr"SELECT COUNT(*) FROM selfservicebank WHERE" ++
        Fragments.in(fr"createdbyuserid", cats.data.NonEmptyList.fromListUnsafe(userIds.distinct)))
        .query[Long].unique)

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM selfservicebank".update.run)
    ()
  }
}
