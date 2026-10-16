package code.api.v7_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetTrafficSources
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UserHasMissingRoles}
import code.api.v6_0_0.V600ServerSetup
import code.entitlement.Entitlement
import code.telemetry.TrafficSources
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * This suite checks GET /obp/v7.0.0/management/traffic/top-callers: that it needs its Role, and that
 * real requests appear in the right tables (an authenticated one under its Consumer, every one under
 * its address, and an unknown path as `unmatched`).
 */
class TrafficSourcesEndpointTest extends V600ServerSetup {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint extends Tag("getTrafficSources")

  private def topCallers = v7_0_0_Request / "management" / "traffic" / "top-callers"

  private def withRole[T](body: => T): T = {
    val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetTrafficSources.toString)
    try body finally Entitlement.entitlement.vend.deleteEntitlement(entitlement)
  }

  feature(s"Get Top Callers - GET /obp/v7.0.0/management/traffic/top-callers - $VersionOfApi") {

    scenario("anonymous access is 401, and a user without the Role gets 403", ApiEndpoint, VersionOfApi) {
      val anonymous = makeGetRequest(topCallers.GET)
      anonymous.code should equal(401)
      anonymous.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
      val noRole = makeGetRequest(topCallers.GET <@ (user1))
      noRole.code should equal(403)
      noRole.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetTrafficSources)
    }

    scenario("requests appear under their Consumer, their address, and their endpoint", ApiEndpoint, VersionOfApi) {
      // The endpoint lists only the 50 busiest caller and endpoint pairs of the window, and the window
      // is the current minute of this JVM. Pairs that other suites made in the same minute can crowd
      // out the single request of each pair below, so start from an empty record.
      TrafficSources.clear()
      makeGetRequest((v7_0_0_Request / "banks").GET <@ (user1)).code should equal(200)
      makeGetRequest((v7_0_0_Request / "banks").GET).code should equal(200)
      makeGetRequest((v7_0_0_Request / "no-such-endpoint-traffic-probe").GET).code should equal(404)

      val response = withRole(makeGetRequest(topCallers.GET <@ (user1) <<? List("window" -> "1")))
      response.code should equal(200)
      val traffic = response.body.extract[TrafficSourcesJsonV700]
      traffic.window_minutes should equal(1)

      Then("the authenticated request is under the test Consumer")
      val consumer = traffic.consumers.find(_.consumer_id == testConsumer.consumerId.get)
      consumer should not be empty
      consumer.get.endpoints.exists(_.endsWith("-getBanks")) shouldBe true

      And("every request is under this client's address, the unknown path as unmatched")
      traffic.addresses should not be empty
      traffic.callers_and_endpoints.exists(p => p.caller_kind == "ip" && p.endpoint == "unmatched") shouldBe true
      traffic.callers_and_endpoints.exists(p => p.caller_kind == "consumer" && p.caller == testConsumer.consumerId.get &&
        p.endpoint.endsWith("-getBanks")) shouldBe true
    }

    scenario("a window other than 1, 5 or 15 is refused with 400", ApiEndpoint, VersionOfApi) {
      withRole {
        makeGetRequest(topCallers.GET <@ (user1) <<? List("window" -> "7")).code should equal(400)
        makeGetRequest(topCallers.GET <@ (user1) <<? List("window" -> "abc")).code should equal(400)
      }
    }
  }
}
