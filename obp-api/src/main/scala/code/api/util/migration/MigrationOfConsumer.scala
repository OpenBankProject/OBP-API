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
import code.model.{AppType, Consumer}
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.{DefaultConnectionIdentifier, Helpers}

object MigrationOfConsumer {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def populateNamAndAppType(name: String): Boolean = {
    DbFunction.tableExists(Consumer) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val emptyNameConsumers = 
          for {
            consumer <- Consumer.findAll() if consumer.name.get.isEmpty()
          } yield {
            consumer
              .name(Helpers.randomString(10).toLowerCase())
              .saveMe()
          }

        val emptyAppTypeConsumers =
          for {
            consumer <- Consumer.findAll() if consumer.appType.get.isEmpty()
          } yield {
            consumer
              .appType(AppType.Confidential.toString())
              .saveMe()
          }
        
        val consumersAll = (emptyNameConsumers++emptyAppTypeConsumers).distinct
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Updated number of rows: 
             |${consumersAll.size}
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
  def populateAzpAndSub(name: String): Boolean = {
    DbFunction.tableExists(Consumer) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val emptyNameConsumers = 
          for {
            consumer <- Consumer.findAll() if consumer.azp.equals(null)
          } yield {
            consumer
              .azp(APIUtil.generateUUID())
              .saveMe()
          }

        val emptyAppTypeConsumers =
          for {
            consumer <- Consumer.findAll() if consumer.sub.equals(null)
          } yield {
            consumer
              .sub(APIUtil.generateUUID())
              .saveMe()
          }
        
        val consumersAll = (emptyNameConsumers++emptyAppTypeConsumers).distinct
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Updated number of rows: 
             |${consumersAll.size}
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


  def alterTypeofAud(name: String): Boolean = {
    DbFunction.tableExists(Consumer) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") match {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """
                    |ALTER TABLE consumer ALTER COLUMN aud VARCHAR(MAX) NULL;
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |ALTER TABLE consumer ALTER COLUMN aud TYPE text;
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
          s"""${Consumer._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }


}
