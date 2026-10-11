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

package code.api.v2_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.AuthenticatedUserIsRequired
import code.setup.{APIResponse, DefaultUsers}
import org.json4s._

/**
 * This suite checks that getTransactionTypes (GET /banks/BANK_ID/transaction-types) honours the prop
 * apiOptions.getTransactionTypesIsPublic: anyone may call it while the prop is true (the default), and only a
 * logged-in User may call it when the prop is false.
 */
class TransactionTypesIsPublicTest extends V200ServerSetup with DefaultUsers {

  private def transactionTypesAt(bankId: String) = (v2_0Request / "banks" / bankId / "transaction-types").GET

  private def messageOf(response: APIResponse): String = (response.body \ "message").extractOpt[String].getOrElse("")

  feature("getTransactionTypes honours apiOptions.getTransactionTypesIsPublic") {

    scenario("an anonymous caller gets the transaction types while the prop is true") {
      setPropsValues("apiOptions.getTransactionTypesIsPublic" -> "true")
      val response = makeGetRequest(transactionTypesAt(testBankId1.value))
      response.code should equal(200)
    }

    scenario("an anonymous caller is refused with 401 when the prop is false") {
      setPropsValues("apiOptions.getTransactionTypesIsPublic" -> "false")
      val response = makeGetRequest(transactionTypesAt(testBankId1.value))
      response.code should equal(401)
      messageOf(response) should include(AuthenticatedUserIsRequired)
    }

    scenario("a logged-in User gets the transaction types when the prop is false") {
      setPropsValues("apiOptions.getTransactionTypesIsPublic" -> "false")
      val response = makeGetRequest(transactionTypesAt(testBankId1.value) <@ (user1))
      response.code should equal(200)
    }
  }
}
