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
 * which Lift's DB.runQuery doesn't handle.
 *
 * TRANSACTION UNIFICATION:
 * When called within a Lift HTTP request context, Doobie uses the SAME Connection
 * that Lift is holding for the current request transaction (via Transactor.fromConnection).
 * This means Doobie queries participate in Lift's transaction boundary:
 * - Same connection, same transaction, same commit/rollback
 * - Doobie can see uncommitted Lift writes (same session)
 * - If Lift rolls back, Doobie's operations are also rolled back
 *
 * When called outside a Lift request context (e.g., background tasks, schedulers),
 * falls back to Lift's shared HikariCP connection pool via Transactor.fromDataSource.
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
   * Fallback transactor that shares Lift's HikariCP connection pool.
   * Used when no Lift request context is available (background tasks, schedulers).
   * Strategy.void: Doobie will not call setAutoCommit/commit/rollback.
   */
  private lazy val fallbackTransactor: Transactor[IO] = {
    val liftDataSource = APIUtil.vendor.HikariDatasource.ds
    logger.info("DoobieUtil: Initialized fallback transactor sharing Lift's HikariCP pool")
    val xa = Transactor.fromDataSource[IO].apply(
      liftDataSource,
      ExecutionContext.global
    )
    xa.copy(strategy0 = Strategy.void)
  }

  /**
   * Create a transactor that wraps an existing JDBC Connection.
   * Strategy.void ensures Doobie does not interfere with Lift's transaction management.
   */
  private def transactorFromConnection(conn: java.sql.Connection): Transactor[IO] = {
    val xa = Transactor.fromConnection[IO].apply(conn, None)
    xa.copy(strategy0 = Strategy.void)
  }

  /**
   * Try to get the current Lift request's Connection.
   * Uses DB.currentConnection which peeks at the DynoVar without
   * triggering reference counting or creating a new connection.
   * Returns Some(connection) if inside a Lift HTTP request context,
   * None otherwise (background tasks, schedulers, tests without request context).
   */
  private def liftCurrentConnection: Option[java.sql.Connection] = {
    // DB.currentConnection returns Box[SuperConnection]
    // SuperConnection has implicit conversion to java.sql.Connection
    DB.currentConnection match {
      case Full(superConn) =>
        val conn: java.sql.Connection = superConn.connection
        if (!conn.isClosed) Some(conn) else None
      case _ => None
    }
  }

  /**
   * Run a Doobie query synchronously, sharing Lift's transaction when available.
   *
   * When called within a Lift HTTP request context:
   * - Uses the SAME Connection that Lift holds for the current request
   * - Doobie query participates in Lift's transaction (same commit/rollback)
   * - Can see uncommitted Lift writes (same database session)
   *
   * When called outside a Lift request context (background tasks, schedulers):
   * - Falls back to Lift's shared HikariCP pool (separate connection)
   *
   * @param query The Doobie ConnectionIO query to execute
   * @return The query result
   */
  def runQuery[A](query: ConnectionIO[A]): A = {
    liftCurrentConnection match {
      case Some(conn) =>
        // Inside Lift request: use the same connection for transaction unification
        query.transact(transactorFromConnection(conn)).unsafeRunSync()
      case None =>
        // Outside Lift request: fallback to shared pool
        logger.debug("DoobieUtil.runQuery: No Lift request context, using fallback pool transactor")
        query.transact(fallbackTransactor).unsafeRunSync()
    }
  }

  /**
   * Run a Doobie query asynchronously, returning a Future.
   * Note: async queries always use the fallback pool transactor because
   * Lift's request connection may not be available on a different thread.
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
   * the IO may be evaluated outside the Lift request context.
   *
   * @param query The Doobie ConnectionIO query to execute
   * @return IO containing the query result
   */
  def runQueryIO[A](query: ConnectionIO[A]): IO[A] = {
    query.transact(fallbackTransactor)
  }

  /**
   * Fallback transactor that commits. Used for updates outside Lift requests.
   */
  private lazy val fallbackUpdateTransactor: Transactor[IO] = {
    val liftDataSource = APIUtil.vendor.HikariDatasource.ds
    Transactor.fromDataSource[IO].apply(
      liftDataSource,
      ExecutionContext.global
    ) // Strategy.default includes commit/rollback
  }

  /**
   * Run a Doobie update synchronously, sharing Lift's transaction when available.
   * If not in a Lift request context, uses a transactor that COMMITs the connection.
   */
  def runUpdate[A](query: ConnectionIO[A]): A = {
    liftCurrentConnection match {
      case Some(conn) =>
        query.transact(transactorFromConnection(conn)).unsafeRunSync()
      case None =>
        logger.debug("DoobieUtil.runUpdate: No Lift request context, using fallback update transactor")
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
