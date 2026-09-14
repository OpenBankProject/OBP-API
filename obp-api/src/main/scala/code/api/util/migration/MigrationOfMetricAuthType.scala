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

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.metrics.MappedMetric
import net.liftweb.mapper.Schemifier

/**
 * Migration: add `auth_type VARCHAR(32)` to both the live `Metric` table and the
 * `metricarchive` table — the authentication SCHEME of each call ("Consent",
 * "OAuth2", "OAuth1", "DirectLogin", "GatewayLogin", "DAuth", "Anonymous",
 * "Other"), never the credential itself.
 *
 * No backup and no backfill: the column is additive and nullable — historical rows
 * legitimately predate it and stay null. No index: always queried alongside the
 * indexed date range.
 *
 * Lift's Schemifier auto-creates the column on fresh deploys from the updated model;
 * this migration handles existing deploys. Table name note as in
 * MigrationOfMetricConsentReferenceId: unquoted lowercase `metric` everywhere.
 */
object MigrationOfMetricAuthType {

  def migrate(name: String): Boolean = {
    DbFunction.tableExists(MappedMetric) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val dbDriver = APIUtil.getPropsValue("db.driver") openOr "org.h2.Driver"
        val isMssql = dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver")
        var isSuccessful = false
        val sqlLog = new StringBuilder()

        try {
          val addColumnMetric = if (isMssql) {
            "ALTER TABLE metric ADD auth_type VARCHAR(32) NULL;"
          } else {
            "ALTER TABLE metric ADD COLUMN IF NOT EXISTS auth_type VARCHAR(32);"
          }
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => addColumnMetric)).append("\n")

          val addColumnArchive = if (isMssql) {
            "ALTER TABLE metricarchive ADD auth_type VARCHAR(32) NULL;"
          } else {
            "ALTER TABLE metricarchive ADD COLUMN IF NOT EXISTS auth_type VARCHAR(32);"
          }
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => addColumnArchive)).append("\n")

          isSuccessful = true
        } catch {
          case e: Exception =>
            isSuccessful = false
            sqlLog.append(s"\nException: ${e.getMessage}\n")
        }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Executed SQL:
             |$sqlLog
             |""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String = s"""${MappedMetric._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
