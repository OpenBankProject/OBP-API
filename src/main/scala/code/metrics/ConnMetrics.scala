package code.metrics

import java.util.{Calendar, Date}

import code.bankconnectors.OBPQueryParam
import code.remotedata.RemotedataConnectorMetrics
import net.liftweb.util.SimpleInjector

object ConnMetrics extends SimpleInjector {

  val metrics = new Inject(buildOne _) {}

  def buildOne: ConnMetrics = RemotedataConnectorMetrics

  /**
   * Returns a Date which is at the start of the day of the date
   * of the metric. Useful for implementing getAllGroupedByDay
   * @param metric
   * @return
   */
  def getMetricDay(metric : ConnMetric) : Date = {
    val cal = Calendar.getInstance()
    cal.setTime(metric.getDate())
    cal.set(Calendar.HOUR_OF_DAY,0)
    cal.set(Calendar.MINUTE,0)
    cal.set(Calendar.SECOND,0)
    cal.set(Calendar.MILLISECOND,0)
    cal.getTime
  }

}

trait ConnMetrics {

  def saveMetric(connectorName: String, functionName: String, obpApiRequestId: String, date: Date, duration: Long): Unit
  def getAllMetrics(queryParams: List[OBPQueryParam]): List[ConnMetric]
  def bulkDeleteMetrics(): Boolean

}

class RemotedataConnectorMetricsCaseClasses {
  case class saveMetric(connectorName: String, functionName: String, obpApiRequestId: String, date: Date, duration: Long)
  case class getAllMetrics(queryParams: List[OBPQueryParam])
  case class bulkDeleteMetrics()
}

object RemotedataConnectorMetricsCaseClasses extends RemotedataConnectorMetricsCaseClasses

trait ConnMetric {

  def getConnectorName(): String
  def getFunctionName(): String
  def getObpApiRequestId(): String
  def getDate(): Date
  def getDuration(): Long

}
