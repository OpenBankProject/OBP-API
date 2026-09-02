package code.api.v7_0_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanReadMetrics
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UserHasMissingRoles}
import code.api.v6_0_0.V600ServerSetup
import code.api.v7_0_0.JSONFactory700.TopUsersJsonV700
import code.entitlement.Entitlement
import code.metrics.MetricBatchWriter
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * Tests GET /obp/v7.0.0/management/metrics/top-users.
 *
 * Counting is asserted behind a url filter unique to this suite's traffic, because
 * write_metrics is JVM-wide and other suites (or the top-users calls themselves) may also
 * land rows on the metric table.
 *
 * The consent path (attribution of an agent's calls to the granting human) is verified
 * manually against a running instance; spinning up a consent here is disproportionate.
 */
class TopUsersTest extends V600ServerSetup {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("getTopUsers")

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v7.0.0")
      val request = (v7_0_0_Request / "management" / "metrics" / "top-users").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Missing role") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v7.0.0")
      val request = (v7_0_0_Request / "management" / "metrics" / "top-users").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("error should be " + UserHasMissingRoles + CanReadMetrics)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be(UserHasMissingRoles + CanReadMetrics)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Top users by call count") {
    scenario("Two users make traffic and appear ranked with their counts", ApiEndpoint1, VersionOfApi) {
      setPropsValues("write_metrics" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)

      val trafficUrl = "/obp/v5.1.0/banks"

      // 5 calls as user1, 3 as user2 — asymmetric so a wrong grouping cannot look right.
      val requestBanks1 = (v5_1_0_Request / "banks").GET <@ (user1)
      (1 to 5).foreach(_ => makeGetRequest(requestBanks1))
      val requestBanks2 = (v5_1_0_Request / "banks").GET <@ (user2)
      (1 to 3).foreach(_ => makeGetRequest(requestBanks2))

      MetricBatchWriter.flush()

      When("We query top-users filtered to the traffic url")
      val request = (v7_0_0_Request / "management" / "metrics" / "top-users").GET <@ (user1) <<? List(
        ("url", trafficUrl))
      val response = makeGetRequest(request)
      Then("We get both users ranked by count")
      response.code should equal(200)
      val topUsers = response.body.extract[TopUsersJsonV700].top_users
      topUsers.length shouldBe 2
      topUsers.head.count shouldBe 5
      topUsers.head.user_id shouldBe resourceUser1.userId
      topUsers.head.username should not be empty
      topUsers(1).count shouldBe 3
      topUsers(1).user_id shouldBe resourceUser2.userId

      When("We query with limit=1")
      val request2 = (v7_0_0_Request / "management" / "metrics" / "top-users").GET <@ (user1) <<? List(
        ("url", trafficUrl),
        ("limit", "1"))
      val response2 = makeGetRequest(request2)
      Then("Only the busiest user is returned")
      response2.code should equal(200)
      val topUsers2 = response2.body.extract[TopUsersJsonV700].top_users
      topUsers2.length shouldBe 1
      topUsers2.head.user_id shouldBe resourceUser1.userId
    }
  }
}
