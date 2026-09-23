package bootstrap.http4s

import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.LoggerContextListener
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import org.slf4j.Logger.ROOT_LOGGER_NAME

/**
 * Directly verifies the shipped src/main/resources/logback.xml resolves
 * <root level="${LOG_LEVEL:-INFO}"> the way this fix depends on:
 *
 *   - LOG_LEVEL unset -> root defaults to INFO, not the old hard-coded DEBUG.
 *   - LOG_LEVEL set -> root honours it.
 *
 * The application's actual SLF4J LoggerContext is configured once, at JVM start, from whatever
 * LOG_LEVEL happened to be set at that moment -- so asserting against
 * LoggerFactory.getILoggerFactory() here wouldn't exercise the default-value resolution at all,
 * only whatever this test JVM's own environment happened to be. Instead this parses the actual
 * classpath resource into a fresh, throwaway LoggerContext per scenario (via Joran, the same
 * parser Logback uses internally), with the system property set immediately beforehand -- this is
 * the standard way to unit-test a logback.xml's own logic rather than the ambient environment.
 */
class LogbackDefaultLevelTest extends FlatSpec with Matchers with BeforeAndAfterEach {

  private val propertyName = "LOG_LEVEL"

  override def afterEach(): Unit = {
    System.clearProperty(propertyName)
  }

  private def rootLevelFromShippedConfig(): Level = {
    val context = new LoggerContext()
    context.reset()
    val configurator = new JoranConfigurator()
    configurator.setContext(context)
    configurator.doConfigure(getClass.getClassLoader.getResource("logback.xml"))
    context.getLogger(ROOT_LOGGER_NAME).getLevel
  }

  "the shipped logback.xml" should "default the root level to INFO when LOG_LEVEL is unset" in {
    System.clearProperty(propertyName)
    rootLevelFromShippedConfig() should equal(Level.INFO)
  }

  it should "honour LOG_LEVEL=DEBUG when set" in {
    System.setProperty(propertyName, "DEBUG")
    rootLevelFromShippedConfig() should equal(Level.DEBUG)
  }

  it should "honour LOG_LEVEL=WARN when set" in {
    System.setProperty(propertyName, "WARN")
    rootLevelFromShippedConfig() should equal(Level.WARN)
  }
}
