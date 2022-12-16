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
import code.api.util.ApiRole
import code.api.util.ErrorMessages.{ApiCollectionEndpointNotFound, UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class ApiCollectionTest extends V400ServerSetup {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createMyApiCollection))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getMyApiCollections))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getMyApiCollectionByName))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.deleteMyApiCollection))
  object ApiEndpoint7 extends Tag(nameOf(Implementations4_0_0.getMyApiCollectionById))
  
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getSharableApiCollectionById))
  object ApiEndpoint6 extends Tag(nameOf(Implementations4_0_0.getApiCollectionsForUser))
  object ApiEndpoint8 extends Tag(nameOf(Implementations4_0_0.getAllApiCollections))

  feature("Test the apiCollection endpoints") {
    scenario("We create my apiCollection and get,delete", ApiEndpoint1,ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint7, ApiEndpoint8, VersionOfApi) {
      When("We make a request v4.0.0")

      {
       //first we test the ApiEndpoint1 without Authentication
        val request = (v4_0_0_Request / "my" / "api-collections").POST
        lazy val postApiCollectionJson = SwaggerDefinitionsJSON.postApiCollectionJson400
        val response = makePostRequest(request, write(postApiCollectionJson))
        Then(s"we should get the error messages")
        response.code should equal(401)
        response.body.toString contains(s"$UserNotLoggedIn") should be (true)
      }
      
      val request = (v4_0_0_Request / "my" / "api-collections").POST <@ (user1)

      lazy val postApiCollectionJson = SwaggerDefinitionsJSON.postApiCollectionJson400

      val response = makePostRequest(request, write(postApiCollectionJson))
      Then("We should get a 201")
      response.code should equal(201)

      val apiCollectionJson400 = response.body.extract[ApiCollectionJson400]

      apiCollectionJson400.is_sharable should be (postApiCollectionJson.is_sharable)
      apiCollectionJson400.api_collection_name should be (postApiCollectionJson.api_collection_name)
      apiCollectionJson400.user_id should be (resourceUser1.userId)
      apiCollectionJson400.api_collection_id shouldNot be (null)
      
      
      Then(s"we test the $ApiEndpoint2")
      val requestGet = (v4_0_0_Request / "my" / "api-collections").GET <@ (user1)

      {
        //then we test the ApiEndpoint2 without Authentication
        val requestGet = (v4_0_0_Request / "my" / "api-collections")
        val responseGet = makeGetRequest(requestGet)
        Then(s"we should get the error messages")
        responseGet.code should equal(401)
        responseGet.body.toString contains(s"$UserNotLoggedIn") should be (true)
      }

      

      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)

      val apiCollectionsJsonGet400 = responseGet.body.extract[ApiCollectionsJson400]

      apiCollectionsJsonGet400.api_collections.length should be (1)
      apiCollectionsJsonGet400.api_collections.head should be (apiCollectionJson400)


      Then(s"we test the $ApiEndpoint3")
      val requestGetSingleByName = (v4_0_0_Request / "my" / "api-collections" / "name" /apiCollectionJson400.api_collection_name).GET <@ (user1)

      {
       //then we test the ApiEndpoint3 without Authentication
        val requestGetSingle = (v4_0_0_Request / "my" / "api-collections"/ "name"  / apiCollectionJson400.api_collection_name).GET
        val responseGetSingle = makeGetRequest(requestGetSingle)
        Then(s"we should get the error messages")
        responseGetSingle.code should equal(401)
        responseGetSingle.body.toString contains(s"$UserNotLoggedIn") should be (true)
      }
      

      val responseGetSingleByName = makeGetRequest(requestGetSingleByName)
      Then("We should get a 200")
      responseGetSingleByName.code should equal(200)

      Then(s"we test the $ApiEndpoint7")
      val requestGetSingle = (v4_0_0_Request / "my" / "api-collections" / apiCollectionJson400.api_collection_id).GET <@ (user1)

      {
        //then we test the ApiEndpoint3 without Authentication
        val requestGetSingle = (v4_0_0_Request / "my" / "api-collections" / apiCollectionJson400.api_collection_id).GET
        val responseGetSingle = makeGetRequest(requestGetSingle)
        Then(s"we should get the error messages")
        responseGetSingle.code should equal(401)
        responseGetSingle.body.toString contains(s"$UserNotLoggedIn") should be (true)
      }


      val responseGetSingle = makeGetRequest(requestGetSingle)
      Then("We should get a 200")
      responseGetSingle.code should equal(200)

      val apiCollectionsJsonGetSingle400 = responseGetSingle.body.extract[ApiCollectionJson400]

      apiCollectionsJsonGetSingle400 should be (apiCollectionJson400)

      Then(s"we test the $ApiEndpoint4")
      val requestDelete = (v4_0_0_Request / "my" / "api-collections" / apiCollectionJson400.api_collection_id).DELETE <@ (user1)

      {
        //then we test the ApiEndpoint4 without Authentication
        val requestDelete = (v4_0_0_Request / "my" / "api-collections" / apiCollectionJson400.api_collection_id).DELETE
        val responseDelete = makeDeleteRequest(requestDelete)
        Then(s"we should get the error messages")
        responseDelete.code should equal(401)
        responseDelete.body.toString contains(s"$UserNotLoggedIn") should be (true)
      }

      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 204")
      responseDelete.code should equal(204)

      val responseGetAfterDelete = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGetAfterDelete.code should equal(200)

      val apiCollectionsJsonGetAfterDelete = responseGetAfterDelete.body.extract[ApiCollectionsJson400]

      apiCollectionsJsonGetAfterDelete.api_collections.length should be (0)
    }
    
    scenario("We create the apiCollection and get sharable api collection",  ApiEndpoint5, ApiEndpoint6, VersionOfApi) {
      When("We make a request v4.0.0")
      
      val request = (v4_0_0_Request / "my" / "api-collections").POST <@ (user1)

      lazy val postApiCollectionJson = SwaggerDefinitionsJSON.postApiCollectionJson400

      val response = makePostRequest(request, write(postApiCollectionJson))
      Then("We should get a 201")
      response.code should equal(201)
      val apiCollectionJson400 = response.body.extract[ApiCollectionJson400]
      
      Then(s"we test the $ApiEndpoint5 without Authentication")
      val requestApiEndpoint5 = (v4_0_0_Request  / "api-collections" / "sharable" / apiCollectionJson400.api_collection_id).GET 

      val responseApiEndpoint5 = makeGetRequest(requestApiEndpoint5)
      responseApiEndpoint5.code should equal(200)
      
      val apiCollectionsJsonApiEndpoint5 = responseApiEndpoint5.body.extract[ApiCollectionJson400]
      apiCollectionsJsonApiEndpoint5 should be (apiCollectionJson400)
      
      {
        //Then we test the api collection but is_sharable == false
        lazy val postApiCollectionJson = SwaggerDefinitionsJSON.postApiCollectionJson400.copy(is_sharable = false,api_collection_name = "newName")

        val response = makePostRequest(request, write(postApiCollectionJson))
        Then("We should get a 201")
        response.code should equal(201)
        val apiCollectionJson400 = response.body.extract[ApiCollectionJson400]

        Then(s"we test the $ApiEndpoint5 without Authentication")
        val requestApiEndpoint5 = (v4_0_0_Request  / "api-collections" / "sharable" / apiCollectionJson400.api_collection_id).GET


        val responseApiEndpoint5 = makeGetRequest(requestApiEndpoint5)
        responseApiEndpoint5.code should equal(400)

       responseApiEndpoint5.body.toString contains 
         (s"$ApiCollectionEndpointNotFound Current api_collection_id(${apiCollectionJson400.api_collection_id}) is not sharable.") shouldBe (true)
      }

      {
        Then(s"we test the $ApiEndpoint6")
        val requestApiEndpoint6 = (v4_0_0_Request / "users" / resourceUser1.userId / "api-collections").GET 
  
        val responseApiEndpoint6 = makeGetRequest(requestApiEndpoint6)
        Then(s"we should get the error messages")
        responseApiEndpoint6.code should equal(401)
        responseApiEndpoint6.body.toString contains(s"$UserNotLoggedIn") should be (true)
      }
      
      Then(s"we test the $ApiEndpoint6")
      val requestApiEndpoint6 = (v4_0_0_Request / "users" / resourceUser1.userId / "api-collections").GET <@ (user1)

      val responseApiEndpoint6 = makeGetRequest(requestApiEndpoint6)
      Then(s"we should get the error messages")
      responseApiEndpoint6.code should equal(403)
      responseApiEndpoint6.body.toString contains(s"$UserHasMissingRoles") should be (true)

      Then("grant the role and test it again")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetAllApiCollectionsForUser.toString)
      val responseApiEndpoint6WithRole = makeGetRequest(requestApiEndpoint6)
      
      Then("We should get a 200")
      responseApiEndpoint6WithRole.code should equal(200)
      val apiCollectionsResponseApiEndpoint6 = responseApiEndpoint6WithRole.body.extract[ApiCollectionsJson400]
      apiCollectionsResponseApiEndpoint6.api_collections.head should be (apiCollectionJson400)

      val requestUser2 = (v4_0_0_Request / "my" / "api-collections").POST <@ (user2)
      val responseUser2 = makePostRequest(requestUser2, write(postApiCollectionJson))
      Then("We should get a 201")
      responseUser2.code should equal(201)

      {
        Then(s"we test the $ApiEndpoint8")
        val requestApiEndpoint8 = (v4_0_0_Request / "management" / "api-collections").GET

        val responseApiEndpoint8 = makeGetRequest(requestApiEndpoint8)
        Then(s"we should get the error messages")
        responseApiEndpoint8.code should equal(401)
        responseApiEndpoint8.body.toString contains(s"$UserNotLoggedIn") should be (true)
      }

      Then(s"we test the $ApiEndpoint8")
      val requestApiEndpoint8 = (v4_0_0_Request /"management" / "api-collections").GET <@ (user1)

      val responseApiEndpoint8 = makeGetRequest(requestApiEndpoint8)
      Then(s"we should get the error messages")
      responseApiEndpoint8.code should equal(403)
      responseApiEndpoint8.body.toString contains(s"$UserHasMissingRoles") should be (true)

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
