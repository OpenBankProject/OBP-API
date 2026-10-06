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

import code.api.Constant
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetTelemetry
import code.api.util.ErrorMessages.{ApplicationNotIdentified, UserHasMissingRoles}
import code.api.v6_0_0.V600ServerSetup
import code.entitlement.Entitlement
import code.scope.Scope
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * This suite checks GET /obp/v7.0.0/management/telemetry: that it needs the CanGetTelemetry Role,
 * that it names the instance that answered, and that it shows what the rest of OBP-API records
 * (the endpoint requests counted by ResourceDocMiddleware, the standard JVM meters and the log
 * dispatch counters registered at start-up).
 */
class TelemetryEndpointTest extends V600ServerSetup {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint extends Tag("getTelemetry")

  private def telemetryRequest = v7_0_0_Request / "management" / "telemetry"

  private def withTelemetryRole[T](body: => T): T = {
    val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetTelemetry.toString)
    try body finally Entitlement.entitlement.vend.deleteEntitlement(entitlement)
  }

  feature(s"Get Telemetry - GET /obp/v7.0.0/management/telemetry - $VersionOfApi") {

    scenario("Anonymous access fails with 401", ApiEndpoint, VersionOfApi) {
      val response = makeGetRequest(telemetryRequest.GET)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ApplicationNotIdentified)
    }

    scenario("A logged-in user without CanGetTelemetry gets 403", ApiEndpoint, VersionOfApi) {
      val response = makeGetRequest(telemetryRequest.GET <@ (user1))
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetTelemetry)
    }

    scenario("A user with CanGetTelemetry sees this instance's Telemetry", ApiEndpoint, VersionOfApi) {
      val (first, second) = withTelemetryRole {
        (makeGetRequest(telemetryRequest.GET <@ (user1)), makeGetRequest(telemetryRequest.GET <@ (user1)))
      }
      first.code should equal(200)
      second.code should equal(200)
      val telemetry = second.body.extract[TelemetryJsonV700]

      Then("it names the instance that answered")
      telemetry.api_instance_id should equal(Constant.ApiInstanceId)
      telemetry.port.path should equal("/telemetry")

      And("the standard JVM meters and the log dispatch counters were registered at start-up")
      val names = telemetry.meters.map(_.name).toSet
      names should contain("jvm.threads.live")
      names should contain("obp.api.log.dispatch.entries")
      names should contain("obp.api.instance.info")

      And("every meter the API Manager Telemetry page reads is present under the name it expects")
      // OBP-Frontend apps/api-manager/src/lib/telemetry/telemetry.ts reads these by name.
      List(
        "jvm.memory.used", "jvm.memory.max", "jvm.memory.usage.after.gc", "jvm.gc.overhead",
        "jvm.threads.live", "process.cpu.usage", "process.uptime",
        "hikaricp.connections", "hikaricp.connections.active", "hikaricp.connections.idle",
        "hikaricp.connections.pending", "hikaricp.connections.max", "hikaricp.connections.timeout",
        "cache.gets", "cache.size", "cache.evictions",
        "obp.api.log.dispatch.queue.depth", "obp.api.log.masking.calls"
      ).foreach(expected => names should contain(expected))
      telemetry.meters.filter(_.name == "cache.gets").flatMap(_.tags.get("result")).toSet should equal(Set("hit", "miss"))

      And("the first request was counted by the middleware, under its operation id")
      val requestsToThisEndpoint = telemetry.meters.filter(meter =>
        meter.name == "obp.api.endpoint.requests" &&
          meter.tags.get("operation").contains("OBPv7.0.0-getTelemetry") &&
          meter.tags.get("status").contains("2xx"))
      requestsToThisEndpoint should have size 1
      requestsToThisEndpoint.head.measurements("count") should be >= 1.0
    }

    scenario("List responses record their item counts, and memoised Connector calls are labelled by method", ApiEndpoint, VersionOfApi) {
      Given("a list endpoint and a Connector call that is memoised with a key built by CacheKeyFromArguments")
      makeGetRequest((v7_0_0_Request / "banks").GET).code should equal(200)
      val response = withTelemetryRole { makeGetRequest(telemetryRequest.GET <@ (user1)) }
      val meters = response.body.extract[TelemetryJsonV700].meters

      Then("the list response's item count was recorded under its operation id")
      meters.exists(meter => meter.name == "obp.api.endpoint.response.items" && meter.tags.get("operation").exists(_.endsWith("-getBanks"))) shouldBe true

      And("memoised calls whose key names the cached method are labelled Owner.method, not other")
      val memoizeLabels = meters.filter(_.name == "obp.api.memoize.gets").flatMap(_.tags.get("cache")).toSet
      withClue(s"memoize labels seen: ${memoizeLabels.mkString(", ")} ") {
        memoizeLabels.exists(label => label != "other") shouldBe true
      }
    }

    scenario("name_prefix limits the list", ApiEndpoint, VersionOfApi) {
      val response = withTelemetryRole {
        makeGetRequest(telemetryRequest.GET <@ (user1) <<? List("name_prefix" -> "jvm.memory"))
      }
      response.code should equal(200)
      val names = response.body.extract[TelemetryJsonV700].meters.map(_.name)
      names should not be empty
      all(names) should startWith("jvm.memory")
    }

    scenario("A Consumer holding CanGetTelemetry as a Scope may read Telemetry without an Entitlement", ApiEndpoint, VersionOfApi) {
      Given("user2 holds no CanGetTelemetry Entitlement and testConsumer2 no Scope")
      makeGetRequest(telemetryRequest.GET <@ (user2)).code should equal(403)

      When("testConsumer2, which user2 signs with, is granted CanGetTelemetry as a Scope")
      val granted = Scope.scope.vend.addScope("", testConsumer2.id.get.toString, CanGetTelemetry.toString)
      val response = try makeGetRequest(telemetryRequest.GET <@ (user2)) finally Scope.scope.vend.deleteScope(granted)

      Then("Telemetry is returned")
      response.code should equal(200)
      response.body.extract[TelemetryJsonV700].api_instance_id should equal(Constant.ApiInstanceId)
    }
  }
}
