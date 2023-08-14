package code.api.util

import java.util.concurrent.TimeoutException
import java.util.{Timer, TimerTask}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.postfixOps

object FutureUtil {

  // All Future's that use futureWithTimeout will use the same Timer object
  // it is thread safe and scales to thousands of active timers
  // The true parameter ensures that timeout timers are daemon threads and do not stop
  // the program from shutting down

  val timer: Timer = new Timer(true)

  /**
   * Returns the result of the provided future within the given time or a timeout exception, whichever is first
   * This uses Java Timer which runs a single thread to handle all futureWithTimeouts and does not block like a
   * Thread.sleep would
   * @param future Caller passes a future to execute
   * @param timeout Time before we return a Timeout exception instead of future's outcome
   * @return Future[T]
   */
  def futureWithTimeout[T](future : Future[T], timeout : FiniteDuration)(implicit ec: ExecutionContext): Future[T] = {

    // Promise will be fulfilled with either the callers Future or the timer task if it times out
    var p = Promise[T]

    // and a Timer task to handle timing out

    val timerTask = new TimerTask() {
      def run() : Unit = {
        p.tryFailure(new TimeoutException(ErrorMessages.requestTimeout))
      }
    }

    // Set the timeout to check in the future
    timer.schedule(timerTask, timeout.toMillis)

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

}
