package code.endpointTag

import code.api.util.{APIUtil, CustomJsonFormats, DoobieUtil}
import com.openbankproject.commons.model.EndpointTagT
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

/**
 * One user-defined tag on an endpoint.
 *
 * The name is kept from the Lift entity rather than becoming DoobieEndpointTag: the concrete type
 * is used directly by LocalMappedConnector's twelve endpoint-tag methods, so renaming it would
 * churn the connector for no gain. (The provider trait and the Connector trait are both stated in
 * terms of the obp-commons EndpointTagT, so nothing leaks beyond LocalMappedConnector.)
 *
 * A tag is system-level when bankId is absent and bank-level otherwise; Mapper stored the
 * system-level case by writing null into the column and read it back as None for null-or-empty,
 * which is preserved here.
 */
case class EndpointTag(
  endpointTagIdValue: String,
  operationId: String,
  tagName: String,
  bankIdValue: Option[String]
) extends EndpointTagT {
  override def endpointTagId: Option[String] = Option(endpointTagIdValue)
  override def bankId: Option[String] = bankIdValue
}

object EndpointTag {

  private val selectColumns =
    fr"SELECT endpointtagid, operationid, tagname, bankid FROM endpointtag"

  private type Row = (Option[String], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): EndpointTag = row match {
    case (endpointTagId, operationId, tagName, bankId) =>
      // null and "" both mean "system-level", matching the Mapper getter.
      EndpointTag(endpointTagId.orNull, operationId.orNull, tagName.orNull,
        bankId.filter(_.nonEmpty))
  }

  private def query(condition: Fragment): List[EndpointTag] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[EndpointTag] =
    query(condition ++ fr"LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findAll(): List[EndpointTag] = query(Fragment.empty)

  def findByEndpointTagId(endpointTagId: String): Box[EndpointTag] =
    one(fr"WHERE endpointtagid = $endpointTagId")

  def findByOperationId(operationId: String): Box[EndpointTag] =
    one(fr"WHERE operationid = $operationId")

  def findAllByOperationId(operationId: String): List[EndpointTag] =
    query(fr"WHERE operationid = $operationId ORDER BY tagname ASC")

  def findAllByBankIdAndOperationId(bankId: String, operationId: String): List[EndpointTag] =
    query(fr"WHERE bankid = $bankId AND operationid = $operationId ORDER BY tagname ASC")

  def findByOperationIdAndTagName(operationId: String, tagName: String): Box[EndpointTag] =
    one(fr"WHERE operationid = $operationId AND tagname = $tagName")

  def insert(bankId: Option[String], operationId: String, tagName: String): EndpointTag = {
    val newId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    import doobie.implicits.javasql._
    DoobieUtil.runUpdate(
      sql"""INSERT INTO endpointtag (endpointtagid, bankid, operationid, tagname, createdat, updatedat)
            VALUES ($newId, $bankId, $operationId, $tagName, $now, $now)"""
        .update.run)
    EndpointTag(newId, operationId, tagName, bankId.filter(_.nonEmpty))
  }

  /** Overwrite an existing tag by id; Empty when there is no such row. */
  def updateById(endpointTagId: String, bankId: Option[String], operationId: String, tagName: String): Box[EndpointTag] =
    findByEndpointTagId(endpointTagId) match {
      case Full(_) =>
        val now = new java.sql.Timestamp(System.currentTimeMillis())
        import doobie.implicits.javasql._
        DoobieUtil.runUpdate(
          sql"""UPDATE endpointtag SET bankid = $bankId, operationid = $operationId,
                tagname = $tagName, updatedat = $now WHERE endpointtagid = $endpointTagId"""
            .update.run)
        Full(EndpointTag(endpointTagId, operationId, tagName, bankId.filter(_.nonEmpty)))
      case other => other
    }

  def deleteByEndpointTagId(endpointTagId: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM endpointtag WHERE endpointtagid = $endpointTagId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM endpointtag".update.run)
    ()
  }
}

object MappedEndpointTagProvider extends EndpointTagProvider with CustomJsonFormats {

  override def getById(endpointTagId: String): Box[EndpointTagT] =
    EndpointTag.findByEndpointTagId(endpointTagId)

  override def getByOperationId(operationId: String): Box[EndpointTagT] =
    EndpointTag.findByOperationId(operationId)

  override def createOrUpdate(endpointTag: EndpointTagT): Box[EndpointTagT] = {
    //to find exists endpointTag, if endpointTagId supplied, query by endpointTagId, or use endpointName and endpointTagId to do query
    val existsEndpointTag: Box[EndpointTag] = endpointTag.endpointTagId match {
      case Some(id) if StringUtils.isNotBlank(id) => EndpointTag.findByEndpointTagId(id)
      case _ => Empty
    }
    tryo {
      existsEndpointTag match {
        case Full(existing) =>
          // Mapper reused the found row and only wrote OperationId/TagName, leaving BankId as it
          // was on that row; a fresh row got whatever BankId its defaults gave it (empty).
          EndpointTag.updateById(existing.endpointTagIdValue, existing.bankIdValue,
            endpointTag.operationId, endpointTag.tagName)
            .openOrThrowException("the row just matched must still be updatable")
        case _ =>
          EndpointTag.insert(None, endpointTag.operationId, endpointTag.tagName)
      }
    }
  }

  override def delete(endpointTagId: String): Box[Boolean] =
    EndpointTag.findByEndpointTagId(endpointTagId).map(_ => EndpointTag.deleteByEndpointTagId(endpointTagId))

  override def getAllEndpointTags: List[EndpointTagT] = EndpointTag.findAll()
}
