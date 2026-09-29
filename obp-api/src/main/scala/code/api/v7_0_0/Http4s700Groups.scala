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
package code.api.v7_0_0

import cats.effect.IO
import code.api.Constant
import code.api.Constant.ApiPathZero
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{APIUtil, ApiRole, CustomJsonFormats}
import code.entitlement.Entitlement
import code.group.{GroupMemberships, GroupTrait}
import code.users.{Users => UserVend}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Full
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

case class GroupMemberRoleMovedJsonV700(role_name: String, to_group_id: String)
case class GroupMemberSyncJsonV700(
  user_id: String,
  username: String,
  entitlements_created: List[String],
  entitlements_deleted: List[String],
  entitlements_moved: List[GroupMemberRoleMovedJsonV700]
)
case class GroupMembersSyncJsonV700(
  group_id: String,
  bank_id: Option[String],
  dry_run: Boolean,
  members: List[GroupMemberSyncJsonV700]
)

object Http4s700Groups {
  implicit val formats: Formats = CustomJsonFormats.formats
  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString
  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private val addRoles = canAddUserToGroupAtOneBank :: canAddUserToGroupAtAllBanks :: Nil
  private val removeRoles = canRemoveUserFromGroupAtOneBank :: canRemoveUserFromGroupAtAllBanks :: Nil

  /** The caller may add users to, and remove users from, a Group at `bankId` (None: a system level Group). */
  private def mayAddAndRemove(bankId: Option[String], userId: String): Boolean = {
    def holds(roles: List[ApiRole]) = bankId match {
      case Some(b) => APIUtil.hasAtLeastOneEntitlement(b, userId, roles)
      case None => APIUtil.hasEntitlement("", userId, roles.last) // the AllBanks Role
    }
    APIUtil.isSuperAdmin(userId) || (holds(addRoles) && holds(removeRoles))
  }

  /** Bring one member's Entitlements in line with the Group's Roles. Changes nothing when `dryRun`. */
  private def syncMember(group: GroupTrait, userId: String, grantedBy: String, dryRun: Boolean): GroupMemberSyncJsonV700 = {
    val bankId = group.bankId.getOrElse("")
    val held = Entitlement.entitlement.vend.getEntitlementsByUserId(userId).toList.flatten.filter(_.bankId == bankId)
    val heldRoles = held.map(_.roleName).toSet

    val toCreate = group.listOfRoles.filterNot(heldRoles.contains).distinct
    val noLongerGranted = held.filter(e => e.groupId.contains(group.groupId) && !group.listOfRoles.contains(e.roleName))
    val (toMove, toDelete) = noLongerGranted
      .map(e => (e, GroupMemberships.otherGroupGranting(userId, bankId, e.roleName, group.groupId)))
      .partition(_._2.isDefined)

    if (!dryRun) {
      GroupMemberships.addMembership(group.groupId, userId, Some(grantedBy))
      toCreate.foreach(role => Entitlement.entitlement.vend.addEntitlement(
        bankId, userId, role, Constant.group_membership, Some(grantedBy), Some(group.groupId)))
      toMove.foreach { case (e, other) => Entitlement.entitlement.vend.setEntitlementGroupId(e.entitlementId, other.get.groupId) }
      toDelete.foreach { case (e, _) => Entitlement.entitlement.vend.deleteEntitlement(Full(e)) }
    }
    GroupMemberSyncJsonV700(
      user_id = userId,
      username = UserVend.users.vend.getUserByUserId(userId).map(_.name).getOrElse(""),
      entitlements_created = toCreate,
      entitlements_deleted = toDelete.map(_._1.roleName).sorted,
      entitlements_moved = toMove.map { case (e, other) => GroupMemberRoleMovedJsonV700(e.roleName, other.get.groupId) }
        .sortBy(_.role_name)
    )
  }

  // Route: POST /obp/v7.0.0/management/groups/GROUP_ID/sync-members
  lazy val syncGroupMembers: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `prefixPath` / "management" / "groups" / groupId / "sync-members" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        val dryRun = req.uri.query.params.get("dry_run").exists(_.equalsIgnoreCase("true"))
        for {
          group <- Future(GroupTrait.group.vend.getGroup(groupId))
            .map(APIUtil.unboxFullOrFail(_, Some(cc), s"$UnknownError Group not found", 404))
          _ <- Helper.booleanToFuture(
            UserHasMissingRoles + addRoles.mkString(" or ") + " and " + removeRoles.mkString(" or "),
            failCode = 403, cc = Some(cc))(mayAddAndRemove(group.bankId, user.userId))
          _ <- Helper.booleanToFuture(s"$UnknownError Group is not enabled", 400, Some(cc))(group.isEnabled)
          granted <- Entitlement.entitlement.vend.getEntitlementsByGroupId(groupId)
            .map(APIUtil.unboxFullOrFail(_, Some(cc), s"$UnknownError Cannot get entitlements", 400))
          members = GroupMemberships.userIdsOfGroup(groupId, granted)
          synced <- Future(members.map(syncMember(group, _, user.userId, dryRun)))
        } yield GroupMembersSyncJsonV700(group.groupId, group.bankId, dryRun, synced)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(syncGroupMembers),
    "POST",
    "/management/groups/GROUP_ID/sync-members",
    "Sync Group Members",
    s"""Bring the Entitlements of every member of a Group in line with the Group's current Roles.
       |
       |A Group grants its Roles when a user is added to it, so changing a Group's Roles does not change
       |what its existing members hold. This endpoint does that, for each member:
       |
       |- a Role of the Group the member does not hold at the Group's bank id is granted (the member gets
       |  an email for it), recorded against this Group;
       |- an Entitlement this Group granted, for a Role the Group no longer has, is deleted, unless another
       |  Group the member is in, at the same bank id, still grants that Role: then it is kept and recorded
       |  against that Group (no email; the member's Roles do not change).
       |
       |Entitlements granted by hand, or by other Groups, are not touched. Nobody is added to or removed
       |from the Group.
       |
       |The members are the users added to the Group, plus the users holding an Entitlement it granted.
       |
       |With `dry_run=true` nothing is changed and the response says what would be.
       |
       |Requires CanAddUserToGroupAtOneBank or CanAddUserToGroupAtAllBanks, and
       |CanRemoveUserFromGroupAtOneBank or CanRemoveUserFromGroupAtAllBanks (the AllBanks Roles for a
       |system level Group).
       |""".stripMargin,
    EmptyBody,
    GroupMembersSyncJsonV700(
      group_id = "group-id-123",
      bank_id = Some("gh.29.uk"),
      dry_run = false,
      members = List(GroupMemberSyncJsonV700(
        user_id = "user-id-123",
        username = "felixsmith",
        entitlements_created = List("CanGetCustomer"),
        entitlements_deleted = List("CanCreateTransaction"),
        entitlements_moved = List(GroupMemberRoleMovedJsonV700("CanGetAccount", "group-id-456"))
      ))
    ),
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
    List(apiTagGroup, apiTagUser, apiTagEntitlement),
    None,
    http4sPartialFunction = Some(syncGroupMembers)
  )
}
