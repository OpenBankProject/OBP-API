package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.metrics.MetricArchive
import net.liftweb.common.Full

/**
 * Widen `metricarchive.correlationid` from varchar(36) to varchar(256) so it matches
 * the live `metric` table (which was already widened by
 * [[MigrationOfMetricTable.alterColumnCorrelationidLength]]).
 *
 * Why this is needed: the correlation id is whatever the caller sends in `X-Request-ID`
 * (mandatory for Berlin Group / PSD2, optional elsewhere) — a free-form, client-controlled
 * string up to 256 chars, not a UUID. The archive column was modelled as a MappedUUID
 * (varchar 36), so the MetricsArchiveScheduler failed on the first row whose correlation id
 * exceeded 36 chars with `value too long for type character varying(36)`. Because the
 * archiver moves rows oldest-first, the same un-archivable rows were retried every run, so
 * no archive run ever succeeded. Lift's Schemifier never alters an existing column's width,
 * so this explicit migration is required on already-provisioned databases.
 */
object MigrationOfMetricArchiveTable {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def alterColumnCorrelationidLength(name: String): Boolean = {
    DbFunction.tableExistsByName("metricarchive")
    match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true) {
            APIUtil.getPropsValue("db.driver") match    {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """
                    |ALTER TABLE metricarchive ALTER COLUMN correlationid varchar(256);
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |ALTER TABLE metricarchive ALTER COLUMN correlationid TYPE character varying(256);
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
          s"""metricarchive table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
