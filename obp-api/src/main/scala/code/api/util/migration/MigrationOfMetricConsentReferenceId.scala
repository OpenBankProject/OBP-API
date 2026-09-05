package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.metrics.{MappedMetric, MetricArchive}

/**
 * Migration: add `consent_reference_id VARCHAR(36)` to both the live `Metric` table and
 * the `metricarchive` table, plus an index on each for efficient search-by-consent.
 *
 * Backs up the live `Metric` table only (not metricarchive — the archive is itself a
 * long-term backup of metrics, so duplicating it would be wasteful).
 *
 * No backfill: historical rows legitimately have no consent reference; nullable column.
 *
 * Lift's Schemifier auto-creates the column on fresh deploys from the updated model;
 * this migration handles existing deploys.
 */
object MigrationOfMetricConsentReferenceId {

  def migrate(name: String): Boolean = {
    DbFunction.tableExistsByName("metric") match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val dbDriver = APIUtil.getPropsValue("db.driver") openOr "org.h2.Driver"
        val isMssql = dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver")
        var isSuccessful = false
        val sqlLog = new StringBuilder()

        try {
          // 1. Backup of the live metric table (NOT the archive — it's already a long-term snapshot).
          //    Although MappedMetric.dbTableName is "Metric", Lift's Schemifier emits unquoted DDL,
          //    so Postgres folds the name to lowercase `metric`. Every other SQL site in the codebase
          //    (MetricBatchWriter INSERT, DoobieMetricsQueries, MigrationOfMetricView) references it
          //    as lowercase unquoted `metric` — mirror that here. MSSQL is case-insensitive by default.
          val backupMetric = if (isMssql) {
            "SELECT * INTO backup_2026_05_metric FROM metric;"
          } else {
            "CREATE TABLE backup_2026_05_metric AS SELECT * FROM metric;"
          }
          sqlLog.append(DbFunction.maybeWrite(true)(() => backupMetric)).append("\n")

          // 2. Add the new column to the live metric table.
          val addColumnMetric = if (isMssql) {
            "ALTER TABLE metric ADD consent_reference_id VARCHAR(36) NULL;"
          } else {
            "ALTER TABLE metric ADD COLUMN IF NOT EXISTS consent_reference_id VARCHAR(36);"
          }
          sqlLog.append(DbFunction.maybeWrite(true)(() => addColumnMetric)).append("\n")

          // 3. Add the new column to the archive table.
          val addColumnArchive = if (isMssql) {
            "ALTER TABLE metricarchive ADD consent_reference_id VARCHAR(36) NULL;"
          } else {
            "ALTER TABLE metricarchive ADD COLUMN IF NOT EXISTS consent_reference_id VARCHAR(36);"
          }
          sqlLog.append(DbFunction.maybeWrite(true)(() => addColumnArchive)).append("\n")

          // 4. Index for search-by-consent on both tables.
          val indexMetric = if (isMssql) {
            "CREATE INDEX idx_metric_consent_reference_id ON metric(consent_reference_id);"
          } else {
            "CREATE INDEX IF NOT EXISTS idx_metric_consent_reference_id ON metric(consent_reference_id);"
          }
          sqlLog.append(DbFunction.maybeWrite(true)(() => indexMetric)).append("\n")

          val indexArchive = if (isMssql) {
            "CREATE INDEX idx_metricarchive_consent_reference_id ON metricarchive(consent_reference_id);"
          } else {
            "CREATE INDEX IF NOT EXISTS idx_metricarchive_consent_reference_id ON metricarchive(consent_reference_id);"
          }
          sqlLog.append(DbFunction.maybeWrite(true)(() => indexArchive)).append("\n")

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
        val comment: String = s"""metric table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
