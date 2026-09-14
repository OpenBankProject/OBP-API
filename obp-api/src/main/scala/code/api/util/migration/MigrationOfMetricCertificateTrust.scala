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
 * Migration: add `certificate_trust VARCHAR(32)` and `certificate_trust_detail VARCHAR(255)` to
 * both the live `metric` table and the `metricarchive` table.
 *
 * The columns persist PeerTrust.Resolution per request — how the caller's certificate was
 * established (`direct` / `forwarded` / `none`) and the specifics (the forwarding proxy's subject
 * DN, or the rejection reason). See docs/MTLS_TOPOLOGIES.md §5.5: this is the audit trail the
 * prod-behind-nginx rollout gates on.
 *
 * No backfill: historical rows predate the trust decision; nullable columns.
 *
 * No index: certificate_trust holds three distinct values, so an index on it alone is useless —
 * queries combine it with the already-indexed date range.
 *
 * Unlike MigrationOfMetricConsentReferenceId this takes NO backup of the metric table first: the
 * change is purely additive (nullable columns, no rewrite of existing data), and the metric table
 * is routinely the largest table in a deployment — copying it to add two nullable columns costs
 * more than the operation it would insure.
 *
 * Lift's Schemifier auto-creates the columns on fresh deploys from the updated model; this
 * migration handles existing deploys.
 */
object MigrationOfMetricCertificateTrust {

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
          // Both tables are referenced as lowercase unquoted names, matching every other SQL site
          // in the codebase (Schemifier emits unquoted DDL, so Postgres folds to lowercase).
          val statements =
            if (isMssql) List(
              "ALTER TABLE metric ADD certificate_trust VARCHAR(32) NULL;",
              "ALTER TABLE metric ADD certificate_trust_detail VARCHAR(255) NULL;",
              "ALTER TABLE metricarchive ADD certificate_trust VARCHAR(32) NULL;",
              "ALTER TABLE metricarchive ADD certificate_trust_detail VARCHAR(255) NULL;"
            ) else List(
              "ALTER TABLE metric ADD COLUMN IF NOT EXISTS certificate_trust VARCHAR(32);",
              "ALTER TABLE metric ADD COLUMN IF NOT EXISTS certificate_trust_detail VARCHAR(255);",
              "ALTER TABLE metricarchive ADD COLUMN IF NOT EXISTS certificate_trust VARCHAR(32);",
              "ALTER TABLE metricarchive ADD COLUMN IF NOT EXISTS certificate_trust_detail VARCHAR(255);"
            )
          statements.foreach { statement =>
            sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => statement)).append("\n")
          }

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
