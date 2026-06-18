package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.metrics.MappedMetric
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfMetricTable {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def alterColumnCorrelationidLength(name: String): Boolean = {
    DbFunction.tableExists(MappedMetric)
    match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false
        val isSqlServer = APIUtil.getPropsValue("db.driver") match {
          case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") => true
          case _ => false
        }

        // 1. Drop the dependent view: Postgres/H2 refuse ALTER TYPE on a column referenced by a view
        //    (`v_metric` selects `correlationid`). The view may or may not exist when this migration runs
        //    — the migration log and the physical schema can diverge (e.g. a re-provisioned test DB), and
        //    this runs before `addMetricView` — so `DROP ... IF EXISTS` keeps it safe in every ordering.
        //    (Same dance as MigrationOfMetricConsumerIdFieldLength.)
        val dropViewSql = DbFunction.maybeWrite(true, Schemifier.infoF _) { () =>
          "DROP VIEW IF EXISTS v_metric;"
        }

        // 2. Widen metric.correlationid to 256.
        val alterMetricSql = DbFunction.maybeWrite(true, Schemifier.infoF _) { () =>
          if (isSqlServer) "ALTER TABLE metric ALTER COLUMN correlationid varchar(256);"
          else "ALTER TABLE metric ALTER COLUMN correlationid TYPE character varying(256);"
        }

        // 3. Recreate v_metric (keep in sync with MigrationOfMetricView.addMetricView).
        val createViewSql = DbFunction.maybeWrite(true, Schemifier.infoF _) { () =>
          val createClause = if (isSqlServer) "CREATE OR ALTER VIEW v_metric AS" else "CREATE OR REPLACE VIEW v_metric AS"
          s"""$createClause
             |SELECT
             |    id                           AS metric_id,
             |    userid                       AS user_id,
             |    url                          AS url,
             |    date_c                       AS date,
             |    duration                     AS duration,
             |    username                     AS username,
             |    appname                      AS app_name,
             |    developeremail               AS developer_email,
             |    consumerid                   AS consumer_id,
             |    implementedbypartialfunction AS implemented_by_partial_function,
             |    implementedinversion         AS implemented_in_version,
             |    verb                         AS verb,
             |    httpcode                     AS http_code,
             |    correlationid                AS correlation_id,
             |    responsebody                 AS response_body,
             |    sourceip                     AS source_ip,
             |    targetip                     AS target_ip
             |FROM metric;
             |""".stripMargin
        }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Executed SQL:
             |$dropViewSql
             |$alterMetricSql
             |$createViewSql
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
          s"""${MappedMetric._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}