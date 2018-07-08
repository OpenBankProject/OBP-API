package code.metrics

import java.util.Date

import code.api.util.APIUtil
import code.bankconnectors._
import code.search.elasticsearchMetrics
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Props

import scala.concurrent.Future

object ElasticsearchMetrics extends APIMetrics {

  val es = new elasticsearchMetrics

  override def saveMetric(userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String, correlationId: String): Unit = {
    if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) && APIUtil.getPropsAsBoolValue("allow_elasticsearch_metrics", false) ) {
      //TODO ,need to be fixed now add more parameters
      es.indexMetric(userId, url, date, duration, userName, appName, developerEmail, correlationId)
    }
  }

//  override def getAllGroupedByUserId(): Map[String, List[APIMetric]] = {
//    //TODO: replace the following with valid ES query
//    MappedMetric.findAll.groupBy(_.getUserId)
//  }
//
//  override def getAllGroupedByDay(): Map[Date, List[APIMetric]] = {
//    //TODO: replace the following with valid ES query
//    MappedMetric.findAll.groupBy(APIMetrics.getMetricDay)
//  }
//
//  override def getAllGroupedByUrl(): Map[String, List[APIMetric]] = {
//    //TODO: replace the following with valid ES query
//    MappedMetric.findAll.groupBy(_.getUrl())
//  }

  override def getAllMetrics(queryParams: List[OBPQueryParam]): List[APIMetric] = {
    //TODO: replace the following with valid ES query
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedMetric](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedMetric](value) }.headOption
    val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedMetric.date, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedMetric.date, date) }.headOption
    val ordering = queryParams.collect {
      //we don't care about the intended sort field and only sort on finish date for now
      case OBPOrdering(_, direction) =>
        direction match {
          case OBPAscending => OrderBy(MappedMetric.date, Ascending)
          case OBPDescending => OrderBy(MappedMetric.date, Descending)
        }
    }
    val optionalParams : Seq[QueryParam[MappedMetric]] = Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering).flatten

    MappedMetric.findAll(optionalParams: _*)
  }
  
  override def getAllAggregateMetricsFuture(queryParams: List[OBPQueryParam]): Future[Box[List[AggregateMetrics]]] = ???
  
  override def getTopApisFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopApi]]] = ???
  
  override def getTopConsumersFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopConsumer]]] = ???

  override def bulkDeleteMetrics(): Boolean = {
    MappedMetric.bulkDelete_!!()
  }
}
