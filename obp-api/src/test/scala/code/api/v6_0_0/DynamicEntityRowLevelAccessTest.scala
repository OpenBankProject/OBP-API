/**
Open Bank Project - API
Copyright (C) 2011-2025, TESOBE GmbH

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
TESOBE GmbH
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.entitlement.Entitlement
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonDSL._
import org.json4s.native.Serialization.write
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._
import org.scalatest.Tag
import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID

/**
 * Row-level access (use_row_level_access) — see ideas/DYNAMIC_ENTITY_ROW_LEVEL_ACCESS.md.
 * Covers: owner bootstrap, per-row read isolation (404 hides), owner sharing with no role,
 * per-row update gate (403), revoke cascade, get-all ACL filter, admin-role override,
 * flag-off 400, the §8.3 public-access guard, community reads with the Get role, and Organisation grants.
 */
class DynamicEntityRowLevelAccessTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)

  // ==================== Helpers ====================

  def createSystemEntity(entityJson: JValue): (Int, JValue) = {
    Entitlement.entitlement.vend.addEntitlement(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser1.userId, CanCreateDynamicEntityDefinition.toString)
    val request = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@ (user1)
    val response = makePostRequest(request, write(entityJson))
    (response.code, response.body)
  }

  def deleteSystemEntity(dynamicEntityId: String): Unit = {
    Entitlement.entitlement.vend.addEntitlement(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser1.userId, CanDeleteDynamicEntityDefinition.toString)
    makeDeleteRequest((v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@ (user1))
  }

  def simpleSchema: JValue = parse(
    """
      |{
      |    "description": "Row-level access test entity.",
      |    "required": ["name"],
      |    "properties": {
      |        "name": { "type": "string", "maxLength": 40, "minLength": 1, "example": "Test" }
      |    }
      |}
    """.stripMargin)

  val entityRowLevel: JValue =
    ("entity_name" -> "test_rl") ~
    ("use_row_level_access" -> true) ~
    ("schema" -> simpleSchema)

  // user1 owns the entity definition (and is auto-granted the entity roles incl. the admin grant role).
  // user1 creates records (it holds CanCreateDynamicEntityRecord_test_rl from the definition create).
  def createRecord(name: String): String = {
    val resp = makePostRequest((dynamicEntity_Request / "test_rl").POST <@ (user1), write(("name" -> name): JValue))
    resp.code should equal(201)
    (resp.body \ "test_rl" \ "test_rl_id").extract[String]
  }

  // ==================== Feature 1: owner bootstrap & read isolation ====================

  Feature("Feature 1: owner bootstrap & per-row read isolation") {
    Scenario("1.1: creator reads own record; another user gets 404; list is ACL-filtered", VersionOfApi) {
      val (code, body) = createSystemEntity(entityRowLevel)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      try {
        val id = createRecord("owned by user1")

        When("user1 (owner) GETs the record")
        makeGetRequest((dynamicEntity_Request / "test_rl" / id).GET <@ (user1)).code should equal(200)

        When("user2 (no ACL) GETs the record")
        Then("it is 404 — an unreadable row is indistinguishable from a missing one")
        makeGetRequest((dynamicEntity_Request / "test_rl" / id).GET <@ (user2)).code should equal(404)

        When("user1 GETs the list")
        val ownerList = makeGetRequest((dynamicEntity_Request / "test_rl").GET <@ (user1))
        ownerList.code should equal(200)
        (ownerList.body \ "test_rl_list").asInstanceOf[JArray].arr.size should equal(1)

        When("user2 GETs the list")
        Then("it is empty — user2 can read none of the rows")
        val otherList = makeGetRequest((dynamicEntity_Request / "test_rl").GET <@ (user2))
        otherList.code should equal(200)
        (otherList.body \ "test_rl_list").asInstanceOf[JArray].arr.size should equal(0)
      } finally deleteSystemEntity(dynamicEntityId)
    }
  }

  // ==================== Feature 2: owner shares with no role; per-row update gate ====================

  Feature("Feature 2: owner shares (no role) and the per-row update gate") {
    Scenario("2.1: owner grants read → grantee reads; update needs a separate grant", VersionOfApi) {
      val (code, body) = createSystemEntity(entityRowLevel)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      try {
        val id = createRecord("shared record")

        When("user1 (owner, holds ACL CanGrant — no role needed) grants READ to user2")
        val grantResp = makePutRequest(
          (dynamicEntity_Request / "test_rl" / id / "access").PUT <@ (user1),
          write(("user_id" -> resourceUser2.userId) ~ ("can_read" -> true) ~ ("can_update" -> false)))
        grantResp.code should equal(200)

        Then("user2 can now read the record")
        makeGetRequest((dynamicEntity_Request / "test_rl" / id).GET <@ (user2)).code should equal(200)

        When("user2 (read-only) tries to update the record")
        Then("it is 403 — read does not confer update")
        makePutRequest((dynamicEntity_Request / "test_rl" / id).PUT <@ (user2), write(("name" -> "u2 edit"): JValue))
          .code should equal(403)

        When("user1 additionally grants UPDATE to user2")
        makePutRequest(
          (dynamicEntity_Request / "test_rl" / id / "access").PUT <@ (user1),
          write(("user_id" -> resourceUser2.userId) ~ ("can_read" -> true) ~ ("can_update" -> true)))
          .code should equal(200)

        Then("user2 can now update")
        makePutRequest((dynamicEntity_Request / "test_rl" / id).PUT <@ (user2), write(("name" -> "u2 edit"): JValue))
          .code should equal(200)
      } finally deleteSystemEntity(dynamicEntityId)
    }
  }

  // ==================== Feature 3: revoke ====================

  Feature("Feature 3: revoke removes access") {
    Scenario("3.1: revoke a grantee → they lose read", VersionOfApi) {
      val (code, body) = createSystemEntity(entityRowLevel)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      try {
        val id = createRecord("revoke me")
        makePutRequest(
          (dynamicEntity_Request / "test_rl" / id / "access").PUT <@ (user1),
          write(("user_id" -> resourceUser2.userId) ~ ("can_read" -> true))).code should equal(200)
        makeGetRequest((dynamicEntity_Request / "test_rl" / id).GET <@ (user2)).code should equal(200)

        When("user1 revokes user2")
        val revoke = makeDeleteRequest((dynamicEntity_Request / "test_rl" / id / "access" / resourceUser2.userId).DELETE <@ (user1))
        revoke.code should equal(200)
        (revoke.body \ "revoked_count").extract[Int] should be >= 1

        Then("user2 can no longer read the record")
        makeGetRequest((dynamicEntity_Request / "test_rl" / id).GET <@ (user2)).code should equal(404)
      } finally deleteSystemEntity(dynamicEntityId)
    }
  }

  // ==================== Feature 4: admin-role override ====================

  Feature("Feature 4: CanGrantDynamicEntityRowAccess admin override") {
    Scenario("4.1: a role holder may list/grant access on a row they cannot read", VersionOfApi) {
      val (code, body) = createSystemEntity(entityRowLevel)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      try {
        val id = createRecord("admin-managed")

        When("user3 (no ACL, no role) lists access on the row")
        Then("it is 403")
        makeGetRequest((dynamicEntity_Request / "test_rl" / id / "access").GET <@ (user3)).code should equal(403)

        When("user3 is granted the admin row-access role")
        Entitlement.entitlement.vend.addEntitlement(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser3.userId, "CanGrantDynamicEntityRowAccess_test_rl")

        Then("user3 can list access even though it cannot read the row")
        makeGetRequest((dynamicEntity_Request / "test_rl" / id / "access").GET <@ (user3)).code should equal(200)

        And("user3 can grant access to user2")
        makePutRequest(
          (dynamicEntity_Request / "test_rl" / id / "access").PUT <@ (user3),
          write(("user_id" -> resourceUser2.userId) ~ ("can_read" -> true))).code should equal(200)
        makeGetRequest((dynamicEntity_Request / "test_rl" / id).GET <@ (user2)).code should equal(200)
      } finally deleteSystemEntity(dynamicEntityId)
    }
  }

  // ==================== Feature 5: flag-off & definition-time validation ====================

  Feature("Feature 5: flag-off and flag-combination validation") {
    Scenario("5.1: access endpoints return 400 for a non-row-level entity", VersionOfApi) {
      val normalEntity: JValue =
        ("entity_name" -> "test_normal_rl") ~ ("has_personal_entity" -> false) ~ ("schema" -> simpleSchema)
      val (code, body) = createSystemEntity(normalEntity)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      try {
        Entitlement.entitlement.vend.addEntitlement(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser1.userId, "CanCreateDynamicEntityRecord_test_normal_rl")
        val createResp = makePostRequest((dynamicEntity_Request / "test_normal_rl").POST <@ (user1), write(("name" -> "x"): JValue))
        createResp.code should equal(201)
        val id = (createResp.body \ "test_normal_rl" \ "test_normal_rl_id").extract[String]

        When("we hit the row-access endpoint on an entity without use_row_level_access")
        Then("it is 400")
        makeGetRequest((dynamicEntity_Request / "test_normal_rl" / id / "access").GET <@ (user1)).code should equal(400)
      } finally deleteSystemEntity(dynamicEntityId)
    }

    Scenario("5.2: use_row_level_access cannot be combined with has_public_access (§8.3)", VersionOfApi) {
      val contradictory: JValue =
        ("entity_name" -> "test_rl_conflict") ~
        ("use_row_level_access" -> true) ~
        ("has_public_access" -> true) ~
        ("schema" -> simpleSchema)
      When("we create an entity with both flags")
      val (code, _) = createSystemEntity(contradictory)
      Then("it is rejected (not 201)")
      code should equal(400)
    }

    scenario("5.3: use_row_level_access with has_community_access lets the Get role read every record", VersionOfApi) {
      val rowLevelWithCommunity: JValue =
        ("entity_name" -> "test_rl_community") ~
        ("use_row_level_access" -> true) ~
        ("has_community_access" -> true) ~
        ("schema" -> simpleSchema)
      val (createStatus, body) = createSystemEntity(rowLevelWithCommunity)
      Then("the entity is created")
      createStatus should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      try {
        val createResponse = makePostRequest((dynamicEntity_Request / "test_rl_community").POST <@ (user1), write(("name" -> "private to user1"): JValue))
        createResponse.code should equal(201)
        val recordId = (createResponse.body \ "test_rl_community" \ "test_rl_community_id").extract[String]

        When("user2, with no grant and no role, reads the record and the community list")
        Then("the record is hidden and the community list is refused")
        makeGetRequest((dynamicEntity_Request / "test_rl_community" / recordId).GET <@ (user2)).code should equal(404)
        val refusedCommunityList = makeGetRequest((dynamicEntity_Request / "community" / "test_rl_community").GET <@ (user2))
        refusedCommunityList.code should equal(403)
        refusedCommunityList.body.toString should include("CanGetDynamicEntityRecord_test_rl_community")

        When("user2 is given the entity's Get role")
        Entitlement.entitlement.vend.addEntitlement(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser2.userId, "CanGetDynamicEntityRecord_test_rl_community")
        Then("user2 reads every record through the community URL")
        val communityList = makeGetRequest((dynamicEntity_Request / "community" / "test_rl_community").GET <@ (user2))
        communityList.code should equal(200)
        (communityList.body \ "test_rl_community_list").asInstanceOf[JArray].arr.size should equal(1)
        And("the role still does not open the record through the shared URL, where the access list decides")
        makeGetRequest((dynamicEntity_Request / "test_rl_community" / recordId).GET <@ (user2)).code should equal(404)
      } finally deleteSystemEntity(dynamicEntityId)
    }
  }

  // ==================== Feature 6: a row-level record keeps its id through updates ====================

  feature("Feature 6: updating a row-level record keeps its id, and the entity can be cascade-deleted") {

    // Cascade delete is refused for an entity that has personal endpoints, so this one has none.
    val entityName = "test_rlc"
    val rowLevelWithoutPersonal: JValue =
      ("entity_name" -> entityName) ~ ("use_row_level_access" -> true) ~ ("has_personal_entity" -> false) ~ ("schema" -> simpleSchema)

    def createRecordOf(name: String): String = {
      val resp = makePostRequest((dynamicEntity_Request / entityName).POST <@ (user1), write(("name" -> name): JValue))
      resp.code should equal(201)
      (resp.body \ entityName \ s"${entityName}_id").extract[String]
    }

    def idOf(body: JValue): JValue = body \ entityName \ s"${entityName}_id"

    def cascadeDelete(dynamicEntityId: String): Int = {
      Entitlement.entitlement.vend.addEntitlement(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser1.userId, CanDeleteCascadeDynamicEntityDefinition.toString)
      makeDeleteRequest((v6_0_0_Request / "management" / "system-dynamic-entities" / "cascade" / dynamicEntityId).DELETE <@ (user1)).code
    }

    scenario("6.1: PUT and PATCH keep the record id in the stored record, and cascade delete succeeds", VersionOfApi) {
      val (createStatus, body) = createSystemEntity(rowLevelWithoutPersonal)
      createStatus should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      val id = createRecordOf("before")

      When("the record is replaced with PUT, whose body carries no id")
      makePutRequest((dynamicEntity_Request / entityName / id).PUT <@ (user1), write(("name" -> "after put"): JValue)).code should equal(200)

      Then("a GET still returns the record with its id")
      val afterPut = makeGetRequest((dynamicEntity_Request / entityName / id).GET <@ (user1))
      afterPut.code should equal(200)
      idOf(afterPut.body) should equal(JString(id))
      (afterPut.body \ entityName \ "name").extract[String] should equal("after put")

      When("the record is changed with PATCH")
      makePatchRequest((dynamicEntity_Request / entityName / id).PATCH <@ (user1), write(("name" -> "after patch"): JValue)).code should equal(200)

      Then("a GET still returns the record with its id")
      val afterPatch = makeGetRequest((dynamicEntity_Request / entityName / id).GET <@ (user1))
      idOf(afterPatch.body) should equal(JString(id))
      (afterPatch.body \ entityName \ "name").extract[String] should equal("after patch")

      And("the entity can be deleted with its records")
      cascadeDelete(dynamicEntityId) should equal(200)
    }

    scenario("6.2: a record whose stored JSON already lost its id is still cascade-deleted", VersionOfApi) {
      val (createStatus, body) = createSystemEntity(rowLevelWithoutPersonal)
      createStatus should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      val id = createRecordOf("damaged")

      Given("a record written by an earlier version, whose stored JSON has no id")
      // Rewrites the stored JSON directly, as the record's row in the database; the store has no
      // setter for it, since every write path through it keeps the id.
      locally {
        import doobie.implicits._
        val damaged = """{"name":"damaged"}"""
        val updated = code.api.util.DoobieUtil.runUpdate(
          sql"UPDATE dynamicdata SET datajson = $damaged WHERE dynamicentityname = $entityName AND dynamicdataid = $id"
            .update.run)
        require(updated == 1, "the record should exist")
      }

      When("the entity is deleted with its records")
      Then("it succeeds instead of failing on the missing id")
      cascadeDelete(dynamicEntityId) should equal(200)
    }
  }

  // ==================== Feature 7: sharing with an Organisation ====================

  // user1 creates the Organisation and is its admin; user2 is a member; user3 belongs to nothing.
  private def createOrganisationWithMember(memberUserId: String): String = {
    val organisationId = code.organisation.Organisations.createOrganisation(
      DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, "Row access team", "", None, resourceUser1.userId, resourceUser1.userId
    ).openOrThrowException("the Organisation should be created").organisationId
    code.organisation.Organisations.addMember(organisationId, memberUserId, code.organisation.Organisations.RoleMember, resourceUser1.userId)
      .openOrThrowException("the member should be added")
    organisationId
  }

  private def grantToOrganisation(recordId: String, organisationId: String, canUpdate: Boolean) =
    makePutRequest(
      (dynamicEntity_Request / "test_rl" / recordId / "access").PUT <@ (user1),
      write(("organisation_id" -> organisationId) ~ ("can_read" -> true) ~ ("can_update" -> canUpdate)))

  feature("Feature 7: a grant to an Organisation follows its membership") {
    scenario("7.1: members read and update; outsiders, former members and suspended Organisations do not", VersionOfApi) {
      val (createStatus, body) = createSystemEntity(entityRowLevel)
      createStatus should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      val organisationId = createOrganisationWithMember(resourceUser2.userId)
      try {
        val recordId = createRecord("team answer")

        When("user1 grants read and update to the Organisation")
        val grantResponse = grantToOrganisation(recordId, organisationId, canUpdate = true)
        grantResponse.code should equal(200)
        Then("the access list shows the grant under organisation_access, with can_grant false by default")
        val organisationGrant = (grantResponse.body \ "organisation_access").asInstanceOf[JArray].arr.head
        (organisationGrant \ "organisation_id").extract[String] should equal(organisationId)
        (organisationGrant \ "can_grant").extract[Boolean] should equal(false)

        Then("user2, a member, reads, lists and updates the record")
        makeGetRequest((dynamicEntity_Request / "test_rl" / recordId).GET <@ (user2)).code should equal(200)
        val memberList = makeGetRequest((dynamicEntity_Request / "test_rl").GET <@ (user2))
        (memberList.body \ "test_rl_list").asInstanceOf[JArray].arr.size should equal(1)
        makePutRequest((dynamicEntity_Request / "test_rl" / recordId).PUT <@ (user2), write(("name" -> "edited by the team"): JValue))
          .code should equal(200)

        And("user2 cannot change the access list, because the Organisation has no can_grant")
        makePutRequest(
          (dynamicEntity_Request / "test_rl" / recordId / "access").PUT <@ (user2),
          write(("user_id" -> resourceUser3.userId) ~ ("can_read" -> true))).code should equal(403)

        And("user3, who is not a member, gets 404")
        makeGetRequest((dynamicEntity_Request / "test_rl" / recordId).GET <@ (user3)).code should equal(404)

        When("the Organisation is suspended")
        code_organisation.updateStatus(organisationId, code_organisation.StatusSuspended)
        Then("user2 loses access")
        makeGetRequest((dynamicEntity_Request / "test_rl" / recordId).GET <@ (user2)).code should equal(404)
        code_organisation.updateStatus(organisationId, code_organisation.StatusActive)
        makeGetRequest((dynamicEntity_Request / "test_rl" / recordId).GET <@ (user2)).code should equal(200)

        When("user2 leaves the Organisation")
        code_organisation.removeMember(organisationId, resourceUser2.userId)
        Then("user2 loses access, with no change to the grant")
        makeGetRequest((dynamicEntity_Request / "test_rl" / recordId).GET <@ (user2)).code should equal(404)

        When("user3 joins the Organisation")
        code_organisation.addMember(organisationId, resourceUser3.userId, code_organisation.RoleMember, resourceUser1.userId)
        Then("user3 gains access")
        makeGetRequest((dynamicEntity_Request / "test_rl" / recordId).GET <@ (user3)).code should equal(200)

        When("user1 revokes the Organisation")
        val revokeResponse = makeDeleteRequest(
          (dynamicEntity_Request / "test_rl" / recordId / "access" / "organisations" / organisationId).DELETE <@ (user1))
        revokeResponse.code should equal(200)
        (revokeResponse.body \ "revoked_count").extract[Int] should equal(1)
        Then("its members lose access, and the creator keeps theirs")
        makeGetRequest((dynamicEntity_Request / "test_rl" / recordId).GET <@ (user3)).code should equal(404)
        makeGetRequest((dynamicEntity_Request / "test_rl" / recordId).GET <@ (user1)).code should equal(200)
      } finally {
        deleteSystemEntity(dynamicEntityId)
        code_organisation.deleteOrganisation(organisationId)
      }
    }

    scenario("7.2: an entry must name a User or an Organisation that exists", VersionOfApi) {
      val (createStatus, body) = createSystemEntity(entityRowLevel)
      createStatus should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      val organisationId = createOrganisationWithMember(resourceUser2.userId)
      try {
        val recordId = createRecord("checked entries")

        When("an entry names both a user_id and an organisation_id")
        val bothResponse = makePutRequest(
          (dynamicEntity_Request / "test_rl" / recordId / "access").PUT <@ (user1),
          write(("user_id" -> resourceUser2.userId) ~ ("organisation_id" -> organisationId) ~ ("can_read" -> true)))
        Then("it is 400")
        bothResponse.code should equal(400)
        bothResponse.body.toString should include(code.api.util.ErrorMessages.InvalidRecordAccessEntry.takeWhile(_ != ':'))

        When("an entry names an Organisation that does not exist")
        val unknownResponse = grantToOrganisation(recordId, "no-such-organisation", canUpdate = false)
        Then("it is 404")
        unknownResponse.code should equal(404)
        unknownResponse.body.toString should include(code.api.util.ErrorMessages.RecordAccessOrganisationNotFound.takeWhile(_ != ':'))

        When("the Organisation is deleted after it was granted access")
        grantToOrganisation(recordId, organisationId, canUpdate = false).code should equal(200)
        code_organisation.deleteOrganisation(organisationId)
        Then("its grant is gone from the access list")
        val accessList = makeGetRequest((dynamicEntity_Request / "test_rl" / recordId / "access").GET <@ (user1))
        (accessList.body \ "organisation_access").asInstanceOf[JArray].arr should be(empty)
      } finally {
        deleteSystemEntity(dynamicEntityId)
        code_organisation.deleteOrganisation(organisationId)
      }
    }
  }

  private val code_organisation = code.organisation.Organisations
}
