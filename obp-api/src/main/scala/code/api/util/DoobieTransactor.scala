package code.api.util

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import com.zaxxer.hikari.HikariConfig
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
 * Doobie Transactor for OBP-API
 *
 * Provides a type-safe, functional JDBC layer for raw SQL queries.
 * This handles all JDBC types correctly, including SQL Server's NVARCHAR (type -9)
 * which Lift's DB.runQuery doesn't handle.
 *
 * IMPORTANT: This uses a SEPARATE HikariCP connection pool from Lift's pool.
 * This ensures complete isolation and safety - Doobie manages its own connections
 * without any interference with Lift's connection management.
 *
 * Benefits over DBUtil.runQuery:
 * - Type-safe query results via case classes
 * - Type-safe parameters (no SQL injection risk)
 * - Proper JDBC type handling for all databases
 * - Composable queries using cats-effect IO
 * - Complete isolation from Lift's connection pool
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
object DoobieUtil {

  /**
   * Lazy-initialized HikariCP transactor for Doobie.
   *
   * This creates a separate connection pool from Lift's pool, ensuring
   * complete isolation and safety. The pool is initialized on first use
   * and kept alive for the lifetime of the application.
   */
  private lazy val transactor: Transactor[IO] = {
    val (dbUrl, dbUser, dbPassword) = DBUtil.getDbConnectionParameters

    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(dbUrl)
    hikariConfig.setUsername(dbUser)
    hikariConfig.setPassword(dbPassword)

    // Pool configuration - conservative settings for safety
    hikariConfig.setPoolName("doobie-pool")
    hikariConfig.setMaximumPoolSize(
      APIUtil.getPropsAsIntValue("doobie.hikari.maximumPoolSize", 10)
    )
    hikariConfig.setMinimumIdle(
      APIUtil.getPropsAsIntValue("doobie.hikari.minimumIdle", 2)
    )
    hikariConfig.setConnectionTimeout(
      APIUtil.getPropsAsLongValue("doobie.hikari.connectionTimeout", 30000L)
    )
    hikariConfig.setIdleTimeout(
      APIUtil.getPropsAsLongValue("doobie.hikari.idleTimeout", 600000L)
    )
    hikariConfig.setMaxLifetime(
      APIUtil.getPropsAsLongValue("doobie.hikari.maxLifetime", 1800000L)
    )

    // Set driver class based on database type
    if (dbUrl.contains("sqlserver")) {
      hikariConfig.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
    } else if (dbUrl.contains("postgresql")) {
      hikariConfig.setDriverClassName("org.postgresql.Driver")
    } else if (dbUrl.contains("mysql")) {
      hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver")
    } else if (dbUrl.contains("h2")) {
      hikariConfig.setDriverClassName("org.h2.Driver")
    } else if (dbUrl.contains("oracle")) {
      hikariConfig.setDriverClassName("oracle.jdbc.OracleDriver")
    }
    // For other databases, HikariCP will try to auto-detect the driver

    // Create the transactor - this allocates the pool immediately
    // We use unsafeRunSync here because we need a stable, long-lived transactor
    HikariTransactor.fromHikariConfig[IO](hikariConfig)
      .allocated
      .map(_._1)  // Get the transactor, discard the finalizer (pool lives for app lifetime)
      .unsafeRunSync()
  }

  /**
   * Run a Doobie query synchronously using the dedicated Doobie connection pool.
   *
   * This uses a completely separate HikariCP pool from Lift's pool,
   * ensuring no interference between Doobie and Lift's connection management.
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
