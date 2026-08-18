package code.DynamicEndpoint

import code.api.cache.Caching
import code.api.dynamic.endpoint.helper.DynamicEndpointHelper
import code.api.util.{APIUtil, CustomJsonFormats, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.concurrent.duration.DurationInt

/**
 * One uploaded OpenAPI document, served as a set of proxied endpoints.
 *
 * `bankId` genuinely holds NULL for system-level endpoints, so it is bound as an Option.
 */
case class DynamicEndpoint(
  private val dynamicEndpointIdRaw: String,
  swaggerString: String,
  userId: String,
  private val bankIdRaw: String
) extends DynamicEndpointT {
  override def dynamicEndpointId: Option[String] = Option(dynamicEndpointIdRaw)
  override def bankId: Option[String] =
    if (bankIdRaw == null || bankIdRaw.isEmpty) None else Some(bankIdRaw)
}

object DynamicEndpoint {

  private val selectColumns =
    fr"SELECT dynamicendpointid, swaggerstring, userid, bankid FROM dynamicendpoint"

  private type Row = (Option[String], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): DynamicEndpoint = row match {
    case (dynamicEndpointId, swaggerString, userId, bankId) =>
      DynamicEndpoint(dynamicEndpointId.orNull, swaggerString.orNull, userId.orNull, bankId.orNull)
  }

  private def query(condition: Fragment): List[DynamicEndpoint] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[DynamicEndpoint] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /** The bank id, when supplied, only narrows the match — it is not part of the key. */
  private def idCondition(dynamicEndpointId: String, bankId: Option[String]): Fragment =
    bankId match {
      case None => fr"WHERE dynamicendpointid = $dynamicEndpointId"
      case Some(b) => fr"WHERE dynamicendpointid = $dynamicEndpointId AND bankid = $b"
    }

  def insert(userId: String, bankId: Option[String], swaggerString: String): DynamicEndpoint = {
    val dynamicEndpointId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO dynamicendpoint
            (dynamicendpointid, userid, bankid, swaggerstring, createdat, updatedat)
            VALUES ($dynamicEndpointId, $userId, $bankId, $swaggerString, $now, $now)"""
        .update.run)
    DynamicEndpoint(dynamicEndpointId, swaggerString, userId, bankId.orNull)
  }

  def find(dynamicEndpointId: String, bankId: Option[String]): Box[DynamicEndpoint] =
    one(idCondition(dynamicEndpointId, bankId))

  def findAll(bankId: Option[String]): List[DynamicEndpoint] = bankId match {
    case None => query(fr"ORDER BY id ASC")
    case Some(b) => query(fr"WHERE bankid = $b ORDER BY id ASC")
  }

  def findAllByUserId(userId: String): List[DynamicEndpoint] =
    query(fr"WHERE userid = $userId ORDER BY id ASC")

  def updateSwagger(dynamicEndpointId: String, bankId: Option[String],
                    swaggerString: String): Box[DynamicEndpoint] =
    find(dynamicEndpointId, bankId).map { _ =>
      DoobieUtil.runUpdate(
        (fr"UPDATE dynamicendpoint SET swaggerstring = $swaggerString," ++
          fr"updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}" ++
          idCondition(dynamicEndpointId, bankId).stripMargin).update.run)
      find(dynamicEndpointId, bankId)
    }.flatMap(box => box)

  def delete(dynamicEndpointId: String, bankId: Option[String]): Boolean = {
    val where = idCondition(dynamicEndpointId, bankId)
    DoobieUtil.runUpdate((fr"DELETE FROM dynamicendpoint" ++ where).update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicendpoint".update.run)
    ()
  }
}

object MappedDynamicEndpointProvider extends DynamicEndpointProvider with CustomJsonFormats {
  val dynamicEndpointTTL : Int = {
    if(Props.testMode) 0
    else //Better set this to 0, we maybe create multiple endpoints, when we create new ones. 
      APIUtil.getPropsValue(s"dynamicEndpoint.cache.ttl.seconds", "0").toInt
  }

  override def create(bankId: Option[String], userId: String, swaggerString: String): Box[DynamicEndpointT] =
    tryo(DynamicEndpoint.insert(userId, bankId, swaggerString))

  override def update(bankId: Option[String], dynamicEndpointId: String,
                      swaggerString: String): Box[DynamicEndpointT] =
    DynamicEndpoint.updateSwagger(dynamicEndpointId, bankId, swaggerString)

  override def updateHost(bankId: Option[String], dynamicEndpointId: String,
                          hostString: String): Box[DynamicEndpointT] =
    DynamicEndpoint.find(dynamicEndpointId, bankId).flatMap { dynamicEndpoint =>
      val updatedHost = DynamicEndpointHelper.changeOpenApiVersionHost(dynamicEndpoint.swaggerString, hostString)
      DynamicEndpoint.updateSwagger(dynamicEndpointId, bankId, updatedHost)
    }

  override def get(bankId: Option[String], dynamicEndpointId: String): Box[DynamicEndpointT] =
    DynamicEndpoint.find(dynamicEndpointId, bankId)

  override def getAll(bankId: Option[String]): List[DynamicEndpointT] = {
    val cacheKey = ("code.dynamicEndpoint.MappedDynamicEndpointProvider", "getAll", List(bankId).mkString("_"))
    Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (dynamicEndpointTTL.second) {
      DynamicEndpoint.findAll(bankId)
    }
  }

  override def getDynamicEndpointsByUserId(userId: String): List[DynamicEndpointT] =
    DynamicEndpoint.findAllByUserId(userId)

  override def delete(bankId: Option[String], dynamicEndpointId: String): Boolean =
    DynamicEndpoint.delete(dynamicEndpointId, bankId)
}
