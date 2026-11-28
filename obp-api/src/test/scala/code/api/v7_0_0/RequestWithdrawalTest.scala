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

import org.json4s.jvalue2extractable
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.InvalidUri
import code.setup.ServerSetupWithTestData
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class RequestWithdrawalTest extends ServerSetupWithTestData {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint extends Tag("requestWithdrawal")

  def v7 = baseRequest / "obp" / "v7.0.0"

  feature("Request Withdrawal") {
    // the endpoint is switched off, so the path is not served at all
    scenario("A logged-in account holder cannot request a withdrawal", ApiEndpoint, VersionOfApi) {
      val url = v7 / "banks" / testBankId1.value / "accounts" / testAccountId1.value / "views" / "owner" / "market" / "withdrawals"
      val body = """{"settlement_account_id":"other-account","amount":50.0,"address":"0xdestination"}"""

      val response = makePostRequest(url.POST <@ (user1), body)

      response.code should equal(404)
      response.body.extract[ErrorMessage].message should startWith(InvalidUri)
    }
  }
}
