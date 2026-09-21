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

import code.DynamicData.DynamicData
import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import net.liftweb.common.Full
import net.liftweb.mapper.Schemifier

/**
 * This migration widens the column that holds the identifier of a single Dynamic Entity record.
 *
 * The column started life as a `MappedUUID`, which is 36 characters wide because that is the length
 * of a UUID. A caller may however supply the identifier itself in the request body rather than let
 * one be generated, which is how a Dynamic Entity is given a natural key such as a country code or
 * the name of a scheme. Such a value is not a UUID and is often longer than 36 characters, and it
 * used to reach the database unchanged and fail there with
 * "value too long for type character varying(36)", surfacing to the caller as an opaque server error.
 *
 * The new width is 255 characters, which is what the two other columns holding this same identifier
 * already use: the DynamicDataId column of the row level access list in DynamicDataAccess, and the
 * `data_id` column of the SQL projection tables. Widening a column never invalidates existing rows,
 * so there is nothing to back fill and nothing to undo. Lift's Schemifier creates columns but never
 * widens one that already exists, which is why an existing database needs this migration at all.
 */
object MigrationOfDynamicDataIdFieldLength {

  def alterColumnDynamicDataIdLength(name: String): Boolean = {
    DbFunction.tableExists(DynamicData) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") match {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """
                    |ALTER TABLE dynamicdata ALTER COLUMN dynamicdataid varchar(255);
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |ALTER TABLE dynamicdata ALTER COLUMN dynamicdataid TYPE character varying(255);
                    |""".stripMargin
            }
          }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Executed SQL:
             |$executedSql
             |""".stripMargin
        isSuccessful = true
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""${DynamicData._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
