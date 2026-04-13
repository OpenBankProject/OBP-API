package code.api.util.http4s

import cats.effect.{IO, IOLocal}
import cats.effect.unsafe.IORuntime
import com.alibaba.ttl.TransmittableThreadLocal
import net.liftweb.common.{Box, Full}
import net.liftweb.db.ConnectionManager
import net.liftweb.util.ConnectionIdentifier

import code.util.Helper.MdcLoggable
import java.lang.reflect.{InvocationHandler, Method, Proxy => JProxy}
import java.sql.Connection
import scala.concurrent.Future

/**
 * Request-scoped transaction support for v7 http4s endpoints.
 *
 * PROBLEM: Lift Mapper uses a plain ThreadLocal for connection tracking, while
 * cats-effect IO switches compute threads across flatMap / IO.fromFuture boundaries.
 * A single DB.use scope opened on thread T is invisible on thread T2 after a
 * thread switch, so each mapper call would normally open its own connection and
 * commit independently — no request-level atomicity.
 *
 * SOLUTION (two-layer):
 *
 *   Layer 1 — IOLocal (fiber-local, survives IO thread switches):
 *     Stores the request-scoped proxy for the duration of the request fiber.
 *     Always readable from any IO step in the same fiber regardless of which
 *     compute thread is currently executing.
 *
 *   Layer 2 — TransmittableThreadLocal (thread-local, propagated to Futures):
 *     Set on the compute thread immediately before each IO(Future { }) submission.
 *     The global ExecutionContext wraps every Runnable with TtlRunnable, which
 *     captures TTL values from the submitting thread and restores them on the
 *     worker thread — so the Future body sees the same proxy as the IO fiber.
 *
 * FLOW per request (ResourceDocMiddleware):
 *   1. Borrow a real Connection from HikariCP.
 *   2. Wrap it in a non-closing proxy (commit/rollback/close are no-ops).
 *   3. Store the proxy in requestProxyLocal (IOLocal) and currentProxy (TTL).
 *   4. Run validateRequest + routes.run inside withRequestTransaction.
 *   5. Each IO.fromFuture call site uses RequestScopeConnection.fromFuture, which:
 *        a. Reads the proxy from requestProxyLocal (IOLocal — always correct).
 *        b. Sets currentProxy (TTL) on the current compute thread.
 *        c. Calls IO.fromFuture — the Future is submitted from the compute thread
 *           where TTL is set, so TtlRunnable propagates it to the worker thread.
 *   6. Inside the Future, Lift Mapper calls DB.use(DefaultConnectionIdentifier).
 *      RequestAwareConnectionManager.newConnection reads currentProxy (TTL) and
 *      returns the proxy → all mapper calls share one underlying Connection.
 *   7. The proxy's no-op commit/close prevents Lift from committing or releasing
 *      the connection at the end of each individual DB.use scope.
 *   8. At request end: commit (or rollback on exception) and close the real connection.
 *
 * METRIC WRITES: recordMetric runs in IO.blocking (blocking pool, no TTL from compute
 * thread). currentProxy.get() returns null there, so RequestAwareConnectionManager
 * falls back to the pool — metric writes use a separate connection and commit
 * independently, matching v6 behaviour.
 *
 * NON-V7 PATHS (v6 via bridge, background tasks): requestProxyLocal is not set,
 * currentProxy is null — RequestAwareConnectionManager delegates to APIUtil.vendor
 * as before. DB.buildLoanWrapper (v6) continues to manage its own transaction.
 */
object RequestScopeConnection extends MdcLoggable {

  /**
   * Fiber-local proxy reference.  Readable from any IO step in the request fiber
   * regardless of which compute thread runs it.  This is the source of truth.
   */
  val requestProxyLocal: IOLocal[Option[Connection]] =
    IOLocal[Option[Connection]](None).unsafeRunSync()(IORuntime.global)

  /**
   * Thread-local proxy reference, propagated to Future workers via TtlRunnable.
   * Set from requestProxyLocal immediately before each IO(Future { }) submission.
   */
  val currentProxy: TransmittableThreadLocal[Connection] =
    new TransmittableThreadLocal[Connection]()

