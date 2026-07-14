package code.bankconnectors.storedprocedure

import org.json4s._
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.util.APIUtil
import code.api.util.migration.Migration
import code.api.util.migration.Migration.DbFunction
import code.bankconnectors.Connector
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.TopicTrait
import com.zaxxer.hikari.HikariDataSource
import doobie._
import doobie.implicits._
import doobie.free.{connection => FC}
import doobie.util.transactor.Strategy
import net.liftweb.common.{Box, Empty, Full}
import org.json4s.native.Serialization.write
import net.liftweb.mapper.Schemifier

/**
 * Stored procedure utils.
 * The reason of extract this util: if not call stored procedure connector method, the db connection of
 * stored procedure will not be initialized.
 */
object StoredProcedureUtils extends MdcLoggable{

  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats

  // lazy initial DB connection: separate HikariCP pool dedicated to the stored procedure connector
  private lazy val spTransactor: Transactor[IO] = {
    val driver = APIUtil.getPropsValue("stored_procedure_connector.driver").openOrThrowException("mandatory property stored_procedure_connector.driver is missing!")
    val url = APIUtil.getPropsValue("stored_procedure_connector.url").openOrThrowException("mandatory property stored_procedure_connector.url is missing!")
    val user = APIUtil.getPropsValue("stored_procedure_connector.user").openOrThrowException("mandatory property stored_procedure_connector.user is missing!")
    val password = APIUtil.getPropsValue("stored_procedure_connector.password").openOrThrowException("mandatory property stored_procedure_connector.password is missing!")

    val initialSize = APIUtil.getPropsAsIntValue("stored_procedure_connector.poolInitialSize", 5)
    val maxSize = APIUtil.getPropsAsIntValue("stored_procedure_connector.poolMaxSize", 20)
    val timeoutMillis = APIUtil.getPropsAsLongValue("stored_procedure_connector.poolConnectionTimeoutMillis", 3000L)
    val validationQuery = APIUtil.getPropsValue("stored_procedure_connector.poolValidationQuery", "select 1")

    Class.forName(driver)
    val ds = new HikariDataSource()
    ds.setJdbcUrl(url)
    ds.setUsername(user)
    ds.setPassword(password)
    ds.setDriverClassName(driver)
    ds.setMinimumIdle(initialSize)
    ds.setMaximumPoolSize(maxSize)
    ds.setConnectionTimeout(timeoutMillis)
    ds.setConnectionTestQuery(validationQuery)

    Transactor.fromDataSource[IO].apply(ds, code.api.util.BlockingIoExecutionContext.ec)
      .copy(strategy0 = Strategy.void)
  }


  /**
   * Health check case class for stored procedure connector
   */
  case class StoredProcedureConnectorHealth(
    status: String,
    serverName: Option[String],
    serverIp: Option[String],
    databaseName: Option[String],
    responseTimeMs: Long,
    errorMessage: Option[String]
  )

  /**
   * Perform a health check on the stored procedure connector.
   * Executes a database-specific query to verify connectivity and identify the backend node.
   * Supports: SQL Server, PostgreSQL, Oracle, and MySQL.
   */
  def getHealth(): StoredProcedureConnectorHealth = {
    val startTime = System.currentTimeMillis()
    try {
      val (serverName, serverIp, databaseName) = {
        val driver = APIUtil.getPropsValue("stored_procedure_connector.driver", "")

        if (driver.contains("sqlserver")) {
          // Microsoft SQL Server
          FC.raw { conn =>
            val rs = conn.createStatement().executeQuery("""
            SELECT
              @@SERVERNAME AS server_name,
              CAST(CONNECTIONPROPERTY('local_net_address') AS VARCHAR(50)) AS server_ip,
              DB_NAME() AS database_name
          """)
            if (rs.next()) (
              Option(rs.getString("server_name")),
              Option(rs.getString("server_ip")),
              Option(rs.getString("database_name"))
            ) else (None, None, None)
          }.transact(spTransactor).unsafeRunSync()
        } else if (driver.contains("postgresql")) {
          // PostgreSQL
          FC.raw { conn =>
            val rs = conn.createStatement().executeQuery("""
            SELECT
              inet_server_addr()::text AS server_ip,
              current_database() AS database_name,
              (SELECT setting FROM pg_settings WHERE name = 'cluster_name') AS server_name
          """)
            if (rs.next()) (
              Option(rs.getString("server_name")),
              Option(rs.getString("server_ip")),
              Option(rs.getString("database_name"))
            ) else (None, None, None)
          }.transact(spTransactor).unsafeRunSync()
        } else if (driver.contains("oracle")) {
          // Oracle
          FC.raw { conn =>
            val rs = conn.createStatement().executeQuery("""
            SELECT
              SYS_CONTEXT('USERENV', 'SERVER_HOST') AS server_name,
              SYS_CONTEXT('USERENV', 'IP_ADDRESS') AS server_ip,
              SYS_CONTEXT('USERENV', 'DB_NAME') AS database_name
            FROM DUAL
          """)
            if (rs.next()) (
              Option(rs.getString("server_name")),
              Option(rs.getString("server_ip")),
              Option(rs.getString("database_name"))
            ) else (None, None, None)
          }.transact(spTransactor).unsafeRunSync()
        } else if (driver.contains("mysql") || driver.contains("mariadb")) {
          // MySQL / MariaDB
          FC.raw { conn =>
            val rs = conn.createStatement().executeQuery("""
            SELECT
              @@hostname AS server_name,
              @@bind_address AS server_ip,
              DATABASE() AS database_name
          """)
            if (rs.next()) (
              Option(rs.getString("server_name")),
              Option(rs.getString("server_ip")),
              Option(rs.getString("database_name"))
            ) else (None, None, None)
          }.transact(spTransactor).unsafeRunSync()
        } else {
          // Generic fallback - just test connectivity
          FC.raw { conn =>
            conn.createStatement().executeQuery("SELECT 1")
            (None, None, None)
          }.transact(spTransactor).unsafeRunSync()
        }
      }
      val responseTime = System.currentTimeMillis() - startTime
      StoredProcedureConnectorHealth(
        status = "ok",
        serverName = serverName,
        serverIp = serverIp,
        databaseName = databaseName,
        responseTimeMs = responseTime,
        errorMessage = None
      )
    } catch {
      case e: Exception =>
        val responseTime = System.currentTimeMillis() - startTime
        logger.error(s"Stored procedure connector health check failed: ${e.getMessage}", e)
        StoredProcedureConnectorHealth(
          status = "error",
          serverName = None,
          serverIp = None,
          databaseName = None,
          responseTimeMs = responseTime,
          errorMessage = Some(e.getMessage)
        )
    }
  }

