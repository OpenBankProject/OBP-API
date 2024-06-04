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
