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
import code.api.util.ApiRole.CanGetCurrentConsumer
import code.api.util.ErrorMessages.{UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class ConsumerTest extends V600ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getCurrentConsumer))

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0")
      val request600 = (v6_0_0_Request / "consumers" / "current").GET
      val response600 = makeGetRequest(request600)
      Then("We should get a 401")
      response600.code should equal(401)
      response600.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without proper Role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0 without a proper role")
      val request600 = (v6_0_0_Request / "consumers" / "current").GET <@ (user1)
      val response600 = makeGetRequest(request600)
      Then("We should get a 403")
      response600.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCurrentConsumer)
      response600.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetCurrentConsumer)
    }

    scenario("We will call the endpoint with proper Role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0 with a proper role")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCurrentConsumer.toString)
      val request600 = (v6_0_0_Request / "consumers" / "current").GET <@ (user1)
      val response600 = makeGetRequest(request600)
      Then("We should get a 200")
      response600.code should equal(200)
      And("we should get the correct response format")
      val consumerJson = response600.body.extract[CurrentConsumerJsonV600]
      consumerJson.consumer_id should not be empty
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Response validation") {
    scenario("We will verify the response structure contains expected fields", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCurrentConsumer.toString)
      When("We make a request v6.0.0")
      val request600 = (v6_0_0_Request / "consumers" / "current").GET <@ (user1)
      val response600 = makeGetRequest(request600)
      Then("We should get a 200")
      response600.code should equal(200)
      And("The response should have the correct structure")
      val consumerJson = response600.body.extract[CurrentConsumerJsonV600]
      consumerJson.consumer_id should not be empty
      consumerJson.consumer_id should not be null
      consumerJson.consumer_id shouldBe a[String]
      // consumerJson.app_name should not be empty (can be empty)
      consumerJson.app_name shouldBe a[String]
      // consumerJson.app_type should not be empty (can be empty)
      consumerJson.app_type shouldBe a[String]
      // consumerJson.description should not be empty (can be empty)
      consumerJson.description shouldBe a[String]
      consumerJson.active_rate_limits should not be null
      consumerJson.active_rate_limits.considered_rate_limit_ids should not be null
      consumerJson.active_rate_limits.active_at_date should not be null
      consumerJson.call_counters should not be null
      consumerJson.call_counters.per_second should not be null
      consumerJson.call_counters.per_minute should not be null
      consumerJson.call_counters.per_hour should not be null
      consumerJson.call_counters.per_day should not be null
      consumerJson.call_counters.per_week should not be null
      consumerJson.call_counters.per_month should not be null
      // Verify each counter has valid status
      val validStatuses = List("ACTIVE", "NO_COUNTER", "EXPIRED", "REDIS_UNAVAILABLE", "DATA_MISSING")
      consumerJson.call_counters.per_second.status should (be ("ACTIVE") or be ("NO_COUNTER") or be ("EXPIRED") or be ("REDIS_UNAVAILABLE") or be ("DATA_MISSING"))
      consumerJson.call_counters.per_minute.status should (be ("ACTIVE") or be ("NO_COUNTER") or be ("EXPIRED") or be ("REDIS_UNAVAILABLE") or be ("DATA_MISSING"))
      consumerJson.call_counters.per_hour.status should (be ("ACTIVE") or be ("NO_COUNTER") or be ("EXPIRED") or be ("REDIS_UNAVAILABLE") or be ("DATA_MISSING"))
      consumerJson.call_counters.per_day.status should (be ("ACTIVE") or be ("NO_COUNTER") or be ("EXPIRED") or be ("REDIS_UNAVAILABLE") or be ("DATA_MISSING"))
      consumerJson.call_counters.per_week.status should (be ("ACTIVE") or be ("NO_COUNTER") or be ("EXPIRED") or be ("REDIS_UNAVAILABLE") or be ("DATA_MISSING"))
      consumerJson.call_counters.per_month.status should (be ("ACTIVE") or be ("NO_COUNTER") or be ("EXPIRED") or be ("REDIS_UNAVAILABLE") or be ("DATA_MISSING"))
    }
  }
}
