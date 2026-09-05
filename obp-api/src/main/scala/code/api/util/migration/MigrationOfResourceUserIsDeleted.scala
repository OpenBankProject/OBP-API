package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.dataAccess.ResourceUser
import net.liftweb.common.Full
import net.liftweb.util.DefaultConnectionIdentifier

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

object MigrationOfResourceUserIsDeleted {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def populateNewFieldIsDeleted(name: String): Boolean = {
    DbFunction.tableExistsByName("resourceuser") match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        // Make back up
        DbFunction.makeBackUpOfTableByName("resourceuser")

        val emptyDeletedField = 
          for {
            user <- ResourceUser.findAll() if user.isDeleted.getOrElse(false) == false
          } yield {
            ResourceUser.update(user.copy(isDeleted = Some(false)))
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
          // Names the consumer table although the check above is on resourceuser - a copy-paste
          // in the original that only ever reached a log line. Preserved verbatim.
          s"""consumer table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

  def alterColumnEmail(name: String): Boolean = {
    DbFunction.tableExistsByName("resourceuser") match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true) {
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
          s"""${"resourceuser"} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
  
}
