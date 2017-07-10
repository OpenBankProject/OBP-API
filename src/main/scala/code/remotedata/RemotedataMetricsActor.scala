package code.remotedata

import java.util.Date

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.bankconnectors.OBPQueryParam
import code.metrics.{MappedMetrics, RemotedataMetricsCaseClasses}
import code.util.Helper.MdcLoggable


class RemotedataMetricsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedMetrics
  val cc = RemotedataMetricsCaseClasses

  def receive = {

    case cc.saveMetric(userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String, correlationId: String) =>
      logger.debug("saveMetric()")
      sender ! extractResult(mapper.saveMetric(userId, url, date, duration, userName, appName, developerEmail, consumerId, implementedByPartialFunction, implementedInVersion, verb, correlationId))

//    case cc.getAllGroupedByUrl() =>
//      logger.debug("getAllGroupedByUrl()")
//      sender ! extractResult(mapper.getAllGroupedByUrl())
//
//    case cc.getAllGroupedByDay() =>
//      logger.debug("getAllGroupedByDay()")
//      sender ! extractResult(mapper.getAllGroupedByDay())
//
//    case cc.getAllGroupedByUserId() =>
//      logger.debug("getAllGroupedByUserId()")
//      sender ! extractResult(mapper.getAllGroupedByUserId())

    case cc.getAllMetrics(queryParams) =>
      logger.debug("getAllMetrics()")
      sender ! extractResult(mapper.getAllMetrics(queryParams))

    case cc.bulkDeleteMetrics() =>
      logger.debug("bulkDeleteMetrics()")
      sender ! extractResult(mapper.bulkDeleteMetrics())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

