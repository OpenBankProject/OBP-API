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

import code.api.Constant._
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.CannotFindAccountAccess
import code.api.v2_0_0.BasicAccountsJSON
import code.api.v3_1_0.AccountsBalancesV310Json
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, BankIdAccountIdViewId, CreateViewJson, View}
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

import scala.util.Random

class AccountBalanceTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getBankAccountsBalancesForCurrentUser))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getBankAccountBalancesForCurrentUser))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)

  feature(s"test $ApiEndpoint1 and $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint1, ApiEndpoint2) {
      val requestGetAccountBalances = (v4_0_0_Request / "banks" / bankAccount.bank_id / "accounts" / bankAccount.id / "balances").GET <@ (user1)
      val responseGetAccountBalances = makeGetRequest(requestGetAccountBalances)
      Then("We should get a 200")
      responseGetAccountBalances.code should equal(200)

      val requestGetAccountsBalances = (v4_0_0_Request / "banks" / bankId / "balances").GET <@ (user1)
      val responseGetAccountsBalances = makeGetRequest(requestGetAccountsBalances)
      Then("We should get a 200")
      responseGetAccountsBalances.code should equal(200)
    }
    scenario("We will call the endpoint with user2 who has no account access ", VersionOfApi, ApiEndpoint1, ApiEndpoint2) {
      val requestGetAccountBalances = (v4_0_0_Request / "banks" / bankAccount.bank_id / "accounts" / bankAccount.id / "balances").GET <@ (user2)
      val responseGetAccountBalances = makeGetRequest(requestGetAccountBalances)
      Then("We should get a 200")
      responseGetAccountBalances.code should equal(400)
      responseGetAccountBalances.body.toString contains(CannotFindAccountAccess) should be (true)

      val requestGetAccountsBalances = (v4_0_0_Request / "banks" / bankId / "balances").GET <@ (user2)
      val responseGetAccountsBalances = makeGetRequest(requestGetAccountsBalances)
      Then("We should get a 200")
      responseGetAccountsBalances.code should equal(200)
      responseGetAccountsBalances.body.extract[AccountsBalancesJsonV400].accounts.length should equal(0)
    }
  }

  // Creates a custom view on an account with the given permissions and gives user2 access to it
  private def grantCustomViewToUser2(bankId: String, accountId: String, viewName: String, allowedActions: List[String]): View = {
    val bankIdAccountId = BankIdAccountId(BankId(bankId), AccountId(accountId))
    val createViewJson = CreateViewJson(
      name = viewName,
      description = "Used by AccountBalanceTest",
      metadata_view = viewName,
      is_public = false,
      which_alias_to_use = "",
      hide_metadata_if_alias_used = false,
      allowed_actions = allowedActions
    )
    val view = Views.views.vend.createCustomView(bankIdAccountId, createViewJson).openOrThrowException("Could not create the custom view")
    Views.views.vend.grantAccessToCustomView(
      BankIdAccountIdViewId(BankId(bankId), AccountId(accountId), view.viewId), resourceUser2
    ).openOrThrowException("Could not grant the custom view")
    view
  }

  feature(s"test $ApiEndpoint1 and $ApiEndpoint2 version $VersionOfApi - balances follow the user's view permissions") {
    scenario("A custom view without the balance permission does not show the balance, even when the same view id allows it on another account",
      VersionOfApi, ApiEndpoint1, ApiEndpoint2) {
      val accounts = getPrivateAccountsViaEndpoint(bankId, user1).body.extract[BasicAccountsJSON].accounts
      accounts.length should be >= 2
      val accountWithBalance = accounts(0)
      val accountWithoutBalance = accounts(1)

      Given("user2 has a custom view on one account that can see the balance but not the label or routings")
      val viewName = s"_balance${Random.alphanumeric.take(8).mkString.toLowerCase}"
      grantCustomViewToUser2(bankId, accountWithBalance.id, viewName,
        List(CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT, CAN_SEE_BANK_ACCOUNT_BALANCE))
      And("a custom view with the same id on a second account that cannot see the balance")
      grantCustomViewToUser2(bankId, accountWithoutBalance.id, viewName,
        List(CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT, CAN_SEE_BANK_ACCOUNT_LABEL))

      When("user2 gets the balances at the bank through v3.1.0")
      val response310 = makeGetRequest((baseRequest / "obp" / "v3.1.0" / "banks" / bankId / "balances").GET <@ (user2))
      Then("only the first account is returned, without its label or routings")
      response310.code should equal(200)
      val accounts310 = response310.body.extract[AccountsBalancesV310Json].accounts
      accounts310.map(_.id) should equal(List(accountWithBalance.id))
      accounts310.head.label should equal("")
      accounts310.head.account_routings should equal(Nil)

      When("user2 gets the balances at the bank through v4.0.0")
      val response400 = makeGetRequest((v4_0_0_Request / "banks" / bankId / "balances").GET <@ (user2))
      Then("only the first account is returned")
      response400.code should equal(200)
      response400.body.extract[AccountsBalancesJsonV400].accounts.map(_.account_id) should equal(List(accountWithBalance.id))

      When("user2 gets the balances of the second account through v4.0.0")
      val responseOneAccount = makeGetRequest((v4_0_0_Request / "banks" / bankId / "accounts" / accountWithoutBalance.id / "balances").GET <@ (user2))
      Then("the account is not found among the accounts user2 may see balances on")
      responseOneAccount.code should equal(400)
      responseOneAccount.body.toString contains (CannotFindAccountAccess) should be(true)
    }
  }
}
