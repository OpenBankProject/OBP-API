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
package code.api.v6_0_0

import org.json4s._
import code.api.Constant
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
  * This suite checks that an ETH_SEND_RAW_TRANSACTION request whose params are not a valid signed raw
  * Ethereum transaction is answered with a 400. The raw transaction decoder used to call sys.exit(1) on
  * such input, so one bad request stopped the whole server; the last scenario checks the server still
  * answers after the bad requests.
  */
class EthSendRawTransactionRequestTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object CreateTransactionRequestEthSendRawTransaction extends Tag(nameOf(Implementations6_0_0.createTransactionRequestEthSendRawTransaction))

  private def createEthSendRawTransactionRequest(params: String) = {
    setPropsValues("transactionRequests_enabled" -> "true")
    setPropsValues("transactionRequests_supported_types" -> "ETH_SEND_RAW_TRANSACTION")
    val request = (v6_0_0_Request / "banks" / testBankId1.value / "accounts" / testAccountId1.value /
      Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "ETH_SEND_RAW_TRANSACTION" / "transaction-requests").POST <@ (user1)
    makePostRequest(request, write(TransactionRequestBodyEthSendRawTransactionJsonV600(params = params, description = "malformed raw transaction")))
  }

  feature("Create ETH_SEND_RAW_TRANSACTION transaction request with malformed params - v6.0.0") {

    List(
      "hello",          // no 0x prefix
      "0xzz",           // non-hex characters
      "0xabc",          // odd length
      "0x00ff00ff00ff"  // valid hex that does not decode as a transaction
    ).foreach { malformedParams =>
      scenario(s"params '$malformedParams' are answered with 400", CreateTransactionRequestEthSendRawTransaction, VersionOfApi) {
        When(s"We create an ETH_SEND_RAW_TRANSACTION transaction request with params '$malformedParams'")
        val response = createEthSendRawTransactionRequest(malformedParams)
        Then("We should get a 400")
        response.code should equal(400)
        And("error should be " + InvalidJsonValue)
        response.body.extract[ErrorMessage].message should include(InvalidJsonValue)
      }
    }

    scenario("The server still answers after malformed params", CreateTransactionRequestEthSendRawTransaction, VersionOfApi) {
      When("We send malformed params and then a second request")
      createEthSendRawTransactionRequest("0xzz").code should equal(400)
      val response = makeGetRequest((v6_0_0_Request / "root").GET)
      Then("The second request is answered with 200")
      response.code should equal(200)
    }
  }
}
