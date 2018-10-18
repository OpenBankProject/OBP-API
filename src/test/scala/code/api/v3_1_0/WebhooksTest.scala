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
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.accountWebhookPutJson
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.util.{ApiTrigger, ApiVersion}
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class WebhooksTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.getAccountWebhooks))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.createAccountWebhook))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.enableDisableAccountWebhook))

  val postJson = SwaggerDefinitionsJSON.accountWebhookPostJson
  val postJsonIncorrectTriggerName = SwaggerDefinitionsJSON.accountWebhookPostJson.copy(trigger_name = "I am not a valid trigger name")

  feature("Create an Account Web Hook v3.1.0 - Unauthorized access")
  {
    scenario("We will try to create the web hook without user credentials", ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-web-hooks").POST
      val response310 = makePostRequest(request310, write(postJson))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
  }

  feature("Create an Account Web Hook v3.1.0 - Authorized access")
  {
    scenario("We will try to create the web hook without a proper Role " + canCreateWebhook, ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      When("We make a request v3.1.0 without a Role " + canCreateWebhook)
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-web-hooks").POST <@(user1)
      val response310 = makePostRequest(request310, write(postJson))
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateWebhook)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanCreateWebhook)
    }

    scenario("We will try to create the web hook with a proper Role " + canCreateWebhook + " but without proper trigger name", ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateWebhook.toString)
      When("We make a request v3.1.0 with a Role " + canCreateWebhook)
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-web-hooks").POST <@(user1)
      val response310 = makePostRequest(request310, write(postJsonIncorrectTriggerName))
      Then("We should get a 400")
      response310.code should equal(400)
      val failMsg = IncorrectTriggerName + postJsonIncorrectTriggerName.trigger_name + ". Possible values are " + ApiTrigger.availableTriggers.sorted.mkString(", ")
      And("error should be " + failMsg)
      response310.body.extract[ErrorMessage].error should include (failMsg)
    }

    scenario("We will try to create the web hook with a proper Role " + canCreateWebhook, ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateWebhook.toString)
      When("We make a request v3.1.0 with a Role " + canCreateWebhook)
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-web-hooks").POST <@(user1)
      val response310 = makePostRequest(request310, write(postJson))
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[AccountWebhookJson]
    }

  }

  feature("Get Account Web Hooks v3.1.0 - Unauthorized access") {
    scenario("We will try to get web hooks without user credentials", ApiEndpoint1, VersionOfApi) {
      val bankId = randomBankId
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "banks" / bankId / "account-web-hooks").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
  }

  feature("Get Account Web Hooks v3.1.0 - Authorized access") {
    scenario("We will try to get web hooks without a proper Role " + canGetWebhooks, ApiEndpoint1, VersionOfApi) {
      val bankId = randomBankId
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "banks" / bankId / "account-web-hooks").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetWebhooks)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanGetWebhooks)
    }
    scenario("We will try to get web hooks with a proper Role " + canGetWebhooks, ApiEndpoint1, VersionOfApi) {
      val bankId = randomBankId
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetWebhooks.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "banks" / bankId / "account-web-hooks").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[AccountWebhooksJson]
    }
  }


  feature("Update an Account Web Hook v3.1.0 - Authorized access") {
    scenario("We will try to Update an Account Web Hook without a proper Role " + canUpdateWebhook, ApiEndpoint3, VersionOfApi) {
      val bankId = randomBankId
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-web-hooks").PUT <@(user1)
      val response310 = makePutRequest(request310, write(accountWebhookPutJson))
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanUpdateWebhook)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanUpdateWebhook)
    }
    scenario("We will try to Update an Account Web Hook with a proper Role " + canUpdateWebhook, ApiEndpoint3, VersionOfApi) {
      val bankId = randomBankId
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateWebhook.toString)
      When("We create a web hook with a Role " + canCreateWebhook)
      val createRequest310 = (v3_1_0_Request / "banks" / bankId / "account-web-hooks").POST <@(user1)
      val createResponse310 = makePostRequest(createRequest310, write(postJson))
      Then("We should get a 200")
      createResponse310.code should equal(200)
      val id = createResponse310.body.extract[AccountWebhookJson].account_webhook_id
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanUpdateWebhook.toString)
      When("We update the web hook")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-web-hooks").PUT <@(user1)
      val response310 = makePutRequest(request310, write(accountWebhookPutJson.copy(account_webhook_id = id)))
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[AccountWebhooksJson]
    }
  }


}
