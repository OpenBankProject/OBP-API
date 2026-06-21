package code.api.util.http4s

import cats.effect.{Deferred, IO, IOLocal, Outcome}
import cats.effect.unsafe.IORuntime
import com.alibaba.ttl.TransmittableThreadLocal
import net.liftweb.common.{Box, Full}
import net.liftweb.db.ConnectionManager
import net.liftweb.util.ConnectionIdentifier
import org.http4s.Response

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import java.lang.reflect.{InvocationHandler, Method, Proxy => JProxy}
import java.sql.Connection
import scala.concurrent.Future

/**
 * Request-scoped transaction support for Http4s native endpoints.
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
 *   1. Set requestLazyAcquire to a once-only acquisition IO — no connection borrowed yet.
 *   2. Run validateOnly (auth, roles, entity lookups) — outside any transaction, on
 *      auto-commit vendor connections.  On Left: return error response, clean up IOLocals.
 *      On Right (GET/HEAD): run routes.run directly on auto-commit connections.
 *      On Right (POST/PUT/DELETE/PATCH): run routes.run inside the lazy transaction scope.
 *   3. On the FIRST fromFuture call that touches DB:
 *        a. ensureProxy reads requestProxyLocal — fast path if already cached in this fiber.
 *        b. Falls through to requestLazyAcquire: invokes the once-only IO which borrows
 *           a real Connection, wraps it in a non-closing proxy, and completes the request-
 *           scoped Deferred.  Concurrent callers that lose the race discard their connection
 *           and wait for the Deferred — all fibers end up with the same proxy.
 *        c. Caches the proxy in requestProxyLocal (fiber-local) for subsequent calls.
 *        d. Sets currentProxy (TTL) on compute thread T so TtlRunnable carries it to the
 *           Future worker thread.
 *   4. Inside the Future, Lift Mapper calls DB.use(DefaultConnectionIdentifier).
 *      RequestAwareConnectionManager.newConnection reads currentProxy (TTL) and returns
 *      the proxy → all mapper calls share one underlying Connection.
 *   5. The proxy's no-op commit/close prevents Lift from committing or releasing the
 *      connection at the end of each individual DB.use scope.
 *   6. At request end: deferred.tryGet (held in withBusinessDBTransaction's closure):
 *      - None  → endpoint made zero DB calls; pool unaffected, nothing to commit or close.
 *      - Some(realConn, _) → commit (or rollback on error/cancel), then close realConn.
 *
 * METRIC WRITES: recordMetric runs in IO.blocking (blocking pool, no TTL from compute
 * thread). currentProxy.get() returns null there, so RequestAwareConnectionManager
 * falls back to the pool — metric writes use a separate connection and commit
 * independently, matching traditional Lift behaviour.
 *
 * BACKGROUND TASKS / NON-HTTP PATHS: requestProxyLocal is not set, currentProxy is null
 * — RequestAwareConnectionManager delegates to APIUtil.vendor. Any Lift Mapper operations
 * outside of a Http4s request scope will auto-commit unless wrapped in a Lift LoanWrapper.
 */
object RequestScopeConnection extends MdcLoggable {

  /**
   * Fiber-local proxy reference.  Set lazily on the first fromFuture call that
   * needs a DB connection.  Used as a fast-path cache on subsequent calls in the
   * same fiber so the IOLocal / Deferred lookup is skipped.
   */
  val requestProxyLocal: IOLocal[Option[Connection]] =
    IOLocal[Option[Connection]](None).unsafeRunSync()(IORuntime.global)

  /**
   * Fiber-local handle to the once-only acquisition IO installed by
   * withBusinessDBTransaction.  None outside a transaction scope (GET/HEAD, or before
   * the first POST/PUT/DELETE request scope is set up).
   *
   * The IO[Connection] value is the same object reference across all fibers that
   * inherit it (IOLocal copy-on-fork copies the reference, not the IO's internal
   * Deferred state), so concurrent callers safely serialise through the Deferred.
   */
  val requestLazyAcquire: IOLocal[Option[IO[Connection]]] =
    IOLocal[Option[IO[Connection]]](None).unsafeRunSync()(IORuntime.global)

