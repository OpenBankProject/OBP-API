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

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.setup.ServerSetupWithTestData
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s._
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.util.UUID

/**
 * Organisations, and the /my/consumers endpoints with which a Consumer's owners read it
 * (todo/todo_organisations.md, todo/todo_consumer_owner_access.md).
 *
 * user1 creates the Organisation and adds user2 as a member; user3 is an outsider.
 */
class OrganisationTest extends ServerSetupWithTestData {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object CreateMyOrganisation extends Tag(nameOf(Http4s700Organisations.createMyOrganisation))
  object GetMyOrganisations extends Tag(nameOf(Http4s700Organisations.getMyOrganisations))
  object GetMyOrganisation extends Tag(nameOf(Http4s700Organisations.getMyOrganisation))
  object UpdateMyOrganisation extends Tag(nameOf(Http4s700Organisations.updateMyOrganisation))
  object DeleteMyOrganisation extends Tag(nameOf(Http4s700Organisations.deleteMyOrganisation))
  object GetMyOrganisationMembers extends Tag(nameOf(Http4s700Organisations.getMyOrganisationMembers))
  object AddMyOrganisationMember extends Tag(nameOf(Http4s700Organisations.addMyOrganisationMember))
  object UpdateMyOrganisationMember extends Tag(nameOf(Http4s700Organisations.updateMyOrganisationMember))
  object RemoveMyOrganisationMember extends Tag(nameOf(Http4s700Organisations.removeMyOrganisationMember))
  object CreateOrganisation extends Tag(nameOf(Http4s700Organisations.createOrganisation))
  object GetOrganisations extends Tag(nameOf(Http4s700Organisations.getOrganisations))
  object GetOrganisationMembers extends Tag(nameOf(Http4s700Organisations.getOrganisationMembers))
  object UpdateOrganisationStatus extends Tag(nameOf(Http4s700Organisations.updateOrganisationStatus))
  object GetMyConsumers extends Tag(nameOf(Http4s700Organisations.getMyConsumers))
  object GetMyConsumer extends Tag(nameOf(Http4s700Organisations.getMyConsumer))
  object GetMyConsumerUsage extends Tag(nameOf(Http4s700Organisations.getMyConsumerUsage))
  object GetMyConsumerScopes extends Tag(nameOf(Http4s700Organisations.getMyConsumerScopes))
  object GetMyConsumerApiProductSubscriptions extends Tag(nameOf(Http4s700Organisations.getMyConsumerApiProductSubscriptions))
  object UpdateMyConsumerOrganisation extends Tag(nameOf(Http4s700Organisations.updateMyConsumerOrganisation))

  def v7 = baseRequest / "obp" / "v7.0.0"

  def errorOf(response: code.setup.APIResponse): String = response.body.extract[ErrorMessage].message

  def createOwnedConsumer(ownerUserId: String, label: String, enabled: Boolean = true): String = Consumers.consumers.vend.createConsumer(
    key = Some(UUID.randomUUID().toString.replace("-", "")),
    secret = Some(UUID.randomUUID().toString.replace("-", "")),
    isActive = Some(enabled),
    name = Some(s"organisation-test-$label-" + UUID.randomUUID().toString.take(8)),
    appType = None,
    description = Some("created by OrganisationTest"),
    developerEmail = Some(s"$label@example.com"),
    redirectURL = None,
    createdByUserId = Some(ownerUserId),
    None, None, None
  ).map(_.consumerId.get).openOrThrowException("could not create test consumer")

  def createOrganisation(name: String, as: Option[(Consumer, Token)]): MyOrganisationJsonV700 = {
    val response = makePostRequest((v7 / "my" / "organisations").POST <@ (as),
      write(PostOrganisationJsonV700(name, Some("A test organisation"), None)))
    response.code should equal(201)
    response.body.extract[MyOrganisationJsonV700]
  }

