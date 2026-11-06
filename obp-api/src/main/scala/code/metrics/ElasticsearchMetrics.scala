/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.metrics

import java.util.Date
import code.api.util._
import code.search.elasticsearchMetrics
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Box

import scala.concurrent.Future

object ElasticsearchMetrics extends APIMetrics {

  lazy val es = new elasticsearchMetrics

  override def saveMetric(userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String,  httpCode: Option[Int], correlationId: String,
                          responseBody: String, sourceIp: String, targetIp: String, apiInstanceId: String, consentReferenceId: String,
                          certificateTrust: String, certificateTrustDetail: String,
                          authType: String): Unit = {
    if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) && APIUtil.getPropsAsBoolValue("allow_elasticsearch_metrics", false) ) {
      //TODO ,need to be fixed now add more parameters
      es.indexMetric(userId, url, date, duration, userName, appName, developerEmail, correlationId, apiInstanceId)
    }
  }
  override def saveMetricsArchive(primaryKey: Long, userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String,  httpCode: Option[Int], correlationId: String,
                                  responseBody: String,
                                  sourceIp: String,
                                  targetIp: String,
                                  apiInstanceId: String,
                                  consentReferenceId: String,
                                  certificateTrust: String,
                                  certificateTrustDetail: String,
                                  authType: String): Boolean = ???

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
    // This reads the SQL metrics table, not Elasticsearch, and it only honours paging, the date
    // range and the ordering - never the other filters. Preserved as it was: the sort field of an
    // OBPOrdering is ignored here and the rows are ordered by date either way.
    val params = MetricQuery.fromQueryParams(queryParams)
    MappedMetric.findAll(params.copy(
      orderBy = params.orderBy.map { case (_, ascending) => ("date_c", ascending) },
      consumerId = None, bankId = None, userId = None, url = None, appName = None,
      implementedInVersion = None, implementedByPartialFunction = None, verb = None,
      correlationId = None, durationGreaterThan = None, httpStatusCode = None,
      consentReferenceId = None, certificateTrust = None, anon = None, excludeAppNames = None))
  }
  
  override def getAllAggregateMetricsFuture(queryParams: List[OBPQueryParam], isNewVersion: Boolean): Future[Box[List[AggregateMetrics]]] = ???
  
  override def getTopApisFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopApi]]] = ???
  
  override def getTopConsumersFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopConsumer]]] = ???

  override def getTopUsersFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopUser]]] = ???

  override def getTopConsumersByConsumerIdFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopConsumer]]] = ???

  override def bulkDeleteMetrics(): Boolean = {
    MappedMetric.deleteAll()
    true
  }
}
