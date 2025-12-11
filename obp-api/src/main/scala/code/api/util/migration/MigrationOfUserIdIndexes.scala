package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.metrics.MappedMetric
import code.model.dataAccess.ResourceUser
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}

object MigrationOfUserIdIndexes {

  /**
   * Creates a UNIQUE index on resourceuser.userid_ field
   * This ensures that user_id is actually unique at the database level
   */
  def addUniqueIndexOnResourceUserUserId(name: String): Boolean = {
    DbFunction.tableExists(ResourceUser) match {
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
                    |-- Check if index exists, if not create it
                    |IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'resourceuser_userid_unique' AND object_id = OBJECT_ID('resourceuser'))
                    |BEGIN
                    |    CREATE UNIQUE INDEX resourceuser_userid_unique ON resourceuser(userid_);
                    |END
                  """.stripMargin
              case Full(dbDriver) if dbDriver.contains("org.postgresql.Driver") =>
                () =>
                  """
                    |-- PostgreSQL: Create unique index if not exists
                    |CREATE UNIQUE INDEX IF NOT EXISTS resourceuser_userid_unique ON resourceuser(userid_);
                  """.stripMargin
              case Full(dbDriver) if dbDriver.contains("com.mysql.cj.jdbc.Driver") =>
                () =>
                  """
                    |-- MySQL: Create unique index (will fail silently if exists in some versions)
                    |CREATE UNIQUE INDEX resourceuser_userid_unique ON resourceuser(userid_);
                  """.stripMargin
              case _ => // Default (H2, PostgreSQL, etc.)
                () =>
                  """
                    |CREATE UNIQUE INDEX IF NOT EXISTS resourceuser_userid_unique ON resourceuser(userid_);
                  """.stripMargin
            }
          }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Added UNIQUE index on resourceuser.userid_ field
             |Executed SQL:
             |$executedSql
             |This ensures user_id is enforced as unique at the database level.
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
          s"""${ResourceUser._dbTableNameLC} table does not exist. Skipping unique index creation.""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

  /**
   * Creates a regular index on Metric.userid field
   * This improves performance when querying metrics by user_id (for last activity date, etc.)
   * Note: The table name is "Metric" (capital M), not "mappedmetric"
   */
  def addIndexOnMappedMetricUserId(name: String): Boolean = {
    DbFunction.tableExists(MappedMetric) match {
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
                    |-- Check if index exists, if not create it
                    |IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'metric_userid_idx' AND object_id = OBJECT_ID('Metric'))
                    |BEGIN
                    |    CREATE INDEX metric_userid_idx ON Metric(userid);
                    |END
                  """.stripMargin
              case Full(dbDriver) if dbDriver.contains("org.postgresql.Driver") =>
                () =>
                  """
                    |-- PostgreSQL: Create index if not exists
                    |CREATE INDEX IF NOT EXISTS metric_userid_idx ON Metric(userid);
                  """.stripMargin
              case Full(dbDriver) if dbDriver.contains("com.mysql.cj.jdbc.Driver") =>
                () =>
                  """
                    |-- MySQL: Create index (will fail silently if exists in some versions)
                    |CREATE INDEX metric_userid_idx ON Metric(userid);
                  """.stripMargin
              case _ => // Default (H2, PostgreSQL, etc.)
                () =>
                  """
                    |CREATE INDEX IF NOT EXISTS metric_userid_idx ON Metric(userid);
                  """.stripMargin
            }
          }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Added index on Metric.userid field
             |Executed SQL:
             |$executedSql
             |This improves performance when querying metrics by user_id for features like last_activity_date.
             |Note: Table name is "Metric" (capital M), not "mappedmetric".
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
          s"""${MappedMetric._dbTableNameLC} table does not exist. Skipping index creation.""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}