package code.api.v6_0_0

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.AppenderBase
import code.api.util.JsonSchemaGenerator
import code.api.v2_2_0.MessageDocsJsonCache
import code.setup.DefaultUsers
import code.util.Helper
import org.slf4j.LoggerFactory

import java.util.concurrent.{CopyOnWriteArrayList, TimeUnit}
import scala.collection.JavaConverters._

/**
 * Budget tests: make real HTTP requests and check what they cost (generator runs, cache hits,
 * log lines), not only what they return. A change that breaks a cache or adds a log line to a
 * hot path fails here instead of showing up as CPU and heap growth in production.
 *
 * Counters only ever go up and are shared by the whole JVM, so every check compares before
 * and after values. Background jobs may log while a scenario runs, which is why the log
 * budgets have some slack.
 */
class PerformanceBudgetTest extends V600ServerSetup with DefaultUsers {

  private val Requests = 20
  private val Connector = "rest_vMar2019"

  // Measured, then rounded up. Raise it only with a reason; a jump usually means a new log line
  // on the request path.
  private val MaxLogLinesPerCachedRequest = 5

  private def v2_2_0_Request = baseRequest / "obp" / "v2.2.0"

  /** Captures every event reaching the root logger while `body` runs. */
  private def captureLogs(body: => Unit): List[ILoggingEvent] = {
    val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[LogbackLogger]
    val captured = new CopyOnWriteArrayList[ILoggingEvent]()
    val appender = new AppenderBase[ILoggingEvent] {
      override def append(event: ILoggingEvent): Unit = captured.add(event)
    }
    appender.setContext(root.getLoggerContext)
    appender.start()
    root.addAppender(appender)
    try {
      body
      awaitLogsWritten(captured)
    } finally root.detachAppender(appender)
    captured.asScala.toList
  }

  /** Log writes are asynchronous: wait for the dispatch queue to empty and output to settle. */
  private def awaitLogsWritten(captured: CopyOnWriteArrayList[ILoggingEvent]): Unit = {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    var lastSize = -1
    while ((Helper.mdcLogQueueDepth > 0 || captured.size != lastSize) && System.nanoTime() < deadline) {
      lastSize = captured.size
      Thread.sleep(100)
    }
  }

  private def describe(events: List[ILoggingEvent]): String =
    events.map(e => s"${e.getLevel} ${e.getLoggerName}: ${e.getFormattedMessage.take(160)}").mkString("\n")

  feature("GET /obp/v2.2.0/message-docs/CONNECTOR stays within its cost budget") {

    scenario("repeated requests build the response once and are then served from memory") {
      MessageDocsJsonCache.invalidateAll()
      val hitsBefore = MessageDocsJsonCache.stats.hitCount
      val missesBefore = MessageDocsJsonCache.stats.missCount
      val generatorBefore = MessageDocsJsonCache.generatorCalls
      val sharedGetsBefore = MessageDocsJsonCache.sharedGets

      (1 to Requests).foreach { _ =>
        makeGetRequest((v2_2_0_Request / "message-docs" / Connector).GET).code should equal(200)
      }

      MessageDocsJsonCache.stats.missCount - missesBefore shouldBe 1
      MessageDocsJsonCache.stats.hitCount - hitsBefore shouldBe (Requests - 1)
      // 0 when an earlier run left the response in Redis, otherwise 1.
      (MessageDocsJsonCache.generatorCalls - generatorBefore) should be <= 1L
      MessageDocsJsonCache.sharedGets - sharedGetsBefore shouldBe 1
    }

    scenario("unknown connector names are rejected before they reach the cache") {
      makeGetRequest((v2_2_0_Request / "message-docs" / Connector).GET).code should equal(200)
      val sizeBefore = MessageDocsJsonCache.size
      val generatorBefore = MessageDocsJsonCache.generatorCalls

      (1 to Requests).foreach { i =>
        makeGetRequest((v2_2_0_Request / "message-docs" / s"no_such_connector_$i").GET).code should not equal 200
      }

      MessageDocsJsonCache.size shouldBe sizeBefore
      MessageDocsJsonCache.generatorCalls shouldBe generatorBefore
    }

    scenario("a cached request writes no warnings or errors and few log lines") {
      makeGetRequest((v2_2_0_Request / "message-docs" / Connector).GET).code should equal(200) // warm up

      val events = captureLogs {
        (1 to Requests).foreach { _ =>
          makeGetRequest((v2_2_0_Request / "message-docs" / Connector).GET).code should equal(200)
        }
      }

      withClue(s"captured ${events.size} log events:\n${describe(events)}\n") {
        events.filter(_.getLevel.isGreaterOrEqual(Level.WARN)) shouldBe empty
        events.size should be <= (Requests * MaxLogLinesPerCachedRequest)
      }
    }
  }

  feature("GET /obp/v6.0.0/message-docs/CONNECTOR/json-schema stays within its cost budget") {

    scenario("repeated requests build the schema at most once") {
      val generatorBefore = JsonSchemaGenerator.generatorCalls

      (1 to Requests).foreach { _ =>
        makeGetRequest((v6_0_0_Request / "message-docs" / Connector / "json-schema").GET).code should equal(200)
      }

      // Redis sits in front of the in-process cache; either way the schema is built at most once.
      (JsonSchemaGenerator.generatorCalls - generatorBefore) should be <= 1L
    }
  }
}
