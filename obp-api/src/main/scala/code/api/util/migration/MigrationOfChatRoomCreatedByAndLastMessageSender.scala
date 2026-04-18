package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.chat.ChatRoom
import net.liftweb.common.Full
import net.liftweb.db.DB
import net.liftweb.mapper.Schemifier
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfChatRoomCreatedByAndLastMessageSender {

  /**
   * Migrate the old ambiguously-named columns to their renamed, explicit counterparts:
   *   createdby          -> createdbyuserid             (already was a user_id; name made explicit)
   *   lastmessagesender  -> lastmessagesenderusername   (always stored a username; name made explicit)
   *
   * Schemifier will have already created the new columns (defaulting to empty).
   * This migration copies data from the old columns and then drops them.
   *
   * If an old column does not exist (fresh install), that half is skipped.
   */
  def migrateColumns(name: String): Boolean = {
    DbFunction.tableExists(ChatRoom) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit

        val oldCreatedByExists = columnExists("createdby")
        val oldLastMessageSenderExists = columnExists("lastmessagesender")

        if (!oldCreatedByExists && !oldLastMessageSenderExists) {
          val endDate = System.currentTimeMillis()
          val comment = "Old columns createdby and lastmessagesender do not exist (fresh install). No migration needed."
          saveLog(name, commitId, true, startDate, endDate, comment)
          return true
        }

        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") match {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () => buildSql(oldCreatedByExists, oldLastMessageSenderExists)
              case _ =>
                // PostgreSQL and MySQL
                () => buildSql(oldCreatedByExists, oldLastMessageSenderExists)
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
          s"""${ChatRoom._dbTableNameLC} table does not exist"""
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

  private def columnExists(columnName: String): Boolean = {
    try {
      DB.use(DefaultConnectionIdentifier) { conn =>
        val rs = conn.getMetaData.getColumns(null, null, "chatroom", columnName)
        val exists = rs.next()
        rs.close()
        exists
      }
    } catch {
      case _: Throwable => false
    }
  }

  private def buildSql(migrateCreatedBy: Boolean, migrateLastMessageSender: Boolean): String = {
    val createdByPart =
      if (migrateCreatedBy)
        """UPDATE chatroom SET createdbyuserid = createdby WHERE createdby IS NOT NULL;
          |ALTER TABLE chatroom DROP COLUMN createdby;
          |""".stripMargin
      else ""
    val lastMessageSenderPart =
      if (migrateLastMessageSender)
        """UPDATE chatroom SET lastmessagesenderusername = lastmessagesender WHERE lastmessagesender IS NOT NULL;
          |ALTER TABLE chatroom DROP COLUMN lastmessagesender;
          |""".stripMargin
      else ""
    createdByPart + lastMessageSenderPart
  }
}
