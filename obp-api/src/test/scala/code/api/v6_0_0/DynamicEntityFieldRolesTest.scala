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
import com.openbankproject.commons.model.ErrorMessage
import code.api.util.ApiRole._
import code.entitlement.Entitlement
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonDSL._
import org.json4s.native.Serialization.write
import org.json4s._
import com.openbankproject.commons.util.JsonAliases.parse
import org.scalatest.Tag

/**
 * Field-level write/read role permissions on Dynamic Entities.
 *
 * Lives in v6_0_0 because Dynamic Entity *definitions* are created via the v6.0.0
 * `management/system-dynamic-entities` endpoint and the DE test harness lives here; the runtime
 * instance CRUD (incl. the new PATCH) is served on the version-agnostic `/obp/dynamic-entity` path.
 */
class DynamicEntityFieldRolesTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)

  // ==================== Helpers ====================

  private def grant(role: String): Unit =
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, role)

  // Grant to a specific user. Creating a dynamic entity auto-grants its CRUD roles to the *creator*
  // (resourceUser1, via createSystemEntity), so the "no entity update role" scenarios use resourceUser2 —
  // a user who did not create the entity and therefore only holds what we explicitly grant here.
  private def grantTo(userId: String, role: String): Unit =
    Entitlement.entitlement.vend.addEntitlement("", userId, role)

  private def createSystemEntity(entityJson: JValue): (Int, JValue) = {
    grant(CanCreateSystemLevelDynamicEntity.toString)
    val request = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
    val response = makePostRequest(request, write(entityJson))
    (response.code, response.body)
  }

  private def deleteSystemEntity(dynamicEntityId: String): Unit = {
    grant(CanDeleteSystemLevelDynamicEntity.toString)
    val deleteRequest = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1)
    makeDeleteRequest(deleteRequest)
  }

  // ==================== Fixture ====================

  private val entityName = "field_roles_test"
  private val single = "field_roles_test"      // singleName wrapper key
  private val idName = "field_roles_test_id"

  // Auto-generated (system-level) field roles
  private val writeInternalRole = "CanWriteDynamicEntityField_Systemfield_roles_test__internal_note"
  private val readSecretRole    = "CanGetDynamicEntityField_Systemfield_roles_test__secret_note"
  // Entity-level (system-level) roles
  private val createRole = "CanCreateDynamicEntity_Systemfield_roles_test"
  private val getRole    = "CanGetDynamicEntity_Systemfield_roles_test"
  private val updateRole = "CanUpdateDynamicEntity_Systemfield_roles_test"

  private val schema: JValue = parse(
    """
      |{
      |  "description": "Field-level role test entity.",
      |  "required": ["name"],
      |  "properties": {
      |    "name":          {"type": "string", "minLength": 1, "maxLength": 40, "example": "Acme"},
      |    "internal_note": {"type": "string", "example": "set via patch", "write_role_required": true},
      |    "secret_note":   {"type": "string", "example": "hush", "read_role_required": true}
      |  }
      |}
    """.stripMargin)

  private val entity: JValue =
    ("entity_name" -> entityName) ~
    ("has_personal_entity" -> true) ~
    ("schema" -> schema)

  private def recordId(createBody: JValue): String = (createBody \ single \ idName).extract[String]

  // ---- Per-entity fixtures for the per-field authorisation scenarios ----
  // Each scenario uses a UNIQUE entity name so the (entity-scoped) role names don't collide with grants
  // accumulated by earlier scenarios on resourceUser1 — that's what lets us test "role X alone".
  private def createRoleFor(n: String)    = s"CanCreateDynamicEntity_System$n"
  private def getRoleFor(n: String)       = s"CanGetDynamicEntity_System$n"
  private def updateRoleFor(n: String)    = s"CanUpdateDynamicEntity_System$n"
  private def writeNoteRoleFor(n: String) = s"CanWriteDynamicEntityField_System${n}__internal_note"

  private def fieldRolesEntity(n: String): JValue =
    ("entity_name" -> n) ~
    ("has_personal_entity" -> true) ~
    ("schema" -> parse(
      s"""{"description":"Per-field auth test entity.","required":["name"],
         |"properties":{
         |"name":{"type":"string","minLength":1,"maxLength":40,"example":"Acme"},
         |"internal_note":{"type":"string","example":"set via patch","write_role_required":true},
         |"secret_note":{"type":"string","example":"hush","read_role_required":true}}}""".stripMargin))

  private def recordIdFor(n: String, body: JValue): String = (body \ n \ s"${n}_id").extract[String]

  // ==================== Scenarios ====================

  Feature("Field-level write/read role permissions on Dynamic Entities") {

    Scenario("A definition with field-level keywords can be created", VersionOfApi) {
      val (code, body) = createSystemEntity(entity)
      try code should equal(201)
      finally deleteSystemEntity((body \ "dynamic_entity_id").extract[String])
    }

    Scenario("POST drops a write-restricted field", VersionOfApi) {
      val (code, body) = createSystemEntity(entity)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      try {
        grant(createRole); grant(getRole)
        When("We POST a record that includes the write-restricted internal_note")
        val createResp = makePostRequest((dynamicEntity_Request / entityName).POST <@(user1),
          write(parse("""{"name":"Acme","internal_note":"should be dropped"}""")))
        createResp.code should equal(201)
        val id = recordId(createResp.body)

        Then("GET should not contain internal_note (it was stripped at create)")
        val getResp = makeGetRequest((dynamicEntity_Request / entityName / id).GET <@(user1))
        getResp.code should equal(200)
        (getResp.body \ single \ "name").extract[String] should equal("Acme")
        (getResp.body \ single \ "internal_note") should equal(JNothing)
      } finally deleteSystemEntity(dynamicEntityId)
    }

    Scenario("PUT cannot set a write-restricted field", VersionOfApi) {
      val (code, body) = createSystemEntity(entity)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      try {
        grant(createRole); grant(getRole); grant(updateRole)
        val createResp = makePostRequest((dynamicEntity_Request / entityName).POST <@(user1), write(parse("""{"name":"Acme"}""")))
        val id = recordId(createResp.body)

        When("We PUT trying to set internal_note")
        val putResp = makePutRequest((dynamicEntity_Request / entityName / id).PUT <@(user1),
          write(parse("""{"name":"Acme2","internal_note":"hacked"}""")))
        putResp.code should equal(200)

        Then("internal_note remains unset; the unrestricted field updated")
        val getResp = makeGetRequest((dynamicEntity_Request / entityName / id).GET <@(user1))
        (getResp.body \ single \ "name").extract[String] should equal("Acme2")
        (getResp.body \ single \ "internal_note") should equal(JNothing)
      } finally deleteSystemEntity(dynamicEntityId)
    }

    Scenario("PATCH a write-restricted field requires the field write role", VersionOfApi) {
      val (code, body) = createSystemEntity(entity)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      try {
        grant(createRole); grant(getRole); grant(updateRole)
        val createResp = makePostRequest((dynamicEntity_Request / entityName).POST <@(user1), write(parse("""{"name":"Acme"}""")))
        val id = recordId(createResp.body)

        When("We PATCH internal_note WITHOUT the field write role")
        val patch1 = makePatchRequest((dynamicEntity_Request / entityName / id).PATCH <@(user1),
          write(parse("""{"internal_note":"viaPatch"}""")))
        Then("We get 403")
        patch1.code should equal(403)

        When("We grant the field write role and PATCH again")
        grant(writeInternalRole)
        val patch2 = makePatchRequest((dynamicEntity_Request / entityName / id).PATCH <@(user1),
          write(parse("""{"internal_note":"viaPatch"}""")))
        Then("We get 200 and the value is set; the other field is preserved")
        patch2.code should equal(200)
        val getResp = makeGetRequest((dynamicEntity_Request / entityName / id).GET <@(user1))
        (getResp.body \ single \ "internal_note").extract[String] should equal("viaPatch")
        (getResp.body \ single \ "name").extract[String] should equal("Acme")
      } finally deleteSystemEntity(dynamicEntityId)
    }

    Scenario("GET omits a read-restricted field unless the caller holds the read role", VersionOfApi) {
      val (code, body) = createSystemEntity(entity)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]
      try {
        grant(createRole); grant(getRole)
        When("We POST a record with secret_note (read-restricted but writable)")
        val createResp = makePostRequest((dynamicEntity_Request / entityName).POST <@(user1),
          write(parse("""{"name":"Acme","secret_note":"hush"}""")))
        createResp.code should equal(201)
        val id = recordId(createResp.body)

        Then("GET without the field read role omits secret_note")
        val getResp1 = makeGetRequest((dynamicEntity_Request / entityName / id).GET <@(user1))
        (getResp1.body \ single \ "secret_note") should equal(JNothing)

        When("We grant the field read role")
        grant(readSecretRole)
        Then("GET now includes secret_note")
        val getResp2 = makeGetRequest((dynamicEntity_Request / entityName / id).GET <@(user1))
        (getResp2.body \ single \ "secret_note").extract[String] should equal("hush")
      } finally deleteSystemEntity(dynamicEntityId)
    }
  }

  Feature("Per-field PATCH authorisation (no blanket entity-update precondition)") {

    Scenario("Field write role alone (no entity update role) can PATCH the restricted field", VersionOfApi) {
      val n = "fr_field_alone"
      val (code, body) = createSystemEntity(fieldRolesEntity(n))   // user1 is the creator (auto-granted entity roles)
      code should equal(201)
      val deId = (body \ "dynamic_entity_id").extract[String]
      try {
        val createResp = makePostRequest((dynamicEntity_Request / n).POST <@(user1), write(parse("""{"name":"Acme"}""")))
        createResp.code should equal(201)
        val id = recordIdFor(n, createResp.body)
        // user2 (NOT the creator) holds ONLY the field write role — no entity update/get role.
        grantTo(resourceUser2.userId, writeNoteRoleFor(n))

        When("user2 PATCHes the restricted field holding only its field write role (no entity update role)")
        val patch = makePatchRequest((dynamicEntity_Request / n / id).PATCH <@(user2), write(parse("""{"internal_note":"viaPatch"}""")))
        Then("It succeeds — a field role alone is sufficient to write that field")
        patch.code should equal(200)
        val getResp = makeGetRequest((dynamicEntity_Request / n / id).GET <@(user1))
        (getResp.body \ n \ "internal_note").extract[String] should equal("viaPatch")
        (getResp.body \ n \ "name").extract[String] should equal("Acme")
      } finally deleteSystemEntity(deId)
    }

    Scenario("Field write role alone cannot PATCH an unrestricted field", VersionOfApi) {
      val n = "fr_unrestricted_denied"
      val (code, body) = createSystemEntity(fieldRolesEntity(n))   // user1 is the creator
      code should equal(201)
      val deId = (body \ "dynamic_entity_id").extract[String]
      try {
        val createResp = makePostRequest((dynamicEntity_Request / n).POST <@(user1), write(parse("""{"name":"Acme"}""")))
        val id = recordIdFor(n, createResp.body)
        // user2 (NOT the creator) holds ONLY the field write role — no entity update role.
        grantTo(resourceUser2.userId, writeNoteRoleFor(n))

        When("user2 PATCHes an unrestricted field without the entity update role")
        val patch1 = makePatchRequest((dynamicEntity_Request / n / id).PATCH <@(user2), write(parse("""{"name":"Acme2"}""")))
        Then("We get 403 naming the entity update role")
        patch1.code should equal(403)
        patch1.body.extract[ErrorMessage].message should include(updateRoleFor(n))

        And("A mixed body (restricted + unrestricted) is also rejected")
        val patch2 = makePatchRequest((dynamicEntity_Request / n / id).PATCH <@(user2), write(parse("""{"internal_note":"x","name":"Acme2"}""")))
        patch2.code should equal(403)

        And("The unrestricted field is unchanged")
        val getResp = makeGetRequest((dynamicEntity_Request / n / id).GET <@(user1))
        (getResp.body \ n \ "name").extract[String] should equal("Acme")
      } finally deleteSystemEntity(deId)
    }

    Scenario("Entity update role alone can PATCH unrestricted fields but not restricted ones", VersionOfApi) {
      val n = "fr_baseline_only"
      val (code, body) = createSystemEntity(fieldRolesEntity(n))
      code should equal(201)
      val deId = (body \ "dynamic_entity_id").extract[String]
      try {
        grant(createRoleFor(n)); grant(getRoleFor(n)); grant(updateRoleFor(n))   // NOT the field write role
        val createResp = makePostRequest((dynamicEntity_Request / n).POST <@(user1), write(parse("""{"name":"Acme"}""")))
        val id = recordIdFor(n, createResp.body)

        When("We PATCH an unrestricted field with the entity update role")
        val patch1 = makePatchRequest((dynamicEntity_Request / n / id).PATCH <@(user1), write(parse("""{"name":"Acme2"}""")))
        Then("It succeeds")
        patch1.code should equal(200)

        When("We PATCH the restricted field without its field write role")
        val patch2 = makePatchRequest((dynamicEntity_Request / n / id).PATCH <@(user1), write(parse("""{"internal_note":"x"}""")))
        Then("We get 403 naming the field write role")
        patch2.code should equal(403)
        patch2.body.extract[ErrorMessage].message should include(writeNoteRoleFor(n))
      } finally deleteSystemEntity(deId)
    }

    Scenario("PATCH a restricted field with its current (unchanged) value still requires the role", VersionOfApi) {
      val n = "fr_unchanged_value"
      val (code, body) = createSystemEntity(fieldRolesEntity(n))
      code should equal(201)
      val deId = (body \ "dynamic_entity_id").extract[String]
      try {
        grant(createRoleFor(n)); grant(getRoleFor(n)); grant(updateRoleFor(n)); grant(writeNoteRoleFor(n))
        val createResp = makePostRequest((dynamicEntity_Request / n).POST <@(user1), write(parse("""{"name":"Acme"}""")))
        val id = recordIdFor(n, createResp.body)
        // user1 (who holds the field role) sets internal_note to a known value
        makePatchRequest((dynamicEntity_Request / n / id).PATCH <@(user1), write(parse("""{"internal_note":"verified"}"""))).code should equal(200)

        When("user2 (no roles for this entity) PATCHes the restricted field to the SAME value")
        val patch = makePatchRequest((dynamicEntity_Request / n / id).PATCH <@(user2), write(parse("""{"internal_note":"verified"}""")))
        Then("It is still rejected — presence in the body is checked, not whether the value changed")
        patch.code should equal(403)
        patch.body.extract[ErrorMessage].message should include(writeNoteRoleFor(n))
      } finally deleteSystemEntity(deId)
    }

    Scenario("Personal entity without personal_requires_role: unrestricted PATCH needs no role; restricted still needs the field role", VersionOfApi) {
      val n = "fr_personal"
      val (code, body) = createSystemEntity(fieldRolesEntity(n))   // has_personal_entity=true, personal_requires_role defaults false
      code should equal(201)
      val deId = (body \ "dynamic_entity_id").extract[String]
      try {
        // user2 holds no roles for this entity; personal_requires_role defaults false.
        When("user2 creates a personal record without any entity role")
        val createResp = makePostRequest((dynamicEntity_Request / "my" / n).POST <@(user2), write(parse("""{"name":"Acme"}""")))
        createResp.code should equal(201)
        val id = recordIdFor(n, createResp.body)

        Then("PATCH of an unrestricted field succeeds without any role")
        makePatchRequest((dynamicEntity_Request / "my" / n / id).PATCH <@(user2), write(parse("""{"name":"Acme2"}"""))).code should equal(200)

        And("PATCH of the restricted field is still rejected without the field write role")
        val patch1 = makePatchRequest((dynamicEntity_Request / "my" / n / id).PATCH <@(user2), write(parse("""{"internal_note":"x"}""")))
        patch1.code should equal(403)
        patch1.body.extract[ErrorMessage].message should include(writeNoteRoleFor(n))

        And("Granting the field write role lets the restricted field be PATCHed")
        grantTo(resourceUser2.userId, writeNoteRoleFor(n))
        makePatchRequest((dynamicEntity_Request / "my" / n / id).PATCH <@(user2), write(parse("""{"internal_note":"x"}"""))).code should equal(200)
      } finally deleteSystemEntity(deId)
    }

    // Mirrors the original reproduction: a field declares an EXPLICIT, shareable write_role (rather than the
    // auto-generated CanWriteDynamicEntityField_* role). Granting that role to another user lets them PATCH the
    // field on the field role ALONE — no entity update role required.
    Scenario("Explicit write_role: a named shareable role lets another user PATCH the field alone", VersionOfApi) {
      val n = "fr_explicit_role"
      val explicitRole = "CanUpdateWritableExplicit"   // explicit role named in the schema (cf. the ticket's CanUpdateWritable)
      val (code, body) = createSystemEntity(
        ("entity_name" -> n) ~
        ("has_personal_entity" -> false) ~
        ("schema" -> parse(
          s"""{"description":"Explicit write_role test entity.","required":["some_id"],
             |"properties":{
             |"some_id":{"type":"string","minLength":1,"maxLength":40,"example":"3dece208"},
             |"status_code":{"type":"string","example":"verified","write_role":"$explicitRole",
             |"description":"in_progress, verified, failed"}}}""".stripMargin)))
      code should equal(201)
      val deId = (body \ "dynamic_entity_id").extract[String]
      try {
        grant(createRoleFor(n)); grant(getRoleFor(n))   // user1 (creator already auto-granted, but explicit for clarity)
        val createResp = makePostRequest((dynamicEntity_Request / n).POST <@(user1), write(parse("""{"some_id":"x1"}""")))
        createResp.code should equal(201)
        val id = recordIdFor(n, createResp.body)

        When("user2 PATCHes status_code WITHOUT the explicit role")
        val patch1 = makePatchRequest((dynamicEntity_Request / n / id).PATCH <@(user2), write(parse("""{"status_code":"verified"}""")))
        Then("We get 403 naming the explicit role (NOT the auto-generated field role)")
        patch1.code should equal(403)
        patch1.body.extract[ErrorMessage].message should include(explicitRole)

        When("We grant the explicit role to user2 and PATCH again")
        grantTo(resourceUser2.userId, explicitRole)
        val patch2 = makePatchRequest((dynamicEntity_Request / n / id).PATCH <@(user2), write(parse("""{"status_code":"verified"}""")))
        Then("It succeeds on the explicit field role alone — no entity update role needed")
        patch2.code should equal(200)
        val getResp = makeGetRequest((dynamicEntity_Request / n / id).GET <@(user1))
        (getResp.body \ n \ "status_code").extract[String] should equal("verified")
        (getResp.body \ n \ "some_id").extract[String] should equal("x1")
      } finally deleteSystemEntity(deId)
    }
  }
}
