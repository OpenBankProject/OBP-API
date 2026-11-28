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

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.Constant
import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.util.Helper
import code.model.dataAccess.AuthUser
import net.liftweb.common.Full
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfAuthUser {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  /**
   * All five columns already as wide as this migration makes them, which they are on any table the changelog
   * built. Re-running the SQL Server arm there is not a no-op: it drops the unique index on (username, provider)
   * and recreates it as a plain one, so two users could then share a username at one provider.
   */
  private def alreadyWide: Boolean =
    List("username", "provider", "firstname", "lastname", "email")
      .forall(column => DbFunction.columnMaxLength("authuser", column).exists(_ >= 100))

  def alterColumnUsernameProviderEmailFirstnameAndLastname(name: String): Boolean = {
    DbFunction.tableExistsByName("authuser") match {
      case true if alreadyWide =>
        val now = System.currentTimeMillis()
        saveLog(name, APIUtil.gitCommit, true, now, now, "authuser columns are already 100 wide; nothing to do")
        true
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true) {
            APIUtil.getPropsValue("db.driver") match    {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  s"""
                    |${Helper.dropIndexIfExists(dbDriver,"authUser", "authuser_username_provider")}
                    |
                    |ALTER TABLE authuser ALTER COLUMN username nvarchar(100);
                    |ALTER TABLE authuser ALTER COLUMN provider nvarchar(100);
                    |ALTER TABLE authuser ALTER COLUMN firstname nvarchar(100);
                    |ALTER TABLE authuser ALTER COLUMN lastname nvarchar(100);
                    |ALTER TABLE authuser ALTER COLUMN email nvarchar(100);
                    |
                    |${Helper.createIndexIfNotExists(dbDriver,"authUser", "authuser_username_provider")}
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |ALTER TABLE authuser ALTER COLUMN username type varchar(100);
                    |ALTER TABLE authuser ALTER COLUMN provider type varchar(100);
                    |ALTER TABLE authuser ALTER COLUMN firstname type varchar(100);
                    |ALTER TABLE authuser ALTER COLUMN lastname type varchar(100);
                    |ALTER TABLE authuser ALTER COLUMN email type varchar(100);
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
          s"""${"authuser"} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

  def populateMissingProviderWithLocalIdentity(name: String): Boolean = {
    DbFunction.tableExistsByName("authuser") match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        // Make back up
        DbFunction.makeBackUpOfTableByName("authuser")

        val updatedRows =
          for {
            user <- AuthUser.findAll()
            providerValue = Option(user.provider).map(_.trim).getOrElse("") if providerValue.isEmpty
          } yield {
            AuthUser.update(user.copy(provider = Constant.localIdentityProvider))
          }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Updated number of rows: 
             |${updatedRows.size}
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
          s"""${"authuser"} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

  def dropIndexAtColumnUsername(name: String): Boolean = {
    DbFunction.tableExistsByName("authuser") match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true) {
            val dbDriver = APIUtil.getPropsValue("db.driver", "org.h2.Driver")
            () =>
              s"""${Helper.dropIndexIfExists(dbDriver, "authuser", "authuser_username")}"""
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
          s"""${"authuser"} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
  
}
