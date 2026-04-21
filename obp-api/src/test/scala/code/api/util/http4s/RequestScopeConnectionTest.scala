package code.api.util.http4s

import cats.effect.{IO, IOLocal}
import cats.effect.unsafe.IORuntime
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.db.ConnectionManager
import net.liftweb.util.{ConnectionIdentifier, DefaultConnectionIdentifier}
import org.scalatest.{BeforeAndAfter, FeatureSpec, GivenWhenThen, Matchers}

import java.lang.reflect.{InvocationHandler, Method, Proxy => JProxy}
import java.sql.Connection
import scala.concurrent.{ExecutionContext, Future}

/**
 * Unit tests for the request-scoped transaction infrastructure:
 *   - RequestScopeConnection.makeProxy  — lifecycle methods are no-ops
 *   - RequestAwareConnectionManager     — proxy vs. delegate selection
 *   - RequestScopeConnection.fromFuture — TTL propagation to Future workers
 *
 * All tests use JDK dynamic proxy to build trackable mock Connections; no
 * mocking framework is needed.  The `after` block resets the global TTL so
 * that tests do not bleed state into each other.
 */
class RequestScopeConnectionTest extends FeatureSpec with Matchers with GivenWhenThen with BeforeAndAfter {

  // Use the OBP EC so TtlRunnable wraps every Future submission — required for
  // the TTL propagation scenarios.
  implicit val ec: ExecutionContext = com.openbankproject.commons.ExecutionContext.Implicits.global
  implicit val runtime: IORuntime   = IORuntime.global

  after {
    // Reset global TTL state and fiber-locals so tests are independent.
    RequestScopeConnection.currentProxy.set(null)
    RequestScopeConnection.requestProxyLocal.set(None).unsafeRunSync()
    RequestScopeConnection.requestLazyAcquire.set(None).unsafeRunSync()
  }

  // ─── helpers ─────────────────────────────────────────────────────────────────

  /** Mutable counters written by the tracking Connection handler. */
  class ConnectionTracker {
    @volatile var commitCount   = 0
    @volatile var rollbackCount = 0
    @volatile var closeCount    = 0
    @volatile var autoCommitArg: Option[Boolean] = None
  }

  /** Create a JDK-proxy Connection that records lifecycle calls. */
  private def trackingConn(t: ConnectionTracker): Connection =
    JProxy.newProxyInstance(
      classOf[Connection].getClassLoader,
      Array(classOf[Connection]),
      new InvocationHandler {
        def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef =
          method.getName match {
            case "commit"        => t.commitCount   += 1; null
            case "rollback"      => t.rollbackCount += 1; null
            case "close"         => t.closeCount    += 1; null
            case "setAutoCommit" =>
              if (args != null && args.nonEmpty)
                t.autoCommitArg = Some(args(0).asInstanceOf[Boolean])
              null
            case "getAutoCommit" => Boolean.box(t.autoCommitArg.getOrElse(true))
            case "isClosed"      => Boolean.box(false)
            case _               => null
          }
      }
    ).asInstanceOf[Connection]

  private def simpleManager(conn: Connection): ConnectionManager =
    new ConnectionManager {
      def newConnection(name: ConnectionIdentifier): Box[Connection] = Full(conn)
      def releaseConnection(c: Connection): Unit = ()
    }

  // ─── makeProxy ───────────────────────────────────────────────────────────────

