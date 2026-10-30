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
import code.api.util.APIUtil.generateUUID
import code.api.util.ApiRole.{CanCreateOrganisationMember, CanDeleteOrganisationMember}
import code.api.util.ErrorMessages._
import code.api.v6_0_0.V600ServerSetup
import code.entitlement.Entitlement
import code.organisation.{OrganisationMembers, Organisations}
import code.setup.APIResponse
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * This suite checks the v7.0.0 Organisation member endpoints: who may add, read, change and delete members (a Role
 * at the Organisation's space, an owner or admin member, any active member for reading, a member on their own
 * membership), the values accepted, the two user ids recorded for each write, that memberships stay in their
 * Organisation's space, and Get My Organisations.
 */
class OrganisationMembersTest extends V600ServerSetup {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"
  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object OrganisationMembersTag extends Tag("organisationMembers")

  private lazy val bankId = testBankId1.value
  private val SYS = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID

  private def members(orgId: String, bank: String = bankId) =
    v7_0_0_Request / "banks" / bank / "organisations" / orgId / "members"

  private def newOrganisation(bank: String = bankId): String = {
    val orgId = s"members-org-${generateUUID().take(8)}"
    Organisations.organisation.vend.createOrganisation(bank, orgId, s"Org $orgId", None, None, Some("startup"),
      "active", "public", resourceUser1.userId).openOrThrowException("expected the organisation to be created")
    orgId
  }

  /** A membership written through the provider, for setting up who is an owner or a member. */
  private def addMember(orgId: String, userId: String, memberRole: String, status: String = "active"): Unit =
    OrganisationMembers.organisationMember.vend.createMember(orgId, userId, memberRole, None, status, isPrimary = false,
      resourceUser1.userId).openOrThrowException("expected the member to be created")

  private def withEntitlements[T](grants: (String, String, String)*)(f: => T): T = {
    val added = grants.map { case (bank, userId, role) => Entitlement.entitlement.vend.addEntitlement(bank, userId, role) }
    try f finally added.foreach(Entitlement.entitlement.vend.deleteEntitlement)
  }

  private def postBody(userId: String, memberRole: Option[String] = None, status: Option[String] = None,
                       title: Option[String] = None, isPrimary: Option[Boolean] = None): String =
    write(PostOrganisationMemberJsonV700(userId, memberRole, title, status, isPrimary))

  private def putBody(memberRole: Option[String] = None, status: Option[String] = None, title: Option[String] = None,
                      isPrimary: Option[Boolean] = None): String =
    write(PutOrganisationMemberJsonV700(memberRole, title, status, isPrimary))

  private def message(response: APIResponse): String = response.body.extract[ErrorMessage].message

