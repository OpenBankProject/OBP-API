package code.methodrouting

import code.api.util.{APIUtil, CustomJsonFormats, DoobieUtil}
import com.openbankproject.commons.util.Functions.Implicits._
import com.openbankproject.commons.util.json
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils
import org.json4s.JsonAST.JArray
import org.json4s._
import org.json4s.native.Serialization.write

/**
 * One routing rule: which connector implementation handles a method, optionally scoped to banks.
 *
 * Read on every connector call — StarConnector resolves method+bank to a connector through
 * code.bankconnectors.package, in the order exact method+bankId, then regex bankIdPattern, then
 * method-only, then the `mapped` fallback.
 */
case class MethodRouting(
  methodRoutingIdValue: String,
  methodName: String,
  bankIdPatternValue: String,
  isBankIdExactMatch: Boolean,
  connectorName: String,
  parametersJson: String
) extends MethodRoutingT with CustomJsonFormats {

  override def methodRoutingId: Option[String] = Option(methodRoutingIdValue)
  override def bankIdPattern: Option[String] = Option(bankIdPatternValue)

  // The whole key/value list lives in one column as a JSON array, not a child table.
  override def parameters: List[MethodRoutingParam] = {
    val value = json.parse(parametersJson ?: "[]").asInstanceOf[JArray]
    value.arr.map(MethodRoutingParam(_))
  }
}

object MethodRouting extends CustomJsonFormats {

  /**
    * default bankIdPattern is match any
    */
  val bankIdPatternMatchAny: String = ".*"

  private val selectColumns =
    fr"SELECT methodroutingid, methodname, bankidpattern, isbankidexactmatch, connectorname, parameters FROM methodrouting"

  private type Row = (Option[String], Option[String], Option[String], Option[Boolean],
    Option[String], Option[String])

  private def fromRow(row: Row): MethodRouting = row match {
    case (methodRoutingId, methodName, bankIdPattern, isBankIdExactMatch, connectorName, parameters) =>
      MethodRouting(methodRoutingId.orNull, methodName.orNull, bankIdPattern.orNull,
        isBankIdExactMatch.getOrElse(false), connectorName.orNull, parameters.orNull)
  }

  private def query(condition: Fragment): List[MethodRouting] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findByMethodRoutingId(methodRoutingId: String): Box[MethodRouting] =
    query(fr"WHERE methodroutingid = $methodRoutingId LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /**
   * Each filter is applied only when supplied — mirrors the Mapper QueryParam list.
   *
   * The String values are bound as Option, NOT as bare String, because callers legitimately pass
   * `Some(null)`: bankId is extracted reflectively from connector-method arguments
   * (code.bankconnectors.package) and comes back null when the argument is absent, so
   * `getMethodRoutings(Some(methodName), Some(true), Some(bankId))` can carry a null inside the
   * Some. Lift's `By(field, null)` rendered that as `field = NULL`, which matches nothing and
   * quietly returns an empty list; Doobie's Put for a non-nullable String throws
   * "oops, null" instead and the request 500s. Binding Option restores the SQL-NULL behaviour,
   * so a null bankId means "no exact-match routing" exactly as before rather than an error.
   */
  def findAllBy(methodName: Option[String],
                isBankIdExactMatch: Option[Boolean],
                bankIdPattern: Option[String]): List[MethodRouting] = {
    val conditions = List(
      methodName.map(v => fr"methodname = ${Option(v)}"),
      isBankIdExactMatch.map(v => fr"isbankidexactmatch = $v"),
      bankIdPattern.map(v => fr"bankidpattern = ${Option(v)}")
    ).flatten
    val where =
      if (conditions.isEmpty) Fragment.empty
      else fr"WHERE " ++ conditions.reduce((a, b) => a ++ fr"AND" ++ b)
    query(where)
  }

  def insert(methodName: String, bankIdPattern: String, isBankIdExactMatch: Boolean,
             connectorName: String, parametersJson: String): MethodRouting = {
    val newId = APIUtil.generateUUID()
    DoobieUtil.runUpdate(
      sql"""INSERT INTO methodrouting
            (methodroutingid, methodname, bankidpattern, isbankidexactmatch, connectorname, parameters)
            VALUES ($newId, $methodName, $bankIdPattern, $isBankIdExactMatch, $connectorName, $parametersJson)"""
        .update.run)
    MethodRouting(newId, methodName, bankIdPattern, isBankIdExactMatch, connectorName, parametersJson)
  }

  def updateByMethodRoutingId(methodRoutingId: String, methodName: String, bankIdPattern: String,
                              isBankIdExactMatch: Boolean, connectorName: String,
                              parametersJson: String): MethodRouting = {
    DoobieUtil.runUpdate(
      sql"""UPDATE methodrouting SET methodname = $methodName, bankidpattern = $bankIdPattern,
              isbankidexactmatch = $isBankIdExactMatch, connectorname = $connectorName,
              parameters = $parametersJson
            WHERE methodroutingid = $methodRoutingId"""
        .update.run)
    MethodRouting(methodRoutingId, methodName, bankIdPattern, isBankIdExactMatch, connectorName, parametersJson)
  }

  def deleteByMethodRoutingId(methodRoutingId: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM methodrouting WHERE methodroutingid = $methodRoutingId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM methodrouting".update.run)
    ()
  }
}

object MappedMethodRoutingProvider extends MethodRoutingProvider with CustomJsonFormats {

