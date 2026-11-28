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
import java.text.SimpleDateFormat
import java.util.TimeZone

import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.metrics.{APIMetrics, MetricBatchWriter}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * Get Popular Endpoints is public, so it must give only the overall ranking.
 * A caller must not be able to narrow it to one user or app, or raise the cap of 50.
 */
class PopularApisTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getPopularApis))

  private val metricDateFormat = {
    val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    format.setTimeZone(TimeZone.getTimeZone("UTC"))
    format
  }

  // Records one call to an endpoint, made by the given user and consumer on the given date.
  private def saveCall(userId: String, consumerId: String, partialFunctionName: String, date: String): Unit =
    APIMetrics.apiMetrics.vend.saveMetric(
      userId, s"/obp/v6.0.0/$partialFunctionName", metricDateFormat.parse(date), 10L, "popular-test-user",
      "popular-test-app", "popular-test@example.com", consumerId, partialFunctionName, "v6.0.0", "GET",
      Some(200), s"correlation-$userId", "", "127.0.0.1", "127.0.0.1", "", "", "", "", "", "")

  private def getPopularEndpoints(params: List[(String, String)]): PopularApisJsonV600 = {
    // Metrics are written in batches, so write the saved calls to the table first.
    MetricBatchWriter.flush()
    val request = (v6_0_0_Request / "api" / "popular-endpoints").GET <<? params
    val response = makeGetRequest(request)
    response.code should equal(200)
    response.body.extract[PopularApisJsonV600]
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - filters other than the date range are ignored") {
    scenario("An anonymous caller asks for one user's or one consumer's endpoints", ApiEndpoint1, VersionOfApi) {
      Given("Two users who called different endpoints in March 2031")
      saveCall("popular-user-a", "popular-consumer-a", "getBanks", "2031-03-10T10:00:00.000Z")
      saveCall("popular-user-a", "popular-consumer-a", "getBanks", "2031-03-11T10:00:00.000Z")
      saveCall("popular-user-b", "popular-consumer-b", "getBank", "2031-03-12T10:00:00.000Z")
      val march2031 = List(("from_date", "2031-03-01T00:00:00.000Z"), ("to_date", "2031-03-31T00:00:00.000Z"))

      When("We filter by the first user's user_id without logging in")
      val byUser = getPopularEndpoints(march2031 :+ ("user_id" -> "popular-user-a"))
      Then("The ranking still includes the second user's endpoint")
      byUser.operation_ids.size should equal(2)
      byUser.operation_ids.head should endWith("-getBanks")
      byUser.operation_ids.exists(_.endsWith("-getBank")) should equal(true)

      When("We filter by the second user's consumer_id, app_name, correlation_id and url")
      val byConsumer = getPopularEndpoints(march2031 ++ List(
        "consumer_id" -> "popular-consumer-b",
        "app_name" -> "popular-test-app",
        "correlation_id" -> "correlation-popular-user-b",
        "url" -> "/obp/v6.0.0/getBank"))
      Then("Those filters are ignored too")
      byConsumer.operation_ids.size should equal(2)
    }

    scenario("An anonymous caller sets a limit", ApiEndpoint1, VersionOfApi) {
      Given("Two endpoints called in April 2031")
      saveCall("popular-user-c", "popular-consumer-c", "getBanks", "2031-04-10T10:00:00.000Z")
      saveCall("popular-user-c", "popular-consumer-c", "getBank", "2031-04-11T10:00:00.000Z")

      When("We ask for limit=1")
      val limited = getPopularEndpoints(List(
        "from_date" -> "2031-04-01T00:00:00.000Z",
        "to_date" -> "2031-04-30T00:00:00.000Z",
        "limit" -> "1"))
      Then("The limit is ignored and both endpoints are listed")
      limited.operation_ids.size should equal(2)
    }

    scenario("An anonymous caller narrows the period", ApiEndpoint1, VersionOfApi) {
      Given("An endpoint called in May 2031")
      saveCall("popular-user-d", "popular-consumer-d", "getBanks", "2031-05-10T10:00:00.000Z")

      When("We ask for a period with no calls in it")
      val emptyPeriod = getPopularEndpoints(List(
        "from_date" -> "2031-05-20T00:00:00.000Z",
        "to_date" -> "2031-05-31T00:00:00.000Z"))
      Then("from_date and to_date still apply, so the list is empty")
      emptyPeriod.operation_ids shouldBe empty
    }
  }
}
