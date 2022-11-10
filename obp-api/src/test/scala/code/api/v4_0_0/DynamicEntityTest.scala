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
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createSystemDynamicEntity))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateSystemDynamicEntity))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getSystemDynamicEntities))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.deleteSystemDynamicEntity))
  
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getMyDynamicEntities))
  object ApiEndpoint6 extends Tag(nameOf(Implementations4_0_0.updateMyDynamicEntity))
  object ApiEndpoint7 extends Tag(nameOf(Implementations4_0_0.deleteMyDynamicEntity))
  
  object ApiEndpoint8 extends Tag(nameOf(Implementations4_0_0.getBankLevelDynamicEntities))
  object ApiEndpoint9 extends Tag(nameOf(Implementations4_0_0.createBankLevelDynamicEntity))
  object ApiEndpoint10 extends Tag(nameOf(Implementations4_0_0.deleteBankLevelDynamicEntity))
  object ApiEndpoint11 extends Tag(nameOf(Implementations4_0_0.updateBankLevelDynamicEntity))

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

  val foobarObject = parse("""{  "name":"James Brown",  "number":698761728}""".stripMargin)
  
  val foobarUpdateObject = parse("""{  "name":"James Brown123",  "number":698761728}""".stripMargin)
  
  
  feature("CRUD System Level Dynamic Entity endpoints") {

    scenario("CRUD Dynamic - without user credentials", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      When(s"We make a  $ApiEndpoint1 request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities").POST
      val response400 = makePostRequest(request400, write(rightEntity))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)

      {
        When(s"We make a request $ApiEndpoint2 v4.0.0")
        val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / "some-method-routing-id").PUT
        val response400 = makePutRequest(request400, write(rightEntity))
        Then("We should get a 401")
        response400.code should equal(401)
        And("error should be " + UserNotLoggedIn)
        response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
      }

      {
        When(s"We make a request $ApiEndpoint3 v4.0.0")
        val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities").GET
        val response400 = makeGetRequest(request400)
        Then("We should get a 401")
        response400.code should equal(401)
        And("error should be " + UserNotLoggedIn)
        response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
      }

      {
        When(s"We make a $ApiEndpoint4 request v4.0.0")
        val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / "DYNAMIC_ENTITY_ID").DELETE
        val response400 = makeDeleteRequest(request400)
        Then("We should get a 401")
        response400.code should equal(401)
        And("error should be " + UserNotLoggedIn)
        response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
      }
    }

    scenario("CRUD Dynamic - without the proper Role" , ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4,  VersionOfApi) {
      When("We make a request v4.0.0 without a Role " + canCreateSystemLevelDynamicEntity)
      val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val response400 = makePostRequest(request400, write(rightEntity))
      Then("We should get a 403")
      response400.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateSystemLevelDynamicEntity)
      response400.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanCreateSystemLevelDynamicEntity)

      {
        When(s"We make a request $ApiEndpoint2 v4.0.0")
        val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / "some-method-routing-id").PUT <@(user1)
        val response400 = makePutRequest(request400, write(rightEntity))
        Then("We should get a 403")
        response400.code should equal(403)
        And("error should be " + UserHasMissingRoles + CanUpdateSystemLevelDynamicEntity)
        response400.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanUpdateSystemLevelDynamicEntity)
      }

      {
        When(s"We make a request $ApiEndpoint3 v4.0.0")
        val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities").GET <@(user1)
        val response400 = makeGetRequest(request400)
        Then("We should get a 403")
        response400.code should equal(403)
        And("error should be " + UserHasMissingRoles + CanGetSystemLevelDynamicEntities)
        response400.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetSystemLevelDynamicEntities)
      }

      {
        When(s"We make a $ApiEndpoint4 request v4.0.0")
        val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / "DYNAMIC_ENTITY_ID").DELETE <@(user1)
        val response400 = makeDeleteRequest(request400)
        Then("We should get a 403")
        response400.code should equal(403)
        And("error should be " + UserHasMissingRoles + CanDeleteSystemLevelDynamicEntity)
        response400.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanDeleteSystemLevelDynamicEntity)
      }
    }


    scenario("Create Dynamic - two users can not create the same entity name", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, CanCreateSystemLevelDynamicEntity.toString)
      val request400User1 = (v4_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val response400User1 = makePostRequest(request400User1, write(rightEntity))
      Then("We should get a 201")
      response400User1.code should equal(201)

      val request400User2 = (v4_0_0_Request / "management" / "system-dynamic-entities").POST <@(user2)
      val response400User2 = makePostRequest(request400User2, write(rightEntity))
      Then("We should get a 400")
      response400User2.code should equal(400)
      val errorMessage = response400User2.body.extract[ErrorMessage].message
      errorMessage contains DynamicEntityNameAlreadyExists should be (true)
    }

    scenario("We will test the successful cases " , ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
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

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateSystemLevelDynamicEntity.toString)
      When("We make a request v4.0.0 with the Role " + canUpdateSystemDynamicEntity)

      {
        // update success
        val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(updateRequest))
        Then("We should get a 200")
        response400.code should equal(200)
        val updateResponseJson = response400.body
        updateResponseJson shouldEqual expectUpdatedResponseJson
      }

      {
        // update a not exists DynamicEntity
        val request404 = (v4_0_0_Request / "management" / "system-dynamic-entities" / "not-exists-id" ).PUT <@(user1)
        val response404 = makePutRequest(request404, compactRender(updateRequest))
        Then("We should get a 404")
        response404.code should equal(404)
        response404.body.extract[ErrorMessage].message should startWith (DynamicEntityNotFoundByDynamicEntityId)
      }

      {
        // update a DynamicEntity with wrong required field name
        val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(wrongRequiredEntity))
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (DynamicEntityInstanceValidateFail)
      }

      {
        // update a DynamicEntity with wrong type of description
        val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(wrongDescriptionEntity))
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (DynamicEntityInstanceValidateFail)
      }

      {
        // update a DynamicEntity with wrong type of property description
        val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(wrongPropertyDescriptionEntity))
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (DynamicEntityInstanceValidateFail)
      }

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemLevelDynamicEntities.toString)
      When("We make a request v4.0.0 with the Role " + canGetSystemLevelDynamicEntities)
      val requestGet = (v4_0_0_Request / "management" / "system-dynamic-entities").GET <@(user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
      val json = responseGet.body \ "dynamic_entities"
      val dynamicEntitiesGetJson = json.asInstanceOf[JArray]

      dynamicEntitiesGetJson.values should have size 1

      dynamicEntitiesGetJson.arr should contain(expectUpdatedResponseJson)

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
      When("We make a request v4.0.0 with the Role " + canDeleteSystemLevelDynamicEntity)
      val requestDelete400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1)
      val responseDelete400 = makeDeleteRequest(requestDelete400)
      Then("We should get a 200")
      responseDelete400.code should equal(200)

      {
        When(s"We $canGetSystemLevelDynamicEntities again, it return empty")
        val requestGet = (v4_0_0_Request / "management" / "system-dynamic-entities").GET <@(user1)
        val responseGet = makeGetRequest(requestGet)
        Then("We should get a 200")
        responseGet.code should equal(200)
        val json = responseGet.body \ "dynamic_entities"
        val dynamicEntitiesGetJson = json.asInstanceOf[JArray]

        dynamicEntitiesGetJson.values should have size 0
      }
    }
  }

  feature("Test CRUD Bank Level Dynamic Entities endpoints") {

    scenario("CRUD Bank Level DynamicEntities - without user credentials", ApiEndpoint8, ApiEndpoint9, ApiEndpoint10, ApiEndpoint11, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities").POST
      val response400 = makePostRequest(request400, write(rightEntity))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)

      {
        When("We make a request v4.0.0")
        val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities"/ "some-method-routing-id").PUT
        val response400 = makePutRequest(request400, write(rightEntity))
        Then("We should get a 401")
        response400.code should equal(401)
        And("error should be " + UserNotLoggedIn)
        response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
      }

      {
        When("We make a request v4.0.0")
        val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities").GET
        val response400 = makeGetRequest(request400)
        Then("We should get a 401")
        response400.code should equal(401)
        And("error should be " + UserNotLoggedIn)
        response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
      }

      {
        When("We make a request v4.0.0")
        val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities" / "METHOD_ROUTING_ID").DELETE
        val response400 = makeDeleteRequest(request400)
        Then("We should get a 401")
        response400.code should equal(401)
        And("error should be " + UserNotLoggedIn)
        response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
      }


    }

    scenario("Create Dynamic - without the proper Roles", ApiEndpoint8, ApiEndpoint9, ApiEndpoint10, ApiEndpoint11,  VersionOfApi) {
      val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities").POST <@(user1)
      val response400 = makePostRequest(request400, write(rightEntity))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message contains UserHasMissingRoles should be (true)
      response400.body.extract[ErrorMessage].message contains CanCreateBankLevelDynamicEntity.toString() should be (true)


      {
        When("We make a request v4.0.0")
        val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities"/ "some-method-routing-id").PUT <@(user1)
        val response400 = makePutRequest(request400, write(rightEntity))
        Then("We should get a 403")
        response400.code should equal(403)
        response400.body.extract[ErrorMessage].message contains UserHasMissingRoles should be (true)
        response400.body.extract[ErrorMessage].message contains CanUpdateBankLevelDynamicEntity.toString() should be (true)
      }

      {
        When("We make a request v4.0.0")
        val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities").GET <@(user1)
        val response400 = makeGetRequest(request400)
        Then("We should get a 403")
        response400.code should equal(403)
        response400.body.extract[ErrorMessage].message contains UserHasMissingRoles should be (true)
        response400.body.extract[ErrorMessage].message contains CanGetBankLevelDynamicEntities.toString() should be (true)
      }

      {
        When("We make a request v4.0.0")
        val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities" / "METHOD_ROUTING_ID").DELETE <@(user1)
        val response400 = makeDeleteRequest(request400)
        Then("We should get a 403")
        response400.code should equal(403)
        response400.body.extract[ErrorMessage].message contains UserHasMissingRoles should be (true)
        response400.body.extract[ErrorMessage].message contains CanDeleteBankLevelDynamicEntity.toString() should be (true)
      }

    }

    scenario("Create Dynamic - two users can not the same entity name at same bank", ApiEndpoint9, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser2.userId, CanCreateBankLevelDynamicEntity.toString)
      val request400User1BankLevel = (v4_0_0_Request / "management" / "banks"/ testBankId1.value / "dynamic-entities").POST <@(user1)
      val response400User1BankLevel = makePostRequest(request400User1BankLevel, write(rightEntity))
      Then("We should get a 201")
      response400User1BankLevel.code should equal(201)

      val request400User2BankLevel = (v4_0_0_Request / "management" / "banks"/ testBankId1.value / "dynamic-entities").POST <@(user2)
      val response400User2BankLevel = makePostRequest(request400User2BankLevel, write(rightEntity))
      Then("We should get a 400")
      response400User2BankLevel.code should equal(400)
      val errorMessageBankLevel = response400User2BankLevel.body.extract[ErrorMessage].message
      errorMessageBankLevel contains DynamicEntityNameAlreadyExists should be (true)
    }

    scenario("Create Dynamic - one user can create the same entity name at different banks", ApiEndpoint9, VersionOfApi) {
      When("We make a request v4.0.0")

      Then(s"we test the Bank Level $ApiEndpoint9")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId2.value, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
      val request400User1BankLevel = (v4_0_0_Request / "management" / "banks"/ testBankId1.value / "dynamic-entities").POST <@(user1)
      val response400User1BankLevel = makePostRequest(request400User1BankLevel, write(rightEntity))
      Then("We should get a 201")
      response400User1BankLevel.code should equal(201)

      val request400User2BankLevel = (v4_0_0_Request / "management" / "banks"/ testBankId2.value / "dynamic-entities").POST <@(user1)
      val response400User2BankLevel = makePostRequest(request400User2BankLevel, write(rightEntity))
      Then("We should get a 201")
      response400User2BankLevel.code should equal(201)
    }

    scenario("We will test the successful cases  ", ApiEndpoint8, ApiEndpoint9, ApiEndpoint10, ApiEndpoint11,  VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "banks" /testBankId1.value/ "dynamic-entities").POST <@(user1)
      val response = makePostRequest(request, write(rightEntity))
      Then("We should get a 201")
      response.code should equal(201)

      val responseJson = response.body
      val dynamicEntityId = (responseJson \ "dynamicEntityId").asInstanceOf[JString].s
      val dynamicBankId = (responseJson \ "bankId").asInstanceOf[JString].s

      val dynamicEntityUserIdJObject: JObject = "userId" -> resourceUser1.userId
      val dynamicEntityIdJObject: JObject = "dynamicEntityId" -> dynamicEntityId
      val dynamicBankIdJObject: JObject = "bankId" -> testBankId1.value

      val expectCreateResponseJson: JValue = rightEntity merge dynamicEntityUserIdJObject merge dynamicBankIdJObject merge dynamicEntityIdJObject

      responseJson shouldEqual expectCreateResponseJson


      val newNameValue: JObject =
        "FooBar" -> (
          "properties" ->
            ("name" -> (
              "example" -> "hello")
              )
          )

      val updateRequest: JValue = rightEntity merge newNameValue
      val expectUpdatedResponseJson: JValue = expectCreateResponseJson merge newNameValue

      {
        Then(s"We test $ApiEndpoint8")
        Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanGetBankLevelDynamicEntities.toString)
        val requestGet = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities").GET <@ (user1)
        val responseGet = makeGetRequest(requestGet)
        responseGet.code should equal(200)
        val json = responseGet.body \ "dynamic_entities"
        val dynamicEntitiesGetJson = json.asInstanceOf[JArray]

        dynamicEntitiesGetJson.values should have size 1
      }

      {
        // we try the different bank id, but no roles for that bank.

        val requestGet = (v4_0_0_Request /"management" / "banks" /testBankId2.value/ "dynamic-entities").GET <@(user1)
        val responseGet = makeGetRequest(requestGet)
        Then("We should get a 403")
        responseGet.code should equal(403)
        And("error should be " + UserHasMissingRoles + CanGetBankLevelDynamicEntities)
        val errorMessage = responseGet.body.extract[ErrorMessage].message
        errorMessage contains UserHasMissingRoles should be (true)
        errorMessage contains CanGetBankLevelDynamicEntities.toString() should be (true)
        //we grant the role and try it again.

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

      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanUpdateBankLevelDynamicEntity.toString)
      When("We make a request v4.0.0 with the Role " + CanUpdateSystemLevelDynamicEntity)

      {
        // update success
        val request400 = (v4_0_0_Request / "management" / "banks" /testBankId1.value/ "dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(updateRequest))
        Then("We should get a 200")
        response400.code should equal(200)
        val updateResponseJson = response400.body
        updateResponseJson shouldEqual expectUpdatedResponseJson
      }

      {
        // update a not exists DynamicEntity
        val request404 = (v4_0_0_Request / "management" / "banks" /testBankId1.value/ "dynamic-entities" / "not-exists-id" ).PUT <@(user1)
        val response404 = makePutRequest(request404, compactRender(updateRequest))
        Then("We should get a 404")
        response404.code should equal(404)
        response404.body.extract[ErrorMessage].message should startWith (DynamicEntityNotFoundByDynamicEntityId)
      }

      {
        // update a DynamicEntity with wrong required field name
        val request400 = (v4_0_0_Request / "management" / "banks" /testBankId1.value/ "dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(wrongRequiredEntity))
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (DynamicEntityInstanceValidateFail)
      }

      {
        // update a DynamicEntity with wrong type of description
        val request400 = (v4_0_0_Request / "management" / "banks" /testBankId1.value/ "dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(wrongDescriptionEntity))
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (DynamicEntityInstanceValidateFail)
      }

      {
        // update a DynamicEntity with wrong type of property description
        val request400 = (v4_0_0_Request / "management" / "banks" /testBankId1.value/ "dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(wrongPropertyDescriptionEntity))
        Then("We should get a 400")

        response400.code should equal(400)
        response400.body.extract[ErrorMessage].message should startWith (DynamicEntityInstanceValidateFail)
      }

      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
      When("We make a request v4.0.0 with the Role " + CanCreateBankLevelDynamicEntity)
      val requestGet = (v4_0_0_Request / "management" / "banks" /testBankId1.value/ "dynamic-entities").GET <@(user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
      val json = responseGet.body \ "dynamic_entities"
      val dynamicEntitiesGetJson = json.asInstanceOf[JArray]

      dynamicEntitiesGetJson.values should have size 1

      dynamicEntitiesGetJson.arr should contain(expectUpdatedResponseJson)

      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanDeleteBankLevelDynamicEntity.toString)
      When("We make a request v4.0.0 with the Role " + CanDeleteSystemLevelDynamicEntity)
      val requestDelete400 = (v4_0_0_Request / "management" / "banks" /testBankId1.value/ "dynamic-entities" / dynamicEntityId).DELETE <@(user1)
      val responseDelete400 = makeDeleteRequest(requestDelete400)
      Then("We should get a 200")
      responseDelete400.code should equal(200)

    }
  }

  feature("Test CRUD my Dynamic Entities endpoints") {

    scenario("Test CRUD myDynamic Entities- without user credentials", ApiEndpoint5, ApiEndpoint6, ApiEndpoint7, VersionOfApi) {
      val dynamicEntityId  = "forTestId"
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "my" / "dynamic-entities").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)

      val request400Put = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityId).PUT
      val response400Put = makePutRequest(request400Put, write(rightEntity))
      Then("We should get a 401")
      response400Put.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400Put.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)

      val request400Delete = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityId).DELETE
      val response400Delete = makeDeleteRequest(request400Delete)
      Then("We should get a 401")
      response400Delete.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400Delete.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }

    scenario("Test the CRUD Success cases ", ApiEndpoint1, ApiEndpoint5, ApiEndpoint6, ApiEndpoint7, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
      When("we first create system level entity")
      val request = (v4_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val response = makePostRequest(request, write(rightEntity))
      Then("We should get a 201")
      response.code should equal(201)
      val responseJson = response.body
      val dynamicEntityId = (responseJson \ "dynamicEntityId").asInstanceOf[JString].s


      Then("We create the bank level entity.")
      val requestBankLevel = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities").POST<@(user1)
      val responseBankLevel = makePostRequest(requestBankLevel, write(rightEntity))
      Then("We should get a 201")
      responseBankLevel.code should equal(201)

      val responseBankLevelJson = responseBankLevel.body
      val dynamicEntityIdBankLevel = (responseBankLevelJson \ "dynamicEntityId").asInstanceOf[JString].s

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
        // test system level update success
        val request400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityId ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(updateRequest))
        Then("We should get a 200")
        response400.code should equal(200)
        val updateResponseJson = response400.body
        updateResponseJson shouldEqual expectUpdatedResponseJson
      }

      {
        // test bank level update success
        val request400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityIdBankLevel ).PUT <@(user1)
        val response400 = makePutRequest(request400, compactRender(updateRequest))
        Then("We should get a 200")
        response400.code should equal(200)
        val updateResponseJson = response400.body
        updateResponseJson.toString contains(dynamicEntityIdBankLevel) shouldBe (true)
      }

      {
        // update a not exists DynamicEntity
        val request404 = (v4_0_0_Request / "my" / "dynamic-entities" / "not-exists-id" ).PUT <@(user1)
        val response404 = makePutRequest(request404, compactRender(updateRequest))
        Then("We should get a 404")
        response404.code should equal(400)
        response404.body.extract[ErrorMessage].message should startWith (InvalidMyDynamicEntityUser)
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

      dynamicEntitiesGetJson.values should have size 2

      dynamicEntitiesGetJson.arr should contain(expectUpdatedResponseJson)

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
        // delete a MyDynamicEntity with proper user1
        val requestDelete400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityId).DELETE <@(user1)
        val responseDelete400 = makeDeleteRequest(requestDelete400)
        Then("We should get a 200")
        responseDelete400.code should equal(200)
      }

      {
        // delete a MyDynamicEntity with proper user1
        val requestDelete400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityIdBankLevel).DELETE <@(user1)
        val responseDelete400 = makeDeleteRequest(requestDelete400)
        Then("We should get a 200")
        responseDelete400.code should equal(200)
      }

      {
        Then(s"after delete all the dynamic entities, we call getEntities again, it should return empty list" )
        val requestGet = (v4_0_0_Request / "my" / "dynamic-entities").GET <@(user1)
        val responseGet = makeGetRequest(requestGet)
        Then("We should get a 200")
        responseGet.code should equal(200)
        val json = responseGet.body \ "dynamic_entities"
        val dynamicEntitiesGetJson = json.asInstanceOf[JArray]
        dynamicEntitiesGetJson.values should have size 0
      }
    }
  }

  feature("Test CRUD Dynamic Entities Mixed System, Bank and my endpoints") {
    scenario("We will test the successful cases ", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, ApiEndpoint6, ApiEndpoint7, ApiEndpoint8, ApiEndpoint9, VersionOfApi) {

      //      First, we create the system level dynamic entity
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "system-dynamic-entities").POST <@ (user1)
      val response = makePostRequest(request, write(rightEntity))
      Then("We should get a 201")
      response.code should equal(201)

      val responseJson = response.body
      val dynamicEntityId = (responseJson \ "dynamicEntityId").asInstanceOf[JString].s
      val dynamicEntityUserIdJObject: JObject = "userId" -> resourceUser1.userId
      val dynamicEntityIdJObject: JObject = "dynamicEntityId" -> dynamicEntityId
      val expectCreateResponseJson: JValue = rightEntity merge dynamicEntityUserIdJObject merge dynamicEntityIdJObject

      //      2rd: we create the bank level dynamic entity
      val requestBankLevel = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities").POST <@ (user1)
      val responseBankLevel = makePostRequest(requestBankLevel, write(rightEntity))
      Then("We should get a 201")
      responseBankLevel.code should equal(201)
      val responseJsonBankLevel = responseBankLevel.body
      val dynamicEntityIdBankLevel = (responseBankLevel.body \ "dynamicEntityId").asInstanceOf[JString].s
      val dynamicEntityIdJObjectBankLevel: JObject = "dynamicEntityId" -> dynamicEntityIdBankLevel
      val bankIdObject: JObject = "bankId" -> testBankId1.value

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

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateSystemLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanUpdateBankLevelDynamicEntity.toString)
      When("We make a request v4.0.0 with the Role " + CanUpdateSystemLevelDynamicEntity)

      {
        // can update system entity
        val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).PUT <@ (user1)
        val response400 = makePutRequest(request400, compactRender(updateRequest))
        Then("We should get a 200")
        response400.code should equal(200)
        val updateResponseJson = response400.body
        updateResponseJson shouldEqual expectUpdatedResponseJson

        // But can not update bankLevel entity
        {
          val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityIdBankLevel).PUT <@ (user1)
          val response400 = makePutRequest(request400, compactRender(updateRequest))
          Then("We should get a 404")
          response400.code should equal(404)
          response400.body.toString contains (DynamicEntityNotFoundByDynamicEntityId) should be(true)
        }

        {
          // can update bank level entity using 
          val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities" / dynamicEntityIdBankLevel).PUT <@ (user1)
          val response400 = makePutRequest(request400, compactRender(updateRequest))
          Then("We should get a 200")
          response400.code should equal(200)
          val updateResponseJson = response400.body

          // But can not update system entity 
          {
            val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities" / dynamicEntityId).PUT <@ (user1)
            val response400 = makePutRequest(request400, compactRender(updateRequest))
            Then("We should get a 404")
            response400.code should equal(404)
            response400.body.toString contains (DynamicEntityNotFoundByDynamicEntityId) should be(true)
          }
        }

        {
          // myDynamic can update bank level entity  
          val request400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityIdBankLevel).PUT <@ (user1)
          val response400 = makePutRequest(request400, compactRender(updateRequest))
          Then("We should get a 200")
          response400.code should equal(200)
          val updateResponseJson = response400.body

          // myDynamic can update system entity 
          {
            val request400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityId).PUT <@ (user1)
            val response400 = makePutRequest(request400, compactRender(updateRequest))
            Then("We should get a 200")
            response400.code should equal(200)
            val updateResponseJson = response400.body
            updateResponseJson shouldEqual expectUpdatedResponseJson
          }
        }
      }

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemLevelDynamicEntities.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanGetBankLevelDynamicEntities.toString)

      {
        // get system entity return one record
        val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities").GET <@ (user1)
        val response400 = makeGetRequest(request400)
        Then("We should get a 200")
        response400.code should equal(200)
        val json = response400.body \ "dynamic_entities"
        val dynamicEntitiesGetJson = json.asInstanceOf[JArray]
        dynamicEntitiesGetJson.values should have size 1
        dynamicEntitiesGetJson.arr should contain(expectUpdatedResponseJson)

        // get bank entity return one record
        {
          val request400 = (v4_0_0_Request / "management" / "banks" /testBankId1.value/ "dynamic-entities").GET <@(user1)
          val response400 = makeGetRequest(request400)
          Then("We should get a 200")
          response400.code should equal(200)
          val json = response400.body \ "dynamic_entities"
          val dynamicEntitiesGetJson = json.asInstanceOf[JArray]
          dynamicEntitiesGetJson.values should have size 1
        }

        // get myDynamic can return 2 records 
        {
          val request400 = (v4_0_0_Request / "my" / "dynamic-entities").GET <@ (user1)
          val response400 = makeGetRequest(request400)
          Then("We should get a 200")
          response400.code should equal(200)
          val json = response400.body \ "dynamic_entities"
          val dynamicEntitiesGetJson = json.asInstanceOf[JArray]
          dynamicEntitiesGetJson.values should have size 2
        }
      }

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanDeleteBankLevelDynamicEntity.toString)
      When("We make a request v4.0.0 with the Role " + CanDeleteSystemLevelDynamicEntity)

      //      delete system level entity using bank level endpoint -- failed
      val requestDelete400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities" / dynamicEntityId).DELETE <@ (user1)
      val responseDelete400 = makeDeleteRequest(requestDelete400)
      Then("We should get a 404")
      responseDelete400.code should equal(404)
      responseDelete400.body.toString contains (DynamicEntityNotFoundByDynamicEntityId) should be(true)

      {
        //      delete system level entity using system level endpoint -- success
        val requestDelete400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@ (user1)
        val responseDelete400 = makeDeleteRequest(requestDelete400)
        Then("We should get a 200")
        responseDelete400.code should equal(200)
      }

      {
        //      delete bank level entity using system level endpoint -- failed
        val requestDelete400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityIdBankLevel).DELETE <@ (user1)
        val responseDelete400 = makeDeleteRequest(requestDelete400)
        Then("We should get a 404")
        responseDelete400.code should equal(404)
        responseDelete400.body.toString contains (DynamicEntityNotFoundByDynamicEntityId) should be(true)

        {
          //      delete bank level entity using bank level endpoint -- success
          val requestDelete400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities" / dynamicEntityIdBankLevel).DELETE <@ (user1)
          val responseDelete400 = makeDeleteRequest(requestDelete400)
          Then("We should get a 200")
          responseDelete400.code should equal(200)
        }
    }