  def addMember(organisationId: String, userId: String, memberRole: String, as: Option[(Consumer, Token)]) =
    makePostRequest((v7 / "my" / "organisations" / organisationId / "members").POST <@ (as),
      write(PostOrganisationMemberJsonV700(userId, memberRole)))

  def moveConsumer(consumerId: String, organisationId: Option[String], as: Option[(Consumer, Token)]) =
    makePutRequest((v7 / "my" / "consumers" / consumerId / "organisation").PUT <@ (as),
      write(PutConsumerOrganisationJsonV700(organisationId)))

  def myConsumerIds(as: Option[(Consumer, Token)]): List[String] = {
    val response = makeGetRequest((v7 / "my" / "consumers").GET <@ (as))
    response.code should equal(200)
    response.body.extract[MyConsumersJsonV700].consumers.map(_.consumer_id)
  }

  feature("Organisations and their members") {
    scenario("Anyone logged in creates an Organisation and is its admin; outsiders get 404; anonymous 401",
      CreateMyOrganisation, GetMyOrganisations, GetMyOrganisation, VersionOfApi) {
      makePostRequest((v7 / "my" / "organisations").POST, write(PostOrganisationJsonV700("x", None, None))).code should equal(401)
      val blank = makePostRequest((v7 / "my" / "organisations").POST <@ (user1), write(PostOrganisationJsonV700("  ", None, None)))
      blank.code should equal(400)
      errorOf(blank) should startWith(InvalidOrganisationName)
      Then("an Organisation you create for yourself cannot be put at a bank")
      val atBank = makePostRequest((v7 / "my" / "organisations").POST <@ (user1),
        s"""{"bank_id":"${testBankId1.value}","name":"Fintech at a bank"}""")
      atBank.code should equal(400)
      errorOf(atBank) should startWith(MyOrganisationBankIdNotAllowed)
      And("sending SYS is accepted, since that is where it goes anyway")
      val atSys = makePostRequest((v7 / "my" / "organisations").POST <@ (user1), """{"bank_id":"SYS","name":"Fintech at SYS"}""")
      atSys.code should equal(201)
      atSys.body.extract[MyOrganisationJsonV700].bank_id should equal("SYS")

      val organisation = createOrganisation("Fintech A", user1)
      organisation.member_role should equal("admin")
      organisation.bank_id should equal("SYS")
      organisation.status should equal("active")
      organisation.created_by_user_id should equal(resourceUser1.userId)

      val mine = makeGetRequest((v7 / "my" / "organisations").GET <@ (user1))
      mine.body.extract[MyOrganisationsJsonV700].organisations.map(_.organisation_id) should contain(organisation.organisation_id)
      makeGetRequest((v7 / "my" / "organisations" / organisation.organisation_id).GET <@ (user1)).code should equal(200)
      val outsider = makeGetRequest((v7 / "my" / "organisations" / organisation.organisation_id).GET <@ (user3))
      outsider.code should equal(404)
      errorOf(outsider) should startWith(OrganisationNotFound)
    }

    scenario("Admins manage members; members cannot; the last admin stays",
      AddMyOrganisationMember, UpdateMyOrganisationMember, RemoveMyOrganisationMember, GetMyOrganisationMembers, UpdateMyOrganisation, VersionOfApi) {
      val organisation = createOrganisation("Fintech B", user1)
      val organisationId = organisation.organisation_id

      addMember(organisationId, resourceUser2.userId, "owner", user1).code should equal(400)
      addMember(organisationId, "no-such-user-" + UUID.randomUUID(), "member", user1).code should equal(404)
      addMember(organisationId, resourceUser2.userId, "member", user1).code should equal(201)
      val again = addMember(organisationId, resourceUser2.userId, "member", user1)
      again.code should equal(409)
      errorOf(again) should startWith(OrganisationMemberAlreadyExists)

      val members = makeGetRequest((v7 / "my" / "organisations" / organisationId / "members").GET <@ (user2))
      members.code should equal(200)
      members.body.extract[OrganisationMembersJsonV700].members.map(_.user_id) should contain theSameElementsAs
        List(resourceUser1.userId, resourceUser2.userId)

      val memberAdds = addMember(organisationId, resourceUser3.userId, "member", user2)
      memberAdds.code should equal(403)
      errorOf(memberAdds) should startWith(OrganisationAdminRequired)
      makePutRequest((v7 / "my" / "organisations" / organisationId).PUT <@ (user2),
        write(PutOrganisationJsonV700(Some("Renamed"), None, None))).code should equal(403)
      val renamed = makePutRequest((v7 / "my" / "organisations" / organisationId).PUT <@ (user1),
        write(PutOrganisationJsonV700(Some("Fintech B Ltd"), None, None)))
      renamed.code should equal(200)
      renamed.body.extract[MyOrganisationJsonV700].name should equal("Fintech B Ltd")

      val demoteLastAdmin = makePutRequest((v7 / "my" / "organisations" / organisationId / "members" / resourceUser1.userId).PUT <@ (user1),
        write(PutOrganisationMemberJsonV700("member")))
      demoteLastAdmin.code should equal(400)
      errorOf(demoteLastAdmin) should startWith(OrganisationNeedsAnAdmin)
      makeDeleteRequest((v7 / "my" / "organisations" / organisationId / "members" / resourceUser1.userId).DELETE <@ (user1)).code should equal(400)

      makePutRequest((v7 / "my" / "organisations" / organisationId / "members" / resourceUser2.userId).PUT <@ (user1),
        write(PutOrganisationMemberJsonV700("admin"))).code should equal(200)
      Then("with a second admin, the first may leave")
      makeDeleteRequest((v7 / "my" / "organisations" / organisationId / "members" / resourceUser1.userId).DELETE <@ (user1)).code should equal(204)
      makeGetRequest((v7 / "my" / "organisations" / organisationId).GET <@ (user1)).code should equal(404)
    }
  }

