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

package code.api.v4_0_0

import org.json4s._
import code.api.Constant.{INCOMING_SETTLEMENT_ACCOUNT_ID, OUTGOING_SETTLEMENT_ACCOUNT_ID}
import com.openbankproject.commons.model.{AccountId, BankId, ErrorMessage}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.bankJson400
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanCreateBank
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.util.{ApiRole, ErrorMessages, NewStyle}
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.entitlement.Entitlement
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

class BankTests extends V400ServerSetup with DefaultUsers {

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
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createBank))

  feature("Assuring that endpoint createBank works as expected - v4.0.0") {

    scenario("We try to consume endpoint createBank - Anonymous access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "banks").POST
      val responseGet = makePostRequest(requestGet, write(bankJson400))
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.AuthenticatedUserIsRequired)
      responseGet.code should equal(401)
      responseGet.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("We try to consume endpoint createBank without proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "banks").POST <@ (user1)
      val responseGet = makePostRequest(requestGet, write(bankJson400))
      Then("We should get a 403")
      And("We should get a message: " + s"$CanCreateBank entitlement required")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanCreateBank)
    }

    scenario("We try to consume endpoint createBank with proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateBank.toString)
      And("We make the request")
      val requestGet = (v4_0_0_Request / "banks").POST <@ (user1)
      val before = Await.result(
        NewStyle.function.getEntitlementsByUserId(resourceUser1.userId, None), 10.seconds
      ).exists(e => e.roleName == ApiRole.CanCreateEntitlementAtOneBank.toString && e.bankId == bankJson400.id)
      val response = makePostRequest(requestGet, write(bankJson400))
      val after = Await.result(
        NewStyle.function.getEntitlementsByUserId(resourceUser1.userId, None), 10.seconds
      ).exists(e => e.roleName == ApiRole.CanCreateEntitlementAtOneBank.toString && e.bankId == bankJson400.id)
      Then("We should get a 201")
      before should equal(false) // Before we create a bank there is no role CanCreateEntitlementAtOneBank
      after should equal(true) // After we create a bank there is a role CanCreateEntitlementAtOneBank
      response.code should equal(201)
      Then("Default settlement accounts should be created")
      val defaultOutgoingAccount = Await.result(
        NewStyle.function.checkBankAccountExists(BankId(bankJson400.id), AccountId(OUTGOING_SETTLEMENT_ACCOUNT_ID), None), 10.seconds)
      val defaultIncomingAccount = Await.result(
        NewStyle.function.checkBankAccountExists(BankId(bankJson400.id), AccountId(INCOMING_SETTLEMENT_ACCOUNT_ID), None), 10.seconds)
      defaultOutgoingAccount._1.accountId.value should equal(OUTGOING_SETTLEMENT_ACCOUNT_ID)
      defaultIncomingAccount._1.accountId.value should equal(INCOMING_SETTLEMENT_ACCOUNT_ID)
    }
  }


 }