package code.bankconnectors.storedprocedure

import java.sql.PreparedStatement

import code.api.util.APIUtil
import com.openbankproject.commons.model.TopicTrait
import net.liftweb.json
import net.liftweb.json.JValue
import net.liftweb.json.Serialization.write
import scalikejdbc.{DB, _}

object MssqlDBUtils {
  private implicit val formats = code.api.util.CustomJsonFormats.formats
  private val before: PreparedStatement => Unit = _ => ()

  // lazy initial DB connection
  {
    val driver = APIUtil.getPropsValue("db.default.driver").openOrThrowException("mandatory property db.default.driver is missing!")
    val url = APIUtil.getPropsValue("db.default.url").openOrThrowException("mandatory property db.default.url is missing!")
    val user = APIUtil.getPropsValue("db.default.user").openOrThrowException("mandatory property db.default.user is missing!")
    val password = APIUtil.getPropsValue("db.default.password").openOrThrowException("mandatory property db.default.password is missing!")

    val initialSize = APIUtil.getPropsAsIntValue("db.default.poolInitialSize", 5)
    val maxSize = APIUtil.getPropsAsIntValue("db.default.poolMaxSize", 20)
    val timeoutMillis = APIUtil.getPropsAsLongValue("db.default.poolConnectionTimeoutMillis", 3000L)
    val validationQuery = APIUtil.getPropsValue("db.default.poolValidationQuery", "select 1 from dual")
    val poolFactoryName = APIUtil.getPropsValue("db.default.poolFactoryName", "defaultPoolFactory")


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


  def callMsProcedure[T: Manifest](procedureName: String, outBound: TopicTrait): T = {
    val procedureParam: String = write(outBound) // convert OutBound to json string

    var responseJson: String = ""
    DB autoCommit { implicit session =>

      sql"{ CALL ? (?) }"
        .bind(procedureName, procedureParam)
        .executeWithFilters(before,
          statement => {
            val resultSet = statement.getResultSet()
            require(resultSet.next(), s"stored procedure $procedureName must return a json response")
            responseJson = resultSet.getString(1)
          }).apply()
    }
    if(classOf[JValue].isAssignableFrom(manifest[T].runtimeClass)) {
      json.parse(responseJson).asInstanceOf[T]
    } else {
      json.parse(responseJson).extract[T]
    }

  }

}
