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
 *   3. Store the proxy in requestProxyLocal (IOLocal) only — currentProxy (TTL) is
 *      NOT set here to avoid leaving compute threads dirty.
 *   4. Run validateOnly (auth, roles, entity lookups) — outside the transaction, on
 *      auto-commit vendor connections.  On Left: return error response, no transaction
 *      opened.  On Right (GET/HEAD): run routes.run directly on auto-commit connections.
 *      On Right (POST/PUT/DELETE/PATCH): open the transaction and run routes.run inside it.
 *   5. Each IO.fromFuture call site uses RequestScopeConnection.fromFuture, which in
 *      a single synchronous IO.defer block on compute thread T:
 *        a. Sets currentProxy (TTL) on T.
 *        b. Evaluates `fut` — the Future is submitted; TtlRunnable captures T's TTL.
 *        c. Removes currentProxy from T immediately after submission (T is clean).
 *        d. Awaits the already-submitted future asynchronously.
 *   6. Inside the Future, Lift Mapper calls DB.use(DefaultConnectionIdentifier).
 *      RequestAwareConnectionManager.newConnection reads currentProxy (TTL — set by
 *      TtlRunnable on the worker thread) and returns the proxy → all mapper calls
 *      share one underlying Connection.
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
                case e: java.lang.reflect.InvocationTargetException =>
                  val cause = Option(e.getCause).getOrElse(e)
                  logger.error(
                    s"[RequestScopeProxy] method=${method.getName} failed: ${cause.getClass.getName}: ${cause.getMessage}",
                    cause
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
   * then — in a single synchronous IO.defer block on the current compute thread T:
   *   1. Sets TTL on T so TtlRunnable captures it at Future-submission time.
   *   2. Evaluates `fut`, which submits the Future to the OBP EC; the TtlRunnable
   *      wraps the submitted task and carries the proxy to the Future's worker thread.
   *   3. Removes the TTL from T immediately, so T is clean after this step.
   *   4. Returns IO.fromFuture(IO.pure(f)) to await the already-submitted future.
   *
   * Steps 1-3 are synchronous within IO.defer, guaranteeing they all run on T before
   * any fiber scheduling can switch threads.  The Future worker still receives the
   * proxy via the TtlRunnable captured in step 2.
   */
  def fromFuture[A](fut: => Future[A]): IO[A] =
    requestProxyLocal.get.flatMap { proxyOpt =>
      IO.defer {
        proxyOpt.foreach(currentProxy.set)  // (1) set TTL on current thread T
        val f = fut                          // (2) submit Future; TtlRunnable captures proxy from T
        currentProxy.remove()               // (3) clear TTL on T — T is clean after this point
        IO.fromFuture(IO.pure(f))           // await the already-submitted future
      }
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
    if (proxy != null) {
      // Guard: if the underlying connection is already closed, the proxy is stale — it
      // was captured in a TtlRunnable submitted during a prior request and that request's
      // withBusinessDBTransaction has already committed and closed the real connection.
      // Returning a stale proxy would throw "Connection is closed" inside the caller's
      // DB.use and, if that caller is inside authenticate, would be caught as Left(_)
      // and silently turned into a 401 response.
      val proxyIsClosed = try { proxy.isClosed() } catch { case e: Exception =>
        logger.warn(s"[RequestAwareConnectionManager] isClosed() threw on proxy: ${e.getClass.getName}: ${e.getMessage}")
        true
      }
      if (!proxyIsClosed) Full(proxy)
      else {
        logger.warn(
          s"[RequestAwareConnectionManager] newConnection: stale proxy (underlying connection already " +
          s"closed) — falling back to fresh vendor connection"
        )
        delegate.newConnection(name)
      }
    } else {
      delegate.newConnection(name)
    }
  }

  /**
   * If conn is our request proxy, skip release — it is managed by withBusinessDBTransaction.
   * Otherwise delegate to the original vendor (which does HikariCP ProxyConnection.close()).
   *
   * Reference equality is safe: one proxy instance per request, same object throughout.
   */
  override def releaseConnection(conn: Connection): Unit = {
    val proxy = RequestScopeConnection.currentProxy.get()
    if (proxy != null && (conn eq proxy.asInstanceOf[AnyRef])) {
      // Skip release — this connection is managed by withBusinessDBTransaction.
    } else {
      delegate.releaseConnection(conn)
    }
  }
}