  feature(s"Create Organisation Member - POST /banks/BANK_ID/organisations/ORGANISATION_ID/members - $VersionOfApi") {

    scenario("Anonymous access fails with 401", OrganisationMembersTag, VersionOfApi) {
      makePostRequest(members(newOrganisation()).POST, postBody(resourceUser2.userId)).code should equal(401)
    }

    scenario("A user who is neither a manager nor holds the Role gets 403", OrganisationMembersTag, VersionOfApi) {
      val response = makePostRequest(members(newOrganisation()).POST <@ (user2), postBody(resourceUser3.userId))
      response.code should equal(403)
      message(response) should include(UserHasMissingRoles)
    }

    scenario("The Role at the bank adds a member and records who did it", OrganisationMembersTag, VersionOfApi) {
      val orgId = newOrganisation()
      withEntitlements((bankId, resourceUser1.userId, CanCreateOrganisationMember.toString)) {
        val response = makePostRequest(members(orgId).POST <@ (user1),
          postBody(resourceUser2.userId, memberRole = Some("owner"), title = Some("CEO")))
        withClue(response.body) { response.code should equal(201) }
        val member = response.body.extract[OrganisationMemberJsonV700]
        member.organisation_id should equal(orgId)
        member.bank_id should equal(bankId)
        member.user_id should equal(resourceUser2.userId)
        member.username should equal(resourceUser2.name)
        member.member_role should equal("owner")
        member.status should equal("active")
        member.title should equal(Some("CEO"))
        member.created_by_user_id should equal(resourceUser1.userId)
        member.created_by_on_behalf_of_user_id should equal(resourceUser1.userId)

        And("adding the same User again is a 409")
        val again = makePostRequest(members(orgId).POST <@ (user1), postBody(resourceUser2.userId))
        again.code should equal(409)
        message(again) should include(OrganisationMemberAlreadyExists)
      }
    }

    scenario("A Role held at another space does not help", OrganisationMembersTag, VersionOfApi) {
      withEntitlements((SYS, resourceUser1.userId, CanCreateOrganisationMember.toString)) {
        makePostRequest(members(newOrganisation()).POST <@ (user1), postBody(resourceUser2.userId)).code should equal(403)
      }
    }

    scenario("Unknown users and invalid values are refused", OrganisationMembersTag, VersionOfApi) {
      val orgId = newOrganisation()
      withEntitlements((bankId, resourceUser1.userId, CanCreateOrganisationMember.toString)) {
        val unknown = makePostRequest(members(orgId).POST <@ (user1), postBody("no-such-user"))
        unknown.code should equal(404)
        message(unknown) should include(UserNotFoundByUserId)
        val badRole = makePostRequest(members(orgId).POST <@ (user1), postBody(resourceUser2.userId, memberRole = Some("boss")))
        badRole.code should equal(400)
        message(badRole) should include(InvalidOrganisationMemberRole)
        val badStatus = makePostRequest(members(orgId).POST <@ (user1), postBody(resourceUser2.userId, status = Some("gone")))
        badStatus.code should equal(400)
        message(badStatus) should include(InvalidOrganisationMemberStatus)
      }
    }

    scenario("An owner adds members without a Role; a plain member cannot", OrganisationMembersTag, VersionOfApi) {
      val orgId = newOrganisation()
      addMember(orgId, resourceUser2.userId, "owner")
      val added = makePostRequest(members(orgId).POST <@ (user2), postBody(resourceUser3.userId, status = Some("invited")))
      withClue(added.body) { added.code should equal(201) }
      added.body.extract[OrganisationMemberJsonV700].created_by_user_id should equal(resourceUser2.userId)

      val otherOrg = newOrganisation()
      addMember(otherOrg, resourceUser3.userId, "member")
      makePostRequest(members(otherOrg).POST <@ (user3), postBody(resourceUser2.userId)).code should equal(403)
    }

    scenario("An Organisation in another space is not found", OrganisationMembersTag, VersionOfApi) {
      val orgId = newOrganisation(SYS)
      withEntitlements((bankId, resourceUser1.userId, CanCreateOrganisationMember.toString)) {
        val response = makePostRequest(members(orgId).POST <@ (user1), postBody(resourceUser2.userId))
        response.code should equal(404)
        message(response) should include(OrganisationNotFound)
      }
    }
  }

  feature(s"Get Organisation Members - GET .../members[/USER_ID] - $VersionOfApi") {

    scenario("Active members read the list; others get 403; a User reads their own membership", OrganisationMembersTag, VersionOfApi) {
      val orgId = newOrganisation()
      addMember(orgId, resourceUser2.userId, "member")
      addMember(orgId, resourceUser3.userId, "member", status = "invited")

      val list = makeGetRequest(members(orgId).GET <@ (user2))
      withClue(list.body) { list.code should equal(200) }
      list.body.extract[OrganisationMembersJsonV700].members.map(_.user_id) should contain allOf (resourceUser2.userId, resourceUser3.userId)

      And("an invited member is not yet active, so cannot read the list, but can read their own membership")
      makeGetRequest(members(orgId).GET <@ (user3)).code should equal(403)
      makeGetRequest((members(orgId) / resourceUser3.userId).GET <@ (user3)).code should equal(200)

      And("a User who is not a member gets 403")
      makeGetRequest(members(orgId).GET <@ (user1)).code should equal(403)
    }
  }

