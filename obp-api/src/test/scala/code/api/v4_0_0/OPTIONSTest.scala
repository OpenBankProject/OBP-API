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

import com.openbankproject.commons.util.ApiVersion
import dispatch.{Http, as}
import org.asynchttpclient.Response
import org.scalatest.Tag

import scala.concurrent.Await
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

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


  feature("HTTP OPTIONS request should be handled correctly") {
    scenario("We send a common OPTIONS http request", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val requestOPTIONS = (v4_0_0_Request / "banks").OPTIONS
      val response204: Response = Await.result({
        Http.default(requestOPTIONS > as.Response(p => p))
      }, Duration.Inf)

      Then("We should get a 204")
      response204.getStatusCode() should equal(204)

      Then("response header should be correct")
      response204.getHeader("Access-Control-Allow-Origin") shouldBe "*"
      response204.getHeader("Access-Control-Allow-Credentials") shouldBe "true"
      response204.getHeader("Content-Type") shouldBe "text/plain;charset=utf-8"

      Then("body should be empty")
      response204.getResponseBody shouldBe empty
    }
  }



}