  feature("RequestScopeConnection.makeProxy — lifecycle methods are no-ops") {

    scenario("commit on the proxy does not reach the real connection") {
      Given("A tracked real connection wrapped in a proxy")
      val t     = new ConnectionTracker
      val proxy = RequestScopeConnection.makeProxy(trackingConn(t))

      When("commit is called on the proxy")
      proxy.commit()

      Then("The real connection's commit counter remains zero")
      t.commitCount shouldBe 0
    }

    scenario("rollback on the proxy does not reach the real connection") {
      Given("A tracked real connection wrapped in a proxy")
      val t     = new ConnectionTracker
      val proxy = RequestScopeConnection.makeProxy(trackingConn(t))

      When("rollback is called on the proxy")
      proxy.rollback()

      Then("The real connection's rollback counter remains zero")
      t.rollbackCount shouldBe 0
    }

    scenario("close on the proxy does not reach the real connection") {
      Given("A tracked real connection wrapped in a proxy")
      val t     = new ConnectionTracker
      val proxy = RequestScopeConnection.makeProxy(trackingConn(t))

      When("close is called on the proxy")
      proxy.close()

      Then("The real connection's close counter remains zero")
      t.closeCount shouldBe 0
    }

    scenario("non-lifecycle methods are forwarded to the real connection") {
      Given("A tracked real connection wrapped in a proxy")
      val t     = new ConnectionTracker
      val proxy = RequestScopeConnection.makeProxy(trackingConn(t))

      When("setAutoCommit(false) is called on the proxy")
      proxy.setAutoCommit(false)

      Then("The real connection receives the call with the correct argument")
      t.autoCommitArg shouldBe Some(false)
    }
  }

  // ─── RequestAwareConnectionManager.newConnection ─────────────────────────────

  feature("RequestAwareConnectionManager.newConnection — proxy vs. delegate selection") {

    scenario("Returns the request proxy when currentProxy TTL is populated") {
      Given("A proxy stored in the TTL")
      val proxy = RequestScopeConnection.makeProxy(trackingConn(new ConnectionTracker))
      RequestScopeConnection.currentProxy.set(proxy)

      And("A delegate that would return a different connection")
      val mgr = new RequestAwareConnectionManager(simpleManager(trackingConn(new ConnectionTracker)))

      When("newConnection is called")
      val result = mgr.newConnection(DefaultConnectionIdentifier)

      Then("The proxy is returned — the delegate is bypassed")
      result shouldBe Full(proxy)
    }

    scenario("Falls through to the delegate when TTL holds null") {
      Given("No proxy in the TTL")
      RequestScopeConnection.currentProxy.set(null)

      And("A delegate that returns a known connection")
      val delegateConn = trackingConn(new ConnectionTracker)
      val mgr          = new RequestAwareConnectionManager(simpleManager(delegateConn))

      When("newConnection is called")
      val result = mgr.newConnection(DefaultConnectionIdentifier)

      Then("The delegate's connection is returned")
      result shouldBe Full(delegateConn)
    }
  }

  // ─── RequestAwareConnectionManager.releaseConnection ─────────────────────────

  feature("RequestAwareConnectionManager.releaseConnection — proxy is never released") {

    scenario("Releasing the proxy is a no-op — the delegate is not called") {
      Given("A proxy set in the TTL")
      val proxy = RequestScopeConnection.makeProxy(trackingConn(new ConnectionTracker))
      RequestScopeConnection.currentProxy.set(proxy)

      var delegateReleased = false
      val delegate = new ConnectionManager {
        def newConnection(name: ConnectionIdentifier): Box[Connection] = Empty
        def releaseConnection(conn: Connection): Unit = delegateReleased = true
      }
      val mgr = new RequestAwareConnectionManager(delegate)

      When("releaseConnection is called with the proxy instance")
      mgr.releaseConnection(proxy)

      Then("The delegate's releaseConnection is never invoked")
      delegateReleased shouldBe false
    }

    scenario("Releasing a non-proxy connection delegates normally") {
      Given("No proxy in the TTL (null)")
      RequestScopeConnection.currentProxy.set(null)

      var releasedConn: Connection = null
      val realConn = trackingConn(new ConnectionTracker)
      val delegate = new ConnectionManager {
        def newConnection(name: ConnectionIdentifier): Box[Connection] = Empty
        def releaseConnection(conn: Connection): Unit = releasedConn = conn
      }
      val mgr = new RequestAwareConnectionManager(delegate)

      When("releaseConnection is called with a non-proxy connection")
      mgr.releaseConnection(realConn)

      Then("The delegate receives the exact same connection instance")
      releasedConn should be theSameInstanceAs realConn
    }
  }

