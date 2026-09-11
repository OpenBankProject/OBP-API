package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.{APIUtil, DoobieUtil}
import code.api.util.migration.Migration.{DbFunction, saveLog}
import doobie._
import doobie.implicits._

object MigrationOfProductAttribute {

  private val tableName = "mappedproductattribute"

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def populateTheFieldIsActive(name: String): Boolean = {
    DbFunction.tableExistsByName(tableName) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        // Make back up
        DbFunction.makeBackUpOfTableByName(tableName)

        val emptyIds = DoobieUtil.runQuery(
          sql"SELECT id FROM mappedproductattribute WHERE isactive IS NULL".query[Long].to[List])
        emptyIds.foreach { id =>
          DoobieUtil.runUpdate(sql"UPDATE mappedproductattribute SET isactive = true WHERE id = $id".update.run)
        }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Updated number of rows:
             |${emptyIds.size}
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
          s"""$tableName table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
