package code.metrics

import java.util.Date

import code.search.elasticsearchMetrics
import net.liftweb.util.Props

object ElasticsearchMetrics extends APIMetrics {

  override def saveMetric(userId: String, url: String, date: Date): Unit = {
    if (Props.getBool("allow_elasticsearch", false) && Props.getBool("allow_elasticsearch_metrics", false) ) {
      elasticsearchMetrics.indexMetric(userId, url, date)
    }
    MappedMetric.create.url(url).date(date).save
  }

  override def getAllGroupedByUserId(): Map[String, List[APIMetric]] = {
    //TODO: do this all at the db level using an actual group by query
    MappedMetric.findAll.groupBy(_.getUserId)
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
