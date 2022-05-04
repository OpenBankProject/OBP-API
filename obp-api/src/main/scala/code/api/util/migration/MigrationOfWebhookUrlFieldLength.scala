package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.webhook.MappedAccountWebhook
import code.webhook.BankAccountNotificationWebhook
import code.webhook.SystemAccountNotificationWebhook
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfWebhookUrlFieldLength {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def alterColumnUrlLength(name: String): Boolean = {
    DbFunction.tableExists(SystemAccountNotificationWebhook, (DB.use(DefaultConnectionIdentifier){ conn => conn})) &&
      DbFunction.tableExists(BankAccountNotificationWebhook, (DB.use(DefaultConnectionIdentifier){ conn => conn}))&&
      DbFunction.tableExists(MappedAccountWebhook, (DB.use(DefaultConnectionIdentifier){ conn => conn}))
    match {
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
          s"""${MappedAccountWebhook._dbTableNameLC} table does not exist or 
             |${BankAccountNotificationWebhook._dbTableNameLC} table does not exist or 
             |${SystemAccountNotificationWebhook._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}