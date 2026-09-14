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
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfAuthUser {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def alterColumnUsernameProviderEmailFirstnameAndLastname(name: String): Boolean = {
    DbFunction.tableExists(AuthUser) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") match    {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  s"""
                    |${Helper.dropIndexIfExists(dbDriver,"authUser", "authuser_username_provider")}
                    |
                    |ALTER TABLE authuser ALTER COLUMN username varchar(100);
                    |ALTER TABLE authuser ALTER COLUMN provider varchar(100);
                    |ALTER TABLE authuser ALTER COLUMN firstname varchar(100);
                    |ALTER TABLE authuser ALTER COLUMN lastname varchar(100);
                    |ALTER TABLE authuser ALTER COLUMN email varchar(100);
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
          s"""${AuthUser._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

  def populateMissingProviderWithLocalIdentity(name: String): Boolean = {
    DbFunction.tableExists(AuthUser) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        // Make back up
        DbFunction.makeBackUpOfTable(AuthUser)

        val updatedRows =
          for {
            user <- AuthUser.findAll()
            providerValue = Option(user.provider.get).map(_.trim).getOrElse("") if providerValue.isEmpty
          } yield {
            user.provider(Constant.localIdentityProvider).saveMe()
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
          s"""${AuthUser._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

  def dropIndexAtColumnUsername(name: String): Boolean = {
    DbFunction.tableExists(AuthUser) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
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
          s"""${AuthUser._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
  
}
