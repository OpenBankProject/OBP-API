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
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
*/
package code.api.v3_1_0

import org.json4s._
import code.api.cache.Redis

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.Date
import code.api.util.APIUtil.OAuth._
import code.api.util.{ApiRole, RateLimitingUtil}
import code.api.util.ApiRole.{CanReadCallLimits, CanUpdateRateLimits}
import code.api.util.ErrorMessages.{UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.v3_1_0.Http4s310.Implementations3_1_0
import code.api.cache.Caching
import code.consumer.Consumers
import code.ratelimiting.RateLimiting
import net.liftweb.mapper.By
import code.entitlement.Entitlement
import code.setup.PropsReset
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class RateLimitTest extends V310ServerSetup with PropsReset {

  /** The consumer's rate limits are gone, and so is the cached copy: the limit just set would refuse the next PUT. */
  private def removeRateLimits(consumerId: String): Unit = {
    RateLimiting.bulkDelete_!!(By(RateLimiting.ConsumerId, consumerId))
    Caching.invalidateRateLimitCache(consumerId)
  }

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

  override def beforeEach() = {
    super.beforeEach()
    setPropsValues("use_consumer_limits"->"true")
    setPropsValues("user_consumer_limit_anonymous_access"->"6000")
  }

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
      And("error should be " + AuthenticatedUserIsRequired)
      response310.body.extract[ErrorMessage].message should equal (AuthenticatedUserIsRequired)
    }
    scenario("We will try to set calls limit per minute without a proper Role " + ApiRole.canUpdateRateLimits, ApiEndpoint, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + ApiRole.canUpdateRateLimits)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
      val response310 = makePutRequest(request310, write(callLimitJson1))
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanUpdateRateLimits)
      response310.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanUpdateRateLimits)
    }
    scenario("We will try to set calls limit per minute with a proper Role " + ApiRole.canUpdateRateLimits, ApiEndpoint, VersionOfApi) {
      When("We make a request v3.1.0 with a Role " + ApiRole.canUpdateRateLimits)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUpdateRateLimits.toString)
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
      val response310 = makePutRequest(request310, write(callLimitJson1))
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[CallLimitJson]
    }
    scenario("We will set calls limit per second for a Consumer", ApiEndpoint, VersionOfApi) {
     
      When("We make a request v3.1.0 with a Role " + ApiRole.canUpdateRateLimits)
        val Some((c, _)) = user1
        val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
        val id: Long = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUpdateRateLimits.toString)
        val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
        // Set by another consumer, so this consumer starts the period with no calls made.
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, ApiRole.CanUpdateRateLimits.toString)
        val response01 = makePutRequest((v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user2), write(callLimitSecondJson))
        Then("We should get a 200")
        response01.code should equal(200)

        When("We make the first call after update")
        val secondStarted = System.nanoTime()
        val response02 = makePutRequest(request310, write(callLimitSecondJson))
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        // Redis rounds the counter's TTL to whole seconds, so the one-second window reads as expired about half a second
        // after the first call: a slower run cannot tell whether the limit was applied.
        assume((System.nanoTime() - secondStarted) / 1000000 < 400, "the first call took too long to test a per-second limit")
        val response03 = makePutRequest(request310, write(callLimitSecondJson))
        Then("We should get a 429, as the new limit applies at once")
        response03.code should equal(429)

        // Revert to initial state
        Consumers.consumers.vend.updateConsumerCallLimits(id, Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"))
        removeRateLimits(consumerId)
    }
    scenario("We will set calls limit per minute for a Consumer", ApiEndpoint, VersionOfApi) {
     
      When("We make a request v3.1.0 with a Role " + ApiRole.canUpdateRateLimits)
        val Some((c, _)) = user1
        val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
        val id: Long = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUpdateRateLimits.toString)
        val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
        // Set by another consumer, so this consumer starts the period with no calls made.
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, ApiRole.CanUpdateRateLimits.toString)
        val response01 = makePutRequest((v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user2), write(callLimitMinuteJson))
        Then("We should get a 200")
        response01.code should equal(200)

        When("We make the first call after update")
        val response02 = makePutRequest(request310, write(callLimitMinuteJson))
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = makePutRequest(request310, write(callLimitMinuteJson))
        Then("We should get a 429, as the new limit applies at once")
        response03.code should equal(429)

        // Revert to initial state
        Consumers.consumers.vend.updateConsumerCallLimits(id, Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"))
        removeRateLimits(consumerId)
    }
    scenario("We will set calls limit per hour for a Consumer", ApiEndpoint, VersionOfApi) {
     
      When("We make a request v3.1.0 with a Role " + ApiRole.canUpdateRateLimits)
        val Some((c, _)) = user1
        val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
        val id: Long = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUpdateRateLimits.toString)
        val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
        // Set by another consumer, so this consumer starts the period with no calls made.
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, ApiRole.CanUpdateRateLimits.toString)
        val response01 = makePutRequest((v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user2), write(callLimitHourJson))
        Then("We should get a 200")
        response01.code should equal(200)

        When("We make the first call after update")
        val response02 = makePutRequest(request310, write(callLimitHourJson))
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = makePutRequest(request310, write(callLimitHourJson))
        Then("We should get a 429, as the new limit applies at once")
        response03.code should equal(429)

        // Revert to initial state
        Consumers.consumers.vend.updateConsumerCallLimits(id, Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"))
        removeRateLimits(consumerId)
    }
    scenario("We will set calls limit per day for a Consumer", ApiEndpoint, VersionOfApi) {
     
      When("We make a request v3.1.0 with a Role " + ApiRole.canUpdateRateLimits)
        val Some((c, _)) = user1
        val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
        val id: Long = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUpdateRateLimits.toString)
        val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
        // Set by another consumer, so this consumer starts the period with no calls made.
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, ApiRole.CanUpdateRateLimits.toString)
        val response01 = makePutRequest((v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user2), write(callLimitDayJson))
        Then("We should get a 200")
        response01.code should equal(200)

        When("We make the first call after update")
        val response02 = makePutRequest(request310, write(callLimitDayJson))
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = makePutRequest(request310, write(callLimitDayJson))
        Then("We should get a 429, as the new limit applies at once")
        response03.code should equal(429)

        // Revert to initial state
        Consumers.consumers.vend.updateConsumerCallLimits(id, Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"))
        removeRateLimits(consumerId)
    }
    scenario("We will set calls limit per week for a Consumer", ApiEndpoint, VersionOfApi) {
     
      When("We make a request v3.1.0 with a Role " + ApiRole.canUpdateRateLimits)
        val Some((c, _)) = user1
        val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
        val id: Long = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUpdateRateLimits.toString)
        val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
        // Set by another consumer, so this consumer starts the period with no calls made.
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, ApiRole.CanUpdateRateLimits.toString)
        val response01 = makePutRequest((v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user2), write(callLimitWeekJson))
        Then("We should get a 200")
        response01.code should equal(200)

        When("We make the first call after update")
        val response02 = makePutRequest(request310, write(callLimitWeekJson))
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = makePutRequest(request310, write(callLimitWeekJson))
        Then("We should get a 429, as the new limit applies at once")
        response03.code should equal(429)

        // Revert to initial state
        Consumers.consumers.vend.updateConsumerCallLimits(id, Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"))
        removeRateLimits(consumerId)
    }
    scenario("We will set calls limit per month for a Consumer", ApiEndpoint, VersionOfApi) {
     
      When("We make a request v3.1.0 with a Role " + ApiRole.canUpdateRateLimits)
        val Some((c, _)) = user1
        val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
        val id: Long = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUpdateRateLimits.toString)
        val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user1)
        // Set by another consumer, so this consumer starts the period with no calls made.
        Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, ApiRole.CanUpdateRateLimits.toString)
        val response01 = makePutRequest((v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(user2), write(callLimitMonthJson))
        Then("We should get a 200")
        response01.code should equal(200)

        When("We make the first call after update")
        val response02 = makePutRequest(request310, write(callLimitMonthJson))
        Then("We should get a 200")
        response02.code should equal(200)

        When("We make the second call after update")
        val response03 = makePutRequest(request310, write(callLimitMonthJson))
        Then("We should get a 429, as the new limit applies at once")
        response03.code should equal(429)

        // Revert to initial state
        Consumers.consumers.vend.updateConsumerCallLimits(id, Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"))
        removeRateLimits(consumerId)
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
      And("error should be " + AuthenticatedUserIsRequired)
      response310.body.extract[ErrorMessage].message should equal (AuthenticatedUserIsRequired)
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