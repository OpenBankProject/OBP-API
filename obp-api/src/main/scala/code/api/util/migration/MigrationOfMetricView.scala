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
