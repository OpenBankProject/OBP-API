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
import code.counterpartylimit.CounterpartyLimit
import net.liftweb.common.Full
import net.liftweb.mapper.Schemifier

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

object MigrationOfCounterpartyLimitFieldType {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def alterCounterpartyLimitFieldType(name: String): Boolean = {
    DbFunction.tableExists(CounterpartyLimit)
    match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") match    {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """
                    |ALTER TABLE counterpartylimit
                    |ALTER COLUMN maxsingleamount numeric(16, 10);
                    |
                    |ALTER TABLE counterpartylimit
                    |ALTER COLUMN maxmonthlyamount numeric(16, 10);
                    |
                    |ALTER TABLE counterpartylimit
                    |ALTER COLUMN maxyearlyamount numeric(16, 10);
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |alter table counterpartylimit
                    |    alter column maxsingleamount type numeric(16, 10) using maxsingleamount::numeric(16, 10);
                    |alter table counterpartylimit		
                    |    alter column maxmonthlyamount type numeric(16, 10) using maxmonthlyamount::numeric(16, 10);
                    |alter table counterpartylimit		
                    |    alter column maxyearlyamount type numeric(16, 10) using maxyearlyamount::numeric(16, 10);
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
        val comment: String = s"""${CounterpartyLimit._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}