package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.util.Helper
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

/**
 * One-time historical migration: drops a legacy unique index that constrained mUsername alone
 * and would have rejected the same username logging in under two different providers.
 * Originally looked the table up via the Lift MappedBadLoginAttempt entity; that entity is gone
 * - the table is now created by Liquibase (the table is in db/changelog/db.changelog-baseline.yaml) - so this checks for the table by name
 * instead. Kept only so migration_script_log stays a complete history; a fresh environment's
 * Liquibase-created table never had the legacy index in the first place.
 */
object MigrationOfMappedBadLoginAttemptDropIndex {

  private val tableName = "mappedbadloginattempt"

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
          s"""$tableName table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
