package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.metrics.MappedMetric
import code.model.dataAccess.ResourceUser
import net.liftweb.common.Full
import net.liftweb.mapper.Schemifier

/**
 * Indexes for the self-service metrics reads behind the Activity dashboards.
 *
 * Declared on the models too (MappedMetric.dbIndexes / ResourceUser.dbIndexes) so fresh
 * deploys get them from Lift's Schemifier; existing databases get them here, under the
 * migration framework's control — the composite metric index can take a while to build on
 * a large table, which is exactly the kind of DDL ops should schedule, not boot should
 * spring on them.
 */
object MigrationOfActivityDashboardIndexes {

  /**
   * Composite index on Metric(userid, date_c).
   *
   * Serves /my/metrics (locks on user ids, filters/sorts/limits on date) and top-users
   * (groups by userid over a date range) in one pass: equality on user + range and order
   * on date. The single-column metric_userid_idx (MigrationOfUserIdIndexes) still needs a
   * per-user sort for these queries and becomes redundant once this exists.
   * Note: The table name is "Metric" (capital M) and the date column is "date_c".
   */
  def addCompositeIndexOnMetricUserIdDate(name: String): Boolean = {
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
                    |IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'metric_userid_date_idx' AND object_id = OBJECT_ID('Metric'))
                    |BEGIN
                    |    CREATE INDEX metric_userid_date_idx ON Metric(userid, date_c);
                    |END
                  """.stripMargin
              case Full(dbDriver) if dbDriver.contains("com.mysql.cj.jdbc.Driver") =>
                () =>
                  """
                    |-- MySQL: Create index (will fail silently if exists in some versions)
                    |CREATE INDEX metric_userid_date_idx ON Metric(userid, date_c);
                  """.stripMargin
              case _ => // Default (H2, PostgreSQL, etc.)
                () =>
                  """
                    |CREATE INDEX IF NOT EXISTS metric_userid_date_idx ON Metric(userid, date_c);
                  """.stripMargin
            }
          }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Added composite index on Metric(userid, date_c)
             |Executed SQL:
             |$executedSql
             |Serves /my/metrics (user ids + date range/order) and top-users (group by userid over a date range).
             |Note: Table name is "Metric" (capital M); the date column is "date_c".
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

  /**
   * Index on resourceuser.createdbyconsentid.
   *
   * The delegation registry: consent-agent fan-down (/my/metrics, /my/banks) and
   * CallContext.accountableUserId look up agent users by the consent that minted them.
   * Unindexed this is a full scan of resourceuser on every such request, which matters on
   * consent-heavy instances where every consent mints a user row.
   */
  def addIndexOnResourceUserCreatedByConsentId(name: String): Boolean = {
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
                    |IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'resourceuser_createdbyconsentid_idx' AND object_id = OBJECT_ID('resourceuser'))
                    |BEGIN
                    |    CREATE INDEX resourceuser_createdbyconsentid_idx ON resourceuser(createdbyconsentid);
                    |END
                  """.stripMargin
              case Full(dbDriver) if dbDriver.contains("com.mysql.cj.jdbc.Driver") =>
                () =>
                  """
                    |-- MySQL: Create index (will fail silently if exists in some versions)
                    |CREATE INDEX resourceuser_createdbyconsentid_idx ON resourceuser(createdbyconsentid);
                  """.stripMargin
              case _ => // Default (H2, PostgreSQL, etc.)
                () =>
                  """
                    |CREATE INDEX IF NOT EXISTS resourceuser_createdbyconsentid_idx ON resourceuser(createdbyconsentid);
                  """.stripMargin
            }
          }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Added index on resourceuser.createdbyconsentid
             |Executed SQL:
             |$executedSql
             |Serves the consent-agent delegation fan-down (/my/metrics, /my/banks, accountableUserId).
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
          s"""${ResourceUser._dbTableNameLC} table does not exist. Skipping index creation.""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
