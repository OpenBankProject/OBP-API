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
