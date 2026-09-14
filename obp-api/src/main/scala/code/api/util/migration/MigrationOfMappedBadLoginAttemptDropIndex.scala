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

import code.api.util.{APIUtil, DBUtil}
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.loginattempts.MappedBadLoginAttempt
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.common.Full
import code.util.Helper
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

object MigrationOfMappedBadLoginAttemptDropIndex {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def dropUniqueIndex(name: String): Boolean = {
    DbFunction.tableExists(MappedBadLoginAttempt) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false
        
        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            val dbDriver = APIUtil.getPropsValue("db.driver", "org.h2.Driver")
            () =>
              s"""${Helper.dropIndexIfExists(dbDriver, "mappedbadloginattempt", "mappedbadloginattempt_musername")}""".stripMargin
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
          s"""${MappedBadLoginAttempt._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
