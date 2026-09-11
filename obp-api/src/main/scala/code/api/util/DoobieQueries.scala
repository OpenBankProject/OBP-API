package code.api.util

import doobie._
import doobie.implicits._

/**
 * Common Doobie queries used across OBP-API.
 *
 * These replace raw SQL queries that were using Lift's DB.runQuery (via DBUtil.runQuery).
 * Doobie provides type-safe query results and proper JDBC type handling for all databases,
 * including SQL Server's NVARCHAR type which Lift doesn't handle correctly.
 */
object DoobieQueries {

  /**
   * Get distinct providers from the resourceuser table.
   * Used by ResourceUser.getDistinctProviders
   *
   * @return List of distinct provider names, sorted alphabetically
   */
  def getDistinctProviders: List[String] = {
    val query: ConnectionIO[List[String]] =
      sql"""SELECT DISTINCT provider_ FROM resourceuser WHERE provider_ IS NOT NULL ORDER BY provider_"""
        .query[String]
        .to[List]

    DoobieUtil.runQuery(query)
  }
}