  feature(s"Update Organisation Member - PUT .../members/USER_ID - $VersionOfApi") {

    scenario("A member accepts an invitation and leaves, but cannot change their own member_role", OrganisationMembersTag, VersionOfApi) {
      val orgId = newOrganisation()
      addMember(orgId, resourceUser3.userId, "member", status = "invited")
      val one = members(orgId) / resourceUser3.userId

      val accepted = makePutRequest(one.PUT <@ (user3), putBody(status = Some("active")))
      withClue(accepted.body) { accepted.code should equal(200) }
      val member = accepted.body.extract[OrganisationMemberJsonV700]
      member.status should equal("active")
      member.updated_by_user_id should equal(resourceUser3.userId)
      member.updated_by_on_behalf_of_user_id should equal(resourceUser3.userId)

      val promoted = makePutRequest(one.PUT <@ (user3), putBody(memberRole = Some("admin")))
      promoted.code should equal(403)
      message(promoted) should include(OrganisationMemberSelfChangeNotAllowed)

      makePutRequest(one.PUT <@ (user3), putBody(status = Some("left"))).body.extract[OrganisationMemberJsonV700].status should equal("left")
    }

    scenario("An admin member changes another member's role", OrganisationMembersTag, VersionOfApi) {
      val orgId = newOrganisation()
      addMember(orgId, resourceUser2.userId, "admin")
      addMember(orgId, resourceUser3.userId, "member")
      val response = makePutRequest((members(orgId) / resourceUser3.userId).PUT <@ (user2), putBody(memberRole = Some("admin")))
      withClue(response.body) { response.code should equal(200) }
      response.body.extract[OrganisationMemberJsonV700].member_role should equal("admin")
    }

    scenario("Making a membership primary clears the flag on the User's others", OrganisationMembersTag, VersionOfApi) {
      val first = newOrganisation()
      val second = newOrganisation()
      addMember(first, resourceUser2.userId, "member")
      addMember(second, resourceUser2.userId, "member")
      makePutRequest((members(first) / resourceUser2.userId).PUT <@ (user2), putBody(isPrimary = Some(true))).code should equal(200)
      makePutRequest((members(second) / resourceUser2.userId).PUT <@ (user2), putBody(isPrimary = Some(true))).code should equal(200)
      val provider = OrganisationMembers.organisationMember.vend
      provider.getMember(first, resourceUser2.userId).map(_.isPrimary) should equal(net.liftweb.common.Full(false))
      provider.getMember(second, resourceUser2.userId).map(_.isPrimary) should equal(net.liftweb.common.Full(true))
    }
  }

  feature(s"Delete Organisation Member - DELETE .../members/USER_ID - $VersionOfApi") {

    scenario("Deleting needs the Role, even for an owner", OrganisationMembersTag, VersionOfApi) {
      val orgId = newOrganisation()
      addMember(orgId, resourceUser2.userId, "owner")
      addMember(orgId, resourceUser3.userId, "member")
      val one = members(orgId) / resourceUser3.userId
      makeDeleteRequest(one.DELETE <@ (user2)).code should equal(403)
      withEntitlements((bankId, resourceUser1.userId, CanDeleteOrganisationMember.toString)) {
        makeDeleteRequest(one.DELETE <@ (user1)).code should equal(204)
      }
      OrganisationMembers.organisationMember.vend.getMember(orgId, resourceUser3.userId).isEmpty shouldBe true
    }

    scenario("Deleting an Organisation deletes its members", OrganisationMembersTag, VersionOfApi) {
      val orgId = newOrganisation()
      addMember(orgId, resourceUser2.userId, "owner")
      Organisations.organisation.vend.deleteOrganisation(bankId, orgId)
      OrganisationMembers.organisationMember.vend.getMembers(orgId).map(_.isEmpty) should equal(net.liftweb.common.Full(true))
    }
  }

  feature(s"Get My Organisations - GET /my/organisations - $VersionOfApi") {

    scenario("A User sees each Organisation they belong to, with their membership", OrganisationMembersTag, VersionOfApi) {
      makeGetRequest((v7_0_0_Request / "my" / "organisations").GET).code should equal(401)
      val orgId = newOrganisation()
      addMember(orgId, resourceUser2.userId, "admin")
      val response = makeGetRequest((v7_0_0_Request / "my" / "organisations").GET <@ (user2))
      withClue(response.body) { response.code should equal(200) }
      val mine = response.body.extract[MyOrganisationsJsonV700].organisations.find(_.organisation.organisation_id == orgId)
      mine.map(_.membership.member_role) should equal(Some("admin"))
      mine.map(_.organisation.bank_id) should equal(Some(bankId))
    }
  }
}
