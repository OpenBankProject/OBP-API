package code.api.util.migration

import code.api.util.{APIUtil, DBUtil}
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.context.MappedConsentAuthContext
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier
import scalikejdbc.DB.CPContext
import scalikejdbc._
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.Constant

object MigrationOfConsentAuthContextDropIndex {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
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
    val (dbUrl, user, password) = DBUtil.getDbConnectionParameters
    val dbName = "DB_NAME" // corresponding props db.url DB
    ConnectionPool.add(dbName, dbUrl, user, password, settings)
    val connectionPool = ConnectionPool.get(dbName)
    MultipleConnectionPoolContext(ConnectionPool.DEFAULT_NAME -> connectionPool)
  }
  
  def dropUniqueIndex(name: String): Boolean = {
    DbFunction.tableExists(MappedConsentAuthContext, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false
        
        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _, DB.use(DefaultConnectionIdentifier){ conn => conn}) {
            APIUtil.getPropsValue("db.driver") match    {
              case _ =>
                () => "DROP INDEX IF EXISTS consentauthcontext_consentid_key_c;"
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
          s"""${MappedConsentAuthContext._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
