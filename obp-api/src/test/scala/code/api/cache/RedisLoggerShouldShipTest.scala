package code.api.cache

import org.scalatest.{FlatSpec, Matchers}

/**
 * Guards the two invariants MdcLoggable's level gate (code.util.Helper) depends on to skip
 * SecureLogging.maskSensitive when nothing will consume the message:
 *
 *   1. LogLevel's declaration order is severity-ascending (TRACE < DEBUG < INFO < WARNING < ERROR),
 *      since RedisLogger.shouldShip compares levels by `.id`. Scala Enumeration assigns ids by
 *      declaration order, not by name, so this is a silent footgun if the `val TRACE, DEBUG, ... =
 *      Value` line is ever reordered or a new level inserted in the wrong place - nothing about the
 *      Enumeration itself would complain, shouldShip would just start comparing the wrong things.
 *
 *   2. With Redis log shipping disabled - the default, and what this test suite runs with -
 *      shouldShip is false for every level. This is the fix's core contract: SecureLogging.
 *      maskSensitive must only run when the local logger or Redis will actually consume the
 *      message.
 *
 * What this does NOT cover: shouldShip's own enabled/min-level reading of props
 * (redis_logging_enabled / redis_logging_min_level). RedisLogger is a singleton object whose
 * config vals are read once, on first JVM access to the object - by the time any test in this
 * suite runs, something else has almost certainly already touched it during boot, so changing
 * props at test time (even via PropsReset) has no effect on what RedisLogger already cached.
 * That half of the behaviour was verified by hand against the actual props read at JVM start
 * (see the redis_logging_min_level default in sample.props.template) rather than by a test here.
 */
class RedisLoggerShouldShipTest extends FlatSpec with Matchers {

  "LogLevel" should "be declared in ascending severity order" in {
    RedisLogger.LogLevel.TRACE.id should be < RedisLogger.LogLevel.DEBUG.id
    RedisLogger.LogLevel.DEBUG.id should be < RedisLogger.LogLevel.INFO.id
    RedisLogger.LogLevel.INFO.id should be < RedisLogger.LogLevel.WARNING.id
    RedisLogger.LogLevel.WARNING.id should be < RedisLogger.LogLevel.ERROR.id
  }

  it should "round-trip through valueOf for every level MdcLoggable actually calls shouldShip with" in {
    RedisLogger.LogLevel.valueOf("TRACE") should equal(RedisLogger.LogLevel.TRACE)
    RedisLogger.LogLevel.valueOf("DEBUG") should equal(RedisLogger.LogLevel.DEBUG)
    RedisLogger.LogLevel.valueOf("INFO") should equal(RedisLogger.LogLevel.INFO)
    RedisLogger.LogLevel.valueOf("WARNING") should equal(RedisLogger.LogLevel.WARNING)
    RedisLogger.LogLevel.valueOf("ERROR") should equal(RedisLogger.LogLevel.ERROR)
  }

  "RedisLogger.shouldShip" should "be false for every level when Redis log shipping is disabled" in {
    // This suite's props leave redis_logging_enabled at its default (false), which is also the
    // production-safe default - so this exercises the same configuration MdcLoggable runs under
    // wherever nobody has opted into Redis log shipping.
    RedisLogger.isEnabled shouldBe false

    RedisLogger.shouldShip(RedisLogger.LogLevel.TRACE) shouldBe false
    RedisLogger.shouldShip(RedisLogger.LogLevel.DEBUG) shouldBe false
    RedisLogger.shouldShip(RedisLogger.LogLevel.INFO) shouldBe false
    RedisLogger.shouldShip(RedisLogger.LogLevel.WARNING) shouldBe false
    RedisLogger.shouldShip(RedisLogger.LogLevel.ERROR) shouldBe false
  }

  it should "never ship LogLevel.ALL, which is a read-side aggregate queue, not a real message level" in {
    RedisLogger.shouldShip(RedisLogger.LogLevel.ALL) shouldBe false
  }
}
