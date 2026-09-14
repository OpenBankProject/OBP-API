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
import net.liftweb.mapper.Schemifier

/**
 * Forward migration that removes the fast-firehose SQL objects created by
 * [[MigrationOfFastFireHoseView]] / [[MigrationOfFastFireHoseMaterializedView]].
 *
 * The firehose approach is being retired in favour of the account directory + ABAC, and neither
 * `v_fast_firehose_accounts` nor `mv_fast_firehose_accounts` is read by any application code. They are
 * also a recurring schema-drift hazard (a view pinning a column blocks later ALTERs).
 *
 * `runOnce` (see [[Migration]]) means this executes exactly once per database. It is registered AFTER
 * the create migrations in the run sequence, so on a fresh database the views are created and then
 * dropped in the same boot; on an existing database the creates are already logged (skipped) and this
 * simply drops them. `DROP ... IF EXISTS` makes it a safe no-op when they were never created.
 *
 * Postgres-only: the create migrations only ever produced working SQL on Postgres (the view body uses
 * `string_agg`, and `MATERIALIZED VIEW` is Postgres syntax), so there is nothing to drop on H2 / MS SQL.
 */
object MigrationOfDropFastFireHoseViews {

  def dropFastFireHoseViews(name: String): Boolean = {
    val startDate = System.currentTimeMillis()
    val commitId: String = APIUtil.gitCommit
    var isSuccessful = false

    val executedSql =
      DbFunction.maybeWrite(true, Schemifier.infoF _) {
        APIUtil.getPropsValue("db.driver") openOr ("org.h2.Driver") match {
          case value if value.contains("postgresql") =>
            () =>
              """
                |DROP MATERIALIZED VIEW IF EXISTS mv_fast_firehose_accounts CASCADE;
                |DROP VIEW IF EXISTS v_fast_firehose_accounts CASCADE;
                |""".stripMargin
          case _ =>
            () => "" // firehose views were only ever created on Postgres; nothing to drop elsewhere.
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
  }

}
