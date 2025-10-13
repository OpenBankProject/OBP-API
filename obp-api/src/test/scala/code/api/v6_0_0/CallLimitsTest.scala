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
import code.api.util.ApiRole.{CanDeleteRateLimiting, CanReadCallLimits, CanSetCallLimits}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v6_0_0.OBPAPI6_0_0.Implementations6_0_0
import code.consumer.Consumers
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import java.util.Date

class CallLimitsTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.createCallLimits))
  object ApiEndpoint2 extends Tag(nameOf(Implementations6_0_0.deleteCallLimits))
  object ApiEndpoint3 extends Tag(nameOf(Implementations6_0_0.getActiveCallLimitsAtDate))

  lazy val postCallLimitJsonV600 = CallLimitPostJsonV600(
    from_date = new Date(),
    to_date = new Date(System.currentTimeMillis() + 86400000L), // +1 day
    api_version = Some("v6.0.0"),
    api_name = Some("testEndpoint"),
    bank_id = None,
    per_second_call_limit = "10",
    per_minute_call_limit = "100",
    per_hour_call_limit = "1000",
    per_day_call_limit = "-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )

  override def beforeAll() = {
    super.beforeAll()
  }

  override def beforeEach() = {
    super.beforeEach()
  }

  feature("POST Create Call Limits v6.0.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0 without user credentials")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      val request600 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").POST
      val response600 = makePostRequest(request600, write(postCallLimitJsonV600))
      Then("We should get a 401")
      response600.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response600.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature("POST Create Call Limits v6.0.0 - Authorized access") {
    scenario("We will call the endpoint without proper Role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0 without a proper role")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      val request600 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").POST <@ (user1)
      val response600 = makePostRequest(request600, write(postCallLimitJsonV600))
      Then("We should get a 403")
      response600.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanSetCallLimits)
      response600.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanSetCallLimits)
    }

    scenario("We will call the endpoint with proper Role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0 with a proper role")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanSetCallLimits.toString)
      val request600 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").POST <@ (user1)
      val response600 = makePostRequest(request600, write(postCallLimitJsonV600))
      Then("We should get a 201")
      response600.code should equal(201)
      And("we should get the correct response format")
      val callLimitResponse = response600.body.extract[CallLimitJsonV600]
      callLimitResponse.per_second_call_limit should equal("10")
      callLimitResponse.per_minute_call_limit should equal("100")
      callLimitResponse.per_hour_call_limit should equal("1000")
    }
  }

  feature("DELETE Call Limits v6.0.0") {
    scenario("We will delete a call limit by rate limiting ID", ApiEndpoint2, VersionOfApi) {
      Given("We create a call limit first")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanSetCallLimits.toString)
      val request600 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").POST <@ (user1)
      val createResponse = makePostRequest(request600, write(postCallLimitJsonV600))
      createResponse.code should equal(201)
      val createdCallLimit = createResponse.body.extract[CallLimitJsonV600]

      When("We delete the call limit")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteRateLimiting.toString)
      val deleteRequest = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits" / createdCallLimit.rate_limiting_id).DELETE <@ (user1)
      val deleteResponse = makeDeleteRequest(deleteRequest)
      
      Then("We should get a 204")
      deleteResponse.code should equal(204)
    }

    scenario("We will try to delete without proper role", ApiEndpoint2, VersionOfApi) {
      Given("We create a call limit first")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanSetCallLimits.toString)
      val request600 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").POST <@ (user1)
      val createResponse = makePostRequest(request600, write(postCallLimitJsonV600))
      createResponse.code should equal(201)
      val createdCallLimit = createResponse.body.extract[CallLimitJsonV600]

      When("We try to delete without proper role")
      val deleteRequest = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits" / createdCallLimit.rate_limiting_id).DELETE <@ (user1)
      val deleteResponse = makeDeleteRequest(deleteRequest)
      
      Then("We should get a 403")
      deleteResponse.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanDeleteRateLimiting)
      deleteResponse.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanDeleteRateLimiting)
    }
  }

  feature("GET Active Call Limits at Date v6.0.0") {
    scenario("We will get active call limits at a specific date", ApiEndpoint3, VersionOfApi) {
      Given("We create a call limit first")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanSetCallLimits.toString)
      val request600 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").POST <@ (user1)
      val createResponse = makePostRequest(request600, write(postCallLimitJsonV600))
      createResponse.code should equal(201)

      When("We get active call limits at current date")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadCallLimits.toString)
      val currentDateString = ZonedDateTime
        .now(ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
      val getRequest = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits" / "active-at-date" / currentDateString).GET <@ (user1)
      val getResponse = makeGetRequest(getRequest)
      
      Then("We should get a 200")
      getResponse.code should equal(200)
      And("we should get the active call limits response")
      val activeCallLimits = getResponse.body.extract[ActiveCallLimitsJsonV600]
      activeCallLimits.call_limits should not be empty
      activeCallLimits.total_per_second_call_limit should be > 0L
    }

    scenario("We will try to get active call limits without proper role", ApiEndpoint3, VersionOfApi) {
      When("We try to get active call limits without proper role")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      val currentDateString = ZonedDateTime
        .now(ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
      val getRequest = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits" / "active-at-date" / currentDateString).GET <@ (user1)
      val getResponse = makeGetRequest(getRequest)
      
      Then("We should get a 403")
      getResponse.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanReadCallLimits)
      getResponse.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanReadCallLimits)
    }
  }
}