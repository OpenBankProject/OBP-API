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
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getMyDynamicEntities))
  object ApiEndpoint6 extends Tag(nameOf(Implementations4_0_0.updateMyDynamicEntity))
  object ApiEndpoint7 extends Tag(nameOf(Implementations4_0_0.deleteMyDynamicEntity))
  object ApiEndpoint8 extends Tag(nameOf(Implementations4_0_0.getBankLevelDynamicEntities))

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

    scenario("We will call the endpoint with the proper Role " + canCreateDynamicEntity , ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint8, VersionOfApi) {
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
      val dynamicEntityUserIdJObject: JObject = "userId" -> resourceUser1.userId
      val dynamicEntityIdJObject: JObject = "dynamicEntityId" -> dynamicEntityId

      val expectCreateResponseJson: JValue = rightEntity merge dynamicEntityUserIdJObject merge dynamicEntityIdJObject

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
      Then("We should get a 204")
      responseDelete400.code should equal(204)

    }
  }

  feature("Add a DynamicEntity v4.0.4- and test all the myDynamicEntity endpoints") {
    scenario("We will call the endpoint with the proper Role " + canCreateDynamicEntity , ApiEndpoint1, ApiEndpoint5, ApiEndpoint6, ApiEndpoint7, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateDynamicEntity.toString)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-entities").POST <@(user1)
      val response = makePostRequest(request, write(rightEntity))
      Then("We should get a 201")
      response.code should equal(201)

      val responseJson = response.body
      val dynamicEntityId = (responseJson \ "dynamicEntityId").asInstanceOf[JString].s

      val dynamicEntityUserIdJObject: JObject = "userId" -> resourceUser1.userId
      val dynamicEntityIdJObject: JObject = "dynamicEntityId" -> dynamicEntityId

      val expectCreateResponseJson: JValue = rightEntity merge dynamicEntityUserIdJObject merge dynamicEntityIdJObject

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

      When(s"We make a $ApiEndpoint6" )

      {
        // update success
        val request400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(updateRequest))
        Then("We should get a 200")
        response400.code should equal(200)
        val updateResponseJson = response400.body
        updateResponseJson shouldEqual expectUpdatedResponseJson
      }

      {
        // update a not exists DynamicEntity
        val request404 = (v4_0_0_Request / "my" / "dynamic-entities" / "not-exists-id" ).PUT <@(user1)
        val response404 = makePutRequest(request404, compactRender(updateRequest))
        Then("We should get a 404")
        response404.code should equal(404)
        response404.body.extract[ErrorMessage].message should startWith (DynamicEntityNotFoundByDynamicEntityId)
      }

      {
        // update a DynamicEntity with wrong required field name
        val request400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(wrongRequiredEntity))
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (DynamicEntityInstanceValidateFail)
      }

      {
        // update a DynamicEntity with wrong type of description
        val request400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(wrongDescriptionEntity))
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (DynamicEntityInstanceValidateFail)
      }

      {
        // update a DynamicEntity with wrong type of property description
        val request400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(wrongPropertyDescriptionEntity))
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (DynamicEntityInstanceValidateFail)
      }

      {
        // update a DynamicEntity with wrong user
        val request400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityId ).PUT <@(user2)
        val response400 = makePutRequest(request400, compactRender(updateRequest))
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (InvalidMyDynamicEntityUser)
      }

      When(s"We make a $ApiEndpoint5 request" )
      val requestGet = (v4_0_0_Request / "my" / "dynamic-entities").GET <@(user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
      val json = responseGet.body \ "dynamic_entities"
      val dynamicEntitiesGetJson = json.asInstanceOf[JArray]

      dynamicEntitiesGetJson.values should have size 1

      val JArray(head :: Nil) = dynamicEntitiesGetJson

      head should equal(expectUpdatedResponseJson)

      {
        // get a DynamicEntity with wrong user
        val request400 = (v4_0_0_Request / "my" / "dynamic-entities"  ).GET <@(user2)
        val response400 = makeGetRequest(request400)
        Then("We should get a 200")

        val json = response400.body \ "dynamic_entities"
        val dynamicEntitiesGetJson = json.asInstanceOf[JArray]

        dynamicEntitiesGetJson.values should have size 0
      }

      When(s"We make a $ApiEndpoint7 request" )

      {
        // delete a MyDynamicEntity with wrong user
        val request400 = (v4_0_0_Request / "my" / "dynamic-entities"/ dynamicEntityId ).DELETE <@(user2)
        val response400 = makeDeleteRequest(request400)
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (InvalidMyDynamicEntityUser)
      }
      {
        // delete a MyDynamicEntity 
        val requestDelete400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityId).DELETE <@(user1)
        val responseDelete400 = makeDeleteRequest(requestDelete400)
        Then("We should get a 200")
        responseDelete400.code should equal(200)
      }

    }
  }

  feature("Add a DynamicEntity v4.0.4- and test all the getBankLevelDynamicEntities endpoints") {
    scenario("We will call the endpoint with the proper Role " + canCreateDynamicEntity , ApiEndpoint1, ApiEndpoint5, ApiEndpoint6, ApiEndpoint8, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateDynamicEntity.toString)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-entities").POST <@(user1)
      val entityWithBankId = parse(
        s"""
          |{
          |    "bankId": "${testBankId1.value}",
          |    "FooBar": {
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

      val response = makePostRequest(request, write(entityWithBankId))
      Then("We should get a 201")
      response.code should equal(201)

      val responseJson = response.body
      val dynamicEntityId = (responseJson \ "dynamicEntityId").asInstanceOf[JString].s
      val dynamicBankId = (responseJson \ "bankId").asInstanceOf[JString].s

      val dynamicEntityUserIdJObject: JObject = "userId" -> resourceUser1.userId
      val dynamicEntityIdJObject: JObject = "dynamicEntityId" -> dynamicEntityId

      val expectCreateResponseJson: JValue = entityWithBankId merge dynamicEntityUserIdJObject merge dynamicEntityIdJObject


      responseJson shouldEqual expectCreateResponseJson

      When(s"We make a $ApiEndpoint8 request without the role" )
      val requestGet = (v4_0_0_Request /"management" / "banks" /testBankId1.value/ "dynamic-entities").GET <@(user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 403")
      responseGet.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetBankLevelDynamicEntities)
      responseGet.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetBankLevelDynamicEntities)

      {
        Then("We grant the role and call it again")
        Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanGetBankLevelDynamicEntities.toString)
        val requestGet = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities").GET <@ (user1)
        val responseGet = makeGetRequest(requestGet)
        responseGet.code should equal(200)
        val json = responseGet.body \ "dynamic_entities"
        val dynamicEntitiesGetJson = json.asInstanceOf[JArray]

        dynamicEntitiesGetJson.values should have size 1
      }

      {
        // we try the different bank id.

        val requestGet = (v4_0_0_Request /"management" / "banks" /testBankId2.value/ "dynamic-entities").GET <@(user1)
        val responseGet = makeGetRequest(requestGet)
        Then("We should get a 403")
        responseGet.code should equal(403)
        And("error should be " + UserHasMissingRoles + CanGetBankLevelDynamicEntities)
        responseGet.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetBankLevelDynamicEntities)

        {
          Entitlement.entitlement.vend.addEntitlement(testBankId2.value, resourceUser1.userId, CanGetBankLevelDynamicEntities.toString)
          val responseGet = makeGetRequest(requestGet)
          Then("We should get a 200")
          responseGet.code should equal(200)
          val json = responseGet.body \ "dynamic_entities"
          val dynamicEntitiesGetJson = json.asInstanceOf[JArray]

          dynamicEntitiesGetJson.values should have size 0
        }

      }

    }
  }

  feature("Add a DynamicEntity v4.0.4- and test all the Foobar endpoints and Foobar Roles") {
    scenario("We will call the endpoint with the proper Role " + canCreateDynamicEntity , ApiEndpoint1, ApiEndpoint5, ApiEndpoint6, ApiEndpoint8, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateDynamicEntity.toString)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-entities").POST <@(user1)

      val entityWithBankId = parse(
        s"""
           |{
           |    "bankId": "${testBankId1.value}",
           |    "FooBar": {
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

      val notBankIdEntity = parse(
        s"""
           |{
           |    "FooBar1": {
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

      val foobarObject = parse("""{  "name":"James Brown",  "number":698761728}""".stripMargin)
      val foobarUpdateObject = parse("""{  "name":"James Brown123",  "number":698761728}""".stripMargin)

      val responseWithBankId = makePostRequest(request, write(entityWithBankId))
      Then("We should get a 201")
      responseWithBankId.code should equal(201)

      val responseNoBankId = makePostRequest(request, write(notBankIdEntity))
      Then("We should get a 201")
      responseNoBankId.code should equal(201)

      {
        Then("we can insert the new FooBar data - BankLevel")
        val requestCreateFoobar = (v4_0_0_Request / "banks"/ testBankId1.value / "FooBar").POST <@(user1)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
        val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s
        val dynamicBankId = (responseCreateFoobar.body \ "bank_id").asInstanceOf[JString].s
        dynamicBankId should equal(testBankId1.value)

        val requestGetFoobars = (v4_0_0_Request / "banks"/ testBankId1.value / "FooBar").GET <@(user1)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)
        val dynamicBankIdGetFoobars = (responseGetFoobars.body \ "bank_id").asInstanceOf[JString].s
        dynamicBankIdGetFoobars should equal(testBankId1.value)

        val requestGetFoobar = (v4_0_0_Request / "banks"/ testBankId1.value / "FooBar" / dynamicEntityId ).GET <@(user1)
        val responseGetFoobar = makeGetRequest(requestGetFoobar)
        responseGetFoobar.code should equal(200)
        val dynamicBankIdGetFoobar = (responseGetFoobar.body \ "bank_id").asInstanceOf[JString].s
        dynamicBankIdGetFoobar should equal(testBankId1.value)

        val requestUpdateFoobar = (v4_0_0_Request / "banks"/ testBankId1.value / "FooBar" / dynamicEntityId).PUT <@(user1)
        val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
        responseUpdateFoobar.code should equal(200)
        val responseUpdateFoobarName = (responseUpdateFoobar.body \ "foo_bar" \ "name").asInstanceOf[JString].s
        responseUpdateFoobarName should equal("James Brown123")

        val requestDeleteFoobar = (v4_0_0_Request / "banks"/ testBankId1.value / "FooBar" / dynamicEntityId ).DELETE <@(user1)
        val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
        responseDeleteFoobar.code should equal(204)

        When("When other user call the foobar endpoints, it need some roles")
        val requestCreateFoobarUser2 = (v4_0_0_Request / "banks"/ testBankId1.value / "FooBar").POST <@(user2)
        val responseCreateFoobarUser2 = makePostRequest(requestCreateFoobarUser2, write(foobarObject))
        responseCreateFoobarUser2.code should equal(403)
        And("error should be " + UserHasMissingRoles + CanGetDynamicEntities)
        responseCreateFoobarUser2.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
      }

      {
        Then("we can insert the new FooBar data - BankLevel")
        val requestCreateFoobar = (v4_0_0_Request / "FooBar1").POST <@(user1)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
        val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar1" \ "foo_bar1_id").asInstanceOf[JString].s

        val requestGetFoobars = (v4_0_0_Request / "FooBar1").GET <@(user1)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)

        val requestGetFoobar = (v4_0_0_Request / "FooBar1" / dynamicEntityId ).GET <@(user1)
        val responseGetFoobar = makeGetRequest(requestGetFoobar)
        responseGetFoobar.code should equal(200)

        val requestUpdateFoobar = (v4_0_0_Request / "FooBar1" / dynamicEntityId).PUT <@(user1)
        val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
        responseUpdateFoobar.code should equal(200)
        val responseUpdateFoobarName = (responseUpdateFoobar.body \ "foo_bar1" \ "name").asInstanceOf[JString].s
        responseUpdateFoobarName should equal("James Brown123")

        val requestDeleteFoobar = (v4_0_0_Request / "FooBar1" / dynamicEntityId ).DELETE <@(user1)
        val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
        responseDeleteFoobar.code should equal(204)

        When("When other user call the foobar endpoints, it need some roles")
        val requestCreateFoobarUser2 = (v4_0_0_Request / "FooBar1").POST <@(user2)
        val responseCreateFoobarUser2 = makePostRequest(requestCreateFoobarUser2, write(foobarObject))
        responseCreateFoobarUser2.code should equal(403)
        And("error should be " + UserHasMissingRoles + CanGetDynamicEntities)
        responseCreateFoobarUser2.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
      }

    }

    scenario("when user1 create fooBar, and delete the foorbar, user2 create foobar again. user1 should not have the role for it " , ApiEndpoint1, ApiEndpoint5, ApiEndpoint6, ApiEndpoint8, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, CanCreateDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, CanDeleteDynamicEntity.toString)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-entities").POST <@(user1)

      val entityWithBankId = parse(
        s"""
           |{
           |    "bankId": "${testBankId1.value}",
           |    "FooBar": {
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

      val notBankIdEntity = parse(
        s"""
           |{
           |    "FooBar1": {
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

      val foobarObject = parse("""{  "name":"James Brown",  "number":698761728}""".stripMargin)
      val foobarUpdateObject = parse("""{  "name":"James Brown123",  "number":698761728}""".stripMargin)

      val responseWithBankId = makePostRequest(request, write(entityWithBankId))
      Then("We should get a 201")
      responseWithBankId.code should equal(201)
      val dynamicEntityIdWithBankId = (responseWithBankId.body \ "dynamicEntityId").asInstanceOf[JString].s

      val responseNoBankId = makePostRequest(request, write(notBankIdEntity))
      Then("We should get a 201")
      responseNoBankId.code should equal(201)
      val dynamicEntityIdNoBankId = (responseNoBankId.body \ "dynamicEntityId").asInstanceOf[JString].s

      {
        Then("user1 can insert the new FooBar data - BankLevel")
        val requestCreateFoobar = (v4_0_0_Request / "banks"/ testBankId1.value / "FooBar").POST <@(user1)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
        val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s
        val dynamicBankId = (responseCreateFoobar.body \ "bank_id").asInstanceOf[JString].s
        dynamicBankId should equal(testBankId1.value)

        Then("we grant user3 can get FooBar role ")
        Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser3.userId, "CanGetDynamicEntity_FooBar")
        val requestCreateFoobarUser3 = (v4_0_0_Request / "banks"/ testBankId1.value / "FooBar").GET <@(user3)
        val responseCreateFoobarUser3 = makeGetRequest(requestCreateFoobarUser3)
        responseCreateFoobarUser3.code should equal(200)
        
        
        Then("user1 delete the FooBar data")
        val requestDeleteFoobar = (v4_0_0_Request / "banks"/ testBankId1.value / "FooBar" / dynamicEntityId ).DELETE <@(user1)
        val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
        responseDeleteFoobar.code should equal(204)
        
        
        
        Then("user1 delete the FooBar entity")
        val request400 = (v4_0_0_Request / "management" / "dynamic-entities" / dynamicEntityIdWithBankId).DELETE <@(user1)
        val response400 = makeDeleteRequest(request400)
        Then("We should get a 204")
        response400.code should equal(204)

       
        
        
        When("When user2 call the foobar endpoints, it need some roles")
        val request = (v4_0_0_Request / "management" / "dynamic-entities").POST <@(user2)
        val responseWithBankId = makePostRequest(request, write(entityWithBankId))
        Then("We should get a 201")
        responseWithBankId.code should equal(201)
        
        val requestCreateFoobarUser2 = (v4_0_0_Request / "banks"/ testBankId1.value / "FooBar").POST <@(user2)
        val responseCreateFoobarUser2 = makePostRequest(requestCreateFoobarUser2, write(foobarObject))
        responseCreateFoobarUser2.code should equal(201)

        When("When user1 call the foobar endpoints, it need some roles")
        val requestCreateFoobarUser1 = (v4_0_0_Request / "banks"/ testBankId1.value / "FooBar").POST <@(user1)
        val responseCreateFoobarUser1 = makePostRequest(requestCreateFoobarUser1, write(foobarObject))
        responseCreateFoobarUser1.code should equal(403)
        And("error should be " + UserHasMissingRoles + CanGetDynamicEntities)
        responseCreateFoobarUser1.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)

        val responseCreateFoobarUser3Again = makePostRequest(requestCreateFoobarUser3, write(foobarObject))
        responseCreateFoobarUser3Again.code should equal(403)
        
      }

      {
        Then("we can insert the new FooBar data - BankLevel")
        val requestCreateFoobar = (v4_0_0_Request / "FooBar1").POST <@(user1)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
        val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar1" \ "foo_bar1_id").asInstanceOf[JString].s

        Then("we grant user3 can get FooBar role ")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser3.userId, "CanGetDynamicEntity_FooBar1")
        val requestCreateFoobarUser3 = (v4_0_0_Request / "FooBar1").GET <@(user3)
        val responseCreateFoobarUser3 = makeGetRequest(requestCreateFoobarUser3)
        responseCreateFoobarUser3.code should equal(200)
        
        Then("user1 delete the FooBar data")
        val requestDeleteFoobar = (v4_0_0_Request / "FooBar1" / dynamicEntityId ).DELETE <@(user1)
        val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
        responseDeleteFoobar.code should equal(204)

        Then("user1 delete the FooBar entity")
        val request400 = (v4_0_0_Request / "management" / "dynamic-entities" / dynamicEntityIdNoBankId).DELETE <@(user1)
        val response400 = makeDeleteRequest(request400)
        response400.code should equal(204)

        When("When user2 call the foobar endpoints, it need some roles")
        val request = (v4_0_0_Request / "management" / "dynamic-entities").POST <@(user2)
        val responseNoBankId = makePostRequest(request, write(notBankIdEntity))
        Then("We should get a 201")
        responseNoBankId.code should equal(201)

        val requestCreateFoobarUser2 = (v4_0_0_Request / "FooBar1").POST <@(user2)
        val responseCreateFoobarUser2 = makePostRequest(requestCreateFoobarUser2, write(foobarObject))
        responseCreateFoobarUser2.code should equal(201)

        When("When user1 call the foobar endpoints, it need some roles")
        val requestCreateFoobarUser1 = (v4_0_0_Request / "FooBar1").POST <@(user1)
        val responseCreateFoobarUser1 = makePostRequest(requestCreateFoobarUser1, write(foobarObject))
        responseCreateFoobarUser1.code should equal(403)
        And("error should be " + UserHasMissingRoles + CanGetDynamicEntities)
        responseCreateFoobarUser1.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)

        val responseCreateFoobarUser3Again = makePostRequest(requestCreateFoobarUser3, write(foobarObject))
        responseCreateFoobarUser3Again.code should equal(403)
      }

    }
  }

}