  feature("Consumers of an Organisation, and /my/consumers") {
    scenario("A personal Consumer is its creator's; in an Organisation it is the members'",
      GetMyConsumers, GetMyConsumer, UpdateMyConsumerOrganisation, DeleteMyOrganisation, VersionOfApi) {
      val consumerId = createOwnedConsumer(resourceUser1.userId, "user1")
      myConsumerIds(user1) should contain(consumerId)
      myConsumerIds(user2) should not contain consumerId
      makeGetRequest((v7 / "my" / "consumers" / consumerId).GET <@ (user2)).code should equal(404)

      val organisation = createOrganisation("Fintech C", user1)
      val organisationId = organisation.organisation_id
      addMember(organisationId, resourceUser2.userId, "member", user1).code should equal(201)

      When("user2, a member but not an admin, tries to move user1's Consumer in")
      moveConsumer(consumerId, Some(organisationId), user2).code should equal(404)
      When("user1, the creator and an admin, moves it in")
      val moved = moveConsumer(consumerId, Some(organisationId), user1)
      moved.code should equal(200)
      moved.body.extract[MyConsumerJsonV700].organisation_id should equal(Some(organisationId))

      Then("members see it, outsiders do not")
      myConsumerIds(user2) should contain(consumerId)
      makeGetRequest((v7 / "my" / "consumers" / consumerId).GET <@ (user2)).code should equal(200)
      myConsumerIds(user3) should not contain consumerId

      And("a member may not move it out; an admin may")
      val memberMoves = moveConsumer(consumerId, None, user2)
      memberMoves.code should equal(403)
      errorOf(memberMoves) should startWith(OrganisationAdminRequired)

      And("the Organisation cannot be deleted while it has Consumers")
      val deleteWithConsumers = makeDeleteRequest((v7 / "my" / "organisations" / organisationId).DELETE <@ (user1))
      deleteWithConsumers.code should equal(409)
      errorOf(deleteWithConsumers) should startWith(OrganisationHasConsumers)

      moveConsumer(consumerId, None, user1).code should equal(200)
      myConsumerIds(user2) should not contain consumerId
      makeDeleteRequest((v7 / "my" / "organisations" / organisationId).DELETE <@ (user2)).code should equal(403)
      makeDeleteRequest((v7 / "my" / "organisations" / organisationId).DELETE <@ (user1)).code should equal(204)
    }

    scenario("The creator no longer acts alone once the Consumer belongs to an Organisation they left",
      GetMyConsumer, VersionOfApi) {
      val consumerId = createOwnedConsumer(resourceUser1.userId, "leaver")
      val organisation = createOrganisation("Fintech D", user1)
      addMember(organisation.organisation_id, resourceUser2.userId, "admin", user1).code should equal(201)
      moveConsumer(consumerId, Some(organisation.organisation_id), user1).code should equal(200)
      makeDeleteRequest((v7 / "my" / "organisations" / organisation.organisation_id / "members" / resourceUser1.userId).DELETE <@ (user1)).code should equal(204)
      makeGetRequest((v7 / "my" / "consumers" / consumerId).GET <@ (user1)).code should equal(404)
      makeGetRequest((v7 / "my" / "consumers" / consumerId).GET <@ (user2)).code should equal(200)
    }

    scenario("A disabled Consumer is shown with enabled false; usage, Scopes and subscriptions are readable; no secret",
      GetMyConsumer, GetMyConsumerUsage, GetMyConsumerScopes, GetMyConsumerApiProductSubscriptions, VersionOfApi) {
      val consumerId = createOwnedConsumer(resourceUser1.userId, "disabled", enabled = false)
      val consumer = makeGetRequest((v7 / "my" / "consumers" / consumerId).GET <@ (user1))
      consumer.code should equal(200)
      consumer.body.extract[MyConsumerJsonV700].enabled should equal(false)
      (consumer.body \ "consumer_key") should equal(JNothing)
      (consumer.body \ "secret") should equal(JNothing)

      val usage = makeGetRequest((v7 / "my" / "consumers" / consumerId / "usage").GET <@ (user1))
      usage.code should equal(200)
      (usage.body \ "consumer_id").extract[String] should equal(consumerId)
      (usage.body \ "active_rate_limits") should not equal JNothing
      (usage.body \ "call_counters") should not equal JNothing

      val scopes = makeGetRequest((v7 / "my" / "consumers" / consumerId / "scopes").GET <@ (user1))
      scopes.code should equal(200)
      scopes.body.extract[MyConsumerScopesJsonV700].scopes should equal(Nil)

      val subscriptions = makeGetRequest((v7 / "my" / "consumers" / consumerId / "api-product-subscriptions").GET <@ (user1))
      subscriptions.code should equal(200)

      List("usage", "scopes", "api-product-subscriptions").foreach { part =>
        makeGetRequest((v7 / "my" / "consumers" / consumerId / part).GET <@ (user3)).code should equal(404)
      }
      makeGetRequest((v7 / "my" / "consumers" / consumerId / "usage").GET).code should equal(401)
    }
  }

