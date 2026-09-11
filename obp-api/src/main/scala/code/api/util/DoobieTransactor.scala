package code.api.util

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import doobie.util.transactor.Strategy
import net.liftweb.common.Full
import net.liftweb.db.DB

import scala.concurrent.{ExecutionContext, Future}

/**
 * Doobie Transactor for OBP-API
 *
 * Provides a type-safe, functional JDBC layer for raw SQL queries.
 * This handles all JDBC types correctly, including SQL Server's NVARCHAR (type -9)
 * which Lift Mapper's DB.runQuery doesn't handle.
 *
 * TRANSACTION UNIFICATION:
 * When called within an http4s request scope, Doobie uses the SAME Connection that
 * RequestScopeConnection holds for the current request transaction (via
 * Transactor.fromConnection). This means Doobie queries participate in the request
 * transaction boundary:
 * - Same connection, same transaction, same commit/rollback
 * - Doobie can see uncommitted writes made earlier in the same request (same session)
 * - If the request transaction rolls back, Doobie's operations are also rolled back
 *
 * When called outside an http4s request scope (e.g., background tasks, schedulers),
 * falls back to the shared HikariCP connection pool via Transactor.fromDataSource.
 *
 * Benefits over DBUtil.runQuery:
 * - Type-safe query results via case classes
 * - Type-safe parameters (no SQL injection risk)
 * - Proper JDBC type handling for all databases
 * - Composable queries using cats-effect IO
 *
 * Usage:
 * {{{
 * import doobie._
 * import doobie.implicits._
 * import code.api.util.DoobieUtil._
 *
 * case class TopApi(count: Int, partialFunction: String, version: String)
 *
 * val query = sql"""
 *   SELECT count(*), implementedbypartialfunction, implementedinversion
 *   FROM metric
 *   WHERE date_c >= $fromDate
 *   GROUP BY implementedbypartialfunction, implementedinversion
 * """.query[TopApi].to[List]
 *
 * val result: List[TopApi] = DoobieUtil.runQuery(query)
 * }}}
 */
object DoobieUtil extends MdcLoggable {

  /**
   * Fallback transactor that shares the application HikariCP connection pool.
   * Used when no http4s request scope is available (background tasks, schedulers).
   * Strategy.void: Doobie will not call setAutoCommit/commit/rollback.
   */
  private lazy val fallbackTransactor: Transactor[IO] = {
    val sharedDataSource = APIUtil.vendor.HikariDatasource.ds
    logger.info("DoobieUtil: Initialized fallback transactor sharing the application HikariCP pool")
    val xa = Transactor.fromDataSource[IO].apply(
      sharedDataSource,
      BlockingIoExecutionContext.ec
    )
    xa.copy(strategy0 = Strategy.void)
  }

  /**
   * Create a transactor that wraps an existing JDBC Connection.
   * Strategy.void ensures Doobie does not interfere with the request-scoped transaction
   * management owned by RequestScopeConnection.withBusinessDBTransaction.
   */
  private def transactorFromConnection(conn: java.sql.Connection): Transactor[IO] = {
    val xa = Transactor.fromConnection[IO].apply(conn, None)
    xa.copy(strategy0 = Strategy.void)
  }

  /**
   * Try to get the current request's Connection.
   *
   * Primary path is the http4s RequestScopeConnection proxy (set per request via TTL).
   * As a secondary fallback it reads Lift Mapper's DB.currentConnection — this only
   * resolves when the call happens to run inside an open Mapper DB.use scope (the proxy
   * is also on Mapper's connection stack there); it peeks at the DynaVar without triggering
   * reference counting or creating a new connection.
   *
   * Returns Some(connection) when a request-scoped connection is available, None otherwise
   * (background tasks, schedulers, tests without a request scope).
   */
  /** True iff a request-scoped connection is available, i.e. runUpdate will share the request
   *  transaction rather than falling back to a standalone auto-commit transactor. Callers whose
   *  correctness depends on the request transaction (e.g. SELECT ... FOR UPDATE row locks that
   *  must be HELD after runUpdate returns) should check this and warn or fail when false —
   *  the fallback transactor commits at transact end, releasing any lock immediately. */
  def hasRequestScopeConnection: Boolean = currentRequestConnection.isDefined

