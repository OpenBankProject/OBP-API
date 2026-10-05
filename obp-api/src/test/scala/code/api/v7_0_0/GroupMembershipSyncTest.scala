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
  object SyncGroupMember extends Tag("syncGroupMember")
  object SyncUserGroups extends Tag("syncUserGroups")
  object AddEntitlement extends Tag("addEntitlement")

  private def bankId = testBankId1.value
  private def grant(role: ApiRole, bank: String = "") =
    Entitlement.entitlement.vend.addEntitlement(bank, resourceUser1.userId, role.toString)
  private def message(r: code.setup.APIResponse) = r.body.extract[ErrorMessage].message
  private def sync(groupId: String) = v7 / "management" / "groups" / groupId / "sync-members"
  private def syncOne(groupId: String, userId: String) = v7 / "management" / "groups" / groupId / "users" / userId / "sync"
  private def syncUser(userId: String) = v7 / "management" / "users" / userId / "sync-groups"
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

      makePostRequest(syncOne(group.groupId, resourceUser2.userId).POST, "").code should equal(401)
      val oneWithAddOnly = makePostRequest(syncOne(group.groupId, resourceUser2.userId).POST <@ (user1), "")
      oneWithAddOnly.code should equal(403)
      message(oneWithAddOnly) should startWith(UserHasMissingRoles)

      makePostRequest(syncUser(resourceUser2.userId).POST, "").code should equal(401)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser2.userId, "CanTestSyncAuthRole", groupId = Some(group.groupId))
      val userWithAddOnly = makePostRequest(syncUser(resourceUser2.userId).POST <@ (user1), "")
      userWithAddOnly.code should equal(403)
      message(userWithAddOnly) should startWith(UserHasMissingRoles)
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

  feature("Sync Group Member") {
    scenario("one member of a changed Group, and a user who is not a member", SyncGroupMember, VersionOfApi) {
      grant(canAddUserToGroupAtAllBanks)
      grant(canRemoveUserFromGroupAtAllBanks)
      val s1 = "CanTestSyncOneRole1"
      val s2 = "CanTestSyncOneRole2"
      val g = GroupTrait.group.vend.createGroup(Some(bankId), "sync-one", "", List(s1), isEnabled = true).openOrThrowException("g")
      makePostRequest((v6_0_0_Request / "users" / resourceUser2.userId / "group-entitlements").POST <@ (user1),
        membership(g.groupId)).code should equal(201)

      When("the Group's Roles change from s1 to s2, and user2 is synced as a dry run")
      GroupTrait.group.vend.updateGroup(g.groupId, None, None, Some(List(s2)), None)
      val dry = makePostRequest(syncOne(g.groupId, resourceUser2.userId).POST <@ (user1) <<? List(("dry_run", "true")), "")
      dry.code should equal(200)
      val members = (dry.body \ "members").children
      members.map(m => (m \ "user_id").extract[String]) should equal(List(resourceUser2.userId))
      (members.head \ "entitlements_created").extract[List[String]] should equal(List(s2))
      (members.head \ "entitlements_deleted").extract[List[String]] should equal(List(s1))
      Then("nothing changed")
      roleGroup(s1) should equal(Some(g.groupId))
      roleGroup(s2) should equal(None)

      When("user2 is synced for real")
      makePostRequest(syncOne(g.groupId, resourceUser2.userId).POST <@ (user1), "").code should equal(200)
      Then("user2 holds s2 from the Group, and not s1")
      roleGroup(s2) should equal(Some(g.groupId))
      roleGroup(s1) should equal(None)

      Then("user1, who is not a member, is 404, and so is an unknown user")
      makePostRequest(syncOne(g.groupId, resourceUser1.userId).POST <@ (user1), "").code should equal(404)
      makePostRequest(syncOne(g.groupId, "no-such-user").POST <@ (user1), "").code should equal(404)
    }
  }

  feature("Sync User Groups") {
    scenario("every Group of a user, including one that was deleted", SyncUserGroups, VersionOfApi) {
      grant(canAddUserToGroupAtAllBanks)
      grant(canRemoveUserFromGroupAtAllBanks)
      val u5 = "CanTestSyncUserRole5"
      val u6 = "CanTestSyncUserRole6"
      val u7 = "CanTestSyncUserRole7"
      val u8 = "CanTestSyncUserRole8"
      val u9 = "CanTestSyncUserRole9"
      val c = GroupTrait.group.vend.createGroup(Some(bankId), "sync-user-C", "", List(u5, u6), isEnabled = true).openOrThrowException("C")
      val d = GroupTrait.group.vend.createGroup(Some(bankId), "sync-user-D", "", List(u6), isEnabled = true).openOrThrowException("D")
      val e = GroupTrait.group.vend.createGroup(Some(bankId), "sync-user-E", "", List(u7), isEnabled = true).openOrThrowException("E")
      val addUser2 = (v6_0_0_Request / "users" / resourceUser2.userId / "group-entitlements").POST <@ (user1)
      List(c, d, e).foreach(g => makePostRequest(addUser2, membership(g.groupId)).code should equal(201))
      roleGroup(u6) should equal(Some(c.groupId))

      When("E is deleted (its Entitlements stay), C now grants u5 and u8, and D grants u6, u8 and u9")
      GroupTrait.group.vend.deleteGroup(e.groupId)
      GroupMemberships.removeMembershipsOfGroup(e.groupId)
      roleGroup(u7) should equal(Some(e.groupId))
      GroupTrait.group.vend.updateGroup(c.groupId, None, None, Some(List(u5, u8)), None)
      GroupTrait.group.vend.updateGroup(d.groupId, None, None, Some(List(u6, u8, u9)), None)

      When("user2's Groups are synced as a dry run")
      val dry = makePostRequest(syncUser(resourceUser2.userId).POST <@ (user1) <<? List(("dry_run", "true")), "")
      dry.code should equal(200)
      val groups = (dry.body \ "groups").children
      def of(id: String) = groups.find(g => (g \ "group_id").extract[String] == id).get
      def created(id: String) = (of(id) \ "entitlements_created").extract[List[String]]
      Then("r8, which both C and D now grant, would be granted once")
      (created(c.groupId) ++ created(d.groupId)).count(_ == u8) should equal(1)
      created(d.groupId) should contain(u9)
      (of(c.groupId) \ "entitlements_moved").children.map(m => (m \ "role_name").extract[String]) should equal(List(u6))
      (of(e.groupId) \ "group_deleted").extract[Boolean] should equal(true)
      (of(e.groupId) \ "entitlements_deleted").extract[List[String]] should equal(List(u7))
      Then("nothing changed")
      roleGroup(u8) should equal(None)
      roleGroup(u6) should equal(Some(c.groupId))
      roleGroup(u7) should equal(Some(e.groupId))

      When("user2's Groups are synced for real")
      makePostRequest(syncUser(resourceUser2.userId).POST <@ (user1), "").code should equal(200)
      Then("user2 holds what C and D grant, u6 now recorded against D, and u7 from the deleted E is gone")
      roleGroup(u5) should equal(Some(c.groupId))
      List(Some(c.groupId), Some(d.groupId)) should contain(roleGroup(u8))
      roleGroup(u9) should equal(Some(d.groupId))
      roleGroup(u6) should equal(Some(d.groupId))
      roleGroup(u7) should equal(None)

      Then("a second sync changes nothing")
      val again = makePostRequest(syncUser(resourceUser2.userId).POST <@ (user1), "")
      again.code should equal(200)
      (again.body \ "groups").children.foreach { g =>
        (g \ "entitlements_created").extract[List[String]] shouldBe empty
        (g \ "entitlements_deleted").extract[List[String]] shouldBe empty
        (g \ "entitlements_moved").children shouldBe empty
      }
    }
  }
}
