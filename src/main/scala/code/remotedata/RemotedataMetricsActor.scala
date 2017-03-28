package code.remotedata

import java.util.Date

import akka.actor.Actor
import akka.event.Logging
import code.bankconnectors.OBPQueryParam
import code.metrics.{MappedMetrics, RemotedataMetricsCaseClasses}


class RemotedataMetricsActor extends Actor with ActorHelper {

  val logger = Logging(context.system, this)

  val mapper = MappedMetrics
  val cc = RemotedataMetricsCaseClasses

  def receive = {

    case cc.saveMetric(userId: String, url: String, date: Date, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String) =>
      logger.info("saveMetric()")
      sender ! extractResult(mapper.saveMetric(userId, url, date, userName, appName, developerEmail, consumerId, implementedByPartialFunction, implementedInVersion, verb))

    case cc.getAllGroupedByUrl() =>
      logger.info("getAllGroupedByUrl()")
      sender ! extractResult(mapper.getAllGroupedByUrl())

    case cc.getAllGroupedByDay() =>
      logger.info("getAllGroupedByDay()")
      sender ! extractResult(mapper.getAllGroupedByDay())

    case cc.getAllGroupedByUserId() =>
      logger.info("getAllGroupedByUserId()")
      sender ! extractResult(mapper.getAllGroupedByUserId())

    case cc.getAllMetrics(queryParams) =>
      logger.info("getAllMetrics()")
      sender ! extractResult(mapper.getAllMetrics(queryParams))

    case cc.bulkDeleteMetrics() =>
      logger.info("bulkDeleteMetrics()")
      sender ! extractResult(mapper.bulkDeleteMetrics())

    case message => logger.warning("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

