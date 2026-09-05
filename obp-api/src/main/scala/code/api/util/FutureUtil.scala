package code.api.util

import org.json4s._
import java.util.concurrent.TimeoutException
import java.util.{Timer, TimerTask}

import code.api.{APIFailureNewStyle, Constant}
import org.json4s.{Extraction, JsonAST}
import code.api.util.APIUtil.{decrementFutureCounter, incrementFutureCounter}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.postfixOps

object FutureUtil {

  // All Future's that use futureWithTimeout will use the same Timer object
  // it is thread safe and scales to thousands of active timers
  // The true parameter ensures that timeout timers are daemon threads and do not stop
  // the program from shutting down

  val timer: Timer = new Timer(true)
  
  case class EndpointTimeout(inMillis: Long)
  case class EndpointContext(context: Option[CallContext])
  
  implicit val defaultTimeout: EndpointTimeout = EndpointTimeout(Constant.longEndpointTimeoutInMillis)
  implicit val callContext: code.api.util.FutureUtil.EndpointContext = EndpointContext(context = None)
  implicit val formats: org.json4s.Formats = CustomJsonFormats.formats

  /**
   * Returns the result of the provided future within the given time or a timeout exception, whichever is first
   * This uses Java Timer which runs a single thread to handle all futureWithTimeouts and does not block like a
   * Thread.sleep would
   * @param future Caller passes a future to execute
   * @param timeout Time before we return a Timeout exception instead of future's outcome
   * @return Future[T]
   */
  def futureWithTimeout[T](future : Future[T])(implicit timeout : EndpointTimeout, cc: EndpointContext, ec: ExecutionContext): Future[T] = {

    // Promise will be fulfilled with either the callers Future or the timer task if it times out
    var p = Promise[T]()

    // and a Timer task to handle timing out

    val timerTask = new TimerTask() {
      def run() : Unit = {
        p.tryFailure {
          val error: String = ErrorMessages.apiFailureToString(408, ErrorMessages.requestTimeout, cc.context)
          new TimeoutException(error)
        }
      }
    }

    // Set the timeout to check in the future
    timer.schedule(timerTask, timeout.inMillis)

    future.map {
      a =>
        if(p.trySuccess(a)) {
          timerTask.cancel()
        }
    }
      .recover {
        case e: Exception =>
          if(p.tryFailure(e)) {
            timerTask.cancel()
          }
      }

    p.future
  }

  def futureWithLimits[T](future: Future[T], serviceName: String)(implicit ec: ExecutionContext): Future[T] =
    futureWithLimits(future, serviceName, Constant.longEndpointTimeoutInMillis)

  /**
   * Bound the open-futures counter with a self-healing reaper.
   *
   * incrementFutureCounter runs synchronously; the matching decrement must run exactly once,
   * no matter whether the wrapped Future completes, fails, or NEVER completes (e.g. a connector
   * call to a hung backend). Without the reaper a never-completing Future keeps its slot in
   * openFuturesCount forever, ratcheting getBackOffFactor to its worst tier (1024) so that
   * canOpenFuture rejects ~all subsequent calls with ServiceIsTooBusy until the JVM restarts.
   *
   * The reaper only frees the accounting slot; the underlying Future is left untouched (giving
   * the returned Future a timeout is a separate concern — see the RabbitMQ no-timeout finding).
   * decrementOnce is guarded by an AtomicBoolean so the reaper and the Future's own completion
   * can never decrement twice (which would drive the counter negative).
   */
  def futureWithLimits[T](future: Future[T], serviceName: String, reaperTimeoutMillis: Long)
                         (implicit ec: ExecutionContext): Future[T] = {
    incrementFutureCounter(serviceName)

    val decremented = new java.util.concurrent.atomic.AtomicBoolean(false)
    def decrementOnce(): Unit =
      if (decremented.compareAndSet(false, true)) decrementFutureCounter(serviceName)

    val reaper = new TimerTask() {
      def run(): Unit = decrementOnce()
    }
    timer.schedule(reaper, reaperTimeoutMillis)

    future.onComplete { _ =>
      reaper.cancel()
      decrementOnce()
    }

    future
  }

}
