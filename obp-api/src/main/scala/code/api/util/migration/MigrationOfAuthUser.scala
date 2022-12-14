package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.dataAccess.AuthUser
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfAuthUser {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def alterColumnUsernameProviderEmailFirstnameAndLastname(name: String): Boolean = {
    DbFunction.tableExists(AuthUser, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _, DB.use(DefaultConnectionIdentifier){ conn => conn}) {
            APIUtil.getPropsValue("db.driver") match    {
              case Full(value) if value.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """
                    |ALTER TABLE authuser ALTER COLUMN username varchar(100);
                    |ALTER TABLE authuser ALTER COLUMN provider varchar(100);
                    |ALTER TABLE authuser ALTER COLUMN firstname varchar(100);
                    |ALTER TABLE authuser ALTER COLUMN lastname varchar(100);
                    |ALTER TABLE authuser ALTER COLUMN email varchar(100);
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

  def dropIndexAtColumnUsername(name: String): Boolean = {
    DbFunction.tableExists(AuthUser, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _, DB.use(DefaultConnectionIdentifier){ conn => conn}) {
            APIUtil.getPropsValue("db.driver") match {
              case _ =>
                () =>
                  """
                    |DROP INDEX IF EXISTS authuser_username;
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
  
}
