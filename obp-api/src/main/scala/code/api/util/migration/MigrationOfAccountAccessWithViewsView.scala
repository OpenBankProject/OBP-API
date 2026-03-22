package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.views.system.AccountAccess
import net.liftweb.mapper.Schemifier

object MigrationOfAccountAccessWithViewsView {

  def addAccountAccessWithViewsView(name: String): Boolean = {
    DbFunction.tableExists(AccountAccess) match {
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
                    |CREATE OR ALTER VIEW v_account_access_with_views AS
                    |SELECT
                    |    aa.id              AS account_access_id,
                    |    aa.bank_id         AS bank_id,
                    |    aa.account_id      AS account_id,
                    |    aa.view_id         AS view_id,
                    |    aa.consumer_id     AS consumer_id,
                    |    ru.userid_         AS user_id,
                    |    ru.name_           AS username,
                    |    ru.email           AS email,
                    |    ru.provider_       AS provider,
                    |    ru.id              AS resource_user_primary_key,
                    |    vd.name_           AS view_name,
                    |    vd.description_    AS view_description,
                    |    vd.metadataview_   AS metadata_view,
                    |    vd.issystem_       AS is_system,
                    |    vd.ispublic_       AS is_public,
                    |    vd.isfirehose_     AS is_firehose
                    |FROM accountaccess aa
                    |JOIN resourceuser ru ON ru.id = aa.user_fk
                    |JOIN viewdefinition vd ON vd.issystem_ = 1
                    |                      AND vd.view_id = aa.view_id
                    |UNION ALL
                    |SELECT
                    |    aa.id              AS account_access_id,
                    |    aa.bank_id         AS bank_id,
                    |    aa.account_id      AS account_id,
                    |    aa.view_id         AS view_id,
                    |    aa.consumer_id     AS consumer_id,
                    |    ru.userid_         AS user_id,
                    |    ru.name_           AS username,
                    |    ru.email           AS email,
                    |    ru.provider_       AS provider,
                    |    ru.id              AS resource_user_primary_key,
                    |    vd.name_           AS view_name,
                    |    vd.description_    AS view_description,
                    |    vd.metadataview_   AS metadata_view,
                    |    vd.issystem_       AS is_system,
                    |    vd.ispublic_       AS is_public,
                    |    vd.isfirehose_     AS is_firehose
                    |FROM accountaccess aa
                    |JOIN resourceuser ru ON ru.id = aa.user_fk
                    |JOIN viewdefinition vd ON vd.issystem_ = 0
                    |                      AND vd.bank_id = aa.bank_id
                    |                      AND vd.account_id = aa.account_id
                    |                      AND vd.view_id = aa.view_id;
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |CREATE OR REPLACE VIEW v_account_access_with_views AS
                    |SELECT
                    |    aa.id              AS account_access_id,
                    |    aa.bank_id         AS bank_id,
                    |    aa.account_id      AS account_id,
                    |    aa.view_id         AS view_id,
                    |    aa.consumer_id     AS consumer_id,
                    |    ru.userid_         AS user_id,
                    |    ru.name_           AS username,
                    |    ru.email           AS email,
                    |    ru.provider_       AS provider,
                    |    ru.id              AS resource_user_primary_key,
                    |    vd.name_           AS view_name,
                    |    vd.description_    AS view_description,
                    |    vd.metadataview_   AS metadata_view,
                    |    vd.issystem_       AS is_system,
                    |    vd.ispublic_       AS is_public,
                    |    vd.isfirehose_     AS is_firehose
                    |FROM accountaccess aa
                    |JOIN resourceuser ru ON ru.id = aa.user_fk
                    |JOIN viewdefinition vd ON vd.issystem_ = true
                    |                      AND vd.view_id = aa.view_id
                    |UNION ALL
                    |SELECT
                    |    aa.id              AS account_access_id,
                    |    aa.bank_id         AS bank_id,
                    |    aa.account_id      AS account_id,
                    |    aa.view_id         AS view_id,
                    |    aa.consumer_id     AS consumer_id,
                    |    ru.userid_         AS user_id,
                    |    ru.name_           AS username,
                    |    ru.email           AS email,
                    |    ru.provider_       AS provider,
                    |    ru.id              AS resource_user_primary_key,
                    |    vd.name_           AS view_name,
                    |    vd.description_    AS view_description,
                    |    vd.metadataview_   AS metadata_view,
                    |    vd.issystem_       AS is_system,
                    |    vd.ispublic_       AS is_public,
                    |    vd.isfirehose_     AS is_firehose
                    |FROM accountaccess aa
                    |JOIN resourceuser ru ON ru.id = aa.user_fk
                    |JOIN viewdefinition vd ON vd.issystem_ = false
                    |                      AND vd.bank_id = aa.bank_id
                    |                      AND vd.account_id = aa.account_id
                    |                      AND vd.view_id = aa.view_id;
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
          s"""${AccountAccess._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
