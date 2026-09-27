package code.util

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.AppenderBase
import org.scalatest.{FlatSpec, Matchers}
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CopyOnWriteArrayList, TimeUnit}

/**
 * Budget tests for MdcLoggable: a log call below the enabled level must cost nothing (the
 * message is never built and never masked), and an enabled one must be built, masked and
 * written exactly once.
 *
 * SecureLogging.maskCalls counts for the whole JVM, and other threads may log while this runs,
 * so the skipped-level check allows a small amount of unrelated masking.
 */
class LoggingCostBudgetTest extends FlatSpec with Matchers {

  private object Probe extends Helper.MdcLoggable {
    def debug(msg: => AnyRef): Unit = logger.debug(msg)
  }

  private val Calls = 1000

  private def probeLogger = LoggerFactory.getLogger(Probe.getClass.getName).asInstanceOf[LogbackLogger]

  private def withLevel[T](level: Level)(body: => T): T = {
    val original = probeLogger.getLevel
    probeLogger.setLevel(level)
    try body finally probeLogger.setLevel(original)
  }

  "A DEBUG call on an INFO logger" should "never build or mask its message" in withLevel(Level.INFO) {
    val built = new AtomicInteger(0)
    val maskBefore = SecureLogging.maskCalls
    val dispatchedBefore = Helper.mdcLogDispatchedCount

    (1 to Calls).foreach(i => Probe.debug { built.incrementAndGet(); s"skipped $i" })

    built.get shouldBe 0
    withClue("unrelated threads may log meanwhile, but not once per skipped call: ") {
      (SecureLogging.maskCalls - maskBefore) should be < (Calls / 10).toLong
      (Helper.mdcLogDispatchedCount - dispatchedBefore) should be < (Calls / 10).toLong
    }
  }

  "A DEBUG call on a DEBUG logger" should "build, mask and write its message exactly once" in withLevel(Level.DEBUG) {
    val built = new AtomicInteger(0)
    val captured = new CopyOnWriteArrayList[ILoggingEvent]()
    val appender = new AppenderBase[ILoggingEvent] {
      override def append(event: ILoggingEvent): Unit = captured.add(event)
    }
    appender.setContext(probeLogger.getLoggerContext)
    appender.start()
    probeLogger.addAppender(appender)
    val maskBefore = SecureLogging.maskCalls
    val droppedBefore = Helper.mdcLogDroppedCount

    try {
      (1 to Calls).foreach(i => Probe.debug { built.incrementAndGet(); s"written $i" })

      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
      while (captured.size < Calls && System.nanoTime() < deadline) Thread.sleep(20)

      built.get shouldBe Calls
      captured.size shouldBe Calls
      (SecureLogging.maskCalls - maskBefore) should be >= Calls.toLong
      (Helper.mdcLogDroppedCount - droppedBefore) shouldBe 0L
    } finally probeLogger.detachAppender(appender)
  }
}