  /**
   * Thread-local proxy reference, propagated to Future workers via TtlRunnable.
   * Set from requestProxyLocal immediately before each IO(Future { }) submission.
   *
   * IMPORTANT: `childValue` is overridden to return `null` so newly-spawned threads do
   * NOT inherit the parent thread's proxy.  This blocks a subtle leak where Scala's
   * ForkJoinPool spawns a new worker while an existing worker is mid-TtlRunnable (with
   * `currentProxy` replayed): the new worker inherits the same Connection reference,
   * and every subsequent TtlRunnable.restore() reverts it to that initial inherited
   * value — even for tasks belonging to a completely different request.  Workers stuck
   * with a stale proxy then read 0 rows for the current request's freshly-written data,
   * since the underlying real connection was closed by the original request's WBT.
   *
   * Discussed by the TTL library authors:
   *   https://github.com/alibaba/transmittable-thread-local/issues/100
   * Default childValue (return parent's value) is documented as buggy when the value
   * is request-scoped state that should never cross thread boundaries except through
   * an explicit TtlRunnable capture/replay.
   */
  val currentProxy: TransmittableThreadLocal[Connection] =
    new TransmittableThreadLocal[Connection]() {
      override protected def childValue(parentValue: Connection): Connection = null
    }

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
   * Ensures a proxy connection is available (acquiring lazily on first call if
   * withBusinessDBTransaction set up a lazy acquisition scope), then — in a single
   * synchronous IO.defer block on the current compute thread T:
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
    ensureProxy.flatMap { proxyOpt =>
      IO.defer {
        proxyOpt.foreach(currentProxy.set)  // (1) set TTL on current thread T
        val f = fut                          // (2) submit Future; TtlRunnable captures proxy from T
        currentProxy.remove()               // (3) clear TTL on T — T is clean after this point
        IO.fromFuture(IO.pure(f))           // await the already-submitted future
      }
    }

  /**
   * Wrap an http4s route IO in a request-scoped DB transaction.
   *
   * Installs a once-only lazy connection-acquisition scope (requestLazyAcquire): no real
   * connection is borrowed until the FIRST fromFuture call that touches the DB.  The first
   * fiber to complete the inner Deferred wins; concurrent losers discard their connection
   * and share the winner's proxy, so all fibers use one underlying Connection / one
   * transaction.  On success: commit then close.  On error/cancel: rollback then close.
   * If no DB call was made: nothing to commit or close (pool unaffected).
   *
   * GET/HEAD must NOT be wrapped (they run on auto-commit vendor connections).  Used by
   * ResourceDocMiddleware and by services that build their own request scope
   * without the middleware (e.g. Http4sDynamicEntity).
   */
  def withBusinessDBTransaction(io: IO[Response[IO]]): IO[Response[IO]] =
    Deferred[IO, (Connection, Connection)].flatMap { deferred =>
      // acquireOnce: idempotent across concurrent callers via the Deferred.
      val acquireOnce: IO[Connection] = for {
        realConn <- IO.blocking(APIUtil.vendor.HikariDatasource.ds.getConnection())
        _        <- IO.blocking { realConn.setAutoCommit(false) }
        proxy    =  makeProxy(realConn)
        ok       <- deferred.complete((realConn, proxy))
        _        <- if (!ok) IO.blocking { try { realConn.close() } catch { case _: Exception => () } }
                    else IO.unit
        p        <- deferred.get.map(_._2)
      } yield p

      requestLazyAcquire.set(Some(acquireOnce)).bracket(_ =>
        io.guaranteeCase { outcome =>
          deferred.tryGet.flatMap {
            case None => IO.unit   // no DB calls — pool unaffected
            case Some((realConn, _)) =>
              requestProxyLocal.set(None) *>
                (outcome match {
                  case Outcome.Succeeded(_) =>
                    IO.blocking { realConn.commit() }
                  case _ =>
                    IO.blocking { try { realConn.rollback() } catch { case _: Exception => () } }
                }) *>
                IO.blocking { try { realConn.close() } catch { case _: Exception => () } }
          }
        }
      )(_ => requestLazyAcquire.set(None))
    }

  /**
   * Returns the proxy for the current fiber, acquiring it lazily on first call.
   *
   * Fast path: requestProxyLocal is already set (same fiber, subsequent call) → O(1).
   * Slow path: requestLazyAcquire holds an acquisition IO → invoke it, cache proxy in
   *   requestProxyLocal for this fiber's future calls.
   * No-op: neither is set (GET/HEAD, or no transaction scope) → None, TTL stays clear.
   */
  private def ensureProxy: IO[Option[Connection]] =
    requestProxyLocal.get.flatMap {
      case some @ Some(_) => IO.pure(some)
      case None =>
        requestLazyAcquire.get.flatMap {
          case None          => IO.pure(None)
          case Some(acquire) =>
            acquire.flatMap { proxy =>
              requestProxyLocal.set(Some(proxy)).as(Some(proxy))
            }
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
 *   - Http4s native endpoints (gets proxy from TTL, set right before Future submission)
 *   - Background tasks / Non-HTTP paths (TTL is null → delegates to vendor as before)
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
