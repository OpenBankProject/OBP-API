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

  /** How a database writes a row limit: LIMIT, or the standard OFFSET ... FETCH that SQL Server and Oracle take. */
  private[util] sealed trait RowLimitDialect
  private[util] case object LimitDialect extends RowLimitDialect
  /** SQL Server: OFFSET ... FETCH, and only after an ORDER BY. */
  private[util] case object SqlServerFetchDialect extends RowLimitDialect
  /** Oracle: OFFSET ... FETCH, with or without an ORDER BY. */
  private[util] case object OracleFetchDialect extends RowLimitDialect

  private def rowLimitDialect: RowLimitDialect =
    if (isSqlServer) SqlServerFetchDialect else if (DBUtil.isOracle) OracleFetchDialect else LimitDialect

  /**
   * The page clause in the form the database takes. SQL Server and Oracle write it as OFFSET ... FETCH, and
   * SQL Server requires an ORDER BY in front of it; the other databases take LIMIT ... OFFSET.
   */
  def pagination(limit: Int, offset: Int): Fragment =
    if (rowLimitDialect == LimitDialect) fr"LIMIT $limit OFFSET $offset"
    else fr"OFFSET $offset ROWS FETCH NEXT $limit ROWS ONLY"

  /**
   * Whether `sql` has an ORDER BY of its own, outside any parentheses. SQL Server refuses OFFSET ... FETCH
   * without one, and refuses a second one, so the row limit has to know.
   */
  private[util] def hasTopLevelOrderBy(sql: String): Boolean = {
    val upper = sql.toUpperCase
    var depth = 0
    var found = false
    var i = 0
    while (i < upper.length) {
      upper.charAt(i) match {
        case '(' => depth += 1
        case ')' => depth -= 1
        case 'O' if depth == 0 && upper.startsWith("ORDER BY", i) &&
          (i == 0 || !Character.isLetterOrDigit(upper.charAt(i - 1))) => found = true
        case _ =>
      }
      i += 1
    }
    found
  }

  private[util] def pageFor(dialect: RowLimitDialect, query: Fragment, limit: Option[Int], offset: Option[Int]): Fragment =
    if (limit.isEmpty && offset.isEmpty) query
    else dialect match {
      case LimitDialect =>
        query ++ limit.map(n => fr"LIMIT $n").getOrElse(Fragment.empty) ++
          offset.map(n => fr"OFFSET $n").getOrElse(Fragment.empty)
      case _ =>
        // ORDER BY (SELECT NULL) asks for no particular order, which is what LIMIT without ORDER BY means.
        val order =
          if (dialect == SqlServerFetchDialect && !hasTopLevelOrderBy(query.internals.sql)) fr"ORDER BY (SELECT NULL)"
          else Fragment.empty
        query ++ order ++ fr"OFFSET ${offset.getOrElse(0)} ROWS" ++
          limit.map(n => fr"FETCH NEXT $n ROWS ONLY").getOrElse(Fragment.empty)
    }

  /**
   * `query` with an optional page: LIMIT and OFFSET where the database has them, OFFSET ... FETCH on SQL Server
   * and Oracle. SQL Server also needs an ORDER BY, so a query without one gets `ORDER BY (SELECT NULL)`.
   * With neither given, `query` is unchanged.
   *
   * The ORDER BY is looked for in `query` itself, so pass the whole statement, or at least the part that
   * carries its ORDER BY.
   */
  def pageOf(query: Fragment, limit: Option[Int], offset: Option[Int]): Fragment =
    pageFor(rowLimitDialect, query, limit, offset)

  /** `query` restricted to at most `rows` rows; see `pageOf`. */
  def limitOf(query: Fragment, rows: Int): Fragment = pageFor(rowLimitDialect, query, Some(rows), None)

  /** `query` restricted to its first row; see `pageOf`. Written as a literal 1, as `LIMIT 1` was. */
  def firstRowOf(query: Fragment): Fragment = firstRowFor(rowLimitDialect, query)

  private[util] def firstRowFor(dialect: RowLimitDialect, query: Fragment): Fragment = dialect match {
    case LimitDialect => query ++ fr"LIMIT 1"
    case dialect =>
      val order =
        if (dialect == SqlServerFetchDialect && !hasTopLevelOrderBy(query.internals.sql)) fr"ORDER BY (SELECT NULL)"
        else Fragment.empty
      query ++ order ++ fr"OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"
  }

  /**
   * Row locking for a SELECT is a table hint on SQL Server, placed straight after the table name, and a
   * FOR UPDATE suffix elsewhere. Use `lockHint` after the table and `forUpdate` at the end of the statement;
   * exactly one of the two is empty on any database.
   */
  def lockHint: Fragment = if (isSqlServer) fr"WITH (UPDLOCK, ROWLOCK)" else Fragment.empty

  def forUpdate: Fragment = if (isSqlServer) Fragment.empty else fr"FOR UPDATE"

  /**
   * Get database URL for checking database type
   */
  def dbUrl: String = DBUtil.dbUrl
}
