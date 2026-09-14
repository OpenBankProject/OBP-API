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

package bootstrap.liftweb

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import com.zaxxer.hikari.pool.ProxyConnection
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import java.sql.Connection
import net.liftweb.common.{Box, Full, Logger}
import net.liftweb.db.ConnectionManager
import net.liftweb.util.ConnectionIdentifier
import net.liftweb.util.Helpers.tryo

/**
 * The Custom DB vendor.
 *
 * @param driverName the name of the database driver
 * @param dbUrl the URL for the JDBC data connection
 * @param dbUser the optional username
 * @param dbPassword the optional db password
 */
class CustomDBVendor(driverName: String,
                     dbUrl: String,
                     dbUser: Box[String],
                     dbPassword: Box[String]) extends CustomProtoDBVendor with MdcLoggable {

  object HikariDatasource {
    val config = new HikariConfig()

    val connectionTimeout = APIUtil.getPropsAsLongValue("hikari.connectionTimeout", 30000L)
    // Default 20: each request holds its transaction connection for its whole lifetime,
    // so a pool of 10 exhausts at ~5 concurrent requests (rate-limit queries need a 2nd connection).
    // Kept prop-overridable so ops can tune higher.
    val maximumPoolSize   = APIUtil.getPropsAsIntValue("hikari.maximumPoolSize", 20)
    val idleTimeout       = APIUtil.getPropsAsLongValue("hikari.idleTimeout", 600000L)
    val keepaliveTime     = APIUtil.getPropsAsLongValue("hikari.keepaliveTime", 30000L)
    val maxLifetime       = APIUtil.getPropsAsLongValue("hikari.maxLifetime", 1800000L)

    config.setConnectionTimeout(connectionTimeout)
    config.setMaximumPoolSize(maximumPoolSize)
    config.setIdleTimeout(idleTimeout)
    config.setKeepaliveTime(keepaliveTime)
    config.setMaxLifetime(maxLifetime)
    //Liftweb DB.scala will set all the new connections to false, so here we set default to false
    val autoCommitValue: Boolean = false
    config.setAutoCommit(autoCommitValue)
    logger.info(s"We set HikariDatasource config.setAutoCommit=$autoCommitValue")
    logger.info(s"Note: HirakiCP will reset any connection to autoCommit=$autoCommitValue when it returns it to the pool if it has been otherwise set in code. (This can cause further debug messages and some performance impact.)")

    (dbUser, dbPassword) match {
      case (Full(user), Full(pwd)) =>
        config.setJdbcUrl(dbUrl)
        config.setUsername(user)
        config.setPassword(pwd)
      case _ =>
        config.setJdbcUrl(dbUrl)
    }

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

trait CustomProtoDBVendor extends ConnectionManager with MdcLoggable {

  def createOne: Box[Connection]

  def newConnection(name: ConnectionIdentifier): Box[Connection] = {
    createOne
  }

  def releaseConnection(conn: Connection): Unit = {conn.asInstanceOf[ProxyConnection].close()}

}
