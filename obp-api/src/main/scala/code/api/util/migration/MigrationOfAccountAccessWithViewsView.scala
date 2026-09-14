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
