package code.api.v7_0_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanReadMetrics
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UserHasMissingRoles}
import code.api.v6_0_0.V600ServerSetup
import code.api.v7_0_0.JSONFactory700.TopConsumersJsonV700
import code.entitlement.Entitlement
import code.metrics.MetricBatchWriter
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * Tests GET /obp/v7.0.0/management/metrics/top-consumers (grouped by metric.consumerid,
 * unlike the app-name-joined v3.1.0 version).
 *
 * Counting is asserted behind a url filter unique to this suite's traffic, because
 * write_metrics is JVM-wide and other suites (or the top-consumers calls themselves) may
 * also land rows on the metric table.
 */
class TopConsumersTest extends V600ServerSetup {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("getTopConsumers")

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v7.0.0")
      val request = (v7_0_0_Request / "management" / "metrics" / "top-consumers").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Missing role") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v7.0.0")
      val request = (v7_0_0_Request / "management" / "metrics" / "top-consumers").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("error should be " + UserHasMissingRoles + CanReadMetrics)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be(UserHasMissingRoles + CanReadMetrics)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Top consumers by call count") {
    scenario("Two consumers make traffic and appear ranked with their counts", ApiEndpoint1, VersionOfApi) {
      setPropsValues("write_metrics" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)

      val trafficUrl = "/obp/v5.1.0/banks"

      // 5 calls via testConsumer (user1), 3 via testConsumer2 (user2) — asymmetric on purpose.
      val requestBanks1 = (v5_1_0_Request / "banks").GET <@ (user1)
      (1 to 5).foreach(_ => makeGetRequest(requestBanks1))
      val requestBanks2 = (v5_1_0_Request / "banks").GET <@ (user2)
      (1 to 3).foreach(_ => makeGetRequest(requestBanks2))

      MetricBatchWriter.flush()

      When("We query top-consumers filtered to the traffic url")
      val request = (v7_0_0_Request / "management" / "metrics" / "top-consumers").GET <@ (user1) <<? List(
        ("url", trafficUrl))
      val response = makeGetRequest(request)
      Then("We get both consumers ranked by count")
      response.code should equal(200)
      val topConsumers = response.body.extract[TopConsumersJsonV700].top_consumers
      topConsumers.length shouldBe 2
      topConsumers.head.count shouldBe 5
      topConsumers.head.consumer_id shouldBe testConsumer.consumerId
      topConsumers.head.app_name shouldBe testConsumer.name
      topConsumers(1).count shouldBe 3
      topConsumers(1).consumer_id shouldBe testConsumer2.consumerId

      When("We query with limit=1")
      val request2 = (v7_0_0_Request / "management" / "metrics" / "top-consumers").GET <@ (user1) <<? List(
        ("url", trafficUrl),
        ("limit", "1"))
      val response2 = makeGetRequest(request2)
      Then("Only the busiest consumer is returned")
      response2.code should equal(200)
      val topConsumers2 = response2.body.extract[TopConsumersJsonV700].top_consumers
      topConsumers2.length shouldBe 1
      topConsumers2.head.consumer_id shouldBe testConsumer.consumerId
    }
  }
}
