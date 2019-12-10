package code.bankconnectors.storedprocedure

import java.sql.PreparedStatement

import com.openbankproject.commons.model.TopicTrait
import net.liftweb.json
import net.liftweb.json.JValue
import net.liftweb.json.Serialization.write
import scalikejdbc.{DB, _}
import scalikejdbc.config.DBs

object MssqlDBUtils {
  DBs.setupAll()
  private implicit val formats = code.api.util.CustomJsonFormats.formats
  private val before: PreparedStatement => Unit = _ => ()

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