  feature("Staff") {
    scenario("Staff Roles are held at the Organisation's bank_id; a suspended Organisation's members cannot act for its Consumers",
      GetOrganisations, UpdateOrganisationStatus, VersionOfApi) {
      val organisation = createOrganisation("Fintech E", user1)
      val consumerId = createOwnedConsumer(resourceUser1.userId, "suspended")
      moveConsumer(consumerId, Some(organisation.organisation_id), user1).code should equal(200)

      makeGetRequest((v7 / "management" / "organisations").GET <@ (user3)).code should equal(403)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser3.userId, CanGetOrganisationAtOneBank.toString)
      Then("the Role at another bank_id does not show an Organisation at SYS")
      makeGetRequest((v7 / "management" / "organisations" / organisation.organisation_id).GET <@ (user3)).code should equal(403)
      Entitlement.entitlement.vend.addEntitlement("SYS", resourceUser3.userId, CanGetOrganisationAtOneBank.toString)
      val all = makeGetRequest((v7 / "management" / "organisations").GET <@ (user3))
      all.code should equal(200)
      all.body.extract[OrganisationsJsonV700].organisations.map(_.organisation_id) should contain(organisation.organisation_id)

      val statusPath = v7 / "management" / "organisations" / organisation.organisation_id / "status"
      makePutRequest(statusPath.PUT <@ (user3), write(PutOrganisationStatusJsonV700("suspended"))).code should equal(403)
      Entitlement.entitlement.vend.addEntitlement("SYS", resourceUser3.userId, CanUpdateOrganisationStatusAtOneBank.toString)
      makePutRequest(statusPath.PUT <@ (user3), write(PutOrganisationStatusJsonV700("closed"))).code should equal(400)
      makePutRequest(statusPath.PUT <@ (user3), write(PutOrganisationStatusJsonV700("suspended"))).code should equal(200)

      Then("the member still sees the Organisation but not its Consumer")
      makeGetRequest((v7 / "my" / "organisations" / organisation.organisation_id).GET <@ (user1)).code should equal(200)
      makeGetRequest((v7 / "my" / "consumers" / consumerId).GET <@ (user1)).code should equal(404)

      makePutRequest(statusPath.PUT <@ (user3), write(PutOrganisationStatusJsonV700("active"))).code should equal(200)
      makeGetRequest((v7 / "my" / "consumers" / consumerId).GET <@ (user1)).code should equal(200)
    }

