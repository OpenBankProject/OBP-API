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

/**
 * auth_mode on a Dynamic Entity decides who may hold the roles guarding its data endpoints:
 * the User's Entitlements (UserOnly, the default), the Consumer's Scopes (ApplicationOnly),
 * either (UserOrApplication) or both (UserAndApplication).
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
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
    val response = makePostRequest((v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1), write(json))
    (response.code, response.body)
  }

  def deleteSystemEntity(dynamicEntityId: String): Unit = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
    makeDeleteRequest((v6_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1))
  }

  def getRoleName(entityName: String): String = s"CanGetDynamicEntity_System$entityName"
  def createRoleName(entityName: String): String = s"CanCreateDynamicEntity_System$entityName"

  feature("auth_mode on the entity definition") {

    scenario("defaults to UserOnly and is returned on the definition", VersionOfApi) {
      val (code, body) = createSystemEntity(entityJson("am_default", None))
      code should equal(201)
      (body \ "auth_mode").extract[String] should equal("UserOnly")
      deleteSystemEntity((body \ "dynamic_entity_id").extract[String])
    }

    scenario("accepts UserOrApplication and returns it", VersionOfApi) {
      val (code, body) = createSystemEntity(entityJson("am_either", Some("UserOrApplication")))
      code should equal(201)
      (body \ "auth_mode").extract[String] should equal("UserOrApplication")
      deleteSystemEntity((body \ "dynamic_entity_id").extract[String])
    }

    scenario("rejects an unknown value", VersionOfApi) {
      val (code, body) = createSystemEntity(entityJson("am_bad", Some("Nobody")))
      code should equal(400)
      (body \ "message").extract[String] should include(DynamicEntityInstanceValidateFail)
    }

    scenario("rejects ApplicationOnly on a personal entity", VersionOfApi) {
      val (code, body) = createSystemEntity(entityJson("am_personal_app", Some("ApplicationOnly"), personal = true))
      code should equal(400)
      (body \ "message").extract[String] should include("ApplicationOnly")
    }
  }

  feature("auth_mode on the entity's data endpoints") {

    scenario("UserOnly (default): a Consumer Scope alone is not enough", VersionOfApi) {
      val entityName = "am_useronly_data"
      val (code, body) = createSystemEntity(entityJson(entityName, None))
      code should equal(201)
      val entityId = (body \ "dynamic_entity_id").extract[String]
      val scope = Scope.scope.vend.addScope("", testConsumer2.id.get.toString, getRoleName(entityName))
      try {
        val response = makeGetRequest((dynamicEntity_Request / entityName).GET <@(user2))
        response.code should equal(403)
        response.body.extract[ErrorMessage].message should include(UserHasMissingRoles)
      } finally {
        Scope.scope.vend.deleteScope(scope)
        deleteSystemEntity(entityId)
      }
    }

    scenario("UserOrApplication: a Consumer Scope alone is enough", VersionOfApi) {
      val entityName = "am_either_data"
      val (code, body) = createSystemEntity(entityJson(entityName, Some("UserOrApplication")))
      code should equal(201)
      val entityId = (body \ "dynamic_entity_id").extract[String]
      val scope = Scope.scope.vend.addScope("", testConsumer2.id.get.toString, getRoleName(entityName))
      try {
        val response = makeGetRequest((dynamicEntity_Request / entityName).GET <@(user2))
        response.code should equal(200)
      } finally {
        Scope.scope.vend.deleteScope(scope)
        deleteSystemEntity(entityId)
      }
    }

    scenario("UserOrApplication: a User Entitlement alone is still enough", VersionOfApi) {
      val entityName = "am_either_user"
      val (code, body) = createSystemEntity(entityJson(entityName, Some("UserOrApplication")))
      code should equal(201)
      val entityId = (body \ "dynamic_entity_id").extract[String]
      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, getRoleName(entityName))
      try {
        val response = makeGetRequest((dynamicEntity_Request / entityName).GET <@(user2))
        response.code should equal(200)
      } finally {
        deleteSystemEntity(entityId)
      }
    }

    scenario("UserOrApplication: the Scope only covers the role it names (Get, not Create)", VersionOfApi) {
      val entityName = "am_either_scoped"
      val (code, body) = createSystemEntity(entityJson(entityName, Some("UserOrApplication")))
      code should equal(201)
      val entityId = (body \ "dynamic_entity_id").extract[String]
      val scope = Scope.scope.vend.addScope("", testConsumer2.id.get.toString, getRoleName(entityName))
      try {
        val response = makePostRequest((dynamicEntity_Request / entityName).POST <@(user2), write(("name" -> "x"): JObject))
        response.code should equal(403)
        response.body.extract[ErrorMessage].message should include(createRoleName(entityName))
      } finally {
        Scope.scope.vend.deleteScope(scope)
        deleteSystemEntity(entityId)
      }
    }

    scenario("UserAndApplication: needs both the Entitlement and the Scope", VersionOfApi) {
      val entityName = "am_both_data"
      val (code, body) = createSystemEntity(entityJson(entityName, Some("UserAndApplication")))
      code should equal(201)
      val entityId = (body \ "dynamic_entity_id").extract[String]
      try {
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, getRoleName(entityName))
        makeGetRequest((dynamicEntity_Request / entityName).GET <@(user2)).code should equal(403)
        val scope = Scope.scope.vend.addScope("", testConsumer2.id.get.toString, getRoleName(entityName))
        try {
          makeGetRequest((dynamicEntity_Request / entityName).GET <@(user2)).code should equal(200)
        } finally {
          Scope.scope.vend.deleteScope(scope)
        }
      } finally {
        deleteSystemEntity(entityId)
      }
    }
  }
}
