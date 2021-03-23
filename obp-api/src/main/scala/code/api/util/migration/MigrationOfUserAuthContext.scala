package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.context.MappedUserAuthContext
import code.views.system.AccountAccess
import net.liftweb.mapper.{By, Descending, OrderBy}
import scalikejdbc.DB.CPContext
import scalikejdbc.{DB => scalikeDB, _}

import scala.collection.immutable.List

object MigrationOfUserAuthContext {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  private lazy val getDbConnectionParameters: (String, String, String) = {
    val dbUrl = APIUtil.getPropsValue("db.url") openOr "jdbc:h2:mem:OBPTest;DB_CLOSE_DELAY=-1"
    val username = dbUrl.split(";").filter(_.contains("user")).toList.headOption.map(_.split("=")(1))
    val password = dbUrl.split(";").filter(_.contains("password")).toList.headOption.map(_.split("=")(1))
    val dbUser = APIUtil.getPropsValue("db.user").orElse(username)
    val dbPassword = APIUtil.getPropsValue("db.password").orElse(password)
    (dbUrl, dbUser.getOrElse(""), dbPassword.getOrElse(""))
  }
  
  /**
   * this connection pool context corresponding db.url in default.props
   */
  implicit lazy val context: CPContext = {
    val settings = ConnectionPoolSettings(
      initialSize = 5,
      maxSize = 20,
      connectionTimeoutMillis = 3000L,
      validationQuery = "select 1",
      connectionPoolFactoryName = "commons-dbcp2"
    )
    val (dbUrl, user, password) = getDbConnectionParameters
    val dbName = "DB_NAME" // corresponding props db.url DB
    ConnectionPool.add(dbName, dbUrl, user, password, settings)
    val connectionPool = ConnectionPool.get(dbName)
    MultipleConnectionPoolContext(ConnectionPool.DEFAULT_NAME -> connectionPool)
  }
  
  def removeDuplicates(name: String): Boolean = {

    // Make back up
    DbFunction.makeBackUpOfTable(MappedUserAuthContext)

    val startDate = System.currentTimeMillis()
    val commitId: String = APIUtil.gitCommit

    case class SqlResult(
                          count: Int,
                          userId: String,
                          key: String
                        )

    val result: List[SqlResult] = scalikeDB autoCommit { implicit session =>

      val sqlResult =
        sql"""select count(mkey), muserid, mkey from mappeduserauthcontext group by muserid, mkey having count(mkey) > 1""".stripMargin
          .map(
            rs => // Map result to case class
              SqlResult(
                rs.string(1).toInt,
                rs.string(2),
                rs.string(3))
          ).list.apply()
      sqlResult
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
