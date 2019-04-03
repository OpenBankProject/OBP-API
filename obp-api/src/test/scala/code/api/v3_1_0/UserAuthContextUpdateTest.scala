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

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiVersion
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.context.UserAuthContextUpdateStatus
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.language.postfixOps

class UserAuthContextUpdateTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createUserAuthContextUpdate))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.answerUserAuthContextUpdateChallenge))

  val postUserAuthContextJson = SwaggerDefinitionsJSON.postUserAuthContextJson
  val postUserAuthContextJson2 = SwaggerDefinitionsJSON.postUserAuthContextJson.copy(key="TOKEN")

  feature("Create User Auth Context Update Request v3.1.0") {
    scenario("We will call the Create endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We try to create the UserAuthContext v3.1.0")
      val requestUserAuthContextUpdate310 = (v3_1_0_Request / "users" / "current" / "auth-context-updates").POST <@(user1)
      val responseUserAuthContextUpdate310 = makePostRequest(requestUserAuthContextUpdate310, write(postUserAuthContextJson))
      Then("We should get a 201")
      responseUserAuthContextUpdate310.code should equal(201)
      responseUserAuthContextUpdate310.body.extract[UserAuthContextUpdateJson]
    }
    scenario("We will call the Answer endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We try to answer the UserAuthContext v3.1.0")
      
      val createRequestUserAuthContextUpdate310 = (v3_1_0_Request / "users" / "current" / "auth-context-updates").POST <@(user1)
      val createResponseUserAuthContextUpdate310 = makePostRequest(createRequestUserAuthContextUpdate310, write(postUserAuthContextJson))
      Then("We should get a 201")
      createResponseUserAuthContextUpdate310.code should equal(201)
      val authContextUpdateId = createResponseUserAuthContextUpdate310.body.extract[UserAuthContextUpdateJson].user_auth_context_update_id
      val wrongAnswerJson = PostUserAuthContextUpdateJsonV310(answer = "1234567")
      
      val requestUserAuthContextUpdate310 = (v3_1_0_Request / "users" / "current" / "auth-context-updates" / authContextUpdateId / "challenge").POST <@(user1)
      val responseUserAuthContextUpdate310 = makePostRequest(requestUserAuthContextUpdate310, write(wrongAnswerJson))
      Then("We should get a 200")
      responseUserAuthContextUpdate310.code should equal(200)
      val status = responseUserAuthContextUpdate310.body.extract[UserAuthContextUpdateJson].status
      status should equal(UserAuthContextUpdateStatus.REJECTED.toString)
    }
  }
}
