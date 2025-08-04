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
package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.entitlement.Entitlement
import code.webuiprops.WebUiPropsCommons
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag


class WebUiPropsTest extends V510ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations5_1_0.getWebUiProps))

  val rightEntity = WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com")
  val wrongEntity = WebUiPropsCommons("hello_api_explorer_url", "https://apiexplorer.openbankproject.com") // name not start with "webui_"

  
  feature("Get WebUiPropss v5.1.0 ") {
    
    scenario("successful case", VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateWebUiProps.toString)
      When("We make a request v3.1.0")
      val request510 = (v5_1_0_Request / "management" / "webui_props").POST <@(user1)
      val response510 = makePostRequest(request510, write(rightEntity))
      Then("We should get a 201")
      response510.code should equal(201)
      val customerJson = response510.body.extract[WebUiPropsCommons]
      
      val requestGet510 = (v5_1_0_Request / "webui-props").GET
      val responseGet510 = makeGetRequest(requestGet510)
      Then("We should get a 200")
      responseGet510.code should equal(200)
      val json = responseGet510.body \ "webui_props"
      val webUiPropssGetJson = json.extract[List[WebUiPropsCommons]]

      webUiPropssGetJson.size should be (1)

      val requestGet510AddedQueryParameter =  requestGet510.addQueryParameter("active", "true")
      val responseGet510AddedQueryParameter = makeGetRequest(requestGet510AddedQueryParameter)
      Then("We should get a 200")
      responseGet510AddedQueryParameter.code should equal(200)
      val responseJson = responseGet510AddedQueryParameter.body \ "webui_props"
      val responseGet510AddedQueryParameterJson = responseJson.extract[List[WebUiPropsCommons]]
      responseGet510AddedQueryParameterJson.size >1 should be (true)

    }
  }
  
  
}
