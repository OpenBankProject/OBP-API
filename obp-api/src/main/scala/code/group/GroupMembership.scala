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

import code.entitlement.{Entitlement, EntitlementProvider}
import code.util.UUIDString
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

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
    GroupMembership.find(By(GroupMembership.GroupId, groupId), By(GroupMembership.UserId, userId)) match {
      case Full(existing) => Full(existing)
      case _ =>
        tryo {
          GroupMembership.create
            .GroupId(groupId)
            .UserId(userId)
            .CreatedByUserId(createdByUserId.getOrElse(""))
            .saveMe()
        }
    }

  def removeMembership(groupId: String, userId: String): Box[Boolean] =
    tryo {
      GroupMembership.findAll(By(GroupMembership.GroupId, groupId), By(GroupMembership.UserId, userId))
        .forall(_.delete_!)
    }

  def removeMembershipsOfGroup(groupId: String): Box[Boolean] =
    tryo {
      GroupMembership.findAll(By(GroupMembership.GroupId, groupId)).forall(_.delete_!)
    }

  /** The ids of the Groups the user is in: membership rows, plus the Group ids on their Entitlements. */
  def groupIdsOfUser(userId: String): List[String] = {
    val rows = GroupMembership.findAll(By(GroupMembership.UserId, userId)).map(_.GroupId.get)
    val tagged = entitlements.getEntitlementsByUserId(userId).toList.flatten.flatMap(_.groupId)
    (rows ++ tagged).distinct
  }

  /** The user ids of a Group's members: membership rows, plus the users holding an Entitlement it granted. */
  def userIdsOfGroup(groupId: String, groupEntitlements: List[Entitlement]): List[String] = {
    val rows = GroupMembership.findAll(By(GroupMembership.GroupId, groupId)).map(_.UserId.get)
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

class GroupMembership extends LongKeyedMapper[GroupMembership] with IdPK with CreatedUpdated {

  def getSingleton = GroupMembership

  object GroupId extends UUIDString(this)
  object UserId extends UUIDString(this)
  object CreatedByUserId extends UUIDString(this) {
    override def defaultValue = ""
  }
}

object GroupMembership extends GroupMembership with LongKeyedMetaMapper[GroupMembership] {
  override def dbTableName = "GroupMembership"
  override def dbIndexes = UniqueIndex(GroupId, UserId) :: Index(UserId) :: super.dbIndexes
}
