/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.util

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import com.zaxxer.hikari.HikariDataSource

import java.lang.reflect.{InvocationHandler, InvocationTargetException, Method, Proxy => JProxy}
import java.sql.Connection
import java.util.concurrent.{ConcurrentHashMap, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * This object writes a warning to the log when a database connection has been out of the pool for
 * longer than a set time, and says which code is holding it.
 *
 * The problem it addresses: when the connection pool runs out, every request that needs a
 * connection waits and then fails, and nothing in the log says which work is holding the
 * connections. A request cut off by the endpoint timeout (`long_endpoint_timeout`) gets its 504
 * answer, but the database work underneath it carries on, and keeps its connection, until the
 * query returns, which can take many minutes.
 *
 * HikariCP has a leak detection of its own, but it writes through its own logger, so its warnings
 * never reach the log cache, and the log cache would drop the stack trace even if they did. This
 * object writes through MdcLoggable and puts the stack frames into the text of the message.
 *
 * Taking a connection costs only an entry in a map: no stack trace is captured then. Instead, once
 * a connection has been held too long, the sweep reads the stack of the thread that took it, as it
 * is at that moment. For a query that is still running, that is exactly the code waiting on it. A
 * connection taken by one thread and then used by others (the request transaction in
 * RequestScopeConnection works like that) may have a thread that has moved on; the warning then
 * says that the thread is no longer running OBP-API code.
 *
 * When a connection that was warned about goes back to the pool, a second warning gives the total
 * time it was held, so a reader can pair the two by the connection label.
 *
 * The prop `database_connection_hold_warning_seconds` sets the time (default 60 seconds); 0 turns
 * the watch off, and connections are then handed out unwrapped.
 */
object DatabaseConnectionHoldWatch extends MdcLoggable {

  /** How long a connection may be held before a warning is written. 0 or less turns the watch off. */
  lazy val holdWarningSeconds: Long =
    APIUtil.getPropsAsLongValue("database_connection_hold_warning_seconds", 60L)

  def enabled: Boolean = holdWarningSeconds > 0

  /** Frames of OBP-API's own code to put in a warning; the rest are library and runtime frames. */
  private val maximumOwnFramesShown = 15

  /** One connection out of the pool. `warned` is set by the sweep once the warning has been written. */
  private final class Checkout(val takenAtMillis: Long, val thread: Thread) {
    @volatile var warned: Boolean = false
    val label: String = Integer.toHexString(System.identityHashCode(this))
    def heldSeconds(nowMillis: Long): Long = (nowMillis - takenAtMillis) / 1000
  }

  private val checkouts: java.util.Set[Checkout] = ConcurrentHashMap.newKeySet[Checkout]()

  private val sweeperStarted = new AtomicBoolean(false)

  /** How many connections are out of the pool right now and have been held longer than the limit. */
  def heldTooLongCount: Int = heldTooLongCountAt(System.currentTimeMillis())

  private[util] def heldTooLongCountAt(nowMillis: Long): Int = {
    val limitMillis = holdWarningSeconds * 1000
    checkouts.asScala.count(checkout => nowMillis - checkout.takenAtMillis >= limitMillis)
  }

  private lazy val holdWarnings = code.telemetry.Telemetry.counter("obp.api.database.connection.hold_warnings")

  /**
   * Returns `connection` wrapped so that closing it, which returns it to the pool, is recorded.
   * Every other call goes straight to `connection`.
   */
  def watch(connection: Connection): Connection =
    if (!enabled) connection
    else {
      startSweeperOnce()
      val checkout = new Checkout(System.currentTimeMillis(), Thread.currentThread())
      checkouts.add(checkout)
      JProxy.newProxyInstance(
        classOf[Connection].getClassLoader,
        Array(classOf[Connection]),
        new InvocationHandler {
          def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef =
            method.getName match {
              case "equals"   => java.lang.Boolean.valueOf(proxy.asInstanceOf[AnyRef] eq args(0))
              case "hashCode" => java.lang.Integer.valueOf(System.identityHashCode(proxy))
              case name =>
                if (name == "close") released(checkout)
                try {
                  if (args == null) method.invoke(connection) else method.invoke(connection, args: _*)
                } catch {
                  case e: InvocationTargetException => throw Option(e.getCause).getOrElse(e)
                }
            }
        }
      ).asInstanceOf[Connection]
    }

  private def released(checkout: Checkout): Unit =
    if (checkouts.remove(checkout) && checkout.warned) {
      logger.warn(
        s"DatabaseConnectionHoldWatch says: database connection ${checkout.label}, reported earlier as held " +
          s"too long, went back to the pool after ${checkout.heldSeconds(System.currentTimeMillis())}s."
      )
    }

  private def startSweeperOnce(): Unit =
    if (sweeperStarted.compareAndSet(false, true)) {
      val scheduler = Executors.newSingleThreadScheduledExecutor { runnable =>
        val thread = new Thread(runnable, "database-connection-hold-watch")
        thread.setDaemon(true)
        thread
      }
      val sweepIntervalSeconds = math.max(1L, math.min(10L, holdWarningSeconds / 2))
      scheduler.scheduleWithFixedDelay(() => { sweep(); () }, sweepIntervalSeconds, sweepIntervalSeconds, TimeUnit.SECONDS)
      logger.info(s"DatabaseConnectionHoldWatch says: started; warning after ${holdWarningSeconds}s, checking every ${sweepIntervalSeconds}s")
    }

  /** Writes a warning for each connection newly past the limit at `nowMillis`, and returns the warnings. */
  private[util] def sweep(nowMillis: Long = System.currentTimeMillis()): List[String] =
    try {
      val limitMillis = holdWarningSeconds * 1000
      checkouts.asScala.toList
        .filter(checkout => !checkout.warned && nowMillis - checkout.takenAtMillis >= limitMillis)
        .map { checkout =>
          checkout.warned = true
          holdWarnings.increment()
          val warning = warningFor(checkout, nowMillis)
          logger.warn(warning)
          warning
        }
    } catch {
      // The sweep must keep running: an exception would cancel the scheduled task for good.
      case e: Throwable =>
        logger.error(s"DatabaseConnectionHoldWatch says: sweep failed: ${e.getMessage}")
        Nil
    }

  private def warningFor(checkout: Checkout, nowMillis: Long): String = {
    val thread = checkout.thread
    val frames = thread.getStackTrace.toList
    val ownFrames = frames.filter { frame =>
      val className = frame.getClassName
      (className.startsWith("code.") || className.startsWith("bootstrap.")) &&
        !className.startsWith("code.util.DatabaseConnectionHoldWatch$")
    }
    val whereNow =
      if (ownFrames.isEmpty)
        "That thread is no longer running OBP-API code: it handed the connection on to other work, so where the connection is used now is not known."
      else {
        val shown = ownFrames.take(maximumOwnFramesShown).map(frame => s"  at $frame")
        val innermost = frames.headOption.map(frame => s"  innermost frame: $frame").toList
        ("Where that thread is now:" :: innermost ::: shown).mkString("\n")
      }
    s"DatabaseConnectionHoldWatch says: database connection ${checkout.label} has been out of the pool for " +
      s"${checkout.heldSeconds(nowMillis)}s (limit ${holdWarningSeconds}s), taken by thread ${thread.getName} " +
      s"(now ${thread.getState}). ${poolSummary}\n$whereNow"
  }

  /** The pool whose figures a warning reports; set by CustomDBVendor once the pool exists. */
  @volatile var pool: Option[HikariDataSource] = None

  private def poolSummary: String =
    pool.flatMap(dataSource => Option(dataSource.getHikariPoolMXBean)).flatMap { poolBean =>
      Try(
        s"Pool: ${poolBean.getActiveConnections} in use, ${poolBean.getIdleConnections} idle, " +
          s"${poolBean.getThreadsAwaitingConnection} waiting for one."
      ).toOption
    }.getOrElse("")
}