  /**
   * A proxy is stale once the request that created it has committed and closed the real
   * connection underneath. It can still be sitting in the thread-local: a task submitted late in
   * that request runs afterwards, on a thread that still carries the proxy.
   *
   * Using a stale proxy throws "Connection is closed" from HikariCP's closed-connection stub,
   * which surfaces as a 500 on a request that is otherwise fine. It only happens when one
   * request's async tail overlaps the next, so it looks like flakiness in whichever suite was
   * running at the time.
   *
   * RequestAwareConnectionManager.newConnection applies the same check on the Lift side; this is
   * the Doobie half of it. A proxy that throws from isClosed() counts as closed - there is no
   * reading of that where the connection is safe to use.
   */
  private def isUsable(conn: java.sql.Connection): Boolean =
    try !conn.isClosed
    catch {
      case e: Exception =>
        logger.warn(s"DoobieUtil: isClosed() threw on the request proxy, treating it as closed: " +
          s"${e.getClass.getName}: ${e.getMessage}")
        false
    }

  private def currentRequestConnection: Option[java.sql.Connection] = {
    // 1. Primary: the http4s RequestScopeConnection proxy from Alibaba TTL
    Option(code.api.util.http4s.RequestScopeConnection.currentProxy.get()).filter(isUsable).orElse {
      // 2. Fallback: Lift Mapper's DB.currentConnection (only Full inside an open DB.use scope)
      DB.currentConnection match {
        case Full(superConn) =>
          val conn: java.sql.Connection = superConn.connection
          if (!conn.isClosed) Some(conn) else None
        case _ => None
      }
    }
  }

  /**
   * Run a Doobie query synchronously, sharing the request-scoped transaction when available.
   *
   * When called within an http4s request scope:
   * - Uses the SAME Connection that RequestScopeConnection holds for the current request
   * - Doobie query participates in the request transaction (same commit/rollback)
   * - Can see uncommitted writes made earlier in the same request (same database session)
   *
   * When called outside an http4s request scope (background tasks, schedulers):
   * - Falls back to the shared HikariCP pool (separate connection)
   *
   * @param query The Doobie ConnectionIO query to execute
   * @return The query result
   */
  def runQuery[A](query: ConnectionIO[A]): A = {
    currentRequestConnection match {
      case Some(conn) =>
        // Inside a request scope: use the same connection for transaction unification
        query.transact(transactorFromConnection(conn)).unsafeRunSync()
      case None =>
        // Outside a request scope: fallback to shared pool
        logger.debug("DoobieUtil.runQuery: No request scope, using fallback pool transactor")
        query.transact(fallbackTransactor).unsafeRunSync()
    }
  }

  /**
   * Run a Doobie query asynchronously, returning a Future.
   * Note: async queries always use the fallback pool transactor because
   * the request connection may not be available on a different thread.
   *
   * @param query The Doobie ConnectionIO query to execute
   * @param ec ExecutionContext for the Future
   * @return Future containing the query result
   */
  def runQueryAsync[A](query: ConnectionIO[A])(implicit ec: ExecutionContext): Future[A] = {
    query.transact(fallbackTransactor).unsafeToFuture()
  }

  /**
   * Run a Doobie query and return an IO.
   * Note: IO queries always use the fallback pool transactor because
   * the IO may be evaluated outside the http4s request scope.
   *
   * @param query The Doobie ConnectionIO query to execute
   * @return IO containing the query result
   */
  def runQueryIO[A](query: ConnectionIO[A]): IO[A] = {
    query.transact(fallbackTransactor)
  }

  /**
   * Fallback transactor that commits. Used for updates outside an http4s request scope
   * (background tasks, schedulers).
   */
  private lazy val fallbackUpdateTransactor: Transactor[IO] = {
    val sharedDataSource = APIUtil.vendor.HikariDatasource.ds
    Transactor.fromDataSource[IO].apply(
      sharedDataSource,
      BlockingIoExecutionContext.ec
    ) // Strategy.default includes commit/rollback
  }

  /**
   * Run a Doobie update synchronously, sharing the request-scoped transaction when available.
   * Outside an http4s request scope, uses a transactor that COMMITs the connection.
   */
  def runUpdate[A](query: ConnectionIO[A]): A = {
    currentRequestConnection match {
      case Some(conn) =>
        query.transact(transactorFromConnection(conn)).unsafeRunSync()
      case None =>
        logger.debug("DoobieUtil.runUpdate: No request scope, using fallback update transactor")
        query.transact(fallbackUpdateTransactor).unsafeRunSync()
    }
  }

  /**
   * Check if the database is SQL Server (for syntax differences like TOP vs LIMIT)
   */
  def isSqlServer: Boolean = DBUtil.isSqlServer

  /**
   * Get database URL for checking database type
   */
  def dbUrl: String = DBUtil.dbUrl
}
