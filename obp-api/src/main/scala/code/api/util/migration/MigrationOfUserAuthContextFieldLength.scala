package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.context.MappedUserAuthContext
import code.util.Helper
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfUserAuthContextFieldLength {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def alterColumnKeyAndValueLength(name: String): Boolean = {
    DbFunction.tableExists(MappedUserAuthContext) match {
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
                    |${Helper.dropIndexIfExists(dbDriver,"mappeduserauthcontext", "mappeduserauthcontext_muserid_mkey_createdat")}
                    |
                    |ALTER TABLE MappedUserAuthContext ALTER COLUMN mKey varchar(4000);
                    |ALTER TABLE MappedUserAuthContext ALTER COLUMN mValue varchar(4000);
                    |
                    |${Helper.createIndexIfNotExists(dbDriver, "mappeduserauthcontext", "mappeduserauthcontext_muserid_mkey_createdat")}
                    |
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |ALTER TABLE MappedUserAuthContext ALTER COLUMN mKey type varchar(4000);
                    |ALTER TABLE MappedUserAuthContext ALTER COLUMN mValue type varchar(4000);
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
          s"""${MappedUserAuthContext._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}