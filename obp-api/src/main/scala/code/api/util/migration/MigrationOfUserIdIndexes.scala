/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.metrics.MappedMetric
import code.model.dataAccess.ResourceUser
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

import scala.collection.mutable.ListBuffer

object MigrationOfUserIdIndexes {

  /**
   * Give a new user id to each resource user that has none, and to each one whose user id is
   * also held by a resource user with a lower row id.
   *
   * The unique index on userid_ cannot be created over such rows: every database refuses a
   * repeated value, and SQL Server also refuses a second NULL. No row is removed, because other
   * tables point at a resource user by its row id. The user with the lowest row id keeps the
   * shared user id, so rows that point at that user id still find a user. Postgres and SQL Server
   * compare the values the way their unique index does, so the rows found here are the rows the
   * index would refuse.
   *
   * @return one line per changed row: its row id, its old user id and its new one
   */
  private[migration] def giveEveryResourceUserItsOwnUserId(): List[String] = {
    val table = ResourceUser._dbTableNameLC
    val idColumn = ResourceUser.id.dbColumnName
    val userIdColumn = ResourceUser.userId_.dbColumnName
    DB.use(DefaultConnectionIdentifier) { connection =>
      val select = connection.createStatement()
      val rowsToChange = try {
        val rs = select.executeQuery(
          s"""SELECT r.$idColumn, r.$userIdColumn FROM $table r
             |WHERE r.$userIdColumn IS NULL
             |   OR EXISTS (SELECT 1 FROM $table o WHERE o.$userIdColumn = r.$userIdColumn AND o.$idColumn < r.$idColumn)
             |ORDER BY r.$idColumn""".stripMargin)
        try {
          val rows = ListBuffer.empty[(Long, Option[String])]
          while (rs.next()) rows += (rs.getLong(1) -> Option(rs.getString(2)))
          rows.toList
        } finally rs.close()
      } finally select.close()

      val update = connection.prepareStatement(s"UPDATE $table SET $userIdColumn = ? WHERE $idColumn = ?")
      val changes = try {
        rowsToChange.map { case (rowId, oldUserId) =>
          val newUserId = APIUtil.generateUUID()
          update.setString(1, newUserId)
          update.setLong(2, rowId)
          update.executeUpdate()
          s"$idColumn $rowId: ${oldUserId.getOrElse("NULL")} -> $newUserId"
        }
      } finally update.close()
      if (!connection.getAutoCommit) connection.commit()
      changes
    }
  }

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

        // Rows the index would refuse get their own user id first, or the CREATE fails and the
        // boot that runs this migration stops.
        val changedUserIds = giveEveryResourceUserItsOwnUserId()

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
             |Resource users given a new user id first, because they had none or shared one: ${changedUserIds.size}
             |${changedUserIds.mkString("\n")}
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