  def callProcedure[T: Manifest](procedureName: String, outBound: TopicTrait): Box[T] = {
    val procedureParam: String = write(outBound) // convert OutBound to json string
    logger.debug(s"${StoredProcedureConnector_vDec2019.toString} outBoundJson: $procedureName = $procedureParam" )
    val responseJson: String =
      FC.raw { conn =>
        val callableStatement = conn.prepareCall(s"{ CALL $procedureName(?, ?) }")
        try {
          callableStatement.setString(1, procedureParam)
          callableStatement.registerOutParameter(2, java.sql.Types.LONGVARCHAR)
          //        callableStatement.setString(2, "") // MS sql server must comment this line, other DB need check.
          callableStatement.executeUpdate()
          callableStatement.getString(2)
        } finally {
          callableStatement.close()
        }
      }.transact(spTransactor).unsafeRunSync()
    logger.debug(s"${StoredProcedureConnector_vDec2019.toString} inBoundJson: $procedureName = $responseJson" )
    Connector.extractAdapterResponse[T](responseJson, Empty)
  }
}

object StoredProceduresMockedData {
  def createOrDropMockedPostgresStoredProcedures() = {
    def create(): String = {
      DbFunction.maybeWrite(true, Schemifier.infoF _) {
        () => """CREATE OR REPLACE FUNCTION public.get_banks(p_out_bound_json text, INOUT in_bound_json text)
                | RETURNS text
                | LANGUAGE plpgsql
                |AS $function$
                |BEGIN
                |
                |select 
                |	(SELECT row_to_json(top) 
                |		FROM (
                |			SELECT 
                |			(select row_to_json(call_context) from (
                |				select 
                |				'correlation_id_value' AS "correlationId",
                |				'session_id_value' AS "sessionId") as call_context
                |			) AS "inboundAdapterCallContext",
                |			(select row_to_json(status_code) from (select '' as "errorCode") as status_code) AS "status",
                |			(
                |				select array_to_json(array_agg(row_to_json(d)))
                |				from
                |				(SELECT  
                |					(select row_to_json(bank_id_value) from (select id as value) as bank_id_value) AS "bankId", 
                |					shortbankname AS "shortName", 
                |					fullbankname AS "fullName", 
                |					logourl AS "logoUrl", 
                |					websiteurl AS "websiteUrl", 
                |					mbankroutingaddress AS "bankRoutingAddress", 
                |					mbankroutingscheme AS "bankRoutingScheme"
                |					FROM public.mappedbank 
                |				) AS d
                |			) as data
                |		) AS top) 
                |into in_bound_json;
                |
                |END;
                |$function$
                |;""".stripMargin
      }
    }
    def drop(): String = {
      DbFunction.maybeWrite(true, Schemifier.infoF _) {
        () => "DROP FUNCTION public.get_banks(text, text);"
      }
    }
    val mapperDB = APIUtil.getPropsValue("stored_procedure_connector.driver")
    val connectorDB = APIUtil.getPropsValue("db.driver")
    val thereIsTheProcedure = Migration.DbFunction.procedureExists("get_banks")
    (mapperDB, connectorDB) match {
      case (Full(mapper), Full(connector)) if(mapper == connector && mapper == "org.postgresql.Driver" && thereIsTheProcedure) =>
        drop()
        create()      
      case (Full(mapper), Full(connector)) if(mapper == connector && mapper == "org.postgresql.Driver") =>
        create()
      case _ =>
        ""
    }
  }
}