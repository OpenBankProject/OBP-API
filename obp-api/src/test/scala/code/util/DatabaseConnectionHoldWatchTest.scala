package code.util

import java.lang.reflect.{InvocationHandler, Method, Proxy => JProxy}
import java.sql.{Connection, SQLException}
import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.{FlatSpec, Matchers}

/**
 * DatabaseConnectionHoldWatch wraps every connection taken from the pool. These tests check that
 * the wrapper passes calls through unchanged, that a connection held past the limit is warned
 * about once with the code holding it, and that returning it to the pool ends the watch.
 */
class DatabaseConnectionHoldWatchTest extends FlatSpec with Matchers {

  /** A stand-in for a pooled connection: counts close() calls and fails rollback() with an SQLException. */
  private class FakeConnection {
    val closeCalls = new AtomicInteger(0)
    val connection: Connection = JProxy.newProxyInstance(
      classOf[Connection].getClassLoader,
      Array(classOf[Connection]),
      new InvocationHandler {
        def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {
          case "close"         => closeCalls.incrementAndGet(); null
          case "getAutoCommit" => java.lang.Boolean.FALSE
          case "getSchema"     => "the_schema"
          case "rollback"      => throw new SQLException("rollback failed")
          case _               => null
        }
      }
    ).asInstanceOf[Connection]
  }

  private def limitMillis: Long = DatabaseConnectionHoldWatch.holdWarningSeconds * 1000

  "the watch" should "be on by default" in {
    DatabaseConnectionHoldWatch.enabled shouldBe true
  }

  it should "pass calls through to the connection and keep its exceptions as they are" in {
    val fake = new FakeConnection
    val watched = DatabaseConnectionHoldWatch.watch(fake.connection)
    try {
      watched.getAutoCommit shouldBe false
      watched.getSchema shouldBe "the_schema"
      watched.equals(watched) shouldBe true
      watched.equals(fake.connection) shouldBe false
      an[SQLException] should be thrownBy watched.rollback()
    } finally watched.close()
    fake.closeCalls.get shouldBe 1
  }

  it should "warn once about a connection held past the limit, naming the code holding it" in {
    val fake = new FakeConnection
    val watched = DatabaseConnectionHoldWatch.watch(fake.connection)
    try {
      val past = System.currentTimeMillis() + limitMillis + 1000
      DatabaseConnectionHoldWatch.heldTooLongCountAt(past) should be >= 1
      val warnings = DatabaseConnectionHoldWatch.sweep(past)
      val ours = warnings.filter(_.contains(classOf[DatabaseConnectionHoldWatchTest].getName))
      ours should have size 1
      ours.head should include("has been out of the pool for")
      ours.head should include(s"taken by thread ${Thread.currentThread().getName}")
      DatabaseConnectionHoldWatch.sweep(past + 1000).filter(_.contains(classOf[DatabaseConnectionHoldWatchTest].getName)) shouldBe empty
    } finally watched.close()
  }

  it should "stop watching a connection once it is back in the pool, even if it is closed twice" in {
    val fake = new FakeConnection
    val past = System.currentTimeMillis() + limitMillis + 1000
    val before = DatabaseConnectionHoldWatch.heldTooLongCountAt(past)
    val watched = DatabaseConnectionHoldWatch.watch(fake.connection)
    DatabaseConnectionHoldWatch.heldTooLongCountAt(past) shouldBe before + 1
    watched.close()
    watched.close()
    DatabaseConnectionHoldWatch.heldTooLongCountAt(past) shouldBe before
    fake.closeCalls.get shouldBe 2
  }
}
