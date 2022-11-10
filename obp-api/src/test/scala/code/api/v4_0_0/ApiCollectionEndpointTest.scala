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

class ApiCollectionEndpointTest extends V400ServerSetup {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createMyApiCollectionEndpoint))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getMyApiCollectionEndpoints))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getMyApiCollectionEndpoint))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.deleteMyApiCollectionEndpoint))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getApiCollectionEndpoints))
  object ApiEndpoint6 extends Tag(nameOf(Implementations4_0_0.createMyApiCollectionEndpointById))
  object ApiEndpoint7 extends Tag(nameOf(Implementations4_0_0.getMyApiCollectionEndpointsById))

  feature("Test the apiCollection endpoints") {
    scenario("We create the apiCollection Endpoint", ApiEndpoint1,ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      
      When("First we need to prepare the apiCollection and then test the select endpoints")
      val request = (v4_0_0_Request / "my" / "api-collections").POST <@ (user1)

      lazy val postApiCollectionJson = SwaggerDefinitionsJSON.postApiCollectionJson400

      val response = makePostRequest(request, write(postApiCollectionJson))
      Then("We should get a 201")
      response.code should equal(201)

      val apiCollectionJson400 = response.body.extract[ApiCollectionJson400]

      val apiCollectionName = apiCollectionJson400.api_collection_name
      val apiCollectionId = apiCollectionJson400.api_collection_id

      Then(s"we test the $ApiEndpoint1")
      val requestApiCollectionEndpoint = (v4_0_0_Request / "my" / "api-collections" / apiCollectionName / "api-collection-endpoints").POST <@ (user1)

      lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400

      val responseApiCollectionEndpointJson = makePostRequest(requestApiCollectionEndpoint, write(postApiCollectionEndpointJson))
      Then("We should get a 201")
      responseApiCollectionEndpointJson.code should equal(201)
      val apiCollectionEndpoint = responseApiCollectionEndpointJson.body.extract[ApiCollectionEndpointJson400]

      apiCollectionEndpoint.operation_id should be (postApiCollectionEndpointJson.operation_id)
      apiCollectionEndpoint.api_collection_endpoint_id shouldNot be (null)
      
      val  operationId= apiCollectionEndpoint.operation_id      
      Then(s"we test the $ApiEndpoint2")
      val requestGet = (v4_0_0_Request / "my" / "api-collections" / apiCollectionName / "api-collection-endpoints").GET <@ (user1)

      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)

      val apiCollectionsJsonGet400 = responseGet.body.extract[ApiCollectionEndpointsJson400]

      apiCollectionsJsonGet400.api_collection_endpoints.length should be (1)
      apiCollectionsJsonGet400.api_collection_endpoints.head should be (apiCollectionEndpoint)


      Then(s"we test the $ApiEndpoint3")
      val requestGetSingle = (v4_0_0_Request / "my" / "api-collections" / apiCollectionName / "api-collection-endpoints" /operationId).GET <@ (user1)


      val responseGetSingle = makeGetRequest(requestGetSingle)
      Then("We should get a 200")
      responseGetSingle.code should equal(200)

      val apiCollectionsJsonGetSingle400 = responseGetSingle.body.extract[ApiCollectionEndpointJson400]

      apiCollectionsJsonGetSingle400 should be (apiCollectionEndpoint)


      Then(s"we test the $ApiEndpoint5")
      val request5 = (v4_0_0_Request / "my" / "api-collections" / apiCollectionName / "api-collection-endpoints").GET <@ (user1)


      val response5= makeGetRequest(request5)
      Then("We should get a 200")
      response5.code should equal(200)

      val apiCollectionsJson5 = response5.body.extract[ApiCollectionEndpointsJson400]

      apiCollectionsJson5.api_collection_endpoints.head should be (apiCollectionEndpoint)

      Then(s"we test the $ApiEndpoint4")
      val requestDelete = (v4_0_0_Request / "my" / "api-collections" / apiCollectionName / "api-collection-endpoints" / operationId).DELETE <@ (user1)

      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 204")
      responseDelete.code should equal(204)

      val responseGetAfterDelete = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGetAfterDelete.code should equal(200)

      val apiCollectionEndpointsJsonGetAfterDelete = responseGetAfterDelete.body.extract[ApiCollectionEndpointsJson400]

      apiCollectionEndpointsJsonGetAfterDelete.api_collection_endpoints.length should be (0)

      {
        Then(s"we test the $ApiEndpoint6- OBPv400")
        val requestApiCollectionEndpoint = (v4_0_0_Request / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints").POST <@ (user1)
  
        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id="OBPv4.0.0-getBanks")
  
        val responseApiCollectionEndpointJson = makePostRequest(requestApiCollectionEndpoint, write(postApiCollectionEndpointJson))
        Then("We should get a 201")
        responseApiCollectionEndpointJson.code should equal(201)
        val apiCollectionEndpoint = responseApiCollectionEndpointJson.body.extract[ApiCollectionEndpointJson400]
  
        apiCollectionEndpoint.operation_id should be (postApiCollectionEndpointJson.operation_id)
        apiCollectionEndpoint.api_collection_endpoint_id shouldNot be (null)
  
        val  operationId= apiCollectionEndpoint.operation_id
      }

      {
        Then(s"we test the $ApiEndpoint6- OBPv500")
        val requestApiCollectionEndpoint = (v4_0_0_Request / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints").POST <@ (user1)

        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id="OBPv5.0.0-createCustomer")

        val responseApiCollectionEndpointJson = makePostRequest(requestApiCollectionEndpoint, write(postApiCollectionEndpointJson))
        Then("We should get a 201")
        responseApiCollectionEndpointJson.code should equal(201)
        val apiCollectionEndpoint = responseApiCollectionEndpointJson.body.extract[ApiCollectionEndpointJson400]

        apiCollectionEndpoint.operation_id should be (postApiCollectionEndpointJson.operation_id)
        apiCollectionEndpoint.api_collection_endpoint_id shouldNot be (null)

        val  operationId= apiCollectionEndpoint.operation_id
      }
      
      {
        Then(s"we test the $ApiEndpoint6- UKv3.1-createAccountAccessConsents")
        val requestApiCollectionEndpoint = (v4_0_0_Request / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints").POST <@ (user1)

        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id="UKv3.1-createAccountAccessConsents")

        val responseApiCollectionEndpointJson = makePostRequest(requestApiCollectionEndpoint, write(postApiCollectionEndpointJson))
        Then("We should get a 201")
        responseApiCollectionEndpointJson.code should equal(201)
        val apiCollectionEndpoint = responseApiCollectionEndpointJson.body.extract[ApiCollectionEndpointJson400]

        apiCollectionEndpoint.operation_id should be (postApiCollectionEndpointJson.operation_id)
        apiCollectionEndpoint.api_collection_endpoint_id shouldNot be (null)

        val  operationId= apiCollectionEndpoint.operation_id
      }
      {
        Then(s"we test the $ApiEndpoint6- BGv1.3-getConsentStatus")
        val requestApiCollectionEndpoint = (v4_0_0_Request / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints").POST <@ (user1)

        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id="BGv1.3-getConsentStatus")

        val responseApiCollectionEndpointJson = makePostRequest(requestApiCollectionEndpoint, write(postApiCollectionEndpointJson))
        Then("We should get a 201")
        responseApiCollectionEndpointJson.code should equal(201)
        val apiCollectionEndpoint = responseApiCollectionEndpointJson.body.extract[ApiCollectionEndpointJson400]

        apiCollectionEndpoint.operation_id should be (postApiCollectionEndpointJson.operation_id)
        apiCollectionEndpoint.api_collection_endpoint_id shouldNot be (null)

        val  operationId= apiCollectionEndpoint.operation_id
      }
      {
        Then(s"we test the $ApiEndpoint6- STETv1.4-consentsPut")
        val requestApiCollectionEndpoint = (v4_0_0_Request / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints").POST <@ (user1)

        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id="STETv1.4-consentsPut")

        val responseApiCollectionEndpointJson = makePostRequest(requestApiCollectionEndpoint, write(postApiCollectionEndpointJson))
        Then("We should get a 201")
        responseApiCollectionEndpointJson.code should equal(201)
        val apiCollectionEndpoint = responseApiCollectionEndpointJson.body.extract[ApiCollectionEndpointJson400]

        apiCollectionEndpoint.operation_id should be (postApiCollectionEndpointJson.operation_id)
        apiCollectionEndpoint.api_collection_endpoint_id shouldNot be (null)

        val  operationId= apiCollectionEndpoint.operation_id
      }
      {
        Then(s"we test the $ApiEndpoint6- PAPIv2.1.1.1-getAccount")
        val requestApiCollectionEndpoint = (v4_0_0_Request / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints").POST <@ (user1)

        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id="PAPIv2.1.1.1-getAccount")

        val responseApiCollectionEndpointJson = makePostRequest(requestApiCollectionEndpoint, write(postApiCollectionEndpointJson))
        Then("We should get a 201")
        responseApiCollectionEndpointJson.code should equal(201)
        val apiCollectionEndpoint = responseApiCollectionEndpointJson.body.extract[ApiCollectionEndpointJson400]

        apiCollectionEndpoint.operation_id should be (postApiCollectionEndpointJson.operation_id)
        apiCollectionEndpoint.api_collection_endpoint_id shouldNot be (null)

        val  operationId= apiCollectionEndpoint.operation_id
      }
      {
        Then(s"we test the $ApiEndpoint6- AUv1.0.0-listBalance")
        val requestApiCollectionEndpoint = (v4_0_0_Request / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints").POST <@ (user1)

        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id="AUv1.0.0-listBalance")

        val responseApiCollectionEndpointJson = makePostRequest(requestApiCollectionEndpoint, write(postApiCollectionEndpointJson))
        Then("We should get a 201")
        responseApiCollectionEndpointJson.code should equal(201)
        val apiCollectionEndpoint = responseApiCollectionEndpointJson.body.extract[ApiCollectionEndpointJson400]

        apiCollectionEndpoint.operation_id should be (postApiCollectionEndpointJson.operation_id)
        apiCollectionEndpoint.api_collection_endpoint_id shouldNot be (null)

        val  operationId= apiCollectionEndpoint.operation_id
      }
      {
        Then(s"we test the $ApiEndpoint6- BAHRAIN-OBFv1.0.0-accountAccessConsentsPost")
        val requestApiCollectionEndpoint = (v4_0_0_Request / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints").POST <@ (user1)

        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id="BAHRAIN-OBFv1.0.0-accountAccessConsentsPost")

        val responseApiCollectionEndpointJson = makePostRequest(requestApiCollectionEndpoint, write(postApiCollectionEndpointJson))
        Then("We should get a 201")
        responseApiCollectionEndpointJson.code should equal(201)
        val apiCollectionEndpoint = responseApiCollectionEndpointJson.body.extract[ApiCollectionEndpointJson400]

        apiCollectionEndpoint.operation_id should be (postApiCollectionEndpointJson.operation_id)
        apiCollectionEndpoint.api_collection_endpoint_id shouldNot be (null)

        val  operationId= apiCollectionEndpoint.operation_id
      }
      {
        Then(s"we test the $ApiEndpoint6- CNBV9v1.0.0-getMxAtms")
        val requestApiCollectionEndpoint = (v4_0_0_Request / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints").POST <@ (user1)

        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id="CNBV9v1.0.0-getMxAtms")

        val responseApiCollectionEndpointJson = makePostRequest(requestApiCollectionEndpoint, write(postApiCollectionEndpointJson))
        Then("We should get a 201")
        responseApiCollectionEndpointJson.code should equal(201)
        val apiCollectionEndpoint = responseApiCollectionEndpointJson.body.extract[ApiCollectionEndpointJson400]
  
        apiCollectionEndpoint.operation_id should be (postApiCollectionEndpointJson.operation_id)
        apiCollectionEndpoint.api_collection_endpoint_id shouldNot be (null)
  
        val  operationId= apiCollectionEndpoint.operation_id
      }
      
      {
        Then(s"we test the $ApiEndpoint7")
        val requestGet = (v4_0_0_Request / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints").GET <@ (user1)

        val responseGet = makeGetRequest(requestGet)
        Then("We should get a 200")
        responseGet.code should equal(200)

        val apiCollectionsJsonGet400 = responseGet.body.extract[ApiCollectionEndpointsJson400]

        apiCollectionsJsonGet400.api_collection_endpoints.length should be (9)
      }
    }
  }

}
