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
package code.api.v3_1_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import code.webuiprops.WebUiPropsCommons
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class WebUiPropsTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createWebUiProps))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getWebUiProps))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.deleteWebUiProps))

  val rightEntity = WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com")
  val wrongEntity = WebUiPropsCommons("hello_api_explorer_url", "https://apiexplorer.openbankproject.com") // name not start with "webui_"


  feature("Add a WebUiProps v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "webui_props").POST
      val response310 = makePostRequest(request310, write(rightEntity))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }

  feature("Get WebUiPropss v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "webui_props").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Delete the WebUiProps specified by METHOD_ROUTING_ID v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "webui_props" / "WEB_UI_PROPS_ID").DELETE
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }


  feature("Add a WebUiProps v3.1.0 - Unauthorized access - Authorized access") {
    scenario("We will call the endpoint without the proper Role " + canCreateWebUiProps, ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + canCreateTaxResidence)
      val request310 = (v3_1_0_Request / "management" / "webui_props").POST <@(user1)
      val response310 = makePostRequest(request310, write(rightEntity))
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateWebUiProps)
      response310.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanCreateWebUiProps)
    }

    scenario("We will call the endpoint with the proper Role " + canCreateWebUiProps , ApiEndpoint1, ApiEndpoint2, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "webui_props").POST <@(user1)
      val response310 = makePostRequest(request310, write(rightEntity))
      Then("We should get a 201")
      response310.code should equal(201)
      val customerJson = response310.body.extract[WebUiPropsCommons]

      {
        // create a WebUiProps with wrong name
        val request310 = (v3_1_0_Request / "management" / "webui_props").POST <@(user1)
        val response310 = makePostRequest(request310, write(wrongEntity))
        Then("We should get a 400")
        response310.code should equal(400)
        response310.body.extract[ErrorMessage].message should startWith (InvalidWebUiProps)
      }

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetWebUiProps.toString)
      When("We make a request v3.1.0 with the Role " + canGetWebUiProps)
      val requestGet310 = ((v3_1_0_Request / "management" / "webui_props").GET <@(user1))
      val responseGet310 = makeGetRequest(requestGet310)
      Then("We should get a 200")
      responseGet310.code should equal(200)
      val json = responseGet310.body \ "webui_props"
      val webUiPropssGetJson = json.extract[List[WebUiPropsCommons]]

      webUiPropssGetJson.size should be (1)

      val requestGet310AddedQueryParameter =  requestGet310.addQueryParameter("active", "true")
      val responseGet310AddedQueryParameter = makeGetRequest(requestGet310AddedQueryParameter)
      Then("We should get a 200")
      responseGet310AddedQueryParameter.code should equal(200)
      val responseJson = responseGet310AddedQueryParameter.body \ "webui_props"
      val responseGet310AddedQueryParameterJson = responseJson.extract[List[WebUiPropsCommons]]
      responseGet310AddedQueryParameterJson.size >1 should be (true)

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteWebUiProps.toString)
      When("We make a request v3.1.0 with the Role " + canDeleteWebUiProps)
      val requestDelete310 = (v3_1_0_Request / "management" / "webui_props" / webUiPropssGetJson.head.webUiPropsId.get).DELETE <@(user1)
      val responseDelete310 = makeDeleteRequest(requestDelete310)
      Then("We should get a 200")
      responseDelete310.code should equal(200)

    }
  }


}
