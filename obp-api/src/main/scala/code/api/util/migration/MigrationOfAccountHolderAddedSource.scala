package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.accountholders.MapperAccountHolders
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfAccountHolderAddedSource {


  def addMapperAccountHoldersSource(name: String): Boolean = {
    DbFunction.tableExists(MapperAccountHolders, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
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
                    |ALTER TABLE mapperaccountholders ADD COLUMN IF NOT EXISTS "source" character varchar(255);
                    |""".stripMargin
              case _ =>
                () =>
                  s"""
                    |ALTER TABLE mapperaccountholders ADD COLUMN IF NOT EXISTS "source" character varying(255);
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
          s"""${MapperAccountHolders._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}