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
package code.api.v3_1_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.ErrorMessages.NotImplemented
import code.api.util.APIUtil
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class ObpApiLoopbackTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.getObpConnectorLoopback))
  feature(nameOf(Implementations3_1_0.getObpConnectorLoopback))
  {
    scenario("Success Test", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "connector" / "loopback").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      val connectorVersion = APIUtil.getPropsValue("connector").openOrThrowException("connector props filed `connector` not set")
      val errorMessage = s"${NotImplemented}for connector ${connectorVersion}"
      And("error should be " + errorMessage)
      response310.body.extract[ErrorMessage].message should equal (errorMessage)
    }
    
  }
}
