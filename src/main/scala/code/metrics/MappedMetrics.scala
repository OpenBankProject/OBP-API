package code.metrics

import java.util.Date

import code.util.DefaultStringField
import net.liftweb.mapper._

object MappedMetrics extends APIMetrics {

  override def saveMetric(url: String, date: Date): Unit = {
    MappedMetric.create.url(url).date(date).save
  }

  override def getAllGroupedByDay(): Map[Date, List[APIMetric]] = {
    //TODO: do this all at the db level using an actual group by query
    MappedMetric.findAll.groupBy(APIMetrics.getMetricDay)
  }

  override def getAllGroupedByUrl(): Map[String, List[APIMetric]] = {
    //TODO: do this all at the db level using an actual group by query
    MappedMetric.findAll.groupBy(_.getUrl())
  }

}

class MappedMetric extends APIMetric with LongKeyedMapper[MappedMetric] with IdPK {
  override def getSingleton = MappedMetric

  object url extends DefaultStringField(this)
  object date extends MappedDateTime(this)


  override def getUrl(): String = url.get
  override def getDate(): Date = date.get

}

object MappedMetric extends MappedMetric with LongKeyedMetaMapper[MappedMetric] {
  override def dbIndexes = Index(url) :: Index(date) :: super.dbIndexes
}
