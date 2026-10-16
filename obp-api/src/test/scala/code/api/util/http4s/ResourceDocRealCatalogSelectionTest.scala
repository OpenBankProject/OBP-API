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

package code.api.util.http4s

import cats.effect.IO
import code.api.util.APIUtil.ResourceDoc
import code.setup.ServerSetup
import org.http4s.{Method, Request, Uri}
import org.scalatest.Tag

import scala.collection.mutable.ArrayBuffer

/**
 * Selecting the doc for real request URLs against the real catalogs.
 *
 * These are the requests that used to be given another endpoint's doc, because the matcher read
 * a fixed word written in capitals (a transaction-request type) as a placeholder, or a template made
 * only of placeholders as a wildcard. Each now gets the doc of the route that serves it.
 */
class ResourceDocRealCatalogSelectionTest extends ServerSetup {

  object ResourceDocRealCatalogSelectionTag extends Tag("ResourceDocRealCatalogSelection")

  private def select(docs: ArrayBuffer[ResourceDoc], method: Method, path: String): Option[String] =
    ResourceDocMatcher.selectByRoute(Request[IO](method, Uri.unsafeFromString(path)), docs).map(_.partialFunctionName)

  private val v4 = code.api.v4_0_0.Http4s400.Implementations4_0_0.orderedResourceDocs
  private val v6 = code.api.v6_0_0.Http4s600.Implementations6_0_0.orderedResourceDocs
  private val v7 = code.api.v7_0_0.Http4s700.Implementations7_0_0.orderedResourceDocs
  private val bg13 = code.api.berlin.group.v1_3.Http4sBGv13.orderedResourceDocs

  private def transactionRequest(version: String, viewId: String, typeName: String) =
    s"/obp/$version/banks/gh.29.uk/accounts/8ca8a7e4/$viewId/transaction-request-types/$typeName/transaction-requests"

  feature("v4.0.0: every transaction-request type gets its own doc") {
    Map(
      "ACCOUNT" -> "createTransactionRequestAccount",
      "ACCOUNT_OTP" -> "createTransactionRequestAccountOtp",
      "SEPA" -> "createTransactionRequestSepa",
      "COUNTERPARTY" -> "createTransactionRequestCounterparty",
      "REFUND" -> "createTransactionRequestRefund",
      "FREE_FORM" -> "createTransactionRequestFreeForm",
      "SIMPLE" -> "createTransactionRequestSimple",
      "AGENT_CASH_WITHDRAWAL" -> "createTransactionRequestAgentCashWithDrawal"
    ).foreach { case (typeName, docName) =>
      scenario(s"$typeName is validated under $docName", ResourceDocRealCatalogSelectionTag) {
        select(v4, Method.POST, transactionRequest("v4.0.0", "owner", typeName)) shouldBe Some(docName)
      }
    }

    scenario("A type with no doc of its own is validated under the first doc of the shared route", ResourceDocRealCatalogSelectionTag) {
      select(v4, Method.POST, transactionRequest("v4.0.0", "owner", "HOLD")) shouldBe Some("createTransactionRequestAccount")
    }
  }

  feature("v6.0.0: HOLD does not claim the other types") {
    Map(
      "HOLD" -> "createTransactionRequestHold",
      "CARDANO" -> "createTransactionRequestCardano",
      "ETH_SEND_TRANSACTION" -> "createTransactionRequestEthereumeSendTransaction",
      "ETH_SEND_RAW_TRANSACTION" -> "createTransactionRequestEthSendRawTransaction"
    ).foreach { case (typeName, docName) =>
      scenario(s"$typeName is validated under $docName", ResourceDocRealCatalogSelectionTag) {
        select(v6, Method.POST, transactionRequest("v6.0.0", "owner", typeName)) shouldBe Some(docName)
      }
    }
  }

  feature("v7.0.0: a type v7 has no route for is not claimed by another type's doc") {
    Seq("HOLD", "CARDANO", "ETH_SEND_TRANSACTION", "ETH_SEND_RAW_TRANSACTION").foreach { typeName =>
      scenario(s"$typeName selects no v7 doc, so the request can fall through to v6", ResourceDocRealCatalogSelectionTag) {
        select(v7, Method.POST, transactionRequest("v7.0.0", "owner", typeName)) shouldBe None
      }
    }

    scenario("UTILITY, BULK and OPEN_CORRIDOR_PROMISE are not claimed by MOBILE_WALLET", ResourceDocRealCatalogSelectionTag) {
      val mobileWallet = select(v7, Method.POST, transactionRequest("v7.0.0", "owner", "MOBILE_WALLET"))
      mobileWallet.map(_.contains("MobileWallet")) shouldBe Some(true)
      Seq("UTILITY", "BULK", "OPEN_CORRIDOR_PROMISE").foreach { typeName =>
        select(v7, Method.POST, transactionRequest("v7.0.0", "owner", typeName)) should not be mobileWallet
      }
    }
  }

  feature("Berlin Group v1.3: a template of placeholders does not claim other URLs") {
    scenario("a signing basket's status is its own endpoint, not getPaymentInformation", ResourceDocRealCatalogSelectionTag) {
      select(bg13, Method.GET, "/berlin-group/v1.3/signing-baskets/BASKET1/status") shouldBe Some("getSigningBasketStatus")
    }

    scenario("a signing basket's authorisations are their own endpoint, not getPaymentInformation", ResourceDocRealCatalogSelectionTag) {
      select(bg13, Method.GET, "/berlin-group/v1.3/signing-baskets/BASKET1/authorisations") shouldBe Some("getSigningBasketAuthorisation")
    }

    scenario("a payment of a real service and product is getPaymentInformation", ResourceDocRealCatalogSelectionTag) {
      select(bg13, Method.GET, "/berlin-group/v1.3/payments/sepa-credit-transfers/PAYMENT1") shouldBe Some("getPaymentInformation")
    }
  }
}
