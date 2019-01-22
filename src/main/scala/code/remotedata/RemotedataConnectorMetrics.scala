package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.api.util.OBPQueryParam
import code.metrics.{ConnectorMetricsProvider, MappedConnectorMetric, RemotedataConnectorMetricsCaseClasses}


object RemotedataConnectorMetrics extends ObpActorInit with ConnectorMetricsProvider {

  val cc = RemotedataConnectorMetricsCaseClasses

  def saveConnectorMetric(connectorName: String, functionName: String, obpApiRequestId: String, date: Date, duration: Long) : Unit = getValueFromFuture(
    (actor ? cc.saveConnecotrMetric(connectorName, functionName, obpApiRequestId, date, duration)).mapTo[Unit]
  )

  def getAllConnectorMetrics(queryParams: List[OBPQueryParam]): List[MappedConnectorMetric] = getValueFromFuture(
    (actor ? cc.getAllConnectorMetrics(queryParams)).mapTo[List[MappedConnectorMetric]]
  )

  def bulkDeleteConnectorMetrics(): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteMetrics()).mapTo[Boolean]
  )

}
