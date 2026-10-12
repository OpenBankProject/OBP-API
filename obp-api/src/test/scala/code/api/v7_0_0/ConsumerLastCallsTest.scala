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

package code.api.v7_0_0

import code.api.cache.Redis
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanReadMetrics
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UserHasMissingRoles}
import code.api.v6_0_0.V600ServerSetup
import code.api.v7_0_0.JSONFactory700.ConsumerLastCallsJsonV700
import code.entitlement.Entitlement
import code.metrics.ConsumerLastCall
import code.setup.RedisTestTarget
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag
import org.scalatest.concurrent.Eventually._
import org.scalatest.time.{Seconds, Span}

import java.util.{Date, UUID}
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * GET /obp/v7.0.0/management/metrics/consumer-last-calls and the Redis-only recording behind it
 * (code.metrics.ConsumerLastCall): throttled per node, never moved backwards, read in one HGETALL.
 */
class ConsumerLastCallsTest extends V600ServerSetup {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("getConsumerLastCalls")

  private def endpoint = v7_0_0_Request / "management" / "metrics" / "consumer-last-calls"

  private def lastCallsAsUser1(): ConsumerLastCallsJsonV700 = {
    val response = makeGetRequest(endpoint.GET <@ (user1))
    response.code should equal(200)
    response.body.extract[ConsumerLastCallsJsonV700]
  }

  private def write(consumerId: String, at: Date): Unit =
    ConsumerLastCall.record(consumerId, at).foreach(Await.result(_, 10.seconds))

  feature(s"test $ApiEndpoint1 version $VersionOfApi - access") {
    scenario("Without user credentials", ApiEndpoint1, VersionOfApi) {
      val response = makeGetRequest(endpoint.GET)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }

    scenario("Without CanReadMetrics", ApiEndpoint1, VersionOfApi) {
      val response = makeGetRequest(endpoint.GET <@ (user1))
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be(UserHasMissingRoles + CanReadMetrics)
    }
  }

  feature("ConsumerLastCall throttle") {
    scenario("A node writes a Consumer at most once per ThrottleSeconds", ApiEndpoint1, VersionOfApi) {
      ConsumerLastCall.forgetThrottle()
      val consumerId = "__last_call_throttle_" + UUID.randomUUID.toString.take(8)
      val t0 = new Date()
      ConsumerLastCall.record(consumerId, t0) shouldBe defined
      ConsumerLastCall.record(consumerId, new Date(t0.getTime + 10 * 1000)) shouldBe empty
      ConsumerLastCall.record(consumerId, new Date(t0.getTime + (ConsumerLastCall.ThrottleSeconds + 1) * 1000L)) shouldBe defined
      And("a call without a Consumer records nothing")
      ConsumerLastCall.record(null, t0) shouldBe empty
      ConsumerLastCall.record("", t0) shouldBe empty
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - reading what was recorded") {
    scenario("The newest time wins, an older write never moves it back", ApiEndpoint1, VersionOfApi) {
      RedisTestTarget.requireReachable(Redis.isRedisReady, "consumer last call recording")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)
      val consumerId = "__last_call_order_" + UUID.randomUUID.toString.take(8)
      val newer = new Date((System.currentTimeMillis() / 1000) * 1000)
      val older = new Date(newer.getTime - 3600 * 1000)

      ConsumerLastCall.forgetThrottle()
      write(consumerId, newer)
      ConsumerLastCall.forgetThrottle()
      write(consumerId, older)

      val body = lastCallsAsUser1()
      body.tracked_since shouldBe defined
      body.consumer_last_calls.find(_.consumer_id == consumerId).map(_.last_call_at.getTime) shouldBe Some(newer.getTime)
      And("the list is most recent first")
      val times = body.consumer_last_calls.map(_.last_call_at.getTime)
      times shouldBe times.sorted(Ordering[Long].reverse)
    }

    scenario("An API call records its Consumer", ApiEndpoint1, VersionOfApi) {
      RedisTestTarget.requireReachable(Redis.isRedisReady, "consumer last call recording")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)
      ConsumerLastCall.forgetThrottle()
      val before = (System.currentTimeMillis() / 1000) * 1000

      makeGetRequest((v5_1_0_Request / "banks").GET <@ (user2)).code should equal(200)

      eventually(timeout(Span(10, Seconds))) {
        val recorded = lastCallsAsUser1().consumer_last_calls.find(_.consumer_id == testConsumer2.consumerId.get)
        recorded.map(_.last_call_at.getTime >= before) shouldBe Some(true)
      }
    }
  }
}
