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

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.Date

import code.api.util.ApiRole.CanSetCallLimits
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.{APIUtil, ApiRole}
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0.getCurrentUser
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class RateLimitingTest extends V400ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object ApiVersion400 extends Tag(ApiVersion.v4_0_0.toString)
  object ApiCallsLimit extends Tag(nameOf(Implementations4_0_0.callsLimit))

  val yesterday = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val tomorrow = ZonedDateTime.now(ZoneId.of("UTC")).plusDays(10)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  val fromDate = Date.from(yesterday.toInstant())
  val toDate = Date.from(tomorrow.toInstant())

  val callLimitJsonInitial = CallLimitPostJsonV400(
    from_date = fromDate,
    to_date = toDate,
    api_version = None,
    api_name = None,
    bank_id = None,
    per_second_call_limit = "-1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )
  val callLimitJsonSecond = callLimitJsonInitial.copy(api_name = Some(nameOf(getCurrentUser)), per_second_call_limit = "1")
  val callLimitJsonMinute = callLimitJsonInitial.copy(api_name = Some(nameOf(getCurrentUser)), per_minute_call_limit = "1")
  val callLimitJsonHour = callLimitJsonInitial.copy(api_name = Some(nameOf(getCurrentUser)), per_hour_call_limit = "1")
  val callLimitJsonWeek = callLimitJsonInitial.copy(api_name = Some(nameOf(getCurrentUser)), per_week_call_limit = "1")
  val callLimitJsonMonth = callLimitJsonInitial.copy(api_name = Some(nameOf(getCurrentUser)), per_month_call_limit = "1")
    

  feature("Rate Limit - " + ApiCallsLimit + " - " + ApiVersion400) {

    scenario("We will try to set Rate Limiting per minute for a Consumer - unauthorized access", ApiCallsLimit, ApiVersion400) {
      When("We make a request v4.0.0")
      val response400 = setRateLimitingAnonymousAccess(callLimitJsonInitial)
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will try to set Rate Limiting per minute without a proper Role " + ApiRole.canSetCallLimits, ApiCallsLimit, ApiVersion400) {
      When("We make a request v4.0.0 without a Role " + ApiRole.canSetCallLimits)
      val response400 = setRateLimitingWithoutRole(user1, callLimitJsonInitial)
      Then("We should get a 403")
      response400.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanSetCallLimits)
      response400.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanSetCallLimits)
    }
    scenario("We will try to set Rate Limiting per minute with a proper Role " + ApiRole.canSetCallLimits, ApiCallsLimit, ApiVersion400) {
      When("We make a request v4.0.0 with a Role " + ApiRole.canSetCallLimits)
      val response400 = setRateLimiting(user1, callLimitJsonInitial)
      Then("We should get a 200")
      response400.code should equal(200)
      response400.body.extract[CallLimitJsonV400]
    }
    scenario("We will set Rate Limiting per second for an Endpoint", ApiCallsLimit, ApiVersion400) {
      if(APIUtil.getPropsAsBoolValue("use_consumer_limits", false)) {
        When("We make a request v4.0.0 with a Role " + ApiRole.canSetCallLimits)
        val response01 = setRateLimiting(user1, callLimitJsonSecond)
        Then("We should get a 200")
        response01.code should equal(200)
        org.scalameta.logger.elem(response01)

        When("We make the first call after update")
        val response02 = getCurrentUserEndpoint(user1)
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = getCurrentUserEndpoint(user1)
        Then("We should get a 429")
        response03.code should equal(429)

        // Revert to initial state
        val response04 = setRateLimiting(user1, callLimitJsonInitial)
        Then("We should get a 200")
        response04.code should equal(200)
      }
    }
    scenario("We will set Rate Limiting per minute for an Endpoint", ApiCallsLimit, ApiVersion400) {
      if(APIUtil.getPropsAsBoolValue("use_consumer_limits", false)) {
        When("We make a request v4.0.0 with a Role " + ApiRole.canSetCallLimits)
        val response01 = setRateLimiting(user1, callLimitJsonMinute)
        Then("We should get a 200")
        response01.code should equal(200)
        org.scalameta.logger.elem(response01)

        When("We make the first call after update")
        val response02 = getCurrentUserEndpoint(user1)
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = getCurrentUserEndpoint(user1)
        Then("We should get a 429")
        response03.code should equal(429)

        // Revert to initial state
        val response04 = setRateLimiting(user1, callLimitJsonInitial)
        Then("We should get a 200")
        response04.code should equal(200)
      }
    }
    scenario("We will set Rate Limiting per hour for an Endpoint", ApiCallsLimit, ApiVersion400) {
      if(APIUtil.getPropsAsBoolValue("use_consumer_limits", false)) {
        When("We make a request v4.0.0 with a Role " + ApiRole.canSetCallLimits)
        val response01 = setRateLimiting(user1, callLimitJsonHour)
        Then("We should get a 200")
        response01.code should equal(200)
        org.scalameta.logger.elem(response01)

        When("We make the first call after update")
        val response02 = getCurrentUserEndpoint(user1)
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = getCurrentUserEndpoint(user1)
        Then("We should get a 429")
        response03.code should equal(429)

        // Revert to initial state
        val response04 = setRateLimiting(user1, callLimitJsonInitial)
        Then("We should get a 200")
        response04.code should equal(200)
      }
    }
    scenario("We will set Rate Limiting per week for an Endpoint", ApiCallsLimit, ApiVersion400) {
      if(APIUtil.getPropsAsBoolValue("use_consumer_limits", false)) {
        When("We make a request v4.0.0 with a Role " + ApiRole.canSetCallLimits)
        val response01 = setRateLimiting(user1, callLimitJsonWeek)
        Then("We should get a 200")
        response01.code should equal(200)
        org.scalameta.logger.elem(response01)

        When("We make the first call after update")
        val response02 = getCurrentUserEndpoint(user1)
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = getCurrentUserEndpoint(user1)
        Then("We should get a 429")
        response03.code should equal(429)

        // Revert to initial state
        val response04 = setRateLimiting(user1, callLimitJsonInitial)
        Then("We should get a 200")
        response04.code should equal(200)
      }
    }
    scenario("We will set Rate Limiting per month for an Endpoint", ApiCallsLimit, ApiVersion400) {
      if(APIUtil.getPropsAsBoolValue("use_consumer_limits", false)) {
        When("We make a request v4.0.0 with a Role " + ApiRole.canSetCallLimits)
        val response01 = setRateLimiting(user1, callLimitJsonMonth)
        Then("We should get a 200")
        response01.code should equal(200)
        org.scalameta.logger.elem(response01)

        When("We make the first call after update")
        val response02 = getCurrentUserEndpoint(user1)
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = getCurrentUserEndpoint(user1)
        Then("We should get a 429")
        response03.code should equal(429)

        // Revert to initial state
        val response04 = setRateLimiting(user1, callLimitJsonInitial)
        Then("We should get a 200")
        response04.code should equal(200)
      }
    }

  }

}