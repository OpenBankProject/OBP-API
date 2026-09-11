package code.endpointMapping

import code.api.util.{APIUtil, CustomJsonFormats, DoobieUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

/**
 * A mapping from an OpenAPI operation to request/response transformations.
 *
 * `operationId` is unique GLOBALLY, not per bank, so a bank-level and a system-level mapping
 * cannot share one — the second create fails rather than shadowing the first. `bankId` narrows a
 * read; it does not widen the key.
 *
 * `bankId` genuinely holds NULL for system-level mappings, so it is bound as an Option.
 */
case class EndpointMapping(
  private val endpointMappingIdRaw: String,
  operationId: String,
  requestMapping: String,
  responseMapping: String,
  private val bankIdRaw: String
) extends EndpointMappingT {
  override def endpointMappingId: Option[String] = Option(endpointMappingIdRaw)
  override def bankId: Option[String] =
    if (bankIdRaw == null || bankIdRaw.isEmpty) None else Some(bankIdRaw)
}

object EndpointMapping {

  private val selectColumns =
    fr"""SELECT endpointmappingid, operationid, requestmapping, responsemapping, bankid
         FROM endpointmapping"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String])

  private def fromRow(row: Row): EndpointMapping = row match {
    case (endpointMappingId, operationId, requestMapping, responseMapping, bankId) =>
      EndpointMapping(endpointMappingId.orNull, operationId.orNull, requestMapping.orNull,
        responseMapping.orNull, bankId.orNull)
  }

  private def query(condition: Fragment): List[EndpointMapping] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[EndpointMapping] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findById(endpointMappingId: String): Box[EndpointMapping] =
    one(fr"WHERE endpointmappingid = $endpointMappingId")

  def findByIdAndBankId(endpointMappingId: String, bankId: String): Box[EndpointMapping] =
    one(fr"WHERE endpointmappingid = $endpointMappingId AND bankid = $bankId")

  def findByOperationId(operationId: String): Box[EndpointMapping] =
    one(fr"WHERE operationid = $operationId")

  def findByOperationIdAndBankId(operationId: String, bankId: String): Box[EndpointMapping] =
    one(fr"WHERE operationid = $operationId AND bankid = $bankId")

  def findAll(): List[EndpointMapping] = query(fr"ORDER BY id ASC")

  def findAllByBankId(bankId: String): List[EndpointMapping] =
    query(fr"WHERE bankid = $bankId ORDER BY id ASC")

  def insert(operationId: String, requestMapping: String, responseMapping: String,
             bankId: Option[String]): EndpointMapping = {
    val endpointMappingId = APIUtil.generateUUID()
    DoobieUtil.runUpdate(
      sql"""INSERT INTO endpointmapping
            (endpointmappingid, operationid, requestmapping, responsemapping, bankid)
            VALUES ($endpointMappingId, $operationId, $requestMapping, $responseMapping, $bankId)"""
        .update.run)
    EndpointMapping(endpointMappingId, operationId, requestMapping, responseMapping, bankId.orNull)
  }

  def update(endpointMappingId: String, operationId: String, requestMapping: String,
             responseMapping: String, bankId: Option[String]): Box[EndpointMapping] = {
    DoobieUtil.runUpdate(
      sql"""UPDATE endpointmapping SET operationid = $operationId, requestmapping = $requestMapping,
              responsemapping = $responseMapping, bankid = $bankId
            WHERE endpointmappingid = $endpointMappingId""".update.run)
    findById(endpointMappingId)
  }

  def delete(endpointMappingId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM endpointmapping WHERE endpointmappingid = $endpointMappingId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM endpointmapping".update.run)
    ()
  }
}

object MappedEndpointMappingProvider extends EndpointMappingProvider with CustomJsonFormats {

  override def getById(bankId: Option[String], endpointMappingId: String): Box[EndpointMappingT] =
    if (bankId.isEmpty) EndpointMapping.findById(endpointMappingId)
    else EndpointMapping.findByIdAndBankId(endpointMappingId, bankId.getOrElse(""))

  override def getByOperationId(bankId: Option[String], operationId: String): Box[EndpointMappingT] =
    if (bankId.isEmpty) EndpointMapping.findByOperationId(operationId)
    else EndpointMapping.findByOperationIdAndBankId(operationId, bankId.getOrElse(""))

  override def createOrUpdate(bankId: Option[String], endpointMapping: EndpointMappingT): Box[EndpointMappingT] = {
    // Existing rows are found by endpointMappingId only, ignoring the bankId argument — the same
    // lookup Mapper did. A supplied id that does not resolve becomes an insert rather than an error.
    val existing: Box[EndpointMapping] = endpointMapping.endpointMappingId match {
      case Some(id) if StringUtils.isNotBlank(id) => EndpointMapping.findById(id)
      case _ => Empty
    }
    tryo {
      existing match {
        case Full(row) =>
          EndpointMapping.update(row.endpointMappingId.getOrElse(""), endpointMapping.operationId,
            endpointMapping.requestMapping, endpointMapping.responseMapping, endpointMapping.bankId)
        case _ =>
          Full(EndpointMapping.insert(endpointMapping.operationId, endpointMapping.requestMapping,
            endpointMapping.responseMapping, endpointMapping.bankId))
      }
    }.flatMap(identity)
  }

  override def delete(bankId: Option[String], endpointMappingId: String): Box[Boolean] =
    getById(bankId, endpointMappingId).map(_ => EndpointMapping.delete(endpointMappingId))

  override def getAllEndpointMappings(bankId: Option[String]): List[EndpointMappingT] =
    if (bankId.isEmpty) EndpointMapping.findAll()
    else EndpointMapping.findAllByBankId(bankId.getOrElse(""))
}
