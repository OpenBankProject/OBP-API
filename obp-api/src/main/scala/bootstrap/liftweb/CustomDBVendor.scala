package bootstrap.liftweb

import code.api.util.APIUtil
import com.zaxxer.hikari.pool.ProxyConnection
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import java.sql.{Connection}
import net.liftweb.common.{Box, Failure, Full, Logger}
import net.liftweb.db.ConnectionManager
import net.liftweb.util.ConnectionIdentifier
import net.liftweb.util.Helpers.tryo

/**
 * The standard DB vendor.
 *
 * @param driverName the name of the database driver
 * @param dbUrl the URL for the JDBC data connection
 * @param dbUser the optional username
 * @param dbPassword the optional db password
 */
class CustomDBVendor(driverName: String,
                     dbUrl: String,
                     dbUser: Box[String],
                     dbPassword: Box[String]) extends CustomProtoDBVendor {

  private val logger = Logger(classOf[CustomDBVendor])

  object HikariDatasource {
    val config = new HikariConfig()

    (dbUser, dbPassword) match {
      case (Full(user), Full(pwd)) =>
        config.setJdbcUrl(dbUrl)
        config.setUsername(user)
        config.setPassword(pwd)
      case _ =>
        config.setJdbcUrl(dbUrl)
    }
    
//    val dbMaxPoolSize = APIUtil.getPropsAsIntValue("db.maxPoolSize", config.getMaximumPoolSize)
//    config.setMaximumPoolSize(dbMaxPoolSize)
    config.addDataSourceProperty("cachePrepStmts", "true")
    config.addDataSourceProperty("prepStmtCacheSize", "250")
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

    val ds: HikariDataSource = new HikariDataSource(config)
  }

  def createOne: Box[Connection] =  {
    tryo{t:Throwable => logger.error("Cannot load database driver: %s".format(driverName), t)}{Class.forName(driverName);()}
    tryo{t:Throwable => logger.error("Unable to get database connection. url=%s".format(dbUrl),t)}(HikariDatasource.ds.getConnection())
  }

  def closeAllConnections_!(): Unit = HikariDatasource.ds.close()
}

trait CustomProtoDBVendor extends ConnectionManager {
  private val logger = Logger(classOf[CustomProtoDBVendor])

  /**
   *   How is a connection created?
   */
  def createOne: Box[Connection]

  // Tail Recursive function in order to avoid Stack Overflow
  // PLEASE NOTE: Changing this function you can break the above named feature
  def newConnection(name: ConnectionIdentifier): Box[Connection] = {
    createOne
  }

  def releaseConnection(conn: Connection): Unit = {conn.asInstanceOf[ProxyConnection].close()}

}