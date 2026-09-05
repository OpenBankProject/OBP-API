package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import net.liftweb.common.Full

/**
 * One-time historical migration: drops a legacy unique index that predates the current
 * (userId, key, createdAt) one. Originally looked the table up via the Lift
 * MappedUserAuthContext entity, through the MetaMapper-typed DbFunction.tableExists overload
 * this migration used; both the entity and that overload are gone now (deleted, not just
 * unused) - the table is now created by Liquibase (the table is in db/changelog/db.changelog-baseline.yaml) - so this checks for the table by name
 * instead. Every environment that had already run this migration has it recorded in
 * migration_script_log and runOnce skips it; a fresh environment's Liquibase-created table never had
 * the legacy index in the first place, so the drop is a no-op there. Kept only so
 * migration_script_log stays a complete history.
 */
object MigrationOfMappedUserAuthContext {

  private val tableName = "mappeduserauthcontext"

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def dropUniqueIndex(name: String): Boolean = {
    DbFunction.tableExistsByName(tableName) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql = 
          DbFunction.maybeWrite(true) {
              APIUtil.getPropsValue("db.driver") match    {
                case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
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
          s"""$tableName table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
  
}
