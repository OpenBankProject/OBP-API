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

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.Consumer
import code.model.dataAccess.ResourceUser
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfResourceUser {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def populateNewFieldIsDeleted(name: String): Boolean = {
    DbFunction.tableExists(ResourceUser) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        // Make back up
        DbFunction.makeBackUpOfTable(ResourceUser)

        val emptyDeletedField = 
          for {
            user <- ResourceUser.findAll() if user.isDeleted.getOrElse(false) == false
          } yield {
            user.IsDeleted(false).saveMe()
          }
        
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Updated number of rows: 
             |${emptyDeletedField.size}
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
          s"""${Consumer._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

  def alterColumnEmail(name: String): Boolean = {
    DbFunction.tableExists(ResourceUser) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false
        val targetLength = 100

        // Idempotent guard: only ALTER when the width actually differs. Re-issuing `ALTER ... TYPE` to
        // the SAME width is rejected by Postgres when a view references the column ("cannot alter type of
        // a column used by a view or rule") — which is what caused the recurring schema drift on re-runs
        // (e.g. test-isolation resets that wipe the migration log but keep the views). See the doc comment
        // at the top of Migration.scala.
        val executedSql =
          if (DbFunction.columnMaxLength(ResourceUser._dbTableNameLC, "email").contains(targetLength)) {
            s"-- skipped: resourceuser.email already varchar($targetLength)"
          } else {
            DbFunction.maybeWrite(true, Schemifier.infoF _) {
              APIUtil.getPropsValue("db.driver") match    {
                case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                  () =>
                    """ALTER TABLE resourceuser ALTER COLUMN email varchar(100);
                      |""".stripMargin
                case _ =>
                  () =>
                    """ALTER TABLE resourceuser ALTER COLUMN email type varchar(100);
                      |""".stripMargin
              }

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
          s"""${ResourceUser._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
  
}
