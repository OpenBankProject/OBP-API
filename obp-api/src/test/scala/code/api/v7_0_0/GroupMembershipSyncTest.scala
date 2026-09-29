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

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, BankNotFound, UserHasMissingRoles}
import code.api.v6_0_0.V600ServerSetup
import code.entitlement.Entitlement
import code.group.{GroupMemberships, GroupTrait}
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.{compact, render}
import org.scalatest.Tag

/**
 * Group memberships are recorded apart from the Entitlements a Group grants, so a Group whose Roles a
 * user already held still counts them as a member; a Role two of the user's Groups grant is kept when
 * one of them stops granting it; and sync-members brings members in line with a changed Group.
 */
class GroupMembershipSyncTest extends V600ServerSetup {

  def v7 = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object SyncGroupMembers extends Tag("syncGroupMembers")
  object AddEntitlement extends Tag("addEntitlement")

  private def bankId = testBankId1.value
  private def grant(role: ApiRole, bank: String = "") =
    Entitlement.entitlement.vend.addEntitlement(bank, resourceUser1.userId, role.toString)
  private def message(r: code.setup.APIResponse) = r.body.extract[ErrorMessage].message
  private def sync(groupId: String) = v7 / "management" / "groups" / groupId / "sync-members"
  private def membership(groupId: String) = compact(render("group_id" -> groupId))
  private def user2Entitlements =
    Entitlement.entitlement.vend.getEntitlementsByUserId(resourceUser2.userId).toList.flatten.filter(_.bankId == bankId)
  private def roleGroup(role: String): Option[String] = user2Entitlements.find(_.roleName == role).flatMap(_.groupId)

  // Any Role names do: a Group's Roles are stored as text and granted as Entitlements.
  private val r1 = "CanGetCustomersAtOneBank"
  private val r2 = "CanCreateCustomer"
  private val r3 = "CanGetCustomer"

  feature("Add Entitlement refuses a bank id that names no bank") {
    scenario("a bank id that exists, differs only in case, SYS, or does not exist", AddEntitlement, VersionOfApi) {
      grant(canCreateEntitlementAtAnyBank)
      def add(bank: String) = makePostRequest((v7 / "users" / resourceUser2.userId / "entitlements").POST <@ (user1),
        compact(render(("bank_id" -> bank) ~ ("role_name" -> canCreateCustomer.toString))))

      Then("an unknown bank id is 404, and nothing is granted")
      val unknown = add("no-such-bank-" + java.util.UUID.randomUUID().toString.take(8))
      unknown.code should equal(404)
      message(unknown) should startWith(BankNotFound)

      Then("a bank id matching an existing one apart from case is 404 too")
      if (bankId != bankId.toUpperCase) add(bankId.toUpperCase).code should equal(404)

      Then("SYS, the system space, is accepted")
      add(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID).code should equal(201)

      Then("an existing bank id is accepted")
      add(bankId).code should equal(201)
    }
  }

  feature("Sync Group Members") {
    scenario("unauthenticated, and without the Roles", SyncGroupMembers, VersionOfApi) {
      val group = GroupTrait.group.vend.createGroup(Some(bankId), "sync-auth", "", List(r1), isEnabled = true).openOrThrowException("group")
      val anonymous = makePostRequest(sync(group.groupId).POST, "")
      anonymous.code should equal(401)
      message(anonymous) should equal(AuthenticatedUserIsRequired)

      grant(canAddUserToGroupAtOneBank, bankId)
      val withAddOnly = makePostRequest(sync(group.groupId).POST <@ (user1), "")
      withAddOnly.code should equal(403)
      message(withAddOnly) should startWith(UserHasMissingRoles)
    }

    scenario("overlapping Groups: membership, sync, and removal", SyncGroupMembers, VersionOfApi) {
      grant(canAddUserToGroupAtAllBanks)
      grant(canRemoveUserFromGroupAtAllBanks)
      grant(canGetUserGroupMembershipsAtAllBanks)
      val a = GroupTrait.group.vend.createGroup(Some(bankId), "sync-A", "", List(r1, r2), isEnabled = true).openOrThrowException("A")
      val b = GroupTrait.group.vend.createGroup(Some(bankId), "sync-B", "", List(r2), isEnabled = true).openOrThrowException("B")
      val addUser2 = (v6_0_0_Request / "users" / resourceUser2.userId / "group-entitlements").POST <@ (user1)

      When("user2 is added to A, then to B, whose only Role A already gave them")
      makePostRequest(addUser2, membership(a.groupId)).code should equal(201)
      makePostRequest(addUser2, membership(b.groupId)).code should equal(201)
      roleGroup(r2) should equal(Some(a.groupId))

      Then("user2 is still a member of B, although B granted them nothing")
      GroupMemberships.groupIdsOfUser(resourceUser2.userId) should contain allOf (a.groupId, b.groupId)
      val memberships = makeGetRequest((v6_0_0_Request / "users" / resourceUser2.userId / "group-entitlements").GET <@ (user1))
      memberships.code should equal(200)
      (memberships.body \ "group_entitlements").children.map(m => (m \ "group_id").extract[String]) should contain allOf (a.groupId, b.groupId)

      When("A's Roles change to r1 and r3, and A is synced as a dry run")
      GroupTrait.group.vend.updateGroup(a.groupId, None, None, Some(List(r1, r3)), None)
      val dry = makePostRequest(sync(a.groupId).POST <@ (user1) <<? List(("dry_run", "true")), "")
      dry.code should equal(200)
      val dryMember = (dry.body \ "members").children.find(m => (m \ "user_id").extract[String] == resourceUser2.userId).get
      (dryMember \ "entitlements_created").extract[List[String]] should equal(List(r3))
      (dryMember \ "entitlements_deleted").extract[List[String]] shouldBe empty
      (dryMember \ "entitlements_moved").children.map(m => (m \ "to_group_id").extract[String]) should equal(List(b.groupId))
      Then("nothing changed")
      user2Entitlements.map(_.roleName) should not contain r3
      roleGroup(r2) should equal(Some(a.groupId))

      When("A is synced for real")
      makePostRequest(sync(a.groupId).POST <@ (user1), "").code should equal(200)
      Then("user2 gains r3 from A, and keeps r2, now recorded against B")
      roleGroup(r3) should equal(Some(a.groupId))
      roleGroup(r2) should equal(Some(b.groupId))
      roleGroup(r1) should equal(Some(a.groupId))

      When("user2 is removed from B, and no other Group of theirs grants r2")
      makeDeleteRequest((v6_0_0_Request / "users" / resourceUser2.userId / "group-entitlements" / b.groupId) <@ (user1))
        .code should (equal(200) or equal(204))
      Then("r2 is gone, and so is the membership")
      roleGroup(r2) should equal(None)
      GroupMemberships.groupIdsOfUser(resourceUser2.userId) should not contain b.groupId

      When("A goes back to granting r2 and user2 is added to B again, then removed from A")
      GroupTrait.group.vend.updateGroup(a.groupId, None, None, Some(List(r1, r2)), None)
      makePostRequest(sync(a.groupId).POST <@ (user1), "").code should equal(200)
      makePostRequest(addUser2, membership(b.groupId)).code should equal(201)
      roleGroup(r2) should equal(Some(a.groupId))
      makeDeleteRequest((v6_0_0_Request / "users" / resourceUser2.userId / "group-entitlements" / a.groupId) <@ (user1))
      Then("r2 is kept, recorded against B; r1 and r3, which only A granted, are gone")
      roleGroup(r2) should equal(Some(b.groupId))
      roleGroup(r1) should equal(None)
      roleGroup(r3) should equal(None)
    }
  }
}
