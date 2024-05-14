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
    DbFunction.tableExists(AccountAccess) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") match    {
              case Full(value) if value.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  s"""
                    |-- Check if column 'consumer_id' already exists
                    |IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'accountaccess' AND COLUMN_NAME = 'consumer_id')
                    |BEGIN
                    |    ALTER TABLE accountaccess ADD consumer_id VARCHAR(255) DEFAULT '$ALL_CONSUMERS';
                    |END
                    |
                    |-- Drop index if it exists
                    |IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'accountaccess_bank_id_account_id_view_fk_user_fk' AND object_id = OBJECT_ID('accountaccess'))
                    |BEGIN
                    |    DROP INDEX accountaccess.accountaccess_bank_id_account_id_view_fk_user_fk;
                    |END
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