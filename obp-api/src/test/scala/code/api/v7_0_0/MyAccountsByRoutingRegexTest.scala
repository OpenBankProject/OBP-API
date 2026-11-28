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
package code.api.v7_0_0

import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, InvalidAccountRoutingAddressRegex}
import code.api.v4_0_0.ModeratedAccountsJSON400
import code.api.v6_0_0.V600ServerSetup
import code.bankconnectors.DoobieBankAccountRoutingQueries
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.jvalue2extractable
import org.scalatest.Tag

/** POST /my/accounts/account-routing-regex-query, and the v4.0.0 endpoint it replaces not being served at v7.0.0. */
class MyAccountsByRoutingRegexTest extends V600ServerSetup {

  def v7 = baseRequest / "obp" / "v7.0.0"
  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Http4s700MyAccounts.getMyAccountsByAccountRoutingRegex))

  private val query = v7 / "my" / "accounts" / "account-routing-regex-query"
  private def body(address: String) =
    s"""{"bank_id": null, "account_routing": {"scheme": "AccountNumber", "address": "$address"}}"""
  private def message(r: code.setup.APIResponse) = r.body.extract[ErrorMessage].message

  /** A routing address on a test account user1 holds the owner view of, unique to this test run. */
  private def routedAccount(): String = {
    val prefix = "MYREGEX" + APIUtil.generateUUID().replace("-", "").take(12).toUpperCase
    DoobieBankAccountRoutingQueries.create(
      testBankId1,
      testAccountId0,
      "AccountNumber",
      prefix + "-EUR")
    prefix
  }

  feature(s"test ${ApiEndpoint1.name} - Unauthorized access") {
    scenario("Without a login the caller gets 401", ApiEndpoint1, VersionOfApi) {
      val response = makePostRequest(query.POST, body("123456789-[A-Z]{3}"))
      response.code should equal(401)
      message(response) should equal(AuthenticatedUserIsRequired)
    }
  }

  feature(s"test ${ApiEndpoint1.name} - Authorized access") {
    scenario("The owner finds their account; another user finds nothing", ApiEndpoint1, VersionOfApi) {
      val prefix = routedAccount()

      When("user1, who holds the owner view of the account, searches")
      val mine = makePostRequest(query.POST <@ user1, body("^" + prefix))
      Then("the account is returned")
      mine.code should equal(200)
      mine.body.extract[ModeratedAccountsJSON400].accounts.map(_.id) should equal(List(testAccountId0.value))

      When("user2, who has no view of it, searches with the same pattern")
      val theirs = makePostRequest(query.POST <@ user2, body("^" + prefix))
      Then("nothing is returned, and nothing about user1's account")
      theirs.code should equal(200)
      theirs.body.extract[ModeratedAccountsJSON400].accounts should be(List.empty)
      theirs.body.toString.contains(testAccountId0.value) should be(false)

      When("user1 searches with a pattern that matches none of their accounts")
      val none = makePostRequest(query.POST <@ user1, body("^" + prefix + "-[A-Z]{4}$"))
      Then("the list is empty")
      none.code should equal(200)
      none.body.extract[ModeratedAccountsJSON400].accounts should be(List.empty)
    }

    scenario("A pattern that is not a valid regular expression gets 400", ApiEndpoint1, VersionOfApi) {
      val response = makePostRequest(query.POST <@ user1, body("123456789-[A-Z"))
      response.code should equal(400)
      message(response) should include(InvalidAccountRoutingAddressRegex)
    }
  }

  feature("The v4.0.0 endpoint it replaces") {
    scenario("is not served at v7.0.0, and is still served at v4.0.0", ApiEndpoint1, VersionOfApi) {
      val oldPath = List("management", "accounts", "account-routing-regex-query")
      val atV7 = makePostRequest(oldPath.foldLeft(v7)(_ / _).POST <@ user1, body("^MYREGEX"))
      atV7.code should equal(404)
      val v4 = baseRequest / "obp" / "v4.0.0"
      val atV4 = makePostRequest(oldPath.foldLeft(v4)(_ / _).POST <@ user1, body("^MYREGEX"))
      atV4.code should equal(200)
    }

    scenario("is not listed in the v7.0.0 resource docs", ApiEndpoint1, VersionOfApi) {
      val names = Http4s700.allResourceDocs.map(_.partialFunctionName)
      names should contain(nameOf(Http4s700MyAccounts.getMyAccountsByAccountRoutingRegex))
      names should not contain "getAccountsByAccountRoutingRegex"
    }
  }
}
