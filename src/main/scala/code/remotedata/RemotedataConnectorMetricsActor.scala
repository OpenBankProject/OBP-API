package code.remotedata

import java.util.Date

import akka.actor.Actor
import akka.event.Logging
import code.actorsystem.ActorHelper
import code.metrics.{ConnectorMetrics, RemotedataConnectorMetricsCaseClasses}
import code.util.Helper.MdcLoggable


class RemotedataConnectorMetricsActor extends Actor with ActorHelper with MdcLoggable {

  val mapper = ConnectorMetrics
  val cc = RemotedataConnectorMetricsCaseClasses

  def receive = {

    case cc.saveMetric(connectorName: String, functionName: String, obpApiRequestId: String, date: Date, duration: Long) =>
      logger.debug("saveMetric()")
      sender ! extractResult(mapper.saveMetric(connectorName, functionName, obpApiRequestId, date, duration))

    case cc.getAllMetrics(queryParams) =>
      logger.debug("getAllMetrics()")
      sender ! extractResult(mapper.getAllMetrics(queryParams))

    case cc.bulkDeleteMetrics() =>
      logger.debug("bulkDeleteMetrics()")
      sender ! extractResult(mapper.bulkDeleteMetrics())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

