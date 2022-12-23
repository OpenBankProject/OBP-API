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
import code.api.util.ErrorMessages.UserNotLoggedIn
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import code.api.v4_0_0.ApiCollectionJson400
import code.api.v5_1_0.APIMethods510.Implementations5_1_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
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
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createMyApiCollection))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getMyApiCollectionById))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_1_0.updateMyApiCollection))

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val request510 = (v5_1_0_Request / "my" / "api-collections").POST
      val response510 = makePostRequest(request510, write(SwaggerDefinitionsJSON.postApiCollectionJson400))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val requestPost510 = (v5_1_0_Request / "my" / "api-collections").POST <@ (user1)
      val postApiCollectionJson = SwaggerDefinitionsJSON.postApiCollectionJson400
      val responsePost510 = makePostRequest(requestPost510, write(postApiCollectionJson))
      Then("We should get a 201")
      responsePost510.code should equal(201)
      val apiCollectionJson400 = responsePost510.body.extract[ApiCollectionJson400]
      apiCollectionJson400.is_sharable should be (postApiCollectionJson.is_sharable)
      apiCollectionJson400.api_collection_name should be (postApiCollectionJson.api_collection_name)
      apiCollectionJson400.user_id should be (resourceUser1.userId)
      apiCollectionJson400.api_collection_id shouldNot be (null)

      // Get
      Then(s"we test the $ApiEndpoint2")
      val requestGetSingle = (v5_1_0_Request / "my" / "api-collections" / apiCollectionJson400.api_collection_id).GET <@ (user1)
      val responseGetSingle = makeGetRequest(requestGetSingle)
      Then("We should get a 200")
      responseGetSingle.code should equal(200)
      val apiCollectionsJsonGetSingle400 = responseGetSingle.body.extract[ApiCollectionJson400]
      apiCollectionsJsonGetSingle400 should be (apiCollectionJson400)

      // Update
      When(s"We make a request $ApiEndpoint3")
      val requestPut510 = (v5_1_0_Request / "my" / "api-collections" / apiCollectionsJsonGetSingle400.api_collection_id).PUT <@ (user1)
      val putApiCollectionJson = SwaggerDefinitionsJSON.postApiCollectionJson400
        .copy(api_collection_name = "whatever")
        .copy(description = Some("whatever"))
      val responsePut510 = makePutRequest(requestPut510, write(putApiCollectionJson))
      Then("We should get a 200")
      responsePut510.code should equal(200)
      val apiCollectionPutJson400 = responsePut510.body.extract[ApiCollectionJson400]
      apiCollectionPutJson400.is_sharable should be (putApiCollectionJson.is_sharable)
      apiCollectionPutJson400.api_collection_name should be (putApiCollectionJson.api_collection_name)

      // Get
      Then(s"we test the $ApiEndpoint2")
      val responseGetSingleSecond = makeGetRequest(requestGetSingle)
      Then("We should get a 200")
      responseGetSingleSecond.code should equal(200)
      val apiCollectionsJsonGetSingleSecond400 = responseGetSingleSecond.body.extract[ApiCollectionJson400]
      apiCollectionsJsonGetSingleSecond400.api_collection_name should be (apiCollectionPutJson400.api_collection_name)
      apiCollectionsJsonGetSingleSecond400.description should be (apiCollectionPutJson400.description)
    }
  }

}
