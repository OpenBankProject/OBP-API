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

import org.json4s._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.berlin.group.v1_3.Http4sBGv13Alias
import code.api.util.APIUtil.OAuth._
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
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
  
        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id="OBPv6.0.0-getBanks")
  
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

        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id="OBPv6.0.0-createCustomer")

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
        // Regression pin for the sandbox bug report: BGv2-getAccountDetails was served by the
        // resource-docs dispatcher (/resource-docs/BGv2/obp) but missing from the global
        // operation-id union getAllResourceDocs relies on, so this exact request used to fail
        // with OBP-40048 Invalid operation_id.
        Then(s"we test the $ApiEndpoint6- BGv2-getAccountDetails")
        val requestApiCollectionEndpoint = (v4_0_0_Request / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints").POST <@ (user1)

        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id="BGv2-getAccountDetails")

        val responseApiCollectionEndpointJson = makePostRequest(requestApiCollectionEndpoint, write(postApiCollectionEndpointJson))
        Then("We should get a 201")
        responseApiCollectionEndpointJson.code should equal(201)
        val apiCollectionEndpoint = responseApiCollectionEndpointJson.body.extract[ApiCollectionEndpointJson400]

        apiCollectionEndpoint.operation_id should be (postApiCollectionEndpointJson.operation_id)
        apiCollectionEndpoint.api_collection_endpoint_id shouldNot be (null)

        val  operationId= apiCollectionEndpoint.operation_id
      }

      // Regression pin for the Berlin Group v1.3 alias gap: when berlin_group_v1_3_alias_path is
      // set (0.6/v1 in test.default.props and in both CI workflows) Http4sBGv13Alias publishes
      // re-stamped copies of the canonical BG v1.3 docs under their own operation ids -- served by
      // the resource-docs dispatcher via ScannedApis discovery, but formerly missing from the
      // global operation-id union, the same class of gap as BGv2 above.
      //
      // Guarded on the alias actually being configured, and the expected id is read back from its
      // own docs rather than hard-coded: test.default.props is gitignored (.gitignore:21), so a
      // fresh clone or an IDE runner may not carry that prop, and a deployment may configure a
      // different path (which changes the id's prefix).
      val aliasOperationId: Option[String] = Http4sBGv13Alias.resourceDocs
        .find(_.partialFunctionName == "getPaymentInitiationStatus").map(_.operationId)

      aliasOperationId.foreach { opId =>
        Then(s"we test the $ApiEndpoint6- $opId (Berlin Group v1.3 alias)")
        val requestApiCollectionEndpoint = (v4_0_0_Request / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints").POST <@ (user1)

        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id = opId)

        val responseApiCollectionEndpointJson = makePostRequest(requestApiCollectionEndpoint, write(postApiCollectionEndpointJson))
        Then("We should get a 201")
        responseApiCollectionEndpointJson.code should equal(201)
        val apiCollectionEndpoint = responseApiCollectionEndpointJson.body.extract[ApiCollectionEndpointJson400]

        apiCollectionEndpoint.operation_id should be (postApiCollectionEndpointJson.operation_id)
        apiCollectionEndpoint.api_collection_endpoint_id shouldNot be (null)
      }

      {
        // Regression pin for the third drift instance: the global operation-id union used to be
        // built from the v6.0.0 aggregation, so operation ids belonging to endpoints that exist
        // ONLY in v7.0.0 (getMyMetrics, getTopUsers, getTopConsumers) were absent from it and
        // could not be added to an API collection either. getMyMetrics is v7-only -- it is not
        // part of Http4sResourceDocAggregation.v600 -- so this pins the v7 base specifically,
        // unlike the OBPv6.0.0-* cases above which passed even under the old v6-based union.
        Then(s"we test the $ApiEndpoint6- OBPv7.0.0-getMyMetrics (v7-only endpoint)")
        val requestApiCollectionEndpoint = (v4_0_0_Request / "my" / "api-collection-ids" / apiCollectionId / "api-collection-endpoints").POST <@ (user1)

        lazy val postApiCollectionEndpointJson = SwaggerDefinitionsJSON.postApiCollectionEndpointJson400.copy(operation_id="OBPv7.0.0-getMyMetrics")

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

        // Six unconditional cases above, plus the Berlin Group v1.3 alias one when that alias is
        // configured for this run.
        val expected = if (aliasOperationId.isDefined) 7 else 6
        apiCollectionsJsonGet400.api_collection_endpoints.length should be (expected)
      }
    }
  }

}