  // ─── RequestScopeConnection.fromFuture ───────────────────────────────────────

  feature("RequestScopeConnection.fromFuture — TTL propagation to Future workers") {

    scenario("Future observes the proxy via TTL when requestProxyLocal is populated") {
      Given("A proxy stored in requestProxyLocal")
      val proxy = RequestScopeConnection.makeProxy(trackingConn(new ConnectionTracker))

      val program = for {
        _    <- RequestScopeConnection.requestProxyLocal.set(Some(proxy))
        seen <- RequestScopeConnection.fromFuture {
                  // TtlRunnable (OBP EC) captures currentProxy from the submitting
                  // compute thread and restores it on the worker thread.
                  Future { RequestScopeConnection.currentProxy.get() }
                }
        _    <- RequestScopeConnection.requestProxyLocal.set(None) // cleanup
      } yield seen

      When("fromFuture is executed inside an IO fiber")
      val seen = program.unsafeRunSync()

      Then("The Future observed the proxy through the TransmittableThreadLocal")
      seen should be theSameInstanceAs proxy
    }

    scenario("Future observes null TTL when requestProxyLocal holds None") {
      Given("requestProxyLocal is None (no active request scope)")
      val program = for {
        _    <- RequestScopeConnection.requestProxyLocal.set(None)
        seen <- RequestScopeConnection.fromFuture {
                  Future { RequestScopeConnection.currentProxy.get() }
                }
      } yield seen

      When("fromFuture is executed with no proxy active")
      val seen = program.unsafeRunSync()

      Then("The Future sees null in the TTL — no proxy was propagated")
      seen shouldBe null
    }

    scenario("Future observes the proxy via TTL when acquired lazily through requestLazyAcquire") {
      Given("requestProxyLocal is None but requestLazyAcquire holds an acquisition IO")
      val proxy = RequestScopeConnection.makeProxy(trackingConn(new ConnectionTracker))
      val acquireIO: IO[Connection] = IO.pure(proxy)

      val program = for {
        _    <- RequestScopeConnection.requestLazyAcquire.set(Some(acquireIO))
        seen <- RequestScopeConnection.fromFuture {
                  Future { RequestScopeConnection.currentProxy.get() }
                }
      } yield seen

      When("fromFuture is executed with only requestLazyAcquire populated")
      val seen = program.unsafeRunSync()

      Then("The Future observed the proxy acquired lazily through the TransmittableThreadLocal")
      seen should be theSameInstanceAs proxy
    }

    scenario("Lazy acquisition is skipped when requestProxyLocal already holds a proxy") {
      Given("requestProxyLocal already holds a cached proxy")
      val cachedProxy  = RequestScopeConnection.makeProxy(trackingConn(new ConnectionTracker))
      var acquireCalled = false
      val acquireIO: IO[Connection] = IO { acquireCalled = true; cachedProxy }

      val program = for {
        _    <- RequestScopeConnection.requestProxyLocal.set(Some(cachedProxy))
        _    <- RequestScopeConnection.requestLazyAcquire.set(Some(acquireIO))
        seen <- RequestScopeConnection.fromFuture {
                  Future { RequestScopeConnection.currentProxy.get() }
                }
      } yield seen

      When("fromFuture is executed")
      val seen = program.unsafeRunSync()

      Then("The cached proxy is used and the acquisition IO is never called")
      seen should be theSameInstanceAs cachedProxy
      acquireCalled shouldBe false
    }

    scenario("fromFuture returns the value produced by the Future") {
      Given("A simple Future that returns a known value")
      val program = for {
        _      <- RequestScopeConnection.requestProxyLocal.set(None)
        result <- RequestScopeConnection.fromFuture {
                    Future.successful(42)
                  }
      } yield result

      When("fromFuture wraps and awaits the Future")
      val result = program.unsafeRunSync()

      Then("The returned value matches the Future's result")
      result shouldBe 42
    }
  }
}
