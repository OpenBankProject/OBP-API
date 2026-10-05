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
import net.liftweb.mapper.By

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class MappedSigningBasketProviderTest extends ServerSetup {

  private val provider = MappedSigningBasketProvider

  private def newBasket(consumerId: String = "consumer-1", psuUserId: Option[String] = None) =
    provider.createSigningBasket(Some(List("payment-1", "payment-2")), Some(List("consent-1")), consumerId, psuUserId)
      .openOrThrowException("the basket must be created")

  feature("a signing basket records who created it") {
    scenario("the creating consumer, the named PSU, the creation time and the members are stored") {
      val basket = newBasket("consumer-a", Some("psu-a"))
      val stored = provider.getSigningBasketByBasketId(basket.basketId).openOrThrowException("stored")
      stored.basket.status should equal("RCVD")
      stored.basket.consumerId should equal(Some("consumer-a"))
      stored.basket.psuUserId should equal(Some("psu-a"))
      stored.payments should equal(Some(List("payment-1", "payment-2")))
      stored.consents should equal(Some(List("consent-1")))
      MappedSigningBasket.find(By(MappedSigningBasket.BasketId, basket.basketId)).map(_.createdAt.get.getTime > 0) should equal(net.liftweb.common.Full(true))
    }

    scenario("a basket created without a PSU has none, and a row with no consumer reads as unowned") {
      val basket = newBasket()
      provider.getSigningBasketByBasketId(basket.basketId).map(_.basket.psuUserId) should equal(net.liftweb.common.Full(None))
      val legacy = MappedSigningBasket.create.Status("RCVD").saveMe()
      provider.getSigningBasketByBasketId(legacy.basketId).map(_.basket.consumerId) should equal(net.liftweb.common.Full(None))
    }
  }

  feature("a status transition happens only from the status the caller read") {
    scenario("the first caller moves the basket and the next finds it already moved") {
      val basket = newBasket()
      provider.transitionSigningBasketStatus(basket.basketId, "RCVD", "CANC").openOrThrowException("x") should be(true)
      provider.transitionSigningBasketStatus(basket.basketId, "RCVD", "ACTC").openOrThrowException("x") should be(false)
      provider.getSigningBasketByBasketId(basket.basketId).map(_.basket.status) should equal(net.liftweb.common.Full("CANC"))
    }

    scenario("a transition from the wrong status changes nothing") {
      val basket = newBasket()
      provider.transitionSigningBasketStatus(basket.basketId, "ACTC", "CANC").openOrThrowException("x") should be(false)
      provider.getSigningBasketByBasketId(basket.basketId).map(_.basket.status) should equal(net.liftweb.common.Full("RCVD"))
    }

    scenario("callers racing for different transitions out of RCVD have exactly one winner") {
      import scala.concurrent.ExecutionContext.Implicits.global
      (1 to 20).foreach { round =>
        val basket = newBasket()
        val callers = (1 to 8).map { i =>
          val target = if (i % 2 == 0) "ACTC" else "CANC"
          Future(provider.transitionSigningBasketStatus(basket.basketId, "RCVD", target).openOr(false) -> target)
        }
        val outcomes = Await.result(Future.sequence(callers), 60.seconds)
        withClue(s"round $round: ") {
          outcomes.count(_._1) should equal(1)
          provider.getSigningBasketByBasketId(basket.basketId).map(_.basket.status) should equal(net.liftweb.common.Full(outcomes.find(_._1).get._2))
        }
      }
    }
  }

  feature("the PSU is bound once") {
    scenario("the first PSU binds, the same PSU may bind again, another PSU may not") {
      val basket = newBasket()
      provider.bindSigningBasketPsu(basket.basketId, "psu-1").openOrThrowException("x") should be(true)
      provider.bindSigningBasketPsu(basket.basketId, "psu-1").openOrThrowException("x") should be(true)
      provider.bindSigningBasketPsu(basket.basketId, "psu-2").openOrThrowException("x") should be(false)
      provider.getSigningBasketByBasketId(basket.basketId).map(_.basket.psuUserId) should equal(net.liftweb.common.Full(Some("psu-1")))
    }

    scenario("a PSU named at creation cannot be replaced") {
      val basket = newBasket(psuUserId = Some("psu-named"))
      provider.bindSigningBasketPsu(basket.basketId, "psu-other").openOrThrowException("x") should be(false)
      provider.getSigningBasketByBasketId(basket.basketId).map(_.basket.psuUserId) should equal(net.liftweb.common.Full(Some("psu-named")))
    }
  }
}
