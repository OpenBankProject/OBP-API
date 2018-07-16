package code.metrics

import java.util.Date

import code.bankconnectors._
import code.util.{MappedUUID}
import net.liftweb.mapper._

object ConnectorMetrics extends ConnectorMetricsProvider {

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
