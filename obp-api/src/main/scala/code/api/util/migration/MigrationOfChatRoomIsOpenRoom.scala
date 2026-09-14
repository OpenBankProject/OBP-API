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
import code.chat.ChatRoom
import net.liftweb.common.Full
import net.liftweb.db.DB
import net.liftweb.mapper.Schemifier
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
    DbFunction.tableExists(ChatRoom) match {
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
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
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
          s"""${ChatRoom._dbTableNameLC} table does not exist"""
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
