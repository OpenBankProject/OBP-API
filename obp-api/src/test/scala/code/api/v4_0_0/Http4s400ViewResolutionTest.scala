package code.api.v4_0_0

import code.api.util.CallContext
import com.openbankproject.commons.model.View
import net.liftweb.common.{Box, Empty}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * `Http4s400.Implementations4_0_0.resolveCreateTransactionRequestView` decides between two
 * failure shapes for `createTransactionRequest`'s view lookup: a genuinely missing view
 * (client's fault, 404) and anything else the lookup throws (a connection-pool exhaustion, a
 * transient SQL error, a Mapper bug -- none of them the client's fault, all of them a 500).
 *
 * `NewStyle.function.tryons` cannot tell these apart on its own: it catches any `Exception` the
 * wrapped block raises and reports it via the caller-supplied failCode regardless of cause. If
 * the whole DB lookup sits inside that block, an infra failure and a real not-found produce the
 * identical JSON-encoded {"failCode":404,...} exception -- indistinguishable to
 * ErrorResponseConverter, which resolves the 404 straight from that embedded field. A client
 * whose retry logic reacts to 500 (retry) differently from 404 (stop) is told to stop when the
 * backend is actually just broken.
 *
 * These tests call the production function directly with a stub `lookup`, so no live Mapper
 * connection is needed to exercise the distinction -- but Implementations4_0_0's own static
 * init registers the full v4.0.0 ResourceDoc set, which needs the app booted, hence
 * V400ServerSetup rather than a bare unit-test base.
 */
class Http4s400ViewResolutionTest extends V400ServerSetup {

  private implicit val cc: CallContext = CallContext()

  private def resultOf[T](f: => T): Either[Throwable, T] =
    try Right(f) catch { case t: Throwable => Left(t) }

  feature("createTransactionRequest's view lookup distinguishes not-found from infra failure") {

    scenario("a lookup that finds nothing fails with the JSON-encoded 404 envelope") {
      val notFound: () => Box[View] = () => Empty
      val outcome = resultOf(Await.result(
        Http4s400.Implementations4_0_0.resolveCreateTransactionRequestView("nonexistent-view", notFound),
        5.seconds))

      outcome match {
        case Left(t) =>
          val msg = t.getMessage
          withClue(s"expected a JSON envelope carrying failCode 404; got: $msg") {
            msg should include("\"failCode\":404")
            msg should include("View not found")
          }
        case Right(v) =>
          fail(s"expected the lookup to fail as not-found, but it returned $v")
      }
    }

    scenario("a lookup that throws for an infra reason propagates that exception, not a 404") {
      val infraFailure = new RuntimeException("connection pool exhausted")
      val broken: () => Box[View] = () => throw infraFailure
      val outcome = resultOf(Await.result(
        Http4s400.Implementations4_0_0.resolveCreateTransactionRequestView("some-view", broken),
        5.seconds))

      outcome match {
        case Left(t) =>
          val msg = t.getMessage
          withClue(s"expected the original infra exception to propagate untouched so it resolves " +
                   s"to 500, not the 404 envelope reserved for a genuine not-found; got: $msg") {
            msg should not include "\"failCode\":404"
            msg should include("connection pool exhausted")
          }
        case Right(v) =>
          fail(s"expected the lookup's exception to propagate, but it returned $v")
      }
    }
  }
}