    scenario("Staff create an Organisation with a first admin, and read its members", CreateOrganisation, GetOrganisationMembers, VersionOfApi) {
      val body = write(PostManagementOrganisationJsonV700(Some(testBankId2.value), "Participant Bank", None, None, resourceUser2.userId))
      makePostRequest((v7 / "management" / "organisations").POST <@ (user1), body).code should equal(403)
      Entitlement.entitlement.vend.addEntitlement(testBankId2.value, resourceUser1.userId, CanCreateOrganisationAtOneBank.toString)
      val created = makePostRequest((v7 / "management" / "organisations").POST <@ (user1), body)
      created.code should equal(201)
      val organisation = created.body.extract[OrganisationJsonV700]
      organisation.bank_id should equal(testBankId2.value)

      Then("the named first admin, not the member of staff, is its admin")
      makeGetRequest((v7 / "my" / "organisations" / organisation.organisation_id).GET <@ (user2)).body
        .extract[MyOrganisationJsonV700].member_role should equal("admin")
      makeGetRequest((v7 / "my" / "organisations" / organisation.organisation_id).GET <@ (user1)).code should equal(404)

      val membersPath = v7 / "management" / "organisations" / organisation.organisation_id / "members"
      makeGetRequest(membersPath.GET <@ (user1)).code should equal(403)
      Entitlement.entitlement.vend.addEntitlement(testBankId2.value, resourceUser1.userId, CanGetOrganisationAtOneBank.toString)
      val members = makeGetRequest(membersPath.GET <@ (user1))
      members.code should equal(200)
      members.body.extract[OrganisationMembersJsonV700].members.map(_.user_id) should equal(List(resourceUser2.userId))

      val listed = makeGetRequest((v7 / "management" / "organisations").GET <<? List("bank_id" -> testBankId2.value) <@ (user1))
      listed.code should equal(200)
      listed.body.extract[OrganisationsJsonV700].organisations.map(_.organisation_id) should equal(List(organisation.organisation_id))
      makeGetRequest((v7 / "management" / "organisations").GET <<? List("bank_id" -> "SYS") <@ (user1)).code should equal(403)
    }
  }
}
