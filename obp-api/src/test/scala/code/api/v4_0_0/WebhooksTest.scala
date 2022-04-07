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

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.ApiRole._
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class WebhooksTest extends V400ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createSystemAccountNotificationWebhook))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.createBankAccountNotificationWebhook))

  val postJson = SwaggerDefinitionsJSON.accountNotificationWebhookPostJson
  val postJsonIncorrectHttpMethod = SwaggerDefinitionsJSON.accountNotificationWebhookPostJson.copy(http_method="GET")
  val postJsonIncorrectHttpProtocol = SwaggerDefinitionsJSON.accountNotificationWebhookPostJson.copy(http_protocol="HTTP/1.0")

  feature("createBankAccountNotificationWebhook - Unauthorized access")
  {
    scenario(s"We will try to create the web hook without user credentials $ApiEndpoint1", ApiEndpoint1, VersionOfApi) {
      val bankId = randomBankId
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request  / "web-hooks" / "account" / "notifications" / "on-create-transaction").POST
      val response400 = makePostRequest(request400, write(postJson))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    
    scenario(s"We will try to create the web hook without user credentials $ApiEndpoint2", ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "web-hooks" / "account" / "notifications" / "on-create-transaction").POST
      val response400 = makePostRequest(request400, write(postJson))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    
  }

  feature(s"createSystemAccountNotificationWebhook - Authorized access $ApiEndpoint1")
  {
    scenario("We will try to create the web hook without a proper Role " + canCreateSystemAccountNotificationWebhook, ApiEndpoint1, VersionOfApi) {
      val bankId = randomBankId
      When("We make a request v4.0.0 without a Role " + canCreateSystemAccountNotificationWebhook)
      val request400 = (v4_0_0_Request / "web-hooks" / "account" / "notifications" / "on-create-transaction").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postJson))
      Then("We should get a 403")
      response400.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateSystemAccountNotificationWebhook)
      val errorMessage = response400.body.extract[ErrorMessage].message
      errorMessage contains (UserHasMissingRoles) should be (true)
      errorMessage contains (CanCreateSystemAccountNotificationWebhook.toString()) should be (true)
    }

    scenario("We will try to create the web hook with a proper Role " + canCreateSystemAccountNotificationWebhook + " but without proper http method ", ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemAccountNotificationWebhook.toString)
      When("We make a request v4.0.0 with a Role " + canCreateSystemAccountNotificationWebhook)
      val request400 = (v4_0_0_Request / "web-hooks" / "account" / "notifications" / "on-create-transaction").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postJsonIncorrectHttpMethod))
      Then("We should get a 400")
      response400.code should equal(400)
      val failMsg = InvalidHttpMethod
      And("error should be " + failMsg)
      response400.body.extract[ErrorMessage].message should include (failMsg)
    }

    scenario("We will try to create the web hook with a proper Role " + canCreateSystemAccountNotificationWebhook + " but without proper http protocal ", ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemAccountNotificationWebhook.toString)
      When("We make a request v4.0.0 with a Role " + canCreateSystemAccountNotificationWebhook)
      val request400 = (v4_0_0_Request / "web-hooks" / "account" / "notifications" / "on-create-transaction").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postJsonIncorrectHttpProtocol))
      Then("We should get a 400")
      response400.code should equal(400)
      val failMsg = InvalidHttpProtocol
      And("error should be " + failMsg)
      response400.body.extract[ErrorMessage].message should include (failMsg)
    }

    scenario("We will try to create the web hook with a proper Role " + canCreateSystemAccountNotificationWebhook, ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemAccountNotificationWebhook.toString)
      When("We make a request v4.0.0 with a Role " + canCreateSystemAccountNotificationWebhook)
      val request400 = (v4_0_0_Request / "web-hooks" / "account" / "notifications" / "on-create-transaction").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postJson))
      Then("We should get a 201")
      response400.code should equal(201)
      response400.body.extract[SystemAccountNotificationWebhookJson]
    }

  }
  
  feature(s"createBankAccountNotificationWebhook - Authorized access $ApiEndpoint2")
  {
    scenario("We will try to create the web hook without a proper Role " + canCreateAccountNotificationWebhookAtOneBank, ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      When("We make a request v4.0.0 without a Role " + canCreateAccountNotificationWebhookAtOneBank)
      val request400 = (v4_0_0_Request / "banks" / bankId / "web-hooks" / "account" / "notifications" / "on-create-transaction").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postJson))
      Then("We should get a 403")
      response400.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateSystemAccountNotificationWebhook)
      val errorMessage = response400.body.extract[ErrorMessage].message
      errorMessage contains (UserHasMissingRoles) should be (true)
      errorMessage contains (CanCreateAccountNotificationWebhookAtOneBank.toString()) should be (true)
    }

    scenario("We will try to create the web hook with a proper Role " + canCreateAccountNotificationWebhookAtOneBank + " but without proper http method ", ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateAccountNotificationWebhookAtOneBank.toString)
      When("We make a request v4.0.0 with a Role " + canCreateAccountNotificationWebhookAtOneBank)
      val request400 = (v4_0_0_Request / "banks" / bankId / "web-hooks" / "account" / "notifications" / "on-create-transaction").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postJsonIncorrectHttpMethod))
      Then("We should get a 400")
      response400.code should equal(400)
      val failMsg = InvalidHttpMethod
      And("error should be " + failMsg)
      response400.body.extract[ErrorMessage].message should include (failMsg)
    }

    scenario("We will try to create the web hook with a proper Role " + canCreateAccountNotificationWebhookAtOneBank + " but without proper http protocal ", ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateAccountNotificationWebhookAtOneBank.toString)
      When("We make a request v4.0.0 with a Role " + canCreateAccountNotificationWebhookAtOneBank)
      val request400 = (v4_0_0_Request / "banks" / bankId / "web-hooks" / "account" / "notifications" / "on-create-transaction").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postJsonIncorrectHttpProtocol))
      Then("We should get a 400")
      response400.code should equal(400)
      val failMsg = InvalidHttpProtocol
      And("error should be " + failMsg)
      response400.body.extract[ErrorMessage].message should include (failMsg)
    }

    scenario("We will try to create the web hook with a proper Role " + canCreateAccountNotificationWebhookAtOneBank, ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateAccountNotificationWebhookAtOneBank.toString)
      When("We make a request v4.0.0 with a Role " + canCreateAccountNotificationWebhookAtOneBank)
      val request400 = (v4_0_0_Request / "banks" / bankId / "web-hooks" / "account" / "notifications" / "on-create-transaction").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postJson))
      Then("We should get a 201")
      response400.code should equal(201)
      response400.body.extract[BankAccountNotificationWebhookJson]
    }

  }

}