  override def getById(methodRoutingId: String): Box[MethodRoutingT] =
    MethodRouting.findByMethodRoutingId(methodRoutingId)

  override def getMethodRoutings(methodName: Option[String],
                                 isBankIdExactMatch: Option[Boolean] = None,
                                 bankIdPattern: Option[String] = None): List[MethodRouting] =
    MethodRouting.findAllBy(methodName, isBankIdExactMatch, bankIdPattern)

  override def createOrUpdate(methodRouting: MethodRoutingT): Box[MethodRoutingT] = {

    val bankIdPattern = methodRouting.bankIdPattern
                          .filter(StringUtils.isNotBlank) // treat blank string as not supplied

    //to find exists methodRouting, if methodRoutingId supplied, query by methodRoutingId, or use methodName and methodRoutingId to do query
    val existsMethodRouting: Box[MethodRouting] = methodRouting.methodRoutingId match {
      case Some(id) if StringUtils.isNotBlank(id) => MethodRouting.findByMethodRoutingId(id)
      case _ => Empty
    }
    // if not supply bankIdPattern, isExactMatch must be false
    val isExactMatch = if (bankIdPattern.isDefined) methodRouting.isBankIdExactMatch else false

    val existsMethodRoutingParameters = methodRouting.parameters match {
      case parameters if parameters.nonEmpty => parameters
      case _ => List.empty[MethodRoutingParam]
    }
    // Mapper wrote BankIdPattern(bankIdPattern.orNull); reading a null column back through
    // MappedString yields the field's defaultValue, which for this entity is ".*" (match any).
    // Storing the default directly keeps that observable behaviour without relying on a null
    // round-trip.
    val bankIdPatternToStore = bankIdPattern.getOrElse(MethodRouting.bankIdPatternMatchAny)
    val parametersJson = write(existsMethodRoutingParameters)

    tryo {
      existsMethodRouting match {
        case Full(existing) =>
          MethodRouting.updateByMethodRoutingId(
            existing.methodRoutingIdValue, methodRouting.methodName, bankIdPatternToStore,
            isExactMatch, methodRouting.connectorName, parametersJson)
        case _ =>
          MethodRouting.insert(
            methodRouting.methodName, bankIdPatternToStore, isExactMatch,
            methodRouting.connectorName, parametersJson)
      }
    }
  }

  override def delete(methodRoutingId: String): Box[Boolean] =
    MethodRouting.findByMethodRoutingId(methodRoutingId)
      .map(_ => MethodRouting.deleteByMethodRoutingId(methodRoutingId))
}
