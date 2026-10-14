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
package code.api.v3_1_0

import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanReadMetrics
import code.entitlement.Entitlement
import code.metrics.MetricBatchWriter
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * This tests v3.1.0 GET /management/metrics/top-consumers, which matches metric rows to their
 * consumer by name.
 */
class MetricsTopConsumersTest extends V310ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag("getMetricsTopConsumers")

  private def metricsDate(millisecondsAgo: Long): String =
    APIUtil.DateWithMsFormat.format(new java.util.Date(System.currentTimeMillis() - millisecondsAgo))

  private val oneDayInMillis = 24L * 60 * 60 * 1000

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Filters") {
    scenario("Traffic is counted for its consumer, and filters match their values exactly", ApiEndpoint1, VersionOfApi) {
      setPropsValues("write_metrics" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)
      // The traffic is on v5.1.0 /banks, whose metric rows record that url, as in the other
      // metrics tests; only the top-consumers call itself is v3.1.0.
      (1 to 3).foreach(_ => makeGetRequest((baseRequest / "obp" / "v5.1.0" / "banks").GET <@ (user1)))
      MetricBatchWriter.flush()
      val trafficUrl = "/obp/v5.1.0/banks"

      When("We ask for the top consumers of that url by signed-in users")
      val request = (v3_1_0_Request / "management" / "metrics" / "top-consumers").GET <@ (user1) <<? List(
        ("from_date", metricsDate(oneDayInMillis)),
        ("url", trafficUrl),
        ("anon", "false"))
      val response = makeGetRequest(request)
      Then("user1's consumer is listed with its calls")
      response.code should equal(200)
      val topConsumers = response.body.extract[TopConsumersJson].top_consumers
      topConsumers.map(_.app_name) should contain(testConsumer.name.get)
      topConsumers.find(_.app_name == testConsumer.name.get).map(_.count).getOrElse(0) should be >= 3

      When("We filter on a url containing a quote, which no metric has")
      val injected = (v3_1_0_Request / "management" / "metrics" / "top-consumers").GET <@ (user1) <<? List(
        ("from_date", metricsDate(oneDayInMillis)),
        ("url", s"$trafficUrl' OR '1'='1"))
      val injectedResponse = makeGetRequest(injected)
      Then("No metric has that url, so no consumer is listed")
      injectedResponse.code should equal(200)
      injectedResponse.body.extract[TopConsumersJson].top_consumers shouldBe empty
    }
  }
}
