package code.remotedata

import java.util.Date
import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metrics.{MappedAggregateMetrics, RemotedataAggregateMetricsCaseClasses}
import code.util.Helper.MdcLoggable


class RemotedataAggregateMetricsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedAggregateMetrics
  val cc = RemotedataAggregateMetricsCaseClasses

  def receive = {
    case cc.getAllAggregateMetrics(startDate: Date, endDate: Date) =>
      logger.debug("getAllAggregateMetrics()")
      sender ! extractResult(mapper.getAllAggregateMetrics(startDate: Date, endDate: Date))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

