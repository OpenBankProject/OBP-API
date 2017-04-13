package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.bankconnectors.OBPQueryParam
import code.metrics.{ConnMetrics, MappedConnectorMetric, RemotedataConnectorMetricsCaseClasses}


object RemotedataConnectorMetrics extends ActorInit with ConnMetrics {

  val cc = RemotedataConnectorMetricsCaseClasses

  def saveMetric(connectorName: String, functionName: String, obpApiRequestId: String, date: Date, duration: Long) : Unit =
    extractFuture(actor ? cc.saveMetric(connectorName, functionName, obpApiRequestId, date, duration))

  def getAllMetrics(queryParams: List[OBPQueryParam]): List[MappedConnectorMetric] =
    extractFuture(actor ? cc.getAllMetrics(queryParams))

  def bulkDeleteMetrics(): Boolean =
    extractFuture(actor ? cc.bulkDeleteMetrics())

}
