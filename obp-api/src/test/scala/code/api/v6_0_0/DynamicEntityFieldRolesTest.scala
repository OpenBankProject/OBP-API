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
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Serialization.write
import net.liftweb.json._
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
      |    "internal_note": {"type": "string", "example": "set via patch", "writeRoleRequired": true},
      |    "secret_note":   {"type": "string", "example": "hush", "readRoleRequired": true}
      |  }
      |}
    """.stripMargin)

  private val entity: JValue =
    ("entity_name" -> entityName) ~
    ("has_personal_entity" -> true) ~
    ("schema" -> schema)

  private def recordId(createBody: JValue): String = (createBody \ single \ idName).extract[String]

  // ==================== Scenarios ====================

  feature("Field-level write/read role permissions on Dynamic Entities") {

    scenario("A definition with field-level keywords can be created", VersionOfApi) {
      val (code, body) = createSystemEntity(entity)
      try code should equal(201)
      finally deleteSystemEntity((body \ "dynamic_entity_id").extract[String])
    }

    scenario("POST drops a write-restricted field", VersionOfApi) {
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

    scenario("PUT cannot set a write-restricted field", VersionOfApi) {
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

    scenario("PATCH a write-restricted field requires the field write role", VersionOfApi) {
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

    scenario("GET omits a read-restricted field unless the caller holds the read role", VersionOfApi) {
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
}
