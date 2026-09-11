package code.metrics

import java.util.Date

import code.api.cache.Caching
import code.api.util._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

import scala.concurrent.duration._

/**
 * One connector method call.
 *
 * The table is append-only from the application's side: ConnectorMetricBatchWriter batches the
 * inserts and nothing updates a row afterwards. Five plain indexes and no unique one is correct —
 * a connector may call the same function under the same correlation id more than once, and each
 * call is its own row.
 */
case class MappedConnectorMetric(
  private val connectorName: String,
  private val functionName: String,
  private val correlationId: String,
  private val date: Date,
  private val duration: Long,
  private val requestParams: String,
  private val isSuccessful: Boolean,
  private val apiInstanceId: String
) extends ConnectorMetric {
  override def getConnectorName(): String = connectorName
  override def getFunctionName(): String = functionName
  override def getCorrelationId(): String = correlationId
  override def getDate(): Date = date
  override def getDuration(): Long = duration
  override def getRequestParams(): String = requestParams
  override def getIsSuccessful(): Boolean = isSuccessful
  override def getApiInstanceId(): String = apiInstanceId
}

object MappedConnectorMetric {

  // date is stored as date_c: DATE collides with a SQL reserved word.
  private val selectColumns =
    fr"""SELECT connectorname, functionname, correlationid, date_c, duration, requestparams,
                issuccessful, apiinstanceid
         FROM mappedconnectormetric"""

  private type Row = (Option[String], Option[String], Option[String], Option[java.sql.Timestamp],
    Option[Long], Option[String], Option[Boolean], Option[String])

  private def fromRow(row: Row): MappedConnectorMetric = row match {
    case (connectorName, functionName, correlationId, date, duration, requestParams, isSuccessful,
          apiInstanceId) =>
      MappedConnectorMetric(connectorName.orNull, functionName.orNull, correlationId.orNull,
        date.orNull, duration.getOrElse(0L), requestParams.orNull, isSuccessful.getOrElse(false),
        apiInstanceId.orNull)
  }

  /**
   * Filters, ordering, limit and offset are applied only when supplied, matching the Mapper
   * QueryParam list. When no ordering is requested the id order stands in for the database's scan
   * order, which is what Mapper returned and what makes LIMIT/OFFSET deterministic.
   */
  def findAllFiltered(queryParams: List[OBPQueryParam]): List[MappedConnectorMetric] = {
    val conditions = List(
      queryParams.collectFirst { case OBPFromDate(date) =>
        fr"date_c >= ${new java.sql.Timestamp(date.getTime)}" },
      queryParams.collectFirst { case OBPToDate(date) =>
        fr"date_c <= ${new java.sql.Timestamp(date.getTime)}" },
      queryParams.collectFirst { case OBPCorrelationId(value) => fr"correlationid = $value" },
      queryParams.collectFirst { case OBPFunctionName(value) => fr"functionname = $value" },
      queryParams.collectFirst { case OBPConnectorName(value) => fr"connectorname = $value" }
    ).flatten
    val where =
      if (conditions.isEmpty) Fragment.empty
      else fr"WHERE " ++ conditions.reduce((a, b) => a ++ fr"AND" ++ b)
    // We don't care about the intended sort field and only sort on finish date for now.
    val ordering = queryParams.collectFirst {
      case OBPOrdering(_, OBPAscending) => fr"ORDER BY date_c ASC, id ASC"
      case OBPOrdering(_, OBPDescending) => fr"ORDER BY date_c DESC, id DESC"
    }.getOrElse(fr"ORDER BY id ASC")
    val limit = queryParams.collectFirst { case OBPLimit(value) => fr"LIMIT $value" }.getOrElse(Fragment.empty)
    val offset = queryParams.collectFirst { case OBPOffset(value) => fr"OFFSET $value" }.getOrElse(Fragment.empty)
    DoobieUtil.runQuery(
      (selectColumns ++ where ++ ordering ++ limit ++ offset).query[Row].to[List]).map(fromRow)
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedconnectormetric".update.run)
    ()
  }
}

object ConnectorMetrics extends ConnectorMetricsProvider {

  val cachedAllConnectorMetrics = APIUtil.getPropsValue(s"ConnectorMetrics.cache.ttl.seconds.getAllConnectorMetrics", "7").toInt

  override def saveConnectorMetric(connectorName: String, functionName: String, correlationId: String, date: Date, duration: Long,
                                   requestParams: String, isSuccessful: Boolean, apiInstanceId: String): Unit = {
    ConnectorMetricBatchWriter.enqueue(
      ConnectorMetricBatchWriter.ConnectorMetricRow(
        connectorName = connectorName,
        functionName = functionName,
        correlationId = correlationId,
        date = date,
        duration = duration,
        requestParams = requestParams,
        isSuccessful = isSuccessful,
        apiInstanceId = apiInstanceId
      )
    )
  }

  override def getAllConnectorMetrics(queryParams: List[OBPQueryParam]): List[MappedConnectorMetric] = {
    val cacheKey = ("code.metrics.ConnectorMetrics", "getAllConnectorMetrics", List(queryParams).mkString("_"))
    Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cachedAllConnectorMetrics.days) {
      MappedConnectorMetric.findAllFiltered(queryParams)
    }
  }

  // Deletes MappedMetric, not MappedConnectorMetric. That is a pre-existing defect — the method
  // named bulkDeleteConnectorMetrics empties the API-metric table and leaves connector metrics
  // untouched — and it is preserved verbatim rather than corrected under a storage swap, because
  // any caller relying on it today is relying on the API metrics being cleared.
  override def bulkDeleteConnectorMetrics(): Boolean = {
    MappedMetric.deleteAll()
    true
  }
}
