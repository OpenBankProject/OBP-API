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

import code.api.Constant
import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.APIUtil
import code.api.util.ErrorMessages._
import code.api.v2_1_0.TransactionRequestWithChargeJSONs210
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId}
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class TransactionRequestTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.getTransactionRequests))

  feature("Get Transaction Requests - v3.1.0")
  {
    scenario("We will Get Transaction Requests - user is NOT logged in", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = bankAccount.views_available.map(_.id).headOption.getOrElse("owner")
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "transaction-requests").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will Get Transaction Requests - user is logged in", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = bankAccount.views_available.map(_.id).headOption.getOrElse("owner")
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "transaction-requests").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[TransactionRequestWithChargeJSONs210]
    }
    scenario("We will try to Get Transaction Requests for someone else account - user is logged in", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val bankId = randomBankId
      val account = createAccountRelevantResource(Some(resourceUser1), BankId(bankId), AccountId(APIUtil.generateUUID()), "EUR")
      val request310 = (
        v3_1_0_Request / "banks" / bankId / "accounts" / account.accountId.value 
        / Constant.CUSTOM_OWNER_VIEW_ID / "transaction-requests").GET <@(user2)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserNoPermissionAccessView)
      response310.body.extract[ErrorMessage].message should equal (UserNoPermissionAccessView)
    }
  }

}
