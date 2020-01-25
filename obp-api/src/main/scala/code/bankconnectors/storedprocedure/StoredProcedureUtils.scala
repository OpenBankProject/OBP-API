package code.bankconnectors.storedprocedure

import java.sql.Connection

import code.api.util.APIUtil
import com.openbankproject.commons.model.TopicTrait
import net.liftweb.json
import net.liftweb.json.JValue
import net.liftweb.json.Serialization.write
import scalikejdbc.{DB, _}

/**
 * Stored procedure utils.
 * The reason of extract this util: if not call stored procedure connector method, the db connection of
 * stored procedure will not be initialized.
 */
object StoredProcedureUtils {

  // lazy initial DB connection
  {
    val driver = APIUtil.getPropsValue("stored_procedure_connector.driver").openOrThrowException("mandatory property stored_procedure_connector.driver is missing!")
    val url = APIUtil.getPropsValue("stored_procedure_connector.url").openOrThrowException("mandatory property stored_procedure_connector.url is missing!")
    val user = APIUtil.getPropsValue("stored_procedure_connector.user").openOrThrowException("mandatory property stored_procedure_connector.user is missing!")
    val password = APIUtil.getPropsValue("stored_procedure_connector.password").openOrThrowException("mandatory property stored_procedure_connector.password is missing!")

    val initialSize = APIUtil.getPropsAsIntValue("stored_procedure_connector.poolInitialSize", 5)
    val maxSize = APIUtil.getPropsAsIntValue("stored_procedure_connector.poolMaxSize", 20)
    val timeoutMillis = APIUtil.getPropsAsLongValue("stored_procedure_connector.poolConnectionTimeoutMillis", 3000L)
    val validationQuery = APIUtil.getPropsValue("stored_procedure_connector.poolValidationQuery", "select 1 from dual")
    val poolFactoryName = APIUtil.getPropsValue("stored_procedure_connector.poolFactoryName", "defaultPoolFactory")


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


  def callProcedure(procedureName: String, outBound: TopicTrait): JValue = {
    implicit val formats = code.api.util.CustomJsonFormats.formats
    val procedureParam: String = write(outBound) // convert OutBound to json string

    val responseJson: String =
      DB autoCommit { implicit session =>
        val conn: Connection = session.connection
        // postgresql DB is different with other DB, it need a special way to call procedure.
        val dbName = conn.getMetaData().getDatabaseProductName()
        if(dbName.equalsIgnoreCase("PostgreSQL")) {

          val preparedStatement = conn.prepareStatement(s" CALL $procedureName (?, '')")

          preparedStatement.setString(1, procedureParam)
          preparedStatement.execute()
          val rs = preparedStatement.getResultSet()
          rs.next
          rs.getString(1)
        } else {
          val sql = s"{ CALL $procedureName(?, ?) }"

          val callableStatement = conn.prepareCall(sql)
          callableStatement.setString(1, procedureParam)

          callableStatement.registerOutParameter(2, java.sql.Types.LONGVARCHAR)
          callableStatement.setString(2, "")
          callableStatement.executeUpdate()
          callableStatement.getString(2)
        }
     }

    json.parse(responseJson)
  }

}
