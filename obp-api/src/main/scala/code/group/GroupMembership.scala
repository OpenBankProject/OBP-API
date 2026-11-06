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

package code.group

import code.api.util.DoobieUtil
import code.entitlement.{Entitlement, EntitlementProvider}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.util.Date

/**
 * Who is in which Group.
 *
 * A Group grants its Roles as ordinary Entitlements, each tagged with the Group's id, and an
 * Entitlement is unique per (bank id, user, Role). So when two Groups share a Role, the user holds
 * it once, tagged with whichever Group granted it first, and a Group whose Roles the user already
 * held leaves no Entitlement behind at all. The Entitlements therefore cannot say reliably who is in
 * a Group. This table does: a row is written when a user is added to a Group and removed when they
 * are taken out of it. Entitlements stay the only thing a Role check reads.
 *
 * Rows only exist for users added since the table was introduced, so every question here also
 * counts the Group ids found on the user's Entitlements (the way membership was worked out before).
 */
object GroupMemberships {

  private def entitlements: EntitlementProvider = Entitlement.entitlement.vend

  def addMembership(groupId: String, userId: String, createdByUserId: Option[String]): Box[GroupMembership] =
    GroupMembership.find(groupId, userId) match {
      case Full(existing) => Full(existing)
      case _ =>
        tryo {
          GroupMembership.insert(groupId, userId, createdByUserId.getOrElse(""))
        }
    }

  def removeMembership(groupId: String, userId: String): Box[Boolean] =
    tryo {
      GroupMembership.findAll(fr"WHERE groupid = ${Option(groupId)} AND userid = ${Option(userId)}")
        .forall(row => GroupMembership.delete(row.id))
    }

  def removeMembershipsOfGroup(groupId: String): Box[Boolean] =
    tryo {
      GroupMembership.findAll(fr"WHERE groupid = ${Option(groupId)}").forall(row => GroupMembership.delete(row.id))
    }

  /** The ids of the Groups the user is in: membership rows, plus the Group ids on their Entitlements. */
  def groupIdsOfUser(userId: String): List[String] = {
    val rows = GroupMembership.findAll(fr"WHERE userid = ${Option(userId)}").map(_.groupId)
    val tagged = entitlements.getEntitlementsByUserId(userId).toList.flatten.flatMap(_.groupId)
    (rows ++ tagged).distinct
  }

  /** The user ids of a Group's members: membership rows, plus the users holding an Entitlement it granted. */
  def userIdsOfGroup(groupId: String, groupEntitlements: List[Entitlement]): List[String] = {
    val rows = GroupMembership.findAll(fr"WHERE groupid = ${Option(groupId)}").map(_.userId)
    (rows ++ groupEntitlements.map(_.userId)).distinct
  }

  /**
   * Another Group the user is in, at `bankId`, that grants `roleName`, other than `excludeGroupId`.
   * An Entitlement a Group granted is moved to such a Group, rather than deleted, when the first
   * Group stops granting the Role to this user.
   */
  def otherGroupGranting(userId: String, bankId: String, roleName: String, excludeGroupId: String): Option[GroupTrait] =
    groupIdsOfUser(userId).iterator
      .filterNot(_ == excludeGroupId)
      .flatMap(gid => GroupTrait.group.vend.getGroup(gid).toOption)
      .find(g => g.bankId.getOrElse("") == bankId && g.listOfRoles.contains(roleName))
}

/**
 * One row of groupmembership. Upstream declares this as a Lift Mapper entity; here it is a Doobie store
 * over the table the changelog creates (db.changelog-develop-merge-3.yaml), unique on (groupid, userid).
 * created_by_user_id is "" when nobody was named, as the entity's default was.
 */
case class GroupMembership(
  id: Long,
  groupId: String,
  userId: String,
  createdByUserId: String,
  createdAt: Date,
  updatedAt: Date
)

object GroupMembership {

  private val selectColumns =
    fr"SELECT id, groupid, userid, createdbyuserid, createdat, updatedat FROM groupmembership"

  private type Row = (Long, Option[String], Option[String], Option[String], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp])

  private def readDate(t: Option[java.sql.Timestamp]): Date = t.map(ts => new Date(ts.getTime)).orNull

  private def fromRow(r: Row): GroupMembership =
    GroupMembership(r._1, r._2.orNull, r._3.orNull, r._4.orNull, readDate(r._5), readDate(r._6))

  private[group] def findAll(condition: Fragment): List[GroupMembership] =
    DoobieUtil.runQuery((selectColumns ++ condition ++ fr"ORDER BY id ASC").query[Row].to[List]).map(fromRow)

  def find(groupId: String, userId: String): Box[GroupMembership] =
    findAll(fr"WHERE groupid = ${Option(groupId)} AND userid = ${Option(userId)}").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /** Writes a membership; both dates are set to now, as Mapper's CreatedUpdated did. */
  def insert(groupId: String, userId: String, createdByUserId: String): GroupMembership = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO groupmembership (groupid, userid, createdbyuserid, createdat, updatedat)
            VALUES (${Option(groupId)}, ${Option(userId)}, ${Option(createdByUserId)}, $now, $now)""".update.run)
    find(groupId, userId).openOr(
      GroupMembership(0L, groupId, userId, createdByUserId, new Date(now.getTime), new Date(now.getTime)))
  }

  def delete(id: Long): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM groupmembership WHERE id = $id".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM groupmembership".update.run)
    ()
  }
}
