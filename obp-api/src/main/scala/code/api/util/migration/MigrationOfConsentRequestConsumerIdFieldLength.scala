package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import net.liftweb.common.Full

object MigrationOfConsentRequestConsumerIdFieldLength {

  // The table is named here rather than through a Mapper singleton: consentrequest is owned by
  // Flyway now, and this historical script still has to run against databases created before that.
  private val tableName = "consentrequest"

  def alterColumnConsumerIdLength(name: String): Boolean = {
    DbFunction.tableExistsByName(tableName) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true) {
            APIUtil.getPropsValue("db.driver") match {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """
                    |ALTER TABLE consentrequest ALTER COLUMN consumerid varchar(250);
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |ALTER TABLE consentrequest ALTER COLUMN consumerid TYPE character varying(250);
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
          s"""$tableName table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
