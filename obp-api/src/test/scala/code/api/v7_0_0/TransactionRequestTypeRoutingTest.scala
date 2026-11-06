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

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import code.api.v4_0_0.Http4s400
import code.api.v6_0_0.Http4s600
import code.setup.ServerSetup
import fs2.Stream
import org.http4s.{Headers, HttpApp, Method, Request, Response, Uri}
import org.scalatest.{GivenWhenThen, Tag}
import org.typelevel.ci.CIString

/**
 * This test checks that a transaction request reaches the ResourceDoc of its own type.
 *
 * The middleware picks a ResourceDoc by matching the URL against templates, separately from
 * the http4s route that runs the handler. Transaction-request types are written in capitals
 * like placeholders, so a template such as
 * `.../transaction-request-types/MOBILE_WALLET/transaction-requests` used to match every type.
 * Two failures followed. In v7.0.0, HOLD, CARDANO and Ethereum requests, which v7 has no
 * handler for, were claimed by the MOBILE_WALLET doc and answered with an empty 404 instead
 * of falling through to v6.0.0, which serves them. And UTILITY, BULK and OPEN_CORRIDOR_PROMISE
 * requests were validated under the MOBILE_WALLET doc, so switching MOBILE_WALLET off switched
 * them off too. v6.0.0 had the same problem with its HOLD doc.
 *
 * Every request here is sent without credentials. A request that reaches the doc of its own
 * type is refused with 401 by that doc's login requirement. A request claimed by a disabled
 * doc gets 404 instead, and the `X-OBP-Version-Served` header shows which version answered.
 * No test data is needed.
 */
class TransactionRequestTypeRoutingTest extends ServerSetup with GivenWhenThen {

  object TransactionRequestTypeRoutingTag extends Tag("TransactionRequestTypeRouting")

  implicit val runtime: IORuntime = IORuntime.global

  private val v700App = Http4s700.wrappedRoutesV700Services.orNotFound
  private val v600App = Http4s600.wrappedRoutesV600Services.orNotFound
  private val v400App = Http4s400.wrappedRoutesV400Services.orNotFound

  private val versionServedHeader = CIString("X-OBP-Version-Served")

  private def postTransactionRequest(app: HttpApp[IO], version: String, viewId: String, transactionRequestType: String): Response[IO] = {
    val path = s"/obp/$version/banks/gh.29.uk/accounts/8ca8a7e4-6d02-40e3-a129-0b2bf89de9f1/$viewId" +
      s"/transaction-request-types/$transactionRequestType/transaction-requests"
    val request = Request[IO](Method.POST, Uri.unsafeFromString(path), headers = Headers.empty,
      body = Stream.emits("{}".getBytes("UTF-8")))
    app.run(request).unsafeRunSync()
  }

  private def versionServed(response: Response[IO]): Option[String] =
    response.headers.get(versionServedHeader).map(_.head.value)

  feature("v7.0.0 hands transaction-request types it has no handler for to v6.0.0") {
    List("HOLD", "CARDANO", "ETH_SEND_TRANSACTION", "ETH_SEND_RAW_TRANSACTION").foreach { transactionRequestType =>
      scenario(s"POST /obp/v7.0.0/.../transaction-request-types/$transactionRequestType/transaction-requests is served by v6.0.0", TransactionRequestTypeRoutingTag) {
        When(s"an unauthenticated $transactionRequestType request is sent to the v7.0.0 prefix")
        val response = postTransactionRequest(v700App, "v7.0.0", "owner", transactionRequestType)

        Then("v6.0.0 answers it, refusing it for lack of credentials")
        versionServed(response) shouldBe Some("v6.0.0")
        response.status.code shouldBe 401
      }
    }

    scenario("A transaction-request type native to v7.0.0 is answered by v7.0.0", TransactionRequestTypeRoutingTag) {
      val response = postTransactionRequest(v700App, "v7.0.0", "owner", "UTILITY")
      versionServed(response) shouldBe None
      response.status.code shouldBe 401
    }
  }

  feature("Switching one transaction-request type off leaves the other types on") {

    scenario("v7.0.0: disabling MOBILE_WALLET leaves UTILITY, BULK and OPEN_CORRIDOR_PROMISE on", TransactionRequestTypeRoutingTag) {
      Given("api_disabled_endpoints names only the MOBILE_WALLET endpoint")
      setPropsValues("api_disabled_endpoints" -> "[OBPv7.0.0-createTransactionRequestMobileWallet]")

      Then("MOBILE_WALLET is switched off")
      postTransactionRequest(v700App, "v7.0.0", "owner", "MOBILE_WALLET").status.code shouldBe 404

      And("the other v7.0.0 types still reach their own docs")
      List("UTILITY", "BULK", "OPEN_CORRIDOR_PROMISE").foreach { transactionRequestType =>
        withClue(s"$transactionRequestType: ") {
          postTransactionRequest(v700App, "v7.0.0", "owner", transactionRequestType).status.code shouldBe 401
        }
      }
    }

    scenario("v6.0.0: disabling HOLD leaves CARDANO and the Ethereum types on", TransactionRequestTypeRoutingTag) {
      Given("api_disabled_endpoints names only the v6.0.0 HOLD endpoint")
      setPropsValues("api_disabled_endpoints" -> "[OBPv6.0.0-createTransactionRequestHold]")

      Then("HOLD is switched off")
      postTransactionRequest(v600App, "v6.0.0", "owner", "HOLD").status.code shouldBe 404

      And("the other v6.0.0 types still reach their own docs")
      List("CARDANO", "ETH_SEND_TRANSACTION", "ETH_SEND_RAW_TRANSACTION").foreach { transactionRequestType =>
        withClue(s"$transactionRequestType: ") {
          postTransactionRequest(v600App, "v6.0.0", "owner", transactionRequestType).status.code shouldBe 401
        }
      }
    }

    scenario("v4.0.0: disabling the ACCOUNT endpoint leaves SEPA and COUNTERPARTY on", TransactionRequestTypeRoutingTag) {
      Given("api_disabled_endpoints names only the v4.0.0 ACCOUNT endpoint")
      setPropsValues("api_disabled_endpoints" -> "[OBPv4.0.0-createTransactionRequestAccount]")

      Then("SEPA and COUNTERPARTY still reach their own v4.0.0 docs")
      List("SEPA", "COUNTERPARTY").foreach { transactionRequestType =>
        withClue(s"$transactionRequestType: ") {
          val response = postTransactionRequest(v400App, "v4.0.0", "owner", transactionRequestType)
          response.status.code shouldBe 401
          versionServed(response) shouldBe None
        }
      }
    }
  }
}
