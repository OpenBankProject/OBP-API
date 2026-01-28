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
package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v6_0_0.OBPAPI6_0_0.Implementations6_0_0
import code.entitlement.Entitlement
import code.webuiprops.WebUiPropsCommons
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.JsonAST.JNothing
import net.liftweb.json.Serialization.write
import org.scalatest.Tag


class WebUiPropsTest extends V600ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getWebUiProp))
  object ApiEndpoint2 extends Tag(nameOf(Implementations6_0_0.createOrUpdateWebUiProps))
  object ApiEndpoint3 extends Tag(nameOf(Implementations6_0_0.deleteWebUiProps))

  val rightEntity = WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com")
  val anotherEntity = WebUiPropsCommons("webui_api_manager_url", "https://apimanager.openbankproject.com")
  val wrongEntity = WebUiPropsCommons("hello_api_explorer_url", "https://apiexplorer.openbankproject.com") // name not start with "webui_"

  
  feature("Get Single WebUiProp by Name v6.0.0") {
    
    scenario("Get WebUiProp - successful case with explicit prop from database", VersionOfApi, ApiEndpoint1) {
      // First create a webui prop
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      When("We create a webui prop")
      val requestCreate = (v6_0_0_Request / "management" / "webui_props").POST <@(user1)
      val responseCreate = makePostRequest(requestCreate, write(rightEntity))
      Then("We should get a 201")
      responseCreate.code should equal(201)
      
      When("We get the webui prop by name without active flag")
      val requestGet = (v6_0_0_Request / "webui-props" / rightEntity.name).GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
      val webUiPropJson = responseGet.body.extract[WebUiPropsCommons]
      webUiPropJson.name should equal(rightEntity.name)
      webUiPropJson.value should equal(rightEntity.value)
    }

    scenario("Get WebUiProp - successful case with active=true returns explicit prop", VersionOfApi, ApiEndpoint1) {
      // First create a webui prop
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      When("We create a webui prop")
      val requestCreate = (v6_0_0_Request / "management" / "webui_props").POST <@(user1)
      val responseCreate = makePostRequest(requestCreate, write(anotherEntity))
      Then("We should get a 201")
      responseCreate.code should equal(201)
      
      When("We get the webui prop by name with active=true")
      val requestGet = (v6_0_0_Request / "webui-props" / anotherEntity.name).GET.addQueryParameter("active", "true")
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
      val webUiPropJson = responseGet.body.extract[WebUiPropsCommons]
      webUiPropJson.name should equal(anotherEntity.name)
      webUiPropJson.value should equal(anotherEntity.value)
    }

    scenario("Get WebUiProp - not found without active flag", VersionOfApi, ApiEndpoint1) {
      When("We get a non-existent webui prop by name without active flag")
      val requestGet = (v6_0_0_Request / "webui-props" / "webui_non_existent_prop").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 400")
      responseGet.code should equal(400)
      val error = responseGet.body.extract[ErrorMessage]
      error.message should include(WebUiPropsNotFoundByName)
    }

    scenario("Get WebUiProp - with active=true returns implicit prop from config", VersionOfApi, ApiEndpoint1) {
      // Test that we can get implicit props from sample.props.template when active=true
      When("We get a webui prop by name with active=true that exists in config but not in DB")
      // Use a prop that should exist in sample.props.template like webui_sandbox_introduction
      val requestGet = (v6_0_0_Request / "webui-props" / "webui_sandbox_introduction").GET.addQueryParameter("active", "true")
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200 with implicit prop")
      responseGet.code should equal(200)
      val webUiPropJson = responseGet.body.extract[WebUiPropsCommons]
      webUiPropJson.name should equal("webui_sandbox_introduction")
      webUiPropJson.webUiPropsId should equal(Some("default"))
    }

    scenario("Get WebUiProp - invalid active parameter", VersionOfApi, ApiEndpoint1) {
      When("We get a webui prop with invalid active parameter")
      val requestGet = (v6_0_0_Request / "webui-props" / "webui_api_explorer_url").GET.addQueryParameter("active", "invalid")
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 400")
      responseGet.code should equal(400)
      val error = responseGet.body.extract[ErrorMessage]
      error.message should include(InvalidFilterParameterFormat)
    }

    scenario("Get WebUiProp - database prop takes precedence over config prop when active=true", VersionOfApi, ApiEndpoint1) {
      // Create a webui prop that overrides a config value
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      val customValue = WebUiPropsCommons("webui_get_started_text", "Custom Get Started Text")
      When("We create a webui prop that overrides config")
      val requestCreate = (v6_0_0_Request / "management" / "webui_props").POST <@(user1)
      val responseCreate = makePostRequest(requestCreate, write(customValue))
      Then("We should get a 201")
      responseCreate.code should equal(201)
      
      When("We get the webui prop with active=true")
      val requestGet = (v6_0_0_Request / "webui-props" / customValue.name).GET.addQueryParameter("active", "true")
      val responseGet = makeGetRequest(requestGet)
      Then("We should get the database value, not the config value")
      responseGet.code should equal(200)
      val webUiPropJson = responseGet.body.extract[WebUiPropsCommons]
      webUiPropJson.name should equal(customValue.name)
      webUiPropJson.value should equal(customValue.value)
      webUiPropJson.webUiPropsId should not equal(Some("default"))
    }
  }

  feature("Create or Update WebUiProp (PUT) v6.0.0") {
    
    scenario("PUT WebUiProp - create new property successfully", VersionOfApi, ApiEndpoint2) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      When("We create a new webui prop using PUT")
      val putValue = """{"value": "https://new-api-explorer.com"}"""
      val requestPut = (v6_0_0_Request / "management" / "webui_props" / "webui_test_new_prop").PUT <@(user1)
      val responsePut = makePutRequest(requestPut, putValue)
      Then("We should get a 201 Created")
      responsePut.code should equal(201)
      val webUiProp = responsePut.body.extract[WebUiPropsCommons]
      webUiProp.name should equal("webui_test_new_prop")
      webUiProp.value should equal("https://new-api-explorer.com")
      webUiProp.webUiPropsId.isDefined should equal(true)
    }

    scenario("PUT WebUiProp - update existing property successfully", VersionOfApi, ApiEndpoint2) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      When("We create a webui prop")
      val putValue1 = """{"value": "original value"}"""
      val requestPut1 = (v6_0_0_Request / "management" / "webui_props" / "webui_test_update_prop").PUT <@(user1)
      val responsePut1 = makePutRequest(requestPut1, putValue1)
      Then("We should get a 201 Created")
      responsePut1.code should equal(201)
      
      When("We update the same webui prop")
      val putValue2 = """{"value": "updated value"}"""
      val requestPut2 = (v6_0_0_Request / "management" / "webui_props" / "webui_test_update_prop").PUT <@(user1)
      val responsePut2 = makePutRequest(requestPut2, putValue2)
      Then("We should get a 200 OK")
      responsePut2.code should equal(200)
      val webUiProp = responsePut2.body.extract[WebUiPropsCommons]
      webUiProp.name should equal("webui_test_update_prop")
      webUiProp.value should equal("updated value")
    }

    scenario("PUT WebUiProp - idempotent create (same value twice)", VersionOfApi, ApiEndpoint2) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      val putValue = """{"value": "idempotent value"}"""
      
      When("We create a webui prop")
      val requestPut1 = (v6_0_0_Request / "management" / "webui_props" / "webui_test_idempotent").PUT <@(user1)
      val responsePut1 = makePutRequest(requestPut1, putValue)
      Then("We should get a 201 Created")
      responsePut1.code should equal(201)
      val webUiPropsId1 = responsePut1.body.extract[WebUiPropsCommons].webUiPropsId
      
      When("We PUT the same value again")
      val requestPut2 = (v6_0_0_Request / "management" / "webui_props" / "webui_test_idempotent").PUT <@(user1)
      val responsePut2 = makePutRequest(requestPut2, putValue)
      Then("We should get a 200 OK with same ID")
      responsePut2.code should equal(200)
      val webUiPropsId2 = responsePut2.body.extract[WebUiPropsCommons].webUiPropsId
      webUiPropsId1 should equal(webUiPropsId2)
    }

    scenario("PUT WebUiProp - name converted to lowercase", VersionOfApi, ApiEndpoint2) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      When("We create a webui prop with UPPERCASE name")
      val putValue = """{"value": "test value"}"""
      val requestPut = (v6_0_0_Request / "management" / "webui_props" / "WEBUI_UPPERCASE_TEST").PUT <@(user1)
      val responsePut = makePutRequest(requestPut, putValue)
      Then("We should get a 201 and name should be lowercase")
      responsePut.code should equal(201)
      val webUiProp = responsePut.body.extract[WebUiPropsCommons]
      webUiProp.name should equal("webui_uppercase_test")
    }

    scenario("PUT WebUiProp - dot allowed in name", VersionOfApi, ApiEndpoint2) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      When("We create a webui prop with dots in name")
      val putValue = """{"value": "https://api.v1.example.com"}"""
      val requestPut = (v6_0_0_Request / "management" / "webui_props" / "webui_api.v1.endpoint").PUT <@(user1)
      val responsePut = makePutRequest(requestPut, putValue)
      Then("We should get a 201")
      responsePut.code should equal(201)
      val webUiProp = responsePut.body.extract[WebUiPropsCommons]
      webUiProp.name should equal("webui_api.v1.endpoint")
    }

    scenario("PUT WebUiProp - fail without webui_ prefix", VersionOfApi, ApiEndpoint2) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      When("We create a webui prop without webui_ prefix")
      val putValue = """{"value": "test value"}"""
      val requestPut = (v6_0_0_Request / "management" / "webui_props" / "invalid_name").PUT <@(user1)
      val responsePut = makePutRequest(requestPut, putValue)
      Then("We should get a 400")
      responsePut.code should equal(400)
      val error = responsePut.body.extract[ErrorMessage]
      error.message should include(InvalidWebUiProps)
      error.message should include("must start with webui_")
    }

    scenario("PUT WebUiProp - fail with hyphen in name", VersionOfApi, ApiEndpoint2) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      When("We create a webui prop with hyphen")
      val putValue = """{"value": "test value"}"""
      val requestPut = (v6_0_0_Request / "management" / "webui_props" / "webui_api-explorer").PUT <@(user1)
      val responsePut = makePutRequest(requestPut, putValue)
      Then("We should get a 400")
      responsePut.code should equal(400)
      val error = responsePut.body.extract[ErrorMessage]
      error.message should include(InvalidWebUiProps)
      error.message should include("alphanumeric characters, underscore, and dot")
    }

    scenario("PUT WebUiProp - fail with space in name", VersionOfApi, ApiEndpoint2) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      When("We create a webui prop with space")
      val putValue = """{"value": "test value"}"""
      val requestPut = (v6_0_0_Request / "management" / "webui_props" / "webui_invalid name").PUT <@(user1)
      val responsePut = makePutRequest(requestPut, putValue)
      Then("We should get a 400")
      responsePut.code should equal(400)
      val error = responsePut.body.extract[ErrorMessage]
      error.message should include(InvalidWebUiProps)
    }

    scenario("PUT WebUiProp - fail without authentication", VersionOfApi, ApiEndpoint2) {
      When("We try to PUT without authentication")
      val putValue = """{"value": "test value"}"""
      val requestPut = (v6_0_0_Request / "management" / "webui_props" / "webui_test_noauth").PUT
      val responsePut = makePutRequest(requestPut, putValue)
      Then("We should get a 401")
      responsePut.code should equal(401)
    }

    scenario("PUT WebUiProp - fail without CanCreateWebUiProps role", VersionOfApi, ApiEndpoint2) {
      When("We try to PUT without proper role")
      val putValue = """{"value": "test value"}"""
      val requestPut = (v6_0_0_Request / "management" / "webui_props" / "webui_test_norole").PUT <@(user1)
      val responsePut = makePutRequest(requestPut, putValue)
      Then("We should get a 403")
      responsePut.code should equal(403)
      val error = responsePut.body.extract[ErrorMessage]
      error.message should include(UserHasMissingRoles)
    }

    scenario("PUT WebUiProp - fail with invalid JSON body", VersionOfApi, ApiEndpoint2) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      When("We PUT with invalid JSON")
      val putValue = """{"invalid": "no value field"}"""
      val requestPut = (v6_0_0_Request / "management" / "webui_props" / "webui_test_invalid_json").PUT <@(user1)
      val responsePut = makePutRequest(requestPut, putValue)
      Then("We should get a 400")
      responsePut.code should equal(400)
      val error = responsePut.body.extract[ErrorMessage]
      error.message should include(InvalidJsonFormat)
    }

    scenario("PUT WebUiProp - fail with name exceeding 255 characters", VersionOfApi, ApiEndpoint2) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      When("We create a webui prop with name exceeding 255 chars")
      val longName = "webui_" + ("a" * 250) // 256 chars total
      val putValue = """{"value": "test value"}"""
      val requestPut = (v6_0_0_Request / "management" / "webui_props" / longName).PUT <@(user1)
      val responsePut = makePutRequest(requestPut, putValue)
      Then("We should get a 400")
      responsePut.code should equal(400)
      val error = responsePut.body.extract[ErrorMessage]
      error.message should include(InvalidWebUiProps)
      error.message should include("255 characters")
    }
  }

  feature("Delete WebUiProp (DELETE) v6.0.0") {
    
    scenario("DELETE WebUiProp - delete existing property successfully", VersionOfApi, ApiEndpoint3) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteWebUiProps.toString)
      
      When("We create a webui prop")
      val putValue = """{"value": "to be deleted"}"""
      val requestPut = (v6_0_0_Request / "management" / "webui_props" / "webui_test_delete").PUT <@(user1)
      val responsePut = makePutRequest(requestPut, putValue)
      Then("We should get a 201")
      responsePut.code should equal(201)
      
      When("We delete the webui prop")
      val requestDelete = (v6_0_0_Request / "management" / "webui_props" / "webui_test_delete").DELETE <@(user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 204 No Content")
      responseDelete.code should equal(204)
      // HTTP 204 No Content should have empty body
      responseDelete.body shouldBe(JNothing)
    }

    scenario("DELETE WebUiProp - idempotent delete (delete twice)", VersionOfApi, ApiEndpoint3) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteWebUiProps.toString)
      
      When("We create a webui prop")
      val putValue = """{"value": "to be deleted twice"}"""
      val requestPut = (v6_0_0_Request / "management" / "webui_props" / "webui_test_delete_twice").PUT <@(user1)
      val responsePut = makePutRequest(requestPut, putValue)
      responsePut.code should equal(201)
      
      When("We delete the webui prop first time")
      val requestDelete1 = (v6_0_0_Request / "management" / "webui_props" / "webui_test_delete_twice").DELETE <@(user1)
      val responseDelete1 = makeDeleteRequest(requestDelete1)
      Then("We should get a 204")
      responseDelete1.code should equal(204)
      
      When("We delete the same webui prop again (idempotent)")
      val requestDelete2 = (v6_0_0_Request / "management" / "webui_props" / "webui_test_delete_twice").DELETE <@(user1)
      val responseDelete2 = makeDeleteRequest(requestDelete2)
      Then("We should still get a 204")
      responseDelete2.code should equal(204)
    }

    scenario("DELETE WebUiProp - delete non-existent property (idempotent)", VersionOfApi, ApiEndpoint3) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteWebUiProps.toString)
      When("We delete a non-existent webui prop")
      val requestDelete = (v6_0_0_Request / "management" / "webui_props" / "webui_never_existed").DELETE <@(user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 204 (idempotent)")
      responseDelete.code should equal(204)
    }

    scenario("DELETE WebUiProp - name converted to lowercase", VersionOfApi, ApiEndpoint3) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteWebUiProps.toString)
      
      When("We create a webui prop with lowercase name")
      val putValue = """{"value": "test value"}"""
      val requestPut = (v6_0_0_Request / "management" / "webui_props" / "webui_delete_uppercase").PUT <@(user1)
      val responsePut = makePutRequest(requestPut, putValue)
      responsePut.code should equal(201)
      
      When("We delete using UPPERCASE name")
      val requestDelete = (v6_0_0_Request / "management" / "webui_props" / "WEBUI_DELETE_UPPERCASE").DELETE <@(user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 204 (lowercase conversion works)")
      responseDelete.code should equal(204)
    }

    scenario("DELETE WebUiProp - fail without webui_ prefix", VersionOfApi, ApiEndpoint3) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteWebUiProps.toString)
      When("We try to delete with invalid name")
      val requestDelete = (v6_0_0_Request / "management" / "webui_props" / "invalid_name").DELETE <@(user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 400")
      responseDelete.code should equal(400)
      val error = responseDelete.body.extract[ErrorMessage]
      error.message should include(InvalidWebUiProps)
      error.message should include("must start with webui_")
    }

    scenario("DELETE WebUiProp - fail with hyphen in name", VersionOfApi, ApiEndpoint3) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteWebUiProps.toString)
      When("We try to delete with hyphen in name")
      val requestDelete = (v6_0_0_Request / "management" / "webui_props" / "webui_api-explorer").DELETE <@(user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 400")
      responseDelete.code should equal(400)
      val error = responseDelete.body.extract[ErrorMessage]
      error.message should include(InvalidWebUiProps)
    }

    scenario("DELETE WebUiProp - fail without authentication", VersionOfApi, ApiEndpoint3) {
      When("We try to DELETE without authentication")
      val requestDelete = (v6_0_0_Request / "management" / "webui_props" / "webui_test_noauth").DELETE
      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 401")
      responseDelete.code should equal(401)
    }

    scenario("DELETE WebUiProp - fail without CanDeleteWebUiProps role", VersionOfApi, ApiEndpoint3) {
      When("We try to DELETE without proper role")
      val requestDelete = (v6_0_0_Request / "management" / "webui_props" / "webui_test_norole").DELETE <@(user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 403")
      responseDelete.code should equal(403)
      val error = responseDelete.body.extract[ErrorMessage]
      error.message should include(UserHasMissingRoles)
    }

    scenario("DELETE WebUiProp - complete CRUD workflow", VersionOfApi, ApiEndpoint3) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteWebUiProps.toString)
      
      When("We create a webui prop")
      val putValue1 = """{"value": "initial value"}"""
      val requestPut1 = (v6_0_0_Request / "management" / "webui_props" / "webui_test_crud").PUT <@(user1)
      val responsePut1 = makePutRequest(requestPut1, putValue1)
      Then("We should get a 201")
      responsePut1.code should equal(201)
      
      When("We read the webui prop")
      val requestGet1 = (v6_0_0_Request / "webui-props" / "webui_test_crud").GET
      val responseGet1 = makeGetRequest(requestGet1)
      Then("We should get a 200 with correct value")
      responseGet1.code should equal(200)
      responseGet1.body.extract[WebUiPropsCommons].value should equal("initial value")
      
      When("We update the webui prop")
      val putValue2 = """{"value": "updated value"}"""
      val requestPut2 = (v6_0_0_Request / "management" / "webui_props" / "webui_test_crud").PUT <@(user1)
      val responsePut2 = makePutRequest(requestPut2, putValue2)
      Then("We should get a 200")
      responsePut2.code should equal(200)
      
      When("We read the updated webui prop")
      val requestGet2 = (v6_0_0_Request / "webui-props" / "webui_test_crud").GET
      val responseGet2 = makeGetRequest(requestGet2)
      Then("We should get the updated value")
      responseGet2.code should equal(200)
      responseGet2.body.extract[WebUiPropsCommons].value should equal("updated value")
      
      When("We delete the webui prop")
      val requestDelete = (v6_0_0_Request / "management" / "webui_props" / "webui_test_crud").DELETE <@(user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 204")
      responseDelete.code should equal(204)
      
      When("We try to read the deleted webui prop")
      val requestGet3 = (v6_0_0_Request / "webui-props" / "webui_test_crud").GET
      val responseGet3 = makeGetRequest(requestGet3)
      Then("We should get a 400 (not found in database)")
      responseGet3.code should equal(400)
    }
  }
  
}