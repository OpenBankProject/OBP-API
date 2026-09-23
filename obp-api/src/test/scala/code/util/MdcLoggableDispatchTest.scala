package code.util

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.AppenderBase
import org.scalatest.{FlatSpec, Matchers}
import org.slf4j.LoggerFactory

import java.util.concurrent.{CopyOnWriteArrayList, TimeUnit}

/**
 * Verifies the actual behavioural change from the fix in Helper.scala: masking + the local
 * write now happen on a dedicated background pool (mdcLoggingExecutionContext) instead of
 * inline on the calling thread. RedisLoggerShouldShipTest covers the gating logic
 * (shouldShip); this covers the dispatch itself -- that a call which passes the gate still
 * reliably reaches the underlying logger (just asynchronously), and that it does so on a
 * thread other than the caller's.
 */
class MdcLoggableDispatchTest extends FlatSpec with Matchers {

  private object ProbeLogger extends Helper.MdcLoggable {
    // `logger` is protected on the MdcLoggable/Loggable trait -- only reachable from within a
    // mixing class's own body, not from the test. Expose the one call the test needs.
    def probeDebug(msg: String): Unit = logger.debug(msg)
  }

  "MdcLoggable" should "deliver an enabled log call to the underlying logger asynchronously, off the calling thread" in {
    val logbackLogger = LoggerFactory.getLogger(ProbeLogger.getClass.getName).asInstanceOf[LogbackLogger]
    val originalLevel = logbackLogger.getLevel
    logbackLogger.setLevel(Level.DEBUG)

    val captured = new CopyOnWriteArrayList[ILoggingEvent]()
    val appender = new AppenderBase[ILoggingEvent] {
      override def append(event: ILoggingEvent): Unit = captured.add(event)
    }
    appender.setContext(logbackLogger.getLoggerContext)
    appender.start()
    logbackLogger.addAppender(appender)

    val callingThreadName = Thread.currentThread().getName
    // Not a bare digit run: a 16-19 digit nanoTime() collides with SecureLogging's
    // credit-card-shaped masking pattern and gets partially masked -- correct behaviour from
    // the masking regex, but it broke this test's own substring check the first time around.
    val marker = s"MdcLoggableDispatchTest-probe-id${System.nanoTime()}"

    try {
      ProbeLogger.probeDebug(marker)

      // The dispatch is asynchronous, so the event won't necessarily be there immediately --
      // poll briefly rather than asserting right away (which would just be testing timing,
      // not the actual delivery guarantee).
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      while (captured.isEmpty && System.nanoTime() < deadline) {
        Thread.sleep(20)
      }

      captured should not be empty
      val event = captured.get(0)
      event.getFormattedMessage should include(marker)
      event.getThreadName should not equal callingThreadName
      event.getThreadName should startWith("mdc-log-dispatch-")
    } finally {
      logbackLogger.detachAppender(appender)
      logbackLogger.setLevel(originalLevel)
    }
  }
}
