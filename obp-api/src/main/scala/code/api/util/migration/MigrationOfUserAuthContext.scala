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

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import code.api.util.{APIUtil, DBUtil}
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.context.MappedUserAuthContext
import net.liftweb.mapper.{By,Descending, OrderBy}
import java.sql.ResultSet
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier
object MigrationOfUserAuthContext {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def removeDuplicates(name: String): Boolean = {

    // Make back up
    DbFunction.makeBackUpOfTable(MappedUserAuthContext)

    MappedUserAuthContext.findAll()

    val startDate = System.currentTimeMillis()
    val commitId: String = APIUtil.gitCommit

    case class SqlResult(
                          count: Int,
                          userId: String,
                          key: String
                        )

    val result = DB.use(DefaultConnectionIdentifier) { conn =>
      DB.exec(conn, "select count(mkey), muserid, mkey from mappeduserauthcontext group by muserid, mkey having count(mkey) > 1") {
        rs: ResultSet => {
          Iterator.from(0).takeWhile(_ => rs.next()).map(_ => SqlResult(
            rs.getInt(1),
            rs.getString(2),
            rs.getString(3)
          )).toList
        }
      }
    }
    val deleted: List[Boolean] = for (i <- result) yield {
      val duplicatedRows = MappedUserAuthContext.findAll(
        By(MappedUserAuthContext.mUserId, i.userId),
        By(MappedUserAuthContext.mKey, i.key),
        OrderBy(MappedUserAuthContext.updatedAt, Descending)
      )
      duplicatedRows match {
        case _ :: tail => tail.forall(_.delete_!) // Delete all elements except the head of the list
        case _ => true
      }
    }

    val isSuccessful = deleted.forall(_ == true)
    val endDate = System.currentTimeMillis()
    val comment: String =
      s"""Deleted all redundant rows in the table MappedUserAuthContext
         |""".stripMargin
    saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
    org.scalameta.logger.elem(comment)
    isSuccessful
  }
}
