package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.context.MappedUserAuthContext
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfMappedUserAuthContext {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def dropUniqueIndex(name: String): Boolean = {
    DbFunction.tableExists(MappedUserAuthContext, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql = 
          DbFunction.maybeWrite(true, Schemifier.infoF _, DB.use(DefaultConnectionIdentifier){ conn => conn}) {
              APIUtil.getPropsValue("db.driver") match    {
                case Full(value) if value.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                  () => "DROP INDEX IF EXISTS mappeduserauthcontext_muserid_mkey ON mappeduserauthcontext;"
                case _ =>
                  () => "DROP INDEX IF EXISTS mappeduserauthcontext_muserid_mkey;"
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
