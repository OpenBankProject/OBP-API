package code.api.v6_0_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanReadAggregateMetrics
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UserHasMissingRoles}
import code.entitlement.Entitlement
import code.metrics.MetricBatchWriter
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * Tests GET /obp/v6.0.0/management/aggregate-metrics, in particular the fields added in v6.0.0:
 * distinct_user_count, distinct_consumer_count, consent_call_count, distinct_consent_count.
 *
 * distinct_user_count is on-behalf-of aware: consent-borne rows are attributed to the granting
 * human via the consent table (see MappedMetrics.getAllAggregateMetricsBox). The consent path is
 * not exercised here — spinning up a consent in this harness is disproportionate — so this suite
 * pins the plain-auth behaviour (consent_call_count == 0) and the consent attribution is verified
 * manually against a running instance (create a consent, call with it, and check the counts).
 */
class AggregateMetricsTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag("getAggregateMetrics")

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0")
      val request = (v6_0_0_Request / "management" / "aggregate-metrics").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Missing role") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0")
      val request = (v6_0_0_Request / "management" / "aggregate-metrics").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("error should be " + UserHasMissingRoles + CanReadAggregateMetrics)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be(UserHasMissingRoles + CanReadAggregateMetrics)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Aggregate counts including v6.0.0 distinct fields") {
    scenario("We make traffic as two users and check count and the distinct/consent fields", ApiEndpoint1, VersionOfApi) {
      setPropsValues("write_metrics" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadAggregateMetrics.toString)

      // Counts are asserted behind a url filter unique to this traffic (GET /obp/v5.1.0/banks):
      // write_metrics may already be true when this class runs (props are JVM-wide and an earlier
      // suite may have set it), so calls made by the scenarios above — and the aggregate-metrics
      // requests themselves — are also on the metric table. The url filter keeps them out.
      val trafficUrl = "/obp/v5.1.0/banks"

      // 5 calls as user1 (consumer: testConsumer), 3 as user2 (consumer: testConsumer2) —
      // asymmetric on purpose, so a swapped or ignored filter cannot produce the right numbers.
      val requestBanks1 = (v5_1_0_Request / "banks").GET <@ (user1)
      (1 to 5).foreach(_ => makeGetRequest(requestBanks1))
      val requestBanks2 = (v5_1_0_Request / "banks").GET <@ (user2)
      (1 to 3).foreach(_ => makeGetRequest(requestBanks2))

      MetricBatchWriter.flush()

      When("We query aggregate-metrics filtered to user1's consumer and the traffic url")
      val request = (v6_0_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(
        ("include_app_names", testConsumer.name),
        ("url", trafficUrl))
      val response = makeGetRequest(request)
      Then("We get a successful response with the v6.0.0 fields")
      response.code should equal(200)
      val aggregateMetric = response.body.extract[List[AggregateMetricJsonV600]].head
      aggregateMetric.count shouldBe 5
      aggregateMetric.distinct_user_count shouldBe 1
      aggregateMetric.distinct_consumer_count shouldBe 1
      aggregateMetric.consent_call_count shouldBe 0
      aggregateMetric.distinct_consent_count shouldBe 0

      MetricBatchWriter.flush()

      When("We query aggregate-metrics across both consumers for the traffic url")
      val request2 = (v6_0_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(
        ("include_app_names", s"${testConsumer.name},${testConsumer2.name}"),
        ("url", trafficUrl))
      val response2 = makeGetRequest(request2)
      Then("We get a successful response covering both users")
      response2.code should equal(200)
      val aggregateMetric2 = response2.body.extract[List[AggregateMetricJsonV600]].head
      aggregateMetric2.count shouldBe 8
      aggregateMetric2.distinct_user_count shouldBe 2
      aggregateMetric2.distinct_consumer_count shouldBe 2
      aggregateMetric2.consent_call_count shouldBe 0
      aggregateMetric2.distinct_consent_count shouldBe 0
    }
  }
}
