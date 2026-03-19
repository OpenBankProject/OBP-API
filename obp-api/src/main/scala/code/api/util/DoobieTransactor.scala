package code.api.util

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import doobie.util.transactor.Strategy

import scala.concurrent.{ExecutionContext, Future}

/**
 * Doobie Transactor for OBP-API
 *
 * Provides a type-safe, functional JDBC layer for raw SQL queries.
 * This handles all JDBC types correctly, including SQL Server's NVARCHAR (type -9)
 * which Lift's DB.runQuery doesn't handle.
 *
 * IMPORTANT: This shares Lift's HikariCP connection pool (via APIUtil.vendor.HikariDatasource.ds)
 * instead of creating a separate pool. This reduces total database connections
 * and simplifies pool management.
 *
 * NOTE: Doobie is used ONLY for READ-ONLY queries (SELECT).
 * All write operations (INSERT/UPDATE/DELETE) must go through Lift Mapper.
 * The transactor uses Strategy.void to avoid interfering with Lift's
 * transaction management (autoCommit=false, commit/rollback per HTTP request).
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
   * Lazy-initialized transactor that shares Lift's HikariCP connection pool.
   *
   * Uses Transactor.fromDataSource to wrap Lift's existing HikariDataSource,
   * so Doobie borrows connections from the same pool as Lift Mapper.
   * This eliminates the separate Doobie pool (saving 10 database connections).
   *
   * Strategy.void is used because:
   * - Doobie is only used for read-only queries
   * - Lift already manages transactions (autoCommit=false, commit/rollback per request)
   * - We don't want Doobie to call setAutoCommit/commit/rollback on shared connections
   */
  private lazy val transactor: Transactor[IO] = {
    val liftDataSource = APIUtil.vendor.HikariDatasource.ds
    logger.info("DoobieUtil: Sharing Lift's HikariCP connection pool (no separate Doobie pool)")
    logger.info(s"DoobieUtil: Lift pool max size = ${liftDataSource.getMaximumPoolSize}")

    // Use Lift's DataSource with Strategy.void (no transaction management by Doobie)
    // connectEC = ExecutionContext.global is used for obtaining connections
    val xa = Transactor.fromDataSource[IO].apply(
      liftDataSource,
      ExecutionContext.global
    )
    // Override strategy to void: Doobie will not call setAutoCommit/commit/rollback
    // This is safe because Doobie is only used for read-only queries
    xa.copy(strategy0 = Strategy.void)
  }

  /**
   * Run a Doobie query synchronously using Lift's shared HikariCP connection pool.
   *
   * IMPORTANT: Only use for READ-ONLY queries (SELECT).
   * Do NOT use for write operations (INSERT/UPDATE/DELETE).
   *
   * @param query The Doobie ConnectionIO query to execute
   * @return The query result
   */
  def runQuery[A](query: ConnectionIO[A]): A = {
    query.transact(transactor).unsafeRunSync()
  }

  /**
   * Run a Doobie query asynchronously, returning a Future.
   *
   * IMPORTANT: Only use for READ-ONLY queries (SELECT).
   *
   * @param query The Doobie ConnectionIO query to execute
   * @param ec ExecutionContext for the Future
   * @return Future containing the query result
   */
  def runQueryAsync[A](query: ConnectionIO[A])(implicit ec: ExecutionContext): Future[A] = {
    query.transact(transactor).unsafeToFuture()
  }

  /**
   * Run a Doobie query and return an IO.
   * Useful when you want to compose with other cats-effect operations.
   *
   * IMPORTANT: Only use for READ-ONLY queries (SELECT).
   *
   * @param query The Doobie ConnectionIO query to execute
   * @return IO containing the query result
   */
  def runQueryIO[A](query: ConnectionIO[A]): IO[A] = {
    query.transact(transactor)
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
