package code.concurrency

import code.api.util.{APIUtil, FutureUtil}

import java.util.UUID
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * A: futureWithLimits must self-heal the open-futures counter.
 *
 * THE HAZARD:
 *   incrementFutureCounter runs synchronously; decrementFutureCounter only ran inside the
 *   wrapped Future's .map/.recover. A Future that never completes (hung backend) never
 *   decremented, so openFuturesCount for that service grew unbounded, ratcheting
 *   APIUtil.getBackOffFactor to its worst tier (1024) — canOpenFuture's modulo check then
 *   rejected ~all subsequent calls with ServiceIsTooBusy, permanently, until JVM restart.
 *
 * The fix adds a reaper TimerTask (3-arg futureWithLimits overload) that decrements the
 * counter on a timeout ceiling if the wrapped Future hasn't completed by then, guarded by
 * an AtomicBoolean so the reaper and the Future's own completion can never both decrement.
 *
 * This is a pure Future/counter test — it does not touch the DB or HTTP layer, so it does
 * not extend ConcurrentRaceSetup/ServerSetupWithTestData (avoids an unnecessary full Lift
 * server boot). Tagged ConcurrencyRace for consistency with the rest of the suite.
 */
class ConcurrentBackoffCounterSelfHealTest extends AnyFlatSpec with Matchers {

  private implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private def openFuturesCount(serviceName: String): Int =
    APIUtil.serviceNameCountersMap.getOrDefault(serviceName, (0, 0))._2

  "futureWithLimits" should "self-heal the open-futures counter when the wrapped Future never completes" taggedAs ConcurrencyRace in {
    val serviceName = s"__conc_backoff_selfheal_${UUID.randomUUID.toString.take(8)}"
    val neverCompletes = Promise[Unit]().future

    FutureUtil.futureWithLimits(neverCompletes, serviceName, reaperTimeoutMillis = 200)
    Thread.sleep(600)

    withClue(s"openFuturesCount=${openFuturesCount(serviceName)} after reaper should have fired: ") {
      openFuturesCount(serviceName) shouldBe 0
    }
    APIUtil.canOpenFuture(serviceName) shouldBe true
  }

  it should "not double-decrement when the underlying Future completes late, after the reaper already fired" taggedAs ConcurrencyRace in {
    val serviceName = s"__conc_backoff_idempotent_${UUID.randomUUID.toString.take(8)}"
    val promise = Promise[String]()

    val wrapped = FutureUtil.futureWithLimits(promise.future, serviceName, reaperTimeoutMillis = 200)
    Thread.sleep(400)
    promise.success("late value")
    Await.result(wrapped, 5.seconds)
    // give the onComplete callback a moment to run after the promise resolved
    Thread.sleep(200)

    withClue(s"openFuturesCount=${openFuturesCount(serviceName)}: reaper and completion both firing must not double-decrement: ") {
      openFuturesCount(serviceName) shouldBe 0
    }
  }

  it should "behave exactly like the original Future when it completes immediately (no regression)" taggedAs ConcurrencyRace in {
    val serviceNameOk = s"__conc_backoff_ok_${UUID.randomUUID.toString.take(8)}"
    val serviceNameFail = s"__conc_backoff_fail_${UUID.randomUUID.toString.take(8)}"

    val okResult = Try(Await.result(FutureUtil.futureWithLimits(Future.successful("ok"), serviceNameOk, reaperTimeoutMillis = 5000), 5.seconds))
    val failResult = Try(Await.result(FutureUtil.futureWithLimits(Future.failed[String](new RuntimeException("boom")), serviceNameFail, reaperTimeoutMillis = 5000), 5.seconds))

    okResult shouldBe Success("ok")
    failResult match {
      case Failure(e) => e.getMessage shouldBe "boom"
      case Success(_) => fail("expected the failed Future to propagate its failure")
    }

    withClue(s"openFuturesCount(ok)=${openFuturesCount(serviceNameOk)}: ") {
      openFuturesCount(serviceNameOk) shouldBe 0
    }
    withClue(s"openFuturesCount(fail)=${openFuturesCount(serviceNameFail)}: ") {
      openFuturesCount(serviceNameFail) shouldBe 0
    }
  }
}
