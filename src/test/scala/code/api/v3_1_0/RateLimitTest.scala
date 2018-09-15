package code.api.v3_1_0

import code.api.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole, ApiVersion}
import code.api.util.ApiRole.CanSetCallLimit
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.consumer.Consumers
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.github.sebruck.EmbeddedRedis
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class RateLimitTest extends V310ServerSetup with EmbeddedRedis {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations3_1_0.callsLimit))

  val callLimitJson1 = CallLimitJson(
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )
  val callLimitJson2 = CallLimitJson(
    per_minute_call_limit = "1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )

  feature("Rate Limit - v3.1.0")
  {

    scenario("We will try to set calls limit per minute for a Consumer - unauthorized access", ApiEndpoint, VersionOfApi) {
      When("We make a request v3.1.0")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "calls_limit").PUT
      val response310 = makePutRequest(request310, write(callLimitJson1))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
    scenario("We will try to set calls limit per minute without a proper Role " + ApiRole.canGetConsumers, ApiEndpoint, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + ApiRole.canSetCallLimit)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "calls_limit").PUT <@(user1)
      val response310 = makePutRequest(request310, write(callLimitJson1))
      Then("We should get a 403")
      org.scalameta.logger.elem(response310.body)
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanSetCallLimit)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanSetCallLimit)
    }
    scenario("We will try to set calls limit per minute with a proper Role " + ApiRole.canGetConsumers, ApiEndpoint, VersionOfApi) {
      When("We make a request v3.1.0 with a Role " + ApiRole.canSetCallLimit)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimit.toString)
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "calls_limit").PUT <@(user1)
      val response310 = makePutRequest(request310, write(callLimitJson1))
      Then("We should get a 200")
      org.scalameta.logger.elem(response310.body)
      response310.code should equal(200)
      response310.body.extract[CallLimitJson]
    }
    scenario("We will set calls limit per minute for a Consumer", ApiEndpoint, VersionOfApi) {
      withRedis() {
        port =>
          if(APIUtil.getPropsAsBoolValue("use_consumer_limits", false)) {
            When("We make a request v3.1.0 with a Role " + ApiRole.canSetCallLimit)
            val Some((c, _)) = user1
            val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
            Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimit.toString)
            val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "calls_limit").PUT <@(user1)
            val response01 = makePutRequest(request310, write(callLimitJson2))
            Then("We should get a 200")
            response01.code should equal(200)

            When("We make the first call after update")
            val response02 = makePutRequest(request310, write(callLimitJson2))
            Then("We should get a 200")
            response02.code should equal(200)

            When("We make the second call after update")
            val response03 = makePutRequest(request310, write(callLimitJson2))
            Then("We should get a 429")
            response03.code should equal(429)
          }
      }
        succeed
    }
  }

}