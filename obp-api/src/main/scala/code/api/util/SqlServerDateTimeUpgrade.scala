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

import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

/**
 * Lift Mapper creates MappedDateTime columns as legacy DATETIME on SQL Server, whose valid range starts at
 * 1753-01-01. OBP stores dates before that (an example date of birth of 1100-01-01), which the driver
 * rejects with "The conversion of a datetime2 data type to a datetime data type resulted in an
 * out-of-range value". This upgrades every DATETIME column of every base table to DATETIME2.
 */
object SqlServerDateTimeUpgrade extends MdcLoggable {

  /** Columns that were upgraded, and those that could not be, each as `[schema].[table].[column]`. */
  final case class Result(altered: List[String], failed: List[String])

  def upgradeAll(): Result = {
    val altered = ListBuffer[String]()
    val failed = ListBuffer[String]()
    DB.use(DefaultConnectionIdentifier) { conn =>
      try {
        val columns = ListBuffer[(String, String, String)]()
        val stmt = conn.createStatement()
        try {
          val rs = stmt.executeQuery(
            """
              |SELECT c.TABLE_SCHEMA, c.TABLE_NAME, c.COLUMN_NAME
              |FROM INFORMATION_SCHEMA.COLUMNS c
              |JOIN INFORMATION_SCHEMA.TABLES t
              |  ON c.TABLE_NAME = t.TABLE_NAME AND c.TABLE_SCHEMA = t.TABLE_SCHEMA
              |WHERE c.DATA_TYPE = 'datetime' AND t.TABLE_TYPE = 'BASE TABLE'
              |""".stripMargin
          )
          while (rs.next()) {
            columns += ((rs.getString(1), rs.getString(2), rs.getString(3)))
          }
          rs.close()
        } finally {
          stmt.close()
        }

        for ((schema, table, column) <- columns) {
          val qualified = s"[$schema].[$table].[$column]"
          try {
            val alterStmt = conn.createStatement()
            try {
              alterStmt.execute(s"ALTER TABLE [$schema].[$table] ALTER COLUMN [$column] DATETIME2 NULL")
              conn.commit()
              altered += qualified
            } finally {
              alterStmt.close()
            }
          } catch {
            case NonFatal(e) =>
              failed += s"$qualified (${e.getMessage})"
          }
        }
      } catch {
        case NonFatal(e) =>
          logger.warn(s"SqlServerDateTimeUpgrade: listing the DATETIME columns failed: ${e.getMessage}")
      }
    }
    Result(altered.toList, failed.toList)
  }
}
