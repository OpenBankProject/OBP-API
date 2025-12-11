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
  object ApiEndpoint extends Tag(nameOf(Implementations6_0_0.getWebUiProp))

  val rightEntity = WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com")
  val anotherEntity = WebUiPropsCommons("webui_api_manager_url", "https://apimanager.openbankproject.com")
  val wrongEntity = WebUiPropsCommons("hello_api_explorer_url", "https://apiexplorer.openbankproject.com") // name not start with "webui_"

  
  feature("Get Single WebUiProp by Name v6.0.0") {
    
    scenario("Get WebUiProp - successful case with explicit prop from database", VersionOfApi, ApiEndpoint) {
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

    scenario("Get WebUiProp - successful case with active=true returns explicit prop", VersionOfApi, ApiEndpoint) {
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

    scenario("Get WebUiProp - not found without active flag", VersionOfApi, ApiEndpoint) {
      When("We get a non-existent webui prop by name without active flag")
      val requestGet = (v6_0_0_Request / "webui-props" / "webui_non_existent_prop").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 400")
      responseGet.code should equal(400)
      val error = responseGet.body.extract[ErrorMessage]
      error.message should include(WebUiPropsNotFoundByName)
    }

    scenario("Get WebUiProp - with active=true returns implicit prop from config", VersionOfApi, ApiEndpoint) {
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

    scenario("Get WebUiProp - invalid active parameter", VersionOfApi, ApiEndpoint) {
      When("We get a webui prop with invalid active parameter")
      val requestGet = (v6_0_0_Request / "webui-props" / "webui_api_explorer_url").GET.addQueryParameter("active", "invalid")
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 400")
      responseGet.code should equal(400)
      val error = responseGet.body.extract[ErrorMessage]
      error.message should include(InvalidFilterParameterFormat)
    }

    scenario("Get WebUiProp - database prop takes precedence over config prop when active=true", VersionOfApi, ApiEndpoint) {
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
  
}