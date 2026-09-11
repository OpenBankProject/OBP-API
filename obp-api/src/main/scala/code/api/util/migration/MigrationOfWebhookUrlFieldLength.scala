package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import net.liftweb.common.Full
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfWebhookUrlFieldLength {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def alterColumnUrlLength(name: String): Boolean = {
    DbFunction.tableExistsByName("systemaccountnotificationwebhook") &&
      DbFunction.tableExistsByName("bankaccountnotificationwebhook") &&
      DbFunction.tableExistsByName("mappedaccountwebhook")
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
                    |ALTER TABLE SystemAccountNotificationWebhook ALTER COLUMN Url varchar(1024);
                    |ALTER TABLE BankAccountNotificationWebhook ALTER COLUMN Url varchar(1024);
                    |ALTER TABLE MappedAccountWebhook ALTER COLUMN mUrl varchar(1024);
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |ALTER TABLE SystemAccountNotificationWebhook ALTER COLUMN Url TYPE character varying(1024);
                    |ALTER TABLE BankAccountNotificationWebhook ALTER COLUMN Url TYPE character varying(1024);
                    |ALTER TABLE MappedAccountWebhook ALTER COLUMN mUrl TYPE character varying(1024);
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
          """mappedaccountwebhook table does not exist or
             |bankaccountnotificationwebhook table does not exist or
             |systemaccountnotificationwebhook table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}