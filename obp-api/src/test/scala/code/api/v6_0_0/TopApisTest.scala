/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanReadMetrics
import code.api.util.ErrorMessages.{UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * Test for the getTopAPIs v6.0.0 endpoint.
 *
 * This endpoint uses Doobie for database access (DoobieMetricsQueries.getTopApis)
 * and returns API usage metrics with operation_id fields.
 */
class TopApisTest extends V600ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getTopAPIs))

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0")
      val request = (v6_0_0_Request / "management" / "metrics" / "top-apis").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access without role") {
    scenario("We will call the endpoint with user credentials but without proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0")
      val request = (v6_0_0_Request / "management" / "metrics" / "top-apis").GET <@(user1)
      val response = makeGetRequest(request)
      Then("error should be " + UserHasMissingRoles + CanReadMetrics)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanReadMetrics)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access with proper Role") {
    scenario("We will call the endpoint with user credentials and proper entitlement", ApiEndpoint1, VersionOfApi) {
      // Enable metrics writing so API calls are recorded
      setPropsValues("write_metrics" -> "true")

      // Add required entitlement
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)

      // Make some API calls to generate metrics data
      val requestUsers = (v6_0_0_Request / "users" / "current").GET <@ (user1)
      makeGetRequest(requestUsers)
      makeGetRequest(requestUsers)
      makeGetRequest(requestUsers)

      val requestBanks = (v6_0_0_Request / "banks").GET <@ (user1)
      makeGetRequest(requestBanks)
      makeGetRequest(requestBanks)

      When("We make a request v6.0.0 to get top APIs")
      val request = (v6_0_0_Request / "management" / "metrics" / "top-apis").GET <@(user1)
      val response = makeGetRequest(request)

      Then("We get successful response")
      response.code should equal(200)

      And("The response should have the correct structure")
      val topApisJson = response.body.extract[TopApisJsonV600]
      topApisJson.top_apis should not be null
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Response structure with operation_id") {
    scenario("We verify the response includes operation_id field", ApiEndpoint1, VersionOfApi) {
      setPropsValues("write_metrics" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)

      // Generate some metrics by calling APIs
      val requestBanks = (v6_0_0_Request / "banks").GET <@ (user1)
      makeGetRequest(requestBanks)
      makeGetRequest(requestBanks)
      makeGetRequest(requestBanks)
      makeGetRequest(requestBanks)
      makeGetRequest(requestBanks)

      When("We make a request v6.0.0 to get top APIs")
      val request = (v6_0_0_Request / "management" / "metrics" / "top-apis").GET <@(user1)
      val response = makeGetRequest(request)

      Then("We get successful response")
      response.code should equal(200)

      val topApisJson = response.body.extract[TopApisJsonV600]

      And("Each top API entry should have the required fields including operation_id")
      if (topApisJson.top_apis.nonEmpty) {
        topApisJson.top_apis.foreach { topApi =>
          topApi.count should be >= 0
          topApi.implemented_by_partial_function should not be empty
          topApi.implemented_in_version should not be empty
          topApi.operation_id should not be empty
        }
      }
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Filter parameters") {
    scenario("We test filtering by limit parameter", ApiEndpoint1, VersionOfApi) {
      setPropsValues("write_metrics" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)

      // Generate some metrics
      val requestBanks = (v6_0_0_Request / "banks").GET <@ (user1)
      makeGetRequest(requestBanks)
      val requestUsers = (v6_0_0_Request / "users" / "current").GET <@ (user1)
      makeGetRequest(requestUsers)

      When("We request top APIs with limit=1")
      val request = (v6_0_0_Request / "management" / "metrics" / "top-apis").GET <@(user1) <<? List(("limit", "1"))
      val response = makeGetRequest(request)

      Then("We get successful response")
      response.code should equal(200)

      val topApisJson = response.body.extract[TopApisJsonV600]
      And("The result should have at most 1 entry")
      topApisJson.top_apis.size should be <= 1
    }

    scenario("We test filtering by implemented_by_partial_function parameter", ApiEndpoint1, VersionOfApi) {
      setPropsValues("write_metrics" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)

      // Generate some metrics
      val requestBanks = (v6_0_0_Request / "banks").GET <@ (user1)
      makeGetRequest(requestBanks)
      makeGetRequest(requestBanks)

      When("We request top APIs filtered by implemented_by_partial_function=getBanks")
      val request = (v6_0_0_Request / "management" / "metrics" / "top-apis").GET <@(user1) <<? List(("implemented_by_partial_function", "getBanks"))
      val response = makeGetRequest(request)

      Then("We get successful response")
      response.code should equal(200)

      val topApisJson = response.body.extract[TopApisJsonV600]
      And("All results should be for getBanks")
      topApisJson.top_apis.foreach { topApi =>
        topApi.implemented_by_partial_function should equal("getBanks")
      }
    }

    scenario("We test filtering by verb parameter", ApiEndpoint1, VersionOfApi) {
      setPropsValues("write_metrics" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)

      When("We request top APIs filtered by verb=GET")
      val request = (v6_0_0_Request / "management" / "metrics" / "top-apis").GET <@(user1) <<? List(("verb", "GET"))
      val response = makeGetRequest(request)

      Then("We get successful response")
      response.code should equal(200)

      val topApisJson = response.body.extract[TopApisJsonV600]
      topApisJson.top_apis should not be null
    }

    scenario("We test filtering by exclude_app_names parameter", ApiEndpoint1, VersionOfApi) {
      setPropsValues("write_metrics" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)

      When("We request top APIs excluding certain app names")
      val request = (v6_0_0_Request / "management" / "metrics" / "top-apis").GET <@(user1) <<? List(("exclude_app_names", "API-EXPLORER,API-Manager"))
      val response = makeGetRequest(request)

      Then("We get successful response")
      response.code should equal(200)

      val topApisJson = response.body.extract[TopApisJsonV600]
      topApisJson.top_apis should not be null
    }

    scenario("We test filtering by date range parameters", ApiEndpoint1, VersionOfApi) {
      setPropsValues("write_metrics" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)

      When("We request top APIs with from_date far in the future")
      val request = (v6_0_0_Request / "management" / "metrics" / "top-apis").GET <@(user1) <<? List(("from_date", "2222-01-10T01:20:02.900Z"))
      val response = makeGetRequest(request)

      Then("We get successful response")
      response.code should equal(200)

      val topApisJson = response.body.extract[TopApisJsonV600]
      And("The result should be empty since no metrics exist in that date range")
      topApisJson.top_apis shouldBe empty
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Multiple filter parameters") {
    scenario("We test combining multiple filter parameters", ApiEndpoint1, VersionOfApi) {
      setPropsValues("write_metrics" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)

      // Generate some metrics
      val requestBanks = (v6_0_0_Request / "banks").GET <@ (user1)
      makeGetRequest(requestBanks)
      makeGetRequest(requestBanks)

      When("We request top APIs with multiple filters")
      val params = List(
        ("verb", "GET"),
        ("implemented_by_partial_function", "getBanks"),
        ("limit", "10")
      )
      val request = (v6_0_0_Request / "management" / "metrics" / "top-apis").GET <@(user1) <<? params
      val response = makeGetRequest(request)

      Then("We get successful response")
      response.code should equal(200)

      val topApisJson = response.body.extract[TopApisJsonV600]
      And("Results should match the filters")
      topApisJson.top_apis.foreach { topApi =>
        topApi.implemented_by_partial_function should equal("getBanks")
      }
      topApisJson.top_apis.size should be <= 10
    }
  }
}
