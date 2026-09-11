package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.entitlement.MappedEntitlement
import net.liftweb.common.Full

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

object MigrationOfRoleNameFieldLength {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def alterRoleNameLength(name: String): Boolean = {
    val entitlementTableExists = DbFunction.tableExistsByName("mappedentitlement")
    val entitlementRequestTableExists = DbFunction.tableExistsByName("mappedentitlementrequest")
    val scopeTableExists = DbFunction.tableExistsByName("mappedscope")

    if (!entitlementTableExists || !entitlementRequestTableExists || !scopeTableExists) {
      val startDate = System.currentTimeMillis()
      val commitId: String = APIUtil.gitCommit
      val isSuccessful = false
      val endDate = System.currentTimeMillis()
      val comment: String =
        s"""One or more required tables do not exist:
           |entitlement table exists: $entitlementTableExists
           |entitlementrequest table exists: $entitlementRequestTableExists
           |scope table exists: $scopeTableExists
           |""".stripMargin
      saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
      return isSuccessful
    }

    val startDate = System.currentTimeMillis()
    val commitId: String = APIUtil.gitCommit
    var isSuccessful = false

    val executedSql =
      DbFunction.maybeWrite(true) {
        APIUtil.getPropsValue("db.driver") match {
          case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
            () =>
              """
                |ALTER TABLE mappedentitlement ALTER COLUMN mrolename varchar(255);
                |ALTER TABLE mappedentitlementrequest ALTER COLUMN mrolename varchar(255);
                |ALTER TABLE mappedscope ALTER COLUMN mrolename varchar(255);
                |""".stripMargin
          case _ =>
            () =>
              """
                |ALTER TABLE mappedentitlement ALTER COLUMN mrolename TYPE varchar(255);
                |ALTER TABLE mappedentitlementrequest ALTER COLUMN mrolename TYPE varchar(255);
                |ALTER TABLE mappedscope ALTER COLUMN mrolename TYPE varchar(255);
                |""".stripMargin
        }
      }

    val endDate = System.currentTimeMillis()
    val comment: String =
      s"""Executed SQL: 
         |$executedSql
         |
         |Increased mrolename column length from 64 to 255 characters in three tables:
         |  - mappedentitlement
         |  - mappedentitlementrequest
         |  - mappedscope
         |
         |This allows for longer dynamic entity names and role names.
         |""".stripMargin
    isSuccessful = true
    saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
    isSuccessful
  }
}