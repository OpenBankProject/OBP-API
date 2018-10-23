/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
*/
package code.api.v3_1_0

import code.api.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanReadCallLimits, CanSetCallLimits}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.{APIUtil, ApiRole, ApiVersion}
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.consumer.Consumers
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class RateLimitTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations3_1_0.callsLimit))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getCallsLimit))

  val callLimitJson1 = CallLimitPostJson(
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )
  val callLimitJson2 = CallLimitPostJson(
    per_minute_call_limit = "1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )

  feature("Rate Limit - " + ApiEndpoint + " - " + VersionOfApi)
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
    scenario("We will try to set calls limit per minute without a proper Role " + ApiRole.canSetCallLimits, ApiEndpoint, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + ApiRole.canSetCallLimits)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "calls_limit").PUT <@(user1)
      val response310 = makePutRequest(request310, write(callLimitJson1))
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanSetCallLimits)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanSetCallLimits)
    }
    scenario("We will try to set calls limit per minute with a proper Role " + ApiRole.canSetCallLimits, ApiEndpoint, VersionOfApi) {
      When("We make a request v3.1.0 with a Role " + ApiRole.canSetCallLimits)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimits.toString)
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "calls_limit").PUT <@(user1)
      val response310 = makePutRequest(request310, write(callLimitJson1))
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[CallLimitJson]
    }
    scenario("We will set calls limit per minute for a Consumer", ApiEndpoint, VersionOfApi) {
      if(APIUtil.getPropsAsBoolValue("use_consumer_limits", false)) {
        When("We make a request v3.1.0 with a Role " + ApiRole.canSetCallLimits)
        val Some((c, _)) = user1
        val consumerId: Long = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimits.toString)
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

        // Revert to initial state
        Consumers.consumers.vend.updateConsumerCallLimits(consumerId, Some("-1"), Some("-1"), Some("-1"), Some("-1"), Some("-1"))
      }
    }
  }

  feature("Rate Limit - " + ApiEndpoint2 + " - " + VersionOfApi)
  {
    scenario("We will try to get calls limit per minute for a Consumer - unauthorized access", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "calls_limit").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
    scenario("We will try to get calls limit per minute without a proper Role " + ApiRole.canReadCallLimits, ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + ApiRole.canReadCallLimits)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "calls_limit").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanReadCallLimits)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanReadCallLimits)
    }
    scenario("We will try to get calls limit per minute with a proper Role " + ApiRole.canReadCallLimits, ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0 with a Role " + ApiRole.canReadCallLimits)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.id.get).getOrElse(0)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanReadCallLimits.toString)
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "calls_limit").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[CallLimitJson]
    }
  }

}