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

import code.util.Helper.MdcLoggable
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier

import java.sql.Connection
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

/**
 * Lift Mapper creates MappedDateTime columns as legacy DATETIME on SQL Server, whose valid range starts at
 * 1753-01-01. OBP stores dates before that (an example date of birth of 1100-01-01), which the driver
 * rejects with "The conversion of a datetime2 data type to a datetime data type resulted in an
 * out-of-range value". This upgrades every DATETIME column of every base table to DATETIME2.
 *
 * SQL Server refuses to change the type of a column that an index or a default constraint depends on
 * (error 5074), and a plain `ALTER COLUMN ... DATETIME2 NULL` would also make a NOT NULL column nullable.
 * So for each column the indexes and the default that depend on it are dropped, the type is changed with
 * the nullability the column had, and then they are created again as they were. The work for one column
 * is one transaction: if any step fails, the column and its indexes are left exactly as they were.
 *
 * An index is recreated with its uniqueness, clustered or not, key columns and their direction, included
 * columns and filter. Storage options such as fill factor and compression are not carried over.
 */
object SqlServerDateTimeUpgrade extends MdcLoggable {

  /** Columns that were upgraded, and those that could not be, each as `[schema].[table].[column]`. */
  final case class Result(altered: List[String], failed: List[String])

  private final case class DateTimeColumn(schema: String, table: String, column: String, columnId: Int, objectId: Int, nullable: Boolean) {
    def qualifiedTable: String = s"${quote(schema)}.${quote(table)}"
    def qualified: String = s"${quote(schema)}.${quote(table)}.${quote(column)}"
  }

  private final case class IndexDefinition(name: String, unique: Boolean, clustered: Boolean, keys: List[(String, Boolean)], included: List[String], filter: Option[String])

  private final case class DefaultConstraint(name: String, definition: String)

