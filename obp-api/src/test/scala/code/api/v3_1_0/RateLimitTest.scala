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
package code.api.v3_1_0

import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.{Calendar, Date}

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.DateWithDayFormat
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanReadCallLimits, CanSetCallLimits}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.{APIUtil, ApiRole}
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.consumer.Consumers
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class RateLimitTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations3_1_0.callsLimit))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getCallsLimit))

  val yesterday = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val tomorrow = ZonedDateTime.now(ZoneId.of("UTC")).plusDays(10)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  val fromDate = Date.from(yesterday.toInstant())
  val toDate = Date.from(tomorrow.toInstant())

  val callLimitJson1 = CallLimitPostJson(
    fromDate,
    toDate,
    per_second_call_limit = "-1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )
  val callLimitSecondJson = CallLimitPostJson(
    fromDate,
    toDate,
    per_second_call_limit = "1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )
  val callLimitMinuteJson = CallLimitPostJson(
    fromDate,
    toDate,
    per_second_call_limit = "-1",
    per_minute_call_limit = "1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )
  val callLimitHourJson = CallLimitPostJson(
    fromDate,
    toDate,
    per_second_call_limit = "-1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )
  val callLimitDayJson = CallLimitPostJson(
    fromDate,
    toDate,
    per_second_call_limit = "-1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )
  val callLimitWeekJson = CallLimitPostJson(
    fromDate,
    toDate,
    per_second_call_limit = "-1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "1",
    per_month_call_limit = "-1"
  )
  val callLimitMonthJson = CallLimitPostJson(
    fromDate,
    toDate,
    per_second_call_limit = "-1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "1"
  )

  feature("Rate Limit - " + ApiEndpoint + " - " + VersionOfApi)
  {

    scenario("We will try to set calls limit per minute for a Consumer - unauthorized access", ApiEndpoint, VersionOfApi) {
      When("We make a request v3.1.0")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT
      val response310 = makePutRequest(request310, write(callLimitJson1))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will try to set calls limit per minute without a proper Role " + ApiRole.canSetCallLimits, ApiEndpoint, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + ApiRole.canSetCallLimits)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
      val response310 = makePutRequest(request310, write(callLimitJson1))
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanSetCallLimits)
      response310.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanSetCallLimits)
    }
    scenario("We will try to set calls limit per minute with a proper Role " + ApiRole.canSetCallLimits, ApiEndpoint, VersionOfApi) {
      When("We make a request v3.1.0 with a Role " + ApiRole.canSetCallLimits)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimits.toString)
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
      val response310 = makePutRequest(request310, write(callLimitJson1))
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[CallLimitJson]
    }
    scenario("We will set calls limit per second for a Consumer", ApiEndpoint, VersionOfApi) {
      if(APIUtil.getPropsAsBoolValue("use_consumer_limits", false)) {
        When("We make a request v3.1.0 with a Role " + ApiRole.canSetCallLimits)
        val Some((c, _)) = user1
        val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
        val id: Long = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimits.toString)
        val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
        val response01 = makePutRequest(request310, write(callLimitSecondJson))
        Then("We should get a 200")
        response01.code should equal(200)

        When("We make the first call after update")
        val response02 = makePutRequest(request310, write(callLimitSecondJson))
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = makePutRequest(request310, write(callLimitSecondJson))
        Then("We should get a 429")
        response03.code should equal(429)

        // Revert to initial state
        Consumers.consumers.vend.updateConsumerCallLimits(id, Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"))
      }
    }
    scenario("We will set calls limit per minute for a Consumer", ApiEndpoint, VersionOfApi) {
      if(APIUtil.getPropsAsBoolValue("use_consumer_limits", false)) {
        When("We make a request v3.1.0 with a Role " + ApiRole.canSetCallLimits)
        val Some((c, _)) = user1
        val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
        val id: Long = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimits.toString)
        val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
        val response01 = makePutRequest(request310, write(callLimitMinuteJson))
        Then("We should get a 200")
        response01.code should equal(200)

        When("We make the first call after update")
        val response02 = makePutRequest(request310, write(callLimitMinuteJson))
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = makePutRequest(request310, write(callLimitMinuteJson))
        Then("We should get a 429")
        response03.code should equal(429)

        // Revert to initial state
        Consumers.consumers.vend.updateConsumerCallLimits(id, Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"))
      }
    }
    scenario("We will set calls limit per hour for a Consumer", ApiEndpoint, VersionOfApi) {
      if(APIUtil.getPropsAsBoolValue("use_consumer_limits", false)) {
        When("We make a request v3.1.0 with a Role " + ApiRole.canSetCallLimits)
        val Some((c, _)) = user1
        val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
        val id: Long = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimits.toString)
        val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
        val response01 = makePutRequest(request310, write(callLimitHourJson))
        Then("We should get a 200")
        response01.code should equal(200)

        When("We make the first call after update")
        val response02 = makePutRequest(request310, write(callLimitHourJson))
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = makePutRequest(request310, write(callLimitHourJson))
        Then("We should get a 429")
        response03.code should equal(429)

        // Revert to initial state
        Consumers.consumers.vend.updateConsumerCallLimits(id, Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"))
      }
    }
    scenario("We will set calls limit per day for a Consumer", ApiEndpoint, VersionOfApi) {
      if(APIUtil.getPropsAsBoolValue("use_consumer_limits", false)) {
        When("We make a request v3.1.0 with a Role " + ApiRole.canSetCallLimits)
        val Some((c, _)) = user1
        val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
        val id: Long = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimits.toString)
        val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
        val response01 = makePutRequest(request310, write(callLimitDayJson))
        Then("We should get a 200")
        response01.code should equal(200)

        When("We make the first call after update")
        val response02 = makePutRequest(request310, write(callLimitDayJson))
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = makePutRequest(request310, write(callLimitDayJson))
        Then("We should get a 429")
        response03.code should equal(429)

        // Revert to initial state
        Consumers.consumers.vend.updateConsumerCallLimits(id, Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"))
      }
    }
    scenario("We will set calls limit per week for a Consumer", ApiEndpoint, VersionOfApi) {
      if(APIUtil.getPropsAsBoolValue("use_consumer_limits", false)) {
        When("We make a request v3.1.0 with a Role " + ApiRole.canSetCallLimits)
        val Some((c, _)) = user1
        val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
        val id: Long = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimits.toString)
        val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
        val response01 = makePutRequest(request310, write(callLimitWeekJson))
        Then("We should get a 200")
        response01.code should equal(200)

        When("We make the first call after update")
        val response02 = makePutRequest(request310, write(callLimitWeekJson))
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = makePutRequest(request310, write(callLimitWeekJson))
        Then("We should get a 429")
        response03.code should equal(429)

        // Revert to initial state
        Consumers.consumers.vend.updateConsumerCallLimits(id, Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"))
      }
    }
    scenario("We will set calls limit per month for a Consumer", ApiEndpoint, VersionOfApi) {
      if(APIUtil.getPropsAsBoolValue("use_consumer_limits", false)) {
        When("We make a request v3.1.0 with a Role " + ApiRole.canSetCallLimits)
        val Some((c, _)) = user1
        val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
        val id: Long = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimits.toString)
        val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
        val response01 = makePutRequest(request310, write(callLimitMonthJson))
        Then("We should get a 200")
        response01.code should equal(200)

        When("We make the first call after update")
        val response02 = makePutRequest(request310, write(callLimitMonthJson))
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = makePutRequest(request310, write(callLimitMonthJson))
        Then("We should get a 429")
        response03.code should equal(429)

        // Revert to initial state
        Consumers.consumers.vend.updateConsumerCallLimits(id, Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"))
      }
    }
  }

  feature("Rate Limit - " + ApiEndpoint2 + " - " + VersionOfApi)
  {
    scenario("We will try to get calls limit per minute for a Consumer - unauthorized access", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will try to get calls limit per minute without a proper Role " + ApiRole.canReadCallLimits, ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + ApiRole.canReadCallLimits)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanReadCallLimits)
      response310.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanReadCallLimits)
    }
    scenario("We will try to get calls limit per minute with a proper Role " + ApiRole.canReadCallLimits, ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0 with a Role " + ApiRole.canReadCallLimits)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanReadCallLimits.toString)
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[CallLimitJson]
    }
  }

}