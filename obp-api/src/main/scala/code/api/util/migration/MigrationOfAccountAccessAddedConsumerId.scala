package code.api.util.migration

import code.api.Constant.ALL_CONSUMERS
import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.views.system.AccountAccess
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

object MigrationOfAccountAccessAddedConsumerId {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def addAccountAccessConsumerId(name: String): Boolean = {
    DbFunction.tableExists(AccountAccess, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _, DB.use(DefaultConnectionIdentifier){ conn => conn}) {
            APIUtil.getPropsValue("db.driver") match    {
              case Full(value) if value.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  s"""
                    |ALTER TABLE accountaccess ADD COLUMN IF NOT EXISTS "consumer_id" character varchar(255) DEFAULT '$ALL_CONSUMERS';
                    |DROP INDEX IF EXISTS accountaccess_bank_id_account_id_view_fk_user_fk;
                    |""".stripMargin
              case _ =>
                () =>
                  s"""
                    |ALTER TABLE accountaccess ADD COLUMN IF NOT EXISTS "consumer_id" character varying(255) DEFAULT '$ALL_CONSUMERS';
                    |DROP INDEX IF EXISTS accountaccess_bank_id_account_id_view_fk_user_fk;
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
          s"""${AccountAccess._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}