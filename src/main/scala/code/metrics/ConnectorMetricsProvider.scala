package code.metrics

import java.util.{Calendar, Date}

import code.api.util.APIUtil
import code.bankconnectors.OBPQueryParam
import code.remotedata.RemotedataConnectorMetrics
import net.liftweb.util.{Props, SimpleInjector}

object ConnectorMetricsProvider extends SimpleInjector {

  val metrics = new Inject(buildOne _) {}

  def buildOne: ConnectorMetricsProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => ConnectorMetrics
      case true => RemotedataConnectorMetrics     // We will use Akka as a middleware
    }

  /**
   * Returns a Date which is at the start of the day of the date
   * of the metric. Useful for implementing getAllGroupedByDay
   * @param metric
   * @return
   */
  def getMetricDay(metric : ConnectorMetric) : Date = {
    val cal = Calendar.getInstance()
    cal.setTime(metric.getDate())
    cal.set(Calendar.HOUR_OF_DAY,0)
    cal.set(Calendar.MINUTE,0)
    cal.set(Calendar.SECOND,0)
    cal.set(Calendar.MILLISECOND,0)
    cal.getTime
  }

}

trait ConnectorMetricsProvider {

  def saveConnectorMetric(connectorName: String, functionName: String, correlationId: String, date: Date, duration: Long): Unit
  def getAllConnectorMetrics(queryParams: List[OBPQueryParam]): List[ConnectorMetric]
  def bulkDeleteConnectorMetrics(): Boolean

}

class RemotedataConnectorMetricsCaseClasses {
  case class saveConnecotrMetric(connectorName: String, functionName: String, correlationId: String, date: Date, duration: Long)
  case class getAllConnectorMetrics(queryParams: List[OBPQueryParam])
  case class bulkDeleteMetrics()
}

object RemotedataConnectorMetricsCaseClasses extends RemotedataConnectorMetricsCaseClasses

trait ConnectorMetric {

  def getConnectorName(): String
  def getFunctionName(): String
  def getCorrelationId(): String
  def getDate(): Date
  def getDuration(): Long

}