//      than prepare 2 dynamic entity for delete my entities:
      {
        When("We make a request v4.0.0")
        val response = makePostRequest(request, write(rightEntity))
        response.code should equal(201)
        val dynamicEntityId = (response.body \ "dynamicEntityId").asInstanceOf[JString].s

        //2rd: we create the bank level dynamic entity
        val responseBankLevel = makePostRequest(requestBankLevel, write(rightEntity))
        responseBankLevel.code should equal(201)
       
        val dynamicEntityIdBankLevel = (responseBankLevel.body \ "dynamicEntityId").asInstanceOf[JString].s
        
        {//can delete system level
          val requestDelete400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityId).DELETE <@ (user1)
          val responseDelete400 = makeDeleteRequest(requestDelete400)
          Then("We should get a 200")
          responseDelete400.code should equal(200)
        }
        
        {//can delete bank level
          val requestDelete400 = (v4_0_0_Request / "my" / "dynamic-entities" / dynamicEntityIdBankLevel).DELETE <@ (user1)
          val responseDelete400 = makeDeleteRequest(requestDelete400)
          Then("We should get a 200")
          responseDelete400.code should equal(200)
        }
      }
  }
  }

  feature("Test CRUD Foobar Records and Roles (both Bank and System levels) ") {
    scenario("We create the system and bank level entities, and check the Foobar roles ", ApiEndpoint1, ApiEndpoint5, ApiEndpoint6, ApiEndpoint8, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
      val requestBankLevel = (v4_0_0_Request / "management" /"banks" /testBankId1.value/ "dynamic-entities").POST <@(user1)

      val foobarObject = parse("""{  "name":"James Brown",  "number":698761728}""".stripMargin)
      val foobarUpdateObject = parse("""{  "name":"James Brown123",  "number":698761728}""".stripMargin)

      val systemLevelEntity = makePostRequest(request, write(rightEntity))
      Then("We should get a 201")
      systemLevelEntity.code should equal(201)

      {
        Then("we can insert the new FooBar data - SystemLevel")
        val requestCreateFoobar = (dynamicEntity_Request / "FooBar").POST <@(user1)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
        val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s

        val requestGetFoobars = (dynamicEntity_Request / "FooBar").GET <@(user1)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)

        val requestGetFoobar = (dynamicEntity_Request / "FooBar" / dynamicEntityId ).GET <@(user1)
        val responseGetFoobar = makeGetRequest(requestGetFoobar)
        responseGetFoobar.code should equal(200)

        val requestUpdateFoobar = (dynamicEntity_Request / "FooBar" / dynamicEntityId).PUT <@(user1)
        val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
        responseUpdateFoobar.code should equal(200)
        val responseUpdateFoobarName = (responseUpdateFoobar.body \ "foo_bar" \ "name").asInstanceOf[JString].s
        responseUpdateFoobarName should equal("James Brown123")

        val requestDeleteFoobar = (dynamicEntity_Request / "FooBar" / dynamicEntityId ).DELETE <@(user1)
        val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
        responseDeleteFoobar.code should equal(200)

        {
          When("user 2 call the foobar endpoints, it need the roles")
          Then("we can insert the new FooBar data - SystemLevel")
          val requestCreateFoobar = (dynamicEntity_Request / "FooBar").POST <@(user2)
          val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
          responseCreateFoobar.code should equal(403)
          And("error should be " + UserHasMissingRoles)
          responseCreateFoobar.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
          responseCreateFoobar.body.extract[ErrorMessage].message contains ("CanCreateDynamicEntity_SystemFooBar") should be (true)

          val requestGetFoobars = (dynamicEntity_Request / "FooBar").GET <@(user2)
          val responseGetFoobars = makeGetRequest(requestGetFoobars)
          responseGetFoobars.code should equal(403)
          And("error should be " + UserHasMissingRoles)
          responseGetFoobars.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
          responseGetFoobars.body.extract[ErrorMessage].message contains ("CanGetDynamicEntity_SystemFooBar") should be (true)

          val requestGetFoobar = (dynamicEntity_Request / "FooBar" / dynamicEntityId ).GET <@(user2)
          val responseGetFoobar = makeGetRequest(requestGetFoobar)
          responseGetFoobar.code should equal(403)
          And("error should be " + UserHasMissingRoles)
          responseGetFoobar.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
          responseGetFoobar.body.extract[ErrorMessage].message contains ("CanGetDynamicEntity_SystemFooBar") should be (true)

          val requestUpdateFoobar = (dynamicEntity_Request / "FooBar" / dynamicEntityId).PUT <@(user2)
          val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
          responseUpdateFoobar.code should equal(403)
          And("error should be " + UserHasMissingRoles)
          responseUpdateFoobar.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
          responseUpdateFoobar.body.extract[ErrorMessage].message contains ("CanUpdateDynamicEntity_SystemFooBar") should be (true)

          val requestDeleteFoobar = (dynamicEntity_Request / "FooBar" / dynamicEntityId ).DELETE <@(user2)
          val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
          responseDeleteFoobar.code should equal(403)
          And("error should be " + UserHasMissingRoles)
          responseDeleteFoobar.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
          responseDeleteFoobar.body.extract[ErrorMessage].message contains ("CanDeleteDynamicEntity_SystemFooBar") should be (true)
        }

        {
          Then("we grant user2 the missing roles and CRUD again - SystemLevel")
          Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanCreateDynamicEntity_SystemFooBar")
          Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanUpdateDynamicEntity_SystemFooBar")
          Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanGetDynamicEntity_SystemFooBar")
          Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanDeleteDynamicEntity_SystemFooBar")
          val requestCreateFoobar = (dynamicEntity_Request / "FooBar").POST <@(user2)
          val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
          responseCreateFoobar.code should equal(201)
          val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s

          val requestGetFoobars = (dynamicEntity_Request / "FooBar").GET <@(user2)
          val responseGetFoobars = makeGetRequest(requestGetFoobars)
          responseGetFoobars.code should equal(200)

          val requestGetFoobar = (dynamicEntity_Request / "FooBar" / dynamicEntityId ).GET <@(user2)
          val responseGetFoobar = makeGetRequest(requestGetFoobar)
          responseGetFoobar.code should equal(200)

          val requestUpdateFoobar = (dynamicEntity_Request / "FooBar" / dynamicEntityId).PUT <@(user2)
          val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
          responseUpdateFoobar.code should equal(200)
          val responseUpdateFoobarName = (responseUpdateFoobar.body \ "foo_bar" \ "name").asInstanceOf[JString].s
          responseUpdateFoobarName should equal("James Brown123")

          val requestDeleteFoobar = (dynamicEntity_Request / "FooBar" / dynamicEntityId ).DELETE <@(user2)
          val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
          responseDeleteFoobar.code should equal(200)
          
        }
      }

      val bankLevelEntity = makePostRequest(requestBankLevel, write(rightEntity))
      Then("We should get a 201")
      bankLevelEntity.code should equal(201)

      {
        Then("we can insert the new FooBar data - BankLevel")
        val requestCreateFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar").POST <@(user1)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
        val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s
        val dynamicBankId = (responseCreateFoobar.body \ "bank_id").asInstanceOf[JString].s
        dynamicBankId should equal(testBankId1.value)

        val requestGetFoobars = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar").GET <@(user1)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)
        val dynamicBankIdGetFoobars = (responseGetFoobars.body \ "bank_id").asInstanceOf[JString].s
        dynamicBankIdGetFoobars should equal(testBankId1.value)

        val requestGetFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar" / dynamicEntityId ).GET <@(user1)
        val responseGetFoobar = makeGetRequest(requestGetFoobar)
        responseGetFoobar.code should equal(200)
        val dynamicBankIdGetFoobar = (responseGetFoobar.body \ "bank_id").asInstanceOf[JString].s
        dynamicBankIdGetFoobar should equal(testBankId1.value)

        val requestUpdateFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar" / dynamicEntityId).PUT <@(user1)
        val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
        responseUpdateFoobar.code should equal(200)
        val responseUpdateFoobarName = (responseUpdateFoobar.body \ "foo_bar" \ "name").asInstanceOf[JString].s
        responseUpdateFoobarName should equal("James Brown123")

        val requestDeleteFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar" / dynamicEntityId ).DELETE <@(user1)
        val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
        responseDeleteFoobar.code should equal(200)

        {
          When("user 2 call the foobar endpoints, it need the roles")
          Then("we can insert the new FooBar data - SystemLevel")
          val requestCreateFoobar = (dynamicEntity_Request / "banks"/ testBankId1.value / "FooBar").POST <@(user2)
          val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
          responseCreateFoobar.code should equal(403)
          And("error should be " + UserHasMissingRoles)
          responseCreateFoobar.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
          responseCreateFoobar.body.extract[ErrorMessage].message contains ("CanCreateDynamicEntity_FooBar") should be (true)

          val requestGetFoobars = (dynamicEntity_Request /"banks"/ testBankId1.value / "FooBar").GET <@(user2)
          val responseGetFoobars = makeGetRequest(requestGetFoobars)
          responseGetFoobars.code should equal(403)
          And("error should be " + UserHasMissingRoles)
          responseGetFoobars.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
          responseGetFoobars.body.extract[ErrorMessage].message contains ("CanGetDynamicEntity_FooBar") should be (true)
          

          val requestGetFoobar = (dynamicEntity_Request / "banks"/ testBankId1.value / "FooBar" / dynamicEntityId ).GET <@(user2)
          val responseGetFoobar = makeGetRequest(requestGetFoobar)
          responseGetFoobar.code should equal(403)
          And("error should be " + UserHasMissingRoles)
          responseGetFoobar.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
          responseGetFoobar.body.extract[ErrorMessage].message contains ("CanGetDynamicEntity_FooBar") should be (true)

          val requestUpdateFoobar = (dynamicEntity_Request / "banks"/ testBankId1.value /"FooBar" / dynamicEntityId).PUT <@(user2)
          val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
          responseUpdateFoobar.code should equal(403)
          And("error should be " + UserHasMissingRoles)
          responseUpdateFoobar.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
          responseUpdateFoobar.body.extract[ErrorMessage].message contains ("CanUpdateDynamicEntity_FooBar") should be (true)

          val requestDeleteFoobar = (dynamicEntity_Request / "banks"/ testBankId1.value /"FooBar" / dynamicEntityId ).DELETE <@(user2)
          val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
          responseDeleteFoobar.code should equal(403)
          And("error should be " + UserHasMissingRoles)
          responseDeleteFoobar.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
          responseDeleteFoobar.body.extract[ErrorMessage].message contains ("CanDeleteDynamicEntity_FooBar") should be (true)
        }
        
        {
          Then("we grant user2 roles and try CRUD again")
          Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser2.userId, "CanCreateDynamicEntity_FooBar")
          Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser2.userId, "CanGetDynamicEntity_FooBar")
          Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser2.userId, "CanUpdateDynamicEntity_FooBar")
          Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser2.userId, "CanDeleteDynamicEntity_FooBar")
          
          val requestCreateFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar").POST <@(user2)
          val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
          responseCreateFoobar.code should equal(201)
          val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s
          val dynamicBankId = (responseCreateFoobar.body \ "bank_id").asInstanceOf[JString].s
          dynamicBankId should equal(testBankId1.value)

          val requestGetFoobars = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar").GET <@(user2)
          val responseGetFoobars = makeGetRequest(requestGetFoobars)
          responseGetFoobars.code should equal(200)
          val dynamicBankIdGetFoobars = (responseGetFoobars.body \ "bank_id").asInstanceOf[JString].s
          dynamicBankIdGetFoobars should equal(testBankId1.value)

          val requestGetFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar" / dynamicEntityId ).GET <@(user2)
          val responseGetFoobar = makeGetRequest(requestGetFoobar)
          responseGetFoobar.code should equal(200)
          val dynamicBankIdGetFoobar = (responseGetFoobar.body \ "bank_id").asInstanceOf[JString].s
          dynamicBankIdGetFoobar should equal(testBankId1.value)

          val requestUpdateFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar" / dynamicEntityId).PUT <@(user2)
          val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
          responseUpdateFoobar.code should equal(200)
          val responseUpdateFoobarName = (responseUpdateFoobar.body \ "foo_bar" \ "name").asInstanceOf[JString].s
          responseUpdateFoobarName should equal("James Brown123")

          val requestDeleteFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar" / dynamicEntityId ).DELETE <@(user1)
          val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
          responseDeleteFoobar.code should equal(200)
        }
      }

    }

    scenario("when user1 create fooBar, and delete the foobar entity, user2 create foobar again. user1 should not have the role for it " , ApiEndpoint1, ApiEndpoint5, ApiEndpoint6, ApiEndpoint8, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, CanCreateSystemLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, CanDeleteSystemLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser2.userId, CanCreateBankLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanDeleteBankLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser2.userId, CanDeleteBankLevelDynamicEntity.toString)
      When("We make a request v4.0.0")
      val requestSystemLevel = (v4_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val requestBankLevel = (v4_0_0_Request / "management" / "banks" /testBankId1.value / "dynamic-entities").POST <@(user1)

      val foobarObject = parse("""{  "name":"James Brown",  "number":698761728}""".stripMargin)

      val response= makePostRequest(requestSystemLevel, write(rightEntity))
      Then("We should get a 201")
      response.code should equal(201)
      val dynamicEntityId = (response.body \ "dynamicEntityId").asInstanceOf[JString].s

      {
        Then("we can insert the new FooBar data - SystemLevel")
        val requestCreateFoobar = (dynamicEntity_Request / "FooBar").POST <@(user1)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
        val fooBarId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s

        {
          Then("user2 can not get the foo bar records")
          val requestCreateFoobarUser2 = (dynamicEntity_Request/ "FooBar").GET <@(user2)
          val responseCreateFoobarUser2 = makeGetRequest(requestCreateFoobarUser2)
          responseCreateFoobarUser2.code should equal(403)
          And("error should be " + UserHasMissingRoles)
          responseCreateFoobarUser2.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
        }

        Then("we grant user2 can get FooBar role, user2 can get the foobar records. ")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanGetDynamicEntity_SystemFooBar")
        val requestCreateFoobarUser2 = (dynamicEntity_Request / "FooBar").GET <@(user2)
        val responseCreateFoobarUser2 = makeGetRequest(requestCreateFoobarUser2)
        responseCreateFoobarUser2.code should equal(200)

        {
          Then(s"user1 try to delete the FooBar entity, it will show the error $DynamicEntityOperationNotAllowed")
          val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1)
          val response400 = makeDeleteRequest(request400)
          response400.code should equal(400)
          response400.body.extract[ErrorMessage].message contains (DynamicEntityOperationNotAllowed) should be (true)
        }

        Then("user1 delete the FooBar data first")
        val requestDeleteFoobar = (dynamicEntity_Request / "FooBar" / fooBarId ).DELETE <@(user1)
        val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
        responseDeleteFoobar.code should equal(200)

        Then("user1 delete the FooBar entity")
        val request400 = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1)
        val response400 = makeDeleteRequest(request400)
        response400.code should equal(200)

        Then("user2 create foobar dynamic entity")
        val request = (v4_0_0_Request / "management" / "system-dynamic-entities").POST <@(user2)
        val responseNoBankId = makePostRequest(request, write(rightEntity))
        Then("We should get a 201")
        responseNoBankId.code should equal(201)

        {
          val responseCreateFoobarUser2 = makePostRequest(requestCreateFoobarUser2, write(foobarObject))
          responseCreateFoobarUser2.code should equal(201)
        }

        When("When user1 call the foobar endpoints, it need some roles")
        val requestCreateFoobarUser1 = (dynamicEntity_Request / "FooBar").POST <@(user1)
        val responseCreateFoobarUser1 = makePostRequest(requestCreateFoobarUser1, write(foobarObject))
        responseCreateFoobarUser1.code should equal(403)
        And("error should be " + UserHasMissingRoles)
        responseCreateFoobarUser1.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
      }

      Then("we test the bank level")
      val responseBankLevel= makePostRequest(requestBankLevel, write(rightEntity))
      Then("We should get a 201")
      responseBankLevel.code should equal(201)
      val dynamicEntityIdBankLevel = (responseBankLevel.body \ "dynamicEntityId").asInstanceOf[JString].s

      {
        Then("user1 can insert the new FooBar data - BankLevel")
        val requestCreateFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar").POST <@(user1)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
        val fooBarId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s
        val dynamicBankId = (responseCreateFoobar.body \ "bank_id").asInstanceOf[JString].s
        dynamicBankId should equal(testBankId1.value)

        {
          Then("user2 need some roles")
          val requestCreateFoobarUser2 = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar").GET <@(user2)
          val responseCreateFoobarUser2 = makeGetRequest(requestCreateFoobarUser2)
          responseCreateFoobarUser2.code should equal(403)
          And("error should be " + UserHasMissingRoles)
          responseCreateFoobarUser2.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)
        }
        {
          Then("we grant user2 can get FooBar role ")
          Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser3.userId, "CanGetDynamicEntity_FooBar")
          val requestCreateFoobarUser2 = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar").GET <@(user3)
          val responseCreateFoobarUser2 = makeGetRequest(requestCreateFoobarUser2)
          responseCreateFoobarUser2.code should equal(200)
        }
        
        Then("user1 delete the FooBar data")
        val requestDeleteFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar" / fooBarId ).DELETE <@(user1)
        val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
        responseDeleteFoobar.code should equal(200)

        Then("user1 delete the FooBar entity")
        val request400 = (v4_0_0_Request / "management" / "banks"/ testBankId1.value / "dynamic-entities" / dynamicEntityIdBankLevel).DELETE <@(user1)
        val response400 = makeDeleteRequest(request400)
        Then("We should get a 200")
        response400.code should equal(200)

        Then("When user2 call the foobar endpoints, it need some roles")
        val request = (v4_0_0_Request / "management"/"banks"/ testBankId1.value /"dynamic-entities").POST <@(user2)
        val responseWithBankId = makePostRequest(request, write(rightEntity))
        Then("We should get a 201")
        responseWithBankId.code should equal(201)

        val requestCreateFoobarUser2 = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar").POST <@(user2)
        val responseCreateFoobarUser2 = makePostRequest(requestCreateFoobarUser2, write(foobarObject))
        responseCreateFoobarUser2.code should equal(201)

        When("When user1 call the foobar endpoints, it need some roles")
        val requestCreateFoobarUser1 = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar").POST <@(user1)
        val responseCreateFoobarUser1 = makePostRequest(requestCreateFoobarUser1, write(foobarObject))
        responseCreateFoobarUser1.code should equal(403)
        And("error should be " + UserHasMissingRoles)
        responseCreateFoobarUser1.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)

      }

    }

    scenario("User1 create System Foobar, user2 create bank Foobar, test the roles..", VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser2.userId, CanCreateBankLevelDynamicEntity.toString)
      val foobarObject = parse("""{  "name":"James Brown",  "number":698761728}""".stripMargin)
      val foobarUpdateObject = parse("""{  "name":"James Brown123",  "number":698761728}""".stripMargin)
      
      
      When("user1  create system Foobar")
      val request = (v4_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
      val systemLevelEntity = makePostRequest(request, write(rightEntity))
      Then("We should get a 201")
      systemLevelEntity.code should equal(201)

      Then("user1  create bank Foobar")
      val requestBankLevel = (v4_0_0_Request / "management" /"banks" /testBankId1.value/ "dynamic-entities").POST <@(user2)
      val bankLevelEntity = makePostRequest(requestBankLevel, write(rightEntity))
      Then("We should get a 201")
      bankLevelEntity.code should equal(201)

      
      Then("user1 can insert the new FooBar data - SystemLevel")
      val requestCreateFoobar = (dynamicEntity_Request / "FooBar").POST <@(user1)
      val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
      responseCreateFoobar.code should equal(201)
      val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s

      val requestGetFoobars = (dynamicEntity_Request / "FooBar").GET <@(user1)
      val responseGetFoobars = makeGetRequest(requestGetFoobars)
      responseGetFoobars.code should equal(200)

      val requestGetFoobar = (dynamicEntity_Request / "FooBar" / dynamicEntityId ).GET <@(user1)
      val responseGetFoobar = makeGetRequest(requestGetFoobar)
      responseGetFoobar.code should equal(200)

      val requestUpdateFoobar = (dynamicEntity_Request / "FooBar" / dynamicEntityId).PUT <@(user1)
      val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
      responseUpdateFoobar.code should equal(200)
      val responseUpdateFoobarName = (responseUpdateFoobar.body \ "foo_bar" \ "name").asInstanceOf[JString].s
      responseUpdateFoobarName should equal("James Brown123")

      val requestDeleteFoobar = (dynamicEntity_Request / "FooBar" / dynamicEntityId ).DELETE <@(user1)
      val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
      responseDeleteFoobar.code should equal(200)

      When("When user2 user call the system foobar endpoints, it need some roles")
      val requestCreateFoobarUser2 = (dynamicEntity_Request / "FooBar").POST <@(user2)
      val responseCreateFoobarUser2 = makePostRequest(requestCreateFoobarUser2, write(foobarObject))
      responseCreateFoobarUser2.code should equal(403)
      And("error should be " + UserHasMissingRoles)
      responseCreateFoobarUser2.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)

      val requestGetFoobarsUser2 = (dynamicEntity_Request / "FooBar").GET <@(user2)
      val responseGetFoobarsUser2 = makeGetRequest(requestGetFoobarsUser2)
      responseGetFoobarsUser2.code should equal(403)
      And("error should be " + UserHasMissingRoles)
      responseGetFoobarsUser2.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)

      val requestGetFoobarUser2 = (dynamicEntity_Request / "FooBar" / dynamicEntityId ).GET <@(user2)
      val responseGetFoobarUser2 = makeGetRequest(requestGetFoobarUser2)
      responseGetFoobarUser2.code should equal(403)
      And("error should be " + UserHasMissingRoles)
      responseGetFoobarUser2.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)

      val requestUpdateFoobarUser2 = (dynamicEntity_Request / "FooBar" / dynamicEntityId).PUT <@(user2)
      val responseUpdateFoobarUser2 = makePutRequest(requestUpdateFoobarUser2, write(foobarUpdateObject))
      responseUpdateFoobarUser2.code should equal(403)
      And("error should be " + UserHasMissingRoles)
      responseUpdateFoobarUser2.body.extract[ErrorMessage].message contains (UserHasMissingRoles) should be (true)

      val requestDeleteFoobarUser2 = (dynamicEntity_Request / "FooBar" / dynamicEntityId ).DELETE <@(user2)
      val responseDeleteFoobarUser2 = makeDeleteRequest(requestDeleteFoobarUser2)
      responseDeleteFoobarUser2.code should equal(403)

      {
        Then("User2 can insert the new FooBar data - BankLevel")
        val requestCreateFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar").POST <@(user2)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
        val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s
        val dynamicBankId = (responseCreateFoobar.body \ "bank_id").asInstanceOf[JString].s
        dynamicBankId should equal(testBankId1.value)

        val requestGetFoobars = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar").GET <@(user2)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)
        val dynamicBankIdGetFoobars = (responseGetFoobars.body \ "bank_id").asInstanceOf[JString].s
        dynamicBankIdGetFoobars should equal(testBankId1.value)

        val requestGetFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar" / dynamicEntityId ).GET <@(user2)
        val responseGetFoobar = makeGetRequest(requestGetFoobar)
        responseGetFoobar.code should equal(200)
        val dynamicBankIdGetFoobar = (responseGetFoobar.body \ "bank_id").asInstanceOf[JString].s
        dynamicBankIdGetFoobar should equal(testBankId1.value)

        val requestUpdateFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar" / dynamicEntityId).PUT <@(user2)
        val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
        responseUpdateFoobar.code should equal(200)
        val responseUpdateFoobarName = (responseUpdateFoobar.body \ "foo_bar" \ "name").asInstanceOf[JString].s
        responseUpdateFoobarName should equal("James Brown123")

        val requestDeleteFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar" / dynamicEntityId ).DELETE <@(user2)
        val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
        responseDeleteFoobar.code should equal(200)

        {
          When("User1 call the foobar endpoints, it need some roles")
          val requestCreateFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar").POST <@(user1)
          val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
          responseCreateFoobar.code should equal(403)
  
          val requestGetFoobars = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar").GET <@(user1)
          val responseGetFoobars = makeGetRequest(requestGetFoobars)
          responseGetFoobars.code should equal(403)
  
          val requestGetFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar" / dynamicEntityId ).GET <@(user1)
          val responseGetFoobar = makeGetRequest(requestGetFoobar)
          responseGetFoobar.code should equal(403)
  
          val requestUpdateFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar" / dynamicEntityId).PUT <@(user1)
          val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
          responseUpdateFoobar.code should equal(403)
  
          val requestDeleteFoobar = (dynamicEntity_Request/ "banks"/ testBankId1.value / "FooBar" / dynamicEntityId ).DELETE <@(user1)
          val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
          responseDeleteFoobar.code should equal(403)
        } 
      }

    }
 
  }

  feature("Test personal CRUD Records.") {
    scenario("User1 Create System  Foobar, user1 and user2 both CRUD their own myFooBars. ", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, CanCreateSystemLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, CanGetSystemLevelDynamicEntities.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanCreateDynamicEntity_SystemFooBar")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, "CanGetDynamicEntity_SystemFooBar")
      When("We make a request v4.0.0")
      val requestSystemLevel = (v4_0_0_Request / "management" / "system-dynamic-entities").POST <@ (user1)

      val foobarObject = parse("""{  "name":"James Brown",  "number":698761728}""".stripMargin)
      val foobarUpdateObject = parse("""{  "name":"James Brown123",  "number":698761728}""".stripMargin)

      val responseSystemLevel = makePostRequest(requestSystemLevel, write(rightEntity))
      Then("We should get a 201")
      responseSystemLevel.code should equal(201)

      Then("User1 and User2 both create system FooBar, and User2 can get 2 foobars")

      val requestCreateFoobarUser1 = (dynamicEntity_Request / "FooBar").POST <@ (user1)
      val responseCreateFoobarUser1 = makePostRequest(requestCreateFoobarUser1, write(foobarObject))
      responseCreateFoobarUser1.code should equal(201)

      val requestCreateFoobarUser2 = (dynamicEntity_Request / "FooBar").POST <@ (user2)
      val responseCreateFoobarUser2 = makePostRequest(requestCreateFoobarUser2, write(foobarObject))
      responseCreateFoobarUser2.code should equal(201)

      val requestGetFoobars = (dynamicEntity_Request / "FooBar").GET <@ (user2)
      val responseGetFoobars = makeGetRequest(requestGetFoobars)
      responseGetFoobars.code should equal(200)

      (responseGetFoobars.body \ "foo_bar_list").asInstanceOf[JArray].arr.size should be(2)

      {
        Then("user1 CURD the myFooBar")
        val requestCreateFoobar = (dynamicEntity_Request / "my" / "FooBar").POST <@ (user1)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
        val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s

        val requestGetFoobars = (dynamicEntity_Request / "my" / "FooBar").GET <@ (user1)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)

        val requestGetFoobar = (dynamicEntity_Request / "my" / "FooBar" / dynamicEntityId).GET <@ (user1)
        val responseGetFoobar = makeGetRequest(requestGetFoobar)
        responseGetFoobar.code should equal(200)

        val requestUpdateFoobar = (dynamicEntity_Request / "my" / "FooBar" / dynamicEntityId).PUT <@ (user1)
        val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
        responseUpdateFoobar.code should equal(200)
        val responseUpdateFoobarName = (responseUpdateFoobar.body \ "foo_bar" \ "name").asInstanceOf[JString].s
        responseUpdateFoobarName should equal("James Brown123")

        val requestDeleteFoobar = (dynamicEntity_Request / "my" / "FooBar" / dynamicEntityId).DELETE <@ (user1)
        val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
        responseDeleteFoobar.code should equal(200)
      }

      {
        Then("user2 CURD the myFooBar")
        val requestCreateFoobar = (dynamicEntity_Request / "my" / "FooBar").POST <@ (user2)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
        val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s

        val requestGetFoobars = (dynamicEntity_Request / "my" / "FooBar").GET <@ (user2)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)

        val requestGetFoobar = (dynamicEntity_Request / "my" / "FooBar" / dynamicEntityId).GET <@ (user2)
        val responseGetFoobar = makeGetRequest(requestGetFoobar)
        responseGetFoobar.code should equal(200)

        val requestUpdateFoobar = (dynamicEntity_Request / "my" / "FooBar" / dynamicEntityId).PUT <@ (user2)
        val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
        responseUpdateFoobar.code should equal(200)
        val responseUpdateFoobarName = (responseUpdateFoobar.body \ "foo_bar" \ "name").asInstanceOf[JString].s
        responseUpdateFoobarName should equal("James Brown123")

        val requestDeleteFoobar = (dynamicEntity_Request / "my" / "FooBar" / dynamicEntityId).DELETE <@ (user2)
        val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
        responseDeleteFoobar.code should equal(200)
      }

      {
        Then("user1 Create the myFooBar")
        val requestCreateFoobar = (dynamicEntity_Request / "my" / "FooBar").POST <@ (user1)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
      }

      {
        Then("user2 Create the myFooBar")
        val requestCreateFoobar = (dynamicEntity_Request / "my" / "FooBar").POST <@ (user2)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
      }

      {
        Then("User1 get my foobar, only return his own records, only one")
        val requestGetFoobars = (dynamicEntity_Request / "my" / "FooBar").GET <@ (user1)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)

        (responseGetFoobars.body \ "foo_bar_list").asInstanceOf[JArray].arr.size should be(1)
      }
      {
        Then("User2 get my foobar, only return his own records, only one")
        val requestGetFoobars = (dynamicEntity_Request / "my" / "FooBar").GET <@ (user2)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)

        (responseGetFoobars.body \ "foo_bar_list").asInstanceOf[JArray].arr.size should be(1)
      }

      {
        Then("User1 get system foobar, return 2 system records")
        val requestGetFoobars = (dynamicEntity_Request / "FooBar").GET <@ (user1)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)

        (responseGetFoobars.body \ "foo_bar_list").asInstanceOf[JArray].arr.size should be(2)
      }

    }

    scenario("User1 Create Bank Foobar, user1 and user2 both CRUD their own myFooBars.", ApiEndpoint8, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanGetBankLevelDynamicEntities.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser2.userId, "CanCreateDynamicEntity_FooBar")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser2.userId, "CanGetDynamicEntity_FooBar")
      When("We make a request v4.0.0")
      val requestSystemLevel = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities").POST <@ (user1)

      val responseSystemLevel = makePostRequest(requestSystemLevel, write(rightEntity))
      Then("We should get a 201")
      responseSystemLevel.code should equal(201)

      Then("User1 and User2 both create bank FooBar, and User2 can get 2 foobars")

      {
        val requestCreateFoobar = (dynamicEntity_Request / "banks" / testBankId1.value / "FooBar").POST <@ (user1)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
      }

      {
        val requestCreateFoobar = (dynamicEntity_Request / "banks" / testBankId1.value / "FooBar").POST <@ (user2)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)

        val requestGetFoobars = (dynamicEntity_Request / "banks" / testBankId1.value / "FooBar").GET <@ (user2)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)

        (responseGetFoobars.body \ "foo_bar_list").asInstanceOf[JArray].arr.size should be(2)
      }

      {
        Then("user1 CURD the myFooBar")
        val requestCreateFoobar = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar").POST <@ (user1)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
        val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s

        val requestGetFoobars = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar").GET <@ (user1)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)

        val requestGetFoobar = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar" / dynamicEntityId).GET <@ (user1)
        val responseGetFoobar = makeGetRequest(requestGetFoobar)
        responseGetFoobar.code should equal(200)

        val requestUpdateFoobar = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar" / dynamicEntityId).PUT <@ (user1)
        val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
        responseUpdateFoobar.code should equal(200)
        val responseUpdateFoobarName = (responseUpdateFoobar.body \ "foo_bar" \ "name").asInstanceOf[JString].s
        responseUpdateFoobarName should equal("James Brown123")

        val requestDeleteFoobar = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar" / dynamicEntityId).DELETE <@ (user1)
        val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
        responseDeleteFoobar.code should equal(200)
      }

      {
        Then("user2 CURD the myFooBar")
        val requestCreateFoobar = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar").POST <@ (user2)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
        val dynamicEntityId = (responseCreateFoobar.body \ "foo_bar" \ "foo_bar_id").asInstanceOf[JString].s

        val requestGetFoobars = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar").GET <@ (user2)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)

        val requestGetFoobar = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar" / dynamicEntityId).GET <@ (user2)
        val responseGetFoobar = makeGetRequest(requestGetFoobar)
        responseGetFoobar.code should equal(200)

        val requestUpdateFoobar = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar" / dynamicEntityId).PUT <@ (user2)
        val responseUpdateFoobar = makePutRequest(requestUpdateFoobar, write(foobarUpdateObject))
        responseUpdateFoobar.code should equal(200)
        val responseUpdateFoobarName = (responseUpdateFoobar.body \ "foo_bar" \ "name").asInstanceOf[JString].s
        responseUpdateFoobarName should equal("James Brown123")

        val requestDeleteFoobar = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar" / dynamicEntityId).DELETE <@ (user2)
        val responseDeleteFoobar = makeDeleteRequest(requestDeleteFoobar)
        responseDeleteFoobar.code should equal(200)
      }

      {
        Then("user1 Create the myFooBar")
        val requestCreateFoobar = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar").POST <@ (user1)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
      }

      {
        Then("user2 Create the myFooBar")
        val requestCreateFoobar = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar").POST <@ (user2)
        val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
        responseCreateFoobar.code should equal(201)
      }

      {
        Then("User1 get my foobar, only return his own records, only one")
        val requestGetFoobars = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar").GET <@ (user1)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)

        (responseGetFoobars.body \ "foo_bar_list").asInstanceOf[JArray].arr.size should be(1)
      }
      {
        Then("User2 get my foobar, only return his own records, only one")
        val requestGetFoobars = (dynamicEntity_Request / "banks" / testBankId1.value / "my" / "FooBar").GET <@ (user2)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)

        (responseGetFoobars.body \ "foo_bar_list").asInstanceOf[JArray].arr.size should be(1)
      }

      {
        Then("User1 get system foobar, return 2 system records")
        val requestGetFoobars = (dynamicEntity_Request / "banks" / testBankId1.value / "FooBar").GET <@ (user1)
        val responseGetFoobars = makeGetRequest(requestGetFoobars)
        responseGetFoobars.code should equal(200)

        (responseGetFoobars.body \ "foo_bar_list").asInstanceOf[JArray].arr.size should be(2)
      }
    }

    scenario("User1 Create System Level Foobar and set hasPersonalEntity = false, then there will be no my endpoints at all" , ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
      When("We make a request v4.0.0")
      val requestSystemLevel = (v4_0_0_Request / "management" / "system-dynamic-entities").POST <@ (user1)

      val hasPersonalEntityFalse = parse(
        """
          |{
          |    "hasPersonalEntity": false,
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

      val responseSystemLevel = makePostRequest(requestSystemLevel, write(hasPersonalEntityFalse))
      Then("We should get a 201")
      responseSystemLevel.code should equal(201)

      val requestCreateFoobar = (dynamicEntity_Request / "my" / "FooBar").POST <@ (user1)
      val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
      responseCreateFoobar.code should equal(404)
      responseCreateFoobar.body.toString contains (s"$InvalidUri") should be (true)
    }
    
    scenario("User1 Create Bank Level Foobar and set hasPersonalEntity = false, then there will be no my endpoints at all" , ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
      When("We make a request v4.0.0")
      val requestSystemLevel = (v4_0_0_Request / "management" / "banks" / testBankId1.value / "dynamic-entities").POST <@ (user1)

      val hasPersonalEntityFalse = parse(
        """
          |{
          |     "hasPersonalEntity": false,
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
      
      val responseSystemLevel = makePostRequest(requestSystemLevel, write(hasPersonalEntityFalse))
      Then("We should get a 201")
      responseSystemLevel.code should equal(201)

      val requestCreateFoobar = (dynamicEntity_Request/ "banks" / testBankId1.value / "my" / "FooBar").POST <@ (user1)
      val responseCreateFoobar = makePostRequest(requestCreateFoobar, write(foobarObject))
      responseCreateFoobar.code should equal(404)
      responseCreateFoobar.body.toString contains (s"$InvalidUri") should be (true)
    }

  }

}
