package code.metrics

import java.util.{Calendar, Date}

import code.api.util.APIUtil
import code.bankconnectors.OBPQueryParam
import code.remotedata.RemotedataMetrics
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object AggregateMetrics extends SimpleInjector {

  val aggregateMetrics = new Inject(buildOne _) {}

  def buildOne: AggregateMetrics =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
          case false => MappedAggregateMetrics
          case true => MappedAggregateMetrics
    }
}

trait AggregateMetrics {

/*  def saveAggregateMetric(total_api_calls: Long,
                 average_duration: String,
                 minimum_duration: String,
                 maximum_duration: String): Unit*/

//  def getAllAggregateMetrics(queryParams: List[OBPQueryParam]): Box[Long]
  def getAllAggregateMetrics(queryParams: List[OBPQueryParam]): List[Double]

  //def bulkDeleteAggregateMetrics(): Boolean

}

class RemotedataAggregateMetricsCaseClasses {
  case class saveAggregateMetric(total_api_calls: Long, average_duration: String, minimum_duration: String, maximum_duration: String)

  //case class getAllAggregateMetrics(queryParams: List[OBPQueryParam])
  case class getAllAggregateMetrics(queryParams: List[OBPQueryParam])
  case class bulkDeleteAggregateMetrics()
}

object RemotedataAggregateMetricsCaseClasses extends RemotedataAggregateMetricsCaseClasses

trait AggregateMetric {

  def getTotalCount(): Long
  def getAvgDuration(): String
  def getMinDuration(): String
  def getMaxDuration(): String

}
