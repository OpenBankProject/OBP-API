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

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.{Constant, JedisMethod}
import code.api.cache.Redis
import code.setup.OBPReq
import code.api.util.ApiRole.{CanCreateRateLimits, CanDeleteRateLimits, CanGetRateLimits}
import code.api.util.ErrorMessages.{UserHasMissingRoles, AuthenticatedUserIsRequired, TooManyRequests}
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.consumer.Consumers
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import java.util.Date

class RateLimitsTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.createCallLimits))
  object ApiEndpoint2 extends Tag(nameOf(Implementations6_0_0.deleteCallLimits))
  object UpdateRateLimits extends Tag(nameOf(Implementations6_0_0.updateRateLimits))
  object ApiEndpoint3 extends Tag(nameOf(Implementations6_0_0.getActiveRateLimitsAtDate))
  object ApiEndpoint4 extends Tag(nameOf(Implementations6_0_0.getActiveRateLimitsNow))

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
      val request600 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "rate-limits").POST
      val response600 = makePostRequest(request600, write(postCallLimitJsonV600))
      Then("We should get a 401")
      response600.code should equal(401)
      And("error should be " + AuthenticatedUserIsRequired)
      response600.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }

  feature("POST Create Call Limits v6.0.0 - Authorized access") {
    scenario("We will call the endpoint without proper Role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0 without a proper role")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      val request600 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "rate-limits").POST <@ (user1)
      val response600 = makePostRequest(request600, write(postCallLimitJsonV600))
      Then("We should get a 403")
      response600.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateRateLimits)
      response600.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanCreateRateLimits)
    }

    scenario("We will call the endpoint with proper Role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0 with a proper role")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateRateLimits.toString)
      val request600 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "rate-limits").POST <@ (user1)
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
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateRateLimits.toString)
      val request600 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "rate-limits").POST <@ (user1)
      val createResponse = makePostRequest(request600, write(postCallLimitJsonV600))
      createResponse.code should equal(201)
      val createdCallLimit = createResponse.body.extract[CallLimitJsonV600]

      When("We delete the call limit")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteRateLimits.toString)
      val deleteRequest = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "rate-limits" / createdCallLimit.rate_limiting_id).DELETE <@ (user1)
      val deleteResponse = makeDeleteRequest(deleteRequest)

      Then("We should get a 204")
      deleteResponse.code should equal(204)
    }

    scenario("We will try to delete without proper role", ApiEndpoint2, VersionOfApi) {
      Given("We create a call limit first")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateRateLimits.toString)
      val request600 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "rate-limits").POST <@ (user1)
      val createResponse = makePostRequest(request600, write(postCallLimitJsonV600))
      createResponse.code should equal(201)
      val createdCallLimit = createResponse.body.extract[CallLimitJsonV600]

      When("We try to delete without proper role")
      val deleteRequest = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "rate-limits" / createdCallLimit.rate_limiting_id).DELETE <@ (user1)
      val deleteResponse = makeDeleteRequest(deleteRequest)

      Then("We should get a 403")
      deleteResponse.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanDeleteRateLimits)
      deleteResponse.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanDeleteRateLimits)
    }
  }

  feature("GET Active Call Limits at Date v6.0.0") {
    scenario("We will get active call limits at a specific date", ApiEndpoint3, VersionOfApi) {
      Given("We create a call limit first")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateRateLimits.toString)
      val request600 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "rate-limits").POST <@ (user1)
      val createResponse = makePostRequest(request600, write(postCallLimitJsonV600))
      createResponse.code should equal(201)

      When("We get active call limits at current date")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetRateLimits.toString)
      val currentDateString = ZonedDateTime
        .now(ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"))
      val getRequest = (v6_0_0_Request / "management" / "consumers" / consumerId / "active-rate-limits" / currentDateString).GET <@ (user1)
      val getResponse = makeGetRequest(getRequest)

      Then("We should get a 200")
      getResponse.code should equal(200)
      And("we should get the active call limits response")
      val activeCallLimits = getResponse.body.extract[ActiveRateLimitsJsonV600]
      activeCallLimits.considered_rate_limit_ids should not be empty
      // other scenarios may have left records for this consumer; the record created above contributes 10
      activeCallLimits.active_per_second_rate_limit should be >= 10L
    }

    scenario("We will try to get active call limits without proper role", ApiEndpoint3, VersionOfApi) {
      When("We try to get active call limits without proper role")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      val currentDateString = ZonedDateTime
        .now(ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"))
      val getRequest = (v6_0_0_Request / "management" / "consumers" / consumerId / "active-rate-limits" / currentDateString).GET <@ (user1)
      val getResponse = makeGetRequest(getRequest)

      Then("We should get a 403")
      getResponse.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetRateLimits)
      getResponse.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetRateLimits)
    }

    scenario("We will get aggregated call limits for two overlapping rate limit records", ApiEndpoint3, VersionOfApi) {
    // NOTE: This test requires use_consumer_limits=true in props file
      Given("We create two call limit records with overlapping date ranges")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateRateLimits.toString)

      // Create first rate limit record
      val fromDate1 = new Date()
      val toDate1 = new Date(System.currentTimeMillis() + 172800000L) // +2 days
      val rateLimit1 = CallLimitPostJsonV600(
        from_date = fromDate1,
        to_date = toDate1,
        api_version = Some("v6.0.0"),
        api_name = Some("testEndpoint1"),
        bank_id = None,
        per_second_call_limit = "10",
        per_minute_call_limit = "100",
        per_hour_call_limit = "1000",
        per_day_call_limit = "5000",
        per_week_call_limit = "-1",
        per_month_call_limit = "-1"
      )
      val request1 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "rate-limits").POST <@ (user1)
      val createResponse1 = makePostRequest(request1, write(rateLimit1))
      createResponse1.code should equal(201)

      // Create second rate limit record with same date range
      val rateLimit2 = CallLimitPostJsonV600(
        from_date = fromDate1,
        to_date = toDate1,
        api_version = Some("v6.0.0"),
        api_name = Some("testEndpoint2"),
        bank_id = None,
        per_second_call_limit = "5",
        per_minute_call_limit = "50",
        per_hour_call_limit = "500",
        per_day_call_limit = "2500",
        per_week_call_limit = "-1",
        per_month_call_limit = "-1"
      )
      val request2 = (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "rate-limits").POST <@ (user1)
      val createResponse2 = makePostRequest(request2, write(rateLimit2))
      createResponse2.code should equal(201)

      When("We get active call limits at a date within the overlapping range")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetRateLimits.toString)
      val targetDate = ZonedDateTime
        .now(ZoneOffset.UTC)
        .plusDays(1) // Check 1 day from now (within the range)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"))
      val getRequest = (v6_0_0_Request / "management" / "consumers" / consumerId / "active-rate-limits" / targetDate).GET <@ (user1)
      val getResponse = makeGetRequest(getRequest)

      Then("We should get a 200")
      getResponse.code should equal(200)

      And("the totals should be the sum of both records (using single source of truth aggregation)")
      val activeCallLimits = getResponse.body.extract[ActiveRateLimitsJsonV600]
      activeCallLimits.active_per_second_rate_limit should equal(15L) // 10 + 5
      activeCallLimits.active_per_minute_rate_limit should equal(150L) // 100 + 50
      activeCallLimits.active_per_hour_rate_limit should equal(1500L) // 1000 + 500
      activeCallLimits.active_per_day_rate_limit should equal(7500L) // 5000 + 2500
      activeCallLimits.active_per_week_rate_limit should equal(-1L) // -1 (both are -1, so unlimited)
      activeCallLimits.active_per_month_rate_limit should equal(-1L) // -1 (both are -1, so unlimited)
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Value semantics: 0 blocks, -1 is unlimited, no record means the system default.
  // These scenarios use consumer3 (user3), which no other scenario in this class touches, and
  // delete every record they create so that later test classes are not affected.
  // ---------------------------------------------------------------------------------------------

  lazy val consumerId3: String = Consumers.consumers.vend.getConsumerByConsumerKey(consumer3.key).map(_.consumerId.get).getOrElse("")

  def callLimitJson(perSecond: String, perMinute: String, perHour: String): CallLimitPostJsonV600 = CallLimitPostJsonV600(
    from_date = new Date(System.currentTimeMillis() - 3600000L), // one hour ago, so the current hour is covered
    to_date = new Date(System.currentTimeMillis() + 86400000L),  // one day ahead
    api_version = None,
    api_name = None,
    bank_id = None,
    per_second_call_limit = perSecond,
    per_minute_call_limit = perMinute,
    per_hour_call_limit = perHour,
    per_day_call_limit = "-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )

  def createLimit(consumerId: String, json: CallLimitPostJsonV600): String = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateRateLimits.toString)
    val response = makePostRequest((v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "rate-limits").POST <@ (user1), write(json))
    response.code should equal(201)
    response.body.extract[CallLimitJsonV600].rate_limiting_id
  }

  def deleteLimit(consumerId: String, rateLimitingId: String): Unit = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteRateLimits.toString)
    makeDeleteRequest((v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "rate-limits" / rateLimitingId).DELETE <@ (user1)).code should equal(204)
  }

  def activeLimitsNow(consumerId: String): ActiveRateLimitsJsonV600 = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetRateLimits.toString)
    val response = makeGetRequest((v6_0_0_Request / "management" / "consumers" / consumerId / "active-rate-limits").GET <@ (user1))
    response.code should equal(200)
    response.body.extract[ActiveRateLimitsJsonV600]
  }

  def callAsUser3() = makeGetRequest((v6_0_0_Request / "users" / "current").GET <@ (user3))

  feature("Rate limit values v6.0.0: 0 blocks, -1 is unlimited, no record means the system default") {

    scenario("A consumer with no rate limit records gets the system defaults", ApiEndpoint4, VersionOfApi) {
      When("We get the active rate limits of a consumer that has no records")
      val limits = activeLimitsNow(consumerId3)
      Then("No record is considered and every period shows the system default (-1 in the test props)")
      limits.considered_rate_limit_ids shouldBe empty
      limits.active_per_second_rate_limit should equal(-1L)
      limits.active_per_minute_rate_limit should equal(-1L)
      limits.active_per_hour_rate_limit should equal(-1L)
      limits.active_per_day_rate_limit should equal(-1L)
      limits.active_per_week_rate_limit should equal(-1L)
      limits.active_per_month_rate_limit should equal(-1L)
      And("the consumer can call the API")
      callAsUser3().code should equal(200)
    }

    scenario("A record with 0 blocks the consumer and deleting it unblocks", ApiEndpoint1, ApiEndpoint4, VersionOfApi) {
      Given("The consumer can call the API")
      callAsUser3().code should equal(200)
      When("We create a record with 0 per second, per minute and per hour")
      val zeroId = createLimit(consumerId3, callLimitJson("0", "0", "0"))
      try {
        Then("The active rate limits report 0 for those periods and -1 for the rest")
        val limits = activeLimitsNow(consumerId3)
        limits.considered_rate_limit_ids should equal(List(zeroId))
        limits.active_per_second_rate_limit should equal(0L)
        limits.active_per_minute_rate_limit should equal(0L)
        limits.active_per_hour_rate_limit should equal(0L)
        limits.active_per_day_rate_limit should equal(-1L)
        And("every call by the consumer is refused with 429")
        val blocked = callAsUser3()
        blocked.code should equal(429)
        val message = blocked.body.extract[ErrorMessage].message
        message should startWith(TooManyRequests)
        message should include("blocked")
        message should include(consumerId3)
      } finally {
        deleteLimit(consumerId3, zeroId)
      }
      And("after deleting the record the consumer can call the API again")
      callAsUser3().code should equal(200)
      activeLimitsNow(consumerId3).considered_rate_limit_ids shouldBe empty
    }

    scenario("A 0 record adds nothing to a positive record; it blocks only once the sum is 0", ApiEndpoint4, VersionOfApi) {
      Given("A positive record and a record that is 0 per second only")
      val positiveId = createLimit(consumerId3, callLimitJson("10", "100", "1000"))
      val zeroId = createLimit(consumerId3, callLimitJson("0", "-1", "-1"))
      var positiveDeleted = false
      try {
        When("We get the active rate limits")
        val limits = activeLimitsNow(consumerId3)
        Then("every period is the positive record's value: the 0 does not override it")
        limits.considered_rate_limit_ids.toSet should equal(Set(positiveId, zeroId))
        limits.active_per_second_rate_limit should equal(10L)
        limits.active_per_minute_rate_limit should equal(100L)
        limits.active_per_hour_rate_limit should equal(1000L)
        limits.active_per_day_rate_limit should equal(-1L)
        And("the consumer can call the API")
        callAsUser3().code should equal(200)

        When("the positive record is deleted, the 0 record is all that is left")
        deleteLimit(consumerId3, positiveId)
        positiveDeleted = true
        Then("the per-second sum is 0 and the consumer is blocked")
        activeLimitsNow(consumerId3).active_per_second_rate_limit should equal(0L)
        callAsUser3().code should equal(429)
      } finally {
        if (!positiveDeleted) deleteLimit(consumerId3, positiveId)
        deleteLimit(consumerId3, zeroId)
      }
      callAsUser3().code should equal(200)
    }

    // GET /obp/v5.1.0/users/current has no v5.1.0 ResourceDoc, so the request passes the v7.0.0, v6.0.0
    // and v5.1.0 groups (three hops), then the v5.1.0 -> v5.0.0 -> v4.0.0 -> v3.1.0 -> v3.0.0 bridges,
    // and v3.0.0 serves it: seven hops. Every hop that had no doc used to authenticate afresh AND
    // charge one rate-limit unit, so one request cost seven units and a per-minute limit of 2
    // refused the very first request with 429 OBP-10018. A request must cost exactly one unit.
    scenario("A request served after six version hops costs one rate-limit unit, not one per hop", ApiEndpoint4, VersionOfApi) {
      Given("A record limiting the consumer to 2 calls per minute, unlimited otherwise")
      // Earlier scenarios in this class called the API as user3 within the same minute, and
      // counters are incremented even under an unlimited record: start this window from zero.
      resetCallCounters(consumerId3)
      val id = createLimit(consumerId3, callLimitJson("-1", "2", "-1"))
      try {
        def callV510AsUser3() = makeGetRequest((v5_1_0_Request / "users" / "current").GET <@ (user3))
        When("The consumer makes a request that v3.0.0 serves after six version hops")
        val first = callV510AsUser3()
        Then("it is served, and by a bridged version")
        first.code should equal(200)
        first.headers.flatMap(h => Option(h.get("X-OBP-Version-Served"))) should not be empty
        And("a second request is served too: the first one cost one unit, not seven")
        callV510AsUser3().code should equal(200)
        And("the third request is refused by the per-minute limit: the limit is still enforced, once per request")
        val refused = callV510AsUser3()
        refused.code should equal(429)
        val message = refused.body.extract[ErrorMessage].message
        message should startWith(TooManyRequests)
        message should include("per minute")
        message should include(consumerId3)
      } finally {
        deleteLimit(consumerId3, id)
        resetCallCounters(consumerId3)
      }
    }

    scenario("A record with -1 in every period is unlimited, not blocked", ApiEndpoint4, VersionOfApi) {
      Given("A record with -1 everywhere")
      val id = createLimit(consumerId3, callLimitJson("-1", "-1", "-1"))
      try {
        When("We get the active rate limits")
        val limits = activeLimitsNow(consumerId3)
        Then("the record is considered and every period is -1")
        limits.considered_rate_limit_ids should equal(List(id))
        limits.active_per_second_rate_limit should equal(-1L)
        limits.active_per_minute_rate_limit should equal(-1L)
        limits.active_per_hour_rate_limit should equal(-1L)
        And("the consumer can call the API")
        callAsUser3().code should equal(200)
      } finally {
        deleteLimit(consumerId3, id)
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Version fallthrough. A request arriving at /obp/vX/... is offered to every version's route group
  // in turn (Http4sApp: v7.0.0, v6.0.0, v5.1.0, v5.0.0, Berlin Group, UK, v4.0.0, ... v1.2.1), and a
  // group without an endpoint for it may hand it to an older version through its bridge
  // (v7.0.0 -> v6.0.0 -> v5.1.0 -> ... -> v1.2.1). However many groups pass it on and however many
  // bridges it crosses, ONE request must cost ONE rate-limit unit. It used to cost one per hop.
  //
  // Counters are read straight from Redis. RateLimitingUtil counts every served request in every
  // period, whether or not a limit applies, so they are an exact per-request count that does not
  // depend on timing.
  // ---------------------------------------------------------------------------------------------

  def callCounterKey(consumerId: String, period: String): String = s"${Constant.CALL_COUNTER_PREFIX}${consumerId}_$period"

  def callCounter(consumerId: String, period: String): Long =
    Redis.use(JedisMethod.GET, callCounterKey(consumerId, period)).map(_.toLong).getOrElse(0L)

  def resetCallCounters(consumerId: String): Unit =
    Redis.deleteKeysByPattern(s"${Constant.CALL_COUNTER_PREFIX}${consumerId}_*")

  /**
   * @param requestVersion  the version prefix the request arrives at
   * @param path            path segments after the version
   * @param servedBy        which version serves it and how it gets there (documentation for the scenario title)
   * @param versionServed   expected `X-OBP-Version-Served` response header. Only the v7.0.0 -> v6.0.0,
   *                        v6.0.0 -> v5.1.0 and v5.1.0 -> v5.0.0 bridges stamp it, and it names the
   *                        first bridge crossed, not necessarily the version that finally serves.
   */
  case class FallthroughCase(requestVersion: String, path: List[String], servedBy: String, versionServed: Option[String])

  val fallthroughCases: List[FallthroughCase] = List(
    FallthroughCase("v7.0.0", List("users", "current"), "v7.0.0 itself, the first group in the chain", None),
    FallthroughCase("v6.0.0", List("users", "current"), "v6.0.0 itself, after the v7.0.0 group passed it on", None),
    FallthroughCase("v7.0.0", List("banks", "testBank0"), "v6.0.0 through the v7.0.0 -> v6.0.0 bridge", Some("v6.0.0")),
    FallthroughCase("v7.0.0", List("banks"), "v6.0.0 through the v7.0.0 -> v6.0.0 bridge", Some("v6.0.0")),
    FallthroughCase("v5.1.0", List("users", "current"), "v3.0.0 through the v5.1.0 -> v5.0.0 -> v4.0.0 -> v3.1.0 -> v3.0.0 bridges, after the v7.0.0 and v6.0.0 groups passed it on", Some("v5.0.0")),
    FallthroughCase("v4.0.0", List("users", "current"), "v3.0.0 through the v4.0.0 -> v3.1.0 -> v3.0.0 bridges, after nine groups (v7.0.0 down to Berlin Group and UK) passed it on", None),
    FallthroughCase("v3.0.0", List("users", "current"), "v3.0.0 itself, after eleven groups passed it on", None),
    FallthroughCase("v2.2.0", List("users", "current"), "v2.0.0 through the v2.2.0 -> v2.1.0 -> v2.0.0 bridges", None),
    FallthroughCase("v2.0.0", List("banks"), "v1.2.1 through the v2.0.0 -> v1.4.0 -> v1.3.0 -> v1.2.1 bridges", None),
    FallthroughCase("v1.2.1", List("banks"), "v1.2.1 itself, the last OBP group in the chain", None)
  )

  def requestFor(c: FallthroughCase): OBPReq =
    c.path.foldLeft(baseRequest / "obp" / c.requestVersion)(_ / _).GET <@ (user3)

  feature("Rate limiting counts one unit per request, whichever version prefix it arrives at and however many hops it crosses") {
    fallthroughCases.foreach { c =>
      scenario(s"GET /obp/${c.requestVersion}/${c.path.mkString("/")} is served by ${c.servedBy}, and costs one unit", ApiEndpoint4, VersionOfApi) {
        Given("no rate limit record for the consumer, and its call counters at zero")
        activeLimitsNow(consumerId3).considered_rate_limit_ids shouldBe empty // made by user1, so not counted for consumer3
        resetCallCounters(consumerId3)
        When("the consumer makes the request once")
        val first = makeGetRequest(requestFor(c))
        Then("it is served")
        first.code should equal(200)
        And(s"X-OBP-Version-Served is ${c.versionServed.getOrElse("absent")}")
        first.headers.flatMap(h => Option(h.get("X-OBP-Version-Served"))) should equal(c.versionServed)
        And("the X-Rate-Limit headers read -1: no period is limited")
        first.headers.flatMap(h => Option(h.get("X-Rate-Limit-Limit"))) should equal(Some("-1"))
        And("the per-minute and per-hour counters still read 1: activity is counted even when nothing limits it, one unit per request whatever the hop count")
        callCounter(consumerId3, "PER_MINUTE") should equal(1L)
        callCounter(consumerId3, "PER_HOUR") should equal(1L)
        And("a second request makes them 2")
        makeGetRequest(requestFor(c)).code should equal(200)
        callCounter(consumerId3, "PER_MINUTE") should equal(2L)
        callCounter(consumerId3, "PER_HOUR") should equal(2L)
        resetCallCounters(consumerId3)
      }
    }

    scenario("X-Rate-Limit headers describe the shortest LIMITED period, not merely the shortest counted one", ApiEndpoint4, VersionOfApi) {
      Given("A record with per second unlimited, 100 per minute and 1000 per hour, and counters at zero")
      // Every period is counted, so the per-second counter is live too; the headers must skip it
      // because it has no limit, and describe the per-minute limit.
      val id = createLimit(consumerId3, callLimitJson("-1", "100", "1000"))
      try {
        resetCallCounters(consumerId3)
        When("the consumer makes a request")
        val first = callAsUser3()
        Then("it is served with the per-minute limit and remaining calls in the headers")
        first.code should equal(200)
        first.headers.flatMap(h => Option(h.get("X-Rate-Limit-Limit"))) should equal(Some("100"))
        first.headers.flatMap(h => Option(h.get("X-Rate-Limit-Remaining"))) should equal(Some("99"))
        And("the per-second counter was still counted")
        callCounter(consumerId3, "PER_SECOND") should be >= 1L
        And("a second request reports one fewer remaining")
        callAsUser3().headers.flatMap(h => Option(h.get("X-Rate-Limit-Remaining"))) should equal(Some("98"))
      } finally {
        deleteLimit(consumerId3, id)
        resetCallCounters(consumerId3)
      }
    }

    // v4.0.0/users/current: nine groups pass it on, then two bridges, then v3.0.0 serves it. It is the
    // deepest NEW-style target: the old-style versions (v2.0.0 and below) report a refused call as
    // 400 rather than 429 (ResourceDocMiddleware.authenticate keeps Lift's old-style status codes).
    scenario("A per-minute limit of 2 is enforced once per request deep in the chain (v4.0.0/users/current, served by v3.0.0)", ApiEndpoint4, VersionOfApi) {
      Given("A record limiting the consumer to 2 calls per minute, unlimited otherwise, and counters at zero")
      resetCallCounters(consumerId3)
      val id = createLimit(consumerId3, callLimitJson("-1", "2", "-1"))
      try {
        val deepest = fallthroughCases.find(c => c.requestVersion == "v4.0.0" && c.path == List("users", "current")).get
        When("the consumer makes three requests")
        Then("the first two are served and the third is refused with 429 for the per-minute limit")
        makeGetRequest(requestFor(deepest)).code should equal(200)
        makeGetRequest(requestFor(deepest)).code should equal(200)
        val refused = makeGetRequest(requestFor(deepest))
        refused.code should equal(429)
        val message = refused.body.extract[ErrorMessage].message
        message should startWith(TooManyRequests)
        message should include("per minute")
        message should include(consumerId3)
        And("the X-Rate-Limit headers describe the exhausted per-minute limit")
        refused.headers.flatMap(h => Option(h.get("X-Rate-Limit-Limit"))) should equal(Some("2"))
        refused.headers.flatMap(h => Option(h.get("X-Rate-Limit-Remaining"))) should equal(Some("0"))
      } finally {
        deleteLimit(consumerId3, id)
        resetCallCounters(consumerId3)
      }
    }
  }
}
