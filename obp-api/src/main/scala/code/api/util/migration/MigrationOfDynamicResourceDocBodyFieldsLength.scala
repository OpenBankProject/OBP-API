package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.dynamicResourceDoc.DynamicResourceDoc
import net.liftweb.common.Full
import net.liftweb.mapper.Schemifier

object MigrationOfDynamicResourceDocBodyFieldsLength {

  def alterColumnsType(name: String): Boolean = {
    DbFunction.tableExists(DynamicResourceDoc) match {
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
                    |-- A realistic dynamic-endpoint request/response body example (or full error
                    |-- response list) routinely exceeds varchar(255) once it has more than a
                    |-- handful of JSON fields
                    |ALTER TABLE dynamicresourcedoc ALTER COLUMN examplerequestbody VARCHAR(MAX);
                    |ALTER TABLE dynamicresourcedoc ALTER COLUMN successresponsebody VARCHAR(MAX);
                    |ALTER TABLE dynamicresourcedoc ALTER COLUMN errorresponsebodies VARCHAR(MAX);
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |-- A realistic dynamic-endpoint request/response body example (or full error
                    |-- response list) routinely exceeds varchar(255) once it has more than a
                    |-- handful of JSON fields
                    |ALTER TABLE dynamicresourcedoc ALTER COLUMN examplerequestbody TYPE text;
                    |ALTER TABLE dynamicresourcedoc ALTER COLUMN successresponsebody TYPE text;
                    |ALTER TABLE dynamicresourcedoc ALTER COLUMN errorresponsebodies TYPE text;
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
          s"""${DynamicResourceDoc._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
