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
import code.api.util.ApiRole.CanGetMetricsAtOneBank
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v2_1_0.MetricsJson
import code.api.v5_0_0.APIMethods500.Implementations5_0_0
import code.entitlement.Entitlement
import code.setup.APIResponse
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

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

  lazy val bankId = testBankId1.value

  def getMetrics(consumerAndToken: Option[(Consumer, Token)], bankId: String): APIResponse = {
    val request = v5_0_0_Request / "management" / "metrics" / "banks" / bankId <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val response400 = getMetrics(None, bankId)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val response400 = getMetrics(user1, bankId)
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message contains (UserHasMissingRoles + CanGetMetricsAtOneBank) should be (true)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access with proper Role") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetMetricsAtOneBank.toString)
      val response400 = getMetrics(user1, bankId)
      Then("We should get a 200")
      response400.code should equal(200)
      response400.body.extract[MetricsJson]
    }
  }
  
}
