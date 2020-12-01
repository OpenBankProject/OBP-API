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

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class SelectionTest extends V400ServerSetup {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createSelection))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getSelections))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getSelection))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.deleteSelection))

  feature("Test the selection endpoints") {
    scenario("We create the selection ", ApiEndpoint1,ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      
      val request = (v4_0_0_Request / "users" /resourceUser1.userId / "selections").POST <@ (user1)

      lazy val postSelectionJson = SwaggerDefinitionsJSON.postSelectionJson400

      val response = makePostRequest(request, write(postSelectionJson))
      Then("We should get a 201")
      response.code should equal(201)

      val selectionJson400 = response.body.extract[SelectionJson400]

      selectionJson400.is_favourites should be (postSelectionJson.is_favourites)
      selectionJson400.is_sharable should be (postSelectionJson.is_sharable)
      selectionJson400.selection_name should be (postSelectionJson.selection_name)
      selectionJson400.user_id should be (resourceUser1.userId)
      selectionJson400.selection_id shouldNot be (null)
      
      
      Then(s"we test the $ApiEndpoint2")
      val requestGet = (v4_0_0_Request / "users" /resourceUser1.userId / "selections").GET <@ (user1)


      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)

      val selectionsJsonGet400 = responseGet.body.extract[List[SelectionJson400]]

      selectionsJsonGet400.length should be (1)
      selectionsJsonGet400.head should be (selectionJson400)


      Then(s"we test the $ApiEndpoint3")
      val requestGetSingle = (v4_0_0_Request / "users" /resourceUser1.userId / "selections" / selectionJson400.selection_id).GET <@ (user1)


      val responseGetSingle = makeGetRequest(requestGetSingle)
      Then("We should get a 200")
      responseGetSingle.code should equal(200)

      val selectionsJsonGetSingle400 = responseGet.body.extract[SelectionJson400]

      selectionsJsonGetSingle400 should be (selectionJson400)

      Then(s"we test the $ApiEndpoint4")
      val requestDelete = (v4_0_0_Request / "users" /resourceUser1.userId / "selections" / selectionJson400.selection_id).DELETE <@ (user1)

      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 204")
      responseDelete.code should equal(204)

      val responseGetAfterDelete = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGetAfterDelete.code should equal(200)

      val selectionsJsonGetAfterDelete = responseGetAfterDelete.body.extract[List[SelectionJson400]]

      selectionsJsonGetAfterDelete.length should be (0)

    }
  }

}
