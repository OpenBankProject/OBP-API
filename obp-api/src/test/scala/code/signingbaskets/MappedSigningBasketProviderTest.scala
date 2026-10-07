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

  // Members are unique per basket: a payment or consent can be held by one active basket at a time.
  private def uuid() = java.util.UUID.randomUUID().toString

  private def newBasket(consumerId: String = "consumer-1", psuUserId: Option[String] = None,
                        payments: List[String] = List(uuid(), uuid()), consents: List[String] = List(uuid())) =
    provider.createSigningBasket(Some(payments), Some(consents), consumerId, psuUserId)
      .openOrThrowException("the basket must be created")

  feature("a signing basket records who created it") {
    scenario("the creating consumer, the named PSU, the creation time and the members are stored") {
      val payments = List(uuid(), uuid())
      val consents = List(uuid())
      val basket = newBasket("consumer-a", Some("psu-a"), payments, consents)
      val stored = provider.getSigningBasketByBasketId(basket.basketId).openOrThrowException("stored")
      stored.basket.status should equal("RCVD")
      stored.basket.consumerId should equal(Some("consumer-a"))
      stored.basket.psuUserId should equal(Some("psu-a"))
      stored.payments should equal(Some(payments))
      stored.consents should equal(Some(consents))
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

  feature("a payment or consent is held by one active basket at a time") {
    scenario("a second basket naming a held member is refused and leaves nothing behind") {
      val (heldPayment, heldConsent, freePayment) = (uuid(), uuid(), uuid())
      val first = provider.createSigningBasket(Some(List(heldPayment)), Some(List(heldConsent)), "consumer-1", None)
        .openOrThrowException("the first basket must be created")
      val basketsBefore = MappedSigningBasket.count()
      val claimsBefore = MappedSigningBasketMemberClaim.count()

      val refused = provider.createSigningBasket(Some(List(freePayment, heldPayment)), None, "consumer-1", None)
      refused should equal(net.liftweb.common.Failure(code.api.util.ErrorMessages.SigningBasketMemberStatusInvalid))

      MappedSigningBasket.count() should equal(basketsBefore)
      MappedSigningBasketMemberClaim.count() should equal(claimsBefore)
      MappedSigningBasketPayment.findAll(By(MappedSigningBasketPayment.PaymentId, freePayment)) shouldBe empty
      provider.getSigningBasketByBasketId(first.basketId).map(_.payments) should equal(net.liftweb.common.Full(Some(List(heldPayment))))
    }

    scenario("releasing a basket's members lets another basket take them") {
      val released = uuid()
      val first = provider.createSigningBasket(Some(List(released)), None, "consumer-1", None).openOrThrowException("x")
      provider.createSigningBasket(Some(List(released)), None, "consumer-1", None).isEmpty should be(true)
      provider.releaseSigningBasketMembers(first.basketId).openOrThrowException("x")
      provider.createSigningBasket(Some(List(released)), None, "consumer-1", None).isDefined should be(true)
    }

    scenario("a payment and a consent that share an id do not collide") {
      val sameId = uuid()
      provider.createSigningBasket(Some(List(sameId)), None, "consumer-1", None).isDefined should be(true)
      provider.createSigningBasket(None, Some(List(sameId)), "consumer-1", None).isDefined should be(true)
    }

    scenario("two requests racing for the same member: exactly one basket is created") {
      import scala.concurrent.ExecutionContext.Implicits.global
      (1 to 10).foreach { round =>
        val member = uuid()
        val callers = (1 to 6).map(_ => Future(provider.createSigningBasket(Some(List(member)), None, "consumer-1", None)))
        val results = Await.result(Future.sequence(callers), 60.seconds)
        val created = results.map(_.isDefined)
        withClue(s"round $round: ") {
          created.count(identity) should equal(1)
          // Whichever way a loser lost (the check, or the unique index under it), it is the same refusal.
          results.filterNot(_.isDefined).foreach {
            case net.liftweb.common.Failure(message, _, _) => message should equal(code.api.util.ErrorMessages.SigningBasketMemberStatusInvalid)
            case other => fail(s"unexpected result $other")
          }
          MappedSigningBasketPayment.findAll(By(MappedSigningBasketPayment.PaymentId, member)).size should equal(1)
        }
      }
    }
  }

  feature("the execution ledger does not depend on the request that wrote it") {
    scenario("R1: the claim, the member states and the release are committed on their own, so they survive the request's transaction rolling back") {
      import SigningBasketMemberState._
      import code.api.util.http4s.RequestScopeConnection
      val basket = newBasket()
      val member = basket.basketId.reverse // a member id of this basket's own; only the ledger row matters here
      val real = code.api.util.APIUtil.vendor.HikariDatasource.ds.getConnection()
      real.setAutoCommit(false)
      RequestScopeConnection.currentProxy.set(RequestScopeConnection.makeProxy(real))
      try {
        provider.transitionSigningBasketStatus(basket.basketId, "RCVD", "AUTHORISING").openOrThrowException("claimed") should be(true)
        provider.createSigningBasketMemberExecutions(basket.basketId, List((PaymentType, member))).openOrThrowException("recorded") should be(true)
        provider.transitionSigningBasketMemberExecution(basket.basketId, PaymentType, member, Set(Pending), Executing, "").openOrThrowException("moved") should be(true)
        provider.releaseSigningBasketMembers(basket.basketId)
      } finally {
        RequestScopeConnection.currentProxy.remove()
        // The request dies before it commits: a crash, a timeout, a failed commit.
        real.rollback()
        real.close()
      }
      withClue("the claim that the answer was being executed: ") {
        provider.getSigningBasketByBasketId(basket.basketId).openOrThrowException("basket").basket.status should equal("AUTHORISING")
      }
      withClue("the member that was being executed, which is what the resumption turns UNKNOWN: ") {
        provider.getSigningBasketMemberExecutions(basket.basketId).map(m => m.memberId -> m.state) should equal(List(member -> Executing))
      }
      withClue("the release of what the basket held: ") {
        MappedSigningBasketMemberClaim.findAll(By(MappedSigningBasketMemberClaim.BasketId, basket.basketId)) should equal(Nil)
      }
    }
  }

  feature("each member of a basket has its own execution state") {
    import SigningBasketMemberState._

    scenario("members are recorded PENDING in the order given, once") {
      val basket = newBasket()
      val (first, second, consent) = (uuid(), uuid(), uuid())
      provider.createSigningBasketMemberExecutions(basket.basketId, List((PaymentType, first), (PaymentType, second), (ConsentType, consent)))
        .openOrThrowException("x") should be(true)
      // Recording them again changes nothing.
      provider.createSigningBasketMemberExecutions(basket.basketId, List((PaymentType, second), (PaymentType, first)))
      provider.getSigningBasketMemberExecutions(basket.basketId).map(m => (m.memberType, m.memberId, m.state, m.attempts)) should equal(
        List((PaymentType, first, Pending, 0), (PaymentType, second, Pending, 0), (ConsentType, consent, Pending, 0)))
    }

    scenario("a member moves only from a state the caller names, and a claim counts as an attempt") {
      val basket = newBasket()
      val payment = uuid()
      provider.createSigningBasketMemberExecutions(basket.basketId, List((PaymentType, payment)))
      def move(from: Set[String], to: String, detail: String = "") =
        provider.transitionSigningBasketMemberExecution(basket.basketId, PaymentType, payment, from, to, detail).openOrThrowException("x")
      move(Set(Executing), Done) should be(false)
      move(Set(Pending, Failed), Executing) should be(true)
      move(Set(Pending, Failed), Executing) should be(false)
      move(Set(Executing), Failed, "no funds") should be(true)
      move(Set(Pending, Failed), Executing) should be(true)
      move(Set(Executing), Done) should be(true)
      val stored = provider.getSigningBasketMemberExecutions(basket.basketId).head
      (stored.state, stored.attempts) should equal((Done, 2))
    }

    scenario("executors racing for one member have exactly one winner") {
      import scala.concurrent.ExecutionContext.Implicits.global
      (1 to 10).foreach { round =>
        val basket = newBasket()
        val payment = uuid()
        provider.createSigningBasketMemberExecutions(basket.basketId, List((PaymentType, payment)))
        val executors = (1 to 8).map(_ => Future(
          provider.transitionSigningBasketMemberExecution(basket.basketId, PaymentType, payment, Set(Pending), Executing, "").openOr(false)))
        withClue(s"round $round: ") {
          Await.result(Future.sequence(executors), 60.seconds).count(identity) should equal(1)
          provider.getSigningBasketMemberExecutions(basket.basketId).head.attempts should equal(1)
        }
      }
    }

    scenario("a member still EXECUTING after the lease becomes UNKNOWN; one that finished does not") {
      val basket = newBasket()
      val (stuck, finished) = (uuid(), uuid())
      provider.createSigningBasketMemberExecutions(basket.basketId, List((PaymentType, stuck), (PaymentType, finished)))
      List(stuck, finished).foreach(id => provider.transitionSigningBasketMemberExecution(basket.basketId, PaymentType, id, Set(Pending), Executing, ""))
      provider.transitionSigningBasketMemberExecution(basket.basketId, PaymentType, finished, Set(Executing), Done, "")
      Thread.sleep(1200)
      provider.markStaleSigningBasketMembersUnknown(1).openOrThrowException("x") should be >= 1
      provider.getSigningBasketMemberExecutions(basket.basketId).map(m => m.memberId -> m.state).toMap should equal(
        Map(stuck -> Unknown, finished -> Done))
    }

    scenario("a basket that was looked at goes behind the ones not yet tried, so a stuck one does not hold the queue") {
      val looked = newBasket()
      val waiting = newBasket()
      List(looked, waiting).foreach(b =>
        provider.transitionSigningBasketStatus(b.basketId, "RCVD", code.api.berlin.group.ConstantsBG.SigningBasketsStatus.AUTHORISING_INTERNAL))
      Thread.sleep(1200)
      provider.touchSigningBasket(looked.basketId).openOrThrowException("x") should be(true)
      val awaiting = provider.getSigningBasketsAwaitingExecution(1, 100)
      awaiting should contain(waiting.basketId)
      awaiting should not contain looked.basketId
      withClue("a basket that is not awaiting execution is not touched: ") {
        val finished = newBasket()
        provider.transitionSigningBasketStatus(finished.basketId, "RCVD", "ACTC")
        provider.touchSigningBasket(finished.basketId).openOrThrowException("x") should be(false)
      }
    }

    scenario("baskets whose execution has not finished are listed, oldest first") {
      val stuck = newBasket()
      val done = newBasket()
      provider.transitionSigningBasketStatus(stuck.basketId, "RCVD", code.api.berlin.group.ConstantsBG.SigningBasketsStatus.AUTHORISING_INTERNAL)
      provider.transitionSigningBasketStatus(done.basketId, "RCVD", "ACTC")
      Thread.sleep(1200)
      val awaiting = provider.getSigningBasketsAwaitingExecution(1, 100)
      awaiting should contain(stuck.basketId)
      awaiting should not contain done.basketId
      provider.getSigningBasketsAwaitingExecution(3600, 100) should not contain stuck.basketId
    }
  }
}
