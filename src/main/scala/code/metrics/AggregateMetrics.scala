package code.metrics

import java.util.Date
import code.api.util.APIUtil
import code.remotedata.RemotedataAggregateMetrics
import net.liftweb.util.SimpleInjector

object AggregateMetrics extends SimpleInjector {

  val aggregateMetrics = new Inject(buildOne _) {}

  def buildOne: AggregateMetrics =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
          case false => MappedAggregateMetrics
          case true => RemotedataAggregateMetrics
    }
}

trait AggregateMetrics {
  def getAllAggregateMetrics(startDate: Date, endDate: Date): List[Double]

}

class RemotedataAggregateMetricsCaseClasses {
  case class saveAggregateMetric(total_api_calls: Long, average_response_time: String, minimum_response_time: String, maximum_response_time: String)
  case class getAllAggregateMetrics(startDate: Date, endDate: Date)
  case class bulkDeleteAggregateMetrics()
}

object RemotedataAggregateMetricsCaseClasses extends RemotedataAggregateMetricsCaseClasses

