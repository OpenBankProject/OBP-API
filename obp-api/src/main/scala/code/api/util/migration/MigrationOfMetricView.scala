package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.metrics.MappedMetric
import net.liftweb.mapper.Schemifier

object MigrationOfMetricView {

  def addMetricView(name: String): Boolean = {
    DbFunction.tableExists(MappedMetric) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") openOr("org.h2.Driver") match {
              case value if value.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """
                    |CREATE OR ALTER VIEW v_metric AS
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
              case _ =>
                () =>
                  """
                    |CREATE OR REPLACE VIEW v_metric AS
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
          s"""${MappedMetric._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
