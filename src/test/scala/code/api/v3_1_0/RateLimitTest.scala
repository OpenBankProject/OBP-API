package code.api.v3_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.consumer.Consumers
import code.entitlement.Entitlement
import com.github.sebruck.EmbeddedRedis
import net.liftweb.json.Serialization.write

class RateLimitTest extends V310ServerSetup with EmbeddedRedis {

  val callLimitJson = CallLimitJson(
    per_minute_call_limit = "1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )

  feature("Rate Limit - v3.1.0")
  {
    scenario("We will set calls limit per minute for a Consumer") {
      withRedis() {
        port =>
          When("We make a request v3.1.0 with a Role " + ApiRole.canSetCallLimit)
          Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimit.toString)
          val Some((c, _)) = user1
          val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
          val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "calls_limit").GET <@(user1)
          val response01 = makePutRequest(request310, write(callLimitJson))
          Then("We should get a 200")
          response01.code should equal(200)

          When("We make the first call after update")
          val response02 = makePutRequest(request310, write(callLimitJson))
          Then("We should get a 200")
          response02.code should equal(200)

          When("We make the second call after update")
          val response03 = makePutRequest(request310, write(callLimitJson))
          Then("We should get a 429")
          response03.code should equal(429)
      }
        succeed
    }
  }

}