  /**
   * Wrap a real Connection in a proxy that no-ops commit, rollback, and close.
   * All other methods delegate to the real connection.
   *
   * This prevents Lift's per-DB.use lifecycle from committing or returning the
   * connection to the pool before the request transaction scope ends.
   */
  def makeProxy(real: Connection): Connection =
    JProxy.newProxyInstance(
      classOf[Connection].getClassLoader,
      Array(classOf[Connection]),
      new InvocationHandler {
        def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef =
          method.getName match {
            case "commit" | "rollback" | "close" => null
            case _ =>
              try {
                val result =
                  if (args == null || args.isEmpty) method.invoke(real)
                  else method.invoke(real, args: _*)
                if (result == null || method.getReturnType == Void.TYPE) null else result
              } catch {
                case e: java.lang.reflect.InvocationTargetException
                    if Option(e.getCause).exists(_.isInstanceOf[java.sql.SQLException]) =>
                  logger.error(
                    s"[RequestScopeProxy] method=${method.getName} failed on closed/returned connection. " +
                    s"This means the request-scoped proxy was handed to code that ran AFTER withRequestTransaction " +
                    s"committed and closed the underlying connection. " +
                    s"Likely cause: v7 path fell through to Http4sLiftWebBridge without a transaction scope — " +
                    s"currentProxy was still set on this thread from a previous fiber or was not cleared. " +
                    s"Cause: ${e.getCause.getMessage}",
                    e.getCause
                  )
                  throw e
              }
          }
      }
    ).asInstanceOf[Connection]

  /**
   * Drop-in replacement for IO.fromFuture(IO(fut)).
   *
   * Reads the request proxy from the IOLocal (reliable across IO thread switches),
   * re-sets the TTL on the current compute thread, then submits the Future.
   * The global EC's TtlRunnable propagates the TTL to the Future's worker thread,
   * so DB.use inside the Future sees and reuses the request-scoped connection.
   */
  def fromFuture[A](fut: => Future[A]): IO[A] =
    requestProxyLocal.get.flatMap { proxyOpt =>
      IO(proxyOpt.foreach(currentProxy.set)) *>
        IO.fromFuture(IO(fut))
    }
}

/**
 * ConnectionManager that returns the request-scoped proxy when a transaction is
 * active, delegating to the original vendor otherwise.
 *
 * Registered in Boot.scala instead of APIUtil.vendor directly:
 *   DB.defineConnectionManager(..., new RequestAwareConnectionManager(APIUtil.vendor))
 *
 * Used by:
 *   - v7 native endpoints (gets proxy from TTL, set right before Future submission)
 *   - v6 via bridge / background tasks (TTL is null → delegates to vendor as before)
 */
class RequestAwareConnectionManager(delegate: ConnectionManager) extends ConnectionManager with MdcLoggable {

  override def newConnection(name: ConnectionIdentifier): Box[Connection] = {
    val proxy = RequestScopeConnection.currentProxy.get()
    val thread = Thread.currentThread().getName
    val caller = new Exception().getStackTrace.drop(1).take(5).mkString(" <- ")
    if (proxy != null) {
      // Guard: if the underlying connection is already closed, the proxy is stale — it
      // was captured in a TtlRunnable submitted during a prior request and that request's
      // withRequestTransaction has already committed and closed the real connection.
      // Returning a stale proxy would throw "Connection is closed" inside the caller's
      // DB.use and, if that caller is inside authenticate, would be caught as Left(_)
      // and silently turned into a 401 response.
      val proxyIsClosed = try { proxy.isClosed() } catch { case e: Exception =>
        logger.warn(s"[RequestAwareConnectionManager] isClosed() threw on proxy (thread=$thread): ${e.getClass.getName}: ${e.getMessage}")
        true
      }
      if (!proxyIsClosed) {
        logger.debug(
          s"[RequestAwareConnectionManager] newConnection: returning open request-scoped proxy " +
          s"(thread=$thread, caller=$caller)"
        )
        Full(proxy)
      } else {
        logger.warn(
          s"[RequestAwareConnectionManager] newConnection: currentProxy is set but its underlying " +
          s"connection is already closed (thread=$thread). " +
          s"TTL-stale proxy from a prior request. Falling back to a fresh vendor connection. " +
          s"caller=$caller"
        )
        delegate.newConnection(name)
      }
    } else {
      logger.debug(
        s"[RequestAwareConnectionManager] newConnection: no request proxy — delegating to vendor " +
        s"(thread=$thread, caller=$caller)"
      )
      delegate.newConnection(name)
    }
  }

  /**
   * If conn is our request proxy, skip release — it is managed by withRequestTransaction.
   * Otherwise delegate to the original vendor (which does HikariCP ProxyConnection.close()).
   *
   * Reference equality is safe: one proxy instance per request, same object throughout.
   */
  override def releaseConnection(conn: Connection): Unit = {
    val proxy = RequestScopeConnection.currentProxy.get()
    val thread = Thread.currentThread().getName
    if (proxy != null && (conn eq proxy.asInstanceOf[AnyRef])) {
      logger.debug(s"[RequestAwareConnectionManager] releaseConnection: skipping release of request-scoped proxy (thread=$thread)")
    } else {
      if (proxy != null) {
        logger.debug(s"[RequestAwareConnectionManager] releaseConnection: conn is NOT the request proxy — delegating to vendor (thread=$thread)")
      }
      delegate.releaseConnection(conn)
    }
  }
}
