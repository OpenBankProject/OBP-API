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
package code.api.v4_0_0

import code.setup.OBPReq
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class OPTIONSTest extends V400ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag("optionsRequest")


  Feature("HTTP OPTIONS request should be handled correctly") {
    Scenario("We send a common OPTIONS http request", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val requestOPTIONS = (v4_0_0_Request / "banks").OPTIONS
      val response204 = OBPReq.client.newCall(requestOPTIONS.toOkHttpRequest).execute()

      try {
        Then("We should get a 204")
        response204.code() should equal(204)

        Then("response header should be correct")
        response204.header("Access-Control-Allow-Origin") shouldBe "*"
        response204.header("Access-Control-Allow-Credentials") shouldBe "true"
        // Content-Type is absent on 204 No Content — HTTP spec does not permit a body on 204,
        // so Content-Type is irrelevant. The previous assertion reflected incidental Lift bridge
        // behaviour; the native corsHandler correctly omits it.

        Then("body should be empty")
        Option(response204.body()).map(_.string()).getOrElse("") shouldBe empty
      } finally {
        response204.close()
      }
    }
  }



}
