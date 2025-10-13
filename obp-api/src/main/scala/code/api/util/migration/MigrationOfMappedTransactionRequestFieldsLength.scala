package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.transactionrequests.MappedTransactionRequest
import net.liftweb.common.Full
import net.liftweb.mapper.Schemifier

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

object MigrationOfMappedTransactionRequestFieldsLength {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def alterMappedTransactionRequestFieldsLength(name: String): Boolean = {
    DbFunction.tableExists(MappedTransactionRequest) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") match {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  s"""
                    |-- Currency fields: support longer currency names (e.g., "lovelace")
                    |ALTER TABLE MappedTransactionRequest ALTER COLUMN mCharge_Currency varchar(16);
                    |ALTER TABLE MappedTransactionRequest ALTER COLUMN mBody_Value_Currency varchar(16);
                    |
                    |-- Account routing fields: support Cardano addresses (108 characters)
                    |ALTER TABLE MappedTransactionRequest ALTER COLUMN mTo_AccountId varchar(128);
                    |ALTER TABLE MappedTransactionRequest ALTER COLUMN mOtherAccountRoutingAddress varchar(128);
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |-- Currency fields: support longer currency names (e.g., "lovelace")
                    |ALTER TABLE MappedTransactionRequest ALTER COLUMN mCharge_Currency TYPE varchar(16);
                    |ALTER TABLE MappedTransactionRequest ALTER COLUMN mBody_Value_Currency TYPE varchar(16);
                    |
                    |-- Account routing fields: support Cardano addresses (108 characters)
                    |ALTER TABLE MappedTransactionRequest ALTER COLUMN mTo_AccountId TYPE varchar(128);
                    |ALTER TABLE MappedTransactionRequest ALTER COLUMN mOtherAccountRoutingAddress TYPE varchar(128);
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
          s"""${MappedTransactionRequest._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}

