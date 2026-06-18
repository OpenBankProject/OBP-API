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

class DynamicEntityAccessFlagsTest extends V600ServerSetup {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)

  // ==================== Helper Methods ====================

  def createSystemEntity(entityJson: JValue): (Int, JValue) = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
    val request = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
    val response = makePostRequest(request, write(entityJson))
    (response.code, response.body)
  }

  def deleteSystemEntity(dynamicEntityId: String): Unit = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
    val deleteRequest = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1)
    makeDeleteRequest(deleteRequest)
  }

  // ==================== Entity Definition Fixtures ====================

  def simpleSchema: JValue = parse(
    """
      |{
      |    "description": "Test entity for access flag testing.",
      |    "required": ["name"],
      |    "properties": {
      |        "name": {
      |            "type": "string",
      |            "maxLength": 40,
      |            "minLength": 1,
      |            "example": "Test"
      |        }
      |    }
      |}
    """.stripMargin)

  // A: Default flags - has_personal_entity: true, all others default
  val entityDefault: JValue =
    ("entity_name" -> "test_default") ~
    ("has_personal_entity" -> true) ~
    ("schema" -> simpleSchema)

  // B: personal_requires_role: true
  val entityPersonalWithRole: JValue =
    ("entity_name" -> "test_personal_role") ~
    ("has_personal_entity" -> true) ~
    ("personal_requires_role" -> true) ~
    ("schema" -> simpleSchema)

  // C: has_public_access: true
  val entityPublicAccess: JValue =
    ("entity_name" -> "test_public") ~
    ("has_personal_entity" -> true) ~
    ("has_public_access" -> true) ~
    ("schema" -> simpleSchema)

  // D: has_community_access: true
  val entityCommunityAccess: JValue =
    ("entity_name" -> "test_community") ~
    ("has_personal_entity" -> true) ~
    ("has_community_access" -> true) ~
    ("schema" -> simpleSchema)

  // E: All flags true
  val entityAllFlags: JValue =
    ("entity_name" -> "test_all_flags") ~
    ("has_personal_entity" -> true) ~
    ("personal_requires_role" -> true) ~
    ("has_public_access" -> true) ~
    ("has_community_access" -> true) ~
    ("schema" -> simpleSchema)

  // F: has_personal_entity: false
  val entityNoPersonal: JValue =
    ("entity_name" -> "test_no_personal") ~
    ("has_personal_entity" -> false) ~
    ("schema" -> simpleSchema)

  // G: has_personal_entity: false, personal_requires_role: true
  val entityNoPersonalWithRole: JValue =
    ("entity_name" -> "test_no_personal_role") ~
    ("has_personal_entity" -> false) ~
    ("personal_requires_role" -> true) ~
    ("schema" -> simpleSchema)

  // ==================== Test Data ====================

  val testDataRecord: JValue = parse("""{"name": "Test Record"}""")
  val testDataRecordUpdated: JValue = parse("""{"name": "Updated Record"}""")

  // ==================== Feature 1: Default flags ====================

  feature("Feature 1: Default flags - has_personal_entity=true, others default") {

    scenario("1.1: /my/ endpoint works without role", VersionOfApi) {
      val (code, body) = createSystemEntity(entityDefault)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We POST to /my/test_default as user1")
        val createRequest = (dynamicEntity_Request / "my" / "test_default").POST <@(user1)
        val createResponse = makePostRequest(createRequest, write(testDataRecord))
        Then("We should get a 201")
        createResponse.code should equal(201)

        When("We GET /my/test_default as user1")
        val getRequest = (dynamicEntity_Request / "my" / "test_default").GET <@(user1)
        val getResponse = makeGetRequest(getRequest)
        Then("We should get a 200")
        getResponse.code should equal(200)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }

    scenario("1.2: /community/ and /public/ return 404 when flags are false", VersionOfApi) {
      val (code, body) = createSystemEntity(entityDefault)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We GET /public/test_default (no auth)")
        val publicRequest = (dynamicEntity_Request / "public" / "test_default").GET
        val publicResponse = makeGetRequest(publicRequest)
        Then("We should get a 404 because has_public_access is false")
        publicResponse.code should equal(404)

        When("We GET /community/test_default as user1")
        val communityRequest = (dynamicEntity_Request / "community" / "test_default").GET <@(user1)
        val communityResponse = makeGetRequest(communityRequest)
        Then("We should get a 404 because has_community_access is false")
        communityResponse.code should equal(404)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }
  }

  // ==================== Feature 2: personal_requires_role=true ====================

  feature("Feature 2: personal_requires_role=true") {

    scenario("2.1: /my/ POST requires CanCreate role", VersionOfApi) {
      val (code, body) = createSystemEntity(entityPersonalWithRole)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We POST to /my/test_personal_role as user2 without role")
        val createRequest = (dynamicEntity_Request / "my" / "test_personal_role").POST <@(user2)
        val createResponse = makePostRequest(createRequest, write(testDataRecord))
        Then("We should get a 403")
        createResponse.code should equal(403)

        When("We add CanCreateDynamicEntity_Systemtest_personal_role to user2")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanCreateDynamicEntity_Systemtest_personal_role")

        And("We POST again")
        val createResponse2 = makePostRequest(createRequest, write(testDataRecord))
        Then("We should get a 201")
        createResponse2.code should equal(201)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }

    scenario("2.2: /my/ GET requires CanGet role", VersionOfApi) {
      val (code, body) = createSystemEntity(entityPersonalWithRole)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We add CanCreate role to user2 and create a record")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanCreateDynamicEntity_Systemtest_personal_role")
        val createRequest = (dynamicEntity_Request / "my" / "test_personal_role").POST <@(user2)
        val createResponse = makePostRequest(createRequest, write(testDataRecord))
        createResponse.code should equal(201)

        When("We GET /my/test_personal_role as user2 without CanGet role")
        val getRequest = (dynamicEntity_Request / "my" / "test_personal_role").GET <@(user2)
        val getResponse = makeGetRequest(getRequest)
        Then("We should get a 403")
        getResponse.code should equal(403)

        When("We add CanGetDynamicEntity_Systemtest_personal_role to user2")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanGetDynamicEntity_Systemtest_personal_role")

        And("We GET again")
        val getResponse2 = makeGetRequest(getRequest)
        Then("We should get a 200")
        getResponse2.code should equal(200)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }

    scenario("2.3: /my/ PUT requires CanUpdate role", VersionOfApi) {
      val (code, body) = createSystemEntity(entityPersonalWithRole)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We add CanCreate role to user2 and create a record")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanCreateDynamicEntity_Systemtest_personal_role")
        val createRequest = (dynamicEntity_Request / "my" / "test_personal_role").POST <@(user2)
        val createResponse = makePostRequest(createRequest, write(testDataRecord))
        createResponse.code should equal(201)

        val recordId = (createResponse.body \ "test_personal_role" \ "test_personal_role_id").extract[String]

        When("We PUT /my/test_personal_role/<id> as user2 without CanUpdate role")
        val putRequest = (dynamicEntity_Request / "my" / "test_personal_role" / recordId).PUT <@(user2)
        val putResponse = makePutRequest(putRequest, write(testDataRecordUpdated))
        Then("We should get a 403")
        putResponse.code should equal(403)

        When("We add CanUpdateDynamicEntity_Systemtest_personal_role to user2")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanUpdateDynamicEntity_Systemtest_personal_role")

        And("We PUT again")
        val putResponse2 = makePutRequest(putRequest, write(testDataRecordUpdated))
        Then("We should get a 200")
        putResponse2.code should equal(200)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }

    scenario("2.4: /my/ DELETE requires CanDelete role", VersionOfApi) {
      val (code, body) = createSystemEntity(entityPersonalWithRole)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We add CanCreate role to user2 and create a record")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanCreateDynamicEntity_Systemtest_personal_role")
        val createRequest = (dynamicEntity_Request / "my" / "test_personal_role").POST <@(user2)
        val createResponse = makePostRequest(createRequest, write(testDataRecord))
        createResponse.code should equal(201)

        val recordId = (createResponse.body \ "test_personal_role" \ "test_personal_role_id").extract[String]

        When("We DELETE /my/test_personal_role/<id> as user2 without CanDelete role")
        val deleteRequest = (dynamicEntity_Request / "my" / "test_personal_role" / recordId).DELETE <@(user2)
        val deleteResponse = makeDeleteRequest(deleteRequest)
        Then("We should get a 403")
        deleteResponse.code should equal(403)

        When("We add CanDeleteDynamicEntity_Systemtest_personal_role to user2")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanDeleteDynamicEntity_Systemtest_personal_role")

        And("We DELETE again")
        val deleteResponse2 = makeDeleteRequest(deleteRequest)
        Then("We should get a 200")
        deleteResponse2.code should equal(200)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }
  }

  // ==================== Feature 3: has_public_access=true ====================

  feature("Feature 3: has_public_access=true") {

    scenario("3.1: /public/ GET list works without authentication", VersionOfApi) {
      val (code, body) = createSystemEntity(entityPublicAccess)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We create a non-personal record via system endpoint")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanCreateDynamicEntity_Systemtest_public")
        val createRequest = (dynamicEntity_Request / "test_public").POST <@(user1)
        val createResponse = makePostRequest(createRequest, write(testDataRecord))
        createResponse.code should equal(201)

        When("We GET /public/test_public without authentication")
        val publicGetRequest = (dynamicEntity_Request / "public" / "test_public").GET
        val publicGetResponse = makeGetRequest(publicGetRequest)
        Then("We should get a 200")
        publicGetResponse.code should equal(200)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }

    scenario("3.2: /public/ GET single record works without authentication", VersionOfApi) {
      val (code, body) = createSystemEntity(entityPublicAccess)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We create a non-personal record via system endpoint")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanCreateDynamicEntity_Systemtest_public")
        val createRequest = (dynamicEntity_Request / "test_public").POST <@(user1)
        val createResponse = makePostRequest(createRequest, write(testDataRecord))
        createResponse.code should equal(201)

        val recordId = (createResponse.body \ "test_public" \ "test_public_id").extract[String]

        When("We GET /public/test_public/<id> without authentication")
        val publicGetRequest = (dynamicEntity_Request / "public" / "test_public" / recordId).GET
        val publicGetResponse = makeGetRequest(publicGetRequest)
        Then("We should get a 200")
        publicGetResponse.code should equal(200)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }

    scenario("3.3: /public/ POST is not available (read-only)", VersionOfApi) {
      val (code, body) = createSystemEntity(entityPublicAccess)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We POST to /public/test_public")
        val postRequest = (dynamicEntity_Request / "public" / "test_public").POST <@(user1)
        val postResponse = makePostRequest(postRequest, write(testDataRecord))
        Then("We should get a 404 or 405 (endpoint not found)")
        postResponse.code should (equal(404) or equal(405))
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }
  }

  // ==================== Feature 4: has_community_access=true ====================

  feature("Feature 4: has_community_access=true") {

    scenario("4.1: /community/ GET requires authentication", VersionOfApi) {
      val (code, body) = createSystemEntity(entityCommunityAccess)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We GET /community/test_community without authentication")
        val communityGetRequest = (dynamicEntity_Request / "community" / "test_community").GET
        val communityGetResponse = makeGetRequest(communityGetRequest)
        Then("We should get a 401")
        communityGetResponse.code should equal(401)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }

    scenario("4.2: /community/ GET requires CanGet role", VersionOfApi) {
      val (code, body) = createSystemEntity(entityCommunityAccess)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We GET /community/test_community as user2 without CanGet role")
        val communityGetRequest = (dynamicEntity_Request / "community" / "test_community").GET <@(user2)
        val communityGetResponse = makeGetRequest(communityGetRequest)
        Then("We should get a 403")
        communityGetResponse.code should equal(403)

        When("We add CanGetDynamicEntity_Systemtest_community to user2")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanGetDynamicEntity_Systemtest_community")

        And("We GET again")
        val communityGetResponse2 = makeGetRequest(communityGetRequest)
        Then("We should get a 200")
        communityGetResponse2.code should equal(200)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }

    scenario("4.3: /community/ returns ALL records from all users", VersionOfApi) {
      val (code, body) = createSystemEntity(entityCommunityAccess)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("User1 creates a personal record via /my/test_community")
        val myCreateRequest1 = (dynamicEntity_Request / "my" / "test_community").POST <@(user1)
        val myCreateResponse1 = makePostRequest(myCreateRequest1, write(parse("""{"name": "User1 Personal"}""")))
        myCreateResponse1.code should equal(201)

        When("User2 creates a personal record via /my/test_community")
        val myCreateRequest2 = (dynamicEntity_Request / "my" / "test_community").POST <@(user2)
        val myCreateResponse2 = makePostRequest(myCreateRequest2, write(parse("""{"name": "User2 Personal"}""")))
        myCreateResponse2.code should equal(201)

        When("User1 creates a non-personal record via system endpoint")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanCreateDynamicEntity_Systemtest_community")
        val sysCreateRequest = (dynamicEntity_Request / "test_community").POST <@(user1)
        val sysCreateResponse = makePostRequest(sysCreateRequest, write(parse("""{"name": "System Record"}""")))
        sysCreateResponse.code should equal(201)

        When("We add CanGet role to user1 and GET /community/test_community")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanGetDynamicEntity_Systemtest_community")
        val communityGetRequest = (dynamicEntity_Request / "community" / "test_community").GET <@(user1)
        val communityGetResponse = makeGetRequest(communityGetRequest)

        Then("We should get a 200")
        communityGetResponse.code should equal(200)

        And("Response should contain at least 3 records (personal from user1, personal from user2, non-personal)")
        val recordList = (communityGetResponse.body \ "test_community_list").asInstanceOf[JArray].arr
        recordList.size should be >= 3
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }

    scenario("4.4: /community/ POST is not available (read-only)", VersionOfApi) {
      val (code, body) = createSystemEntity(entityCommunityAccess)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We POST to /community/test_community as user1")
        val postRequest = (dynamicEntity_Request / "community" / "test_community").POST <@(user1)
        val postResponse = makePostRequest(postRequest, write(testDataRecord))
        Then("We should get a 404 or 405 (endpoint not found)")
        postResponse.code should (equal(404) or equal(405))
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }
  }

  // ==================== Feature 5: has_personal_entity=false ====================

  feature("Feature 5: has_personal_entity=false") {

    scenario("5.1: /my/ endpoints return 404 when has_personal_entity=false", VersionOfApi) {
      val (code, body) = createSystemEntity(entityNoPersonal)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We POST to /my/test_no_personal as user1")
        val myPostRequest = (dynamicEntity_Request / "my" / "test_no_personal").POST <@(user1)
        val myPostResponse = makePostRequest(myPostRequest, write(testDataRecord))
        Then("We should get a 404 because has_personal_entity is false")
        myPostResponse.code should equal(404)

        When("We GET /my/test_no_personal as user1")
        val myGetRequest = (dynamicEntity_Request / "my" / "test_no_personal").GET <@(user1)
        val myGetResponse = makeGetRequest(myGetRequest)
        Then("We should get a 404")
        myGetResponse.code should equal(404)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }

    scenario("5.2: System-level non-personal CRUD still works", VersionOfApi) {
      val (code, body) = createSystemEntity(entityNoPersonal)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We add CanCreate role and POST to /test_no_personal as user1")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanCreateDynamicEntity_Systemtest_no_personal")
        val createRequest = (dynamicEntity_Request / "test_no_personal").POST <@(user1)
        val createResponse = makePostRequest(createRequest, write(testDataRecord))
        Then("We should get a 201")
        createResponse.code should equal(201)

        When("We add CanGet role and GET /test_no_personal as user1")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanGetDynamicEntity_Systemtest_no_personal")
        val getRequest = (dynamicEntity_Request / "test_no_personal").GET <@(user1)
        val getResponse = makeGetRequest(getRequest)
        Then("We should get a 200")
        getResponse.code should equal(200)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }
  }

  // ==================== Feature 6: personal_requires_role with has_personal_entity=false ====================

  feature("Feature 6: personal_requires_role has no effect when has_personal_entity=false") {

    scenario("6.1: /my/ returns 404 even with personal_requires_role=true when has_personal_entity=false", VersionOfApi) {
      val (code, body) = createSystemEntity(entityNoPersonalWithRole)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("We POST to /my/test_no_personal_role as user1")
        val myPostRequest = (dynamicEntity_Request / "my" / "test_no_personal_role").POST <@(user1)
        val myPostResponse = makePostRequest(myPostRequest, write(testDataRecord))
        Then("We should get a 404 because has_personal_entity is false (personal_requires_role has no effect)")
        myPostResponse.code should equal(404)

        When("Non-personal CRUD with roles works normally")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanCreateDynamicEntity_Systemtest_no_personal_role")
        val createRequest = (dynamicEntity_Request / "test_no_personal_role").POST <@(user1)
        val createResponse = makePostRequest(createRequest, write(testDataRecord))
        Then("We should get a 201")
        createResponse.code should equal(201)

        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanGetDynamicEntity_Systemtest_no_personal_role")
        val getRequest = (dynamicEntity_Request / "test_no_personal_role").GET <@(user1)
        val getResponse = makeGetRequest(getRequest)
        Then("We should get a 200")
        getResponse.code should equal(200)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }
  }

  // ==================== Feature 7: All flags enabled ====================

  feature("Feature 7: All flags enabled simultaneously") {

    scenario("7.1: All endpoint paths work simultaneously", VersionOfApi) {
      val (code, body) = createSystemEntity(entityAllFlags)
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("User1 creates a personal record (with CanCreate role, since personal_requires_role=true)")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanCreateDynamicEntity_Systemtest_all_flags")
        val myCreateRequest = (dynamicEntity_Request / "my" / "test_all_flags").POST <@(user1)
        val myCreateResponse = makePostRequest(myCreateRequest, write(testDataRecord))
        myCreateResponse.code should equal(201)

        When("User1 creates a non-personal record via system endpoint")
        val sysCreateRequest = (dynamicEntity_Request / "test_all_flags").POST <@(user1)
        val sysCreateResponse = makePostRequest(sysCreateRequest, write(parse("""{"name": "System Record"}""")))
        sysCreateResponse.code should equal(201)

        When("Public GET works without auth")
        val publicGetRequest = (dynamicEntity_Request / "public" / "test_all_flags").GET
        val publicGetResponse = makeGetRequest(publicGetRequest)
        Then("We should get a 200")
        publicGetResponse.code should equal(200)

        When("Community GET with auth + CanGet role returns ALL records")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanGetDynamicEntity_Systemtest_all_flags")
        val communityGetRequest = (dynamicEntity_Request / "community" / "test_all_flags").GET <@(user1)
        val communityGetResponse = makeGetRequest(communityGetRequest)
        Then("We should get a 200")
        communityGetResponse.code should equal(200)

        When("/my/ GET with auth + CanGet role returns user's own records")
        val myGetRequest = (dynamicEntity_Request / "my" / "test_all_flags").GET <@(user1)
        val myGetResponse = makeGetRequest(myGetRequest)
        Then("We should get a 200")
        myGetResponse.code should equal(200)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }
  }

}
