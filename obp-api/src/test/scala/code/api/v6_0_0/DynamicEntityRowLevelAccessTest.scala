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
Osloerstrasse 16/17
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

/**
 * Row-level access (use_row_level_access) — see ideas/DYNAMIC_ENTITY_ROW_LEVEL_ACCESS.md.
 * Covers: owner bootstrap, per-row read isolation (404 hides), owner sharing with no role,
 * per-row update gate (403), revoke cascade, get-all ACL filter, admin-role override,
 * flag-off 400, and the §8.3 public/community mutual-exclusion guard.
 */
class DynamicEntityRowLevelAccessTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)

  // ==================== Helpers ====================

  def createSystemEntity(entityJson: JValue): (Int, JValue) = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
    val request = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@ (user1)
    val response = makePostRequest(request, write(entityJson))
    (response.code, response.body)
  }

  def deleteSystemEntity(dynamicEntityId: String): Unit = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
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
  // user1 creates records (it holds CanCreateDynamicEntity_Systemtest_rl from the definition create).
  def createRecord(name: String): String = {
    val resp = makePostRequest((dynamicEntity_Request / "test_rl").POST <@ (user1), write(("name" -> name): JValue))
    resp.code should equal(201)
    (resp.body \ "test_rl" \ "test_rl_id").extract[String]
  }

  // ==================== Feature 1: owner bootstrap & read isolation ====================

  feature("Feature 1: owner bootstrap & per-row read isolation") {
    scenario("1.1: creator reads own record; another user gets 404; list is ACL-filtered", VersionOfApi) {
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

  feature("Feature 2: owner shares (no role) and the per-row update gate") {
    scenario("2.1: owner grants read → grantee reads; update needs a separate grant", VersionOfApi) {
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

  feature("Feature 3: revoke removes access") {
    scenario("3.1: revoke a grantee → they lose read", VersionOfApi) {
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

  feature("Feature 4: CanGrantDynamicEntityRowAccess admin override") {
    scenario("4.1: a role holder may list/grant access on a row they cannot read", VersionOfApi) {
      val (code, body) = createSystemEntity(entityRowLevel)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      try {
        val id = createRecord("admin-managed")

        When("user3 (no ACL, no role) lists access on the row")
        Then("it is 403")
        makeGetRequest((dynamicEntity_Request / "test_rl" / id / "access").GET <@ (user3)).code should equal(403)

        When("user3 is granted the admin row-access role")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser3.userId, "CanGrantDynamicEntityRowAccess_Systemtest_rl")

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

  feature("Feature 5: flag-off and mutual-exclusion validation") {
    scenario("5.1: access endpoints return 400 for a non-row-level entity", VersionOfApi) {
      val normalEntity: JValue =
        ("entity_name" -> "test_normal_rl") ~ ("has_personal_entity" -> false) ~ ("schema" -> simpleSchema)
      val (code, body) = createSystemEntity(normalEntity)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      try {
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanCreateDynamicEntity_Systemtest_normal_rl")
        val createResp = makePostRequest((dynamicEntity_Request / "test_normal_rl").POST <@ (user1), write(("name" -> "x"): JValue))
        createResp.code should equal(201)
        val id = (createResp.body \ "test_normal_rl" \ "test_normal_rl_id").extract[String]

        When("we hit the row-access endpoint on an entity without use_row_level_access")
        Then("it is 400")
        makeGetRequest((dynamicEntity_Request / "test_normal_rl" / id / "access").GET <@ (user1)).code should equal(400)
      } finally deleteSystemEntity(dynamicEntityId)
    }

    scenario("5.2: use_row_level_access cannot be combined with has_public_access (§8.3)", VersionOfApi) {
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
  }

}
