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
