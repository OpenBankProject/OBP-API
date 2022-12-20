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

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.{ApiCollectionJson400, ApiCollectionsJson400}
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class ApiCollectionTest extends V510ServerSetup {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)

  object ApiEndpoint8 extends Tag(nameOf(Implementations5_1_0.getAllApiCollections))

  feature("Test the apiCollection endpoints") {
    scenario("We create the apiCollection get All API collections back", ApiEndpoint8,  VersionOfApi) {
      When("We make a request v4.0.0")
      
      val request = (v5_1_0_Request / "my" / "api-collections").POST <@ (user1)

      lazy val postApiCollectionJson = SwaggerDefinitionsJSON.postApiCollectionJson400
      val response = makePostRequest(request, write(postApiCollectionJson))
      Then("We should get a 201")
      response.code should equal(201)
      val apiCollectionJson400 = response.body.extract[ApiCollectionJson400]
     

      val requestUser2 = (v5_1_0_Request / "my" / "api-collections").POST <@ (user2)
      val responseUser2 = makePostRequest(requestUser2, write(postApiCollectionJson))
      Then("We should get a 201")
      responseUser2.code should equal(201)

      Then(s"we test the $ApiEndpoint8")
      val requestApiEndpoint = (v5_1_0_Request / "management" / "api-collections").GET
      val requestApiEndpoint8 = (v5_1_0_Request /"management" / "api-collections").GET <@ (user1)
      
      val responseApiEndpoint8 = makeGetRequest(requestApiEndpoint)
      Then(s"we should get the error messages")
      responseApiEndpoint8.code should equal(401)
      responseApiEndpoint8.body.toString contains(s"$UserNotLoggedIn") should be (true)

      {
        Then(s"we test the $ApiEndpoint8")
        val responseApiEndpoint8 = makeGetRequest(requestApiEndpoint8)
        Then(s"we should get the error messages")
        responseApiEndpoint8.code should equal(403)
        responseApiEndpoint8.body.toString contains(s"$UserHasMissingRoles") should be (true)
      }
      Then("grant the role and test it again")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetAllApiCollections.toString)
      val responseApiEndpoint8WithRole = makeGetRequest(requestApiEndpoint8)

      Then("We should get a 200")
      responseApiEndpoint8WithRole.code should equal(200)
      val apiCollectionsResponseApiEndpoint8 = responseApiEndpoint8WithRole.body.extract[ApiCollectionsJson400]
      apiCollectionsResponseApiEndpoint8.api_collections.head should be (apiCollectionJson400)
     
    }
  }

}
