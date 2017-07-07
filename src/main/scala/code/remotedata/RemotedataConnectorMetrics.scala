package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.bankconnectors.OBPQueryParam
import code.metrics.{ConnectorMetricsProvider, MappedConnectorMetric, RemotedataConnectorMetricsCaseClasses}


object RemotedataConnectorMetrics extends ObpActorInit with ConnectorMetricsProvider {

  val cc = RemotedataConnectorMetricsCaseClasses

  def saveConnectorMetric(connectorName: String, functionName: String, obpApiRequestId: String, date: Date, duration: Long) : Unit =
    extractFuture(actor ? cc.saveConnecotrMetric(connectorName, functionName, obpApiRequestId, date, duration))

  def getAllConnectorMetrics(queryParams: List[OBPQueryParam]): List[MappedConnectorMetric] =
    extractFuture(actor ? cc.getAllConnectorMetrics(queryParams))

  def bulkDeleteConnectorMetrics(): Boolean =
    extractFuture(actor ? cc.bulkDeleteMetrics())

}
