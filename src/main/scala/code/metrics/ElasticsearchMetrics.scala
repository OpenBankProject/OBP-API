package code.metrics

import java.util.Date

import code.search.elasticsearchMetrics
import net.liftweb.util.Props

object ElasticsearchMetrics extends APIMetrics {

  val es = new elasticsearchMetrics

  override def saveMetric(userId: String, url: String, date: Date): Unit = {
    if (Props.getBool("allow_elasticsearch", false) && Props.getBool("allow_elasticsearch_metrics", false) ) {

      es.indexMetric(userId, url, date)
    }
  }

  override def getAllGroupedByUserId(): Map[String, List[APIMetric]] = {
    //TODO: replace the following with valid ES query
    MappedMetric.findAll.groupBy(_.getUserId)
  }

  override def getAllGroupedByDay(): Map[Date, List[APIMetric]] = {
    //TODO: replace the following with valid ES query
    MappedMetric.findAll.groupBy(APIMetrics.getMetricDay)
  }

  override def getAllGroupedByUrl(): Map[String, List[APIMetric]] = {
    //TODO: replace the following with valid ES query
    MappedMetric.findAll.groupBy(_.getUrl())
  }

}
