/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
package code.api.v4_0_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Serialization.write
import net.liftweb.json._
import org.scalatest.Tag
class DynamicEntityTest extends V400ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createDynamicEntity))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateDynamicEntity))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getDynamicEntities))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.deleteDynamicEntity))

  val rightEntity = parse(
    """
      |{
      |    "FooBar": {
      |       "description": "description of this entity, can be markdown text.",
      |        "required": [
      |            "name"
      |        ],
      |        "properties": {
      |            "name": {
      |                "type": "string",
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
  // wrong required name
  val wrongRequiredEntity = parse(
    """
      |{
      |    "FooBar": {
      |       "description": "description of this entity, can be markdown text.",
      |        "required": [
      |            "name_wrong"
      |        ],
      |        "properties": {
      |            "name": {
      |                "type": "string",
      |                "example": "James Brown",
      |                "description":"description of **name** field, can be markdown text."
      |            },
      |            "number": {
      |                "type": "integer",
      |                "example": 69876172,
      |                "description": "description of **number** field, can be markdown text."
      |            }
      |        }
      |    }
      |}
      |""".stripMargin)

  // wrong description value type
  val wrongDescriptionEntity = parse(
    """
      |{
      |    "FooBar": {
      |       "description": 1,
      |        "required": [
      |            "name_wrong"
      |        ],
      |        "properties": {
      |            "name": {
      |                "type": "string",
      |                "example": "James Brown",
      |                "description":"description of **name** field, can be markdown text."
      |            },
      |            "number": {
      |                "type": "integer",
      |                "example": 69876172,
      |                "description": "description of **number** field, can be markdown text."
      |            }
      |        }
      |    }
      |}
      |""".stripMargin)
  // wrong property description value type
  val wrongPropertyDescriptionEntity = parse(
    """
      |{
      |    "FooBar": {
      |       "description": 1,
      |        "required": [
      |            "name_wrong"
      |        ],
      |        "properties": {
      |            "name": {
      |                "type": "string",
      |                "example": "James Brown",
      |                "description":"description of **name** field, can be markdown text."
      |            },
      |            "number": {
      |                "type": "integer",
      |                "example": 69876172,
      |                "description": true
      |            }
      |        }
      |    }
      |}
      |""".stripMargin)


  feature("Add a DynamicEntity v4.0.4- Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "dynamic-entities").POST
      val response400 = makePostRequest(request400, write(rightEntity))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Update a DynamicEntity v4.0.4- Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "dynamic-entities"/ "some-method-routing-id").PUT
      val response400 = makePutRequest(request400, write(rightEntity))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Get DynamicEntities v4.0.4- Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "dynamic-entities").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Delete the DynamicEntity specified by METHOD_ROUTING_ID v4.0.4- Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "dynamic-entities" / "METHOD_ROUTING_ID").DELETE
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }


  feature("Add a DynamicEntity v4.0.4- Unauthorized access - Authorized access") {
    scenario("We will call the endpoint without the proper Role " + canCreateDynamicEntity, ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0 without a Role " + canCreateDynamicEntity)
      val request400 = (v4_0_0_Request / "management" / "dynamic-entities").POST <@(user1)
      val response400 = makePostRequest(request400, write(rightEntity))
      Then("We should get a 403")
      response400.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateDynamicEntity)
      response400.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanCreateDynamicEntity)
    }

    scenario("We will call the endpoint with the proper Role " + canCreateDynamicEntity , ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateDynamicEntity.toString)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-entities").POST <@(user1)
      val response = makePostRequest(request, write(rightEntity))
      Then("We should get a 201")
      response.code should equal(201)

      { // create duplicated entityName FooBar, cause 400
        val response400 = makePostRequest(request, write(rightEntity))
        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (DynamicEntityNameAlreadyExists)
      }

      val responseJson = response.body
      val dynamicEntityId = (responseJson \ "dynamicEntityId").asInstanceOf[JString].s
      val dynamicEntityIdJObject: JObject = "dynamicEntityId" -> dynamicEntityId

      val expectCreateResponseJson: JValue = rightEntity merge dynamicEntityIdJObject

      val newNameValue: JObject =
        "FooBar" -> (
          "properties" ->
            ("name" -> (
              "example" -> "hello")
              )
          )

      val updateRequest: JValue = rightEntity merge newNameValue
      val expectUpdatedResponseJson: JValue = expectCreateResponseJson merge newNameValue

      responseJson shouldEqual expectCreateResponseJson

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateDynamicEntity.toString)
      When("We make a request v4.0.0 with the Role " + canUpdateDynamicEntity)

      {
        // update success
        val request400 = (v4_0_0_Request / "management" / "dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(updateRequest))
        Then("We should get a 200")
        response400.code should equal(200)
        val updateResponseJson = response400.body
        updateResponseJson shouldEqual expectUpdatedResponseJson
      }

      {
        // update a not exists DynamicEntity
        val request404 = (v4_0_0_Request / "management" / "dynamic-entities" / "not-exists-id" ).PUT <@(user1)
        val response404 = makePutRequest(request404, compactRender(updateRequest))
        Then("We should get a 404")
        response404.code should equal(404)
        response404.body.extract[ErrorMessage].message should startWith (DynamicEntityNotFoundByDynamicEntityId)
      }

      {
        // update a DynamicEntity with wrong required field name
        val request400 = (v4_0_0_Request / "management" / "dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(wrongRequiredEntity))
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (DynamicEntityInstanceValidateFail)
      }

      {
        // update a DynamicEntity with wrong type of description
        val request400 = (v4_0_0_Request / "management" / "dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(wrongDescriptionEntity))
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (DynamicEntityInstanceValidateFail)
      }

      {
        // update a DynamicEntity with wrong type of property description
        val request400 = (v4_0_0_Request / "management" / "dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(wrongPropertyDescriptionEntity))
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (DynamicEntityInstanceValidateFail)
      }

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetDynamicEntities.toString)
      When("We make a request v4.0.0 with the Role " + canGetDynamicEntities)
      val requestGet = (v4_0_0_Request / "management" / "dynamic-entities").GET <@(user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
      val json = responseGet.body \ "dynamic_entities"
      val dynamicEntitiesGetJson = json.asInstanceOf[JArray]

      dynamicEntitiesGetJson.values should have size 1

      val JArray(head :: Nil) = dynamicEntitiesGetJson

      head should equal(expectUpdatedResponseJson)

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteDynamicEntity.toString)
      When("We make a request v4.0.0 with the Role " + canDeleteDynamicEntity)
      val requestDelete400 = (v4_0_0_Request / "management" / "dynamic-entities" / dynamicEntityId).DELETE <@(user1)
      val responseDelete400 = makeDeleteRequest(requestDelete400)
      Then("We should get a 200")
      responseDelete400.code should equal(200)

    }
  }


}
