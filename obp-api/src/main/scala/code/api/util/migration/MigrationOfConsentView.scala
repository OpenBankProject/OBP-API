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
import code.consent.MappedConsent
import net.liftweb.mapper.Schemifier

object MigrationOfConsentView {

  def addConsentView(name: String): Boolean = {
    DbFunction.tableExists(MappedConsent) match {
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
                    |CREATE OR ALTER VIEW v_consent AS
                    |SELECT
                    |    consent_reference_id              AS consent_reference_id,
                    |    mconsentid                        AS consent_id,
                    |    muserid                            AS created_by_user_id,
                    |    mconsumerid                        AS consumer_id,
                    |    mstatus                            AS status,
                    |    mjsonwebtoken                      AS jwt,
                    |    mconsentrequestid                  AS consent_request_id,
                    |    mapistandard                       AS api_standard,
                    |    mapiversion                        AS api_version,
                    |    mlastactiondate                    AS last_action_date,
                    |    musessofartodaycounterupdatedat    AS last_usage_date,
                    |    createdat                          AS created_date,
                    |    mnote                              AS note,
                    |    mfrequencyperday                   AS frequency_per_day,
                    |    musessofartodaycounter             AS uses_so_far_today_counter,
                    |    mjsonwebtokenpayload               AS jwt_payload,
                    |    jwt_expires_at                     AS jwt_expires_at
                    |FROM mappedconsent;
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |CREATE OR REPLACE VIEW v_consent AS
                    |SELECT
                    |    consent_reference_id              AS consent_reference_id,
                    |    mconsentid                        AS consent_id,
                    |    muserid                            AS created_by_user_id,
                    |    mconsumerid                        AS consumer_id,
                    |    mstatus                            AS status,
                    |    mjsonwebtoken                      AS jwt,
                    |    mconsentrequestid                  AS consent_request_id,
                    |    mapistandard                       AS api_standard,
                    |    mapiversion                        AS api_version,
                    |    mlastactiondate                    AS last_action_date,
                    |    musessofartodaycounterupdatedat    AS last_usage_date,
                    |    createdat                          AS created_date,
                    |    mnote                              AS note,
                    |    mfrequencyperday                   AS frequency_per_day,
                    |    musessofartodaycounter             AS uses_so_far_today_counter,
                    |    mjsonwebtokenpayload               AS jwt_payload,
                    |    jwt_expires_at                     AS jwt_expires_at
                    |FROM mappedconsent;
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
          s"""${MappedConsent._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
