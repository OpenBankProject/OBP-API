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

package code.api.v3_0_0

import code.api.Constant._
import com.openbankproject.commons.util.ApiVersion
import code.api.v3_0_0.Http4s300.Implementations3_0_0
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class CounterpartyTest extends V300ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_0_0.getOtherAccountsForBankAccount))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_0_0.getOtherAccountByIdForBankAccount))
  
  feature("Get Other Accounts of one Account.and Get Other Account by Id. - V300") {
    scenario("prepare all the need parameters", VersionOfApi, ApiEndpoint1, ApiEndpoint2) {
      Given("We prepare all the parameters, just check the response")
      val bankId = randomBankId
      val accountId = randomPrivateAccountId(bankId)
      val viewId = SYSTEM_OWNER_VIEW_ID
      val loginedUser = user1
      
      When("we call the `Get Other Accounts of one Account.`")
      val httpResponseAccounts = getOtherAccountsForBankAccount(bankId,accountId,viewId,user1)

      Then("We should get a 200 and check the response body")
      httpResponseAccounts.code should equal(200)
      val otherAccountsJson = httpResponseAccounts.body.extract[OtherAccountsJsonV300]
      
      
      Then("We random get a otherAccountId ")
      val otherAccountId=otherAccountsJson.other_accounts.head.id
      
      Then("we call the `Get Other Account by Id.`")
      val httpResponseAccount = getOtherAccountByIdForBankAccount(bankId,accountId,viewId,otherAccountId,user1)
      
      
      Then("We should get a 200 and check the response body")
      httpResponseAccount.code should equal(200)
      httpResponseAccount.body.extract[OtherAccountJsonV300]
    }
  }

}
