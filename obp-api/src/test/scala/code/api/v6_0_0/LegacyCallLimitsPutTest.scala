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

import org.json4s.jvalue2extractable
import code.api.cache.Caching
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateRateLimits, CanGetRateLimits, CanUpdateRateLimits}
import code.api.util.ErrorMessages.RateLimitScopeNotSupported
import code.api.v3_1_0.CallLimitPostJson
import code.api.v4_0_0.CallLimitPostJsonV400
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.ratelimiting.RateLimiting
import code.setup.OBPReq
import com.openbankproject.commons.model.ErrorMessage
import org.json4s.native.Serialization.write

import java.util.Date

/**
 * The v3.1.0 and v4.0.0 "Set Rate Limits" PUT keeps one rate limit per consumer: it changes the consumer's rate limit
 * or creates it. Next to the records v6.0.0 creates, it must not pick one of several to overwrite.
 */
class LegacyCallLimitsPutTest extends V600ServerSetup {

  lazy val consumerId: String = Consumers.consumers.vend.getConsumerByConsumerKey(consumer3.key).map(_.consumerId).getOrElse("")

  private def v3_1_0_Request: OBPReq = baseRequest / "obp" / "v3.1.0"
  private def callLimitsPath(base: OBPReq) = base / "management" / "consumers" / consumerId / "consumer" / "call-limits"

  private def from = new Date(System.currentTimeMillis() - 3600000L)
  private def to = new Date(System.currentTimeMillis() + 86400000L)

  private def legacyBody(perHour: String) = CallLimitPostJson(from, to, "-1", "-1", perHour, "-1", "-1", "-1")
  private def v400Body(perHour: String) = CallLimitPostJsonV400(from, to, None, None, None, "-1", "-1", perHour, "-1", "-1", "-1")

  /** A rate limit created through v6.0.0, which adds a record next to the others. */
  private def createV600Limit(perHour: String): String = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateRateLimits.toString)
    val response = makePostRequest(
      (v6_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "rate-limits").POST <@ (user1),
      write(CallLimitPostJsonV600(from, to, None, None, None, "-1", "-1", perHour, "-1", "-1", "-1")))
    response.code should equal(201)
    response.body.extract[CallLimitJsonV600].rate_limiting_id
  }

  private def storedPerHourLimits: List[Long] =
    RateLimiting.findAllByConsumerId(consumerId).map(_.perHourCallLimit).sorted

  private def activePerHourLimit: Long = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetRateLimits.toString)
    val response = makeGetRequest((v6_0_0_Request / "management" / "consumers" / consumerId / "active-rate-limits").GET <@ (user1))
    response.code should equal(200)
    response.body.extract[ActiveRateLimitsJsonV600].active_per_hour_rate_limit
  }

  private def startClean(): Unit = {
    RateLimiting.findAllByConsumerId(consumerId).foreach(row => RateLimiting.delete(row.rateLimitingId))
    Caching.invalidateRateLimitCache(consumerId)
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateRateLimits.toString)
  }

  feature("Set Rate Limits (v3.1.0 and v4.0.0) next to the rate limits v6.0.0 creates") {

    scenario("does not change any of a consumer's several rate limits") {
      startClean()
      createV600Limit("1000")
      createV600Limit("5000")

      val v310 = makePutRequest(callLimitsPath(v3_1_0_Request).PUT <@ (user1), write(legacyBody("100")))
      val v400 = makePutRequest(callLimitsPath(v4_0_0_Request).PUT <@ (user1), write(v400Body("100")))

      v310.code should equal(400)
      v310.body.extract[ErrorMessage].message should startWith("OBP-30625")
      v400.code should equal(400)
      v400.body.extract[ErrorMessage].message should startWith("OBP-30625")
      storedPerHourLimits should equal(List(1000L, 5000L))
    }

    scenario("changes a consumer's only rate limit, and creates one for a consumer that has none") {
      startClean()

      makePutRequest(callLimitsPath(v3_1_0_Request).PUT <@ (user1), write(legacyBody("100"))).code should equal(200)
      storedPerHourLimits should equal(List(100L))

      makePutRequest(callLimitsPath(v3_1_0_Request).PUT <@ (user1), write(legacyBody("200"))).code should equal(200)
      storedPerHourLimits should equal(List(200L))
    }

    scenario("changes a consumer's only rate limit that an older version stored with empty scope fields") {
      startClean()
      // As v4.0.0 stored a body that sent "" for api_name, api_version and bank_id.
      RateLimiting.insertWithLimits(consumerId, from, to, apiVersion = Some(""), apiName = Some(""), bankId = Some(""),
        perSecond = -1, perMinute = -1, perHour = 1000, perDay = -1, perWeek = -1, perMonth = -1)

      makePutRequest(callLimitsPath(v3_1_0_Request).PUT <@ (user1), write(legacyBody("100"))).code should equal(200)

      storedPerHourLimits should equal(List(100L))
    }

    scenario("applies the change at once") {
      startClean()
      createV600Limit("1000")
      activePerHourLimit should equal(1000L)

      makePutRequest(callLimitsPath(v3_1_0_Request).PUT <@ (user1), write(legacyBody("100"))).code should equal(200)

      activePerHourLimit should equal(100L)
    }

    scenario("v4.0.0 refuses an api_name, api_version or bank_id, and takes an empty one as not set") {
      startClean()

      val scoped = makePutRequest(callLimitsPath(v4_0_0_Request).PUT <@ (user1), write(v400Body("100").copy(bank_id = Some("some-bank"))))
      scoped.code should equal(400)
      scoped.body.extract[ErrorMessage].message should startWith(RateLimitScopeNotSupported)
      storedPerHourLimits shouldBe empty

      val blank = makePutRequest(callLimitsPath(v4_0_0_Request).PUT <@ (user1), write(v400Body("100").copy(api_name = Some(" "))))
      blank.code should equal(200)
      RateLimiting.findAllByConsumerId(consumerId).map(_.apiName) should equal(List(None))
    }
  }
}
