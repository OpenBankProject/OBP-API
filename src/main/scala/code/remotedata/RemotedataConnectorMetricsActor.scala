package code.remotedata

import java.util.Date

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metrics.{ConnectorMetrics, RemotedataConnectorMetricsCaseClasses}
import code.util.Helper.MdcLoggable

class RemotedataConnectorMetricsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = ConnectorMetrics
  val cc = RemotedataConnectorMetricsCaseClasses

  def receive = {

    case cc.saveConnecotrMetric(connectorName: String, functionName: String, obpApiRequestId: String, date: Date, duration: Long) =>
      logger.debug("saveMetric()")
      sender ! extractResult(mapper.saveConnectorMetric(connectorName, functionName, obpApiRequestId, date, duration))

    case cc.getAllConnectorMetrics(queryParams) =>
      logger.debug("getAllMetrics()")
      sender ! extractResult(mapper.getAllConnectorMetrics(queryParams))

    case cc.bulkDeleteMetrics() =>
      logger.debug("bulkDeleteMetrics()")
      sender ! extractResult(mapper.bulkDeleteConnectorMetrics())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

