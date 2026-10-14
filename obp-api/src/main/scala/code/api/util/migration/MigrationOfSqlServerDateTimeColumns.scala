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
package code.api.util.migration

import code.api.util.{APIUtil, DBUtil}
import code.api.util.migration.Migration.saveLog
import code.util.Helper.MdcLoggable
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier

import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

/**
 * On SQL Server, Lift Mapper's MappedDateTime generates legacy DATETIME columns whose valid date
 * range is 1753-01-01 to 9999-12-31. OBP example dates and historical records use dates before
 * 1753 (such as 1100-01-01), which causes Microsoft JDBC Driver / SQL Server to throw:
 * "The conversion of a datetime2 data type to a datetime data type resulted in an out-of-range value."
 *
 * This migration alters DATETIME columns in customer, card, and meeting tables to DATETIME2 NULL,
 * which supports the full range 0001-01-01 to 9999-12-31.
 */
object MigrationOfSqlServerDateTimeColumns extends MdcLoggable {

  def alterColumns(name: String): Boolean = {
    if (!DBUtil.isSqlServer) {
      return true
    }

    val startDate = System.currentTimeMillis()
    val commitId: String = APIUtil.gitCommit
    val altered = ListBuffer[String]()

    val targetColumns = List(
      ("mappedcustomer", "mdateofbirth"),
      ("mappedcustomer", "mfaceimagetime"),
      ("mappedcustomer", "mlastokdate"),
      ("mappedcustomerdependant", "mdateofbirth"),
      ("mappedusercustomerlink", "mdateinserted"),
      ("mappedphysicalcard", "mvalidfrom"),
      ("mappedphysicalcard", "mexpires"),
      ("mappedphysicalcard", "mcollected"),
      ("mappedphysicalcard", "mposted"),
      ("mappedphysicalcard", "mreplacementdate"),
      ("mappedmeeting", "mwhen")
    )

    DB.use(DefaultConnectionIdentifier) { conn =>
      for ((table, col) <- targetColumns) {
        val sql = s"ALTER TABLE $table ALTER COLUMN $col DATETIME2 NULL"
        try {
          val stmt = conn.createStatement()
          try {
            stmt.execute(sql)
            conn.commit()
            altered += s"$table.$col"
          } finally {
            stmt.close()
          }
        } catch {
          case NonFatal(e) =>
            logger.warn(s"MigrationOfSqlServerDateTimeColumns: skipping $sql: ${e.getMessage}")
        }
      }
    }

    val endDate = System.currentTimeMillis()
    val comment = s"Altered SQL Server DATETIME columns to DATETIME2: ${altered.mkString(", ")}"
    saveLog(name, commitId, isSuccessful = true, startDate, endDate, comment)
    true
  }
}
