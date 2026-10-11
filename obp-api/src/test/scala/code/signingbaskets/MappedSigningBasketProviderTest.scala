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


package code.signingbaskets

import code.setup.ServerSetup

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class MappedSigningBasketProviderTest extends ServerSetup {

  private val provider = MappedSigningBasketProvider

  private def uuid() = java.util.UUID.randomUUID().toString

  private def newBasket(consumerId: String = "consumer-1") =
    provider.createSigningBasket(Some(List(uuid(), uuid())), None, consumerId).openOrThrowException("the basket must be created")

  feature("a signing basket records who created it") {
    scenario("the creating consumer and the payments are stored, and a basket without a consumer reads as unowned") {
      val payments = List(uuid(), uuid())
      val basket = provider.createSigningBasket(Some(payments), None, "consumer-a").openOrThrowException("created")
      val stored = provider.getSigningBasketByBasketId(basket.basketId).openOrThrowException("stored")
      stored.basket.status should equal("RCVD")
      stored.basket.consumerId should equal(Some("consumer-a"))
      stored.payments should equal(Some(payments))

      val legacy = MappedSigningBasket.create.Status("RCVD").saveMe()
      provider.getSigningBasketByBasketId(legacy.basketId).openOrThrowException("stored").basket.consumerId should equal(None)
    }
  }

  feature("a basket moves between statuses with one conditional update") {
    scenario("the first caller moves the basket and the next finds it already moved") {
      val basket = newBasket()
      provider.transitionSigningBasketStatus(basket.basketId, "RCVD", "AUTHORISING").openOrThrowException("moved") should be(true)
      provider.transitionSigningBasketStatus(basket.basketId, "RCVD", "AUTHORISING").openOrThrowException("moved") should be(false)
      provider.getSigningBasketByBasketId(basket.basketId).openOrThrowException("stored").basket.status should equal("AUTHORISING")
    }

    scenario("a transition from the wrong status changes nothing") {
      val basket = newBasket()
      provider.transitionSigningBasketStatus(basket.basketId, "AUTHORISING", "ACTC").openOrThrowException("moved") should be(false)
      provider.getSigningBasketByBasketId(basket.basketId).openOrThrowException("stored").basket.status should equal("RCVD")
    }

    scenario("callers racing for different transitions out of RCVD have exactly one winner") {
      import scala.concurrent.ExecutionContext.Implicits.global
      (1 to 10).foreach { round =>
        val basket = newBasket()
        val moves = List("AUTHORISING", "CANC", "AUTHORISING", "CANC").map(to =>
          Future(provider.transitionSigningBasketStatus(basket.basketId, "RCVD", to).openOr(false)))
        val won = Await.result(Future.sequence(moves), 60.seconds)
        withClue(s"round $round: ") { won.count(identity) should equal(1) }
      }
    }
  }
}
