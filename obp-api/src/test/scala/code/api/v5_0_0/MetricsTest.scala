/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package code.api.v5_0_0

import code.api.util.APIUtil.OAuth._
import org.json4s.jvalue2extractable
import code.api.util.ApiRole.CanGetMetricsAtOneBank
import code.api.util.ErrorMessages.{UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.v2_1_0.MetricsJson
import code.api.v5_0_0.Http4s500.Implementations5_0_0
import code.entitlement.Entitlement
import code.metrics.{APIMetrics, MetricBatchWriter}
import code.setup.APIResponse
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

import java.util.Date

class MetricsTest extends V500ServerSetup {
  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_0_0.getMetricsAtBank))

  lazy val apiEndpointName = nameOf(Implementations5_0_0.getMetricsAtBank)
  lazy val versionName = ApiVersion.v5_0_0.toString

  lazy val bankId = testBankId1.value

  def getMetrics(consumerAndToken: Option[(Consumer, Token)], bankId: String): APIResponse = {
    val request = v5_0_0_Request / "management" / "metrics" / "banks" / bankId <@(consumerAndToken)
    makeGetRequest(request)
  }

  Feature(s"test $apiEndpointName version $versionName - Unauthorized access") {
    Scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val response400 = getMetrics(None, bankId)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }
  Feature(s"test $apiEndpointName version $versionName - Authorized access") {
    Scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val response400 = getMetrics(user1, bankId)
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message contains (UserHasMissingRoles + CanGetMetricsAtOneBank) should be (true)
    }
  }
  Feature(s"test $apiEndpointName version $versionName - Authorized access with proper Role") {
    Scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetMetricsAtOneBank.toString)
      val response400 = getMetrics(user1, bankId)
      Then("We should get a 200")
      response400.code should equal(200)
      response400.body.extract[MetricsJson]
    }
    scenario("We ask for another bank's metrics with a bank_id query parameter", ApiEndpoint1, VersionOfApi) {
      Given("A call made at another bank, and the Role at this bank only")
      val otherBankId = testBankId2.value
      val appName = "metrics-at-bank-other-bank-test"
      APIMetrics.apiMetrics.vend.saveMetric(
        resourceUser1.userId, s"/obp/v5.0.0/banks/$otherBankId/accounts", new Date(), 10L, "metrics-test-user",
        appName, "metrics-test@example.com", "metrics-test-consumer", "getAccounts", "v5.0.0", "GET",
        Some(200), "metrics-at-bank-other-bank", "", "127.0.0.1", "127.0.0.1", "", "", "", "", "", "")
      MetricBatchWriter.flush()
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetMetricsAtOneBank.toString)
      When(s"We make a request $ApiEndpoint1 with bank_id=$otherBankId")
      val request = (v5_0_0_Request / "management" / "metrics" / "banks" / bankId).GET <@(user1) <<?
        List(("bank_id", otherBankId), ("app_name", appName))
      val response = makeGetRequest(request)
      Then("We should get a 200 without the other bank's metrics")
      response.code should equal(200)
      response.body.extract[MetricsJson].metrics.filter(_.url.contains(s"banks/$otherBankId")) should be (Nil)
    }
  }

}
