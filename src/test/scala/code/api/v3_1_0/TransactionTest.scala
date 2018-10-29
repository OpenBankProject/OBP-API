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
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiVersion
import code.api.util.ErrorMessages.UserNotLoggedIn
import code.api.v2_1_0.TransactionRequestWithChargeJSONs210
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class TransactionTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.getTransactionRequests))

  feature("Get Transaction by Id - v3.1.0")
  {
    scenario("We will Get Transaction by Id - user is NOT logged in", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "transactions" / transaction.id / "transaction").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
    scenario("We will Get Transaction by Id - user is logged in", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "transactions" / transaction.id / "transaction").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[TransactionRequestWithChargeJSONs210]
    }
  }

}
