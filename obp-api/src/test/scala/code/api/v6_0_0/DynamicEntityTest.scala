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
import code.api.util.ApiRole
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import code.scope.Scope
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonDSL._
import org.json4s.native.Serialization.write
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._
import org.scalatest.Tag

class DynamicEntityTest extends V600ServerSetup {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.createSystemDynamicEntity))
  object ApiEndpoint2 extends Tag(nameOf(Implementations6_0_0.updateSystemDynamicEntity))
  object ApiEndpoint3 extends Tag(nameOf(Implementations6_0_0.getSystemDynamicEntities))
  object ApiEndpoint4 extends Tag(nameOf(Implementations6_0_0.createBankLevelDynamicEntity))
  object ApiEndpoint5 extends Tag(nameOf(Implementations6_0_0.updateBankLevelDynamicEntity))
  object ApiEndpoint6 extends Tag(nameOf(Implementations6_0_0.getBankLevelDynamicEntities))
  object ApiEndpoint7 extends Tag(nameOf(Implementations6_0_0.getMyDynamicEntities))
  object ApiEndpoint8 extends Tag(nameOf(Implementations6_0_0.updateMyDynamicEntity))
  object ApiEndpoint9 extends Tag(nameOf(Implementations6_0_0.getAvailablePersonalDynamicEntities))

  lazy val bankId = testBankId1.value

  // v6.0.0 request format with snake_case and explicit entity_name
  val rightEntityV600 = parse(
    """
      |{
      |    "entity_name": "foo_bar",
      |    "has_personal_entity": true,
      |    "schema": {
      |       "description": "description of this entity, can be markdown text.",
      |        "required": [
      |            "name"
      |        ],
      |        "properties": {
      |            "name": {
      |                "type": "string",
      |                "maxLength": 20,
      |                "minLength": 3,
      |                "example": "James Brown",
      |                "description":"description of **name** field, can be markdown text."
      |            },
      |            "number": {
      |                "type": "integer",
      |                "example": 69876172
      |            }
      |        }
      |    }
      |}
      |""".stripMargin)

  // Entity with hasPersonalEntity = false
  val entityWithoutPersonalV600 = parse(
    """
      |{
      |    "entity_name": "shared_entity",
      |    "has_personal_entity": false,
      |    "schema": {
      |       "description": "A shared entity without personal endpoints.",
      |        "required": [
      |            "title"
      |        ],
      |        "properties": {
      |            "title": {
      |                "type": "string",
      |                "example": "Some Title"
      |            }
      |        }
      |    }
      |}
      |""".stripMargin)

  // Wrong format - missing required field
  val wrongRequiredEntityV600 = parse(
    """
      |{
      |    "entity_name": "foo_bar",
      |    "has_personal_entity": true,
      |    "schema": {
      |       "description": "description of this entity.",
      |        "required": [
      |            "name_wrong"
      |        ],
      |        "properties": {
      |            "name": {
      |                "type": "string",
      |                "example": "James Brown"
      |            }
      |        }
      |    }
      |}
      |""".stripMargin)

  // Updated entity for PUT tests
  val updatedEntityV600 = parse(
    """
      |{
      |    "entity_name": "foo_bar",
      |    "has_personal_entity": true,
      |    "schema": {
      |       "description": "Updated description of this entity.",
      |        "required": [
      |            "name"
      |        ],
      |        "properties": {
      |            "name": {
      |                "type": "string",
      |                "maxLength": 30,
      |                "minLength": 2,
      |                "example": "Updated Name",
      |                "description":"Updated description of **name** field."
      |            },
      |            "number": {
      |                "type": "integer",
      |                "example": 12345678
      |            }
      |        }
      |    }
      |}
      |""".stripMargin)


  feature("v6.0.0 System Level Dynamic Entity endpoints with snake_case JSON") {

    scenario("Create System Dynamic Entity - without any credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a POST request without any credentials")
      val request = (v6_0_0_Request / "management" / "system-dynamic-entities").POST
      val response = makePostRequest(request, write(rightEntityV600))
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + ApplicationNotIdentified)
      response.body.extract[ErrorMessage].message should equal(ApplicationNotIdentified)
    }

    scenario("Create System Dynamic Entity - without proper role", ApiEndpoint1, VersionOfApi) {
      When(s"We make a POST request without the role " + CanCreateSystemLevelDynamicEntity)
      val request = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val response = makePostRequest(request, write(rightEntityV600))
      Then("We should get a 403")
      response.code should equal(403)
      And("error should contain " + UserHasMissingRoles)
      response.body.extract[ErrorMessage].message should include(UserHasMissingRoles)
    }

    scenario("Create System Dynamic Entity with consumer scope (no user entitlement)", ApiEndpoint1, VersionOfApi) {
      // Add scope to consumer instead of entitlement to user — UserOrApplication should accept this
      val addedScope = Scope.scope.vend.addScope("", testConsumer.id.get.toString, ApiRole.CanCreateSystemLevelDynamicEntity.toString)

      When("We create a dynamic entity using consumer with scope")
      val request = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val response = try {
        makePostRequest(request, write(rightEntityV600))
      } finally {
        Scope.scope.vend.deleteScope(addedScope)
      }

      Then("We should get a 201")
      response.code should equal(201)

      And("Response should have snake_case field: entity_name")
      (response.body \ "entity_name").extract[String] should equal("foo_bar")

      val dynamicEntityId = (response.body \ "dynamic_entity_id").extract[String]

      // Cleanup
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
      val deleteRequest = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1)
      makeDeleteRequest(deleteRequest)
    }

    scenario("Create and verify v6.0.0 snake_case response format", ApiEndpoint1, ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)

      When("We create a dynamic entity with v6.0.0 format")
      val request = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val response = makePostRequest(request, write(rightEntityV600))

      Then("We should get a 201")
      response.code should equal(201)

      val responseJson = response.body

      // Verify snake_case field names exist
      And("Response should have snake_case field: dynamic_entity_id")
      (responseJson \ "dynamic_entity_id") shouldBe a[JString]

      And("Response should have snake_case field: entity_name")
      (responseJson \ "entity_name").extract[String] should equal("foo_bar")

      And("Response should have snake_case field: user_id")
      (responseJson \ "user_id").extract[String] should equal(resourceUser1.userId)

      And("Response should have snake_case field: has_personal_entity")
      (responseJson \ "has_personal_entity").extract[Boolean] should equal(true)

      And("Response should have schema field with just the schema (no entity name wrapper)")
      val schemaField = responseJson \ "schema"
      (schemaField \ "description") shouldBe a[JString]
      (schemaField \ "required") shouldBe a[JArray]
      (schemaField \ "properties") shouldBe a[JObject]

      // Verify schema does NOT contain the entity name as a key (old format would have foo_bar as key)
      And("Schema should NOT contain entity name as a dynamic key")
      (schemaField \ "foo_bar") should equal(JNothing)

      val dynamicEntityId = (responseJson \ "dynamic_entity_id").extract[String]

      // Now test GET to verify the response format is consistent
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemLevelDynamicEntities.toString)

      When("We GET system dynamic entities")
      val getRequest = (v6_0_0_Request / "management" / "system-dynamic-entities").GET <@(user1)
      val getResponse = makeGetRequest(getRequest)

      Then("We should get a 200")
      getResponse.code should equal(200)

      val entitiesJson = getResponse.body \ "dynamic_entities"
      entitiesJson shouldBe a[JArray]

      val entities = entitiesJson.asInstanceOf[JArray].arr
      entities should have size 1

      val entity = entities.head
      And("GET response should also use snake_case fields")
      (entity \ "dynamic_entity_id").extract[String] should equal(dynamicEntityId)
      (entity \ "entity_name").extract[String] should equal("foo_bar")
      (entity \ "has_personal_entity").extract[Boolean] should equal(true)

      And("GET response should include record_count field")
      (entity \ "record_count") shouldBe a[JInt]

      // Cleanup
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
      val deleteRequest = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1)
      makeDeleteRequest(deleteRequest)
    }

    scenario("Update System Dynamic Entity with v6.0.0 format", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateSystemLevelDynamicEntity.toString)

      // Create first
      val createRequest = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val createResponse = makePostRequest(createRequest, write(rightEntityV600))
      createResponse.code should equal(201)

      val dynamicEntityId = (createResponse.body \ "dynamic_entity_id").extract[String]

      When("We update the dynamic entity with v6.0.0 format")
      val updateRequest = (v6_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).PUT <@(user1)
      val updateResponse = makePutRequest(updateRequest, write(updatedEntityV600))

      Then("We should get a 200")
      updateResponse.code should equal(200)

      val responseJson = updateResponse.body

      And("Updated response should use snake_case fields")
      (responseJson \ "dynamic_entity_id").extract[String] should equal(dynamicEntityId)
      (responseJson \ "entity_name").extract[String] should equal("foo_bar")

      And("Schema should be updated")
      val schemaField = responseJson \ "schema"
      (schemaField \ "description").extract[String] should equal("Updated description of this entity.")

      // Cleanup
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
      val deleteRequest = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1)
      makeDeleteRequest(deleteRequest)
    }

    scenario("Create Dynamic Entity with invalid schema should fail", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)

      When("We try to create a dynamic entity with wrong required field")
      val request = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val response = makePostRequest(request, write(wrongRequiredEntityV600))

      Then("We should get a 400")
      response.code should equal(400)

      And("Error message should indicate validation failure")
      response.body.extract[ErrorMessage].message should include(DynamicEntityInstanceValidateFail)
    }
  }


  feature("v6.0.0 Bank Level Dynamic Entity endpoints with snake_case JSON") {

    scenario("Create Bank Level Dynamic Entity - without proper role", ApiEndpoint4, VersionOfApi) {
      When(s"We make a POST request without the role " + CanCreateBankLevelDynamicEntity)
      val request = (v6_0_0_Request / "management" / "banks" / bankId / "dynamic-entities").POST <@(user1)
      val response = makePostRequest(request, write(rightEntityV600))
      Then("We should get a 403")
      response.code should equal(403)
    }

    scenario("Create and GET Bank Level Dynamic Entity with v6.0.0 format", ApiEndpoint4, ApiEndpoint6, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)

      When("We create a bank level dynamic entity with v6.0.0 format")
      val request = (v6_0_0_Request / "management" / "banks" / bankId / "dynamic-entities").POST <@(user1)
      val response = makePostRequest(request, write(rightEntityV600))

      Then("We should get a 201")
      response.code should equal(201)

      val responseJson = response.body

      And("Response should have snake_case field: bank_id")
      (responseJson \ "bank_id").extract[String] should equal(bankId)

      And("Response should have entity_name")
      (responseJson \ "entity_name").extract[String] should equal("foo_bar")

      val dynamicEntityId = (responseJson \ "dynamic_entity_id").extract[String]

      // Test GET bank level
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetBankLevelDynamicEntities.toString)

      When("We GET bank level dynamic entities")
      val getRequest = (v6_0_0_Request / "management" / "banks" / bankId / "dynamic-entities").GET <@(user1)
      val getResponse = makeGetRequest(getRequest)

      Then("We should get a 200")
      getResponse.code should equal(200)

      val entities = (getResponse.body \ "dynamic_entities").asInstanceOf[JArray].arr
      entities should have size 1

      val entity = entities.head
      (entity \ "bank_id").extract[String] should equal(bankId)
      (entity \ "entity_name").extract[String] should equal("foo_bar")
      (entity \ "record_count") shouldBe a[JInt]

      // Cleanup
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanDeleteBankLevelDynamicEntity.toString)
      val deleteRequest = (v4_0_0_Request / "management" / "banks" / bankId / "dynamic-entities" / dynamicEntityId).DELETE <@(user1)
      makeDeleteRequest(deleteRequest)
    }

    scenario("Update Bank Level Dynamic Entity with v6.0.0 format", ApiEndpoint4, ApiEndpoint5, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanUpdateBankLevelDynamicEntity.toString)

      // Create first
      val createRequest = (v6_0_0_Request / "management" / "banks" / bankId / "dynamic-entities").POST <@(user1)
      val createResponse = makePostRequest(createRequest, write(rightEntityV600))
      createResponse.code should equal(201)

      val dynamicEntityId = (createResponse.body \ "dynamic_entity_id").extract[String]

      When("We update the bank level dynamic entity")
      val updateRequest = (v6_0_0_Request / "management" / "banks" / bankId / "dynamic-entities" / dynamicEntityId).PUT <@(user1)
      val updateResponse = makePutRequest(updateRequest, write(updatedEntityV600))

      Then("We should get a 200")
      updateResponse.code should equal(200)

      And("Updated response should have snake_case fields")
      (updateResponse.body \ "entity_name").extract[String] should equal("foo_bar")
      (updateResponse.body \ "bank_id").extract[String] should equal(bankId)

      // Cleanup
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanDeleteBankLevelDynamicEntity.toString)
      val deleteRequest = (v4_0_0_Request / "management" / "banks" / bankId / "dynamic-entities" / dynamicEntityId).DELETE <@(user1)
      makeDeleteRequest(deleteRequest)
    }
  }


  feature("v6.0.0 My Dynamic Entities endpoints") {

    scenario("GET My Dynamic Entities - without user credentials", ApiEndpoint7, VersionOfApi) {
      When("We make a GET request without user credentials")
      val request = (v6_0_0_Request / "my" / "dynamic-entities").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
    }

    scenario("GET and Update My Dynamic Entities with v6.0.0 format", ApiEndpoint7, ApiEndpoint8, VersionOfApi) {
      // First create a system entity with hasPersonalEntity = true
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)

      val createRequest = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val createResponse = makePostRequest(createRequest, write(rightEntityV600))
      createResponse.code should equal(201)

      val dynamicEntityId = (createResponse.body \ "dynamic_entity_id").extract[String]

      When("We GET my dynamic entities")
      val getRequest = (v6_0_0_Request / "my" / "dynamic-entities").GET <@(user1)
      val getResponse = makeGetRequest(getRequest)

      Then("We should get a 200")
      getResponse.code should equal(200)

      val entitiesJson = getResponse.body \ "dynamic_entities"
      entitiesJson shouldBe a[JArray]

      val entities = entitiesJson.asInstanceOf[JArray].arr
      entities.size should be >= 1

      And("Response should use snake_case fields")
      val entity = entities.find(e => (e \ "entity_name").extract[String] == "foo_bar").get
      (entity \ "dynamic_entity_id") shouldBe a[JString]
      (entity \ "entity_name").extract[String] should equal("foo_bar")
      (entity \ "user_id").extract[String] should equal(resourceUser1.userId)
      (entity \ "has_personal_entity").extract[Boolean] should equal(true)

      And("Schema field should contain only the schema structure")
      val schemaField = entity \ "schema"
      (schemaField \ "description") shouldBe a[JString]
      (schemaField \ "foo_bar") should equal(JNothing)  // Should NOT have entity name as key

      // Test Update My Dynamic Entity
      When("We update my dynamic entity")
      val updateRequest = (v6_0_0_Request / "my" / "dynamic-entities" / dynamicEntityId).PUT <@(user1)
      val updateResponse = makePutRequest(updateRequest, write(updatedEntityV600))

      Then("We should get a 200")
      updateResponse.code should equal(200)

      And("Updated response should use snake_case fields")
      (updateResponse.body \ "entity_name").extract[String] should equal("foo_bar")
      (updateResponse.body \ "schema" \ "description").extract[String] should equal("Updated description of this entity.")

      // Cleanup
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
      val deleteRequest = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1)
      makeDeleteRequest(deleteRequest)
    }
  }


  feature("v6.0.0 Available Personal Dynamic Entities discovery endpoint") {

    scenario("GET Available Personal Dynamic Entities - without user credentials", ApiEndpoint9, VersionOfApi) {
      When("We make a GET request without user credentials")
      val request = (v6_0_0_Request / "personal-dynamic-entities" / "available").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
    }

    scenario("GET Available Personal Dynamic Entities returns only entities with hasPersonalEntity=true", ApiEndpoint9, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)

      // Create entity WITH hasPersonalEntity = true
      val createRequest1 = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val response1 = makePostRequest(createRequest1, write(rightEntityV600))
      response1.code should equal(201)
      val entityId1 = (response1.body \ "dynamic_entity_id").extract[String]

      // Create entity WITH hasPersonalEntity = false
      val createRequest2 = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val response2 = makePostRequest(createRequest2, write(entityWithoutPersonalV600))
      response2.code should equal(201)
      val entityId2 = (response2.body \ "dynamic_entity_id").extract[String]

      When("We GET available personal dynamic entities")
      val getRequest = (v6_0_0_Request / "personal-dynamic-entities" / "available").GET <@(user1)
      val getResponse = makeGetRequest(getRequest)

      Then("We should get a 200")
      getResponse.code should equal(200)

      val entities = (getResponse.body \ "dynamic_entities").asInstanceOf[JArray].arr

      And("Response should contain only entities with has_personal_entity = true")
      val entityNames = entities.map(e => (e \ "entity_name").extract[String])
      entityNames should contain("foo_bar")
      entityNames should not contain("shared_entity")

      And("All returned entities should have has_personal_entity = true")
      entities.foreach { entity =>
        (entity \ "has_personal_entity").extract[Boolean] should equal(true)
      }

      And("Response should use snake_case fields")
      entities.foreach { entity =>
        (entity \ "dynamic_entity_id") shouldBe a[JString]
        (entity \ "entity_name") shouldBe a[JString]
        (entity \ "schema") shouldBe a[JObject]
      }

      // Cleanup
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
      val deleteRequest1 = (v4_0_0_Request / "management" / "system-dynamic-entities" / entityId1).DELETE <@(user1)
      makeDeleteRequest(deleteRequest1)
      val deleteRequest2 = (v4_0_0_Request / "management" / "system-dynamic-entities" / entityId2).DELETE <@(user1)
      makeDeleteRequest(deleteRequest2)
    }
  }


  feature("v6.0.0 Dynamic Entity schema field validation") {

    scenario("Verify schema contains only schema structure, not entity name wrapper", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)

      val createRequest = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val createResponse = makePostRequest(createRequest, write(rightEntityV600))
      createResponse.code should equal(201)

      val dynamicEntityId = (createResponse.body \ "dynamic_entity_id").extract[String]
      val schemaField = createResponse.body \ "schema"

      Then("Schema should contain schema fields directly")
      (schemaField \ "description") shouldBe a[JString]
      (schemaField \ "required") shouldBe a[JArray]
      (schemaField \ "properties") shouldBe a[JObject]

      And("Schema should NOT contain the entity name as a nested key (old v4.0.0 format)")
      (schemaField \ "foo_bar") should equal(JNothing)

      And("Schema should NOT contain hasPersonalEntity (that's a separate top-level field)")
      (schemaField \ "hasPersonalEntity") should equal(JNothing)
      (schemaField \ "has_personal_entity") should equal(JNothing)

      // Cleanup
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
      val deleteRequest = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1)
      makeDeleteRequest(deleteRequest)
    }
  }


  feature("v6.0.0 Dynamic Entity _links match resource doc URLs") {

    scenario("_links URLs for personal/public/community must match resource doc URLs", ApiEndpoint1, ApiEndpoint9, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)

      // Create entity with all access flags enabled
      val allFlagsEntity = parse(
        """
          |{
          |    "entity_name": "links_test",
          |    "has_personal_entity": true,
          |    "has_public_access": true,
          |    "has_community_access": true,
          |    "schema": {
          |        "description": "Entity to test _links correctness.",
          |        "required": ["name"],
          |        "properties": {
          |            "name": {
          |                "type": "string",
          |                "example": "Test"
          |            }
          |        }
          |    }
          |}
        """.stripMargin)

      val createRequest = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val createResponse = makePostRequest(createRequest, write(allFlagsEntity))
      createResponse.code should equal(201)

      val dynamicEntityId = (createResponse.body \ "dynamic_entity_id").extract[String]

      When("We GET available personal dynamic entities")
      val getRequest = (v6_0_0_Request / "personal-dynamic-entities" / "available").GET <@(user1)
      val getResponse = makeGetRequest(getRequest)
      getResponse.code should equal(200)

      val entities = (getResponse.body \ "dynamic_entities").asInstanceOf[JArray].arr
      val linksTestEntity = entities.find(e => (e \ "entity_name").extract[String] == "links_test")
      linksTestEntity should not be empty

      val linksJson = linksTestEntity.get \ "_links" \ "related"
      linksJson shouldBe a[JArray]
      val links = linksJson.asInstanceOf[JArray].arr

      Then("_links should contain personal, public, and community links")
      val linkMap = links.map { link =>
        val rel = (link \ "rel").extract[String]
        val href = (link \ "href").extract[String]
        val method = (link \ "method").extract[String]
        (rel, href, method)
      }

      And("_links URLs should use the dynamic-entity API version prefix")
      val dynamicEntityPrefix = s"/obp/${ApiVersion.`dynamic-entity`}"
      linkMap.foreach { case (_, href, _) =>
        href should startWith(dynamicEntityPrefix)
      }

      And("_links should match the resource doc URLs for this entity")
      import code.api.dynamic.entity.helper.DynamicEntityHelper
      val resourceDocs = DynamicEntityHelper.operationToResourceDoc

      // Build expected URLs from resource docs
      val entityName = "links_test"
      import com.openbankproject.commons.model.enums.DynamicEntityOperation._

      // Personal (My) resource doc URLs
      val myGetAll = resourceDocs.get((GET_ALL, s"My$entityName")).map(rd => (s"$dynamicEntityPrefix${rd.requestUrl}", rd.requestVerb))
      val myCreate = resourceDocs.get((CREATE, s"My$entityName")).map(rd => (s"$dynamicEntityPrefix${rd.requestUrl}", rd.requestVerb))
      val myGetOne = resourceDocs.get((GET_ONE, s"My$entityName")).map(rd => (s"$dynamicEntityPrefix${rd.requestUrl}", rd.requestVerb))
      val myUpdate = resourceDocs.get((UPDATE, s"My$entityName")).map(rd => (s"$dynamicEntityPrefix${rd.requestUrl}", rd.requestVerb))
      val myDelete = resourceDocs.get((DELETE, s"My$entityName")).map(rd => (s"$dynamicEntityPrefix${rd.requestUrl}", rd.requestVerb))

      // Public resource doc URLs
      val publicGetAll = resourceDocs.get((GET_ALL, s"Public$entityName")).map(rd => (s"$dynamicEntityPrefix${rd.requestUrl}", rd.requestVerb))
      val publicGetOne = resourceDocs.get((GET_ONE, s"Public$entityName")).map(rd => (s"$dynamicEntityPrefix${rd.requestUrl}", rd.requestVerb))

      // Community resource doc URLs
      val communityGetAll = resourceDocs.get((GET_ALL, s"Community$entityName")).map(rd => (s"$dynamicEntityPrefix${rd.requestUrl}", rd.requestVerb))
      val communityGetOne = resourceDocs.get((GET_ONE, s"Community$entityName")).map(rd => (s"$dynamicEntityPrefix${rd.requestUrl}", rd.requestVerb))

      // Verify personal links match resource docs
      myGetAll should not be empty
      linkMap should contain(("personal-list", myGetAll.get._1, myGetAll.get._2))
      linkMap should contain(("personal-create", myCreate.get._1, myCreate.get._2))
      linkMap should contain(("personal-read", myGetOne.get._1, myGetOne.get._2))
      linkMap should contain(("personal-update", myUpdate.get._1, myUpdate.get._2))
      linkMap should contain(("personal-delete", myDelete.get._1, myDelete.get._2))

      // Verify public links match resource docs
      publicGetAll should not be empty
      linkMap should contain(("public-list", publicGetAll.get._1, publicGetAll.get._2))
      linkMap should contain(("public-read", publicGetOne.get._1, publicGetOne.get._2))

      // Verify community links match resource docs
      communityGetAll should not be empty
      linkMap should contain(("community-list", communityGetAll.get._1, communityGetAll.get._2))
      linkMap should contain(("community-read", communityGetOne.get._1, communityGetOne.get._2))

      // Cleanup
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
      val deleteRequest = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1)
      makeDeleteRequest(deleteRequest)
    }
  }

}