  private def quote(identifier: String): String = s"[${identifier.replace("]", "]]")}]"

  private def query[A](connection: Connection, sql: String, parameters: Any*)(read: java.sql.ResultSet => A): List[A] = {
    val statement = connection.prepareStatement(sql)
    try {
      parameters.zipWithIndex.foreach { case (value, position) => statement.setObject(position + 1, value) }
      val result = statement.executeQuery()
      try {
        val buffer = ListBuffer[A]()
        while (result.next()) buffer += read(result)
        buffer.toList
      } finally result.close()
    } finally statement.close()
  }

  private def execute(connection: Connection, sql: String): Unit = {
    val statement = connection.createStatement()
    try statement.execute(sql) finally statement.close()
  }

  private def dateTimeColumns(connection: Connection): List[DateTimeColumn] =
    query(connection,
      """SELECT s.name, t.name, c.name, c.column_id, t.object_id, c.is_nullable
        |FROM sys.columns c
        |JOIN sys.tables t ON t.object_id = c.object_id
        |JOIN sys.schemas s ON s.schema_id = t.schema_id
        |JOIN sys.types ty ON ty.user_type_id = c.user_type_id
        |WHERE ty.name = 'datetime'
        |ORDER BY s.name, t.name, c.column_id""".stripMargin
    )(r => DateTimeColumn(r.getString(1), r.getString(2), r.getString(3), r.getInt(4), r.getInt(5), r.getBoolean(6)))

  /** The indexes that use the column as a key or as an included column. Throws for what cannot be recreated here. */
  private def dependentIndexes(connection: Connection, column: DateTimeColumn): List[IndexDefinition] = {
    val indexes = query(connection,
      """SELECT DISTINCT i.index_id, i.name, i.is_unique, i.type, i.is_primary_key, i.is_unique_constraint, i.filter_definition
        |FROM sys.indexes i
        |JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
        |WHERE i.object_id = ? AND ic.column_id = ? AND i.index_id > 0""".stripMargin,
      Int.box(column.objectId), Int.box(column.columnId)
    )(r => (r.getInt(1), r.getString(2), r.getBoolean(3), r.getInt(4), r.getBoolean(5), r.getBoolean(6), Option(r.getString(7))))

    indexes.map { case (indexId, name, unique, indexType, isPrimaryKey, isUniqueConstraint, filter) =>
      if (isPrimaryKey || isUniqueConstraint)
        throw new IllegalStateException(s"$name is a primary key or unique constraint on a DATETIME column; it has to be upgraded by hand")
      if (indexType != 1 && indexType != 2)
        throw new IllegalStateException(s"$name is an index of a kind this upgrade does not recreate (type $indexType); it has to be upgraded by hand")
      val parts = query(connection,
        """SELECT c.name, ic.is_included_column, ic.is_descending_key
          |FROM sys.index_columns ic
          |JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
          |WHERE ic.object_id = ? AND ic.index_id = ?
          |ORDER BY ic.is_included_column, ic.key_ordinal, ic.index_column_id""".stripMargin,
        Int.box(column.objectId), Int.box(indexId)
      )(r => (r.getString(1), r.getBoolean(2), r.getBoolean(3)))
      IndexDefinition(
        name, unique, clustered = indexType == 1,
        keys = parts.filterNot(_._2).map { case (c, _, descending) => (c, descending) },
        included = parts.filter(_._2).map(_._1),
        filter = filter
      )
    }
  }

  private def defaultConstraints(connection: Connection, column: DateTimeColumn): List[DefaultConstraint] =
    query(connection,
      "SELECT name, definition FROM sys.default_constraints WHERE parent_object_id = ? AND parent_column_id = ?",
      Int.box(column.objectId), Int.box(column.columnId)
    )(r => DefaultConstraint(r.getString(1), r.getString(2)))

  private def createIndexSql(column: DateTimeColumn, index: IndexDefinition): String = {
    val keys = index.keys.map { case (c, descending) => s"${quote(c)}${if (descending) " DESC" else ""}" }.mkString(", ")
    val included = if (index.included.isEmpty) "" else s" INCLUDE (${index.included.map(quote).mkString(", ")})"
    val filter = index.filter.map(f => s" WHERE $f").getOrElse("")
    s"CREATE ${if (index.unique) "UNIQUE " else ""}${if (index.clustered) "CLUSTERED" else "NONCLUSTERED"} INDEX ${quote(index.name)} ON ${column.qualifiedTable} ($keys)$included$filter"
  }

  private def upgradeColumn(connection: Connection, column: DateTimeColumn): Unit = {
    val indexes = dependentIndexes(connection, column)
    val defaults = defaultConstraints(connection, column)
    try {
      indexes.foreach(index => execute(connection, s"DROP INDEX ${quote(index.name)} ON ${column.qualifiedTable}"))
      defaults.foreach(d => execute(connection, s"ALTER TABLE ${column.qualifiedTable} DROP CONSTRAINT ${quote(d.name)}"))
      execute(connection, s"ALTER TABLE ${column.qualifiedTable} ALTER COLUMN ${quote(column.column)} DATETIME2 ${if (column.nullable) "NULL" else "NOT NULL"}")
      defaults.foreach(d => execute(connection, s"ALTER TABLE ${column.qualifiedTable} ADD CONSTRAINT ${quote(d.name)} DEFAULT ${d.definition} FOR ${quote(column.column)}"))
      indexes.foreach(index => execute(connection, createIndexSql(column, index)))
      connection.commit()
    } catch {
      case NonFatal(e) =>
        connection.rollback()
        throw e
    }
  }

  def upgradeAll(): Result = {
    val altered = ListBuffer[String]()
    val failed = ListBuffer[String]()
    DB.use(DefaultConnectionIdentifier) { connection =>
      val columns =
        try dateTimeColumns(connection)
        catch {
          case NonFatal(e) =>
            logger.warn(s"SqlServerDateTimeUpgrade: listing the DATETIME columns failed: ${e.getMessage}")
            Nil
        }
      columns.foreach { column =>
        try {
          upgradeColumn(connection, column)
          altered += column.qualified
        } catch {
          case NonFatal(e) => failed += s"${column.qualified} (${e.getMessage})"
        }
      }
    }
    Result(altered.toList, failed.toList)
  }
}
