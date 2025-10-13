/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
*/
package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.CanReadCallLimits
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.CallLimitPostJsonV400
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.setup.PropsReset
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.Date

class RateLimitingTest extends V510ServerSetup with PropsReset {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object ApiVersion400 extends Tag(ApiVersion.v4_0_0.toString)
  object ApiVersion510 extends Tag(ApiVersion.v5_1_0.toString)
  object ApiCallsLimit extends Tag(nameOf(Implementations5_1_0.getCallsLimit))
  
  override def beforeEach() = {
    super.beforeEach()
    setPropsValues("use_consumer_limits"->"true")
    setPropsValues("user_consumer_limit_anonymous_access"->"6000")
  }

  val yesterday = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val tomorrow = ZonedDateTime.now(ZoneId.of("UTC")).plusDays(10)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  val fromDate = Date.from(yesterday.toInstant())
  val toDate = Date.from(tomorrow.toInstant())

  val callLimitJsonInitial = CallLimitPostJsonV400(
    from_date = fromDate,
    to_date = toDate,
    api_version = None,
    api_name = None,
    bank_id = None,
    per_second_call_limit = "-1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )
  val callLimitJsonMonth: CallLimitPostJsonV400 = callLimitJsonInitial.copy(per_month_call_limit = "100")
    

  feature("Rate Limit - " + ApiCallsLimit + " - " + ApiVersion400) {

    scenario("We will try to get calls limit per minute for a Consumer - unauthorized access", ApiCallsLimit, ApiVersion510) {
      When(s"We make a request $ApiVersion510")
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      val request510 = (v5_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").GET
      val response510 = makeGetRequest(request510)
      Then("We should get a 401")
      response510.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
    scenario("We will try to get calls limit per minute without a proper Role " + ApiRole.canReadCallLimits, ApiCallsLimit, ApiVersion510) {
      When("We make a request v3.1.0 without a Role " + ApiRole.canReadCallLimits)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      val request510 = (v5_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").GET <@ (user1)
      val response510 = makeGetRequest(request510)
      Then("We should get a 403")
      response510.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanReadCallLimits)
      response510.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanReadCallLimits)
    }
    scenario("We will try to get calls limit per minute with a proper Role " + ApiRole.canReadCallLimits, ApiCallsLimit, ApiVersion510) {

      When("We make a request v5.1.0 with a Role " + ApiRole.canSetCallLimits)
      val response01 = setRateLimiting(user1, callLimitJsonMonth)
      Then("We should get a 200")
      response01.code should equal(200)

      When(s"We make a request v$ApiVersion510 with a Role " + ApiRole.canReadCallLimits)
      val Some((c, _)) = user1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanReadCallLimits.toString)
      val request510 = (v5_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").GET <@ (user1)
      val response510 = makeGetRequest(request510)
      Then("We should get a 200")
      response510.code should equal(200)
      response510.body.extract[CallLimitsJson510]

    }

  }
}