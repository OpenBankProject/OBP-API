package code.metrics

import java.util.Date

import code.bankconnectors._
import code.util.{DefaultStringField, MappedUUID}
import net.liftweb.mapper._

object ConnectorMetrics extends ConnMetrics {

  override def saveMetric(connectorName: String, functionName: String, obpApiRequestId: String, date: Date, duration: Long): Unit = {
    MappedConnectorMetric.create
      .connectorName(connectorName)
      .functionName(functionName)
      .date(date)
      .duration(duration)
      //.obpApiRequestId(obpApiRequestId)
      .save
  }

  override def getAllMetrics(queryParams: List[OBPQueryParam]): List[MappedConnectorMetric] = {
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedConnectorMetric](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedConnectorMetric](value) }.headOption
    val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedConnectorMetric.date, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedConnectorMetric.date, date) }.headOption
    val ordering = queryParams.collect {
      //we don't care about the intended sort field and only sort on finish date for now
      case OBPOrdering(_, direction) =>
        direction match {
          case OBPAscending => OrderBy(MappedConnectorMetric.date, Ascending)
          case OBPDescending => OrderBy(MappedConnectorMetric.date, Descending)
        }
    }
    val optionalParams : Seq[QueryParam[MappedConnectorMetric]] = Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering).flatten

    MappedConnectorMetric.findAll(optionalParams: _*)
  }

  override def bulkDeleteMetrics(): Boolean = {
    MappedMetric.bulkDelete_!!()
  }

}

class MappedConnectorMetric extends ConnMetric with LongKeyedMapper[MappedConnectorMetric] with IdPK {
  override def getSingleton = MappedConnectorMetric

  object connectorName extends DefaultStringField(this)
  object functionName extends DefaultStringField(this)
  object obpApiRequestId extends MappedUUID(this)
  object date extends MappedDateTime(this)
  object duration extends MappedLong(this)

  override def getConnectorName(): String = connectorName.get
  override def getFunctionName(): String = functionName.get
  override def getObpApiRequestId(): String = obpApiRequestId.get
  override def getDate(): Date = date.get
  override def getDuration(): Long = duration.get
}

object MappedConnectorMetric extends MappedConnectorMetric with LongKeyedMetaMapper[MappedConnectorMetric] {
  override def dbIndexes = Index(connectorName) :: Index(functionName) :: Index(date) :: Index(obpApiRequestId) :: super.dbIndexes
}
