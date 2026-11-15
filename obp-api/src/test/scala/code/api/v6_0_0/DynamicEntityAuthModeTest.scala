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

package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import com.openbankproject.commons.model.ErrorMessage
import code.api.util.ErrorMessages.{DynamicEntityInstanceValidateFail, UserHasMissingRoles}
import code.entitlement.Entitlement
import code.scope.Scope
import com.openbankproject.commons.util.ApiVersion
import com.openbankproject.commons.util.JsonAliases._
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.Serialization.write
import org.scalatest.Tag
import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID

/**
 * auth_mode on a Dynamic Entity is gone. A client that still sends it is not refused: the field is ignored.
 * The entity's data endpoints work like static endpoints: a person needs the entity's Role as an Entitlement,
 * and a Platform App acting as itself needs it as a Scope.
 */
class DynamicEntityAuthModeTest extends V600ServerSetup {
  // user1 defines the entities (and may hold broad roles in the shared test DB);
  // user2, who starts with none of the entity roles and signs with testConsumer2, is the subject
  // of the data-endpoint checks, so Scopes go on testConsumer2.

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)

  def simpleSchema: JValue = parse(
    """
      |{
      |    "description": "Test entity for auth mode testing.",
      |    "required": ["name"],
      |    "properties": {
      |        "name": { "type": "string", "maxLength": 40, "minLength": 1, "example": "Test" }
      |    }
      |}
    """.stripMargin)

  def entityJson(name: String, authMode: Option[String], personal: Boolean = false): JValue = {
    val base: JObject = ("entity_name" -> name) ~ ("has_personal_entity" -> personal) ~ ("schema" -> simpleSchema)
    authMode.map(m => base ~ ("auth_mode" -> m)).getOrElse(base)
  }

  def createSystemEntity(json: JValue): (Int, JValue) = {
    Entitlement.entitlement.vend.addEntitlement(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser1.userId, CanCreateDynamicEntityDefinition.toString)
    val response = makePostRequest((v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1), write(json))
    (response.code, response.body)
  }

  def deleteSystemEntity(dynamicEntityId: String): Unit = {
    Entitlement.entitlement.vend.addEntitlement(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser1.userId, CanDeleteDynamicEntityDefinition.toString)
    makeDeleteRequest((v6_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1))
  }

  def getRoleName(entityName: String): String = s"CanGetDynamicEntityRecord_$entityName"
  def createRoleName(entityName: String): String = s"CanCreateDynamicEntityRecord_$entityName"

  feature("auth_mode on the entity definition") {

    scenario("is not returned on the definition", VersionOfApi) {
      val (code, body) = createSystemEntity(entityJson("am_default", None))
      code should equal(201)
      (body \ "auth_mode") should equal(JNothing)
      deleteSystemEntity((body \ "dynamic_entity_id").extract[String])
    }

    scenario("is accepted and ignored when an older client still sends it, whatever its value", VersionOfApi) {
      for ((name, mode) <- List("am_either" -> "UserXORApplication", "am_bad" -> "Nobody")) {
        val (code, body) = createSystemEntity(entityJson(name, Some(mode)))
        code should equal(201)
        (body \ "auth_mode") should equal(JNothing)
        deleteSystemEntity((body \ "dynamic_entity_id").extract[String])
      }
    }

    scenario("ApplicationOnly on a personal entity is accepted too", VersionOfApi) {
      val (code, body) = createSystemEntity(entityJson("am_personal_app", Some("ApplicationOnly"), personal = true))
      code should equal(201)
      deleteSystemEntity((body \ "dynamic_entity_id").extract[String])
    }
  }

  feature("the entity's data endpoints work like static endpoints, whatever auth_mode says") {

    scenario("no auth_mode: a Platform App acting as itself is let in on its Scope", VersionOfApi) {
      val entityName = "am_useronly_data"
      val (code, body) = createSystemEntity(entityJson(entityName, None))
      code should equal(201)
      val entityId = (body \ "dynamic_entity_id").extract[String]
      val scope = Scope.scope.vend.addScope(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, testConsumer2.id.get.toString, getRoleName(entityName))
      try {
        makeGetRequest((dynamicEntity_Request / entityName).GET <@(application2)).code should equal(200)
      } finally {
        Scope.scope.vend.deleteScope(scope)
        deleteSystemEntity(entityId)
      }
    }

    scenario("a Consumer that is not a Platform App does not get its Scope", VersionOfApi) {
      val entityName = "am_not_platform"
      val (code, body) = createSystemEntity(entityJson(entityName, Some("UserXORApplication")))
      code should equal(201)
      val entityId = (body \ "dynamic_entity_id").extract[String]
      val scope = Scope.scope.vend.addScope(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, testConsumer2.id.get.toString, getRoleName(entityName))
      _root_.code.platformapp.PlatformApps.platformAppProvider.vend.deletePlatformApp(testConsumer2.consumerId.get)
      try {
        val response = makeGetRequest((dynamicEntity_Request / entityName).GET <@(Some((consumer2, tokenApplication2))))
        response.code should equal(403)
        response.body.extract[ErrorMessage].message should include(UserHasMissingRoles)
      } finally {
        ensurePlatformApp(testConsumer2)
        Scope.scope.vend.deleteScope(scope)
        deleteSystemEntity(entityId)
      }
    }

    scenario("UserXORApplication: a Consumer Scope alone is enough for the Consumer acting as itself", VersionOfApi) {
      val entityName = "am_either_data"
      val (code, body) = createSystemEntity(entityJson(entityName, Some("UserXORApplication")))
      code should equal(201)
      val entityId = (body \ "dynamic_entity_id").extract[String]
      val scope = Scope.scope.vend.addScope(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, testConsumer2.id.get.toString, getRoleName(entityName))
      try {
        val response = makeGetRequest((dynamicEntity_Request / entityName).GET <@(application2))
        response.code should equal(200)
      } finally {
        Scope.scope.vend.deleteScope(scope)
        deleteSystemEntity(entityId)
      }
    }

    scenario("UserXORApplication: a person does not get the Scope of the Consumer they call through", VersionOfApi) {
      val entityName = "am_either_person"
      val (code, body) = createSystemEntity(entityJson(entityName, Some("UserXORApplication")))
      code should equal(201)
      val entityId = (body \ "dynamic_entity_id").extract[String]
      val scope = Scope.scope.vend.addScope(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, testConsumer2.id.get.toString, getRoleName(entityName))
      try {
        val response = makeGetRequest((dynamicEntity_Request / entityName).GET <@(user2))
        response.code should equal(403)
        response.body.extract[ErrorMessage].message should include(UserHasMissingRoles)
      } finally {
        Scope.scope.vend.deleteScope(scope)
        deleteSystemEntity(entityId)
      }
    }

    scenario("UserXORApplication: a User Entitlement alone is still enough", VersionOfApi) {
      val entityName = "am_either_user"
      val (code, body) = createSystemEntity(entityJson(entityName, Some("UserXORApplication")))
      code should equal(201)
      val entityId = (body \ "dynamic_entity_id").extract[String]
      Entitlement.entitlement.vend.addEntitlement(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser2.userId, getRoleName(entityName))
      try {
        val response = makeGetRequest((dynamicEntity_Request / entityName).GET <@(user2))
        response.code should equal(200)
      } finally {
        deleteSystemEntity(entityId)
      }
    }

    scenario("UserXORApplication: the Scope only covers the role it names (Get, not Create)", VersionOfApi) {
      val entityName = "am_either_scoped"
      val (code, body) = createSystemEntity(entityJson(entityName, Some("UserXORApplication")))
      code should equal(201)
      val entityId = (body \ "dynamic_entity_id").extract[String]
      val scope = Scope.scope.vend.addScope(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, testConsumer2.id.get.toString, getRoleName(entityName))
      try {
        val response = makePostRequest((dynamicEntity_Request / entityName).POST <@(application2), write(("name" -> "x"): JObject))
        response.code should equal(403)
        response.body.extract[ErrorMessage].message should include(createRoleName(entityName))
      } finally {
        Scope.scope.vend.deleteScope(scope)
        deleteSystemEntity(entityId)
      }
    }

    scenario("UserAndApplication no longer asks for the Scope as well: the Entitlement is enough", VersionOfApi) {
      val entityName = "am_both_data"
      val (code, body) = createSystemEntity(entityJson(entityName, Some("UserAndApplication")))
      code should equal(201)
      val entityId = (body \ "dynamic_entity_id").extract[String]
      try {
        Entitlement.entitlement.vend.addEntitlement(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser2.userId, getRoleName(entityName))
        makeGetRequest((dynamicEntity_Request / entityName).GET <@(user2)).code should equal(200)
      } finally {
        deleteSystemEntity(entityId)
      }
    }
  }
}
