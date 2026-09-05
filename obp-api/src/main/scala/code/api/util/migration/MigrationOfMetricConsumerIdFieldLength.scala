package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.metrics.{MappedMetric, MetricArchive}
import net.liftweb.common.Full

/**
 * Widen `metric.consumerid` and `metricarchive.consumerid` from varchar(44) to varchar(250)
 * so they match `Consumer.consumerId` (MappedString 250).
 *
 * Why this is needed: OAuth2/OIDC auto-created consumers get a composed consumer id of the
 * form `${azp}_${uuid}` (see OAuth.scala getOrCreateConsumer). For public providers the azp
 * is the full OAuth2 client id — a Google client id is ~72 chars — so the consumer id is
 * ~108 chars. The metric tables modelled consumerid as UUIDString (varchar 44), so
 * MetricBatchWriter.flush aborted the whole batch with `value too long for type character
 * varying(44)` and every metric in that flush was silently dropped. Lift's Schemifier never
 * alters an existing column's width, so this explicit migration is required on
 * already-provisioned databases. (Same class of bug as
 * [[MigrationOfMetricArchiveTable.alterColumnCorrelationidLength]].)
 *
 * The live `metric` table is referenced by the `v_metric` view, and Postgres refuses to ALTER
 * the type of a view-referenced column, so the view is dropped and recreated around the ALTER.
 * The recreated DDL must stay in sync with [[MigrationOfMetricView]].
 */
object MigrationOfMetricConsumerIdFieldLength {

  def alterColumnConsumerIdLength(name: String): Boolean = {
    DbFunction.tableExistsByName("metric") match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSqlServer = APIUtil.getPropsValue("db.driver") match {
          case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") => true
          case _ => false
        }

        // 1. Drop the dependent view (Postgres/H2 block ALTER TYPE on a view-referenced column).
        val dropViewSql = DbFunction.maybeWrite(true) { () =>
          "DROP VIEW IF EXISTS v_metric;"
        }

        // 2. Widen metric.consumerid to match Consumer.consumerId (250).
        val alterMetricSql = DbFunction.maybeWrite(true) { () =>
          if (isSqlServer) "ALTER TABLE metric ALTER COLUMN consumerid varchar(250);"
          else "ALTER TABLE metric ALTER COLUMN consumerid TYPE character varying(250);"
        }

        // 3. Widen metricarchive.consumerid too — the archiver copies metric rows verbatim,
        //    so a 250-char id in metric would later fail the archive insert. No view depends
        //    on metricarchive, so this is a plain ALTER.
        val alterArchiveSql =
          if (DbFunction.tableExistsByName("metricarchive")) {
            DbFunction.maybeWrite(true) { () =>
              if (isSqlServer) "ALTER TABLE metricarchive ALTER COLUMN consumerid varchar(250);"
              else "ALTER TABLE metricarchive ALTER COLUMN consumerid TYPE character varying(250);"
            }
          } else {
            s"metricarchive table does not exist; skipped"
          }

        // 4. Recreate v_metric (keep in sync with MigrationOfMetricView.addMetricView).
        val createViewSql = DbFunction.maybeWrite(true) { () =>
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
             |$alterArchiveSql
             |$createViewSql
             |""".stripMargin
        saveLog(name, commitId, isSuccessful = true, startDate, endDate, comment)
        true

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val endDate = System.currentTimeMillis()
        val comment: String = s"""metric table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful = false, startDate, endDate, comment)
        false
    }
  }
}
