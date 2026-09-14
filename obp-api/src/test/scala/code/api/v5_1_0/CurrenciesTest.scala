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

package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.v5_1_0.Http4s510.Implementations5_1_0
import code.consumer.Consumers
import code.scope.Scope
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class CurrenciesTest extends V510ServerSetup with DefaultUsers {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.getCurrenciesAtBank))

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }
  
  feature(s"Assuring $ApiEndpoint1 works as expected - $VersionOfApi") {

    scenario(s"We Call $ApiEndpoint1", VersionOfApi, ApiEndpoint1) {
      setPropsValues("require_scopes_for_all_roles" -> "true")
      val testBank = testBankId1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(user1.get._1.key).map(_.id.get.toString).getOrElse("")
      Scope.scope.vend.addScope(testBank.value, consumerId, ApiRole.canReadFx.toString())
      val requestGet = (v5_1_0_Request / "banks" / testBank.value / "currencies" ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get a 200")
      responseGet.code should equal(200)
    }
    scenario(s"We Call $ApiEndpoint1 without a proper scope", VersionOfApi, ApiEndpoint1) {
      setPropsValues("require_scopes_for_all_roles" -> "true")
      val testBank = testBankId1
      val requestGet = (v5_1_0_Request / "banks" / testBank.value / "currencies" ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get a 403")
      responseGet.code should equal(403)
    }
    scenario(s"We Call $ApiEndpoint1 with anonymous access", VersionOfApi, ApiEndpoint1) {
      setPropsValues("require_scopes_for_all_roles" -> "true")
      val testBank = testBankId1
      val requestGet = (v5_1_0_Request / "banks" / testBank.value / "currencies" ).GET
      val responseGet = makeGetRequest(requestGet)
      And("We should get a 401")
      responseGet.code should equal(401)
    }
    
  }

}
