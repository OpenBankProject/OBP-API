package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import net.liftweb.common.Full
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfChatRoomIsOpenRoom {

  /**
   * Migrate the old `allusersareparticipants` column to the new `isopenroom` column.
   *
   * Schemifier will have already created the new `isopenroom` column (defaulting to false).
   * This migration copies data from the old column and then drops it.
   *
   * If the old column does not exist (fresh install), this is a no-op.
   */
  def migrateColumn(name: String): Boolean = {
    DbFunction.tableExistsByName("chatroom") match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit

        // Check if the old column exists before attempting migration
        val oldColumnExists = try {
          DB.use(DefaultConnectionIdentifier) { conn =>
            val rs = conn.getMetaData.getColumns(null, null, "chatroom", "allusersareparticipants")
            val exists = rs.next()
            rs.close()
            exists
          }
        } catch {
          case _: Throwable => false
        }

        if (!oldColumnExists) {
          val endDate = System.currentTimeMillis()
          val comment = "Old column allusersareparticipants does not exist (fresh install). No migration needed."
          saveLog(name, commitId, true, startDate, endDate, comment)
          return true
        }

        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true) {
            APIUtil.getPropsValue("db.driver") match {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """
                    |UPDATE chatroom SET isopenroom = allusersareparticipants WHERE allusersareparticipants IS NOT NULL;
                    |ALTER TABLE chatroom DROP COLUMN allusersareparticipants;
                    |""".stripMargin
              case _ =>
                // PostgreSQL and MySQL
                () =>
                  """
                    |UPDATE chatroom SET isopenroom = allusersareparticipants WHERE allusersareparticipants IS NOT NULL;
                    |ALTER TABLE chatroom DROP COLUMN allusersareparticipants;
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
          "chatroom table does not exist"
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
