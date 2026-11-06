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
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, Http4sRoute}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{APIUtil, ApiRole, CustomJsonFormats, NewStyle}
import code.entitlement.Entitlement
import code.group.{GroupMemberships, GroupTrait}
import code.users.{Users => UserVend}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Empty, Full}
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
case class UserGroupSyncJsonV700(
  group_id: String,
  bank_id: Option[String],
  group_deleted: Boolean,
  entitlements_created: List[String],
  entitlements_deleted: List[String],
  entitlements_moved: List[GroupMemberRoleMovedJsonV700]
)
case class UserGroupsSyncJsonV700(
  user_id: String,
  username: String,
  dry_run: Boolean,
  groups: List[UserGroupSyncJsonV700]
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

  private def isDryRun(req: Request[IO]): Boolean =
    req.uri.query.params.get("dry_run").exists(_.equalsIgnoreCase("true"))

  private def missingRolesMessage = UserHasMissingRoles + addRoles.mkString(" or ") + " and " + removeRoles.mkString(" or ")

  private def usernameOf(userId: String): String = UserVend.users.vend.getUserByUserId(userId).map(_.name).getOrElse("")

  /**
   * Bring one member's Entitlements in line with the Group's Roles. Changes nothing when `dryRun`.
   * `alsoHeld`: Roles at the Group's bank id an earlier Group of this dry run would have granted.
   */
  private def syncMember(group: GroupTrait, userId: String, grantedBy: String, dryRun: Boolean,
                         alsoHeld: Set[String] = Set.empty): GroupMemberSyncJsonV700 = {
    val bankId = group.bankId.getOrElse("")
    val held = Entitlement.entitlement.vend.getEntitlementsByUserId(userId).toList.flatten.filter(_.bankId == bankId)
    val heldRoles = held.map(_.roleName).toSet ++ alsoHeld

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
      username = usernameOf(userId),
      entitlements_created = toCreate,
      entitlements_deleted = toDelete.map(_._1.roleName).sorted,
      entitlements_moved = toMove.map { case (e, other) => GroupMemberRoleMovedJsonV700(e.roleName, other.get.groupId) }
        .sortBy(_.role_name)
    )
  }

  // Route: POST /obp/v7.0.0/management/groups/GROUP_ID/sync-members
  lazy val syncGroupMembers: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "management" / "groups" / groupId / "sync-members" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        val dryRun = isDryRun(req)
        for {
          group <- Future(GroupTrait.group.vend.getGroup(groupId))
            .map(APIUtil.unboxFullOrFail(_, Some(cc), s"$UnknownError Group not found", 404))
          _ <- Helper.booleanToFuture(missingRolesMessage, failCode = 403, cc = Some(cc))(mayAddAndRemove(group.bankId, user.userId))
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

  /**
   * The user's Entitlements recorded against a Group that has since been deleted (deleting a Group
   * leaves them in place): each is moved to another Group of the user's that grants the Role, or
   * deleted. Changes nothing when `dryRun`.
   */
  private def syncDeletedGroup(groupId: String, orphans: List[Entitlement], userId: String, dryRun: Boolean): UserGroupSyncJsonV700 = {
    val (toMove, toDelete) = orphans
      .map(e => (e, GroupMemberships.otherGroupGranting(userId, e.bankId, e.roleName, groupId)))
      .partition(_._2.isDefined)
    if (!dryRun) {
      toMove.foreach { case (e, other) => Entitlement.entitlement.vend.setEntitlementGroupId(e.entitlementId, other.get.groupId) }
      toDelete.foreach { case (e, _) => Entitlement.entitlement.vend.deleteEntitlement(Full(e)) }
    }
    UserGroupSyncJsonV700(
      group_id = groupId,
      bank_id = orphans.headOption.map(_.bankId).filter(_.nonEmpty),
      group_deleted = true,
      entitlements_created = Nil,
      entitlements_deleted = toDelete.map(_._1.roleName).sorted,
      entitlements_moved = toMove.map { case (e, other) => GroupMemberRoleMovedJsonV700(e.roleName, other.get.groupId) }
        .sortBy(_.role_name)
    )
  }

  // Route: POST /obp/v7.0.0/management/groups/GROUP_ID/users/USER_ID/sync
  lazy val syncGroupMember: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "management" / "groups" / groupId / "users" / userId / "sync" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        val dryRun = isDryRun(req)
        for {
          group <- Future(GroupTrait.group.vend.getGroup(groupId))
            .map(APIUtil.unboxFullOrFail(_, Some(cc), s"$UnknownError Group not found", 404))
          _ <- Helper.booleanToFuture(missingRolesMessage, failCode = 403, cc = Some(cc))(mayAddAndRemove(group.bankId, user.userId))
          _ <- NewStyle.function.findByUserId(userId, Some(cc))
          _ <- Helper.booleanToFuture(s"$UnknownError Group is not enabled", 400, Some(cc))(group.isEnabled)
          granted <- Entitlement.entitlement.vend.getEntitlementsByGroupId(groupId)
            .map(APIUtil.unboxFullOrFail(_, Some(cc), s"$UnknownError Cannot get entitlements", 400))
          _ <- Helper.booleanToFuture(s"$UnknownError The User is not a member of the Group", 404, Some(cc))(
            GroupMemberships.userIdsOfGroup(groupId, granted).contains(userId))
          synced <- Future(syncMember(group, userId, user.userId, dryRun))
        } yield GroupMembersSyncJsonV700(group.groupId, group.bankId, dryRun, List(synced))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(syncGroupMember),
    "POST",
    "/management/groups/GROUP_ID/users/USER_ID/sync",
    "Sync Group Member",
    s"""Bring the Entitlements of one member of a Group in line with the Group's current Roles.
       |
       |The same as Sync Group Members (POST /management/groups/GROUP_ID/sync-members), for one member only:
       |
       |- a Role of the Group the member does not hold at the Group's bank id is granted (the member gets
       |  an email for it), recorded against this Group;
       |- an Entitlement this Group granted, for a Role the Group no longer has, is deleted, unless another
       |  Group the member is in, at the same bank id, still grants that Role: then it is kept and recorded
       |  against that Group (no email; the member's Roles do not change).
       |
       |Entitlements granted by hand, or by other Groups, are not touched. The user is not added to or
       |removed from the Group.
       |
       |The user must be a member of the Group (added to it, or holding an Entitlement it granted), or 404.
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
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, UserNotFoundById, UnknownError),
    List(apiTagGroup, apiTagUser, apiTagEntitlement),
    None,
    http4sPartialFunction = Some(syncGroupMember)
  )

  // Route: POST /obp/v7.0.0/management/users/USER_ID/sync-groups
  lazy val syncUserGroups: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "management" / "users" / userId / "sync-groups" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        val dryRun = isDryRun(req)
        for {
          _ <- NewStyle.function.findByUserId(userId, Some(cc))
          held = Entitlement.entitlement.vend.getEntitlementsByUserId(userId).toList.flatten
          found = GroupMemberships.groupIdsOfUser(userId).map(id => id -> GroupTrait.group.vend.getGroup(id))
          groups = found.collect { case (_, Full(g)) if g.isEnabled => g }
          deleted = found.collect { case (id, Empty) => id -> held.filter(_.groupId.contains(id)) }.filter(_._2.nonEmpty)
          bankIds = (groups.map(_.bankId) ++ deleted.flatMap(_._2).map(e => Some(e.bankId).filter(_.nonEmpty))).distinct
          _ <- Helper.booleanToFuture(missingRolesMessage, failCode = 403, cc = Some(cc))(
            bankIds.forall(mayAddAndRemove(_, user.userId)))
          synced <- Future {
            val fromDeleted = deleted.map { case (id, orphans) => syncDeletedGroup(id, orphans, userId, dryRun) }
            // In a dry run nothing is granted, so a Role an earlier Group would grant is passed on to the later ones.
            val (fromGroups, _) = groups.foldLeft((List.empty[UserGroupSyncJsonV700], Map.empty[String, Set[String]])) {
              case ((done, planned), g) =>
                val bankId = g.bankId.getOrElse("")
                val m = syncMember(g, userId, user.userId, dryRun, planned.getOrElse(bankId, Set.empty))
                (done :+ UserGroupSyncJsonV700(g.groupId, g.bankId, group_deleted = false,
                  m.entitlements_created, m.entitlements_deleted, m.entitlements_moved),
                  planned.updated(bankId, planned.getOrElse(bankId, Set.empty) ++ m.entitlements_created))
            }
            fromDeleted ++ fromGroups
          }
        } yield UserGroupsSyncJsonV700(userId, usernameOf(userId), dryRun, synced)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(syncUserGroups),
    "POST",
    "/management/users/USER_ID/sync-groups",
    "Sync User Groups",
    s"""Bring a user's Entitlements in line with every Group they are in, and clear what is left of
       |Groups that have been deleted.
       |
       |For each enabled Group the user is in (added to it, or holding an Entitlement it granted), at any
       |bank id, the same as Sync Group Member (POST /management/groups/GROUP_ID/users/USER_ID/sync):
       |
       |- a Role of the Group the user does not hold at the Group's bank id is granted (the user gets an
       |  email for it), recorded against that Group;
       |- an Entitlement the Group granted, for a Role the Group no longer has, is deleted, unless another
       |  Group the user is in, at the same bank id, still grants that Role: then it is kept and recorded
       |  against that Group.
       |
       |Deleting a Group leaves the Entitlements it granted in place. Each of the user's Entitlements
       |recorded against a deleted Group is moved to another Group the user is in that grants the Role at
       |that bank id, or else deleted. These are listed with `group_deleted: true`.
       |
       |Disabled Groups are left as they are. Entitlements granted by hand are not touched. The user is not
       |added to or removed from any Group.
       |
       |With `dry_run=true` nothing is changed and the response says what would be.
       |
       |Requires CanAddUserToGroupAtOneBank or CanAddUserToGroupAtAllBanks, and
       |CanRemoveUserFromGroupAtOneBank or CanRemoveUserFromGroupAtAllBanks, at the bank id of every Group
       |involved (the AllBanks Roles for a system level Group). If any is missing, nothing is changed.
       |""".stripMargin,
    EmptyBody,
    UserGroupsSyncJsonV700(
      user_id = "user-id-123",
      username = "felixsmith",
      dry_run = false,
      groups = List(
        UserGroupSyncJsonV700(
          group_id = "group-id-123",
          bank_id = Some("gh.29.uk"),
          group_deleted = false,
          entitlements_created = List("CanGetCustomer"),
          entitlements_deleted = List("CanCreateTransaction"),
          entitlements_moved = List(GroupMemberRoleMovedJsonV700("CanGetAccount", "group-id-456"))
        ),
        UserGroupSyncJsonV700(
          group_id = "group-id-789",
          bank_id = Some("gh.29.uk"),
          group_deleted = true,
          entitlements_created = Nil,
          entitlements_deleted = List("CanGetTransaction"),
          entitlements_moved = Nil
        )
      )
    ),
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, UserNotFoundById, UnknownError),
    List(apiTagGroup, apiTagUser, apiTagEntitlement),
    None,
    http4sPartialFunction = Some(syncUserGroups)
  )
}
