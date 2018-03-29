package code.metrics

import java.util.Date
import code.api.util.APIUtil
import net.liftweb.util.SimpleInjector

object AggregateMetrics extends SimpleInjector {

  val aggregateMetrics = new Inject(buildOne _) {}

  def buildOne: AggregateMetrics =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
          case false => MappedAggregateMetrics
          case true => MappedAggregateMetrics
    }
}

trait AggregateMetrics {
  def getAllAggregateMetrics(startDate: Date, endDate: Date): List[Double]

  //def bulkDeleteAggregateMetrics(): Boolean

}

class RemotedataAggregateMetricsCaseClasses {
  case class saveAggregateMetric(total_api_calls: Long, average_duration: String, minimum_duration: String, maximum_duration: String)
  case class getAllAggregateMetrics(startDate: Date, endDate: Date)
  case class bulkDeleteAggregateMetrics()
}

object RemotedataAggregateMetricsCaseClasses extends RemotedataAggregateMetricsCaseClasses

trait AggregateMetric {

  def getTotalCount(): Long
  def getAvgDuration(): String
  def getMinDuration(): String
  def getMaxDuration(): String

}
