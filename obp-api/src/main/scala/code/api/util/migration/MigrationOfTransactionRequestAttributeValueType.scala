package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.transactionRequestAttribute.TransactionRequestAttribute
import net.liftweb.common.Full
import net.liftweb.mapper.Schemifier

object MigrationOfTransactionRequestAttributeValueType {

  def alterColumnValueType(name: String): Boolean = {
    DbFunction.tableExists(TransactionRequestAttribute) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") match {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """
                    |-- Open Corridor promise evidence (preimage JSON) exceeds varchar(255)
                    |ALTER TABLE transactionrequestattribute ALTER COLUMN value VARCHAR(MAX);
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |-- Open Corridor promise evidence (preimage JSON) exceeds varchar(255)
                    |ALTER TABLE transactionrequestattribute ALTER COLUMN value TYPE text;
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
          s"""${TransactionRequestAttribute._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
