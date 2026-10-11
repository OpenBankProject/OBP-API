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
import code.api.util.ErrorMessages.CounterpartyNotFound
import code.metadata.counterparties.Counterparties
import code.api.v2_0_0.BasicAccountsJSON
import com.openbankproject.commons.model.{AccountId, BankId, ErrorMessage}
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

  feature("Get Other Account by Id only returns counterparties of the account in the URL - V300") {
    scenario("A counterparty of the account in the URL is returned", VersionOfApi, ApiEndpoint2) {
      Given("A counterparty that belongs to the account in the URL")
      val bankId = randomBankId
      val accountId = randomPrivateAccountId(bankId)
      val counterparty = createCounterparty(bankId, accountId, accountId, true, resourceUser1.userId)
      Counterparties.counterparties.vend.getOrCreateMetadata(
        BankId(bankId), AccountId(accountId), counterparty.counterpartyId, counterparty.name)

      When("We ask for it through that account")
      val response = getOtherAccountByIdForBankAccount(bankId, accountId, SYSTEM_OWNER_VIEW_ID, counterparty.counterpartyId, user1)

      Then("We should get a 200 with that counterparty")
      response.code should equal(200)
      response.body.extract[OtherAccountJsonV300].id should equal(counterparty.counterpartyId)
    }

    scenario("A counterparty of a different account is not found", VersionOfApi, ApiEndpoint2) {
      Given("A counterparty that belongs to a different account than the one in the URL")
      val bankId = randomBankId
      val accountIds = getPrivateAccounts(bankId, user1).body.extract[BasicAccountsJSON].accounts.map(_.id)
      accountIds.size should be >= 2
      val accountInUrl = accountIds(0)
      val otherAccount = accountIds(1)
      val counterparty = createCounterparty(bankId, otherAccount, otherAccount, true, resourceUser1.userId)
      Counterparties.counterparties.vend.getOrCreateMetadata(
        BankId(bankId), AccountId(otherAccount), counterparty.counterpartyId, counterparty.name)

      When("We ask for it through the account in the URL")
      val response = getOtherAccountByIdForBankAccount(bankId, accountInUrl, SYSTEM_OWNER_VIEW_ID, counterparty.counterpartyId, user1)

      Then("We should get a 400 CounterpartyNotFound, even though the caller can also see the other account")
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(CounterpartyNotFound)
    }
  }

}
