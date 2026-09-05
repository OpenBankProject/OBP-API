package code.api.util

import code.api.Constant
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier
import net.liftweb.util.Helpers.tryo

import java.sql.{ResultSet, Types}

object DBUtil {
  def dbUrl: String = APIUtil.getPropsValue("db.url") openOr Constant.h2DatabaseDefaultUrlValue

  def isSqlServer: Boolean = dbUrl.contains("sqlserver")

  /**
   * SQL Server-safe alternative to Lift's DB.runQuery.
   *
   * Lift's DB.runQuery uses DB.asString which doesn't handle SQL Server's NVARCHAR type
   * (JDBC type -9), causing MatchError. This function handles all JDBC types properly.
   *
   * @param query SQL query string
   * @param params Query parameters (for prepared statement)
   * @return Tuple of (column names, rows as List[List[String]])
   */
  def runQuery(query: String, params: List[String] = Nil): (List[String], List[List[String]]) = {
    DB.use(DefaultConnectionIdentifier) { conn =>
      val stmt = conn.prepareStatement(query)
      try {
        // Set parameters
        params.zipWithIndex.foreach { case (param, idx) =>
          stmt.setString(idx + 1, param)
        }

        val rs = stmt.executeQuery()
        val meta = rs.getMetaData
        val colCount = meta.getColumnCount

        // Get column names
        val colNames = (1 to colCount).map(i => meta.getColumnName(i)).toList

        // Get rows - convert all types to String safely
        var rows = List[List[String]]()
        while (rs.next()) {
          val row = (1 to colCount).map { i =>
            safeGetString(rs, i, meta.getColumnType(i))
          }.toList
          rows = rows :+ row
        }

        (colNames, rows)
      } finally {
        stmt.close()
      }
    }
  }

  /**
   * Safely convert any JDBC type to String, including SQL Server's NVARCHAR (-9).
   */
  private def safeGetString(rs: ResultSet, columnIndex: Int, jdbcType: Int): String = {
    val value = jdbcType match {
      case Types.NVARCHAR | Types.NCHAR | Types.LONGNVARCHAR | Types.NCLOB =>
        // SQL Server NVARCHAR types that Lift doesn't handle
        rs.getNString(columnIndex)
      case Types.CLOB =>
        val clob = rs.getClob(columnIndex)
        if (clob != null) clob.getSubString(1, clob.length().toInt) else null
      case Types.BLOB =>
        val blob = rs.getBlob(columnIndex)
        if (blob != null) new String(blob.getBytes(1, blob.length().toInt)) else null
      case _ =>
        rs.getString(columnIndex)
    }
    if (rs.wasNull()) null else value
  }
  
  def getDbConnectionParameters: (String, String, String) = {
    dbUrl.contains("jdbc:h2") match {
      case true => getH2DbConnectionParameters
      case false => getOtherDbConnectionParameters
    }
  }
  
  private def getOtherDbConnectionParameters: (String, String, String) = {
    // Split URL parameters by both ? / & (PostgreSQL, MySQL) and ; (SQL Server)
    val params = dbUrl.split("[?&;]")
    val username = params.find(_.startsWith("user=")).map(_.split("=", 2)(1))
    val password = params.find(_.startsWith("password=")).map(_.split("=", 2)(1))
    val dbUser = APIUtil.getPropsValue("db.user").orElse(username)
    val dbPassword = APIUtil.getPropsValue("db.password").orElse(password)
    (dbUrl, dbUser.getOrElse(""), dbPassword.getOrElse(""))
  }
  // H2 database has specific bd url string which is different compared to other databases
  private def getH2DbConnectionParameters: (String, String, String) = {
    val username = dbUrl.split(";").filter(_.contains("user")).toList.headOption.map(_.split("=")(1))
    val password = dbUrl.split(";").filter(_.contains("password")).toList.headOption.map(_.split("=")(1))
    val dbUser = APIUtil.getPropsValue("db.user").orElse(username)
    val dbPassword = APIUtil.getPropsValue("db.password").orElse(password)
    (dbUrl, dbUser.getOrElse(""), dbPassword.getOrElse(""))
  }
}
