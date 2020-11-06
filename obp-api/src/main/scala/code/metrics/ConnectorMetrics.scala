package code.metrics

import java.util.Date
import java.util.UUID.randomUUID

import code.api.cache.Caching
import code.api.util._
import code.util.{MappedUUID}
import com.tesobe.CacheKeyFromArguments
import net.liftweb.mapper._
import scala.concurrent.duration._

object ConnectorMetrics extends ConnectorMetricsProvider {

  val cachedAllConnectorMetrics = APIUtil.getPropsValue(s"ConnectorMetrics.cache.ttl.seconds.getAllConnectorMetrics", "7").toInt

  override def saveConnectorMetric(connectorName: String, functionName: String, correlationId: String, date: Date, duration: Long): Unit = {
    MappedConnectorMetric.create
      .connectorName(connectorName)
      .functionName(functionName)
      .date(date)
      .duration(duration)
      .correlationId(correlationId)
      .save
  }

  override def getAllConnectorMetrics(queryParams: List[OBPQueryParam]): List[MappedConnectorMetric] = {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value field with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey { 
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cachedAllConnectorMetrics days){
          val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedConnectorMetric](value) }.headOption
          val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedConnectorMetric](value) }.headOption
          val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedConnectorMetric.date, date) }.headOption
          val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedConnectorMetric.date, date) }.headOption
          val correlationId = queryParams.collect { case OBPCorrelationId(value) => By(MappedConnectorMetric.correlationId, value) }.headOption
          val functionName = queryParams.collect { case OBPFunctionName(value) => By(MappedConnectorMetric.functionName, value) }.headOption
          val connectorName = queryParams.collect { case OBPConnectorName(value) => By(MappedConnectorMetric.connectorName, value) }.headOption
          val ordering = queryParams.collect {
            //we don't care about the intended sort field and only sort on finish date for now
            case OBPOrdering(_, direction) =>
              direction match {
                case OBPAscending => OrderBy(MappedConnectorMetric.date, Ascending)
                case OBPDescending => OrderBy(MappedConnectorMetric.date, Descending)
              }
          }
          val optionalParams : Seq[QueryParam[MappedConnectorMetric]] = Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering,
                                                                            correlationId.toSeq, functionName.toSeq, connectorName.toSeq).flatten
    
          MappedConnectorMetric.findAll(optionalParams: _*)
      }
    }
  }

  override def bulkDeleteConnectorMetrics(): Boolean = {
    MappedMetric.bulkDelete_!!()
  }

}

class MappedConnectorMetric extends ConnectorMetric with LongKeyedMapper[MappedConnectorMetric] with IdPK {
  override def getSingleton = MappedConnectorMetric

  object connectorName extends MappedString(this, 64) // TODO Enforce max lenght of this when we get the Props connector
  object functionName extends MappedString(this, 64)
  object correlationId extends MappedUUID(this)
  object date extends MappedDateTime(this)
  object duration extends MappedLong(this)

  override def getConnectorName(): String = connectorName.get
  override def getFunctionName(): String = functionName.get
  override def getCorrelationId(): String = correlationId.get
  override def getDate(): Date = date.get
  override def getDuration(): Long = duration.get
}

object MappedConnectorMetric extends MappedConnectorMetric with LongKeyedMetaMapper[MappedConnectorMetric] {
  override def dbIndexes = Index(connectorName) :: Index(functionName) :: Index(date) :: Index(correlationId) :: super.dbIndexes
}
