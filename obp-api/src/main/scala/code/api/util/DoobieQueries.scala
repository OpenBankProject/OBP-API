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

  /**
   * Get parent IDs by bank ID from an attribute table (no filters).
   * Used by AttributeQueryTrait.getParentIdByParams and NewAttributeQueryTrait.getParentIdByParams
   *
   * @param tableName The attribute table name
   * @param parentIdColumn The column name for parent ID
   * @param bankIdColumn The column name for bank ID
   * @param bankId The bank ID to filter by
   * @return List of distinct parent IDs
   */
  def getDistinctParentIds(
    tableName: String,
    parentIdColumn: String,
    bankIdColumn: String,
    bankId: String
  ): List[String] = {
    // Note: We use Fragment.const for table/column names since they come from metadata
    // and are not user input. The bankId value is safely parameterized.
    val query: ConnectionIO[List[String]] = {
      val tableF = Fragment.const(tableName)
      val parentIdF = Fragment.const(parentIdColumn)
      val bankIdF = Fragment.const(bankIdColumn)

      (fr"SELECT DISTINCT attr." ++ parentIdF ++ fr" FROM " ++ tableF ++ fr" attr WHERE attr." ++ bankIdF ++ fr" = $bankId")
        .query[String]
        .to[List]
    }

    DoobieUtil.runQuery(query)
  }

  /**
   * Get parent IDs with attribute name/value data for filtering.
   * Used by AttributeQueryTrait.getParentIdByParams and NewAttributeQueryTrait.getParentIdByParams
   *
   * @param tableName The attribute table name
   * @param parentIdColumn The column name for parent ID
   * @param nameColumn The column name for attribute name
   * @param valueColumn The column name for attribute value
   * @param bankIdColumn The column name for bank ID
   * @param bankId The bank ID to filter by
   * @param params Map of attribute name -> list of acceptable values
   * @return List of (parentId, attributeName, attributeValue) tuples
   */
  def getParentIdWithAttributes(
    tableName: String,
    parentIdColumn: String,
    nameColumn: String,
    valueColumn: String,
    bankIdColumn: String,
    bankId: String,
    params: Map[String, List[String]]
  ): List[(String, String, String)] = {
    val paramList = params.toList

    // Build the SQL parameters filter
    // e.g., (name = ? AND value = ?) OR (name = ? AND value in (?, ?, ?))
    val sqlParametersFilter = paramList.map { case (name, values) =>
      if (values.size == 1) {
        (fr"(" ++ Fragment.const(nameColumn) ++ fr" = $name AND " ++ Fragment.const(valueColumn) ++ fr" = ${values.head})")
      } else {
        val valueFragments = values.map(v => fr"$v")
        val inClause = valueFragments.reduceLeft((a, b) => a ++ fr"," ++ b)
        (fr"(" ++ Fragment.const(nameColumn) ++ fr" = $name AND " ++ Fragment.const(valueColumn) ++ fr" IN (" ++ inClause ++ fr"))")
      }
    }.reduceOption((a, b) => a ++ fr" OR " ++ b).getOrElse(fr"1=1")

    val query: ConnectionIO[List[(String, String, String)]] = {
      val tableF = Fragment.const(tableName)
      val parentIdF = Fragment.const(parentIdColumn)
      val nameF = Fragment.const(nameColumn)
      val valueF = Fragment.const(valueColumn)
      val bankIdF = Fragment.const(bankIdColumn)

      (fr"SELECT attr." ++ parentIdF ++ fr", attr." ++ nameF ++ fr", attr." ++ valueF ++
        fr" FROM " ++ tableF ++ fr" attr WHERE attr." ++ bankIdF ++ fr" = $bankId AND (" ++ sqlParametersFilter ++ fr")")
        .query[(String, String, String)]
        .to[List]
    }

    DoobieUtil.runQuery(query)
  }
}
