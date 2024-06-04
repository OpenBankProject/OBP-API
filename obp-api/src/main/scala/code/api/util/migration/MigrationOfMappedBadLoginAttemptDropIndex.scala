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
