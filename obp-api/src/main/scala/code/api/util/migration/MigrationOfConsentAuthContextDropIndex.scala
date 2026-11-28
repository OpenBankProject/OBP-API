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

/**
 * One-time historical migration: drops a legacy unique index that used to block legitimate
 * duplicate (consentId, key) rows in the consent-auth-context table.
 *
 * Originally looked the table up via the Lift MappedConsentAuthContext entity, through the
 * MetaMapper-typed DbFunction.tableExists overload that this migration used - both the entity
 * and that overload are gone now (deleted, not just unused) - the table is now
 * created by Liquibase (the table is in db/changelog/db.changelog-baseline.yaml) - so this checks for the
 * table by name instead. Every environment that had already run this migration has it recorded in
 * migration_script_log and runOnce skips it; a fresh environment's Liquibase-created table never had
 * the legacy index (consentauthcontext_consentid_key_c) in the first place, so dropIndexIfExists
 * is a no-op there. Kept only so migration_script_log stays a complete history.
 */
object MigrationOfConsentAuthContextDropIndex {

  private val tableName = "consentauthcontext"

  def dropUniqueIndex(name: String): Boolean = {
    val startDate = System.currentTimeMillis()
    val commitId: String = APIUtil.gitCommit
    if (DbFunction.tableExistsByName(tableName)) {
      val executedSql =
        DbFunction.maybeWrite(true) {
          val dbDriver = APIUtil.getPropsValue("db.driver", "org.h2.Driver")
          () =>
            code.util.Helper.dropIndexIfExists(dbDriver, tableName, "consentauthcontext_consentid_key_c")
        }
      val endDate = System.currentTimeMillis()
      val comment = s"Executed SQL: \n$executedSql\n"
      saveLog(name, commitId, isSuccessful = true, startDate, endDate, comment)
      true
    } else {
      val endDate = System.currentTimeMillis()
      saveLog(name, commitId, isSuccessful = false, startDate, endDate, s"$tableName table does not exist")
      false
    }
  }
}
