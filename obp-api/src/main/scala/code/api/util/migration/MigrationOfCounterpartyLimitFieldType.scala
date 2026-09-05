package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import net.liftweb.common.Full

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

object MigrationOfCounterpartyLimitFieldType {

  private val tableName = "counterpartylimit"

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def alterCounterpartyLimitFieldType(name: String): Boolean = {
    DbFunction.tableExistsByName(tableName)
    match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true) {
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
        val comment: String = s"""$tableName table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}