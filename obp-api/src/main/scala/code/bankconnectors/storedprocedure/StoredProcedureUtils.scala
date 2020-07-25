package code.bankconnectors.storedprocedure

import java.sql.Connection

import code.api.util.APIUtil
import code.api.util.migration.Migration
import code.api.util.migration.Migration.DbFunction
import code.bankconnectors.Connector
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.TopicTrait
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier
import scalikejdbc.{DB => scalikeDB, _}

/**
 * Stored procedure utils.
 * The reason of extract this util: if not call stored procedure connector method, the db connection of
 * stored procedure will not be initialized.
 */
object StoredProcedureUtils extends MdcLoggable{

  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats

  // lazy initial DB connection
  {
    val driver = APIUtil.getPropsValue("stored_procedure_connector.driver").openOrThrowException("mandatory property stored_procedure_connector.driver is missing!")
    val url = APIUtil.getPropsValue("stored_procedure_connector.url").openOrThrowException("mandatory property stored_procedure_connector.url is missing!")
    val user = APIUtil.getPropsValue("stored_procedure_connector.user").openOrThrowException("mandatory property stored_procedure_connector.user is missing!")
    val password = APIUtil.getPropsValue("stored_procedure_connector.password").openOrThrowException("mandatory property stored_procedure_connector.password is missing!")

    val initialSize = APIUtil.getPropsAsIntValue("stored_procedure_connector.poolInitialSize", 5)
    val maxSize = APIUtil.getPropsAsIntValue("stored_procedure_connector.poolMaxSize", 20)
    val timeoutMillis = APIUtil.getPropsAsLongValue("stored_procedure_connector.poolConnectionTimeoutMillis", 3000L)
    val validationQuery = APIUtil.getPropsValue("stored_procedure_connector.poolValidationQuery", "select 1")
    val poolFactoryName = APIUtil.getPropsValue("stored_procedure_connector.poolFactoryName", "commons-dbcp2")


    Class.forName(driver)
    val settings = ConnectionPoolSettings(
      initialSize = initialSize,
      maxSize = maxSize,
      connectionTimeoutMillis = timeoutMillis,
      validationQuery = validationQuery,
      connectionPoolFactoryName = poolFactoryName
    )
    ConnectionPool.singleton(url, user, password, settings)
  }


  def callProcedure[T: Manifest](procedureName: String, outBound: TopicTrait): Box[T] = {
    val procedureParam: String = write(outBound) // convert OutBound to json string
    logger.debug(s"${StoredProcedureConnector_vDec2019.toString} outBoundJson: $procedureName = $procedureParam" )
    val responseJson: String =
      scalikeDB autoCommit { implicit session =>
        val conn: Connection = session.connection
        val sql = s"{ CALL $procedureName(?, ?) }"

        val callableStatement = conn.prepareCall(sql)
        callableStatement.setString(1, procedureParam)

        callableStatement.registerOutParameter(2, java.sql.Types.LONGVARCHAR)
        //        callableStatement.setString(2, "") // MS sql server must comment this line, other DB need check.
        callableStatement.executeUpdate()
        callableStatement.getString(2)
     }
    logger.debug(s"${StoredProcedureConnector_vDec2019.toString} inBoundJson: $procedureName = $responseJson" )
    Connector.extractAdapterResponse[T](responseJson, Empty)
  }
}

object StoredProceduresMockedData {
  def createOrDropMockedPostgresStoredProcedures() = {
    def create(): String = {
      DbFunction.maybeWrite(true, Schemifier.infoF _, DB.use(DefaultConnectionIdentifier){ conn => conn}) {
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
      DbFunction.maybeWrite(true, Schemifier.infoF _, DB.use(DefaultConnectionIdentifier){ conn => conn}) {
        () => "DROP FUNCTION public.get_banks(text, text);"
      }
    }
    val mapperDB = APIUtil.getPropsValue("stored_procedure_connector.driver")
    val connectorDB = APIUtil.getPropsValue("db.driver")
    val thereIsTheProcedure = Migration.DbFunction.procedureExists("get_banks", DB.use(DefaultConnectionIdentifier){ conn => conn})
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