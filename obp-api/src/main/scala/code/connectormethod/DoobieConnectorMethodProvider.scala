package code.connectormethod

import code.api.cache.Caching
import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.concurrent.duration.DurationInt

/**
 * Doobie implementation of the connector-method store, replacing the Lift ConnectorMethod entity.
 *
 * Lang is nullable and defaults to "Scala" on read. That default is not cosmetic: rows written
 * before the column existed have it null, and DynamicScalaCompiler picks its compiler from this
 * value, so a null would send an existing connector method down the wrong path.
 *
 * getByMethodNameWithCache and getAll stay cached under the same TTL rule, including the zero TTL
 * in test mode. The cache keys keep their shape - they are what lands in Redis - with only the
 * provider class name inside them changing, exactly as the class did.
 *
 * update is keyed on the connector method id and returns Empty when there is no such row, which
 * is how the endpoint tells update apart from create. It rewrites the body and language only; the
 * method name is fixed at creation, and the unique index on it is what the connector dispatch
 * relies on for a single-row lookup.
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on a pool with autoCommit off, so the write would be rolled back on return.
 */
object DoobieConnectorMethodProvider extends ConnectorMethodProvider {

  private val getConnectorMethodTTL: Int = {
    if (Props.testMode) 0
    else APIUtil.getPropsValue(s"connectorMethod.cache.ttl.seconds", "40").toInt
  }

  private type Row = (String, String, String, Option[String])

  private def toJson(r: Row): JsonConnectorMethod =
    JsonConnectorMethod(Some(r._1), r._2, r._3, r._4.getOrElse("Scala"))

  private val selectCols: Fragment =
    fr"SELECT connectormethodid, methodname, methodbody, lang FROM connectormethod"

  override def getById(connectorMethodId: String): Box[JsonConnectorMethod] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE connectormethodid = $connectorMethodId LIMIT 1").query[Row].option
    ) match {
      case Some(r) => Full(toJson(r))
      case None    => Empty
    }

  override def getByMethodNameWithoutCache(methodName: String): Box[JsonConnectorMethod] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE methodname = $methodName LIMIT 1").query[Row].option
    ) match {
      case Some(r) => Full(toJson(r))
      case None    => Empty
    }

  override def getByMethodNameWithCache(methodName: String): Box[JsonConnectorMethod] = {
    val cacheKey = ("code.connectormethod.DoobieConnectorMethodProvider", "getByMethodNameWithCache", List(methodName).mkString("_"))
    Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(getConnectorMethodTTL.second) {
      getByMethodNameWithoutCache(methodName)
    }
  }

  override def getAll(): List[JsonConnectorMethod] = {
    val cacheKey = ("code.connectormethod.DoobieConnectorMethodProvider", "getAll", List().mkString("_"))
    Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(getConnectorMethodTTL.second) {
      DoobieUtil.runQuery(selectCols.query[Row].to[List]).map(toJson)
    }
  }

  override def create(entity: JsonConnectorMethod, createdByUserId: Option[String]): Box[JsonConnectorMethod] = {
    val id = APIUtil.generateUUID()
    // Provenance is written from the authenticated user and a hash computed here, never from the
    // request body. createdat/updatedat are what the Mapper CreatedUpdated trait used to set.
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    tryo {
      // Inside the tryo, not before it: decodedMethodBody is URLDecoder.decode of a
      // caller-supplied string, which throws on a malformed escape ('%' is ordinary in Scala
      // source). Computing it outside turned that into an exception escaping create, where the
      // Mapper implementation returned a Failure the endpoint could report.
      val hash = APIUtil.sha256Hex(entity.decodedMethodBody)
      DoobieUtil.runUpdate(
        sql"""INSERT INTO connectormethod (connectormethodid, methodname, methodbody, lang,
                createdbyuserid, methodbodyhash, createdat, updatedat)
              VALUES ($id, ${entity.methodName}, ${entity.methodBody}, ${entity.programmingLang},
                $createdByUserId, ${Option(hash)}, $now, $now)"""
          .update.run)
      JsonConnectorMethod(Some(id), entity.methodName, entity.methodBody, entity.programmingLang)
    }
  }

  override def update(connectorMethodId: String, connectorMethodBody: String, programmingLang: String,
                      updatedByUserId: Option[String]): Box[JsonConnectorMethod] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE connectormethodid = $connectorMethodId LIMIT 1").query[Row].option
    ) match {
      case Some(existing) =>
        tryo {
          val now = new java.sql.Timestamp(System.currentTimeMillis())
          val hash = APIUtil.sha256Hex(
            java.net.URLDecoder.decode(connectorMethodBody, "UTF-8"))
          DoobieUtil.runUpdate(
            sql"""UPDATE connectormethod SET methodbody = $connectorMethodBody, lang = $programmingLang,
                    updatedbyuserid = $updatedByUserId, methodbodyhash = ${Option(hash)},
                    updatedat = $now
                  WHERE connectormethodid = $connectorMethodId"""
              .update.run)
          JsonConnectorMethod(Some(connectorMethodId), existing._2, connectorMethodBody, programmingLang)
        }
      case None => Empty
    }

  private type ProvRow = (String, String, String, Option[String], Option[String], Option[String],
    Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def toProv(r: ProvRow): ConnectorMethodWithProvenance =
    ConnectorMethodWithProvenance(
      JsonConnectorMethod(Some(r._1), r._2, r._3, r._4.getOrElse("Scala")),
      r._5, r._6, r._7,
      // java.sql.Timestamp is a java.util.Date subclass json4s renders as {} - convert.
      r._8.map(t => new java.util.Date(t.getTime)), r._9.map(t => new java.util.Date(t.getTime)))

  private val selectProvCols: Fragment =
    fr"""SELECT connectormethodid, methodname, methodbody, lang, createdbyuserid, updatedbyuserid,
                methodbodyhash, createdat, updatedat
         FROM connectormethod"""

  /** The v7.0.0 read-only provenance endpoints; the ordinary reads keep returning the plain DTO. */
  def getAllWithProvenance(): List[ConnectorMethodWithProvenance] =
    DoobieUtil.runQuery(selectProvCols.query[ProvRow].to[List]).map(toProv)

  def getByIdWithProvenance(connectorMethodId: String): Box[ConnectorMethodWithProvenance] =
    DoobieUtil.runQuery(
      (selectProvCols ++ fr"WHERE connectormethodid = $connectorMethodId LIMIT 1").query[ProvRow].option
    ) match {
      case Some(r) => Full(toProv(r))
      case None    => Empty
    }

  override def deleteById(id: String): Box[Boolean] = tryo {
    DoobieUtil.runUpdate(sql"DELETE FROM connectormethod WHERE connectormethodid = $id".update.run)
    true
  }
}
